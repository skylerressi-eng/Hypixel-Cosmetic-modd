package dev.cosmeticstudio.gametest;

import dev.cosmeticstudio.CosmeticStudioClient;
import dev.cosmeticstudio.model.PetCosmetic;
import dev.cosmeticstudio.model.PetPosition;
import dev.cosmeticstudio.skyblock.Location;
import dev.cosmeticstudio.skyblock.RealPetHider;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;

/**
 * "Replace my SkyBlock pet": builds a SkyBlock-style pet (name-tag stand + player-head stand) next to the player,
 * then checks the real model is hidden and our fox is drawn in its place.
 */
public class ReplacePetGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.waitFor(mc -> mc.player != null && mc.level != null);
			var server = world.getServer();
			server.runCommand("time set noon");
			server.runCommand("execute at @p run summon armor_stand ~1.5 ~0.9 ~1.5 {Invisible:1b,Marker:1b,NoGravity:1b,CustomNameVisible:1b,CustomName:\"[Lv100] Golden Dragon\"}");
			server.runCommand("execute at @p run summon armor_stand ~1.5 ~-0.3 ~1.5 {Invisible:1b,NoGravity:1b,Small:1b,equipment:{head:{id:\"minecraft:player_head\",count:1}}}");
			context.waitTicks(10);
			context.runOnClient(mc -> {
				mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
				Location.forceForTests(Location.Area.SKYBLOCK);
			});
			context.waitTicks(25);
			context.takeScreenshot("pet-replace-before");

			context.runOnClient(mc -> {
				PetCosmetic pet = new PetCosmetic();
				pet.kind = PetCosmetic.Kind.ENTITY;
				pet.id = "minecraft:fox";
				pet.position = PetPosition.SKYBLOCK_PET;
				CosmeticStudioClient.store().setPet(pet);
			});
			context.waitTicks(25);
			context.runOnClient(mc -> {
				var model = RealPetHider.modelStand();
				if (model == null) throw new AssertionError("real SkyBlock pet model stand not found");
				if (!RealPetHider.isHidden(model)) throw new AssertionError("real pet model should be hidden in replace mode");
			});
			context.takeScreenshot("pet-replace-after");

			context.runOnClient(mc -> {
				Location.forceForTests(null);
				CosmeticStudioClient.store().setPet(new PetCosmetic());
				mc.options.setCameraType(CameraType.FIRST_PERSON);
			});
		}
	}
}
