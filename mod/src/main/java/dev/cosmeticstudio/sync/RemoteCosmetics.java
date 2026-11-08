package dev.cosmeticstudio.sync;

import dev.cosmeticstudio.model.Look;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

/** Cosmetics of other players, fetched from the sync server and cached with a TTL. */
public final class RemoteCosmetics {
	record Entry(@Nullable Look look, long fetchedAt) {}

	static final Map<UUID, Entry> CACHE = new ConcurrentHashMap<>();
	public static final long TTL_MS = 60_000;

	private RemoteCosmetics() {}

	/** Cached look for a player, or null if they have no cosmetics / do not use the mod / not fetched yet. */
	public static @Nullable Look get(UUID player) {
		Entry e = CACHE.get(player);
		return e == null ? null : e.look();
	}

	public static void put(UUID player, @Nullable Look look) {
		CACHE.put(player, new Entry(look, System.currentTimeMillis()));
	}

	public static boolean isFresh(UUID player) {
		Entry e = CACHE.get(player);
		return e != null && System.currentTimeMillis() - e.fetchedAt() < TTL_MS;
	}

	public static void clear() {
		CACHE.clear();
	}
}
