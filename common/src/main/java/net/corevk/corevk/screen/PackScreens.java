package net.corevk.corevk.screen;

import net.corevk.corevk.HostReport;

import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

public final class PackScreens {

	private PackScreens() {
	}

	public static Screen open(@Nullable Screen parent) {
		if (HostReport.otherBackend()) {
			return new BackendPlaceholder(parent);
		}

		return new SettingsScreen(parent);
	}
}
