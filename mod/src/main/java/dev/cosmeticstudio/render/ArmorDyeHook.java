package dev.cosmeticstudio.render;

import dev.cosmeticstudio.catalog.CatalogManager;
import dev.cosmeticstudio.catalog.DyeDef;
import dev.cosmeticstudio.model.ArmorCosmetic;
import dev.cosmeticstudio.model.ArmorSlot;
import dev.cosmeticstudio.resolve.CosmeticResolver;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Glue between the armor-layer mixin and the resolver: finds which player a render state belongs to. */
public final class ArmorDyeHook {
	private ArmorDyeHook() {}

	public static @Nullable Integer wornColor(Object state, ItemStack stack) {
		if (!(state instanceof AvatarRenderState avatar)) return null; // only players (not armor stands / mobs)
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return null;
		Entity wearer = mc.level.getEntity(avatar.id);
		ArmorSlot slot = CosmeticResolver.slotOf(stack);
		if (wearer == null || slot == null) return null;
		ArmorCosmetic c = CosmeticResolver.worn(wearer, slot, stack);
		DyeDef dye = CatalogManager.get().dye(c.dye());
		return dye == null ? null : dye.colorAt(CosmeticResolver.clock());
	}
}
