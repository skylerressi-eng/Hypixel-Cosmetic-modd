package dev.cosmeticstudio;

import static org.junit.jupiter.api.Assertions.*;

import dev.cosmeticstudio.skyblock.RealPetHider;
import org.junit.jupiter.api.Test;

class PetTest {
	@Test
	void parsesSkyBlockPetNameTags() {
		assertEquals("Ammonite", RealPetHider.parseTag("[Lv99] Ammonite"));
		assertEquals("Endermite", RealPetHider.parseTag("[Lv100] Endermite ✦"));
		assertEquals("Golden Dragon", RealPetHider.parseTag("[Lv200] Golden Dragon"));
		assertNull(RealPetHider.parseTag("[Lv30]  Crypt Ghoul 2,000/2,000❤"));
		assertNull(RealPetHider.parseTag("Some Player"));
	}
}
