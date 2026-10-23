package net.corevk.corevk.screen;

import net.corevk.corevk.render.PackChain;
import net.corevk.corevk.ScreenText;
import net.corevk.corevk.CoreVK;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class CompileCard {

	private static final Identifier COMPILE_ICON =
			Identifier.fromNamespaceAndPath(CoreVK.MOD_ID, "textures/gui/compiling.png");

	private static final long PULSE_MILLIS = 1000L;

	private static final int ICON_EDGE = 16;
	private static final int ICON_TEXTURE = 32;
	private static final int ICON_MARGIN = 3;

	private static final long DONE_MILLIS = 2500L;
	private static final long FADE_MILLIS = 800L;

	private static final long RAMP_MILLIS = 300L;

	private static final float FAINT = 0.05F;

	private static long shownAt;
	private static int shownFor;

	public static final boolean DEFAULT_WANTED = true;

	private static final String SETTING_FILE = "compile-card";

	private static Boolean wanted;

	private CompileCard() {
	}

	public static boolean wanted() {
		if (wanted == null) {
			wanted = read();
		}

		return wanted;
	}

	public static void setWanted(boolean asked) {
		wanted = asked;
		try {
			Path file = file();
			Files.createDirectories(file.getParent());
			Files.writeString(file, asked + "\n");
		} catch (IOException | RuntimeException e) {
			CoreVK.logger().warn("Could not store whether the compile corner shows, it holds for "
					+ "this session only", e);
		}
	}

	private static boolean read() {
		try {
			Path file = file();
			if (!Files.isRegularFile(file)) {
				return DEFAULT_WANTED;
			}

			return !"false".equalsIgnoreCase(Files.readString(file).trim());
		} catch (IOException | RuntimeException ignored) {
			return DEFAULT_WANTED;
		}
	}

	private static Path file() {
		return CoreVK.platform().gameDirectory().resolve("corevk").resolve(SETTING_FILE);
	}

	public static void extract(GuiGraphicsExtractor graphics) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!wanted() || minecraft.gui.hud.isHidden() || minecraft.gui.screen() instanceof LevelLoadingScreen) {
			return;
		}

		if (minecraft.gui.hud.getDebugOverlay().showDebugScreen()) {
			return;
		}

		Optional<Component> words = PackChain.compilingWords();
		long warmedFor = PackChain.warmedForMillis();
		if (words.isEmpty() && warmedFor < 0L) {
			return;
		}

		long now = Util.getMillis();
		int load = PackChain.loadNumber();
		if (shownFor != load) {
			shownFor = load;
			shownAt = now;
		}

		float ease = ramp(now - shownAt);
		Font font = minecraft.font;
		int textX = ICON_MARGIN + ICON_EDGE + 4;
		int textY = ICON_MARGIN + (ICON_EDGE - font.lineHeight) / 2 + 1;

		if (words.isPresent()) {
			float phase = (now % PULSE_MILLIS) / (float) PULSE_MILLIS;
			float pulse = 0.75F + 0.25F * (float) Math.cos(2.0 * Math.PI * phase);
			icon(graphics, ease * pulse);
			word(graphics, font, words.get(), textX, textY, ease);

			return;
		}

		if (warmedFor >= DONE_MILLIS + FADE_MILLIS) {
			return;
		}

		float out = warmedFor <= DONE_MILLIS ? 1.0F
				: 1.0F - (warmedFor - DONE_MILLIS) / (float) FADE_MILLIS;
		icon(graphics, ease * out);
		word(graphics, font, Component.translatable(ScreenText.COMPILED), textX, textY, out);
	}

	private static float ramp(long sinceMillis) {
		return Math.min(1.0F, sinceMillis / (float) RAMP_MILLIS);
	}

	private static void icon(GuiGraphicsExtractor graphics, float alpha) {
		if (alpha < FAINT) {
			return;
		}

		graphics.blit(RenderPipelines.GUI_TEXTURED, COMPILE_ICON, ICON_MARGIN, ICON_MARGIN,
				0.0F, 0.0F, ICON_EDGE, ICON_EDGE, ICON_TEXTURE, ICON_TEXTURE, ICON_TEXTURE,
				ICON_TEXTURE, ARGB.colorFromFloat(alpha, 1.0F, 1.0F, 1.0F));
	}

	private static void word(GuiGraphicsExtractor graphics, Font font, Component words, int x,
			int y, float alpha) {
		if (alpha < FAINT) {
			return;
		}

		graphics.text(font, words, x, y, ARGB.colorFromFloat(alpha, 1.0F, 1.0F, 1.0F), true);
	}
}
