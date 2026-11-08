package dev.cosmeticstudio.catalog;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Immutable snapshot of every dye, rune and rune effect the mod knows about. */
public final class Catalog {
	public static final int SCHEMA_VERSION = 1;

	private final String contentHash;
	private final Map<String, DyeDef> dyes;
	private final Map<String, RuneDef> runes;
	private final Map<String, RuneEffect> effects;
	private final Map<String, AppearanceDef> appearances;

	private Catalog(String contentHash, Map<String, DyeDef> dyes, Map<String, RuneDef> runes, Map<String, RuneEffect> effects,
			Map<String, AppearanceDef> appearances) {
		this.contentHash = contentHash;
		this.dyes = Collections.unmodifiableMap(dyes);
		this.runes = Collections.unmodifiableMap(runes);
		this.effects = Collections.unmodifiableMap(effects);
		this.appearances = Collections.unmodifiableMap(appearances);
	}

	public static Catalog empty() {
		return new Catalog("", Map.of(), Map.of(), Map.of(), Map.of());
	}

	/** SkyBlock armor looks keyed by Hypixel item id (without the "sb:" prefix). */
	public Map<String, AppearanceDef> appearances() { return appearances; }
	public @Nullable AppearanceDef appearance(@Nullable String id) {
		return id == null ? null : appearances.get(id.startsWith(AppearanceDef.PREFIX) ? id.substring(AppearanceDef.PREFIX.length()) : id);
	}

	public String contentHash() { return contentHash; }
	public Map<String, DyeDef> dyes() { return dyes; }
	public Map<String, RuneDef> runes() { return runes; }
	public Map<String, RuneEffect> effects() { return effects; }
	public @Nullable DyeDef dye(@Nullable String id) { return id == null ? null : dyes.get(id); }
	public @Nullable RuneDef rune(@Nullable String id) { return id == null ? null : runes.get(id); }
	public @Nullable RuneEffect effect(@Nullable String id) { return id == null ? null : effects.get(id); }

	/** Parses cosmetics.json plus (optional) rune_effects.json. Throws on schema mismatch so callers can fall back. */
	public static Catalog parse(JsonObject cosmetics, @Nullable JsonObject runeEffects) {
		return parse(cosmetics, runeEffects, null);
	}

	public static Catalog parse(JsonObject cosmetics, @Nullable JsonObject runeEffects, @Nullable JsonObject appearancesJson) {
		int schema = cosmetics.get("schemaVersion").getAsInt();
		if (schema != SCHEMA_VERSION) throw new IllegalArgumentException("Unsupported catalog schema " + schema);

		Map<String, DyeDef> dyes = new LinkedHashMap<>();
		for (JsonElement e : cosmetics.getAsJsonArray("dyes")) {
			JsonObject o = e.getAsJsonObject();
			if (o.get("hex") == null || o.get("hex").isJsonNull()) continue;
			List<Integer> frames = new ArrayList<>();
			if (o.has("frames")) for (JsonElement f : o.getAsJsonArray("frames")) frames.add(hex(f.getAsString()));
			dyes.put(str(o, "id"), new DyeDef(str(o, "id"), str(o, "name"), opt(o, "rarity"), hex(str(o, "hex")),
					o.has("animated") && o.get("animated").getAsBoolean(), List.copyOf(frames),
					o.has("frameTicks") ? Math.max(1, o.get("frameTicks").getAsInt()) : 1,
					o.has("frameTicksVerified") && o.get("frameTicksVerified").getAsBoolean()));
		}

		Map<String, RuneDef> runes = new LinkedHashMap<>();
		for (JsonElement e : cosmetics.getAsJsonArray("runes")) {
			JsonObject o = e.getAsJsonObject();
			List<RuneDef.Level> levels = new ArrayList<>();
			for (JsonElement l : o.getAsJsonArray("levels")) {
				JsonObject lo = l.getAsJsonObject();
				levels.add(new RuneDef.Level(lo.get("level").getAsInt(), opt(lo, "rarity")));
			}
			runes.put(str(o, "id"), new RuneDef(str(o, "id"), str(o, "name"), opt(o, "color"), opt(o, "slot"), str(o, "slotLabel"),
					str(o, "description"), List.copyOf(levels), o.has("unique") && o.get("unique").getAsBoolean()));
		}

		Map<String, RuneEffect> effects = new LinkedHashMap<>();
		if (runeEffects != null && runeEffects.has("effects")) {
			for (Map.Entry<String, JsonElement> entry : runeEffects.getAsJsonObject("effects").entrySet()) {
				JsonObject o = entry.getValue().getAsJsonObject();
				List<RuneEffect.Emitter> emitters = new ArrayList<>();
				JsonArray arr = o.getAsJsonArray("emitters");
				if (arr != null) for (JsonElement em : arr) {
					JsonObject eo = em.getAsJsonObject();
					emitters.add(new RuneEffect.Emitter(str(eo, "particle"),
							RuneEffect.Trigger.valueOf(eo.has("trigger") ? str(eo, "trigger") : "AMBIENT"),
							RuneEffect.Shape.valueOf(eo.has("shape") ? str(eo, "shape") : "POINT"),
							eo.has("count") ? eo.get("count").getAsInt() : 1,
							eo.has("interval") ? Math.max(1, eo.get("interval").getAsInt()) : 10,
							eo.has("spread") ? eo.get("spread").getAsFloat() : 0.3f,
							eo.has("speed") ? eo.get("speed").getAsFloat() : 0f,
							eo.has("color") ? ("rainbow".equalsIgnoreCase(str(eo, "color")) ? RuneEffect.RAINBOW : hex(str(eo, "color"))) : null));
				}
				effects.put(entry.getKey(), new RuneEffect(entry.getKey(), o.has("verified") && o.get("verified").getAsBoolean(),
						o.has("source") ? str(o, "source") : "unknown", List.copyOf(emitters)));
			}
		}
		Map<String, AppearanceDef> appearances = new LinkedHashMap<>();
		if (appearancesJson != null && appearancesJson.has("appearances")) {
			for (JsonElement e : appearancesJson.getAsJsonArray("appearances")) {
				JsonObject o = e.getAsJsonObject();
				appearances.put(str(o, "id"), new AppearanceDef(str(o, "id"), str(o, "name"), str(o, "slot"), opt(o, "rarity"), str(o, "item"),
						o.has("color") ? hex(str(o, "color")) : null, opt(o, "texture")));
			}
		}
		return new Catalog(cosmetics.has("contentHash") ? str(cosmetics, "contentHash") : "", dyes, runes, effects, appearances);
	}

	private static String str(JsonObject o, String k) {
		return o.get(k).getAsString();
	}

	private static @Nullable String opt(JsonObject o, String k) {
		JsonElement e = o.get(k);
		return e == null || e.isJsonNull() ? null : e.getAsString();
	}

	static int hex(String s) {
		return Integer.parseInt(s.startsWith("#") ? s.substring(1) : s, 16) & 0xFFFFFF;
	}
}
