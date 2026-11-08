package dev.cosmeticstudio.catalog;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * One SkyBlock rune. {@code slot} is the item type SkyBlock allows it on (HELMET, CHESTPLATE, BOOTS, WEAPON, BOW, AXE,
 * FARMING_TOOL). {@code colorCode} is the Minecraft formatting colour of its name (0-9a-f).
 */
public record RuneDef(String id, String name, @Nullable String colorCode, @Nullable String slot, String slotLabel, String description,
		List<Level> levels, boolean unique) {

	public record Level(int level, @Nullable String rarity) {}

	public int maxLevel() {
		return levels.stream().mapToInt(Level::level).max().orElse(1);
	}

	public @Nullable String rarityAt(int level) {
		return levels.stream().filter(l -> l.level() == level).map(Level::rarity).findFirst().orElse(levels.isEmpty() ? null : levels.getLast().rarity());
	}
}
