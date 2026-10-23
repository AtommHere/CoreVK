package net.corevk.corevk.screen;

import net.corevk.corevk.ScreenText;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

public abstract class OptionWidget extends PageWidget {

	
	private static final Component DIVIDER = Component.literal(": ");

	private static final int WHITE = 0xFFFFFFFF;

	
	private static final int TEXT_INSET = 7;

	
	private static final int LABEL_INSET = 6;

	protected static final Component SET_TO_DEFAULT =
			Component.translatable(ScreenText.SET_TO_DEFAULT).withStyle(ChatFormatting.GREEN);

	
	private @Nullable ScreenHost host;

	
	private MutableComponent label = Component.empty();

	
	private MutableComponent plainLabel = Component.empty();

	private @Nullable Component trimmedLabel;
	private @Nullable Component valueLabel;

	private boolean labelTrimmed;
	private int maxLabelWidth;
	private int valueSectionWidth;

	
	protected boolean usedKeyboard;

	@Override
	public void init(ScreenHost host) {
		this.host = host;
		this.valueLabel = null;
		this.trimmedLabel = null;
	}

	protected final ScreenHost host() {
		if (this.host == null) {
			throw new IllegalStateException("the page was drawn before it was built");
		}

		return this.host;
	}

	
	protected abstract String name();

	
	protected abstract Component valueLabel();

	
	protected abstract boolean nextValue();

	protected abstract boolean previousValue();

	
	protected abstract boolean originalValue();

	protected abstract boolean modified();

	
	protected abstract Optional<String> comment();

	protected final void setLabel(MutableComponent name) {
		this.plainLabel = name;
		this.label = name.copy().append(DIVIDER);
	}

	
	protected final Component plainLabel() {
		return this.plainLabel;
	}

	
	protected final Component valueText() {
		return this.valueLabel == null ? Component.empty() : this.valueLabel;
	}

	
	protected final boolean forced() {
		return host().values().forced(name());
	}

	
	@Override
	protected Component narration() {
		return CommonComponents.optionNameValue(this.plainLabel, valueText());
	}

	@Override
	public Optional<Component> commentTitle() {
		return Optional.of(this.plainLabel);
	}

	@Override
	public Optional<Component> commentBody() {
		return comment().map(ScreenText::fromPack);
	}

	
	protected final void measure(int minValueSectionWidth) {
		this.usedKeyboard = isFocused();

		if (this.valueLabel == null) {
			this.valueLabel = valueLabel();
		}

		Font font = Minecraft.getInstance().font;
		this.valueSectionWidth = Math.max(minValueSectionWidth, font.width(this.valueLabel) + 8);
		this.maxLabelWidth = (bounds().width() - 8) - this.valueSectionWidth;

		// Cut again only when the answer to "does it fit" has changed, which is what keeps a resize
		// from rebuilding every label on every frame.
		if (this.trimmedLabel == null
				|| (font.width(this.label) > this.maxLabelWidth) != this.labelTrimmed) {
			updateLabels();
		}

		this.labelTrimmed = font.width(this.label) > this.maxLabelWidth;
	}

	protected final void updateLabels() {
		Font font = Minecraft.getInstance().font;
		MutableComponent cut = ScreenDraw.shorten(font, this.label.copy(), this.maxLabelWidth);

		this.trimmedLabel = modified() ? cut.withColor(ScreenText.MODIFIED) : cut;
		this.valueLabel = valueLabel();
	}

	protected final void drawWithValue(GuiGraphicsExtractor graphics, boolean hovered) {
		drawWithValue(graphics, hovered, -1.0F, 0);
	}

	
	protected final void drawWithValue(GuiGraphicsExtractor graphics, boolean hovered,
			float sliderPosition, int sliderWidth) {
		ScreenRectangle at = bounds();
		int x = at.position().x();
		int y = at.position().y();

		ScreenDraw.button(graphics, x, y, at.width(), at.height(), hovered, forced());
		ScreenDraw.button(graphics, at.right() - (this.valueSectionWidth + 2), y + 2,
				this.valueSectionWidth, at.height() - 4, false, true);

		if (sliderPosition >= 0.0F) {
			int space = (this.valueSectionWidth - 4) - sliderWidth;
			int position = (at.right() - this.valueSectionWidth) + (int) (sliderPosition * space);

			ScreenDraw.button(graphics, position, y + 4, sliderWidth, at.height() - 8, false, false);
		}

		Font font = Minecraft.getInstance().font;
		graphics.text(font, this.trimmedLabel, x + LABEL_INSET, y + TEXT_INSET, WHITE);
		graphics.text(font, this.valueLabel,
				(at.right() - 2) - this.valueSectionWidth / 2 - font.width(this.valueLabel) / 2,
				y + TEXT_INSET, WHITE);
	}

	
	protected final void tryTooltip(GuiGraphicsExtractor graphics, int x, int y, boolean hovered) {
		if (Minecraft.getInstance().hasShiftDown()) {
			tooltip(graphics, SET_TO_DEFAULT, x, y, hovered);
		} else if (this.labelTrimmed && !host().showingComment()) {
			tooltip(graphics, this.plainLabel, x, y, hovered);
		}
	}

	
	protected final void tooltip(GuiGraphicsExtractor graphics, Component text, int x, int y,
			boolean hovered) {
		if (hovered) {
			host().onTop(() -> ScreenDraw.textPanel(Minecraft.getInstance().font, graphics, text,
					x + 2, y - 16));
		}
	}

	
	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT
				&& event.button() != InputConstants.MOUSE_BUTTON_RIGHT) {
			return super.mouseClicked(event, doubleClick);
		}

		if (forced()) {
			return true;
		}

		boolean moved = Minecraft.getInstance().hasShiftDown() && originalValue();
		if (!moved) {
			moved = event.button() == InputConstants.MOUSE_BUTTON_LEFT
					? nextValue()
					: previousValue();
		}

		if (moved) {
			host().refresh();
		}

		ScreenDraw.clickSound();

		return true;
	}

	
	@Override
	public boolean keyPressed(KeyEvent event) {
		if (!event.isConfirmation()) {
			return false;
		}

		if (forced()) {
			return true;
		}

		boolean moved;
		if (event.hasControlDown()) {
			moved = originalValue();
		} else if (event.hasShiftDown()) {
			moved = previousValue();
		} else {
			moved = nextValue();
		}

		if (moved) {
			host().refresh();
		}

		ScreenDraw.clickSound();

		return true;
	}
}
