package dev.cosmeticstudio.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.cosmeticstudio.render.AppearanceHook;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Armor looks: after a player's render state is built, swap the worn armor/head items in the <b>render state only</b>
 * for the chosen look (e.g. any SkyBlock helmet). The player's real items are never changed.
 */
@Mixin(AvatarRenderer.class)
abstract class AvatarRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
			at = @At("TAIL"))
	private void cosmeticstudio$applyLooks(CallbackInfo ci, @Local(argsOnly = true) Avatar entity, @Local(argsOnly = true) AvatarRenderState state) {
		AppearanceHook.apply(entity, state);
	}
}
