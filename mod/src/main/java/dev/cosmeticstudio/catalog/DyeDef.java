package dev.cosmeticstudio.catalog;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** One SkyBlock dye from the catalog. Colours are 0xRRGGBB. */
public record DyeDef(String id, String name, @Nullable String rarity, int color, boolean animated, List<Integer> frames, int frameTicks,
		boolean frameTicksVerified) {

	/** Colour at the given client time, smoothly interpolating between animation frames. */
	public int colorAt(float ticks) {
		if (!animated || frames.isEmpty()) return color;
		float pos = (ticks / Math.max(1, frameTicks)) % frames.size();
		int i = (int) pos;
		return lerp(frames.get(i), frames.get((i + 1) % frames.size()), pos - i);
	}

	static int lerp(int a, int b, float t) {
		int r = (int) (((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
		int g = (int) (((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
		int bl = (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
		return (r << 16) | (g << 8) | bl;
	}

	/** Hue in [0,1) for sort-by-colour. */
	public float hue() {
		float r = ((color >> 16) & 0xFF) / 255f, g = ((color >> 8) & 0xFF) / 255f, b = (color & 0xFF) / 255f;
		float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
		if (d < 1e-4f) return -1f + max; // greys sort first, dark to light
		float h = max == r ? ((g - b) / d) % 6 : max == g ? (b - r) / d + 2 : (r - g) / d + 4;
		return ((h / 6f) + 1f) % 1f;
	}
}
