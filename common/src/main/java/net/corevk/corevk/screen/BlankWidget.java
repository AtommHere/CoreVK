package net.corevk.corevk.screen;

import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.jspecify.annotations.Nullable;

public final class BlankWidget extends PageWidget {

	@Override
	public void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered) {
	}

	@Override
	public ScreenRectangle getRectangle() {
		return ScreenRectangle.empty();
	}

	@Override
	public @Nullable ComponentPath nextFocusPath(FocusNavigationEvent event) {
		return null;
	}
}
