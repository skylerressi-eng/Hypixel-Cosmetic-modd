package dev.cosmeticstudio;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ModMetadataTest {
	@Test
	void modIdMatchesFabricModJson() {
		assertEquals("cosmeticstudio", CosmeticStudioClient.MOD_ID);
	}
}
