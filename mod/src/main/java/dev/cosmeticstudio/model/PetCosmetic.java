package dev.cosmeticstudio.model;

import org.jspecify.annotations.Nullable;

/**
 * Cosmetic pet. {@code kind} BLOCK uses a block registry id, ENTITY an entity-type registry id.
 * Mutable POJO so it serialises cleanly with Gson; copy() before handing out.
 */
public final class PetCosmetic {
	public enum Kind { NONE, BLOCK, ENTITY }

	public Kind kind = Kind.NONE;
	public @Nullable String id;
	public @Nullable String name;
	public boolean showName = true;
	public PetPosition position = PetPosition.FOLLOW;
	public float scale = 1.0f;
	public float offsetX, offsetY, offsetZ;
	public boolean spin = true;
	public boolean bob = true;
	public boolean hideRealPet = false;

	public static final int MAX_NAME = 24;

	public boolean isActive() {
		return kind != Kind.NONE && id != null;
	}

	public PetCosmetic copy() {
		PetCosmetic c = new PetCosmetic();
		c.kind = kind; c.id = id; c.name = name; c.showName = showName; c.position = position; c.scale = scale;
		c.offsetX = offsetX; c.offsetY = offsetY; c.offsetZ = offsetZ; c.spin = spin; c.bob = bob; c.hideRealPet = hideRealPet;
		return c;
	}

	/** Clamp values coming from disk or the network to sane ranges. */
	public PetCosmetic sanitize() {
		if (kind == null) kind = Kind.NONE;
		if (position == null) position = PetPosition.FOLLOW;
		scale = clamp(Float.isFinite(scale) ? scale : 1f, 0.25f, 2.5f);
		offsetX = clamp(offsetX, -1f, 1f); offsetY = clamp(offsetY, -1f, 1f); offsetZ = clamp(offsetZ, -1f, 1f);
		if (name != null) {
			name = name.replaceAll("[\\p{Cntrl}§]", "").strip();
			if (name.length() > MAX_NAME) name = name.substring(0, MAX_NAME);
			if (name.isEmpty()) name = null;
		}
		return this;
	}

	private static float clamp(float v, float lo, float hi) {
		return Float.isFinite(v) ? Math.max(lo, Math.min(hi, v)) : 0f;
	}
}
