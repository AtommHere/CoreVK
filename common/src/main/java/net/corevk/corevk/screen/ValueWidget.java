package net.corevk.corevk.screen;

import net.corevk.corevk.pack.menu.MenuOption;
import net.corevk.corevk.ScreenText;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.Optional;

public class ValueWidget extends OptionWidget {

	private static final int VALUE_BLUE = 0xFF6688ff;

	protected final MenuOption option;

	protected int index;

	private String applied = "";

	public ValueWidget(MenuOption option) {
		this.option = option;
	}

	@Override
	public void init(ScreenHost host) {
		super.init(host);
		this.index = this.option.indexOf(host.values().pending(this.option.name()));
		this.applied = host.values().applied(this.option.name());
		setLabel(ScreenText.fromPack(host.lang().option(this.option.name())));
	}

	@Override
	public void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered) {
		measure(0);
		drawWithValue(graphics, hovered || isFocused());

		if (this.usedKeyboard) {
			tryTooltip(graphics, bounds().right(), bounds().position().y(), hovered);
		} else {
			tryTooltip(graphics, mouseX, mouseY, hovered);
		}
	}

	@Override
	protected String name() {
		return this.option.name();
	}

	protected final String value() {
		return this.option.at(this.index);
	}

	@Override
	protected Component valueLabel() {
		return ScreenText
				.fromPack(host().lang().value(this.option.name(), value()))
				.withColor(VALUE_BLUE);
	}

	protected final void queue() {
		host().values().queue(this.option.name(), value());
	}

	private void step(int by) {
		this.index = Math.floorMod(this.index + by, Math.max(1, this.option.size()));
		queue();
	}

	@Override
	protected boolean nextValue() {
		step(1);

		return true;
	}

	@Override
	protected boolean previousValue() {
		step(-1);

		return true;
	}

	@Override
	protected boolean originalValue() {
		this.index = this.option.indexOf(this.option.defaultValue());
		queue();

		return true;
	}

	@Override
	protected boolean modified() {
		return !this.applied.equals(value());
	}

	@Override
	protected Optional<String> comment() {
		return host().lang().optionComment(this.option.name());
	}
}
