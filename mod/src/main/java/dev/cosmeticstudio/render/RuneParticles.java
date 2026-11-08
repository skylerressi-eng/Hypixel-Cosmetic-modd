package dev.cosmeticstudio.render;

import dev.cosmeticstudio.CosmeticStudioClient;
import dev.cosmeticstudio.catalog.Catalog;
import dev.cosmeticstudio.catalog.CatalogManager;
import dev.cosmeticstudio.catalog.RuneDef;
import dev.cosmeticstudio.catalog.RuneEffect;
import dev.cosmeticstudio.compat.Compat;
import dev.cosmeticstudio.config.ModConfig;
import dev.cosmeticstudio.model.ArmorCosmetic;
import dev.cosmeticstudio.model.ArmorSlot;
import dev.cosmeticstudio.resolve.CosmeticResolver;
import dev.cosmeticstudio.skyblock.Location;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Client-side rune particles. Spawns vanilla particles locally with {@code ClientLevel.addParticle}; nothing is sent
 * to the server. Respects the vanilla Particles option, the mod's on/off + density settings, the Dungeons/Kuudra
 * switches and the slot rules.
 */
public final class RuneParticles {
	private static final double RANGE_SQ = 32 * 32;
	private static final Map<UUID, Vec3> LAST_POS = new ConcurrentHashMap<>();
	private static final RandomSource RANDOM = RandomSource.create();
	private static long tick;
	private static long emitted;

	/** Total particles spawned this session (for tests/debug). */
	public static long emitted() {
		return emitted;
	}

	private RuneParticles() {}

	/** Effective multiplier (0 = off) from vanilla setting, density slider and area switches. */
	public static float density(Minecraft mc) {
		ModConfig cfg = CosmeticStudioClient.config();
		if (!cfg.particles) return 0;
		if (cfg.disableParticlesInDungeons && Location.area() == Location.Area.DUNGEON) return 0;
		if (cfg.disableParticlesInKuudra && Location.area() == Location.Area.KUUDRA) return 0;
		ParticleStatus vanilla = mc.options.particles().get();
		float v = switch (vanilla) { case ALL -> 1f; case DECREASED -> 0.5f; case MINIMAL -> 0.15f; };
		return v * cfg.particleDensity;
	}

	public static void tick(Minecraft mc) {
		tick++;
		if (mc.level == null || mc.player == null || mc.isPaused()) return;
		float density = density(mc);
		if (density <= 0) return;
		Catalog cat = CatalogManager.get();
		ModConfig cfg = CosmeticStudioClient.config();
		for (Player p : mc.level.players()) {
			if (p.distanceToSqr(mc.player) > RANGE_SQ || p.isInvisible()) continue;
			Vec3 last = LAST_POS.put(p.getUUID(), p.position());
			boolean walking = last != null && p.onGround() && last.distanceToSqr(p.position()) > 0.0004;
			boolean attacking = Compat.isSwinging(p);
			for (ArmorSlot slot : ArmorSlot.values()) {
				ArmorCosmetic c = CosmeticResolver.worn(p, slot, p.getItemBySlot(slot.vanilla));
				RuneDef rune = cat.rune(c.rune());
				if (rune == null) continue;
				if (!cfg.allowAnyRuneOnAnyPiece && !slot.runeSlot.equals(rune.slot())) continue;
				RuneEffect effect = cat.effect(rune.id());
				if (effect == null || (!effect.verified() && !cfg.showUnverifiedRuneEffects)) continue;
				for (RuneEffect.Emitter em : effect.emitters()) {
					if (em.trigger() == RuneEffect.Trigger.WALK && !walking) continue;
					if (em.trigger() == RuneEffect.Trigger.ATTACK && !attacking) continue;
					// Higher rune levels emit more often: level II = 1.5x, level III = 2x.
					int interval = Math.max(1, Math.round(em.interval() / (1f + 0.5f * (c.runeLevel() - 1))));
					if ((tick + p.getId()) % interval != 0) continue;
					emit(mc, p, slot, em, density);
				}
			}
		}
		if (tick % 200 == 0) LAST_POS.keySet().removeIf(id -> mc.level.getPlayerByUUID(id) == null);
	}

	private static void emit(Minecraft mc, Player p, ArmorSlot slot, RuneEffect.Emitter em, float density) {
		ParticleOptions particle = particle(em);
		if (particle == null) return;
		float exact = em.count() * density;
		int count = (int) exact + (RANDOM.nextFloat() < exact - (int) exact ? 1 : 0);
		double h = switch (slot) { case HELMET -> p.getBbHeight() + 0.15; case CHESTPLATE -> p.getBbHeight() * 0.65; case LEGGINGS -> p.getBbHeight() * 0.38; case BOOTS -> 0.1; };
		double x = p.getX(), y = p.getY() + h, z = p.getZ();
		float spread = em.spread();
		for (int i = 0; i < count; i++) {
			double px = x, py = y, pz = z, vx = 0, vy = 0, vz = 0;
			switch (em.shape()) {
				case POINT -> {
					px += (RANDOM.nextDouble() - 0.5) * spread;
					py += (RANDOM.nextDouble() - 0.5) * spread;
					pz += (RANDOM.nextDouble() - 0.5) * spread;
				}
				case BURST -> {
					double a = RANDOM.nextDouble() * Math.PI * 2;
					vx = Math.cos(a) * em.speed();
					vz = Math.sin(a) * em.speed();
					vy = RANDOM.nextDouble() * em.speed();
				}
				case RING -> {
					double a = (tick * 0.25) + i * (Math.PI * 2 / Math.max(1, count)) + RANDOM.nextDouble() * 0.3;
					px += Math.cos(a) * spread;
					pz += Math.sin(a) * spread;
				}
				case SPIRAL -> {
					double a = tick * 0.4 + i * Math.PI;
					double r = spread * 0.9;
					px += Math.cos(a) * r;
					pz += Math.sin(a) * r;
					py = p.getY() + ((tick * 0.05 + i * 0.5) % p.getBbHeight());
				}
				case TRAIL -> {
					double yaw = Math.toRadians(p.getYRot());
					px += Math.sin(yaw) * 0.3 + (RANDOM.nextDouble() - 0.5) * spread;
					pz -= Math.cos(yaw) * 0.3 - (RANDOM.nextDouble() - 0.5) * spread;
					py = p.getY() + 0.05;
				}
				case RAIN -> {
					px += (RANDOM.nextDouble() - 0.5) * 1.2;
					pz += (RANDOM.nextDouble() - 0.5) * 1.2;
					py = p.getY() + p.getBbHeight() + 0.8;
				}
			}
			mc.level.addParticle(particle, px, py, pz, vx, vy, vz);
			emitted++;
		}
	}

	private static @Nullable ParticleOptions particle(RuneEffect.Emitter em) {
		Identifier id = Identifier.tryParse(em.particle());
		if (id == null) return null;
		if (id.getPath().equals("dust")) {
			int color = em.color() == null ? 0xFFFFFF
					: em.color() == RuneEffect.RAINBOW ? Mth.hsvToRgb((tick % 60) / 60f, 1f, 1f) : em.color();
			return new DustParticleOptions(color, 1.0f);
		}
		ParticleType<?> type = BuiltInRegistries.PARTICLE_TYPE.getValue(id);
		return type instanceof ParticleOptions simple ? simple : null;
	}
}
