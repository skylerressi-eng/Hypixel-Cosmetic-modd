package dev.cosmeticstudio.skyblock;

import dev.cosmeticstudio.CosmeticStudioClient;
import dev.cosmeticstudio.model.PetCosmetic;
import dev.cosmeticstudio.model.PetPosition;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Finds the local player's real SkyBlock pet and hides it on <b>their own screen</b> (render-side only), either because
 * "Hide my real SkyBlock pet" is on or because the cosmetic pet uses "Replace my SkyBlock pet" (then the cosmetic is
 * drawn exactly where the real pet model is).
 *
 * How SkyBlock pets look client-side (same identification SkyHanni uses): an invisible armor stand whose custom name is
 * the pet name tag {@code §8[§7Lv99§8] §6Ammonite}, plus an armor stand wearing a player head (the pet model) just below.
 * The tag does not include the owner, so we pick the matching tag closest to us, preferring the one whose pet name equals
 * our active pet from the tab list "Pet:" widget (e.g. {@code [Lvl 100] Golden Dragon}).
 */
public final class RealPetHider {
	/** Pet name tag, e.g. "[Lv99] Ammonite" or "[Lv100] Endermite ✦" (formatting already stripped). */
	static final Pattern NAMETAG = Pattern.compile("^\\[Lv(\\d+)] (?:\\[\\d+✦] )?([A-Za-z][\\w -]*?)(?: ✦)?$");
	/** Tab list pet line, e.g. "[Lvl 100] Golden Dragon". */
	static final Pattern TAB_PET = Pattern.compile("\\[Lvl [\\d,]+] (?:\\[\\d+✦] )?([A-Za-z][\\w -]*?)(?: ✦)?$");

	private static Set<Entity> hidden = Collections.newSetFromMap(new WeakHashMap<>());
	private static WeakReference<ArmorStand> model = new WeakReference<>(null);
	private static int ticks;

	private RealPetHider() {}

	public static boolean isHidden(Entity e) {
		return !hidden.isEmpty() && hidden.contains(e);
	}

	/** The armor stand showing our real pet's model (head), if found. Used by "Replace my SkyBlock pet". */
	public static @Nullable ArmorStand modelStand() {
		ArmorStand s = model.get();
		return s != null && !s.isRemoved() ? s : null;
	}

	public static void tick(Minecraft mc) {
		if (++ticks % 5 != 0) return;
		Set<Entity> next = Collections.newSetFromMap(new WeakHashMap<>());
		ArmorStand foundModel = null;
		PetCosmetic pet = CosmeticStudioClient.store().pet();
		boolean replace = pet.isActive() && pet.position == PetPosition.SKYBLOCK_PET;
		if (mc.player != null && mc.level != null && pet.isActive() && (pet.hideRealPet || replace) && Location.inSkyBlock()
				&& CosmeticStudioClient.config().showMyCosmetics) {
			String active = activePetFromTab(mc);
			double radius = active != null ? 6.0 : 4.0;
			AABB box = mc.player.getBoundingBox().inflate(radius);
			ArmorStand best = null;
			double bestDist = Double.MAX_VALUE;
			for (ArmorStand stand : mc.level.getEntitiesOfClass(ArmorStand.class, box, ArmorStand::hasCustomName)) {
				String petName = petNameOf(stand.getCustomName());
				if (petName == null || (active != null && !petName.equalsIgnoreCase(active))) continue;
				double d = stand.distanceToSqr(mc.player);
				if (d < bestDist) {
					bestDist = d;
					best = stand;
				}
			}
			if (best != null) {
				final ArmorStand tag = best;
				// The head-wearing stand that shows the pet model sits just below its name tag.
				AABB below = tag.getBoundingBox().inflate(0.8, 2.0, 0.8);
				List<ArmorStand> models = mc.level.getEntitiesOfClass(ArmorStand.class, below,
						s -> s != tag && s.getItemBySlot(EquipmentSlot.HEAD).is(Items.PLAYER_HEAD));
				foundModel = models.stream().min((a, b) -> Double.compare(a.distanceToSqr(tag), b.distanceToSqr(tag))).orElse(null);
				if (foundModel != null) next.add(foundModel);
				// Keep the real name tag in replace mode unless we hide it or show our own name instead.
				boolean ownName = pet.showName && pet.name != null;
				if (pet.hideRealPet || !replace || ownName) next.add(tag);
			}
		}
		model = new WeakReference<>(foundModel);
		hidden = next;
	}

	public static @Nullable String petNameOf(@Nullable Component name) {
		if (name == null) return null;
		String plain = ChatFormatting.stripFormatting(name.getString());
		if (plain == null) return null;
		Matcher m = NAMETAG.matcher(plain.strip());
		return m.matches() ? m.group(2).strip() : null;
	}

	static @Nullable String activePetFromTab(Minecraft mc) {
		if (mc.getConnection() == null) return null;
		for (PlayerInfo info : mc.getConnection().getListedOnlinePlayers()) {
			Component c = info.getTabListDisplayName();
			if (c == null) continue;
			String s = ChatFormatting.stripFormatting(c.getString());
			if (s == null) continue;
			Matcher m = TAB_PET.matcher(s.strip());
			if (m.find()) return m.group(1).strip().toLowerCase(Locale.ROOT);
		}
		return null;
	}

	/** Test hook. */
	public static @Nullable String parseTag(String plain) {
		Matcher m = NAMETAG.matcher(plain);
		return m.matches() ? m.group(2).strip() : null;
	}
}
