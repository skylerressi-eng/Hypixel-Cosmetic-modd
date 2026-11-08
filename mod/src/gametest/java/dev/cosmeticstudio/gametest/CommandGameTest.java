package dev.cosmeticstudio.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import dev.cosmeticstudio.CosmeticCommand;
import dev.cosmeticstudio.compat.Compat;
import dev.cosmeticstudio.gui.CosmeticScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/** Milestone 1 smoke test: the mod loads in a real client and /cosmetic runs client-side when typed into chat and opens the GUI. */
public class CommandGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			// Portable across 26.1.2 and 26.3 (the chunk-render wait API moved between versions).
			context.waitFor(mc -> mc.player != null && mc.level != null);
			context.waitTicks(40);
			int before = CosmeticCommand.invocations();

			context.getInput().pressKey(options -> options.keyChat);
			context.waitTicks(2);
			context.getInput().typeChars("/cosmetic");
			context.getInput().pressKey(InputConstants.KEY_RETURN);
			context.waitTicks(5);

			if (CosmeticCommand.invocations() != before + 1) {
				throw new AssertionError("/cosmetic did not execute (invocations " + before + " -> " + CosmeticCommand.invocations() + ")");
			}
			context.waitTicks(5);
			if (!context.computeOnClient(mc -> Compat.screen() instanceof CosmeticScreen)) {
				throw new AssertionError("/cosmetic did not open the Cosmetic Studio screen");
			}
			context.takeScreenshot("m4-gui-open");
			context.runOnClient(mc -> Compat.setScreen(null));
		}
	}
}
