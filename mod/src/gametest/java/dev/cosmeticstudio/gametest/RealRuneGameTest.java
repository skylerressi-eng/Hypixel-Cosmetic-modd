package dev.cosmeticstudio.gametest;

import dev.cosmeticstudio.CosmeticStudioClient;
import dev.cosmeticstudio.compat.Compat;
import dev.cosmeticstudio.gui.CosmeticScreen;
import dev.cosmeticstudio.model.ArmorCosmetic;
import dev.cosmeticstudio.model.ArmorSlot;
import dev.cosmeticstudio.skyblock.RealRuneHider;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * Real SkyBlock runes vs cosmetic runes:
 * - "Hide real runes" hides server particles right around you while you wear a runed item (but not ones further away);
 * - "Remove all my runes" clears old cosmetic runes everywhere and keeps dyes.
 */
public class RealRuneGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.waitFor(mc -> mc.player != null && mc.level != null);
			var server = world.getServer();
			// Boots that carry a real SkyBlock rune, like Hypixel sends them.
			server.runCommand("item replace entity @p armor.feet with leather_boots[custom_data={id:\"FARM_SUIT_BOOTS\",uuid:\"aaaa\",runes:{RAINBOW:3}}]");
			context.waitTicks(10);

			context.runOnClient(mc -> CosmeticStudioClient.config().hideRealRunes = true);
			int before = context.computeOnClient(mc -> RealRuneHider.hiddenCount());
			server.runCommand("execute at @p run particle minecraft:heart ~ ~0.2 ~ 0 0 0 0 1 force");
			context.waitTicks(5);
			int afterNear = context.computeOnClient(mc -> RealRuneHider.hiddenCount());
			server.runCommand("execute at @p run particle minecraft:heart ~6 ~1 ~ 0 0 0 0 1 force");
			context.waitTicks(5);
			int afterFar = context.computeOnClient(mc -> RealRuneHider.hiddenCount());
			if (afterNear <= before) throw new AssertionError("particle at your feet should be hidden (real rune)");
			if (afterFar != afterNear) throw new AssertionError("particle 6 blocks away must not be hidden");

			context.runOnClient(mc -> CosmeticStudioClient.config().hideRealRunes = false);
			server.runCommand("execute at @p run particle minecraft:heart ~ ~0.2 ~ 0 0 0 0 1 force");
			context.waitTicks(5);
			if (context.computeOnClient(mc -> RealRuneHider.hiddenCount()) != afterFar) throw new AssertionError("nothing hidden when the switch is off");

			// Old cosmetic runes on other items/slots get removed; dyes stay.
			context.runOnClient(mc -> {
				var store = CosmeticStudioClient.store();
				store.set("old-item", ArmorSlot.HELMET, ArmorCosmetic.NONE.withRune("SMITTEN_RUNE", 3));
				store.set(null, ArmorSlot.CHESTPLATE, ArmorCosmetic.NONE.withRune("GRAND_FREEZING_RUNE", 1).withDye("DYE_PURE_BLACK"));
				int removed = store.clearAllRunes();
				if (removed != 2) throw new AssertionError("expected 2 runes removed, got " + removed);
				if (store.get("old-item", ArmorSlot.HELMET).rune() != null) throw new AssertionError("helmet rune still there");
				if (!"DYE_PURE_BLACK".equals(store.get(null, ArmorSlot.CHESTPLATE).dye())) throw new AssertionError("dye should be kept");
				store.clearAllArmor();
				store.save();
				Compat.setScreen(new CosmeticScreen());
				((CosmeticScreen) Compat.screen()).selectSlot("BOOTS");
			});
			context.clickScreenButton("cosmeticstudio.tab.runes");
			context.waitTicks(3);
			context.takeScreenshot("runes-real-rune-controls");
			context.runOnClient(mc -> Compat.setScreen(null));
		}
	}
}
