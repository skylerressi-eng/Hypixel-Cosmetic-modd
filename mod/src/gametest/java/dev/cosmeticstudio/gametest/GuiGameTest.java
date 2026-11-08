package dev.cosmeticstudio.gametest;

import dev.cosmeticstudio.compat.Compat;
import dev.cosmeticstudio.gui.CosmeticScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/** Milestone 4: opens the GUI wearing leather armor and screenshots every tab. */
public class GuiGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.waitFor(mc -> mc.player != null && mc.level != null);
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("item replace entity @p armor.chest with leather_chestplate");
			world.getServer().runCommand("item replace entity @p armor.legs with leather_leggings");
			world.getServer().runCommand("item replace entity @p armor.feet with leather_boots");
			world.getServer().runCommand("item replace entity @p armor.head with diamond_helmet");
			context.waitTicks(20);

			context.runOnClient(mc -> Compat.setScreen(new CosmeticScreen()));
			context.waitTicks(5);
			context.takeScreenshot("m4-gui-dyes");
			context.clickScreenButton("cosmeticstudio.tab.runes");
			context.waitTicks(3);
			context.takeScreenshot("m4-gui-runes");
			context.runOnClient(mc -> ((CosmeticScreen) Compat.screen()).selectSlot("PET"));
			context.waitTicks(3);
			context.takeScreenshot("m4-gui-pet-blocks");
			context.clickScreenButton("cosmeticstudio.tab.animals");
			context.waitTicks(10);
			context.takeScreenshot("m4-gui-pet-animals");
			context.clickScreenButton("cosmeticstudio.tab.pet_settings");
			context.waitTicks(3);
			context.takeScreenshot("m4-gui-pet-settings");
			context.runOnClient(mc -> {
				var store = dev.cosmeticstudio.CosmeticStudioClient.store();
				for (String n : new String[] {"Birch Fit", "Necron Black", "Rainbow Run"}) {
					var look = new dev.cosmeticstudio.model.Look();
					look.armor.put(dev.cosmeticstudio.model.ArmorSlot.CHESTPLATE, dev.cosmeticstudio.model.ArmorCosmetic.NONE.withDye("DYE_PURE_BLACK"));
					store.savePreset(n, look);
				}
				store.save();
				if (store.presets().size() < 3) throw new AssertionError("expected 3 presets, got " + store.presets().size());
				Compat.setScreen(new dev.cosmeticstudio.gui.PresetScreen(new CosmeticScreen(), new dev.cosmeticstudio.model.Look(), l -> {}));
			});
			context.waitTicks(3);
			context.takeScreenshot("presets");
			context.runOnClient(mc -> {
				var store = dev.cosmeticstudio.CosmeticStudioClient.store();
				for (String n : new java.util.ArrayList<>(store.presets().keySet())) store.deletePreset(n);
				store.save();
				Compat.setScreen(new dev.cosmeticstudio.config.ConfigScreen(null));
			});
			context.waitTicks(3);
			context.takeScreenshot("m9-config");
			context.runOnClient(mc -> Compat.setScreen(null));
		}
	}
}
