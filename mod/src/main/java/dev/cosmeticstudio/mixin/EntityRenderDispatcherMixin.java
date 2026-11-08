package dev.cosmeticstudio.mixin;

import dev.cosmeticstudio.skyblock.RealPetHider;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "Hide my real SkyBlock pet": skips <b>drawing</b> the two armor stands that make up the local player's own pet.
 * Only affects what this client renders; the entities still exist and nothing is sent to the server.
 */
@Mixin(EntityRenderDispatcher.class)
abstract class EntityRenderDispatcherMixin {
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private <E extends Entity> void cosmeticstudio$hideRealPet(E entity, Frustum culler, double camX, double camY, double camZ, float partialTicks,
			CallbackInfoReturnable<Boolean> cir) {
		if (RealPetHider.isHidden(entity)) cir.setReturnValue(false);
	}
}
