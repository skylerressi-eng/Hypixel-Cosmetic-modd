package dev.cosmeticstudio.gametest;

import dev.cosmeticstudio.CosmeticStudioClient;
import dev.cosmeticstudio.model.ArmorCosmetic;
import dev.cosmeticstudio.model.ArmorSlot;
import dev.cosmeticstudio.render.RuneParticles;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;

/** Milestone 5: helmet + chestplate runes (approximate effects opted in) emit particles; turning particles off stops them. */
public class RuneGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.waitFor(mc -> mc.player != null && mc.level != null);
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("item replace entity @p armor.head with leather_helmet");
			world.getServer().runCommand("item replace entity @p armor.chest with leather_chestplate");
			context.waitTicks(10);
			context.runOnClient(mc -> {
				var cfg = CosmeticStudioClient.config();
				cfg.showUnverifiedRuneEffects = true;
				var store = CosmeticStudioClient.store();
				store.set(null, ArmorSlot.HELMET, ArmorCosmetic.NONE.withRune("SMITTEN_RUNE", 3));
				store.set(null, ArmorSlot.CHESTPLATE, ArmorCosmetic.NONE.withRune("GRAND_FREEZING_RUNE", 3).withDye("DYE_AQUAMARINE"));
				mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
			});
			long start = RuneParticles.emitted();
			context.waitTicks(30);
			long on = RuneParticles.emitted() - start;
			context.takeScreenshot("m5-runes-on");

			context.runOnClient(mc -> CosmeticStudioClient.config().particles = false);
			long mid = RuneParticles.emitted();
			context.waitTicks(30);
			long off = RuneParticles.emitted() - mid;
			if (on < 5 || off != 0) throw new AssertionError("expected rune particles when on (" + on + ") and none when off (" + off + ")");

			context.runOnClient(mc -> {
				var cfg = CosmeticStudioClient.config();
				cfg.particles = true;
				cfg.showUnverifiedRuneEffects = false;
				CosmeticStudioClient.store().clearAllArmor();
				mc.options.setCameraType(CameraType.FIRST_PERSON);
			});
		}
	}
}
