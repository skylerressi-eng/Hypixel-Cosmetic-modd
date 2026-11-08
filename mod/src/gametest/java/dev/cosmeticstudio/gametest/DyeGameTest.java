package dev.cosmeticstudio.gametest;

import dev.cosmeticstudio.CosmeticStudioClient;
import dev.cosmeticstudio.compat.Compat;
import dev.cosmeticstudio.model.ArmorCosmetic;
import dev.cosmeticstudio.model.ArmorSlot;
import dev.cosmeticstudio.resolve.CosmeticResolver;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.EquipmentSlot;

/**
 * Milestone 3: a leather chestplate with a SkyBlock UUID gets Pure Black (by UUID), leather boots without a UUID get
 * Aquamarine (slot fallback), and an iron helmet must stay untouched (not dyeable).
 */
public class DyeGameTest implements FabricClientGameTest {
	static final String UUID = "11111111-2222-3333-4444-555555555555";

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.waitFor(mc -> mc.player != null && mc.level != null);
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("item replace entity @p armor.chest with leather_chestplate[custom_data={uuid:\"" + UUID + "\",id:\"TEST_CHESTPLATE\"}]");
			world.getServer().runCommand("item replace entity @p armor.feet with leather_boots");
			world.getServer().runCommand("item replace entity @p armor.head with iron_helmet");
			world.getServer().runCommand("item replace entity @p armor.legs with leather_leggings[dyed_color=16711680]");
			context.waitTicks(20);

			context.runOnClient(mc -> {
				var store = CosmeticStudioClient.store();
				store.set(UUID, ArmorSlot.CHESTPLATE, ArmorCosmetic.NONE.withDye("DYE_PURE_BLACK"));
				store.set(null, ArmorSlot.BOOTS, ArmorCosmetic.NONE.withDye("DYE_AQUAMARINE"));
				store.save();

				var p = mc.player;
				expect(CosmeticResolver.dyeColor(p.getItemBySlot(EquipmentSlot.CHEST), p), 0x000000, "chestplate by UUID");
				expect(CosmeticResolver.dyeColor(p.getItemBySlot(EquipmentSlot.FEET), p), 0x7FFFD4, "boots by slot");
				expect(CosmeticResolver.dyeColor(p.getItemBySlot(EquipmentSlot.HEAD), p), null, "iron helmet (not dyeable)");
				expect(CosmeticResolver.dyeColor(p.getItemBySlot(EquipmentSlot.LEGS), p), null, "leggings without cosmetic keep vanilla red");
				mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
			});
			context.waitTicks(10);
			context.takeScreenshot("m3-dyes-worn");

			context.runOnClient(mc -> {
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				Compat.setScreen(new InventoryScreen(mc.player));
			});
			context.waitTicks(10);
			context.takeScreenshot("m3-dyes-inventory");
			context.runOnClient(mc -> {
				Compat.setScreen(null);
				CosmeticStudioClient.store().clearAllArmor();
				CosmeticStudioClient.store().save();
			});
		}
	}

	static void expect(Integer actual, Integer expected, String what) {
		if (!java.util.Objects.equals(actual, expected)) {
			throw new AssertionError(what + ": expected " + (expected == null ? "null" : Integer.toHexString(expected)) + " but was "
					+ (actual == null ? "null" : Integer.toHexString(actual)));
		}
	}
}
