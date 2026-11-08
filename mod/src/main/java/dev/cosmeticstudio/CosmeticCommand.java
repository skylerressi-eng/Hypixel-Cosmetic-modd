package dev.cosmeticstudio;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.network.chat.Component;

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
				dispatcher.register(ClientCommands.literal("cosmetic").executes(ctx -> {
					invocations++;
					// sendFeedback only prints to the local chat HUD; nothing is transmitted.
					ctx.getSource().sendFeedback(Component.translatable("cosmeticstudio.command.placeholder", version));
					return 1;
				})));
	}
}
