package dev.cosmeticstudio.catalog;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** SkyBlock rarity order and colours, used for sorting and labels. */
public final class Rarity {
	private static final List<String> ORDER = List.of("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "DIVINE", "SPECIAL", "VERY SPECIAL");
	private static final int[] COLORS = {0xFFFFFF, 0x55FF55, 0x5555FF, 0xAA00AA, 0xFFAA00, 0xFF55FF, 0x55FFFF, 0xFF5555, 0xFF5555};

	private Rarity() {}

	public static int rank(@Nullable String rarity) {
		return rarity == null ? -1 : ORDER.indexOf(rarity);
	}

	public static int color(@Nullable String rarity) {
		int r = rank(rarity);
		return r < 0 ? 0xAAAAAA : COLORS[r];
	}

	public static List<String> all() {
		return ORDER;
	}
}
