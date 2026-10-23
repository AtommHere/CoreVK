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

		// What the resource pack says its material maps mean, beside the two names above and where
		// CoreVK poses it (gl/shader/StandardMacros.java:107-112, built at
		// pbr/format/TextureFormat.java:18-33). A pack written against the symbol guards its labPBR
		// decode on it, and the version symbol tells such a pack which revision of the convention
		// it is reading. Posed only where a resource pack names a convention in
		// optifine/texture.properties, so an install where nothing names one sees neither symbol.
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

		// Packs gate their modern paths on this rather than on a feature test. Claiming it is
		// how a pack is told that includes, compute passes and the newer uniforms are available;
		// it is also a promise, and every one of those has to work before it can stay.
		defines.put("IS_COREVK", "");

		// Custom images are the voxel volumes Complementary writes from shadow geometry and floods
		// in shadowcomp, and the symbol is a promise of that pipe: the allocation, the 3D bind and
		// the compute dispatch are all there, and a pack that reads it goes down the road that
		// uses them.
		defines.put("COREVK_FEATURE_CUSTOM_IMAGES", "");

		// The block emission attribute is served: the chunk element carries the block's own light
		// in the fourth component of at_midBlock, on the terrain and in the shadow map alike
		// (SodiumVertex.MID_BLOCK). CoreVK holds the flag usable whatever the hardware
		// (features/FeatureFlags.java:20), and packs decide more than a vertex read on it:
		// Complementary sizes its two reflection targets under it, Bliss writes its voxel
		// emission under it.
		defines.put("COREVK_FEATURE_BLOCK_EMISSION_ATTRIBUTE", "");

		// A blending entity draw asks for the pack's own gbuffers_entities_translucent rather than
		// for the opaque entity file, and its block entity twin for gbuffers_block_translucent
		// (render/EntityDraw), each falling back onto its own opaque name so a pack that ships one
		// entity program still draws its whole family with the file it wrote
		// (pack/model/ProgramFallbacks). That is the whole of what the name buys: CoreVK routes the
		// blending entity pipelines through getTranslucent whatever the pack declared
		// (pipeline/CoreVKPipelines.java:35,38,210-220) and reads the flag nowhere else, holding it
		// usable on every machine (features/FeatureFlags.java:18). A pack that ships the program
		// without declaring the name is therefore drawn the same on both, which is what BSL,
		// Complementary and Noble do.
		defines.put("COREVK_FEATURE_ENTITY_TRANSLUCENT", "");

		// shadowtex0HW and shadowtex1HW are bound, the same two depth images as the plain pair and
		// compared where the pack declares them so (SamplerPlan.SHADOW_DEPTH). CoreVK poses this one
		// whatever the hardware, features/FeatureFlags.java:12.
		defines.put("COREVK_FEATURE_SEPARATE_HARDWARE_SAMPLERS", "");

		// The eight shadow colour buffers are served: a pack that declares HIGHER_SHADOWCOLOR may
		// name shadowcolor0 to shadowcolor7, and render/ShadowTargets allocates the ones some
		// program of the place writes or samples. CoreVK holds the flag usable whatever the hardware
		// (features/FeatureFlags.java:13) and reads it in one place only, the size of its shadow
		// colour array (shadows/ShadowRenderTargets.java:46). This define does not raise that
		// ceiling and must not be read as though it did: what sizes it is the pack's own
		// corevk.features line, TargetPlan.shadowCeiling here.
		defines.put("COREVK_FEATURE_HIGHER_SHADOWCOLOR", "");

		// Storage blocks are served: a bufferObject directive is read and its buffer allocated
		// (render/storage/StorageBuffers), and a block a program declares is bound as a storage descriptor
		// rather than a uniform one (mixin/VulkanBindGroupLayoutMixin). CoreVK holds the flag usable
		// only where the driver has them (features/FeatureFlags.java:22,
		// CoreVKRenderSystem.supportsSSBO). Vulkan has them everywhere, so the condition has nothing
		// left to test.
		defines.put("COREVK_FEATURE_SSBO", "");

		// Compute passes are served: a shadow compute is dispatched at the head of the frame and a
		// compute hanging off a full screen pass right before that pass, both reading and storing
		// the colour targets on the halves the pass reads (render/PackCompute). Complementary's
		// floodfill and Photon's sky lighting are both that road and both run. CoreVK holds the flag
		// usable where the driver has compute (features/FeatureFlags.java:14,
		// CoreVKRenderSystem.supportsCompute); Vulkan has it everywhere, so the condition has nothing
		// left to test, as with the storage blocks above.
		defines.put("COREVK_FEATURE_COMPUTE_SHADERS", "");

		// Per buffer blending is served where the DEVICE serves it, which is the one symbol here
		// that is not posed for every machine: a blend.<program>.<buffer> directive is built into
		// the attachment whose rank its target holds (render/GeometryProgram.state), and two
		// attachments of one Vulkan pipeline carry two blend states only under independentBlend.
		// CoreVK withholds its own flag where ITS api cannot part them either, the driver having to
		// carry ARB_draw_buffers_blend or OpenGL 4.0 (features/FeatureFlags.java:15,
		// CoreVKRenderSystem.supportsBufferBlending). Same rule over a different question: a Vulkan
		// device feature and a GL extension are asked of two APIs, so the machines the two engines
		// withhold the symbol from are not the same set.
		if (environment.bufferBlending()) {
			defines.put("COREVK_FEATURE_PER_BUFFER_BLENDING", "");
		}

		// The rest of COREVK_FEATURE_ stays unposted until each capability is served, and every name
		// above but the per buffer one is posed for every pack, that one following the device as
		// said just above. That is a divergence: CoreVK poses COREVK_FEATURE_X only where the pack
		// itself listed X under corevk.features.optional
		// (shaderpack/ShaderPack.java:246-250), its all-usable list serving the evaluation of
		// shaders.properties and nothing in the GLSL (lines 171-175). The two differ only for a
		// pack that reads a symbol it never declared, and closing it is a lot of its own. See
		// ShaderProperties.declares for the declaration itself, and PackChain for what a pack
		// requiring one of these names is told.

		// Straight off the enum the engine sets, so that the number a pack compares against and the
		// number the block carries cannot part company. The value IS the ordinal.
		for (RenderStage stage : RenderStage.values()) {
			defines.put(stage.symbol(), Integer.toString(stage.ordinal()));
		}

		// What a pack branches its far terrain on. Posed only where the terrain really reaches the
		// pack, which is what the name means to the packs that read it: what sits under it in the
		// corpus is written for a picture with a far terrain in its depth, its own blend states and
		// its own programs, and posing it over a depth that has none applies them to a screen they
		// were never meant for. CoreVK poses it on the mod being loaded AND its rendering answering,
		// gl/shader/StandardMacros.java:64; render/PackDefines asks the same two questions here.
		if (environment.distantHorizons()) {
			defines.put("DISTANT_HORIZONS", "");
		}

		// Distant Horizons block kinds. No pack declares them and several read them, so leaving
		// them out turns a working pack into a wall of undeclared identifiers.
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

		// Empty until a caller has a registry to walk, which is the point: a biome symbol invented
		// here would be a number the engine cannot answer with.
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
