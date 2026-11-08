package dev.cosmeticstudio.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.cosmeticstudio.CosmeticStudioClient;
import java.nio.file.Files;
import java.nio.file.Path;

/** User settings, stored as config/cosmeticstudio/config.json. */
public final class ModConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	// Visibility
	public boolean showMyCosmetics = true;
	public boolean showOthersCosmetics = true;
	public boolean showOthersPets = true;
	public boolean showOthersPetNames = true;

	// Runes / particles
	public boolean particles = true;
	public float particleDensity = 1.0f; // 0..2
	public boolean disableParticlesInDungeons = true;
	public boolean disableParticlesInKuudra = true;
	public boolean allowAnyRuneOnAnyPiece = false;
	public boolean showUnverifiedRuneEffects = true;
	/** Hide the server's particles from runes really applied to your items, so they don't mix with cosmetic runes. */
	public boolean hideRealRunes = false;

	// Pets
	public boolean petAnimations = true;

	// Dyes
	public boolean onlyInSkyBlock = false;

	// Network
	public boolean syncEnabled = true;
	public String syncServerUrl = "";
	public String catalogUrl = "https://raw.githubusercontent.com/skylerressi-eng/Hypixel-Cosmetic-modd/HEAD/catalog/cosmetics.json";

	/** Bumped when defaults change so existing configs can be migrated. */
	public int configVersion;

	private transient Path file;

	public static ModConfig load(Path dir) {
		Path f = dir.resolve("config.json");
		ModConfig c = new ModConfig();
		try {
			if (Files.exists(f)) c = GSON.fromJson(Files.readString(f), ModConfig.class);
		} catch (Exception e) {
			CosmeticStudioClient.LOGGER.warn("Invalid config.json, using defaults: {}", e.toString());
			c = new ModConfig();
		}
		c.file = f;
		if (c.configVersion < 2) {
			// v2: rune effects are shown by default (they were hidden behind this switch before, so runes looked broken).
			c.showUnverifiedRuneEffects = true;
			c.configVersion = 2;
		}
		c.particleDensity = Math.max(0f, Math.min(2f, c.particleDensity));
		if (c.syncServerUrl == null) c.syncServerUrl = "";
		if (c.catalogUrl == null) c.catalogUrl = "";
		c.save();
		return c;
	}

	public void save() {
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(this));
		} catch (Exception e) {
			CosmeticStudioClient.LOGGER.warn("Could not save config", e);
		}
	}
}
