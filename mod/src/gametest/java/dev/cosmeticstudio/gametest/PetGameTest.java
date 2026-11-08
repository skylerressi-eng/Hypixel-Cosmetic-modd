package dev.cosmeticstudio.gametest;

import dev.cosmeticstudio.CosmeticStudioClient;
import dev.cosmeticstudio.compat.Compat;
import dev.cosmeticstudio.gui.CosmeticScreen;
import dev.cosmeticstudio.model.PetCosmetic;
import dev.cosmeticstudio.model.PetPosition;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;

/** Milestone 6: block and mob pets in every position, with a name tag, in the world and in the GUI preview. */
public class PetGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.waitFor(mc -> mc.player != null && mc.level != null);
			world.getServer().runCommand("time set noon");
			context.waitTicks(10);
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));

			shot(context, PetCosmetic.Kind.ENTITY, "minecraft:fox", PetPosition.FOLLOW, "Birch Buddy");
			shot(context, PetCosmetic.Kind.BLOCK, "minecraft:diamond_block", PetPosition.LEFT_SHOULDER, null);
			shot(context, PetCosmetic.Kind.ENTITY, "minecraft:axolotl", PetPosition.RIGHT_SHOULDER, null);
			shot(context, PetCosmetic.Kind.ENTITY, "minecraft:chicken", PetPosition.HEAD, "Hat");
			shot(context, PetCosmetic.Kind.BLOCK, "minecraft:oak_log", PetPosition.RIGHT_ARM, null);
			shot(context, PetCosmetic.Kind.ENTITY, "minecraft:allay", PetPosition.BEHIND, null);

			context.runOnClient(mc -> {
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				Compat.setScreen(new CosmeticScreen());
				((CosmeticScreen) Compat.screen()).selectSlot("PET");
			});
			context.waitTicks(5);
			context.takeScreenshot("m6-pet-gui-preview");
			context.runOnClient(mc -> {
				Compat.setScreen(null);
				CosmeticStudioClient.store().setPet(new PetCosmetic());
				CosmeticStudioClient.store().save();
			});
		}
	}

	private static void shot(ClientGameTestContext context, PetCosmetic.Kind kind, String id, PetPosition pos, String name) {
		context.runOnClient(mc -> {
			PetCosmetic pet = new PetCosmetic();
			pet.kind = kind;
			pet.id = id;
			pet.position = pos;
			pet.name = name;
			CosmeticStudioClient.store().setPet(pet);
		});
		context.waitTicks(pos == PetPosition.FOLLOW ? 40 : 10);
		context.takeScreenshot("m6-pet-" + pos.name().toLowerCase() + "-" + id.substring(id.indexOf(':') + 1));
	}
}
