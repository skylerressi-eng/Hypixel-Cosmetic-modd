package dev.cosmeticstudio.gui;

import dev.cosmeticstudio.CosmeticStudioClient;
import dev.cosmeticstudio.compat.Compat;
import dev.cosmeticstudio.model.Look;
import dev.cosmeticstudio.store.LocalStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Manage any number of presets: save the current outfit as a new preset, load one, overwrite one with the current
 * outfit, or delete one. Each preset stores all four armor pieces and the pet.
 */
public final class PresetScreen extends Screen {
	private final Screen parent;
	private final Look current;
	private final Consumer<Look> onLoad;
	private final LocalStore store = CosmeticStudioClient.store();
	private @Nullable String selected;
	private String newName = "";
	private @Nullable Component status;
	private int statusColor = 0xFFFFFFFF;
	private @Nullable OptionList<String> list;

	public PresetScreen(Screen parent, Look current, Consumer<Look> onLoad) {
		super(Component.translatable("cosmeticstudio.presets.title"));
		this.parent = parent;
		this.current = current;
		this.onLoad = onLoad;
	}

	@Override
	protected void init() {
		int w = Math.min(320, width - 20), x = (width - w) / 2;
		EditBox name = new EditBox(font, x, 24, w - 104, 20, Component.translatable("cosmeticstudio.gui.preset_name"));
		name.setHint(Component.translatable("cosmeticstudio.presets.name_hint"));
		name.setMaxLength(32);
		name.setValue(newName);
		name.setResponder(s -> newName = s);
		addRenderableWidget(name);
		addRenderableWidget(Button.builder(Component.translatable("cosmeticstudio.presets.save_new"), b -> saveNew()).bounds(x + w - 100, 24, 100, 20).build());

		list = new OptionList<>(x, 50, w, height - 50 - 60, OptionList.Mode.LIST, v -> {}, o -> {
			selected = o.value();
			if (list != null) list.setSelected(selected);
			rebuildWidgets();
		});
		addRenderableWidget(list);
		refresh();

		int by = height - 50, bw = (w - 8) / 3;
		Button load = Button.builder(Component.translatable("cosmeticstudio.presets.load"), b -> load()).bounds(x, by, bw, 20).build();
		Button overwrite = Button.builder(Component.translatable("cosmeticstudio.presets.overwrite"), b -> overwrite()).bounds(x + bw + 4, by, bw, 20).build();
		Button delete = Button.builder(Component.translatable("cosmeticstudio.presets.delete"), b -> delete()).bounds(x + 2 * (bw + 4), by, bw, 20).build();
		boolean has = selected != null && store.presets().containsKey(selected);
		load.active = overwrite.active = delete.active = has;
		addRenderableWidget(load);
		addRenderableWidget(overwrite);
		addRenderableWidget(delete);
		addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose()).bounds(x, height - 26, w, 20).build());
	}

	private void refresh() {
		if (list == null) return;
		List<Option<String>> opts = new ArrayList<>();
		for (Map.Entry<String, Look> e : store.presets().entrySet()) {
			Component label = Component.literal(e.getKey()).append(Component.literal("  " + summary(e.getValue())).withStyle(ChatFormatting.GRAY));
			opts.add(new Option<>(e.getKey(), label, null, null, List.of(Component.literal(e.getKey()), Component.literal(summary(e.getValue())).withStyle(ChatFormatting.GRAY)), true, null));
		}
		list.setOptions(opts);
		list.setSelected(selected);
	}

	static String summary(Look look) {
		List<String> parts = new ArrayList<>();
		long dyes = look.armor.values().stream().filter(c -> c != null && c.dye() != null).count();
		long runes = look.armor.values().stream().filter(c -> c != null && c.rune() != null).count();
		if (dyes > 0) parts.add(dyes + (dyes == 1 ? " dye" : " dyes"));
		if (runes > 0) parts.add(runes + (runes == 1 ? " rune" : " runes"));
		if (look.pet != null && look.pet.isActive()) {
			String id = look.pet.id == null ? "?" : look.pet.id.substring(look.pet.id.indexOf(':') + 1).replace('_', ' ');
			parts.add("pet: " + id);
		}
		return parts.isEmpty() ? "empty" : String.join(" · ", parts);
	}

	private void saveNew() {
		String n = newName.strip();
		if (n.isEmpty()) {
			int i = store.presets().size() + 1;
			while (store.presets().containsKey("Preset " + i)) i++;
			n = "Preset " + i;
		}
		boolean existed = store.presets().containsKey(n);
		store.savePreset(n, current);
		store.save();
		selected = n;
		newName = "";
		status = Component.translatable(existed ? "cosmeticstudio.presets.updated" : "cosmeticstudio.presets.saved", n);
		statusColor = 0xFF55FF55;
		rebuildWidgets();
	}

	private void load() {
		if (selected == null || !store.presets().containsKey(selected)) return;
		onLoad.accept(store.presets().get(selected).copy());
		Compat.setScreen(parent);
	}

	private void overwrite() {
		if (selected == null) return;
		store.savePreset(selected, current);
		store.save();
		status = Component.translatable("cosmeticstudio.presets.updated", selected);
		statusColor = 0xFF55FF55;
		rebuildWidgets();
	}

	private void delete() {
		if (selected == null) return;
		store.deletePreset(selected);
		store.save();
		status = Component.translatable("cosmeticstudio.gui.preset_deleted", selected);
		statusColor = 0xFFAAAAAA;
		selected = null;
		rebuildWidgets();
	}

	@Override
	public void onClose() {
		Compat.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		g.centeredText(font, title, width / 2, 8, 0xFFFFFFFF);
		if (store.presets().isEmpty()) {
			g.centeredText(font, Component.translatable("cosmeticstudio.presets.empty"), width / 2, height / 2 - 10, 0xFFAAAAAA);
		}
		if (status != null) g.centeredText(font, status, width / 2, height - 62 + 2, statusColor);
		g.text(font, Component.translatable("cosmeticstudio.presets.current", summary(current)), 10, height - 12, 0xFF888888);
	}
}
