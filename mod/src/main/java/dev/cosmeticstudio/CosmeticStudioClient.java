package dev.cosmeticstudio;

import net.fabricmc.api.ClientModInitializer;
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

	@Override
	public void onInitializeClient() {
		String version = FabricLoader.getInstance().getModContainer(MOD_ID)
				.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("dev");
		CosmeticCommand.register(version);
		LOGGER.info("Cosmetic Studio {} initialised", version);
	}
}
