package net.corevk.corevk.render;

import net.corevk.corevk.CoreVK;

import java.nio.file.Files;
import java.nio.file.Path;

public final class LegacyTerrainFilter {

	private static final boolean PROPERTY = Boolean.getBoolean("corevk.legacyTerrainFilter");

	private static final String ARM_FILE = "legacy-terrain-filter";

	
	private static volatile boolean armed;

	private static volatile boolean announced;

	private LegacyTerrainFilter() {
	}

	
	public static boolean armed() {
		announce(armed);

		return armed;
	}

	
	public static void read(Path gameDirectory) {
		armed = PROPERTY
				|| Files.isRegularFile(gameDirectory.resolve(CoreVK.MOD_ID).resolve(ARM_FILE));
		announced = false;
	}

	
	private static void announce(boolean on) {
		if (announced) {
			return;
		}

		announced = true;
		if (on) {
			CoreVK.logger().warn("A pack's terrain reads the block atlas through the GAME's "
					+ "filtered sampler, asked for by corevk/{} or by "
					+ "-Dcorevk.legacyTerrainFilter. That is what this engine bound before and what "
					+ "CoreVK never binds. Remove it and reload the pack to go back",
					ARM_FILE);
			return;
		}

		CoreVK.logger().info("A pack's terrain reads the block atlas unfiltered, as it does under "
				+ "CoreVK. corevk/{} would put the game's filtered sampler back, and this line is "
				+ "said either way so that a reading can name the state it was taken under",
				ARM_FILE);
	}
}
