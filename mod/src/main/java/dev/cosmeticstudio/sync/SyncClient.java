package dev.cosmeticstudio.sync;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.cosmeticstudio.CosmeticStudioClient;
import dev.cosmeticstudio.config.ModConfig;
import dev.cosmeticstudio.model.ArmorCosmetic;
import dev.cosmeticstudio.model.ArmorSlot;
import dev.cosmeticstudio.model.Look;
import dev.cosmeticstudio.model.PetCosmetic;
import dev.cosmeticstudio.model.PetPosition;
import dev.cosmeticstudio.skyblock.SkyBlockItems;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Talks to the self-hostable Cosmetic Studio sync server (never to Hypixel).
 *
 * <ul>
 * <li>Login: ask the server for a one-time serverId, call Mojang's {@code joinServer} with it (exactly what the game does
 * when joining an online-mode server), then the server asks Mojang {@code hasJoined} who that was. So nobody can set
 * cosmetics for another player's UUID. The resulting session token is kept in memory only.</li>
 * <li>Upload: what you are wearing right now, resolved per slot (item UUIDs are never sent), plus your pet.</li>
 * <li>Download: batched lookups for nearby players, cached with a TTL; players without the mod come back empty and
 * are not rendered with anything.</li>
 * </ul>
 */
public final class SyncClient {
	private static final int MAX_BATCH = 100;
	private static final double RANGE_SQ = 64 * 64;

	private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "CosmeticStudio-sync");
		t.setDaemon(true);
		return t;
	});
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
	private static final Set<UUID> IN_FLIGHT = ConcurrentHashMap.newKeySet();

	private static volatile @Nullable String token;
	private static volatile long tokenExpiry;
	private static volatile @Nullable String lastUploaded;
	private static volatile boolean uploading;
	private static volatile long backoffUntil;
	private static volatile String status = "idle";
	private static int ticks;

	/** Proves account ownership to Mojang. Replaceable only so tests can run without real Mojang services. */
	@FunctionalInterface
	public interface MojangJoin {
		void join(UUID profileId, String accessToken, String serverId) throws Exception;
	}

	public static MojangJoin mojangJoin = (profile, accessToken, serverId) ->
			Minecraft.getInstance().services().sessionService().joinServer(profile, accessToken, serverId);

	private SyncClient() {}

	public static String status() {
		return status;
	}

	public static void tick(Minecraft mc) {
		if (++ticks % 40 != 0 || mc.player == null || mc.level == null) return;
		ModConfig cfg = CosmeticStudioClient.config();
		String base = baseUrl(cfg);
		if (base == null) {
			status = "off (no server URL)";
			return;
		}
		if (System.currentTimeMillis() < backoffUntil) return;

		// Upload my current look when it changes.
		String json = lookJson(currentWornLook(mc)).toString();
		if (!json.equals(lastUploaded) && !uploading) {
			uploading = true;
			IO.execute(() -> {
				try {
					upload(base, json);
					lastUploaded = json;
					status = "ok";
				} catch (Exception e) {
					fail("upload", e);
				} finally {
					uploading = false;
				}
			});
		}

		// Fetch cosmetics of nearby players that are not cached (or stale).
		if (!cfg.showOthersCosmetics) return;
		List<UUID> want = new ArrayList<>();
		for (Player p : mc.level.players()) {
			UUID id = p.getUUID();
			// Real accounts have version-4 UUIDs; Hypixel NPCs and fake tab entries use other versions.
			if (p == mc.player || id.version() != 4 || p.distanceToSqr(mc.player) > RANGE_SQ) continue;
			if (RemoteCosmetics.isFresh(id) || IN_FLIGHT.contains(id)) continue;
			want.add(id);
			if (want.size() == MAX_BATCH) break;
		}
		if (!want.isEmpty()) {
			IN_FLIGHT.addAll(want);
			IO.execute(() -> {
				try {
					fetch(base, want);
				} catch (Exception e) {
					fail("fetch", e);
				} finally {
					want.forEach(IN_FLIGHT::remove);
				}
			});
		}
	}

	/** Forget the session (e.g. when the server URL changes or on disconnect). */
	public static void reset() {
		token = null;
		lastUploaded = null;
		backoffUntil = 0;
		RemoteCosmetics.clear();
	}

	/** Deletes my data on the server. */
	public static void deleteMyData() {
		String base = baseUrl(CosmeticStudioClient.config());
		if (base == null) return;
		IO.execute(() -> {
			try {
				ensureToken(base);
				send(HttpRequest.newBuilder(URI.create(base + "/v1/cosmetics")).header("Authorization", "Bearer " + token).DELETE());
				reset();
				status = "data deleted";
			} catch (Exception e) {
				fail("delete", e);
			}
		});
	}

	private static @Nullable String baseUrl(ModConfig cfg) {
		if (!cfg.syncEnabled || cfg.syncServerUrl == null || cfg.syncServerUrl.isBlank()) return null;
		String u = cfg.syncServerUrl.strip();
		while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
		return u.startsWith("http://") || u.startsWith("https://") ? u : null;
	}

	private static void fail(String what, Exception e) {
		status = what + " failed: " + e.getMessage();
		backoffUntil = System.currentTimeMillis() + 60_000;
		CosmeticStudioClient.LOGGER.info("Sync {} failed: {}", what, e.toString());
	}

	// ------------------------------------------------------------------ protocol

	private static void ensureToken(String base) throws Exception {
		if (token != null && System.currentTimeMillis() < tokenExpiry - 60_000) return;
		Minecraft mc = Minecraft.getInstance();
		User user = mc.getUser();
		JsonObject challenge = send(HttpRequest.newBuilder(URI.create(base + "/v1/auth/challenge")).POST(HttpRequest.BodyPublishers.noBody()));
		String serverId = challenge.get("serverId").getAsString();
		// Prove account ownership to Mojang, the same call the game makes when joining an online-mode server.
		mojangJoin.join(user.getProfileId(), user.getAccessToken(), serverId);
		JsonObject body = new JsonObject();
		body.addProperty("username", user.getName());
		body.addProperty("serverId", serverId);
		JsonObject res = send(HttpRequest.newBuilder(URI.create(base + "/v1/auth/verify")).header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body.toString())));
		token = res.get("token").getAsString();
		tokenExpiry = res.get("expiresAt").getAsLong();
	}

	private static void upload(String base, String json) throws Exception {
		ensureToken(base);
		try {
			send(HttpRequest.newBuilder(URI.create(base + "/v1/cosmetics")).header("Authorization", "Bearer " + token)
					.header("Content-Type", "application/json").PUT(HttpRequest.BodyPublishers.ofString(json)));
		} catch (HttpError e) {
			if (e.code == 401) { // token expired server-side: log in again once
				token = null;
				ensureToken(base);
				send(HttpRequest.newBuilder(URI.create(base + "/v1/cosmetics")).header("Authorization", "Bearer " + token)
						.header("Content-Type", "application/json").PUT(HttpRequest.BodyPublishers.ofString(json)));
			} else {
				throw e;
			}
		}
	}

	private static void fetch(String base, List<UUID> uuids) throws Exception {
		StringBuilder q = new StringBuilder();
		for (UUID u : uuids) q.append(q.isEmpty() ? "" : ",").append(u);
		JsonObject res = send(HttpRequest.newBuilder(URI.create(base + "/v1/cosmetics?uuids=" + URLEncoder.encode(q.toString(), StandardCharsets.UTF_8))).GET());
		JsonObject players = res.getAsJsonObject("players");
		for (UUID u : uuids) {
			JsonElement e = players.get(u.toString());
			RemoteCosmetics.put(u, e == null || e.isJsonNull() ? null : parseLook(e.getAsJsonObject()));
		}
	}

	static final class HttpError extends Exception {
		final int code;

		HttpError(int code, String body) {
			super("HTTP " + code + (body.isBlank() ? "" : ": " + body.substring(0, Math.min(200, body.length()))));
			this.code = code;
		}
	}

	private static JsonObject send(HttpRequest.Builder req) throws Exception {
		HttpResponse<String> res = HTTP.send(req.timeout(Duration.ofSeconds(15)).header("User-Agent", "CosmeticStudio").build(),
				HttpResponse.BodyHandlers.ofString());
		if (res.statusCode() / 100 != 2) throw new HttpError(res.statusCode(), res.body());
		return JsonParser.parseString(res.body()).getAsJsonObject();
	}

	// ------------------------------------------------------------------ (de)serialisation

	/** What other players should see: per-slot cosmetics of the armor I am wearing now, plus my pet. */
	public static Look currentWornLook(Minecraft mc) {
		Look look = new Look();
		if (mc.player != null && CosmeticStudioClient.config().showMyCosmetics) {
			for (ArmorSlot s : ArmorSlot.values()) {
				ItemStack worn = mc.player.getItemBySlot(s.vanilla);
				ArmorCosmetic c = CosmeticStudioClient.store().get(SkyBlockItems.uuid(worn), s);
				if (!c.isEmpty()) look.armor.put(s, c);
			}
			look.pet = CosmeticStudioClient.store().pet();
		}
		return look;
	}

	public static JsonObject lookJson(Look look) {
		JsonObject root = new JsonObject();
		JsonObject armor = new JsonObject();
		for (Map.Entry<ArmorSlot, ArmorCosmetic> e : look.armor.entrySet()) {
			ArmorCosmetic c = e.getValue();
			if (c.isEmpty()) continue;
			JsonObject o = new JsonObject();
			o.addProperty("dye", c.dye());
			o.addProperty("rune", c.rune());
			o.addProperty("runeLevel", c.runeLevel());
			armor.add(e.getKey().name(), o);
		}
		root.add("armor", armor);
		PetCosmetic p = look.pet;
		JsonObject pet = new JsonObject();
		pet.addProperty("kind", p.isActive() ? p.kind.name() : "NONE");
		if (p.isActive()) {
			pet.addProperty("id", p.id);
			pet.addProperty("name", p.name);
			pet.addProperty("showName", p.showName);
			pet.addProperty("position", p.position.name());
			pet.addProperty("scale", p.scale);
			pet.addProperty("offsetX", p.offsetX);
			pet.addProperty("offsetY", p.offsetY);
			pet.addProperty("offsetZ", p.offsetZ);
			pet.addProperty("spin", p.spin);
			pet.addProperty("bob", p.bob);
		}
		root.add("pet", pet);
		return root;
	}

	public static Look parseLook(JsonObject o) {
		Look look = new Look();
		JsonObject armor = o.has("armor") && o.get("armor").isJsonObject() ? o.getAsJsonObject("armor") : new JsonObject();
		for (ArmorSlot s : ArmorSlot.values()) {
			if (!armor.has(s.name()) || !armor.get(s.name()).isJsonObject()) continue;
			JsonObject c = armor.getAsJsonObject(s.name());
			look.armor.put(s, new ArmorCosmetic(str(c, "dye"), str(c, "rune"), c.has("runeLevel") ? c.get("runeLevel").getAsInt() : 0));
		}
		PetCosmetic pet = new PetCosmetic();
		if (o.has("pet") && o.get("pet").isJsonObject()) {
			JsonObject p = o.getAsJsonObject("pet");
			try {
				pet.kind = PetCosmetic.Kind.valueOf(str(p, "kind") == null ? "NONE" : str(p, "kind"));
				pet.position = str(p, "position") == null ? PetPosition.FOLLOW : PetPosition.valueOf(str(p, "position"));
			} catch (IllegalArgumentException ignored) {
				pet.kind = PetCosmetic.Kind.NONE;
			}
			pet.id = str(p, "id");
			pet.name = str(p, "name");
			pet.showName = !p.has("showName") || p.get("showName").getAsBoolean();
			pet.scale = p.has("scale") ? p.get("scale").getAsFloat() : 1f;
			pet.offsetX = p.has("offsetX") ? p.get("offsetX").getAsFloat() : 0f;
			pet.offsetY = p.has("offsetY") ? p.get("offsetY").getAsFloat() : 0f;
			pet.offsetZ = p.has("offsetZ") ? p.get("offsetZ").getAsFloat() : 0f;
			pet.spin = !p.has("spin") || p.get("spin").getAsBoolean();
			pet.bob = !p.has("bob") || p.get("bob").getAsBoolean();
		}
		look.pet = pet.sanitize();
		return look;
	}

	private static @Nullable String str(JsonObject o, String k) {
		JsonElement e = o.get(k);
		return e == null || e.isJsonNull() ? null : e.getAsString();
	}
}
