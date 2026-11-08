package dev.cosmeticstudio.model;

import org.jspecify.annotations.Nullable;

/** Cosmetic applied to one armor piece. Any field may be null (= not set). Immutable. */
public record ArmorCosmetic(@Nullable String dye, @Nullable String rune, int runeLevel) {
	public static final ArmorCosmetic NONE = new ArmorCosmetic(null, null, 0);

	public ArmorCosmetic withDye(@Nullable String dye) {
		return new ArmorCosmetic(dye, rune, runeLevel);
	}

	public ArmorCosmetic withRune(@Nullable String rune, int level) {
		return new ArmorCosmetic(dye, rune, rune == null ? 0 : Math.max(1, level));
	}

	public boolean isEmpty() {
		return dye == null && rune == null;
	}
}
