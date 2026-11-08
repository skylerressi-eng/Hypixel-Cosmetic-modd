package dev.cosmeticstudio.render;

import dev.cosmeticstudio.CosmeticStudioClient;
import dev.cosmeticstudio.model.PetCosmetic;
import dev.cosmeticstudio.model.PetPosition;
import net.minecraft.client.model.animal.parrot.ParrotModel;
import net.minecraft.client.renderer.entity.state.AllayRenderState;
import net.minecraft.client.renderer.entity.state.ArmadilloRenderState;
import net.minecraft.client.renderer.entity.state.AxolotlRenderState;
import net.minecraft.client.renderer.entity.state.BeeRenderState;
import net.minecraft.client.renderer.entity.state.ChickenRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.FelineRenderState;
import net.minecraft.client.renderer.entity.state.FoxRenderState;
import net.minecraft.client.renderer.entity.state.FrogRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.PandaRenderState;
import net.minecraft.client.renderer.entity.state.ParrotRenderState;
import net.minecraft.client.renderer.entity.state.RabbitRenderState;
import net.minecraft.client.renderer.entity.state.SheepRenderState;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Cute idle behaviour for cosmetic pets. Purely visual and client-side.
 *
 * <ul>
 * <li>Walking with you: normal follow.</li>
 * <li>You stand still a few seconds: they play — random hops, happy spins, looking around, and a signature move
 * per mob (fox pounce, rabbit hop, sheep grazing, dog tail wag, chicken flapping, allay dance, parrot party, panda
 * sneeze, bee roll, frog croak, axolotl playing dead, armadillo peek…).</li>
 * <li>Still for longer: they sit (dogs, cats, foxes, pandas, parrots); then fall asleep (fox curls up, cat lies down,
 * panda rolls on its back, armadillo hides in its shell…) with floating "z Z".</li>
 * <li>Crouch next to your pet: hearts and a happy hop. Move again: they wake up with a hop.</li>
 * </ul>
 */
public final class PetAnimations {
	public enum Mood { FOLLOW, PLAY, SIT, SLEEP }

	public enum Action { NONE, HOP, SPIN, LOOK, SIGNATURE }

	/** Seconds you must stand still before pets play / sit / fall asleep. */
	static final int PLAY_AFTER = 4 * 20, SIT_AFTER = 20 * 20, SLEEP_AFTER = 40 * 20;

	private static final RandomSource RANDOM = RandomSource.create();

	private PetAnimations() {}

	static boolean enabled() {
		return CosmeticStudioClient.config() == null || CosmeticStudioClient.config().petAnimations;
	}

	/** Once per tick: update the pet's mood from how long the owner has been still, and start random actions. */
	static void tick(PetEntities.Pet p, Player owner, PetCosmetic pet, Level level) {
		p.tick++;
		Vec3 now = owner.position();
		boolean moved = p.lastOwnerPos != null && p.lastOwnerPos.distanceToSqr(now) > 0.0009;
		p.lastOwnerPos = now;
		Mood before = p.mood;
		if (moved || !enabled()) {
			p.ownerStill = 0;
			p.mood = PetAnimations.Mood.FOLLOW;
			if (before == Mood.SLEEP && enabled()) start(p, Action.HOP, 10); // wake up!
		} else {
			p.ownerStill++;
			p.mood = p.ownerStill > SLEEP_AFTER ? Mood.SLEEP : p.ownerStill > SIT_AFTER ? Mood.SIT : p.ownerStill > PLAY_AFTER ? Mood.PLAY : Mood.FOLLOW;
		}
		if (p.action != Action.NONE && p.tick - p.actionStart >= p.actionLength) p.action = Action.NONE;

		// Crouching next to your pet = petting it.
		if (enabled() && owner.isShiftKeyDown() && p.pos != null && p.pos.distanceToSqr(now) < 9) {
			if (p.tick - p.lastHearts > 12) {
				p.lastHearts = p.tick;
				level.addParticle(ParticleTypes.HEART, p.pos.x, p.pos.y + 0.6 + RANDOM.nextDouble() * 0.3, p.pos.z, 0, 0.05, 0);
				if (p.action == Action.NONE) start(p, Action.HOP, 10);
				if (p.mood == Mood.SLEEP || p.mood == Mood.SIT) p.ownerStill = PLAY_AFTER + 1; // petting wakes them up
			}
		}

		if (p.mood == Mood.PLAY && p.action == Action.NONE && p.tick >= p.nextAction) {
			boolean free = pet.position == PetPosition.FOLLOW || pet.position == PetPosition.SKYBLOCK_PET || pet.position == PetPosition.BEHIND;
			int roll = RANDOM.nextInt(10);
			Action a = !free ? (roll < 6 ? Action.LOOK : Action.SIGNATURE)
					: roll < 3 ? Action.SIGNATURE : roll < 5 ? Action.HOP : roll < 7 ? Action.LOOK : roll < 8 ? Action.SPIN : Action.SIGNATURE;
			start(p, a, a == Action.SIGNATURE ? 40 : a == Action.SPIN ? 20 : a == Action.LOOK ? 40 : 12);
			p.nextAction = p.tick + 60 + RANDOM.nextInt(120);
		}
	}

	private static void start(PetEntities.Pet p, Action a, int length) {
		p.action = a;
		p.actionStart = p.tick;
		p.actionLength = length;
	}

	/** Progress 0..1 of the current action. */
	static float progress(PetEntities.Pet p, float pt) {
		if (p.action == Action.NONE || p.actionLength <= 0) return 0;
		return Mth.clamp((p.tick - p.actionStart + pt) / p.actionLength, 0, 1);
	}

	/** Extra height (blocks, before pet scaling) for hops/pounces. */
	static float hopHeight(PetEntities.Pet p, float pt, boolean fox) {
		float t = progress(p, pt);
		if (p.action == Action.HOP) return Mth.sin(t * Mth.PI) * 0.45f;
		if (p.action == Action.SIGNATURE && fox) return t < 0.4f ? 0 : Mth.sin((t - 0.4f) / 0.6f * Mth.PI) * 0.9f;
		return 0;
	}

	/** Extra body yaw for spins. */
	static float spin(PetEntities.Pet p, float pt) {
		return p.action == Action.SPIN ? progress(p, pt) * 360f : 0f;
	}

	/** Applies the mood/action to a mob's render state: poses, model animations, head movement. */
	static void apply(EntityRenderState es, PetEntities.Pet p, float pt, float age) {
		if (!(es instanceof LivingEntityRenderState living)) return;
		float t = progress(p, pt);
		boolean signature = p.action == Action.SIGNATURE;
		boolean sit = p.mood == Mood.SIT, sleep = p.mood == Mood.SLEEP;
		int ageTicks = (int) age;

		if (p.action == Action.LOOK) living.yRot = Mth.sin(t * Mth.TWO_PI) * 70f;
		if (sleep) {
			living.yRot = 0;
			living.xRot = 25f; // head down
		}

		switch (es) {
			case FoxRenderState fox -> {
				fox.isSitting = sit;
				fox.isSleeping = sleep;
				if (signature) { // crouch, wiggle, pounce
					fox.isCrouching = t < 0.4f;
					fox.crouchAmount = t < 0.4f ? Math.min(1, t / 0.15f) : 0;
					fox.isPouncing = t >= 0.4f;
					fox.headRollAngle = t < 0.4f ? Mth.sin(age * 1.2f) * 0.25f : 0;
				}
			}
			case FelineRenderState cat -> {
				cat.isSitting = sit;
				cat.lieDownAmount = sleep ? 1 : 0;
				cat.lieDownAmountTail = sleep ? 1 : 0;
				cat.relaxStateOneAmount = sleep ? 1 : 0;
				if (signature) cat.isCrouching = t < 0.6f; // ready to pounce
			}
			case WolfRenderState wolf -> {
				wolf.isSitting = sit || sleep;
				boolean wag = signature || p.mood == Mood.PLAY;
				wolf.tailAngle = wag ? 1.5f + Mth.sin(age * 1.6f) * 0.5f : (float) Math.PI / 5f;
				if (signature) wolf.headRollAngle = Mth.sin(t * Mth.PI) * 0.35f; // curious head tilt
			}
			case PandaRenderState panda -> {
				panda.isSitting = sit;
				panda.sitAmount = sit ? 1 : 0;
				panda.lieOnBackAmount = sleep ? 1 : 0;
				if (signature) {
					panda.isSneezing = true;
					panda.sneezeTime = (int) (t * 20);
				}
			}
			case ParrotRenderState parrot -> {
				if (sit || sleep) parrot.pose = ParrotModel.Pose.SITTING;
				if (signature) parrot.pose = ParrotModel.Pose.PARTY;
			}
			case RabbitRenderState rabbit -> {
				if (signature || p.action == Action.HOP) {
					rabbit.jumpCompletion = t;
					rabbit.hopAnimationState.startIfStopped(p.actionStart);
				}
			}
			case SheepRenderState sheep -> {
				if (signature) { // graze
					float down = t < 0.15f ? t / 0.15f : t > 0.85f ? (1 - t) / 0.15f : 1;
					sheep.headEatPositionScale = down;
					sheep.headEatAngleScale = t > 0.15f && t < 0.85f ? 0.2f + 0.2f * Mth.sin(age * 1.4f) : 0;
				}
			}
			case ChickenRenderState chicken -> {
				if (signature) {
					chicken.flap = age * 1.5f;
					chicken.flapSpeed = 1f;
				}
			}
			case AllayRenderState allay -> {
				allay.isDancing = signature || p.mood == Mood.PLAY && (ageTicks / 100) % 3 == 0;
				if (signature) {
					allay.isSpinning = t < 0.3f;
					allay.spinningProgress = t / 0.3f;
				}
			}
			case BeeRenderState bee -> {
				if (signature) bee.rollAmount = Mth.sin(t * Mth.PI);
				bee.isOnGround = sleep;
			}
			case FrogRenderState frog -> {
				if (signature) frog.croakAnimationState.startIfStopped(p.actionStart);
				if (p.action == Action.HOP) frog.jumpAnimationState.startIfStopped(p.actionStart);
			}
			case AxolotlRenderState axolotl -> {
				if (signature || sleep) axolotl.playingDeadFactor = sleep ? 1 : Mth.sin(t * Mth.PI);
			}
			case ArmadilloRenderState armadillo -> {
				armadillo.isHidingInShell = sleep;
				if (signature) armadillo.peekAnimationState.startIfStopped(p.actionStart);
			}
			default -> {
				if (sit) living.xRot = 10f;
			}
		}
	}

	/** Text bubble for sleeping pets ("z", "z Z", "z Z z"), or null. */
	static String sleepText(PetEntities.Pet p) {
		if (p.mood != Mood.SLEEP) return null;
		return switch ((p.tick / 15) % 3) { case 0 -> "z"; case 1 -> "z Z"; default -> "z Z z"; };
	}
}
