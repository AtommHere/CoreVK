package net.corevk.corevk.screen;

import net.corevk.corevk.screen.ScreenDraw.Icon;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public final class PanelButton extends Button {

	private static final int ICON_REST = 0xFFAAAAAA;
	private static final int ICON_LIT = 0xFF737373;

	private final @Nullable Icon icon;

	private PanelButton(int x, int y, int width, int height, Component message,
			@Nullable Icon icon, OnPress onPress) {
		super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
		this.icon = icon;
	}

	public static PanelButton of(int x, int y, int width, int height, Component message,
			Runnable action) {
		return new PanelButton(x, y, width, height, message, null, _ -> action.run());
	}

	public static PanelButton of(int x, int y, int width, int height, Component message,
			Runnable action, @Nullable Tooltip tooltip) {
		PanelButton button = of(x, y, width, height, message, action);
		button.setTooltip(tooltip);

		return button;
	}

	public static PanelButton icon(int x, int y, int size, Icon icon, Component message,
			Runnable action) {
		PanelButton button = new PanelButton(x, y, size, size, message, icon, _ -> action.run());
		button.setTooltip(Tooltip.create(message));

		return button;
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		ScreenDraw.button(graphics, getX(), getY(), getWidth(), getHeight(),
				isHoveredOrFocused(), !isActive());

		if (this.icon != null) {
			this.icon.draw(graphics, getX() + (getWidth() - this.icon.width()) / 2,
					getY() + (getHeight() - this.icon.height()) / 2,
					isActive() && isHoveredOrFocused() ? ICON_LIT : ICON_REST);

			return;
		}

		extractDefaultLabel(
				graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE));
	}
}
