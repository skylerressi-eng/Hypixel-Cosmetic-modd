package dev.cosmeticstudio.config;

import dev.cosmeticstudio.CosmeticStudioClient;
import dev.cosmeticstudio.compat.Compat;
import dev.cosmeticstudio.gui.FloatSlider;
import dev.cosmeticstudio.sync.SyncClient;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** Settings screen (opened from Mod Menu or with /cosmetic config). Changes save when the screen closes. */
public final class ConfigScreen extends Screen {
	private final @Nullable Screen parent;
	private final ModConfig cfg = CosmeticStudioClient.config();
	private final String originalUrl = cfg.syncServerUrl;
	private int row;
	private int colX;

	public ConfigScreen(@Nullable Screen parent) {
		super(Component.translatable("cosmeticstudio.config.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int w = Math.min(200, (width - 30) / 2);
		int left = width / 2 - w - 5, right = width / 2 + 5;

		colX = left;
		row = 0;
		header("visibility");
		toggle("show_mine", cfg.showMyCosmetics, v -> cfg.showMyCosmetics = v, w);
		toggle("show_others", cfg.showOthersCosmetics, v -> cfg.showOthersCosmetics = v, w);
		toggle("show_others_pets", cfg.showOthersPets, v -> cfg.showOthersPets = v, w);
		toggle("show_others_pet_names", cfg.showOthersPetNames, v -> cfg.showOthersPetNames = v, w);
		toggle("only_skyblock", cfg.onlyInSkyBlock, v -> cfg.onlyInSkyBlock = v, w);

		colX = right;
		row = 0;
		header("particles");
		toggle("particles", cfg.particles, v -> cfg.particles = v, w);
		addRenderableWidget(new FloatSlider(colX, y(), w, 20, 0f, 2f, cfg.particleDensity,
				v -> Component.translatable("cosmeticstudio.config.density", Math.round(v * 100) + "%"), v -> cfg.particleDensity = v));
		row++;
		toggle("no_particles_dungeons", cfg.disableParticlesInDungeons, v -> cfg.disableParticlesInDungeons = v, w);
		toggle("no_particles_kuudra", cfg.disableParticlesInKuudra, v -> cfg.disableParticlesInKuudra = v, w);
		toggle("any_rune", cfg.allowAnyRuneOnAnyPiece, v -> cfg.allowAnyRuneOnAnyPiece = v, w);
		toggle("unverified_runes", cfg.showUnverifiedRuneEffects, v -> cfg.showUnverifiedRuneEffects = v, w);

		// Sync section + buttons anchored to the bottom so nothing overlaps on small screens.
		int sy = height - 50;
		int full = w * 2 + 10;
		addRenderableWidget(CycleButton.onOffBuilder(cfg.syncEnabled).create(left, sy, 120, 20, Component.translatable("cosmeticstudio.config.sync"),
				(b, v) -> cfg.syncEnabled = v));
		EditBox url = new EditBox(font, left + 124, sy, full - 124, 20, Component.translatable("cosmeticstudio.config.sync_url"));
		url.setMaxLength(200);
		url.setHint(Component.translatable("cosmeticstudio.config.sync_url_hint"));
		url.setValue(cfg.syncServerUrl);
		url.setResponder(s -> cfg.syncServerUrl = s.strip());
		url.setTooltip(Tooltip.create(Component.translatable("cosmeticstudio.config.sync_url_tip")));
		addRenderableWidget(url);
		int third = (full - 8) / 3, by = height - 26;
		Button delete = Button.builder(Component.translatable("cosmeticstudio.config.delete_data"), b -> {
			SyncClient.deleteMyData();
			b.active = false;
		}).bounds(left, by, third, 20).tooltip(Tooltip.create(Component.translatable("cosmeticstudio.config.delete_data_tip"))).build();
		delete.active = !cfg.syncServerUrl.isBlank();
		addRenderableWidget(delete);
		addRenderableWidget(Button.builder(Component.translatable("cosmeticstudio.config.open_gui"), b -> {
			onClose();
			Compat.setScreen(new dev.cosmeticstudio.gui.CosmeticScreen());
		}).bounds(left + third + 4, by, third, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(left + 2 * (third + 4), by, third, 20).build());
	}

	private int y() {
		return 20 + row * 21;
	}

	private void header(String key) {
		row++; // the header text is drawn in extractRenderState
	}

	private void toggle(String key, boolean value, Consumer<Boolean> set, int w) {
		CycleButton<Boolean> b = CycleButton.onOffBuilder(value).create(colX, y(), w, 20, Component.translatable("cosmeticstudio.config." + key),
				(btn, v) -> set.accept(v));
		b.setTooltip(Tooltip.create(Component.translatable("cosmeticstudio.config." + key + ".tip")));
		addRenderableWidget(b);
		row++;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		int w = Math.min(200, (width - 30) / 2);
		g.centeredText(font, title, width / 2, 7, 0xFFFFFFFF);
		g.text(font, Component.translatable("cosmeticstudio.config.section.visibility"), width / 2 - w - 5, 27, 0xFFFFFF55);
		g.text(font, Component.translatable("cosmeticstudio.config.section.particles"), width / 2 + 5, 27, 0xFFFFFF55);
		g.text(font, Component.translatable("cosmeticstudio.config.sync_status", SyncClient.status()), width / 2 - w - 5, height - 62, 0xFFAAAAAA);
	}

	@Override
	public void onClose() {
		cfg.particleDensity = Math.max(0f, Math.min(2f, cfg.particleDensity));
		cfg.save();
		if (!cfg.syncServerUrl.equals(originalUrl)) SyncClient.reset();
		Compat.setScreen(parent);
	}

	static String pct(float v) {
		return String.format(Locale.ROOT, "%d%%", Math.round(v * 100));
	}
}
