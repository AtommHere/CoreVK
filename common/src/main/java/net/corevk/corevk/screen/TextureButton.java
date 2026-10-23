package net.corevk.corevk.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class TextureButton extends Button {

	private static final int ATLAS = 256;

	private final Identifier texture;
	private final int u;
	private final int v;

	private final int step;

	private TextureButton(int x, int y, int width, int height, int u, int v, int step,
			Identifier texture, OnPress onPress, Component message) {
		super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
		this.texture = texture;
		this.u = u;
		this.v = v;
		this.step = step;
	}

	public static TextureButton of(int x, int y, int width, int height, int u, int v, int step,
			Identifier texture, Component message, Runnable action) {
		return new TextureButton(x, y, width, height, u, v, step, texture, _ -> action.run(),
				message);
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {

		int row = this.v;
		if (!isActive()) {
			row = this.v + this.step * 2;
		} else if (isHoveredOrFocused()) {
			row = this.v + this.step;
		}

		graphics.blit(RenderPipelines.GUI_TEXTURED, this.texture, getX(), getY(), this.u, row,
				this.width, this.height, ATLAS, ATLAS);
	}
}
