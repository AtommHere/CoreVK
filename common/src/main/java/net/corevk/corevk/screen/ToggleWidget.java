package net.corevk.corevk.screen;

import net.corevk.corevk.pack.menu.MenuOption;
import net.corevk.corevk.ScreenText;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Optional;

public final class ToggleWidget extends OptionWidget {

	
	private static final int VALUE_WIDTH = 28;

	private static final String ON = "on";
	private static final String OFF = "off";

	private final MenuOption option;

	private boolean value;
	private boolean applied;

	public ToggleWidget(MenuOption option) {
		this.option = option;
	}

	@Override
	public void init(ScreenHost host) {
		super.init(host);
		this.value = on(host.values().pending(this.option.name()));
		this.applied = on(host.values().applied(this.option.name()));
		setLabel(ScreenText.fromPack(host.lang().option(this.option.name())));
	}

	private static boolean on(String value) {
		return ON.equals(value);
	}

	@Override
	public void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered) {
		measure(VALUE_WIDTH);
		drawWithValue(graphics, hovered || isFocused());
		tryTooltip(graphics, mouseX, mouseY, hovered);
	}

	@Override
	protected String name() {
		return this.option.name();
	}

	@Override
	protected Component valueLabel() {
		MutableComponent text = CommonComponents.optionStatus(this.value).copy();

		return this.value == on(this.option.defaultValue())
				? text
				: text.withStyle(this.value ? ChatFormatting.GREEN : ChatFormatting.RED);
	}

	private void queue() {
		host().values().queue(this.option.name(), this.value ? ON : OFF);
	}

	
	@Override
	protected boolean nextValue() {
		this.value = !this.value;
		queue();

		return true;
	}

	@Override
	protected boolean previousValue() {
		return nextValue();
	}

	@Override
	protected boolean originalValue() {
		this.value = on(this.option.defaultValue());
		queue();

		return true;
	}

	@Override
	protected boolean modified() {
		return this.value != this.applied;
	}

	@Override
	protected Optional<String> comment() {
		return host().lang().optionComment(this.option.name());
	}
}
