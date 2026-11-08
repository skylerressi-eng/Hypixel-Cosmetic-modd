package dev.cosmeticstudio.store;

import com.google.gson.reflect.TypeToken;
import dev.cosmeticstudio.CosmeticStudioClient;
import dev.cosmeticstudio.model.ArmorCosmetic;
import dev.cosmeticstudio.model.ArmorSlot;
import dev.cosmeticstudio.model.Look;
import dev.cosmeticstudio.model.PetCosmetic;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * The local player's saved cosmetics, in config/cosmeticstudio/cosmetics.json and presets.json.
 * Armor cosmetics are keyed by SkyBlock item UUID so they follow that exact item between armor sets; items without a
 * UUID fall back to a per-slot entry.
 */
public final class LocalStore {
	private static final class Data {
		Map<String, ArmorCosmetic> byItem = new LinkedHashMap<>();
		Map<ArmorSlot, ArmorCosmetic> bySlot = new EnumMap<>(ArmorSlot.class);
		PetCosmetic pet = new PetCosmetic();
	}

	private final Path file;
	private final Path presetsFile;
	private Data data = new Data();
	private Map<String, Look> presets = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
	private int revision;

	public LocalStore(Path dir) {
		this.file = dir.resolve("cosmetics.json");
		this.presetsFile = dir.resolve("presets.json");
	}

	public void load() {
		try {
			if (Files.exists(file)) {
				Data d = JsonIO.GSON.fromJson(Files.readString(file), Data.class);
				if (d != null) data = d;
			}
		} catch (Exception e) {
			CosmeticStudioClient.LOGGER.warn("Invalid cosmetics.json, starting fresh: {}", e.toString());
		}
		if (data.byItem == null) data.byItem = new LinkedHashMap<>();
		if (data.bySlot == null) data.bySlot = new EnumMap<>(ArmorSlot.class);
		data.byItem.values().removeIf(c -> c == null || c.isEmpty());
		data.bySlot.values().removeIf(c -> c == null || c.isEmpty());
		data.pet = data.pet == null ? new PetCosmetic() : data.pet.sanitize();
		try {
			if (Files.exists(presetsFile)) {
				Type t = new TypeToken<Map<String, Look>>() {}.getType();
				Map<String, Look> p = JsonIO.GSON.fromJson(Files.readString(presetsFile), t);
				if (p != null) p.forEach((k, v) -> {
					if (v != null) {
						if (v.pet == null) v.pet = new PetCosmetic();
						v.pet.sanitize();
						if (v.armor == null) v.armor = new EnumMap<>(ArmorSlot.class);
						presets.put(k, v);
					}
				});
			}
		} catch (Exception e) {
			CosmeticStudioClient.LOGGER.warn("Invalid presets.json: {}", e.toString());
		}
		revision++;
	}

	public void save() {
		try {
			JsonIO.writeAtomic(file, data);
			JsonIO.writeAtomic(presetsFile, presets);
		} catch (Exception e) {
			CosmeticStudioClient.LOGGER.warn("Could not save cosmetics", e);
		}
		revision++;
	}

	/** Increases on every change; lets the sync client notice when to upload. */
	public int revision() {
		return revision;
	}

	/** Cosmetic for an armor piece: by item UUID if it has one, otherwise by slot. */
	public ArmorCosmetic get(@Nullable String itemUuid, ArmorSlot slot) {
		ArmorCosmetic c = itemUuid != null ? data.byItem.get(itemUuid) : data.bySlot.get(slot);
		return c == null ? ArmorCosmetic.NONE : c;
	}

	public void set(@Nullable String itemUuid, ArmorSlot slot, ArmorCosmetic cosmetic) {
		if (itemUuid != null) {
			if (cosmetic.isEmpty()) data.byItem.remove(itemUuid); else data.byItem.put(itemUuid, cosmetic);
		} else {
			if (cosmetic.isEmpty()) data.bySlot.remove(slot); else data.bySlot.put(slot, cosmetic);
		}
	}

	/** Removes every armor cosmetic (all items and slots). */
	public void clearAllArmor() {
		data.byItem.clear();
		data.bySlot.clear();
	}

	/** Removes every cosmetic rune (from all items, slots and nothing else); dyes are kept. Returns how many were removed. */
	public int clearAllRunes() {
		int[] n = {0};
		java.util.function.UnaryOperator<ArmorCosmetic> strip = c -> {
			if (c.rune() != null) n[0]++;
			return c.withRune(null, 0);
		};
		data.byItem.replaceAll((k, c) -> strip.apply(c));
		data.bySlot.replaceAll((k, c) -> strip.apply(c));
		data.byItem.values().removeIf(ArmorCosmetic::isEmpty);
		data.bySlot.values().removeIf(ArmorCosmetic::isEmpty);
		return n[0];
	}

	public PetCosmetic pet() {
		return data.pet.copy();
	}

	public void setPet(PetCosmetic pet) {
		data.pet = pet.copy().sanitize();
	}

	public Map<String, Look> presets() {
		return java.util.Collections.unmodifiableMap(presets);
	}

	public void savePreset(String name, Look look) {
		presets.put(name.strip(), look.copy());
	}

	public void deletePreset(String name) {
		presets.remove(name);
	}
}
