package net.corevk.corevk.screen;

import net.corevk.corevk.pack.menu.MenuOption;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenAxis;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public final class SliderWidget extends ValueWidget {

	private static final int PREVIEW_WIDTH = 4;
	private static final int HANDLE_WIDTH = 6;

	
	private static final int VALUE_WIDTH = 35;

	private static final int WHITE = 0xFFFFFFFF;

	
	private static final int TEXT_INSET = 7;

	private boolean dragging;

	public SliderWidget(MenuOption option) {
		super(option);
	}

	
	private int steps() {
		return Math.max(1, this.option.size() - 1);
	}

	private float position() {
		return (float) this.index / steps();
	}

	@Override
	public void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered) {
		measure(VALUE_WIDTH);

		if (!hovered && !isFocused()) {
			if (this.usedKeyboard) {
				this.usedKeyboard = false;
				this.dragging = false;
			}

			drawWithValue(graphics, false, position(), PREVIEW_WIDTH);
		} else {
			drawSlider(graphics);
		}

		// The whole name, always, and not only when it had to be cut: the value box takes most of the
		// cell here, so the name is nearly always the part that gave way. CoreVK's rule too.
		int tipX = this.usedKeyboard ? bounds().right() : mouseX;
		int tipY = this.usedKeyboard ? bounds().position().y() : mouseY;
		if (Minecraft.getInstance().hasShiftDown()) {
			tooltip(graphics, SET_TO_DEFAULT, tipX, tipY, hovered);
		} else if (!host().showingComment()) {
			tooltip(graphics, plainLabel(), tipX, tipY, hovered);
		}

		if (this.usedKeyboard && !isFocused()) {
			this.usedKeyboard = false;
			release();
		}

		if (this.dragging && !this.usedKeyboard) {
			if (!hovered) {
				release();
			}

			drag(mouseX);
		}
	}

	private void drawSlider(GuiGraphicsExtractor graphics) {
		ScreenRectangle at = bounds();
		int x = at.position().x();
		int y = at.position().y();

		ScreenDraw.button(graphics, x, y, at.width(), at.height(), isFocused(), forced());
		ScreenDraw.button(graphics, x + 2, y + 2, at.width() - 4, at.height() - 4, false, true);

		int space = (at.width() - 8) - HANDLE_WIDTH;
		int handle = (x + 4) + (int) (position() * space);
		ScreenDraw.button(graphics, handle, y + 4, HANDLE_WIDTH, at.height() - 8, this.dragging,
				false);

		Font font = Minecraft.getInstance().font;
		Component value = valueText();
		graphics.text(font, value,
				at.getCenterInAxis(ScreenAxis.HORIZONTAL) - font.width(value) / 2,
				y + TEXT_INSET, WHITE);
	}

	
	private void drag(int mouseX) {
		ScreenRectangle at = bounds();
		float across = Mth.clamp(
				(float) (mouseX - (at.position().x() + 4)) / (at.width() - 8), 0.0F, 1.0F);
		int wanted = Math.min(this.option.size() - 1, (int) (across * this.option.size()));

		if (this.index != wanted) {
			this.index = wanted;
			updateLabels();
		}
	}

	private void release() {
		this.dragging = false;
		queue();
		host().refresh();
		ScreenDraw.clickSound();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
			// Deliberately not the cell's own answer: a right click walks a value backwards, and there
			// is nothing to walk backwards on a track.
			return false;
		}

		if (forced()) {
			return true;
		}

		if (Minecraft.getInstance().hasShiftDown()) {
			if (originalValue()) {
				host().refresh();
			}

			ScreenDraw.clickSound();

			return true;
		}

		this.dragging = true;
		ScreenDraw.clickSound();

		return true;
	}

	
	@Override
	public boolean keyPressed(KeyEvent event) {
		if (forced()) {
			return event.isConfirmation();
		}

		if (event.isConfirmation()) {
			if (event.hasShiftDown()) {
				if (originalValue()) {
					host().refresh();
				}

				ScreenDraw.clickSound();

				return true;
			}

			this.dragging = !this.dragging;
			this.usedKeyboard = true;
			ScreenDraw.clickSound();

			return true;
		}

		if (this.dragging && this.usedKeyboard && (event.isLeft() || event.isRight())) {
			this.index = Mth.clamp(this.index + (event.isLeft() ? -1 : 1), 0, steps());
			updateLabels();

			return true;
		}

		return false;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
			return super.mouseReleased(event);
		}

		release();

		return true;
	}
}
