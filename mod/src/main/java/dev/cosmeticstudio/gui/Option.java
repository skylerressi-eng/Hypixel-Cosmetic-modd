package dev.cosmeticstudio.gui;

import java.util.List;
import java.util.function.IntSupplier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * One selectable entry in an {@link OptionList}.
 * @param swatch colour supplier for a colour square (animated dyes return a changing colour), or null
 * @param icon item icon, or null
 * @param enabled false = drawn greyed out; clicking shows {@code disabledReason}
 */
public record Option<T>(T value, Component label, @Nullable IntSupplier swatch, @Nullable ItemStack icon, List<Component> tooltip, boolean enabled,
		@Nullable Component disabledReason) {}
