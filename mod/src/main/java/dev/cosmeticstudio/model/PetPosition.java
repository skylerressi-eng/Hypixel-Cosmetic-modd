package dev.cosmeticstudio.model;

/** Where the cosmetic pet sits relative to its owner. */
public enum PetPosition {
	FOLLOW("Follow me"),
	SKYBLOCK_PET("Replace my SkyBlock pet"),
	LEFT_SHOULDER("Left shoulder"),
	RIGHT_SHOULDER("Right shoulder"),
	HEAD("On my head"),
	LEFT_ARM("Left arm"),
	RIGHT_ARM("Right arm"),
	BEHIND("Floating behind me");

	public final String label;

	PetPosition(String label) {
		this.label = label;
	}
}
