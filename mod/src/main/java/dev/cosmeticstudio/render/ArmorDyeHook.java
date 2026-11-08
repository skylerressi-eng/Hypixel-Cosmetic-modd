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
		// Look up the cosmetic by the REAL worn item (the drawn stack may be a look override without the item's UUID).
		ItemStack real = wearer instanceof net.minecraft.world.entity.LivingEntity living ? living.getItemBySlot(slot.vanilla) : stack;
		ArmorCosmetic c = CosmeticResolver.worn(wearer, slot, real);
		DyeDef dye = CatalogManager.get().dye(c.dye());
		return dye == null ? null : dye.colorAt(CosmeticResolver.clock());
	}
}
