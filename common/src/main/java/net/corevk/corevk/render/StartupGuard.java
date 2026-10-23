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
		// Before anything below can move the backend: CoreVK read the same file before the game was
		// built, and which engine draws this session follows that value and nothing written after it.
		CoreVKBeside.loaded(options);
		if (cleanly) {
			return true;
		}

		GraphicsApiChoice asked = asked();
		if (asked == GraphicsApiChoice.GAME) {
			return false;
		}

		// Beside CoreVK the backend is not this mod's to move. CoreVK took its hooks for the backend the
		// file names before the game was built, so putting the API back to Vulkan here would leave
		// CoreVK hooked for OpenGL on a Vulkan device. Kept as the file has it, and the resets are
		// still refused, for the reason below. A player who chose the game's own rescue still has it,
		// above: that one is theirs to take whatever it does to CoreVK.
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
		// Set rather than merely kept: a launch that already walked the setting down to Default, or
		// on to OpenGL, would otherwise stay there for good. Coming BACK is what was asked for.
		if (options.preferredGraphicsBackend().get() != wanted) {
			options.preferredGraphicsBackend().set(wanted);
		}

		CoreVK.logger().warn("The last startup ended badly. The game was about to reset the "
				+ "graphics API and the fullscreen mode; both are kept and the API is put back to "
				+ "{}, because a crash during startup is almost never the backend. Change this under "
				+ "Video Settings, in this mod's page, or in corevk/graphics-api.txt",
				wanted.getSerializedName());

		// True, so neither reset runs. The game still wrote the flag false and saved it just before
		// this, so the marker itself goes on working for whatever else reads it.
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
