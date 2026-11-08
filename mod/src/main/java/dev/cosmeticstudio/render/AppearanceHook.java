package dev.cosmeticstudio.render;

import dev.cosmeticstudio.model.ArmorCosmetic;
import dev.cosmeticstudio.model.ArmorSlot;
import dev.cosmeticstudio.resolve.CosmeticResolver;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractSkullBlock;

/** Replaces worn-armor items in a player's render state with their chosen looks (render-only). */
public final class AppearanceHook {
	private AppearanceHook() {}

	public static void apply(LivingEntity entity, AvatarRenderState state) {
		for (ArmorSlot slot : ArmorSlot.values()) {
			ItemStack real = entity.getItemBySlot(slot.vanilla);
			ArmorCosmetic c = CosmeticResolver.worn(entity, slot, real);
			if (c.look() == null) continue;
			ItemStack look = Appearances.stack(c.look());
			if (look.isEmpty()) continue;
			ItemStack shown = HumanoidArmorLayer.shouldRender(look, slot.vanilla) ? look.copy() : ItemStack.EMPTY;
			switch (slot) {
				case HELMET -> {
					state.headEquipment = shown;
					// Heads/skulls and non-armor items (pumpkins, blocks) are drawn by the head layer instead.
					if (look.getItem() instanceof BlockItem bi && bi.getBlock() instanceof AbstractSkullBlock skull) {
						state.wornHeadType = skull.getType();
						state.wornHeadProfile = look.get(DataComponents.PROFILE);
						state.headItem.clear();
					} else {
						state.wornHeadType = null;
						state.wornHeadProfile = null;
						if (!HumanoidArmorLayer.shouldRender(look, EquipmentSlot.HEAD)) {
							Minecraft.getInstance().getItemModelResolver().updateForLiving(state.headItem, look, ItemDisplayContext.HEAD, entity);
						} else {
							state.headItem.clear();
						}
					}
				}
				case CHESTPLATE -> state.chestEquipment = shown;
				case LEGGINGS -> state.legsEquipment = shown;
				case BOOTS -> state.feetEquipment = shown;
			}
		}
	}
}
