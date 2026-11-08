package dev.cosmeticstudio.mixin;

import dev.cosmeticstudio.resolve.CosmeticResolver;
import net.minecraft.client.color.item.Dye;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Item icons (inventory, hotbar, GUI): leather armor models tint through {@link Dye}; return the cosmetic colour instead.
 */
@Mixin(Dye.class)
abstract class DyeTintMixin {
	@Inject(method = "calculate", at = @At("HEAD"), cancellable = true)
	private void cosmeticstudio$dyeIcon(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner, CallbackInfoReturnable<Integer> cir) {
		Integer override = CosmeticResolver.dyeColor(stack, owner);
		if (override != null) cir.setReturnValue(0xFF000000 | override);
	}
}
