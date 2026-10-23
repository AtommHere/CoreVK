package net.corevk.corevk.render;

import net.corevk.corevk.CoreVK;

import net.minecraft.client.Minecraft;

public final class FaceShading {

	private static volatile boolean dropped;

	private FaceShading() {
	}

	public static boolean dropped() {
		return dropped;
	}

	public static void install(boolean oldLighting) {
		follow(!oldLighting && TerrainDraw.asked(), true);
	}

	public static void none() {
		follow(false, false);
	}

	private static void follow(boolean asked, boolean said) {
		if (said) {
			CoreVK.logger().info("The game's own per face brightness is {} the chunk mesh",
					asked ? "left out of" : "in");
		}

		if (dropped == asked) {
			return;
		}

		if (!said) {
			CoreVK.logger().info("The game's own per face brightness is in the chunk mesh again, "
					+ "the pack's own terrain program having stopped drawing the chunks");
		}

		dropped = asked;
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null || minecraft.level == null) {
			return;
		}

		CoreVK.logger().info("The sections carry it, so they are all built again");
		minecraft.levelExtractor.allChanged();
	}
}
