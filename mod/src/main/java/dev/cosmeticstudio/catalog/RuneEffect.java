package dev.cosmeticstudio.catalog;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Particle description for one rune, from catalog/rune_effects.json.
 * {@code verified=false} entries are approximations and only shown when the user opts in.
 */
public record RuneEffect(String runeId, boolean verified, String source, List<Emitter> emitters) {

	/** When the emitter fires. */
	public enum Trigger { AMBIENT, WALK, ATTACK }

	/** Spatial pattern relative to the armor piece. */
	public enum Shape { POINT, BURST, RING, SPIRAL, TRAIL, RAIN }

	/**
	 * @param particle vanilla particle id, e.g. "minecraft:note"; "minecraft:dust" uses {@code color}
	 * @param interval ticks between emissions at level I (higher levels emit more often)
	 */
	public record Emitter(String particle, Trigger trigger, Shape shape, int count, int interval, float spread, float speed,
			@Nullable Integer color) {}
}
