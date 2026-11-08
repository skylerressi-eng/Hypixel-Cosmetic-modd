package dev.cosmeticstudio.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.cosmeticstudio.CosmeticStudioClient;
import dev.cosmeticstudio.compat.Compat;
import dev.cosmeticstudio.gui.PetCatalog;
import dev.cosmeticstudio.model.PetCosmetic;
import dev.cosmeticstudio.resolve.CosmeticResolver;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.entity.EndermanRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Draws a player's cosmetic pet as an extra layer of the player model, so it moves with the body, head and arms and
 * also appears in the GUI preview. Nothing is spawned in the world.
 *
 * Coordinate notes: a layer starts in player-model space (y down, origin at the neck, model faces -Z, model-left is +X).
 * {@link #toBodySpace} converts to "body space": y up from the feet, still rotated with the body (front = -Z, left = -X).
 */
public final class PetLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private final BlockModelResolver blockResolver;
	private final BlockModelRenderState blockState = new BlockModelRenderState();
	private final CameraRenderState camera = new CameraRenderState();

	public PetLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, EntityRendererProvider.Context context) {
		super(parent);
		this.blockResolver = context.getBlockModelResolver();
	}

	@Override
	public void submit(PoseStack ps, SubmitNodeCollector out, int light, AvatarRenderState state, float yRot, float xRot) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || state.isInvisible) return;
		Entity owner = mc.level.getEntity(state.id);
		PetCosmetic pet = CosmeticResolver.pet(owner);
		if (pet == null) return;

		float t = state.ageInTicks;
		float bob = pet.bob ? (float) Math.sin(t * 0.1f) * 0.06f : 0f;
		PlayerModel model = getParentModel();
		float base;

		ps.pushPose();
		switch (pet.position) {
			case HEAD -> {
				model.head.translateAndRotate(ps);
				ps.translate(0, -0.5f, 0);
				flip(ps);
				base = 0.5f;
			}
			case LEFT_SHOULDER, RIGHT_SHOULDER -> {
				model.body.translateAndRotate(ps);
				ps.translate(pet.position == dev.cosmeticstudio.model.PetPosition.LEFT_SHOULDER ? 0.33f : -0.33f, 0f, 0f);
				flip(ps);
				base = 0.45f;
			}
			case LEFT_ARM, RIGHT_ARM -> {
				boolean left = pet.position == dev.cosmeticstudio.model.PetPosition.LEFT_ARM;
				(left ? model.leftArm : model.rightArm).translateAndRotate(ps);
				ps.translate(left ? 0.06f : -0.06f, 0.55f, -0.18f);
				flip(ps);
				ps.last().rotate(Axis.XP.rotationDegrees(90f)); // stand on the outstretched forearm
				base = 0.28f;
			}
			case BEHIND -> {
				toBodySpace(ps);
				ps.translate(0, 1.25f + bob, 0.85f);
				base = 0.55f;
			}
			default -> { // FOLLOW: floats beside/behind and lags behind movement like a SkyBlock pet
				toBodySpace(ps);
				followOffset(ps, owner, state, pet);
				ps.translate(0, bob, 0);
				base = 0.6f;
			}
		}
		ps.translate(pet.offsetX * 0.5f, pet.offsetY * 0.5f, pet.offsetZ * 0.5f);
		float scale = base * pet.scale;
		float height;
		if (pet.kind == PetCosmetic.Kind.BLOCK) {
			height = drawBlock(ps, out, light, state, pet, scale, t);
		} else {
			height = drawEntity(ps, out, light, state, pet, scale, t, owner);
		}
		if (height > 0 && pet.showName && pet.name != null && (CosmeticResolver.isLocal(owner) || CosmeticStudioClient.config().showOthersPetNames)) {
			refreshCamera(mc);
			ps.pushPose();
			ps.translate(0, height + 0.15f, 0);
			billboard(ps);
			Compat.submitNameTag(out, ps, new Vec3(0, -0.25, 0), Component.literal(pet.name), light, camera);
			ps.popPose();
		}
		ps.popPose();
	}

	/**
	 * Name tags rotate themselves to face the camera, which assumes an un-rotated pose (as for normal entities).
	 * Strip the body/arm rotations and model flips we accumulated, keeping position and (average) scale.
	 */
	private static void billboard(PoseStack ps) {
		org.joml.Matrix4f m = ps.last().pose();
		org.joml.Vector3f scale = m.getScale(new org.joml.Vector3f());
		float s = (Math.abs(scale.x) + Math.abs(scale.y) + Math.abs(scale.z)) / 3f;
		org.joml.Vector3f pos = m.getTranslation(new org.joml.Vector3f());
		m.identity().translate(pos).scale(s);
		ps.last().normal().identity();
	}

	/** Model space -> local "feet up" space at the current point. */
	private static void flip(PoseStack ps) {
		ps.scale(-1, -1, 1);
	}

	/** Undo the player model's final translate+flip: origin back at the feet, y up, still body-rotated. */
	private static void toBodySpace(PoseStack ps) {
		ps.translate(0, 1.501f, 0);
		ps.scale(-1, -1, 1);
	}

	/** Lagging follow position (exponential smoothing in world space, converted back to body space). */
	private static void followOffset(PoseStack ps, @Nullable Entity owner, AvatarRenderState state, PetCosmetic pet) {
		float yaw = state.bodyRot;
		// Desired spot in body space: to the owner's left, a little behind, around shoulder height.
		double lx = -0.85, ly = 1.15, lz = 0.55;
		double rad = Math.toRadians(180 - yaw);
		double cos = Math.cos(rad), sin = Math.sin(rad);
		// body -> world rotation (rotation about Y by (180 - bodyRot))
		double wx = lx * cos + lz * sin, wz = -lx * sin + lz * cos;
		Vec3 ownerPos = new Vec3(state.x, state.y, state.z);
		Vec3 desired = ownerPos.add(wx, ly, wz);
		Vec3 current = desired;
		if (owner instanceof LivingEntity && Minecraft.getInstance().level != null && pet.kind != null) {
			PetEntities.Pet p = owner.getUUID() == null ? null : PetEntities.followState(owner.getUUID());
			if (p != null) {
				if (p.followPos == null || p.followPos.distanceToSqr(desired) > 64) p.followPos = desired;
				p.followPos = p.followPos.lerp(desired, 0.08);
				current = p.followPos;
			}
		}
		Vec3 d = current.subtract(ownerPos);
		// world -> body (inverse rotation)
		double bx = d.x * cos - d.z * sin, bz = d.x * sin + d.z * cos;
		ps.translate(bx, d.y, bz);
	}

	private float drawBlock(PoseStack ps, SubmitNodeCollector out, int light, AvatarRenderState state, PetCosmetic pet, float scale, float t) {
		Block block = PetCatalog.block(pet.id);
		if (block == null) return 0;
		blockResolver.update(blockState, block.defaultBlockState(), EndermanRenderer.BLOCK_DISPLAY_CONTEXT);
		if (blockState.isEmpty()) return 0;
		ps.pushPose();
		ps.scale(scale, scale, scale);
		if (pet.spin) ps.last().rotate(Axis.YP.rotationDegrees((t * 2f) % 360));
		ps.translate(-0.5f, 0, -0.5f);
		blockState.submit(ps, out, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
		ps.popPose();
		return scale;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private float drawEntity(PoseStack ps, SubmitNodeCollector out, int light, AvatarRenderState state, PetCosmetic pet, float scale, float t,
			@Nullable Entity owner) {
		EntityType<?> type = PetCatalog.entity(pet.id);
		if (type == null || owner == null) return 0;
		PetEntities.Pet p = PetEntities.get(owner.getUUID(), type);
		if (p == null) return 0;
		LivingEntity e = p.entity;
		Minecraft mc = Minecraft.getInstance();
		EntityRenderer renderer = mc.getEntityRenderDispatcher().getRenderer(e);
		EntityRenderState es = renderer.createRenderState(e, 1.0f);
		es.lightCoords = light;
		es.outlineColor = state.outlineColor;
		es.shadowPieces.clear();
		es.nameTag = null;
		es.ageInTicks = e.tickCount + (t - (int) t);
		float size = Math.max(1f, Math.max(es.boundingBoxHeight, es.boundingBoxWidth));
		float s = scale / size;
		if (es instanceof LivingEntityRenderState living) {
			living.bodyRot = 180f;
			living.yRot = (float) Math.sin(t * 0.03f) * 35f; // idle head turning
			living.xRot = (float) Math.sin(t * 0.017f) * 8f;
			boolean walking = pet.position == dev.cosmeticstudio.model.PetPosition.FOLLOW;
			living.walkAnimationPos = walking ? state.walkAnimationPos : 0;
			living.walkAnimationSpeed = walking ? state.walkAnimationSpeed : 0;
			if (pet.spin && !walking) living.bodyRot = 180f + (t * 2f) % 360;
		}
		ps.pushPose();
		ps.scale(s, s, s);
		refreshCamera(mc);
		renderer.submit(es, ps, out, camera);
		ps.popPose();
		return es.boundingBoxHeight * s;
	}

	private void refreshCamera(Minecraft mc) {
		Camera cam = Compat.mainCamera();
		camera.orientation.set(cam.rotation());
		camera.pos = cam.position();
	}
}
