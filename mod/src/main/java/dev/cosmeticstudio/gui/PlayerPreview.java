package dev.cosmeticstudio.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Live 3D preview of the local player (drag to rotate, scroll to zoom). Rendering goes through the normal entity
 * renderer, so the same hooks that dye worn armor and draw the pet show the preview automatically.
 */
public final class PlayerPreview extends AbstractWidget {
	private float yaw = 200f; // slight 3/4 view
	private float zoom = 1f;

	public PlayerPreview(int x, int y, int w, int h) {
		super(x, y, w, h, Component.translatable("cosmeticstudio.gui.preview"));
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		g.fill(getX(), getY(), getX() + width, getY() + height, 0x80000000);
		g.outline(getX(), getY(), width, height, 0xFF555555);
		Minecraft mc = Minecraft.getInstance();
		LivingEntity player = mc.player;
		if (player == null) return;

		@SuppressWarnings("unchecked")
		EntityRenderer<? super LivingEntity, ?> renderer = mc.getEntityRenderDispatcher().getRenderer(player);
		EntityRenderState state = renderer.createRenderState(player, 1.0F);
		state.shadowPieces.clear();
		state.outlineColor = 0;
		state.nameTag = null;
		if (state instanceof LivingEntityRenderState living) {
			living.bodyRot = yaw;
			living.yRot = 0;
			living.xRot = 0;
			if (living.pose == Pose.FALL_FLYING) living.pose = Pose.STANDING;
			living.boundingBoxWidth /= living.scale;
			living.boundingBoxHeight /= living.scale;
			living.scale = 1.0F;
		}
		int size = (int) (Math.min(width / 1.6f, height / 2.6f) * zoom);
		Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI);
		Quaternionf camera = new Quaternionf().rotateX((float) Math.toRadians(-8));
		rotation.mul(camera);
		Vector3f translation = new Vector3f(0, state.boundingBoxHeight / 2.0F + 0.0625F, 0);
		g.entity(state, size, translation, rotation, camera, getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1);
		g.centeredText(mc.font, Component.translatable("cosmeticstudio.gui.drag_hint"), getX() + width / 2, getY() + height - 10, 0xFF888888);
	}

	@Override
	protected void onDrag(MouseButtonEvent event, double dx, double dy) {
		yaw = (yaw - (float) dx * 2f) % 360f;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double sx, double sy) {
		if (!isMouseOver(x, y)) return false;
		zoom = Math.max(0.6f, Math.min(1.8f, zoom + (float) sy * 0.1f));
		return true;
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {}
}
