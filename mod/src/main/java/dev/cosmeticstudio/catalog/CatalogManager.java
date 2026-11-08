package dev.cosmeticstudio.catalog;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.cosmeticstudio.CosmeticStudioClient;
import dev.cosmeticstudio.config.ModConfig;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;

/**
 * Loads the catalog: bundled copy first, then a newer cached copy if present, then refreshes from
 * {@link ModConfig#catalogUrl} in the background (so new dyes/runes work without a mod update).
 * The refresh only talks to the configured catalog host, never to Hypixel.
 */
public final class CatalogManager {
	private static final String BUNDLED_COSMETICS = "/assets/cosmeticstudio/catalog/cosmetics.json";
	private static final String BUNDLED_EFFECTS = "/assets/cosmeticstudio/catalog/rune_effects.json";
	private static final String BUNDLED_APPEARANCES = "/assets/cosmeticstudio/catalog/appearances.json";
	private static @Nullable JsonObject appearances;

	private static JsonObject appearances() {
		if (appearances == null) appearances = readResource(BUNDLED_APPEARANCES);
		return appearances;
	}

	private static volatile Catalog current = Catalog.empty();

	private CatalogManager() {}

	public static Catalog get() {
		return current;
	}

	public static void load(Path cacheDir) {
		JsonObject effects = mergeEffects(readResource(BUNDLED_EFFECTS), cacheDir);
		Path cached = cacheDir.resolve("catalog-cache.json");
		try {
			current = Catalog.parse(readResource(BUNDLED_COSMETICS), effects, appearances());
			if (Files.exists(cached)) current = Catalog.parse(read(cached), effects, appearances());
		} catch (Exception e) {
			CosmeticStudioClient.LOGGER.warn("Catalog cache unusable, using bundled catalog: {}", e.toString());
		}
		CosmeticStudioClient.LOGGER.info("Catalog: {} dyes, {} runes, {} rune effects, {} armor looks", current.dyes().size(), current.runes().size(),
				current.effects().size(), current.appearances().size());
	}

	/** Lets users add/override rune effects locally in config/cosmeticstudio/rune_effects.json. */
	private static JsonObject mergeEffects(JsonObject bundled, Path dir) {
		Path local = dir.resolve("rune_effects.json");
		if (!Files.exists(local)) return bundled;
		try {
			JsonObject merged = bundled.deepCopy();
			JsonObject user = read(local);
			if (user.has("effects")) user.getAsJsonObject("effects").entrySet().forEach(e -> merged.getAsJsonObject("effects").add(e.getKey(), e.getValue()));
			return merged;
		} catch (Exception e) {
			CosmeticStudioClient.LOGGER.warn("Ignoring invalid rune_effects.json: {}", e.toString());
			return bundled;
		}
	}

	public static CompletableFuture<Void> refreshAsync(Path cacheDir, String url) {
		if (url == null || url.isBlank()) return CompletableFuture.completedFuture(null);
		HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();
		HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).header("User-Agent", "CosmeticStudio").GET().build();
		return client.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenAccept(resp -> {
			if (resp.statusCode() != 200) {
				CosmeticStudioClient.LOGGER.info("Catalog refresh skipped (HTTP {})", resp.statusCode());
				return;
			}
			JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
			Catalog fresh = Catalog.parse(json, mergeEffects(readResource(BUNDLED_EFFECTS), cacheDir), appearances()); // validates before caching
			if (fresh.contentHash().equals(current.contentHash())) return;
			try {
				Files.createDirectories(cacheDir);
				Files.writeString(cacheDir.resolve("catalog-cache.json"), resp.body());
			} catch (IOException e) {
				CosmeticStudioClient.LOGGER.warn("Could not write catalog cache", e);
			}
			current = fresh;
			CosmeticStudioClient.LOGGER.info("Catalog refreshed: {} dyes, {} runes", fresh.dyes().size(), fresh.runes().size());
		}).exceptionally(t -> {
			CosmeticStudioClient.LOGGER.info("Catalog refresh failed: {}", t.toString());
			return null;
		});
	}

	private static JsonObject read(Path p) throws IOException {
		return JsonParser.parseString(Files.readString(p)).getAsJsonObject();
	}

	private static JsonObject readResource(String path) {
		try (@Nullable InputStream in = CatalogManager.class.getResourceAsStream(path)) {
			if (in == null) throw new IllegalStateException("Missing resource " + path);
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}
}
