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

	/** True if a worn piece with a real rune has it deselected, or has a different cosmetic rune picked. */
	static boolean pieceWantsHidden(Player p) {
		for (dev.cosmeticstudio.model.ArmorSlot slot : dev.cosmeticstudio.model.ArmorSlot.values()) {
			var stack = p.getItemBySlot(slot.vanilla);
			if (SkyBlockItems.realRunes(stack) == null) continue;
			var c = dev.cosmeticstudio.resolve.CosmeticResolver.worn(p, slot, stack);
			if (c.hideRealRune() || c.rune() != null) return true;
		}
		return false;
	}

	public static boolean shouldHide(double x, double y, double z) {
		if (CosmeticStudioClient.config() == null) return false;
		Player p = Minecraft.getInstance().player;
		if (p == null) return false;
		boolean global = CosmeticStudioClient.config().hideRealRunes && SkyBlockItems.wearingRealRunes(p);
		if (!global && !pieceWantsHidden(p)) return false;
		double dx = x - p.getX(), dz = z - p.getZ();
		boolean hide = dx * dx + dz * dz <= RADIUS * RADIUS && y >= p.getY() - 0.6 && y <= p.getY() + p.getBbHeight() + 1.2;
		if (hide) hiddenCount++;
		return hide;
	}
}
