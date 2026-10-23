package net.corevk.corevk.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;

import java.nio.file.Path;

public final class Desktop {

	private Desktop() {
	}

	public static void openPath(Path path) {
		Util.getPlatform().openPath(path);
	}

	public static boolean fullscreen() {
		return Minecraft.getInstance().getWindow().isFullscreen();
	}
}
