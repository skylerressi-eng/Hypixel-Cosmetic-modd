package dev.cosmeticstudio.render;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import dev.cosmeticstudio.catalog.AppearanceDef;
import dev.cosmeticstudio.catalog.CatalogManager;
import dev.cosmeticstudio.model.ArmorSlot;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ResolvableProfile;
import org.jspecify.annotations.Nullable;

/**
 * Builds the item shown for an armor "look": any SkyBlock armor piece ("sb:ID", drawn with its real leather colour or
 * head texture from Hypixel's item data) or any vanilla item that can be worn in that slot. Purely for rendering.
 */
public final class Appearances {
	private static final Map<String, ItemStack> CACHE = new ConcurrentHashMap<>();
	private static @Nullable Map<ArmorSlot, List<Item>> vanilla;

	private Appearances() {}

	/** The display stack for a look id, or empty if unknown. Cached; callers must not modify it. */
	public static ItemStack stack(@Nullable String look) {
		if (look == null) return ItemStack.EMPTY;
		return CACHE.computeIfAbsent(look, Appearances::build);
	}

	private static ItemStack build(String look) {
		if (look.startsWith(AppearanceDef.PREFIX)) {
			AppearanceDef def = CatalogManager.get().appearance(look);
			if (def == null) return ItemStack.EMPTY;
			Item item = item(def.item());
			if (item == null) return ItemStack.EMPTY;
			ItemStack stack = new ItemStack(item);
			if (def.color() != null) stack.set(DataComponents.DYED_COLOR, new DyedItemColor(def.color()));
			if (def.texture() != null && stack.is(Items.PLAYER_HEAD)) {
				UUID id = UUID.nameUUIDFromBytes(def.texture().getBytes(StandardCharsets.UTF_8));
				PropertyMap props = new PropertyMap(ImmutableMultimap.of("textures", new Property("textures", def.texture())));
				stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(new GameProfile(id, "CSHead", props)));
			}
			return stack;
		}
		Item item = item(look);
		return item == null ? ItemStack.EMPTY : new ItemStack(item);
	}

	private static @Nullable Item item(String id) {
		Identifier rl = Identifier.tryParse(id);
		if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) return null;
		Item item = BuiltInRegistries.ITEM.getValue(rl);
		return item == Items.AIR ? null : item;
	}

	/** Every vanilla item that can be worn in this slot (helmets, pumpkins, mob heads, …), from the item registry. */
	public static List<Item> vanillaFor(ArmorSlot slot) {
		if (vanilla == null) {
			Map<ArmorSlot, List<Item>> map = new EnumMap<>(ArmorSlot.class);
			for (ArmorSlot s : ArmorSlot.values()) map.put(s, new ArrayList<>());
			for (Item item : BuiltInRegistries.ITEM) {
				var eq = new ItemStack(item).get(DataComponents.EQUIPPABLE);
				ArmorSlot s = eq == null ? null : ArmorSlot.of(eq.slot());
				if (s != null) map.get(s).add(item);
			}
			map.values().forEach(l -> l.sort(Comparator.comparing(i -> i.getName(new ItemStack(i)).getString())));
			vanilla = map;
		}
		return vanilla.get(slot);
	}

	public static void clearCache() {
		CACHE.clear();
	}
}
