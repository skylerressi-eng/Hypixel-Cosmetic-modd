package dev.cosmeticstudio.gui;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Scrollable grid (colour swatches) or list (rows with icon + name) of {@link Option}s.
 * Reports hover changes (for live preview) and clicks (to apply).
 */
public final class OptionList<T> extends AbstractWidget {
	public enum Mode { GRID, LIST }

	private static final int CELL = 18, ROW = 18, SCROLLBAR = 4;

	private final Mode mode;
	private final Consumer<@Nullable T> onHover;
	private final Consumer<Option<T>> onClick;
	private List<Option<T>> options = List.of();
	private @Nullable T selected;
	private @Nullable T hovered;
	private double scroll;

	public OptionList(int x, int y, int w, int h, Mode mode, Consumer<@Nullable T> onHover, Consumer<Option<T>> onClick) {
		super(x, y, w, h, Component.empty());
		this.mode = mode;
		this.onHover = onHover;
		this.onClick = onClick;
	}

	public void setOptions(List<Option<T>> options) {
		this.options = options;
		scroll = Math.min(scroll, maxScroll());
	}

	public void setSelected(@Nullable T value) {
		this.selected = value;
	}

	public int size() {
		return options.size();
	}

	private int columns() {
		return mode == Mode.GRID ? Math.max(1, (width - SCROLLBAR - 2) / CELL) : 1;
	}

	private int rowHeight() {
		return mode == Mode.GRID ? CELL : ROW;
	}

	private int contentHeight() {
		int rows = (options.size() + columns() - 1) / columns();
		return rows * rowHeight();
	}

	private double maxScroll() {
		return Math.max(0, contentHeight() - height);
	}

	private int indexAt(double mx, double my) {
		if (mx < getX() || mx >= getX() + width - SCROLLBAR || my < getY() || my >= getY() + height) return -1;
		int row = (int) ((my - getY() + scroll) / rowHeight());
		int col = mode == Mode.GRID ? (int) ((mx - getX() - 1) / CELL) : 0;
		if (col >= columns()) return -1;
		int i = row * columns() + col;
		return i >= 0 && i < options.size() ? i : -1;
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		Font font = Minecraft.getInstance().font;
		g.fill(getX(), getY(), getX() + width, getY() + height, 0xA0000000);
		g.outline(getX(), getY(), width, height, 0xFF555555);

		int hoverIndex = indexAt(mouseX, mouseY);
		T nowHovered = hoverIndex >= 0 ? options.get(hoverIndex).value() : null;
		if (!Objects.equals(nowHovered, hovered)) {
			hovered = nowHovered;
			onHover.accept(nowHovered);
		}

		g.enableScissor(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1);
		int cols = columns(), rh = rowHeight();
		int first = (int) (scroll / rh) * cols;
		for (int i = Math.max(0, first); i < options.size(); i++) {
			int row = i / cols, col = i % cols;
			int y = getY() + row * rh - (int) scroll;
			if (y > getY() + height) break;
			Option<T> o = options.get(i);
			boolean sel = Objects.equals(o.value(), selected);
			if (mode == Mode.GRID) {
				int x = getX() + 1 + col * CELL;
				int c = o.swatch() != null ? o.swatch().getAsInt() : 0x808080;
				g.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, sel ? 0xFFFFFFFF : 0xFF222222);
				g.fill(x + 2, y + 2, x + CELL - 2, y + CELL - 2, 0xFF000000 | c);
				if (!o.enabled()) g.fill(x + 2, y + 2, x + CELL - 2, y + CELL - 2, 0xA0000000);
				if (i == hoverIndex) g.outline(x, y, CELL, CELL, 0xFFFFFF55);
			} else {
				int x = getX() + 1;
				if (sel) g.fill(x, y, getX() + width - SCROLLBAR - 1, y + rh, 0x6060A0FF);
				else if (i == hoverIndex) g.fill(x, y, getX() + width - SCROLLBAR - 1, y + rh, 0x40FFFFFF);
				int tx = x + 3;
				if (o.icon() != null && !o.icon().isEmpty()) {
					g.fakeItem(o.icon(), tx, y + 1);
					tx += 18;
				} else if (o.swatch() != null) {
					g.fill(tx + 2, y + 3, tx + 14, y + 15, 0xFF000000 | o.swatch().getAsInt());
					tx += 18;
				}
				Component label = o.label();
				int maxW = getX() + width - SCROLLBAR - 4 - tx;
				String s = font.plainSubstrByWidth(label.getString(), maxW);
				g.text(font, s.length() < label.getString().length() ? Component.literal(s) : label, tx, y + 5, o.enabled() ? 0xFFFFFFFF : 0xFF777777);
			}
		}
		g.disableScissor();

		if (options.isEmpty()) {
			g.centeredText(font, Component.translatable("cosmeticstudio.gui.no_results"), getX() + width / 2, getY() + height / 2 - 4, 0xFFAAAAAA);
		}
		// Scrollbar
		if (maxScroll() > 0) {
			int barH = Math.max(10, (int) (height * (height / (double) contentHeight())));
			int barY = getY() + (int) ((height - barH) * (scroll / maxScroll()));
			g.fill(getX() + width - SCROLLBAR, getY(), getX() + width, getY() + height, 0xFF111111);
			g.fill(getX() + width - SCROLLBAR, barY, getX() + width, barY + barH, 0xFF888888);
		}
		if (hoverIndex >= 0) {
			Option<T> o = options.get(hoverIndex);
			java.util.List<Component> tip = new java.util.ArrayList<>(o.tooltip());
			if (!o.enabled() && o.disabledReason() != null) tip.add(o.disabledReason().copy().withStyle(net.minecraft.ChatFormatting.RED));
			if (!tip.isEmpty()) g.setComponentTooltipForNextFrame(font, tip, mouseX, mouseY);
		}
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		int i = indexAt(event.x(), event.y());
		if (i >= 0) onClick.accept(options.get(i));
	}

	@Override
	public boolean mouseScrolled(double x, double y, double sx, double sy) {
		if (!isMouseOver(x, y)) return false;
		scroll = Math.max(0, Math.min(maxScroll(), scroll - sy * rowHeight() * 2));
		return true;
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		output.add(NarratedElementType.TITLE, Component.translatable("cosmeticstudio.gui.options", options.size()));
	}
}
