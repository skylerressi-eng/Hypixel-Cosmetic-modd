package dev.cosmeticstudio.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.cosmeticstudio.render.ArmorDyeHook;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Worn armor: when the armor layer asks for the leather colour, substitute the cosmetic dye (if any).
 * Vanilla only applies that colour to layers marked dyeable, so non-leather armor is unaffected, exactly like SkyBlock.
 * Purely a render-time change; the item itself is never modified.
 */
@Mixin(EquipmentLayerRenderer.class)
abstract class EquipmentLayerRendererMixin {
	@WrapOperation(
			method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/component/DyedItemColor;getOrDefault(Lnet/minecraft/world/item/ItemStack;I)I"))
	private int cosmeticstudio$dyeWornArmor(ItemStack stack, int fallback, Operation<Integer> original, @Local(argsOnly = true) Object state) {
		Integer override = ArmorDyeHook.wornColor(state, stack);
		return override != null ? 0xFF000000 | override : original.call(stack, fallback);
	}
}
