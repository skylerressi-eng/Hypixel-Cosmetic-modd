package dev.cosmeticstudio.gui;

import dev.cosmeticstudio.CosmeticStudioClient;
import dev.cosmeticstudio.catalog.Catalog;
import dev.cosmeticstudio.catalog.CatalogManager;
import dev.cosmeticstudio.catalog.DyeDef;
import dev.cosmeticstudio.catalog.Rarity;
import dev.cosmeticstudio.catalog.RuneDef;
import dev.cosmeticstudio.catalog.RuneEffect;
import dev.cosmeticstudio.compat.Compat;
import dev.cosmeticstudio.config.ModConfig;
import dev.cosmeticstudio.model.ArmorCosmetic;
import dev.cosmeticstudio.model.ArmorSlot;
import dev.cosmeticstudio.model.Look;
import dev.cosmeticstudio.model.PetCosmetic;
import dev.cosmeticstudio.model.PetPosition;
import dev.cosmeticstudio.resolve.CosmeticResolver;
import dev.cosmeticstudio.skyblock.SkyBlockItems;
import dev.cosmeticstudio.store.LocalStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The /cosmetic screen. Left: slot buttons + live preview. Right: option browser for the selected slot.
 * Edits go into a working {@link Look}; hovering previews on top of it; Apply/Done saves to {@link LocalStore}.
 */
public final class CosmeticScreen extends Screen {
	/** Which slot is being edited: one of the armor slots or the pet. */
	private enum Target { HELMET, CHESTPLATE, LEGGINGS, BOOTS, PET;
		@Nullable ArmorSlot armor() { return this == PET ? null : ArmorSlot.values()[ordinal()]; }
	}

	private enum Tab { DYES, RUNES, LOOKS, BLOCKS, ANIMALS, PET_SETTINGS }

	enum DyeSort { NAME("A–Z"), HUE("Colour"), RARITY("Rarity");
		final String label; DyeSort(String l) { label = l; } }

	enum RuneSort { NAME("A–Z"), RARITY("Rarity"), SLOT("Slot");
		final String label; RuneSort(String l) { label = l; } }

	private static Target lastTarget = Target.CHESTPLATE;
	private static Tab lastArmorTab = Tab.DYES;
	private static Tab lastPetTab = Tab.BLOCKS;

	private final ModConfig config = CosmeticStudioClient.config();
	private final LocalStore store = CosmeticStudioClient.store();
	private Look working;
	private boolean dirty;
	private Target target = lastTarget;
	private Tab tab;
	private String search = "";
	private DyeSort dyeSort = DyeSort.NAME;
	private DyeSort lookSort = DyeSort.NAME;
	private RuneSort runeSort = RuneSort.NAME;
	private String filter = "All";
	private int runeLevel = 3;
	private @Nullable Component status;
	private int statusColor = 0xFFFFFFFF;
	private String presetName = "";

	private @Nullable OptionList<String> list;
	private @Nullable EditBox searchBox;

	public CosmeticScreen() {
		super(Component.translatable("cosmeticstudio.gui.title"));
		working = currentLook();
		tab = target == Target.PET ? lastPetTab : lastArmorTab;
	}

	/** Builds a Look from what is saved for the armor currently worn (per item UUID, else per slot). */
	static Look currentLook() {
		Look look = new Look();
		Player p = net.minecraft.client.Minecraft.getInstance().player;
		LocalStore store = CosmeticStudioClient.store();
		for (ArmorSlot s : ArmorSlot.values()) {
			ItemStack worn = p == null ? ItemStack.EMPTY : p.getItemBySlot(s.vanilla);
			ArmorCosmetic c = store.get(SkyBlockItems.uuid(worn), s);
			if (!c.isEmpty()) look.armor.put(s, c);
		}
		look.pet = store.pet();
		return look;
	}

	// ---------------------------------------------------------------- layout

	@Override
	protected void init() {
		CosmeticResolver.setPreview(working);
		int left = 8, top = 22;
		int previewW = Math.max(90, Math.min(140, width / 4));
		int bottom = height - 54;

		// Slot buttons
		for (Target t : Target.values()) {
			int y = top + t.ordinal() * 24;
			Button b = Button.builder(Component.literal(""), btn -> selectTarget(t)).bounds(left, y, 22, 22)
					.tooltip(Tooltip.create(slotLabel(t))).build();
			addRenderableWidget(b);
		}
		addRenderableWidget(new PlayerPreview(left + 26, top, previewW, bottom - top));

		int px = left + 26 + previewW + 8, pw = width - px - 8;

		// Tabs
		List<Tab> tabs = target == Target.PET ? List.of(Tab.BLOCKS, Tab.ANIMALS, Tab.PET_SETTINGS) : List.of(Tab.DYES, Tab.RUNES, Tab.LOOKS);
		int tabW = Math.min(80, pw / tabs.size());
		for (int i = 0; i < tabs.size(); i++) {
			Tab t = tabs.get(i);
			Button b = Button.builder(tabLabel(t), btn -> selectTab(t)).bounds(px + i * tabW, top, tabW - 2, 20).build();
			b.active = t != tab;
			addRenderableWidget(b);
		}

		int contentTop = top + 24;
		if (tab == Tab.PET_SETTINGS) {
			initPetSettings(px, contentTop, pw, bottom);
		} else {
			initBrowser(px, contentTop, pw, bottom);
		}

		// Bottom row 1: actions
		int by = height - 48, bw = Math.min(90, (width - 16 - 3 * 4) / 4);
		addRenderableWidget(Button.builder(Component.translatable("cosmeticstudio.gui.apply"), b -> apply()).bounds(left, by, bw, 20).build());
		addRenderableWidget(Button.builder(Component.translatable(target == Target.PET ? "cosmeticstudio.gui.clear_pet" : "cosmeticstudio.gui.clear_piece"),
				b -> clearPiece()).bounds(left + (bw + 4), by, bw, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("cosmeticstudio.gui.reset_all"), b -> resetAll()).bounds(left + 2 * (bw + 4), by, bw, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("cosmeticstudio.gui.done"), b -> onClose())
				.bounds(width - 8 - bw, by, bw, 20).build());

		// Bottom row 2: presets (any number; managed in PresetScreen)
		int ry = height - 24;
		EditBox name = new EditBox(font, left, ry, 130, 20, Component.translatable("cosmeticstudio.gui.preset_name"));
		name.setHint(Component.translatable("cosmeticstudio.gui.preset_hint"));
		name.setMaxLength(32);
		name.setValue(presetName);
		name.setResponder(s -> presetName = s);
		addRenderableWidget(name);
		addRenderableWidget(Button.builder(Component.translatable("cosmeticstudio.gui.save_preset"), b -> savePreset()).bounds(left + 134, ry, 90, 20)
				.tooltip(Tooltip.create(Component.translatable("cosmeticstudio.gui.save_preset_tip"))).build());
		int count = store.presets().size();
		addRenderableWidget(Button.builder(Component.translatable("cosmeticstudio.gui.presets", count), b -> Compat.setScreen(new PresetScreen(this, working.copy(), look -> {
			working = look;
			markDirty();
			CosmeticResolver.setPreview(working);
			setStatus(Component.translatable("cosmeticstudio.gui.preset_loaded_any"), 0xFF55FFFF);
		}))).bounds(left + 228, ry, 110, 20).build());
	}

	private void initBrowser(int px, int y, int pw, int bottom) {
		int controls = tab == Tab.DYES || tab == Tab.RUNES ? 3 : tab == Tab.LOOKS ? 2 : 0;
		int searchW = pw - controls * 72;
		searchBox = new EditBox(font, px, y, Math.max(60, searchW - 4), 18, Component.translatable("cosmeticstudio.gui.search"));
		searchBox.setHint(Component.translatable("cosmeticstudio.gui.search_hint"));
		searchBox.setValue(search);
		searchBox.setResponder(s -> { search = s; refreshList(); });
		addRenderableWidget(searchBox);
		int cx = px + searchW;
		if (tab == Tab.DYES) {
			addRenderableWidget(CycleButton.<DyeSort>builder(s -> Component.literal(s.label), dyeSort).withValues(DyeSort.values())
					.create(cx, y - 1, 70, 20, Component.translatable("cosmeticstudio.gui.sort"), (b, v) -> { dyeSort = v; refreshList(); }));
			addRenderableWidget(CycleButton.<String>builder(Component::literal, dyeFilters().contains(filter) ? filter : "All").withValues(dyeFilters())
					.create(cx + 72, y - 1, 70, 20, Component.translatable("cosmeticstudio.gui.filter"), (b, v) -> { filter = v; refreshList(); }));
		} else if (tab == Tab.RUNES) {
			addRenderableWidget(CycleButton.<RuneSort>builder(s -> Component.literal(s.label), runeSort).withValues(RuneSort.values())
					.create(cx, y - 1, 70, 20, Component.translatable("cosmeticstudio.gui.sort"), (b, v) -> { runeSort = v; refreshList(); }));
			addRenderableWidget(CycleButton.<String>builder(Component::literal, runeFilters().contains(filter) ? filter : "Fits piece").withValues(runeFilters())
					.create(cx + 72, y - 1, 70, 20, Component.translatable("cosmeticstudio.gui.filter"), (b, v) -> { filter = v; refreshList(); }));
			addRenderableWidget(CycleButton.<Integer>builder(l -> Component.literal(roman(l)), runeLevel).withValues(1, 2, 3)
					.create(cx + 144, y - 1, 70, 20, Component.translatable("cosmeticstudio.gui.level"), (b, v) -> { runeLevel = v; refreshList(); }));
		}
		if (tab == Tab.LOOKS) {
			addRenderableWidget(CycleButton.<DyeSort>builder(s -> Component.literal(s.label), lookSort).withValues(DyeSort.NAME, DyeSort.RARITY)
					.create(cx, y - 1, 70, 20, Component.translatable("cosmeticstudio.gui.sort"), (b, v) -> { lookSort = v; refreshList(); }));
			addRenderableWidget(CycleButton.<String>builder(Component::literal, List.of("All", "SkyBlock", "Vanilla").contains(filter) ? filter : "All")
					.withValues("All", "SkyBlock", "Vanilla")
					.create(cx + 72, y - 1, 70, 20, Component.translatable("cosmeticstudio.gui.filter"), (b, v) -> { filter = v; refreshList(); }));
		}
		OptionList.Mode mode = tab == Tab.DYES ? OptionList.Mode.GRID : OptionList.Mode.LIST;
		int extra = tab == Tab.RUNES ? 34 : 0;
		list = new OptionList<>(px, y + 22, pw, bottom - (y + 22) - 12 - extra, mode, this::onHover, this::onPick);
		addRenderableWidget(list);
		refreshList();
		if (tab == Tab.RUNES) {
			int ry = bottom - 32, half = (pw - 4) / 2;
			CycleButton<Boolean> hideReal = CycleButton.onOffBuilder(config.hideRealRunes).create(px, ry, half, 20,
					Component.translatable("cosmeticstudio.gui.hide_real_runes"), (b, v) -> {
						config.hideRealRunes = v;
						config.save();
					});
			hideReal.setTooltip(Tooltip.create(Component.translatable("cosmeticstudio.gui.hide_real_runes_tip")));
			addRenderableWidget(hideReal);
			addRenderableWidget(Button.builder(Component.translatable("cosmeticstudio.gui.remove_all_runes"), b -> removeAllRunes())
					.bounds(px + half + 4, ry, half, 20).tooltip(Tooltip.create(Component.translatable("cosmeticstudio.gui.remove_all_runes_tip"))).build());
			ArmorSlot slot = target.armor();
			String real = slot == null ? null : SkyBlockItems.realRunes(wornStack(slot));
			if (real != null && status == null) {
				setStatus(Component.translatable("cosmeticstudio.gui.real_rune", real), 0xFFFFAA00);
			}
		}
	}

	/** Removes every cosmetic rune: all saved items and slots, plus the outfit being edited. Dyes and pet stay. */
	private void removeAllRunes() {
		int removed = store.clearAllRunes();
		for (ArmorSlot s : ArmorSlot.values()) {
			ArmorCosmetic c = working.get(s);
			if (c.rune() != null) removed++;
			ArmorCosmetic stripped = c.withRune(null, 0);
			if (stripped.isEmpty()) working.armor.remove(s); else working.armor.put(s, stripped);
		}
		store.save();
		CosmeticResolver.setPreview(working);
		if (list != null) list.setSelected(selectedValue());
		setStatus(Component.translatable("cosmeticstudio.gui.runes_removed"), 0xFF55FF55);
	}

	private void initPetSettings(int px, int y, int pw, int bottom) {
		PetCosmetic pet = working.pet;
		int w = Math.min(pw, 230), half = (w - 4) / 2;
		EditBox name = new EditBox(font, px, y, w, 18, Component.translatable("cosmeticstudio.gui.pet_name"));
		name.setHint(Component.translatable("cosmeticstudio.gui.pet_name_hint"));
		name.setMaxLength(PetCosmetic.MAX_NAME);
		name.setValue(pet.name == null ? "" : pet.name);
		name.setResponder(s -> edit(p -> p.name = s));
		addRenderableWidget(name);
		y += 22;
		addRenderableWidget(CycleButton.onOffBuilder(pet.showName).create(px, y, half, 20, Component.translatable("cosmeticstudio.gui.show_name"),
				(b, v) -> edit(p -> p.showName = v)));
		addRenderableWidget(CycleButton.<PetPosition>builder(p -> Component.literal(p.label), pet.position).withValues(PetPosition.values())
				.create(px + half + 4, y, half, 20, Component.translatable("cosmeticstudio.gui.position"), (b, v) -> edit(p -> p.position = v)));
		y += 24;
		addRenderableWidget(new FloatSlider(px, y, w, 20, 0.25f, 2.5f, pet.scale,
				v -> Component.translatable("cosmeticstudio.gui.scale", String.format(Locale.ROOT, "%.2f", v)), v -> edit(p -> p.scale = v)));
		y += 24;
		int third = (w - 8) / 3;
		addRenderableWidget(new FloatSlider(px, y, third, 20, -1f, 1f, pet.offsetX, v -> Component.literal(String.format(Locale.ROOT, "X %+.2f", v)), v -> edit(p -> p.offsetX = v)));
		addRenderableWidget(new FloatSlider(px + third + 4, y, third, 20, -1f, 1f, pet.offsetY, v -> Component.literal(String.format(Locale.ROOT, "Y %+.2f", v)), v -> edit(p -> p.offsetY = v)));
		addRenderableWidget(new FloatSlider(px + 2 * (third + 4), y, third, 20, -1f, 1f, pet.offsetZ, v -> Component.literal(String.format(Locale.ROOT, "Z %+.2f", v)), v -> edit(p -> p.offsetZ = v)));
		y += 24;
		addRenderableWidget(CycleButton.onOffBuilder(pet.spin).create(px, y, half, 20, Component.translatable("cosmeticstudio.gui.spin"), (b, v) -> edit(p -> p.spin = v)));
		addRenderableWidget(CycleButton.onOffBuilder(pet.bob).create(px + half + 4, y, half, 20, Component.translatable("cosmeticstudio.gui.bob"), (b, v) -> edit(p -> p.bob = v)));
		y += 24;
		CycleButton<Boolean> hide = CycleButton.onOffBuilder(pet.hideRealPet).create(px, y, w, 20, Component.translatable("cosmeticstudio.gui.hide_real_pet"),
				(b, v) -> edit(p -> p.hideRealPet = v));
		hide.setTooltip(Tooltip.create(Component.translatable("cosmeticstudio.gui.hide_real_pet_tip")));
		addRenderableWidget(hide);
	}

	private void edit(Consumer<PetCosmetic> change) {
		change.accept(working.pet);
		working.pet.sanitize();
		markDirty();
	}

	// ---------------------------------------------------------------- options

	private void refreshList() {
		if (list == null) return;
		String q = search.toLowerCase(Locale.ROOT).strip();
		List<Option<String>> opts = switch (tab) {
			case DYES -> dyeOptions(q);
			case RUNES -> runeOptions(q);
			case LOOKS -> lookOptions(q);
			case BLOCKS -> PetCatalog.blockOptions(q);
			case ANIMALS -> PetCatalog.entityOptions(q);
			default -> List.of();
		};
		list.setOptions(opts);
		list.setSelected(selectedValue());
		list.scrollToSelected();
	}

	private @Nullable String selectedValue() {
		ArmorSlot slot = target.armor();
		if (tab == Tab.DYES && slot != null) return working.get(slot).dye();
		if (tab == Tab.RUNES && slot != null) {
			ArmorCosmetic c = working.get(slot);
			if (c.rune() != null) return c.rune();
			// The rune really on this item shows as pre-selected until you deselect (hide) it.
			String real = SkyBlockItems.realRuneId(wornStack(slot));
			return real != null && !c.hideRealRune() ? real : null;
		}
		if (tab == Tab.LOOKS && slot != null) return working.get(slot).look();
		if ((tab == Tab.BLOCKS && working.pet.kind == PetCosmetic.Kind.BLOCK) || (tab == Tab.ANIMALS && working.pet.kind == PetCosmetic.Kind.ENTITY))
			return working.pet.id;
		return null;
	}

	private List<String> dyeFilters() {
		List<String> f = new ArrayList<>(List.of("All", "Static", "Animated"));
		for (String r : Rarity.all()) if (CatalogManager.get().dyes().values().stream().anyMatch(d -> r.equals(d.rarity()))) f.add(cap(r));
		return f;
	}

	private List<String> runeFilters() {
		List<String> f = new ArrayList<>(List.of("Fits piece", "All"));
		CatalogManager.get().runes().values().stream().map(RuneDef::slotLabel).distinct().sorted().forEach(f::add);
		return f;
	}

	private List<Option<String>> dyeOptions(String q) {
		Catalog cat = CatalogManager.get();
		ArmorSlot slot = target.armor();
		ItemStack worn = wornStack(slot);
		ItemStack lookStack = slot == null ? ItemStack.EMPTY : dev.cosmeticstudio.render.Appearances.stack(working.get(slot).look());
		boolean dyeable = lookStack.isEmpty() ? worn.isEmpty() || SkyBlockItems.isDyeable(worn) : SkyBlockItems.isDyeable(lookStack);
		Comparator<DyeDef> cmp = switch (dyeSort) {
			case NAME -> Comparator.comparing(DyeDef::name);
			case HUE -> Comparator.comparing(DyeDef::hue).thenComparing(DyeDef::name);
			case RARITY -> Comparator.comparing((DyeDef d) -> -Rarity.rank(d.rarity())).thenComparing(DyeDef::name);
		};
		List<Option<String>> out = new ArrayList<>();
		cat.dyes().values().stream().filter(d -> matches(q, d.name(), d.id())).filter(d -> switch (filter) {
			case "All" -> true;
			case "Static" -> !d.animated();
			case "Animated" -> d.animated();
			default -> filter.equalsIgnoreCase(d.rarity() == null ? "" : d.rarity());
		}).sorted(cmp).forEach(d -> {
			List<Component> tip = new ArrayList<>();
			tip.add(Component.literal(d.name()).withStyle(s -> s.withColor(TextColor.fromRgb(Rarity.color(d.rarity())))));
			tip.add(Component.literal(d.animated() ? "Animated · " + d.frames().size() + " frames" : String.format(Locale.ROOT, "#%06X", d.color()))
					.withStyle(ChatFormatting.GRAY));
			if (d.rarity() != null) tip.add(Component.literal(d.rarity() + " DYE").withStyle(s -> s.withColor(TextColor.fromRgb(Rarity.color(d.rarity()))).withBold(true)));
			if (d.animated() && !d.frameTicksVerified()) tip.add(Component.translatable("cosmeticstudio.gui.speed_unverified").withStyle(ChatFormatting.DARK_GRAY));
			out.add(new Option<>(d.id(), Component.literal(d.name()), () -> d.colorAt(CosmeticResolver.clock()), null, tip, dyeable,
					Component.translatable("cosmeticstudio.gui.not_dyeable", worn.getHoverName())));
		});
		return out;
	}

	private static final class MutableComponentHolder {
		Component c;

		MutableComponentHolder(Component c) {
			this.c = c;
		}
	}

	private List<Option<String>> runeOptions(String q) {
		ArmorSlot slot = target.armor();
		String realRune = slot == null ? null : SkyBlockItems.realRuneId(wornStack(slot));
		int realLevel = slot == null ? 0 : SkyBlockItems.realRuneLevel(wornStack(slot));
		Comparator<RuneDef> cmp = switch (runeSort) {
			case NAME -> Comparator.comparing(RuneDef::name);
			case RARITY -> Comparator.comparing((RuneDef r) -> -Rarity.rank(r.rarityAt(Math.min(runeLevel, r.maxLevel())))).thenComparing(RuneDef::name);
			case SLOT -> Comparator.comparing(RuneDef::slotLabel).thenComparing(RuneDef::name);
		};
		List<Option<String>> out = new ArrayList<>();
		CatalogManager.get().runes().values().stream().filter(r -> matches(q, r.name(), r.id())).filter(r -> switch (filter) {
			case "Fits piece" -> slot != null && (fits(r, slot) || r.id().equals(realRune));
			case "All" -> true;
			default -> filter.equals(r.slotLabel());
		}).sorted(cmp).forEach(r -> {
			int lvl = Math.min(runeLevel, r.maxLevel());
			String rarity = r.rarityAt(lvl);
			boolean allowed = slot != null && (config.allowAnyRuneOnAnyPiece || fits(r, slot));
			RuneEffect effect = CatalogManager.get().effect(r.id());
			List<Component> tip = new ArrayList<>();
			tip.add(Component.literal("◆ " + r.name() + " " + roman(lvl)).withStyle(nameStyle(r)));
			tip.add(Component.literal(r.description()).withStyle(ChatFormatting.GRAY));
			tip.add(Component.translatable("cosmeticstudio.gui.rune_slot", r.slotLabel()).withStyle(ChatFormatting.DARK_GRAY));
			if (rarity != null) tip.add(Component.literal(rarity + " COSMETIC").withStyle(s -> s.withColor(TextColor.fromRgb(Rarity.color(rarity))).withBold(true)));
			if (effect == null) tip.add(Component.translatable("cosmeticstudio.gui.effect_missing").withStyle(ChatFormatting.GOLD));
			else if (!effect.verified()) tip.add(Component.translatable(config.showUnverifiedRuneEffects ? "cosmeticstudio.gui.effect_approx"
					: "cosmeticstudio.gui.effect_approx_off").withStyle(ChatFormatting.GOLD));
			boolean isReal = r.id().equals(realRune);
			MutableComponentHolder label = new MutableComponentHolder(Component.literal(r.name() + " " + roman(isReal ? realLevel : lvl)).withStyle(nameStyle(r)));
			if (isReal) {
				label.c = label.c.copy().append(Component.translatable("cosmeticstudio.gui.on_your_item").withStyle(ChatFormatting.GOLD));
				tip.add(Component.translatable("cosmeticstudio.gui.real_rune_tip").withStyle(ChatFormatting.GOLD));
			}
			out.add(new Option<>(r.id(), label.c, null, null, tip, allowed || isReal,
					Component.translatable("cosmeticstudio.gui.rune_not_allowed", r.slotLabel())));
		});
		return out;
	}

	private List<Option<String>> lookOptions(String q) {
		ArmorSlot slot = target.armor();
		if (slot == null) return List.of();
		List<Option<String>> out = new ArrayList<>();
		if (!filter.equals("Vanilla")) {
			CatalogManager.get().appearances().values().stream().filter(a -> a.slot().equals(slot.runeSlot)).filter(a -> matches(q, a.name(), a.id()))
					.sorted(lookSort == DyeSort.RARITY ? Comparator.comparing((dev.cosmeticstudio.catalog.AppearanceDef a) -> -Rarity.rank(a.rarity()))
							.thenComparing(dev.cosmeticstudio.catalog.AppearanceDef::name) : Comparator.comparing(dev.cosmeticstudio.catalog.AppearanceDef::name))
					.forEach(a -> {
						String id = dev.cosmeticstudio.catalog.AppearanceDef.PREFIX + a.id();
						int col = Rarity.color(a.rarity());
						List<Component> tip = new ArrayList<>(List.of(Component.literal(a.name()).withStyle(st -> st.withColor(TextColor.fromRgb(col))),
								Component.translatable("cosmeticstudio.gui.look_sb").withStyle(ChatFormatting.GRAY)));
						if (a.rarity() != null) tip.add(Component.literal(a.rarity()).withStyle(st -> st.withColor(TextColor.fromRgb(col)).withBold(true)));
						out.add(new Option<>(id, Component.literal(a.name()).withStyle(st -> st.withColor(TextColor.fromRgb(col))), null,
								dev.cosmeticstudio.render.Appearances.stack(id), tip, true, null));
					});
		}
		if (!filter.equals("SkyBlock")) {
			for (var item : dev.cosmeticstudio.render.Appearances.vanillaFor(slot)) {
				String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString();
				ItemStack st = new ItemStack(item);
				String name = st.getHoverName().getString();
				if (!matches(q, name, id)) continue;
				out.add(new Option<>(id, Component.literal(name), null, st,
						List.of(Component.literal(name), Component.translatable("cosmeticstudio.gui.look_vanilla").withStyle(ChatFormatting.GRAY)), true, null));
			}
		}
		return out;
	}

	private static boolean fits(RuneDef r, ArmorSlot slot) {
		return slot.runeSlot.equals(r.slot());
	}

	private static net.minecraft.network.chat.Style nameStyle(RuneDef r) {
		ChatFormatting f = r.colorCode() == null ? null : ChatFormatting.getByCode(r.colorCode().charAt(0));
		return net.minecraft.network.chat.Style.EMPTY.withColor(f == null ? ChatFormatting.WHITE : f);
	}

	static boolean matches(String q, String name, String id) {
		return q.isEmpty() || name.toLowerCase(Locale.ROOT).contains(q) || id.toLowerCase(Locale.ROOT).contains(q);
	}

	// ---------------------------------------------------------------- interaction

	private void onHover(@Nullable String value) {
		if (value == null) {
			CosmeticResolver.setPreview(working);
			return;
		}
		Look preview = working.copy();
		applyTo(preview, value);
		CosmeticResolver.setPreview(preview);
	}

	private void onPick(Option<String> option) {
		if (!option.enabled()) {
			setStatus(option.disabledReason(), 0xFFFF5555);
			return;
		}
		String current = selectedValue();
		ArmorSlot pickSlot = target.armor();
		String realRune = pickSlot == null ? null : SkyBlockItems.realRuneId(wornStack(pickSlot));
		if (tab == Tab.RUNES && pickSlot != null && option.value().equals(realRune) && working.get(pickSlot).rune() == null) {
			// Toggle the rune that's really on the item: deselect = hide it, select again = show it.
			ArmorCosmetic c = working.get(pickSlot);
			boolean hide = !c.hideRealRune();
			working.armor.put(pickSlot, c.withHideRealRune(hide));
			setStatus(Component.translatable(hide ? "cosmeticstudio.gui.real_rune_hidden" : "cosmeticstudio.gui.real_rune_shown"), 0xFFFFAA00);
		} else if (option.value().equals(current)) {
			// Clicking the selected entry again removes it.
			clearValue();
			setStatus(Component.translatable("cosmeticstudio.gui.removed", option.label()), 0xFFAAAAAA);
		} else {
			applyTo(working, option.value());
			setStatus(Component.translatable("cosmeticstudio.gui.selected", option.label()), 0xFF55FF55);
		}
		markDirty();
		CosmeticResolver.setPreview(working);
		if (list != null) list.setSelected(selectedValue());
	}

	private void applyTo(Look look, String value) {
		ArmorSlot slot = target.armor();
		switch (tab) {
			case DYES -> { if (slot != null) look.armor.put(slot, look.get(slot).withDye(value)); }
			case RUNES -> {
				RuneDef r = CatalogManager.get().rune(value);
				if (slot != null && r != null) {
					// A new cosmetic rune replaces the real one on this piece, so they don't mix.
					boolean hasReal = SkyBlockItems.realRuneId(wornStack(slot)) != null;
					look.armor.put(slot, look.get(slot).withRune(value, Math.min(runeLevel, r.maxLevel())).withHideRealRune(hasReal || look.get(slot).hideRealRune()));
				}
			}
			case LOOKS -> { if (slot != null) look.armor.put(slot, look.get(slot).withLook(value)); }
			case BLOCKS -> { look.pet.kind = PetCosmetic.Kind.BLOCK; look.pet.id = value; }
			case ANIMALS -> { look.pet.kind = PetCosmetic.Kind.ENTITY; look.pet.id = value; }
			default -> {}
		}
	}

	private void clearValue() {
		ArmorSlot slot = target.armor();
		switch (tab) {
			case DYES -> { if (slot != null) working.armor.put(slot, working.get(slot).withDye(null)); }
			case RUNES -> { if (slot != null) working.armor.put(slot, working.get(slot).withRune(null, 0).withHideRealRune(false)); }
			case LOOKS -> { if (slot != null) working.armor.put(slot, working.get(slot).withLook(null)); }
			case BLOCKS, ANIMALS -> { working.pet.kind = PetCosmetic.Kind.NONE; working.pet.id = null; }
			default -> {}
		}
	}

	private void selectTarget(Target t) {
		if (t == target) return;
		boolean petSwitch = (t == Target.PET) != (target == Target.PET);
		target = lastTarget = t;
		if (petSwitch) tab = t == Target.PET ? lastPetTab : lastArmorTab;
		filter = tab == Tab.RUNES ? "Fits piece" : "All";
		status = null;
		ArmorSlot slot = t.armor();
		if (slot != null && tab == Tab.DYES) {
			ItemStack worn = wornStack(slot);
			if (!worn.isEmpty() && !SkyBlockItems.isDyeable(worn)) setStatus(Component.translatable("cosmeticstudio.gui.not_dyeable", worn.getHoverName()), 0xFFFFAA00);
			else if (worn.isEmpty()) setStatus(Component.translatable("cosmeticstudio.gui.empty_slot"), 0xFFAAAAAA);
		}
		rebuildWidgets();
	}

	/** The entry currently shown as selected in the list (tests/debug). */
	public @Nullable String selectedForTests() {
		return selectedValue();
	}

	/** Selects a tab by name (DYES, RUNES, LOOKS, …); used by tests. */
	public void selectTabForTests(String name) {
		selectTab(Tab.valueOf(name.toUpperCase(Locale.ROOT)));
	}

	/** Selects a slot by name (HELMET, CHESTPLATE, LEGGINGS, BOOTS, PET); used by keyboard shortcuts and tests. */
	public void selectSlot(String name) {
		selectTarget(Target.valueOf(name.toUpperCase(Locale.ROOT)));
	}

	private void selectTab(Tab t) {
		tab = t;
		if (target == Target.PET) lastPetTab = t; else lastArmorTab = t;
		filter = t == Tab.RUNES ? "Fits piece" : "All";
		search = "";
		status = null;
		rebuildWidgets();
	}

	private void apply() {
		Player p = minecraft.player;
		for (ArmorSlot s : ArmorSlot.values()) {
			ItemStack worn = p == null ? ItemStack.EMPTY : p.getItemBySlot(s.vanilla);
			store.set(SkyBlockItems.uuid(worn), s, working.get(s));
		}
		store.setPet(working.pet);
		store.save();
		dirty = false;
		setStatus(Component.translatable("cosmeticstudio.gui.saved"), 0xFF55FF55);
	}

	private void clearPiece() {
		ArmorSlot slot = target.armor();
		if (slot != null) working.armor.remove(slot);
		else working.pet = new PetCosmetic();
		markDirty();
		CosmeticResolver.setPreview(working);
		rebuildWidgets();
		setStatus(Component.translatable("cosmeticstudio.gui.cleared", slotLabel(target)), 0xFFAAAAAA);
	}

	private void resetAll() {
		working = new Look();
		store.clearAllArmor();
		store.setPet(working.pet);
		store.save();
		dirty = false;
		CosmeticResolver.setPreview(working);
		rebuildWidgets();
		setStatus(Component.translatable("cosmeticstudio.gui.reset_done"), 0xFFAAAAAA);
	}

	private void savePreset() {
		String n = presetName.strip();
		if (n.isEmpty()) {
			int i = store.presets().size() + 1;
			while (store.presets().containsKey("Preset " + i)) i++;
			n = "Preset " + i;
		}
		boolean existed = store.presets().containsKey(n);
		store.savePreset(n, working);
		store.save();
		presetName = "";
		rebuildWidgets();
		setStatus(Component.translatable(existed ? "cosmeticstudio.presets.updated" : "cosmeticstudio.gui.preset_saved", n), 0xFF55FF55);
	}

	private void markDirty() {
		dirty = true;
	}

	private void setStatus(@Nullable Component c, int color) {
		status = c;
		statusColor = color;
	}

	@Override
	public void onClose() {
		if (dirty) apply(); // clicking applies; closing (also with Esc) keeps what you picked
		CosmeticResolver.setPreview(null);
		Compat.setScreen(null);
	}

	@Override
	public void removed() {
		CosmeticResolver.setPreview(null);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ---------------------------------------------------------------- drawing

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		g.centeredText(font, title, width / 2, 7, 0xFFFFFFFF);
		// Slot icons drawn over the slot buttons.
		Player p = minecraft.player;
		for (Target t : Target.values()) {
			int x = 8 + 3, y = 22 + t.ordinal() * 24 + 3;
			ItemStack icon = t == Target.PET ? petIcon() : (p == null ? ItemStack.EMPTY : p.getItemBySlot(t.armor().vanilla));
			if (!icon.isEmpty()) g.item(icon, x, y);
			else g.centeredText(font, slotLetter(t), x + 8, y + 4, 0xFFAAAAAA);
			if (t == target) g.outline(8, 22 + t.ordinal() * 24, 22, 22, 0xFFFFFF55);
		}
		if (status != null) g.text(font, status, width - 8 - font.width(status) < 8 ? 8 : Math.max(8, width / 2 - font.width(status) / 2), height - 62, statusColor);
		if (dirty) g.text(font, Component.translatable("cosmeticstudio.gui.unsaved").withStyle(ChatFormatting.ITALIC), 8, 8, 0xFFFFAA00);
		if (list != null && tab != Tab.PET_SETTINGS) {
			Component count = Component.translatable("cosmeticstudio.gui.count", list.size());
			g.text(font, count, list.getX() + list.getWidth() - font.width(count), list.getY() + list.getHeight() + 2, 0xFF888888);
		}
	}

	private ItemStack petIcon() {
		return working.pet.isActive() ? PetCatalog.icon(working.pet) : new ItemStack(net.minecraft.world.item.Items.BONE);
	}

	private ItemStack wornStack(@Nullable ArmorSlot slot) {
		Player p = minecraft == null ? null : minecraft.player;
		return slot == null || p == null ? ItemStack.EMPTY : p.getItemBySlot(slot.vanilla);
	}

	private static Component slotLabel(Target t) {
		return Component.translatable("cosmeticstudio.slot." + t.name().toLowerCase(Locale.ROOT));
	}

	private static String slotLetter(Target t) {
		return switch (t) { case HELMET -> "H"; case CHESTPLATE -> "C"; case LEGGINGS -> "L"; case BOOTS -> "B"; case PET -> "P"; };
	}

	private static Component tabLabel(Tab t) {
		return Component.translatable("cosmeticstudio.tab." + t.name().toLowerCase(Locale.ROOT));
	}

	static String roman(int n) {
		return switch (n) { case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; default -> String.valueOf(n); };
	}

	private static String cap(String s) {
		return s.charAt(0) + s.substring(1).toLowerCase(Locale.ROOT);
	}
}
