package dev.cosmeticstudio.gui;

import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

/** Slider over [min, max] that reports the float value. */
public final class FloatSlider extends AbstractSliderButton {
	private final float min, max;
	private final Function<Float, Component> label;
	private final Consumer<Float> onChange;

	public FloatSlider(int x, int y, int w, int h, float min, float max, float value, Function<Float, Component> label, Consumer<Float> onChange) {
		super(x, y, w, h, Component.empty(), (value - min) / (max - min));
		this.min = min;
		this.max = max;
		this.label = label;
		this.onChange = onChange;
		updateMessage();
	}

	public float floatValue() {
		return (float) (min + (max - min) * value);
	}

	public void setFloat(float v) {
		value = Math.max(0, Math.min(1, (v - min) / (max - min)));
		updateMessage();
	}

	@Override
	protected void updateMessage() {
		setMessage(label.apply(floatValue()));
	}

	@Override
	protected void applyValue() {
		onChange.accept(floatValue());
	}
}
