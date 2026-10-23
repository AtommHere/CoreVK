package net.corevk.corevk.screen;

import net.corevk.corevk.ScreenText;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenAxis;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

public final class LinkWidget extends PageWidget {

	private static final Component ARROW = Component.literal(">");

	private static final int WHITE = 0xFFFFFFFF;

	
	private static final int TEXT_INSET = 7;
	private static final int ARROW_INSET = 9;

	private final String page;
	private final boolean resolved;

	private @Nullable ScreenHost host;
	private MutableComponent label = Component.empty();
	private @Nullable Component trimmedLabel;
	private boolean labelTrimmed;

	
	public LinkWidget(String page, boolean resolved) {
		this.page = page;
		this.resolved = resolved;
	}

	@Override
	public void init(ScreenHost host) {
		this.host = host;
		this.label = ScreenText.fromPack(host.lang().page(this.page));
		this.trimmedLabel = null;
	}

	private ScreenHost host() {
		if (this.host == null) {
			throw new IllegalStateException("the page was drawn before it was built");
		}

		return this.host;
	}

	@Override
	public void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered) {
		ScreenRectangle at = bounds();
		ScreenDraw.button(graphics, at.position().x(), at.position().y(), at.width(), at.height(),
				hovered || isFocused(), !this.resolved);

		Font font = Minecraft.getInstance().font;
		int room = at.width() - ARROW_INSET;
		if (font.width(this.label) > room) {
			this.labelTrimmed = true;
		}

		if (this.trimmedLabel == null) {
			this.trimmedLabel = ScreenDraw.shorten(font, this.label.copy(), room);
		}

		// Centred, then nudged left by half of however much it still overruns the room the arrow
		// leaves, which is CoreVK's arithmetic and what keeps a long name off the arrow.
		int width = font.width(this.trimmedLabel);
		int overrun = Math.max(width - (at.width() - 18), 0);
		graphics.text(font, this.trimmedLabel,
				at.getCenterInAxis(ScreenAxis.HORIZONTAL) - width / 2 - overrun / 2,
				at.position().y() + TEXT_INSET, WHITE);
		graphics.text(font, ARROW, at.right() - ARROW_INSET, at.position().y() + TEXT_INSET, WHITE);

		if (hovered && this.labelTrimmed) {
			// Drawn after every row, so that the row below cannot be drawn over it.
			host().onTop(() -> ScreenDraw.textPanel(font, graphics, this.label, mouseX + 2,
					mouseY - 16));
		}
	}

	private boolean open() {
		if (!this.resolved) {
			return true;
		}

		host().openPage(this.page);
		ScreenDraw.clickSound();

		return true;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		return event.button() == InputConstants.MOUSE_BUTTON_LEFT
				? open()
				: super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		return event.isConfirmation() && open();
	}

	@Override
	protected Component narration() {
		return this.label;
	}

	@Override
	public Optional<Component> commentTitle() {
		return Optional.of(this.label);
	}

	@Override
	public Optional<Component> commentBody() {
		return host().lang().pageComment(this.page).map(ScreenText::fromPack);
	}
}
