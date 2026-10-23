package net.corevk.corevk.render.timing;

import net.corevk.corevk.CoreVK;

import net.minecraft.client.Minecraft;

import java.nio.file.Files;

public final class PassBarrier {

	private static final boolean PROPERTY = Boolean.getBoolean("corevk.fullPassBarrier");

	private static final String ARM_FILE = "full-pass-barrier";

	private static Boolean armed;

	private static boolean announced;

	private PassBarrier() {
	}

	public static boolean full() {
		if (PROPERTY) {
			announce();

			return true;
		}

		if (armed == null) {
			Minecraft minecraft = Minecraft.getInstance();
			if (minecraft == null || minecraft.gameDirectory == null) {
				return false;
			}

			armed = Files.isRegularFile(minecraft.gameDirectory.toPath()
					.resolve("corevk").resolve(ARM_FILE));
		}

		if (armed) {
			announce();
		}

		return armed;
	}

	private static void announce() {
		if (announced) {
			return;
		}

		announced = true;
		CoreVK.logger().warn("Every render pass closes on the game's full memory barrier, asked "
				+ "for by corevk/{} or by -Dcorevk.fullPassBarrier. The image is the safe one and "
				+ "the frame is slower; remove it to go back to the narrow wait", ARM_FILE);
	}
}
