package dev.cosmeticstudio.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

/** Minecraft 26.3 implementation of APIs that differ between supported versions. */
public final class Compat {
	private Compat() {}

	public static void setScreen(@Nullable Screen screen) {
		Minecraft.getInstance().gui.setScreen(screen);
	}

	public static @Nullable Screen screen() {
		return Minecraft.getInstance().gui.screen();
	}

	private static final java.lang.invoke.VarHandle SWING_STATE;
	static {
		java.lang.invoke.VarHandle h = null;
		try {
			// 26.x is unobfuscated, so this private field name is stable for the version.
			h = java.lang.invoke.MethodHandles.privateLookupIn(net.minecraft.world.entity.LivingEntity.class, java.lang.invoke.MethodHandles.lookup())
					.findVarHandle(net.minecraft.world.entity.LivingEntity.class, "swingState", net.minecraft.world.entity.LivingEntity.SwingState.class);
		} catch (ReflectiveOperationException ignored) {
		}
		SWING_STATE = h;
	}

	/** True while the entity is swinging its arm (used for "on attack" rune effects). */
	public static boolean isSwinging(net.minecraft.world.entity.LivingEntity e) {
		if (SWING_STATE == null) return false;
		var state = (net.minecraft.world.entity.LivingEntity.SwingState) SWING_STATE.get(e);
		return state != null && state.isSwinging();
	}

	public static net.minecraft.client.Camera mainCamera() {
		return Minecraft.getInstance().gameRenderer.mainCamera();
	}

	public static void submitNameTag(net.minecraft.client.renderer.SubmitNodeCollector out, com.mojang.blaze3d.vertex.PoseStack ps,
			net.minecraft.world.phys.Vec3 at, net.minecraft.network.chat.Component name, int light,
			net.minecraft.client.renderer.state.level.CameraRenderState camera) {
		out.submitNameTag(ps, at, 0, name, false, light, camera);
	}

	/** Position of an incoming particle packet. */
	public static net.minecraft.world.phys.Vec3 particlePos(net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket packet) {
		return new net.minecraft.world.phys.Vec3(packet.x(), packet.y(), packet.z());
	}
}
