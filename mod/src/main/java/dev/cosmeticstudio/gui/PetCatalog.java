package dev.cosmeticstudio.gui;

import dev.cosmeticstudio.CosmeticStudioClient;
import dev.cosmeticstudio.model.PetCosmetic;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

/**
 * Every block and every living mob, read from the game registries at runtime (no hardcoded lists), so new
 * blocks/mobs in future versions appear automatically. Ids are registry ids like "minecraft:oak_log".
 */
public final class PetCatalog {
	private static @Nullable List<Block> blocks;
	private static @Nullable List<EntityType<?>> mobs;
	private static final Map<EntityType<?>, Boolean> LIVING = new ConcurrentHashMap<>();

	private PetCatalog() {}

	public static List<Block> blocks() {
		if (blocks == null) {
			List<Block> b = new ArrayList<>();
			for (Block block : BuiltInRegistries.BLOCK) {
				if (block.defaultBlockState().isAir()) continue;
				b.add(block);
			}
			b.sort(Comparator.comparing(x -> x.getName().getString()));
			blocks = List.copyOf(b);
		}
		return blocks;
	}

	/** Living, non-player mob types. Probed once by creating (not spawning) an instance. */
	public static List<EntityType<?>> mobs() {
		if (mobs == null) {
			var level = Minecraft.getInstance().level;
			if (level == null) return List.of();
			List<EntityType<?>> m = new ArrayList<>();
			for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
				if (isLivingMob(type)) m.add(type);
			}
			m.sort(Comparator.comparing(t -> t.getDescription().getString()));
			mobs = List.copyOf(m);
		}
		return mobs;
	}

	public static boolean isLivingMob(EntityType<?> type) {
		return LIVING.computeIfAbsent(type, t -> {
			if (t == net.minecraft.world.entity.EntityTypes.PLAYER) return false;
			var level = Minecraft.getInstance().level;
			if (level == null) return false;
			try {
				Entity e = t.create(level, EntitySpawnReason.LOAD);
				boolean ok = e instanceof LivingEntity && !(e instanceof Player) && !(e instanceof ArmorStand);
				if (e != null) e.discard();
				return ok;
			} catch (Throwable ex) {
				CosmeticStudioClient.LOGGER.debug("Skipping entity {}: {}", t, ex.toString());
				return false;
			}
		});
	}

	public static List<Option<String>> blockOptions(String q) {
		List<Option<String>> out = new ArrayList<>();
		for (Block b : blocks()) {
			String id = BuiltInRegistries.BLOCK.getKey(b).toString();
			String name = b.getName().getString();
			if (!CosmeticScreen.matches(q, name, id)) continue;
			ItemStack icon = b.asItem() == Items.AIR ? ItemStack.EMPTY : new ItemStack(b);
			out.add(new Option<>(id, Component.literal(name), null, icon, List.of(Component.literal(name), Component.literal(id).withStyle(ChatFormatting.DARK_GRAY)),
					true, null));
		}
		return out;
	}

	public static List<Option<String>> entityOptions(String q) {
		List<Option<String>> out = new ArrayList<>();
		for (EntityType<?> t : mobs()) {
			String id = BuiltInRegistries.ENTITY_TYPE.getKey(t).toString();
			String name = t.getDescription().getString();
			if (!CosmeticScreen.matches(q, name, id)) continue;
			out.add(new Option<>(id, Component.literal(name), null, egg(t), List.of(Component.literal(name), Component.literal(id).withStyle(ChatFormatting.DARK_GRAY)),
					true, null));
		}
		return out;
	}

	public static @Nullable Block block(@Nullable String id) {
		Identifier rl = id == null ? null : Identifier.tryParse(id);
		if (rl == null || !BuiltInRegistries.BLOCK.containsKey(rl)) return null;
		Block b = BuiltInRegistries.BLOCK.getValue(rl);
		return b == Blocks.AIR ? null : b;
	}

	public static @Nullable EntityType<?> entity(@Nullable String id) {
		Identifier rl = id == null ? null : Identifier.tryParse(id);
		if (rl == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(rl)) return null;
		EntityType<?> t = BuiltInRegistries.ENTITY_TYPE.getValue(rl);
		return isLivingMob(t) ? t : null;
	}

	public static ItemStack icon(PetCosmetic pet) {
		if (pet.kind == PetCosmetic.Kind.BLOCK) {
			Block b = block(pet.id);
			return b == null || b.asItem() == Items.AIR ? new ItemStack(Items.STONE) : new ItemStack(b);
		}
		EntityType<?> t = entity(pet.id);
		ItemStack egg = t == null ? ItemStack.EMPTY : egg(t);
		return egg.isEmpty() ? new ItemStack(Items.LEAD) : egg;
	}

	private static ItemStack egg(EntityType<?> t) {
		return SpawnEggItem.byId(t).map(h -> new ItemStack(h)).orElse(ItemStack.EMPTY);
	}
}
