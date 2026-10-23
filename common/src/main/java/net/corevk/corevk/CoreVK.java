package net.corevk.corevk;

import net.corevk.corevk.platform.CoreVKPlatform;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class CoreVK {

	public static final String MOD_ID = "corevk";
	public static final String MOD_NAME = "CoreVK";

	private static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

	
	private static final String BUILD_STAMP = "/corevk-build-identity";

	
	private static final String BUILD_IDENTITY = readBuildIdentity();

	private static CoreVKPlatform platform;

	private CoreVK() {
	}

	public static Logger logger() {
		return LOGGER;
	}

	
	public static String cacheVersion() {
		String version = platform().modVersion();
		int dash = version.indexOf('-');
		if (dash < 0) {
			return version;
		}

		int dot = version.indexOf('.', dash);
		return dot < 0 ? version : version.substring(0, dot);
	}

	
	public static String buildIdentity() {
		return BUILD_IDENTITY;
	}

	
	public static String cacheEditionFamily() {
		return plain(cacheVersion()) + "+mc" + plain(platform().minecraftVersion());
	}

	
	public static String cacheEdition() {
		String build = buildIdentity();

		return build.isEmpty() ? cacheEditionFamily() : cacheEditionFamily() + "+" + plain(build);
	}

	
	private static String plain(String text) {
		return text.replaceAll("[^A-Za-z0-9._-]", "_");
	}

	private static String readBuildIdentity() {
		try (InputStream stamp = CoreVK.class.getResourceAsStream(BUILD_STAMP)) {
			if (stamp == null) {
				return "";
			}

			return new String(stamp.readAllBytes(), StandardCharsets.UTF_8).trim();
		} catch (IOException | RuntimeException ignored) {
			// The release answer, which is the edition every build shared before the stamp existed:
			// a slow launch at worst, and never a failure to start. It cannot be anything louder
			// either, since this runs in a static initialiser and a throw here would be an Error.
			return "";
		}
	}

	public static CoreVKPlatform platform() {
		if (platform == null) {
			throw new IllegalStateException("CoreVK has not been initialised by a loader module");
		}

		return platform;
	}

	public static void initClient(CoreVKPlatform loaderPlatform) {
		if (platform != null) {
			throw new IllegalStateException("CoreVK has already been initialised on " + platform.loaderName());
		}

		platform = loaderPlatform;

		LOGGER.info("CoreVK {} starting on {} {}, Minecraft {}",
				loaderPlatform.modVersion(),
				loaderPlatform.loaderName(),
				loaderPlatform.loaderVersion(),
				loaderPlatform.minecraftVersion());
	}
}
