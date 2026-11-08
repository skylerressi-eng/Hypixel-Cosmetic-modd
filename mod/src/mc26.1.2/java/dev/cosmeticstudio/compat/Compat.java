package dev.cosmeticstudio.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

/** Minecraft 26.1.2 implementation of APIs that differ between supported versions. */
public final class Compat {
	private Compat() {}

	public static void setScreen(@Nullable Screen screen) {
		Minecraft.getInstance().setScreen(screen);
	}

	public static @Nullable Screen screen() {
		return Minecraft.getInstance().screen;
	}

	/** True while the entity is swinging its arm (used for "on attack" rune effects). */
	public static boolean isSwinging(net.minecraft.world.entity.LivingEntity e) {
		return e.swinging;
	}

	public static net.minecraft.client.Camera mainCamera() {
		return Minecraft.getInstance().gameRenderer.getMainCamera();
	}

	public static void submitNameTag(net.minecraft.client.renderer.SubmitNodeCollector out, com.mojang.blaze3d.vertex.PoseStack ps,
			net.minecraft.world.phys.Vec3 at, net.minecraft.network.chat.Component name, int light,
			net.minecraft.client.renderer.state.level.CameraRenderState camera) {
		out.submitNameTag(ps, at, 0, name, false, light, 0.0, camera);
	}

	/** Position of an incoming particle packet. */
	public static net.minecraft.world.phys.Vec3 particlePos(net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket packet) {
		return new net.minecraft.world.phys.Vec3(packet.getX(), packet.getY(), packet.getZ());
	}
}
