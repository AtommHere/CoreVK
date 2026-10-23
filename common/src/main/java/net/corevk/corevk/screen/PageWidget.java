package net.corevk.corevk.screen;

import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

public abstract class PageWidget implements GuiEventListener, NarratableEntry {

	private ScreenRectangle bounds = ScreenRectangle.empty();
	private boolean focused;

	public void init(ScreenHost host) {
	}

	public final void place(ScreenRectangle bounds) {
		this.bounds = bounds;
	}

	protected final ScreenRectangle bounds() {
		return this.bounds;
	}

	public abstract void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered);

	public Optional<Component> commentTitle() {
		return Optional.empty();
	}

	public Optional<Component> commentBody() {
		return Optional.empty();
	}

	@Override
	public boolean isFocused() {
		return this.focused;
	}

	@Override
	public void setFocused(boolean focused) {
		this.focused = focused;
	}

	@Override
	public @Nullable ComponentPath nextFocusPath(FocusNavigationEvent event) {
		return isFocused() ? null : ComponentPath.leaf(this);
	}

	@Override
	public ScreenRectangle getRectangle() {
		return this.bounds;
	}

	protected Component narration() {
		return Component.empty();
	}

	@Override
	public NarrationPriority narrationPriority() {
		return isFocused() ? NarrationPriority.FOCUSED : NarrationPriority.NONE;
	}

	@Override
	public void updateNarration(NarrationElementOutput output) {
		Component said = narration();
		if (!said.getString().isEmpty()) {

			output.add(NarratedElementType.TITLE,
					Component.translatable("gui.narrate.button", said));
		}
	}
}
