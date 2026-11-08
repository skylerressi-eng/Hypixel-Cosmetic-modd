package dev.cosmeticstudio.model;

import org.jspecify.annotations.Nullable;

/**
 * Cosmetic applied to one armor piece. Any field may be null (= not set). Immutable.
 *
 * @param look         appearance override: "sb:SUPERIOR_DRAGON_HELMET" (any SkyBlock armor piece) or a vanilla item id
 *                     such as "minecraft:carved_pumpkin"; null keeps the real item's look
 * @param hideRealRune hide the real SkyBlock rune on this piece (local only, never synced)
 */
public record ArmorCosmetic(@Nullable String dye, @Nullable String rune, int runeLevel, @Nullable String look, boolean hideRealRune) {
	public static final ArmorCosmetic NONE = new ArmorCosmetic(null, null, 0, null, false);

	public ArmorCosmetic(@Nullable String dye, @Nullable String rune, int runeLevel) {
		this(dye, rune, runeLevel, null, false);
	}

	public ArmorCosmetic withDye(@Nullable String dye) {
		return new ArmorCosmetic(dye, rune, runeLevel, look, hideRealRune);
	}

	public ArmorCosmetic withRune(@Nullable String rune, int level) {
		return new ArmorCosmetic(dye, rune, rune == null ? 0 : Math.max(1, level), look, hideRealRune);
	}

	public ArmorCosmetic withLook(@Nullable String look) {
		return new ArmorCosmetic(dye, rune, runeLevel, look, hideRealRune);
	}

	public ArmorCosmetic withHideRealRune(boolean hide) {
		return new ArmorCosmetic(dye, rune, runeLevel, look, hide);
	}

	public boolean isEmpty() {
		return dye == null && rune == null && look == null && !hideRealRune;
	}
}
