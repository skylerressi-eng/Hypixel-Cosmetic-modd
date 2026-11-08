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
}
