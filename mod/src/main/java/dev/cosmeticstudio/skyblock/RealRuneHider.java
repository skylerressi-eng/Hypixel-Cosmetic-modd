package dev.cosmeticstudio.skyblock;

import dev.cosmeticstudio.CosmeticStudioClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/**
 * Decides whether a server particle is (very likely) from your own real rune. Real rune particles are spawned by the
 * server on your body or right next to it, so while you wear/hold a runed item we hide server particles inside a small
 * box around you. Our own cosmetic rune particles are spawned locally and are never affected.
 */
public final class RealRuneHider {
	private static final double RADIUS = 1.6;
	private static int hiddenCount;

	/** Number of server particle packets hidden this session (tests/debug). */
	public static int hiddenCount() {
		return hiddenCount;
	}

	private RealRuneHider() {}

	public static boolean shouldHide(double x, double y, double z) {
		if (CosmeticStudioClient.config() == null || !CosmeticStudioClient.config().hideRealRunes) return false;
		Player p = Minecraft.getInstance().player;
		if (p == null || !SkyBlockItems.wearingRealRunes(p)) return false;
		double dx = x - p.getX(), dz = z - p.getZ();
		boolean hide = dx * dx + dz * dz <= RADIUS * RADIUS && y >= p.getY() - 0.6 && y <= p.getY() + p.getBbHeight() + 1.2;
		if (hide) hiddenCount++;
		return hide;
	}
}
