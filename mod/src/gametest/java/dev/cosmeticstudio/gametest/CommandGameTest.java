package dev.cosmeticstudio.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import dev.cosmeticstudio.CosmeticCommand;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/** Milestone 1 smoke test: the mod loads in a real client and /cosmetic runs client-side when typed into chat. */
public class CommandGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			int before = CosmeticCommand.invocations();

			context.getInput().pressKey(options -> options.keyChat);
			context.waitTicks(2);
			context.getInput().typeChars("/cosmetic");
			context.getInput().pressKey(InputConstants.KEY_RETURN);
			context.waitTicks(5);

			if (CosmeticCommand.invocations() != before + 1) {
				throw new AssertionError("/cosmetic did not execute (invocations " + before + " -> " + CosmeticCommand.invocations() + ")");
			}
			context.takeScreenshot("m1-cosmetic-command");
		}
	}
}
