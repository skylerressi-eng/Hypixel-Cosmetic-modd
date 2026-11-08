package dev.cosmeticstudio.catalog;

import org.jspecify.annotations.Nullable;

/**
 * The look of one SkyBlock armor piece (from Hypixel's item list): the item it's drawn as, plus its leather colour
 * or player-head texture. Id in cosmetics is "sb:" + {@link #id}.
 */
public record AppearanceDef(String id, String name, String slot, @Nullable String rarity, String item, @Nullable Integer color,
		@Nullable String texture) {
	public static final String PREFIX = "sb:";
}
