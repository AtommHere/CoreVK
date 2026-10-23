package net.corevk.corevk.screen;

import com.mojang.blaze3d.Blaze3D;
import net.minecraft.client.Minecraft;

import java.nio.file.Path;

public final class Desktop {

	private Desktop() {
	}

	public static void openPath(Path path) {
		Blaze3D.openPath(path);
	}

	public static boolean fullscreen() {
		return Minecraft.getInstance().options.fullscreen().get();
	}
}
