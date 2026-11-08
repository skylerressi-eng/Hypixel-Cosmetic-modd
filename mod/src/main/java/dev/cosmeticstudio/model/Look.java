package dev.cosmeticstudio.model;

import java.util.EnumMap;
import java.util.Map;

/**
 * A complete outfit: one cosmetic per armor slot plus the pet. Used for presets, for previews and as the payload
 * synced to other players (others only ever see per-slot data, never item UUIDs).
 */
public final class Look {
	public Map<ArmorSlot, ArmorCosmetic> armor = new EnumMap<>(ArmorSlot.class);
	public PetCosmetic pet = new PetCosmetic();

	public ArmorCosmetic get(ArmorSlot slot) {
		ArmorCosmetic c = armor.get(slot);
		return c == null ? ArmorCosmetic.NONE : c;
	}

	public Look copy() {
		Look l = new Look();
		l.armor.putAll(armor);
		l.pet = pet.copy();
		return l;
	}
}
