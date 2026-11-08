package dev.cosmeticstudio.gametest;

import dev.cosmeticstudio.CosmeticStudioClient;
import dev.cosmeticstudio.model.PetCosmetic;
import dev.cosmeticstudio.model.PetPosition;
import dev.cosmeticstudio.render.PetAnimations;
import dev.cosmeticstudio.render.PetEntities;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;

/** Cute pet behaviour: play (signature moves), sit, sleep with "z Z", hearts when crouching, waking up when you move. */
public class PetAnimationGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.waitFor(mc -> mc.player != null && mc.level != null);
			world.getServer().runCommand("time set noon");
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));

			pet(context, "minecraft:fox");
			context.waitTicks(30);
			// Play: the fox's signature move is a pounce (crouch, then leap).
			context.runOnClient(mc -> {
				PetEntities.setOwnerStillForTests(mc.player.getUUID(), 5 * 20);
				PetEntities.startActionForTests(mc.player.getUUID(), PetAnimations.Action.SIGNATURE, 40);
			});
			context.waitTicks(25);
			context.takeScreenshot("anim-fox-pounce");
			expectMood(context, PetAnimations.Mood.PLAY);
			// Sit, then sleep.
			context.runOnClient(mc -> PetEntities.setOwnerStillForTests(mc.player.getUUID(), 21 * 20));
			context.waitTicks(5);
			expectMood(context, PetAnimations.Mood.SIT);
			context.takeScreenshot("anim-fox-sit");
			context.runOnClient(mc -> PetEntities.setOwnerStillForTests(mc.player.getUUID(), 41 * 20));
			context.waitTicks(20);
			expectMood(context, PetAnimations.Mood.SLEEP);
			context.takeScreenshot("anim-fox-sleep");

			for (String mob : new String[] {"minecraft:cat", "minecraft:wolf", "minecraft:sheep"}) {
				pet(context, mob);
				context.waitTicks(10);
				boolean sleepy = mob.endsWith("cat");
				context.runOnClient(mc -> {
					PetEntities.setOwnerStillForTests(mc.player.getUUID(), sleepy ? 41 * 20 : mob.endsWith("wolf") ? 21 * 20 : 5 * 20);
					if (mob.endsWith("sheep")) PetEntities.startActionForTests(mc.player.getUUID(), PetAnimations.Action.SIGNATURE, 60);
				});
				context.waitTicks(20);
				context.takeScreenshot("anim-" + mob.substring(10));
			}

			// Crouch next to the pet: hearts and a hop, and it wakes up.
			pet(context, "minecraft:fox");
			context.runOnClient(mc -> PetEntities.setOwnerStillForTests(mc.player.getUUID(), 41 * 20));
			context.waitTicks(5);
			context.runOnClient(mc -> mc.options.keyShift.setDown(true));
			context.waitTicks(15);
			context.takeScreenshot("anim-hearts");
			context.runOnClient(mc -> mc.options.keyShift.setDown(false));
			expectMoodNot(context, PetAnimations.Mood.SLEEP);

			// Moving wakes them up.
			context.runOnClient(mc -> PetEntities.setOwnerStillForTests(mc.player.getUUID(), 41 * 20));
			context.waitTicks(3);
			world.getServer().runCommand("execute as @p at @s run tp @s ~2 ~ ~");
			context.waitTicks(5);
			expectMood(context, PetAnimations.Mood.FOLLOW);

			context.runOnClient(mc -> {
				CosmeticStudioClient.store().setPet(new PetCosmetic());
				mc.options.setCameraType(CameraType.FIRST_PERSON);
			});
		}
	}

	private static void pet(ClientGameTestContext context, String id) {
		context.runOnClient(mc -> {
			PetCosmetic pet = new PetCosmetic();
			pet.kind = PetCosmetic.Kind.ENTITY;
			pet.id = id;
			pet.position = PetPosition.FOLLOW;
			CosmeticStudioClient.store().setPet(pet);
		});
	}

	private static void expectMood(ClientGameTestContext context, PetAnimations.Mood mood) {
		context.runOnClient(mc -> {
			var m = PetEntities.mood(mc.player.getUUID());
			if (m != mood) throw new AssertionError("expected mood " + mood + " but was " + m);
		});
	}

	private static void expectMoodNot(ClientGameTestContext context, PetAnimations.Mood mood) {
		context.runOnClient(mc -> {
			var m = PetEntities.mood(mc.player.getUUID());
			if (m == mood) throw new AssertionError("mood should not be " + mood);
		});
	}
}
