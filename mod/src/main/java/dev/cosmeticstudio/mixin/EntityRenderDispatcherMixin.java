package dev.cosmeticstudio.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.cosmeticstudio.skyblock.RealPetHider;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "Hide my real SkyBlock pet": skips <b>drawing</b> the two armor stands that make up the local player's own pet.
 * Only affects what this client renders; the entities still exist and nothing is sent to the server.
 * Only the entity argument is captured so the same mixin matches every supported version's shouldRender signature.
 */
@Mixin(EntityRenderDispatcher.class)
abstract class EntityRenderDispatcherMixin {
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private void cosmeticstudio$hideRealPet(CallbackInfoReturnable<Boolean> cir, @Local(argsOnly = true) Entity entity) {
		if (RealPetHider.isHidden(entity)) cir.setReturnValue(false);
	}
}
