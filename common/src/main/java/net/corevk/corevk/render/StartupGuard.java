package net.corevk.corevk.render;

import net.corevk.corevk.CoreVKBeside;
import net.corevk.corevk.settings.GraphicsApiChoice;
import net.corevk.corevk.CoreVK;

import net.minecraft.client.Options;
import net.minecraft.client.PreferredGraphicsApi;

public final class StartupGuard {

	private static GraphicsApiChoice choice;

	private StartupGuard() {
	}

	public static boolean startedCleanly(Options options, boolean cleanly) {

		CoreVKBeside.loaded(options);
		if (cleanly) {
			return true;
		}

		GraphicsApiChoice asked = asked();
		if (asked == GraphicsApiChoice.GAME) {
			return false;
		}

		if (CoreVKBeside.installed()) {
			CoreVK.logger().warn("The last startup ended badly. The game was about to reset the "
					+ "graphics API and the fullscreen mode; both are kept as they were, {}, and the API "
					+ "is not put back to Vulkan because CoreVK is installed and has already chosen its "
					+ "hooks for this backend",
					options.preferredGraphicsBackend().get().getSerializedName());

			return true;
		}

		PreferredGraphicsApi wanted = asked == GraphicsApiChoice.OPENGL
				? PreferredGraphicsApi.OPENGL
				: PreferredGraphicsApi.VULKAN;

		if (options.preferredGraphicsBackend().get() != wanted) {
			options.preferredGraphicsBackend().set(wanted);
		}

		CoreVK.logger().warn("The last startup ended badly. The game was about to reset the "
				+ "graphics API and the fullscreen mode; both are kept and the API is put back to "
				+ "{}, because a crash during startup is almost never the backend. Change this under "
				+ "Video Settings, in this mod's page, or in corevk/graphics-api.txt",
				wanted.getSerializedName());

		return true;
	}

	private static GraphicsApiChoice asked() {
		GraphicsApiChoice known = choice;
		if (known == null) {
			known = GraphicsApiChoice.read();
			choice = known;
		}

		return known;
	}

	public static void forget() {
		choice = null;
	}
}
