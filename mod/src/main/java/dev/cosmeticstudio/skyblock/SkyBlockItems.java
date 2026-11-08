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
	private record Ids(String uuid, String id) {}

	private static final java.util.Map<CustomData, Ids> CACHE = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

	private static @Nullable String customString(ItemStack stack, String key) {
		if (stack.isEmpty()) return null;
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null || data.isEmpty()) return null;
		Ids ids = CACHE.computeIfAbsent(data, d -> {
			CompoundTag tag = d.copyTag();
			return new Ids(tag.getStringOr("uuid", ""), tag.getStringOr("id", ""));
		});
		return "uuid".equals(key) ? ids.uuid() : ids.id();
	}

	/** True if dyes can recolour this piece (leather-based armor), the same rule SkyBlock uses. */
	public static boolean isDyeable(ItemStack stack) {
		return stack.is(Items.LEATHER_HELMET) || stack.is(Items.LEATHER_CHESTPLATE) || stack.is(Items.LEATHER_LEGGINGS)
				|| stack.is(Items.LEATHER_BOOTS) || stack.has(DataComponents.DYED_COLOR);
	}
}
