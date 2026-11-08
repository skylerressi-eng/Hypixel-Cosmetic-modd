package dev.cosmeticstudio.skyblock;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.jspecify.annotations.Nullable;

/** Read-only helpers for SkyBlock item data (Hypixel stores it in the item's custom_data component). */
public final class SkyBlockItems {
	private SkyBlockItems() {}

	/** The SkyBlock item UUID ("uuid" in custom data), or null if the item has none. */
	public static @Nullable String uuid(ItemStack stack) {
		String s = customString(stack, "uuid");
		return s == null || s.isEmpty() ? null : s;
	}

	/** The SkyBlock item id, e.g. "SUPERIOR_DRAGON_CHESTPLATE". */
	public static @Nullable String skyblockId(ItemStack stack) {
		String s = customString(stack, "id");
		return s == null || s.isEmpty() ? null : s;
	}

	/** CustomData is immutable, so its parsed "uuid"/"id" can be cached per instance (avoids copying NBT every frame). */
	private record Ids(String uuid, String id, String runes) {}

	private static final java.util.Map<CustomData, Ids> CACHE = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

	private static @Nullable String customString(ItemStack stack, String key) {
		if (stack.isEmpty()) return null;
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null || data.isEmpty()) return null;
		Ids ids = CACHE.computeIfAbsent(data, d -> {
			CompoundTag tag = d.copyTag();
			StringBuilder runes = new StringBuilder();
			tag.getCompound("runes").ifPresent(r -> r.keySet().forEach(k -> runes.append(runes.isEmpty() ? "" : ", ")
					.append(k).append(' ').append(r.getIntOr(k, 1))));
			return new Ids(tag.getStringOr("uuid", ""), tag.getStringOr("id", ""), runes.toString());
		});
		return switch (key) { case "uuid" -> ids.uuid(); case "runes" -> ids.runes(); default -> ids.id(); };
	}

	/** The real SkyBlock rune(s) applied to this item by Hypixel, e.g. "RAINBOW 3", or null if none. */
	public static @Nullable String realRunes(ItemStack stack) {
		String s = customString(stack, "runes");
		return s == null || s.isEmpty() ? null : s;
	}

	/** Catalog id of the item's real rune ("RAINBOW" in item data -> "RAINBOW_RUNE"), or null. */
	public static @Nullable String realRuneId(ItemStack stack) {
		String r = realRunes(stack);
		if (r == null) return null;
		String first = r.split(",")[0].strip();
		int sp = first.lastIndexOf(' ');
		return (sp > 0 ? first.substring(0, sp) : first) + "_RUNE";
	}

	/** Level of the item's real rune, or 0. */
	public static int realRuneLevel(ItemStack stack) {
		String r = realRunes(stack);
		if (r == null) return 0;
		String first = r.split(",")[0].strip();
		try {
			return Integer.parseInt(first.substring(first.lastIndexOf(' ') + 1));
		} catch (NumberFormatException e) {
			return 1;
		}
	}

	/** True if the local player wears or holds anything with a real SkyBlock rune. */
	public static boolean wearingRealRunes(net.minecraft.world.entity.player.Player p) {
		for (var slot : net.minecraft.world.entity.EquipmentSlot.values()) {
			if (realRunes(p.getItemBySlot(slot)) != null) return true;
		}
		return false;
	}

	/** True if dyes can recolour this piece (leather-based armor), the same rule SkyBlock uses. */
	public static boolean isDyeable(ItemStack stack) {
		return stack.is(Items.LEATHER_HELMET) || stack.is(Items.LEATHER_CHESTPLATE) || stack.is(Items.LEATHER_LEGGINGS)
				|| stack.is(Items.LEATHER_BOOTS) || stack.has(DataComponents.DYED_COLOR);
	}
}
