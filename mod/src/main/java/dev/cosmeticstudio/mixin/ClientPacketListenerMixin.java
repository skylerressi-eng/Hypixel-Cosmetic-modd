package dev.cosmeticstudio.mixin;

import dev.cosmeticstudio.compat.Compat;
import dev.cosmeticstudio.skyblock.RealRuneHider;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "Hide my real runes": skips <b>displaying</b> incoming particle packets right around you while you wear/hold an item
 * with a real SkyBlock rune, so they don't mix with your cosmetic runes. Incoming only and display only: nothing is sent
 * or changed on the way to the server.
 */
@Mixin(ClientPacketListener.class)
abstract class ClientPacketListenerMixin {
	@Inject(method = "handleParticleEvent", at = @At("HEAD"), cancellable = true)
	private void cosmeticstudio$hideRealRuneParticles(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
		var pos = Compat.particlePos(packet);
		if (RealRuneHider.shouldHide(pos.x, pos.y, pos.z)) ci.cancel();
	}
}
