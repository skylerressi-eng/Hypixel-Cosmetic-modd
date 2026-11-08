package dev.cosmeticstudio;

import dev.cosmeticstudio.catalog.CatalogManager;
import dev.cosmeticstudio.config.ModConfig;
import dev.cosmeticstudio.skyblock.Location;
import dev.cosmeticstudio.store.LocalStore;
import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client entrypoint. Cosmetic Studio is client-only and purely visual:
 * it never sends chat or alters packets to the server you are playing on.
 */
public final class CosmeticStudioClient implements ClientModInitializer {
	public static final String MOD_ID = "cosmeticstudio";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static ModConfig config;
	private static LocalStore store;
	private static Path dir;

	public static ModConfig config() { return config; }
	public static LocalStore store() { return store; }
	public static Path dir() { return dir; }

	@Override
	public void onInitializeClient() {
		String version = FabricLoader.getInstance().getModContainer(MOD_ID)
				.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("dev");
		dir = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);
		config = ModConfig.load(dir);
		store = new LocalStore(dir);
		store.load();
		CatalogManager.load(dir);
		CatalogManager.refreshAsync(dir, config.catalogUrl);

		ClientTickEvents.END_CLIENT_TICK.register(Location::tick);
		CosmeticCommand.register(version);
		LOGGER.info("Cosmetic Studio {} initialised", version);
	}
}
