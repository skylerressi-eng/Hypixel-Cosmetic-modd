package dev.cosmeticstudio.gametest;

import dev.cosmeticstudio.CosmeticStudioClient;
import dev.cosmeticstudio.compat.Compat;
import dev.cosmeticstudio.gui.CosmeticScreen;
import dev.cosmeticstudio.model.ArmorCosmetic;
import dev.cosmeticstudio.model.ArmorSlot;
import dev.cosmeticstudio.render.Appearances;
import dev.cosmeticstudio.skyblock.RealRuneHider;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.world.item.Items;

/** Armor looks (any SkyBlock/vanilla piece) and real runes shown pre-selected + deselectable. */
public class LookGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.waitFor(mc -> mc.player != null && mc.level != null);
			var server = world.getServer();
			server.runCommand("time set noon");
			server.runCommand("item replace entity @p armor.head with diamond_helmet[custom_data={uuid:\"helm\"}]");
			server.runCommand("item replace entity @p armor.chest with iron_chestplate[custom_data={uuid:\"chest\"}]");
			server.runCommand("item replace entity @p armor.feet with leather_boots[custom_data={uuid:\"boots\",runes:{RAINBOW:3}}]");
			context.waitTicks(10);

			context.runOnClient(mc -> {
				if (!Appearances.stack("sb:SUPERIOR_DRAGON_HELMET").is(Items.PLAYER_HEAD)) throw new AssertionError("SkyBlock helmet look should be a textured head");
				if (Appearances.vanillaFor(ArmorSlot.HELMET).stream().noneMatch(i -> i == Items.CARVED_PUMPKIN)) throw new AssertionError("pumpkin should be a helmet look");
				var store = CosmeticStudioClient.store();
				store.set("helm", ArmorSlot.HELMET, ArmorCosmetic.NONE.withLook("sb:SUPERIOR_DRAGON_HELMET"));
				store.set("chest", ArmorSlot.CHESTPLATE, ArmorCosmetic.NONE.withLook("sb:ARACHNE_CHESTPLATE").withDye("DYE_AQUAMARINE"));
				mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
			});
			context.waitTicks(15);
			context.takeScreenshot("looks-worn");

			// The real rune on the boots shows as pre-selected in the Runes tab.
			context.runOnClient(mc -> {
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				Compat.setScreen(new CosmeticScreen());
				var screen = (CosmeticScreen) Compat.screen();
				screen.selectSlot("BOOTS");
				screen.selectTabForTests("RUNES");
				if (!"RAINBOW_RUNE".equals(screen.selectedForTests())) throw new AssertionError("real rune should be pre-selected, was " + screen.selectedForTests());
			});
			context.waitTicks(3);
			context.takeScreenshot("runes-preselected");
			context.runOnClient(mc -> {
				var screen = (CosmeticScreen) Compat.screen();
				screen.selectSlot("HELMET");
				screen.selectTabForTests("LOOKS");
			});
			context.waitTicks(5);
			context.takeScreenshot("looks-tab");
			context.runOnClient(mc -> Compat.setScreen(null));

			// Deselecting the real rune hides its particles even with the global switch off.
			context.runOnClient(mc -> {
				CosmeticStudioClient.config().hideRealRunes = false;
				CosmeticStudioClient.store().set("boots", ArmorSlot.BOOTS, ArmorCosmetic.NONE.withHideRealRune(true));
			});
			int before = context.computeOnClient(mc -> RealRuneHider.hiddenCount());
			server.runCommand("execute at @p run particle minecraft:heart ~ ~0.2 ~ 0 0 0 0 1 force");
			context.waitTicks(5);
			if (context.computeOnClient(mc -> RealRuneHider.hiddenCount()) <= before) throw new AssertionError("deselected real rune should be hidden");

			context.runOnClient(mc -> {
				CosmeticStudioClient.store().clearAllArmor();
				CosmeticStudioClient.store().save();
			});
		}
	}
}
