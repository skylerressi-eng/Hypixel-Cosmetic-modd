package dev.cosmeticstudio;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.cosmeticstudio.catalog.Catalog;
import dev.cosmeticstudio.catalog.DyeDef;
import dev.cosmeticstudio.skyblock.Location;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class CatalogTest {
	static Catalog bundled() throws Exception {
		Path root = Path.of("..").toAbsolutePath().normalize();
		JsonObject c = JsonParser.parseString(Files.readString(root.resolve("catalog/cosmetics.json"))).getAsJsonObject();
		JsonObject e = JsonParser.parseString(Files.readString(root.resolve("catalog/rune_effects.json"))).getAsJsonObject();
		return Catalog.parse(c, e);
	}

	@Test
	void bundledCatalogParsesWithVerifiedColours() throws Exception {
		Catalog cat = bundled();
		assertTrue(cat.dyes().size() >= 69, "dyes");
		assertTrue(cat.runes().size() >= 69, "runes");
		assertEquals(0x000000, cat.dye("DYE_PURE_BLACK").color());
		assertEquals(0x7FFFD4, cat.dye("DYE_AQUAMARINE").color());
		assertTrue(cat.dye("DYE_ROSE").animated());
		assertEquals(3, cat.rune("MUSIC_RUNE").maxLevel());
		assertEquals("WEAPON", cat.rune("MUSIC_RUNE").slot());
	}

	@Test
	void animatedDyeInterpolatesAndLoops() {
		DyeDef d = new DyeDef("X", "X", null, 0, true, List.of(0x000000, 0xFFFFFF), 10, false);
		assertEquals(0x000000, d.colorAt(0));
		assertEquals(0x7F7F7F, d.colorAt(5));
		assertEquals(0xFFFFFF, d.colorAt(10));
		assertEquals(0x000000, d.colorAt(20)); // looped
	}

	@Test
	void hueSortsGreysFirst() {
		DyeDef grey = new DyeDef("G", "G", null, 0x808080, false, List.of(), 1, true);
		DyeDef red = new DyeDef("R", "R", null, 0xFF0000, false, List.of(), 1, true);
		DyeDef blue = new DyeDef("B", "B", null, 0x0000FF, false, List.of(), 1, true);
		assertTrue(grey.hue() < red.hue());
		assertTrue(red.hue() < blue.hue());
	}

	@Test
	void locationClassification() {
		assertEquals(Location.Area.DUNGEON, Location.classify("Cleared: 40%\n⏣ The Catacombs (F7)\n"));
		assertEquals(Location.Area.KUUDRA, Location.classify(" ⏣ Kuudra's Hollow (T5)"));
		assertEquals(Location.Area.SKYBLOCK, Location.classify(" ⏣ Village\nPurse: 1,000"));
	}
}
