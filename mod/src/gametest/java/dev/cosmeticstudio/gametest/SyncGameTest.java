package dev.cosmeticstudio.gametest;

import dev.cosmeticstudio.CosmeticStudioClient;
import dev.cosmeticstudio.model.ArmorCosmetic;
import dev.cosmeticstudio.model.ArmorSlot;
import dev.cosmeticstudio.model.PetCosmetic;
import dev.cosmeticstudio.sync.SyncClient;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * Milestone 8 end to end: the mod logs in to a real sync server (Mojang join/hasJoined) and uploads what we wear.
 * Only runs when -PsyncUrl is given (needs a running server); otherwise it is skipped.
 */
public class SyncGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		String url = System.getProperty("cosmeticstudio.testSyncUrl");
		if (url == null) return;
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.waitFor(mc -> mc.player != null && mc.level != null);
			world.getServer().runCommand("item replace entity @p armor.chest with leather_chestplate");
			context.waitTicks(5);
			String session = System.getProperty("minecraft.api.session.host");
			if (session != null) {
				// The dev client has no real Mojang session; send the join to the stand-in session server instead.
				SyncClient.mojangJoin = (profile, token, serverId) -> post(session + "/session/minecraft/join",
						"{\"accessToken\":\"dev\",\"selectedProfile\":\"" + profile.toString().replace("-", "") + "\",\"serverId\":\"" + serverId + "\"}");
			}
			UUID me = context.computeOnClient(mc -> {
				CosmeticStudioClient.config().syncServerUrl = url;
				var store = CosmeticStudioClient.store();
				store.set(null, ArmorSlot.CHESTPLATE, ArmorCosmetic.NONE.withDye("DYE_PURE_BLACK"));
				PetCosmetic pet = new PetCosmetic();
				pet.kind = PetCosmetic.Kind.ENTITY;
				pet.id = "minecraft:fox";
				pet.name = "Birch Buddy";
				store.setPet(pet);
				return mc.player.getUUID();
			});
			String body = "";
			for (int i = 0; i < 20 && !body.contains("DYE_PURE_BLACK"); i++) {
				context.waitTicks(40);
				body = get(url + "/v1/cosmetics?uuids=" + me);
			}
			if (!body.contains("DYE_PURE_BLACK") || !body.contains("Birch Buddy")) {
				throw new AssertionError("server did not receive our look; status=" + SyncClient.status() + " body=" + body);
			}
			context.runOnClient(mc -> {
				CosmeticStudioClient.config().syncServerUrl = "";
				CosmeticStudioClient.store().clearAllArmor();
				CosmeticStudioClient.store().setPet(new PetCosmetic());
			});
		}
	}

	private static void post(String url, String json) throws Exception {
		HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(url)).header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(json)).build(), HttpResponse.BodyHandlers.discarding());
	}

	private static String get(String url) {
		try {
			return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofString()).body();
		} catch (Exception e) {
			return e.toString();
		}
	}
}
