package net.corevk.corevk.pack.option;

import net.corevk.corevk.pack.model.RenderStage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class EngineDefines {

	public static final int DEFAULT_MC_VERSION = 260200;

	private static final int COREVK_VERSION = 11102;

	private static final int DEFAULT_MIPMAP_LEVEL = 4;

	private static final int MAX_COLOR_BUFFERS = 32;

	private static volatile Environment machine = Environment.of(DEFAULT_MC_VERSION);

	private EngineDefines() {
	}

	public static void machine(Environment environment) {
		machine = environment;
	}

	public static Environment machine() {
		return machine;
	}

	public enum Os {
		WINDOWS,
		MAC,
		LINUX,
		OTHER
	}

	public record TextureFormat(String name, String version) {
	}

	public record Environment(int mcVersion, Os os, String vendorName, String rendererName,
			int mipmapLevel, boolean distantHorizons, Map<String, Integer> biomes,
			List<String> biomeCategories, TextureFormat textureFormat, boolean bufferBlending) {

		public Environment(int mcVersion, Os os, String vendorName, String rendererName,
				int mipmapLevel, boolean distantHorizons, Map<String, Integer> biomes,
				List<String> biomeCategories) {
			this(mcVersion, os, vendorName, rendererName, mipmapLevel, distantHorizons, biomes,
					biomeCategories, null, false);
		}

		public static Environment of(int mcVersion) {
			return new Environment(mcVersion, Os.WINDOWS, "", "", DEFAULT_MIPMAP_LEVEL, false,
					Map.of(), List.of());
		}
	}

	public static Map<String, String> table(int mcVersion) {
		return table(Environment.of(mcVersion));
	}

	@SuppressWarnings("EnumOrdinal")
	public static Map<String, String> table(Environment environment) {
		Map<String, String> defines = new LinkedHashMap<>();

		defines.put("MC_VERSION", Integer.toString(environment.mcVersion()));
		defines.put("COREVK_VERSION", Integer.toString(COREVK_VERSION));
		defines.put("MC_GL_VERSION", "460");
		defines.put("MC_GLSL_VERSION", "460");
		String os = osSymbol(environment.os());
		if (os != null) {
			defines.put(os, "");
		}
		defines.put(vendorSymbol(environment.vendorName()), "");
		defines.put(rendererSymbol(environment.rendererName()), "");
		defines.put("MC_RENDER_QUALITY", "1.0");
		defines.put("MC_SHADOW_QUALITY", "1.0");
		defines.put("MC_HAND_DEPTH", "0.125");
		defines.put("MC_MIPMAP_LEVEL", Integer.toString(environment.mipmapLevel()));
		defines.put("MC_NORMAL_MAP", "");
		defines.put("MC_SPECULAR_MAP", "");

		TextureFormat format = environment.textureFormat();
		if (format != null) {
			String symbol = "MC_TEXTURE_FORMAT_"
					+ format.name().toUpperCase(Locale.ROOT).replace('-', '_');
			defines.put(symbol, "");
			if (format.version() != null) {
				defines.put(symbol + "_" + format.version().replace('.', '_').replace('-', '_'), "");
			}
		}

		defines.put("MAX_COLOR_BUFFERS", Integer.toString(MAX_COLOR_BUFFERS));

		defines.put("IS_COREVK", "");

		defines.put("COREVK_FEATURE_CUSTOM_IMAGES", "");

		defines.put("COREVK_FEATURE_BLOCK_EMISSION_ATTRIBUTE", "");

		defines.put("COREVK_FEATURE_ENTITY_TRANSLUCENT", "");

		defines.put("COREVK_FEATURE_SEPARATE_HARDWARE_SAMPLERS", "");

		defines.put("COREVK_FEATURE_HIGHER_SHADOWCOLOR", "");

		defines.put("COREVK_FEATURE_SSBO", "");

		defines.put("COREVK_FEATURE_COMPUTE_SHADERS", "");

		if (environment.bufferBlending()) {
			defines.put("COREVK_FEATURE_PER_BUFFER_BLENDING", "");
		}

		for (RenderStage stage : RenderStage.values()) {
			defines.put(stage.symbol(), Integer.toString(stage.ordinal()));
		}

		if (environment.distantHorizons()) {
			defines.put("DISTANT_HORIZONS", "");
		}

		defines.put("DH_BLOCK_UNKNOWN", "0");
		defines.put("DH_BLOCK_LEAVES", "1");
		defines.put("DH_BLOCK_STONE", "2");
		defines.put("DH_BLOCK_WOOD", "3");
		defines.put("DH_BLOCK_METAL", "4");
		defines.put("DH_BLOCK_DIRT", "5");
		defines.put("DH_BLOCK_LAVA", "6");
		defines.put("DH_BLOCK_DEEPSLATE", "7");
		defines.put("DH_BLOCK_SNOW", "8");
		defines.put("DH_BLOCK_SAND", "9");
		defines.put("DH_BLOCK_TERRACOTTA", "10");
		defines.put("DH_BLOCK_NETHER_STONE", "11");
		defines.put("DH_BLOCK_WATER", "12");
		defines.put("DH_BLOCK_GRASS", "13");
		defines.put("DH_BLOCK_AIR", "14");
		defines.put("DH_BLOCK_ILLUMINATED", "15");

		defines.put("PPT_NONE", "0");
		defines.put("PPT_RAIN", "1");
		defines.put("PPT_SNOW", "2");

		environment.biomes().forEach((path, id) ->
				defines.put("BIOME_" + path.toUpperCase(Locale.ROOT), Integer.toString(id)));

		List<String> categories = environment.biomeCategories();
		for (int ordinal = 0; ordinal < categories.size(); ordinal++) {
			defines.put("CAT_" + categories.get(ordinal).toUpperCase(Locale.ROOT),
					Integer.toString(ordinal));
		}

		return defines;
	}

	private static String osSymbol(Os os) {
		return switch (os) {
			case MAC -> null;
			case LINUX -> "MC_OS_LINUX";
			case WINDOWS -> "MC_OS_WINDOWS";
			case OTHER -> "MC_OS_UNKNOWN";
		};
	}

	private static String vendorSymbol(String vendorName) {
		String vendor = vendorName.toLowerCase(Locale.ROOT);
		if (vendor.startsWith("ati")) {
			return "MC_GL_VENDOR_ATI";
		} else if (vendor.startsWith("intel")) {
			return "MC_GL_VENDOR_INTEL";
		} else if (vendor.startsWith("nvidia")) {
			return "MC_GL_VENDOR_NVIDIA";
		} else if (vendor.startsWith("amd")) {
			return "MC_GL_VENDOR_AMD";
		} else if (vendor.startsWith("x.org")) {
			return "MC_GL_VENDOR_XORG";
		}

		return "MC_GL_VENDOR_OTHER";
	}

	private static String rendererSymbol(String rendererName) {
		String renderer = rendererName.toLowerCase(Locale.ROOT);
		if (renderer.startsWith("amd") || renderer.startsWith("ati") || renderer.startsWith("radeon")) {
			return "MC_GL_RENDERER_RADEON";
		} else if (renderer.startsWith("gallium")) {
			return "MC_GL_RENDERER_GALLIUM";
		} else if (renderer.startsWith("intel")) {
			return "MC_GL_RENDERER_INTEL";
		} else if (renderer.startsWith("geforce") || renderer.startsWith("nvidia")) {
			return "MC_GL_RENDERER_GEFORCE";
		} else if (renderer.startsWith("quadro") || renderer.startsWith("nvs")) {
			return "MC_GL_RENDERER_QUADRO";
		} else if (renderer.startsWith("mesa")) {
			return "MC_GL_RENDERER_MESA";
		} else if (renderer.startsWith("apple")) {
			return "MC_GL_RENDERER_APPLE";
		}

		return "MC_GL_RENDERER_OTHER";
	}
}
