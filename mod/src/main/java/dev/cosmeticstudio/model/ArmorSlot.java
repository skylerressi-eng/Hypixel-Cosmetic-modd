package dev.cosmeticstudio.model;

import net.minecraft.world.entity.EquipmentSlot;
import org.jspecify.annotations.Nullable;

/** The four armor slots, mapped to vanilla slots and to the rune slot names used in the catalog. */
public enum ArmorSlot {
	HELMET(EquipmentSlot.HEAD, "HELMET", "Helmet"),
	CHESTPLATE(EquipmentSlot.CHEST, "CHESTPLATE", "Chestplate"),
	LEGGINGS(EquipmentSlot.LEGS, "LEGGINGS", "Leggings"),
	BOOTS(EquipmentSlot.FEET, "BOOTS", "Boots");

	public final EquipmentSlot vanilla;
	public final String runeSlot;
	public final String label;

	ArmorSlot(EquipmentSlot vanilla, String runeSlot, String label) {
		this.vanilla = vanilla;
		this.runeSlot = runeSlot;
		this.label = label;
	}

	public static @Nullable ArmorSlot of(EquipmentSlot slot) {
		for (ArmorSlot s : values()) if (s.vanilla == slot) return s;
		return null;
	}
}
