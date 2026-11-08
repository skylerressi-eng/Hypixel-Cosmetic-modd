package dev.cosmeticstudio;

import static org.junit.jupiter.api.Assertions.*;

import dev.cosmeticstudio.model.ArmorCosmetic;
import dev.cosmeticstudio.model.ArmorSlot;
import dev.cosmeticstudio.model.Look;
import dev.cosmeticstudio.model.PetCosmetic;
import dev.cosmeticstudio.model.PetPosition;
import dev.cosmeticstudio.sync.SyncClient;
import org.junit.jupiter.api.Test;

class SyncJsonTest {
	@Test
	void lookRoundTripsAndIsSanitised() {
		Look look = new Look();
		look.armor.put(ArmorSlot.BOOTS, ArmorCosmetic.NONE.withDye("DYE_AQUAMARINE").withRune("RAINBOW_RUNE", 3));
		look.pet.kind = PetCosmetic.Kind.BLOCK;
		look.pet.id = "minecraft:oak_log";
		look.pet.name = "Birch Fit";
		look.pet.position = PetPosition.RIGHT_ARM;
		look.pet.hideRealPet = true;

		var json = SyncClient.lookJson(look);
		assertFalse(json.toString().contains("hideRealPet"), "private preference must not be uploaded");
		Look back = SyncClient.parseLook(json);
		assertEquals("DYE_AQUAMARINE", back.get(ArmorSlot.BOOTS).dye());
		assertEquals(3, back.get(ArmorSlot.BOOTS).runeLevel());
		assertEquals(PetPosition.RIGHT_ARM, back.pet.position);
		assertEquals("Birch Fit", back.pet.name);

		json.getAsJsonObject("pet").addProperty("name", "§cA very very long pet name indeed!!");
		json.getAsJsonObject("pet").addProperty("scale", 99);
		json.getAsJsonObject("pet").addProperty("position", "NOT_A_POSITION");
		Look bad = SyncClient.parseLook(json);
		assertTrue(bad.pet.name.length() <= PetCosmetic.MAX_NAME);
		assertFalse(bad.pet.name.contains("§"));
		assertEquals(2.5f, bad.pet.scale);
		assertFalse(bad.pet.isActive(), "invalid enum from server -> no pet");
	}
}
