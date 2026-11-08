package dev.cosmeticstudio.render;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * One client-only mob instance per pet owner, used purely as an animation/model source. Instances are created with
 * {@code EntityType.create} and are <b>never added to the world</b>, never ticked by the server and never sent anywhere.
 * We advance their age each tick so blinking, tails and idle animations play, and keep a lagging "follow" position.
 */
public final class PetEntities {
	static final class Pet {
		final @Nullable LivingEntity entity;
		@Nullable Vec3 followPos;
		long lastSeen;

		Pet(@Nullable LivingEntity entity) {
			this.entity = entity;
		}
	}

	private static final Map<UUID, Pet> PETS = new ConcurrentHashMap<>();
	private static final java.util.concurrent.atomic.AtomicInteger NEXT_ID = new java.util.concurrent.atomic.AtomicInteger(-1_000_000);

	private PetEntities() {}

	static @Nullable Pet get(UUID owner, EntityType<?> type) {
		Pet pet = PETS.get(owner);
		if (pet == null || pet.entity == null || pet.entity.getType() != type) {
			var level = Minecraft.getInstance().level;
			if (level == null) return null;
			Entity e;
			try {
				e = type.create(level, EntitySpawnReason.LOAD);
			} catch (Throwable t) {
				return null;
			}
			if (!(e instanceof LivingEntity living)) return null;
			living.setId(NEXT_ID.getAndDecrement()); // negative ids never collide with server entities
			living.setNoGravity(true);
			living.setSilent(true);
			pet = new Pet(living);
			PETS.put(owner, pet);
		}
		pet.lastSeen = System.currentTimeMillis();
		return pet;
	}

	private static final Map<UUID, Pet> FOLLOW = new ConcurrentHashMap<>();

	/** Per-owner follow smoothing state (also used for block pets, which have no entity). */
	static Pet followState(UUID owner) {
		Pet p = PETS.get(owner);
		if (p != null) return p;
		return FOLLOW.computeIfAbsent(owner, o -> new Pet(null));
	}

	/** Advances idle animations and forgets pets that have not been drawn for a while. */
	public static void tick() {
		long now = System.currentTimeMillis();
		for (Iterator<Pet> it = PETS.values().iterator(); it.hasNext(); ) {
			Pet p = it.next();
			if (now - p.lastSeen > 30_000) {
				it.remove();
				continue;
			}
			if (p.entity != null) p.entity.tickCount++;
		}
	}

	public static void clear() {
		PETS.clear();
		FOLLOW.clear();
	}
}
