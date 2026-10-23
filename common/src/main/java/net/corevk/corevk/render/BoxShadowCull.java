package net.corevk.corevk.render;

import net.corevk.corevk.CoreVK;

import net.minecraft.client.Minecraft;

import java.nio.file.Files;

public final class BoxShadowCull {

	private static final boolean PROPERTY = Boolean.getBoolean("corevk.boxShadowCull");

	private static final String ARM_FILE = "box-shadow-cull";

	
	private static Boolean armed;

	private static boolean announced;

	private BoxShadowCull() {
	}

	
	public static boolean asked() {
		boolean box = PROPERTY || file();
		announce(box);

		return box;
	}

	
	private static void announce(boolean box) {
		if (announced) {
			return;
		}

		announced = true;
		if (box) {
			CoreVK.logger().warn("shadow-cull advanced=BOX file={} property=corevk.boxShadowCull",
					ARM_FILE);
			return;
		}

		CoreVK.logger().info("shadow-cull advanced=SWEPT default");
	}

	private static boolean file() {
		if (armed != null) {
			return armed;
		}

		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null || minecraft.gameDirectory == null) {
			return false;
		}

		armed = Files.isRegularFile(minecraft.gameDirectory.toPath()
				.resolve("corevk").resolve(ARM_FILE));

		return armed;
	}
}
