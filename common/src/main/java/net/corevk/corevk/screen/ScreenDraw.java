package net.corevk.corevk.screen;

import net.corevk.corevk.CoreVK;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;

public final class ScreenDraw {

	public static final Identifier WIDGETS =
			Identifier.fromNamespaceAndPath(CoreVK.MOD_ID, "textures/gui/widgets.png");

	private static final int ATLAS = 256;

	private static final int SPRITE_HEIGHT = 20;

	private static final int SPRITE_WIDTH = 200;

	private static final Component ELLIPSIS = Component.literal("...");

	private ScreenDraw() {
	}

	public static void button(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
			boolean hovered, boolean disabled) {
		int halfWidth = width / 2;
		int halfHeight = height / 2;
		int farWidth = width - halfWidth;
		int farHeight = height - halfHeight;

		int v = disabled ? 46 : hovered ? 86 : 66;

		int rightU = SPRITE_WIDTH - farWidth;

		int bottomV = v + (SPRITE_HEIGHT - farHeight);

		graphics.blit(RenderPipelines.GUI_TEXTURED, WIDGETS,
				x, y, 0, v, halfWidth, halfHeight, ATLAS, ATLAS);
		graphics.blit(RenderPipelines.GUI_TEXTURED, WIDGETS,
				x + halfWidth, y, rightU, v, farWidth, halfHeight, ATLAS, ATLAS);
		graphics.blit(RenderPipelines.GUI_TEXTURED, WIDGETS,
				x, y + halfHeight, 0, bottomV, halfWidth, farHeight, ATLAS, ATLAS);
		graphics.blit(RenderPipelines.GUI_TEXTURED, WIDGETS,
				x + halfWidth, y + halfHeight, rightU, bottomV, farWidth, farHeight, ATLAS, ATLAS);
	}

	public static void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
		int border = 0xDEDEDEDE;
		int inner = 0xDE000000;

		graphics.fill(RenderPipelines.GUI, x, y, x + width, y + 1, border);
		graphics.fill(RenderPipelines.GUI, x, y + height - 1, x + width, y + height, border);
		graphics.fill(RenderPipelines.GUI, x, y + 1, x + 1, y + height - 1, border);
		graphics.fill(RenderPipelines.GUI, x + width - 1, y + 1, x + width, y + height - 1, border);
		graphics.fill(RenderPipelines.GUI, x + 1, y + 1, x + width - 1, y + height - 1, inner);
	}

	public static void textPanel(Font font, GuiGraphicsExtractor graphics, Component text, int x,
			int y) {
		panel(graphics, x, y, font.width(text) + 8, 16);
		graphics.text(font, text, x + 4, y + 4, 0xFFFFFFFF);
	}

	public static MutableComponent shorten(Font font, MutableComponent text, int width) {
		if (font.width(text) <= width) {
			return text;
		}

		String kept = font.plainSubstrByWidth(text.getString(), width - font.width(ELLIPSIS));

		return Component.literal(kept).append(ELLIPSIS).setStyle(text.getStyle());
	}

	public static MutableComponent translatedOr(MutableComponent fallback, String key,
			Object... format) {
		return Language.getInstance().has(key) ? Component.translatable(key, format) : fallback;
	}

	public static void clickSound() {
		Minecraft.getInstance().getSoundManager()
				.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1));
	}

	public record Icon(int u, int v, int width, int height) {

		public static final Icon REFRESH = new Icon(12, 0, 10, 10);

		public static final Icon EXPORT = new Icon(22, 0, 7, 8);
		public static final Icon EXPORT_LIT = new Icon(29, 0, 7, 8);
		public static final Icon IMPORT = new Icon(22, 8, 7, 8);
		public static final Icon IMPORT_LIT = new Icon(29, 8, 7, 8);

		public void draw(GuiGraphicsExtractor graphics, int x, int y) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, WIDGETS, x, y, this.u, this.v,
					this.width, this.height, ATLAS, ATLAS);
		}

		public void draw(GuiGraphicsExtractor graphics, int x, int y, int colour) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, WIDGETS, x, y, this.u, this.v,
					this.width, this.height, ATLAS, ATLAS, colour);
		}
	}
}
