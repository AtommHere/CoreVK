package net.corevk.corevk.render;

import net.corevk.corevk.CoreVK;
import net.corevk.corevk.glsl.GlslTranslator;

import java.nio.file.Files;
import java.nio.file.Path;

public final class DriverTrig {

	private static final boolean PROPERTY = Boolean.getBoolean("corevk.driverTrig");

	private static final String ARM_FILE = "driver-trig";

	private static boolean armed;

	private DriverTrig() {
	}

	
	public static void read(Path gameDirectory) {
		armed = PROPERTY
				|| Files.isRegularFile(gameDirectory.resolve(CoreVK.MOD_ID).resolve(ARM_FILE));
		GlslTranslator.reduceTrig(!armed);
	}

	
	public static void announce() {
		int sites = GlslTranslator.trigSites();
		if (armed) {
			CoreVK.logger().warn("Every sin and cos of this pack is left to the DRIVER, asked for "
					+ "by corevk/{} or by -Dcorevk.driverTrig, over {} call sites. A large "
					+ "argument sheds its low bits there, which is what the engine's own reduction "
					+ "was written against; remove it to go back", ARM_FILE, sites);
			return;
		}

		CoreVK.logger().info("Every sin and cos of this pack goes through the engine's own "
				+ "reduced-argument helper, over {} call sites, which is the default. corevk/{} "
				+ "would leave the driver's own two in place, and this line is said either way so "
				+ "that a reading can name the state it was taken under", sites, ARM_FILE);
	}
}
