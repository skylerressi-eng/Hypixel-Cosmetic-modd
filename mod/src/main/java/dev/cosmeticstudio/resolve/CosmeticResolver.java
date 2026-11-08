package dev.cosmeticstudio.resolve;

import dev.cosmeticstudio.CosmeticStudioClient;
import dev.cosmeticstudio.catalog.CatalogManager;
import dev.cosmeticstudio.catalog.DyeDef;
import dev.cosmeticstudio.config.ModConfig;
import dev.cosmeticstudio.model.ArmorCosmetic;
import dev.cosmeticstudio.model.ArmorSlot;
import dev.cosmeticstudio.model.Look;
import dev.cosmeticstudio.model.PetCosmetic;
import dev.cosmeticstudio.skyblock.Location;
import dev.cosmeticstudio.skyblock.SkyBlockItems;
import dev.cosmeticstudio.store.LocalStore;
import dev.cosmeticstudio.sync.RemoteCosmetics;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Single place that answers "which cosmetic applies here?". Order of precedence for the local player:
 * GUI preview (hover) &gt; GUI pending edits &gt; saved cosmetic (item UUID, else slot). Other players use the
 * per-slot {@link Look} fetched from the sync server.
 */
public final class CosmeticResolver {
	private static @Nullable Look previewLook;

	private CosmeticResolver() {}

	/** Set by the GUI while it is open; null restores saved cosmetics. */
	public static void setPreview(@Nullable Look look) {
		previewLook = look;
	}

	public static @Nullable Look preview() {
		return previewLook;
	}

	/** Animation clock in ticks (wall-clock based so dyes keep animating in menus and while paused). */
	public static float clock() {
		return (System.nanoTime() / 1_000_000L) / 50f;
	}

	public static boolean isLocal(@Nullable Entity e) {
		Minecraft mc = Minecraft.getInstance();
		return e != null && mc.player != null && e.getUUID().equals(mc.player.getUUID());
	}

	private static boolean active() {
		ModConfig cfg = CosmeticStudioClient.config();
		return cfg != null && (!cfg.onlyInSkyBlock || Location.inSkyBlock());
	}

	/** Cosmetic for an armor piece worn by {@code wearer}. */
	public static ArmorCosmetic worn(@Nullable Entity wearer, ArmorSlot slot, ItemStack stack) {
		if (!(wearer instanceof Player) || !active()) return ArmorCosmetic.NONE;
		ModConfig cfg = CosmeticStudioClient.config();
		if (isLocal(wearer)) {
			if (!cfg.showMyCosmetics) return ArmorCosmetic.NONE;
			if (previewLook != null) return previewLook.get(slot);
			return CosmeticStudioClient.store().get(SkyBlockItems.uuid(stack), slot);
		}
		if (!cfg.showOthersCosmetics) return ArmorCosmetic.NONE;
		Look remote = RemoteCosmetics.get(wearer.getUUID());
		return remote == null ? ArmorCosmetic.NONE : remote.get(slot);
	}

	/** Pet for a player, or null. */
	public static @Nullable PetCosmetic pet(@Nullable Entity owner) {
		if (!(owner instanceof Player) || !active()) return null;
		ModConfig cfg = CosmeticStudioClient.config();
		PetCosmetic pet;
		if (isLocal(owner)) {
			if (!cfg.showMyCosmetics) return null;
			pet = previewLook != null ? previewLook.pet : CosmeticStudioClient.store().pet();
		} else {
			if (!cfg.showOthersCosmetics || !cfg.showOthersPets) return null;
			Look remote = RemoteCosmetics.get(owner.getUUID());
			pet = remote == null ? null : remote.pet;
		}
		return pet != null && pet.isActive() ? pet : null;
	}

	/**
	 * Dye colour override for {@code stack}, or null to keep vanilla. Used for both worn armor and item icons.
	 * @param holder entity that holds/wears the stack, or null for GUI icons (treated as the local player's items)
	 */
	public static @Nullable Integer dyeColor(ItemStack stack, @Nullable LivingEntity holder) {
		if (stack.isEmpty() || !SkyBlockItems.isDyeable(stack)) return null;
		ArmorSlot slot = slotOf(stack);
		if (slot == null) return null;
		Minecraft mc = Minecraft.getInstance();
		ArmorCosmetic c;
		if (holder != null && !isLocal(holder)) {
			// Other players: only recolour what they are actually wearing in that slot.
			if (holder.getItemBySlot(slot.vanilla) != stack) return null;
			c = worn(holder, slot, stack);
		} else {
			Player local = mc.player;
			boolean wornByMe = local != null && local.getItemBySlot(slot.vanilla) == stack;
			String uuid = SkyBlockItems.uuid(stack);
			if (wornByMe) {
				c = worn(local, slot, stack);
			} else if (uuid != null && active() && CosmeticStudioClient.config().showMyCosmetics) {
				// An inventory item with a UUID keeps its cosmetic even when not worn.
				c = CosmeticStudioClient.store().get(uuid, slot);
			} else {
				return null;
			}
		}
		DyeDef dye = CatalogManager.get().dye(c.dye());
		return dye == null ? null : dye.colorAt(clock());
	}

	public static @Nullable ArmorSlot slotOf(ItemStack stack) {
		var eq = stack.get(net.minecraft.core.component.DataComponents.EQUIPPABLE);
		return eq == null ? null : ArmorSlot.of(eq.slot());
	}

	public static @Nullable UUID uuidOf(@Nullable Entity e) {
		return e == null ? null : e.getUUID();
	}
}
