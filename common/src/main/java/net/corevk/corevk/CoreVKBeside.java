package net.corevk.corevk;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.PreferredGraphicsApi;

public final class CoreVKBeside {

	private static final String MARKER = "net/corevkshaders/corevk/CoreVK.class";

	private static volatile Boolean installed;

	private static volatile Boolean askedVulkan;

	private CoreVKBeside() {
	}

	public static boolean installed() {
		Boolean known = installed;
		if (known == null) {
			known = CoreVKBeside.class.getClassLoader().getResource(MARKER) != null;
			installed = known;
		}

		return known;
	}

	public static void loaded(Options options) {
		if (askedVulkan == null) {
			askedVulkan = options.preferredGraphicsBackend().get() == PreferredGraphicsApi.VULKAN;
		}
	}

	public static boolean draws() {
		if (!installed()) {
			return false;
		}

		Boolean asked = askedVulkan;
		if (asked == null) {
			Minecraft minecraft = Minecraft.getInstance();
			asked = minecraft != null
					&& minecraft.options.preferredGraphicsBackend().get() == PreferredGraphicsApi.VULKAN;
		}

		return !asked;
	}
}
