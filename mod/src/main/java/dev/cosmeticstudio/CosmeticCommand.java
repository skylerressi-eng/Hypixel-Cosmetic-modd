package dev.cosmeticstudio;

import dev.cosmeticstudio.compat.Compat;
import dev.cosmeticstudio.config.ConfigScreen;
import dev.cosmeticstudio.gui.CosmeticScreen;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.Minecraft;

/**
 * Registers {@code /cosmetic} as a <em>client-side</em> command.
 * Fabric intercepts it locally, so it is never sent to the server (Hypixel never sees it).
 */
public final class CosmeticCommand {
	/** Number of times the command has run this session (read by the in-game test). */
	static int invocations;

	private CosmeticCommand() {}

	public static int invocations() {
		return invocations;
	}

	public static void register(String version) {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) ->
				dispatcher.register(ClientCommands.literal("cosmetic")
						.then(ClientCommands.literal("config").executes(ctx -> {
							Minecraft mc = ctx.getSource().getClient();
							mc.schedule(() -> Compat.setScreen(new ConfigScreen(null)));
							return 1;
						}))
						.executes(ctx -> {
					invocations++;
					// Open on the next tick: the chat screen is still closing while the command runs.
					Minecraft mc = ctx.getSource().getClient();
					mc.schedule(() -> Compat.setScreen(new CosmeticScreen()));
					return 1;
				})));
	}
}
