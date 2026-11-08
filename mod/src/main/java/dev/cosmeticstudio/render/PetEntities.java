package dev.cosmeticstudio.render;

import dev.cosmeticstudio.model.PetCosmetic;
import dev.cosmeticstudio.model.PetPosition;
import dev.cosmeticstudio.resolve.CosmeticResolver;
import dev.cosmeticstudio.skyblock.RealPetHider;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Per-owner pet state: an optional client-only mob instance (animation/model source, <b>never added to the world</b>,
 * negative id) plus the pet's own movement for "Follow me" / "Replace my SkyBlock pet".
 *
 * Movement runs once per game tick (so it is the same speed at any frame rate) and is interpolated when drawn.
 * Walking mobs stay on the ground, walk after you and face where they go; flying/swimming mobs and blocks float.
 */
public final class PetEntities {
	/** Flyers whose navigation type doesn't reveal it. Everything with FlyingPathNavigation is detected automatically. */
	private static final Set<String> ALSO_FLYING = Set.of("minecraft:bat", "minecraft:ghast", "minecraft:happy_ghast", "minecraft:phantom",
			"minecraft:vex", "minecraft:blaze", "minecraft:ender_dragon", "minecraft:wither", "minecraft:allay", "minecraft:bee", "minecraft:parrot");

	public static final class Pet {
		@Nullable LivingEntity entity;
		boolean grounded;
		@Nullable Vec3 pos, prevPos;
		float yaw, prevYaw;
		float walkPos, prevWalkPos, walkSpeed;
		long lastSeen;

		Vec3 lerpPos(float pt) {
			return prevPos == null || pos == null ? pos : prevPos.lerp(pos, pt);
		}

		float lerpYaw(float pt) {
			return Mth.rotLerp(pt, prevYaw, yaw);
		}

		float lerpWalk(float pt) {
			return Mth.lerp(pt, prevWalkPos, walkPos);
		}
	}

	private static final Map<UUID, Pet> PETS = new ConcurrentHashMap<>();
	private static final Map<EntityType<?>, Boolean> GROUND = new ConcurrentHashMap<>();
	private static final AtomicInteger NEXT_ID = new AtomicInteger(-1_000_000);

	private PetEntities() {}

	/** State for an owner, creating it if needed. The mob instance is (re)created when the type changes. */
	static Pet state(UUID owner, @Nullable EntityType<?> type) {
		Pet pet = PETS.computeIfAbsent(owner, o -> new Pet());
		pet.lastSeen = System.currentTimeMillis();
		if (type == null) {
			pet.entity = null;
			pet.grounded = false;
		} else if (pet.entity == null || pet.entity.getType() != type) {
			pet.entity = create(type);
			pet.grounded = pet.entity != null && isWalker(type, pet.entity);
		}
		return pet;
	}

	private static @Nullable LivingEntity create(EntityType<?> type) {
		var level = Minecraft.getInstance().level;
		if (level == null) return null;
		try {
			Entity e = type.create(level, EntitySpawnReason.LOAD);
			if (!(e instanceof LivingEntity living)) return null;
			living.setId(NEXT_ID.getAndDecrement()); // negative ids never collide with server entities
			living.setNoGravity(true);
			living.setSilent(true);
			return living;
		} catch (Throwable t) {
			return null;
		}
	}

	/** Mobs that normally walk (not fly or swim). */
	static boolean isWalker(EntityType<?> type, LivingEntity e) {
		return GROUND.computeIfAbsent(type, t -> {
			if (ALSO_FLYING.contains(BuiltInRegistries.ENTITY_TYPE.getKey(t).toString())) return false;
			if (e instanceof Mob mob) {
				var nav = mob.getNavigation();
				return !(nav instanceof FlyingPathNavigation) && !(nav instanceof WaterBoundPathNavigation);
			}
			return true;
		});
	}

	/** Called every client tick: ages mobs (blinking, tails) and moves following pets. */
	public static void tick() {
		Minecraft mc = Minecraft.getInstance();
		long now = System.currentTimeMillis();
		for (Iterator<Map.Entry<UUID, Pet>> it = PETS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Pet> en = it.next();
			Pet p = en.getValue();
			if (now - p.lastSeen > 30_000) {
				it.remove();
				continue;
			}
			if (p.entity != null) p.entity.tickCount++;
			Player owner = mc.level == null ? null : mc.level.getPlayerByUUID(en.getKey());
			PetCosmetic cosmetic = owner == null ? null : CosmeticResolver.pet(owner);
			if (owner == null || cosmetic == null) continue;
			move(mc.level, owner, cosmetic, p);
		}
	}

	private static void move(Level level, Player owner, PetCosmetic cosmetic, Pet p) {
		p.prevPos = p.pos;
		p.prevYaw = p.yaw;
		p.prevWalkPos = p.walkPos;
		boolean replace = cosmetic.position == PetPosition.SKYBLOCK_PET && CosmeticResolver.isLocal(owner);
		ArmorStand real = replace ? RealPetHider.modelStand() : null;
		boolean ground = p.grounded && cosmetic.kind == PetCosmetic.Kind.ENTITY;

		Vec3 target;
		if (real != null) {
			target = ground ? real.position() : real.position().add(0, real.getBbHeight() * 0.62, 0);
		} else {
			// Beside and a little behind the owner (left side), like a SkyBlock pet.
			double yawRad = Math.toRadians(owner.yBodyRot);
			double sideX = Math.cos(yawRad), sideZ = Math.sin(yawRad); // owner's left
			double backX = Math.sin(yawRad), backZ = -Math.cos(yawRad); // behind the owner
			double side = ground ? 1.1 : 0.85, back = ground ? 0.9 : 0.55;
			target = owner.position().add(sideX * side + backX * back, ground ? 0 : 1.15, sideZ * side + backZ * back);
		}

		Vec3 cur = p.pos;
		if (cur == null || cur.distanceToSqr(target) > 16 * 16) cur = target; // first frame or teleport
		Vec3 next;
		if (ground) {
			// Walk towards the target on the ground; hurry when far behind, stop when close.
			Vec3 flat = new Vec3(target.x - cur.x, 0, target.z - cur.z);
			double dist = flat.length();
			double speed = real != null ? dist : (dist < 0.35 ? 0 : Math.min(dist - 0.25, 0.12 + dist * 0.12));
			Vec3 step = dist > 1e-4 ? flat.scale(Math.min(1, speed / dist)) : Vec3.ZERO;
			double nx = cur.x + step.x, nz = cur.z + step.z;
			next = new Vec3(nx, groundY(level, nx, nz, owner.getY(), cur.y), nz);
			double moved = step.length();
			p.walkSpeed = Mth.lerp(0.4f, p.walkSpeed, (float) Math.min(1, moved * 5));
			p.walkPos += p.walkSpeed * 1.2f;
			if (moved > 0.01) p.yaw = rotateTowards(p.yaw, (float) Math.toDegrees(Math.atan2(-step.x, step.z)), 20);
			else p.yaw = rotateTowards(p.yaw, faceYaw(next, owner.position()), 6);
		} else {
			next = real != null ? target : cur.lerp(target, 0.18);
			p.walkSpeed = 0;
			p.yaw = rotateTowards(p.yaw, real != null ? real.getYRot() : owner.yBodyRot, 10);
		}
		p.pos = next;
		if (p.prevPos == null) {
			p.prevPos = next;
			p.prevYaw = p.yaw;
		}
	}

	/** Top of the highest solid block at (x, z) near the owner's height; falls back to the owner's feet. */
	static double groundY(Level level, double x, double z, double ownerY, double currentY) {
		int bx = Mth.floor(x), bz = Mth.floor(z);
		int start = Mth.floor(Math.max(ownerY, currentY)) + 1;
		for (int y = start; y >= start - 6; y--) {
			BlockPos pos = new BlockPos(bx, y, bz);
			BlockState state = level.getBlockState(pos);
			VoxelShape shape = state.getCollisionShape(level, pos);
			if (!shape.isEmpty()) {
				double top = y + shape.max(net.minecraft.core.Direction.Axis.Y);
				if (top <= start + 0.01) return top;
			}
		}
		return ownerY;
	}

	private static float faceYaw(Vec3 from, Vec3 to) {
		return (float) Math.toDegrees(Math.atan2(-(to.x - from.x), to.z - from.z));
	}

	private static float rotateTowards(float from, float to, float max) {
		float d = Mth.wrapDegrees(to - from);
		return from + Mth.clamp(d, -max, max);
	}

	/** Current pet position for an owner (world coordinates), or null. For tests/debug. */
	public static @Nullable Vec3 position(UUID owner) {
		Pet p = PETS.get(owner);
		return p == null ? null : p.pos;
	}

	public static void clear() {
		PETS.clear();
	}
}
