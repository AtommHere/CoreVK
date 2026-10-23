package net.corevk.corevk.glsl;

import net.corevk.corevk.pack.model.ProgramFallbacks;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class LegacyGlsl {

	private LegacyGlsl() {
	}

	public static final Map<String, String> FIXED_FUNCTION_MEMBERS = fixedFunctionMembers();

	public static final Map<String, String> FIXED_ATTRIBUTES = fixedAttributes();

	public static final Map<String, String> FIXED_FUNCTION = fixedFunction();

	public static final Map<String, String> DEPRECATED_FUNCTIONS = deprecatedFunctions();

	public static final Map<String, String> SHADOW_FUNCTIONS = Map.of(
			"shadow2D", "texture",
			"shadow2DLod", "textureLod",
			"shadow2DProj", "textureProj");

	public static final Set<String> DEPTH_LOOKUPS = Set.of(
			"texture", "textureLod", "texelFetch", "textureOffset", "textureLodOffset",
			"textureGrad", "textureProj", "textureProjLod",
			"textureGather", "textureGatherOffset", "textureGatherOffsets");

	public static final Set<String> LEVELLED_LOOKUPS = Set.of(
			"texture", "textureLod", "textureOffset", "textureLodOffset", "textureGrad",
			"textureGradOffset");

	public static final Map<String, String> RESERVED_NAMES = Map.of(
			"texture", "ofTexture",
			"sampler", "ofSampler",
			"image", "ofImage");

	public static final Map<String, String> ENGINE_ATTRIBUTES = engineAttributes();

	public static final Map<String, String> ENTITY_UNIFORMS = entityUniforms();

	public static final Map<String, String> CORE_MATRICES = coreMatrices();

	public static final String GAME_TRANSFORMS = "DynamicTransforms";

	public static final String GAME_TEXTURE_MATRIX = "of_GameTextureMatrix";

	public static final String GLINT_ALPHA = "of_GlintAlpha";

	public static final String OVERLAY_SAMPLER = "ofOverlay";

	public static final String GAME_MODEL_VIEW = "of_GameModelView";

	public static final String CAMERA_BOB = "of_CameraBob";

	private static final Set<String> ENTITY_ROOTS = Set.of(
			"gbuffers_entities", "gbuffers_block", "gbuffers_hand",
			"shadow_entities", "shadow_block");

	private static final Set<String> GAME_DRAW_ROOTS =
			Stream.concat(ENTITY_ROOTS.stream(), Stream.of("gbuffers_armor_glint"))
					.collect(Collectors.toUnmodifiableSet());

	private static final Set<String> LIGHT_DRAW_ROOTS = Set.of("shadow_entities", "shadow_block");

	private static final Set<String> CAMERA_DRAW_ROOTS = GAME_DRAW_ROOTS.stream()
			.filter(root -> !LIGHT_DRAW_ROOTS.contains(root))
			.collect(Collectors.toUnmodifiableSet());

	public static final List<String> FULLSCREEN_ATTRIBUTES = List.of(
			"in vec3 Position;",
			"in vec2 UV0;",
			"#define of_Vertex vec4(Position, 1.0)",
			"#define of_MultiTexCoord0 vec4(UV0, 0.0, 1.0)",
			"#define of_MultiTexCoord1 vec4(0.0, 0.0, 0.0, 1.0)",
			"#define of_MultiTexCoord2 vec4(0.0, 0.0, 0.0, 1.0)",
			"#define of_MultiTexCoord3 vec4(0.0, 0.0, 0.0, 1.0)",
			"#define of_MultiTexCoord4 vec4(0.0, 0.0, 0.0, 1.0)",
			"#define of_MultiTexCoord5 vec4(0.0, 0.0, 0.0, 1.0)",
			"#define of_MultiTexCoord6 vec4(0.0, 0.0, 0.0, 1.0)",
			"#define of_MultiTexCoord7 vec4(0.0, 0.0, 0.0, 1.0)",
			"#define of_Color vec4(1.0)",
			"#define of_Normal vec3(0.0, 0.0, 1.0)");

	public static final List<String> FULLSCREEN_ELEMENTS = List.of("Position", "UV0");

	private static final Map<String, String> FULLSCREEN_ANSWERED = Map.of(
			"vaPosition", "Position",
			"vaUV0", "UV0");

	public static String fullscreenElement(String name, String type) {
		String element = FULLSCREEN_ANSWERED.get(name);
		int wanted = switch (type) {
			case "float" -> 1;
			case "vec2" -> 2;
			case "vec3" -> 3;
			case "vec4" -> 4;
			default -> 0;
		};
		if (element == null || wanted == 0) {
			return null;
		}

		int carried = VertexPrologue.elementType(element).equals("vec3") ? 3 : 2;
		if (wanted == carried) {
			return element;
		}

		if (wanted < carried) {
			return element + "." + "xyzw".substring(0, wanted);
		}

		StringBuilder widened = new StringBuilder(type).append('(').append(element);
		for (int component = carried; component < wanted; component++) {
			widened.append(component == 3 ? ", 1.0" : ", 0.0");
		}

		return widened.append(')').toString();
	}

	public static final Set<String> POST_120_BUILTINS = Set.of(
			"sinh", "cosh", "tanh", "asinh", "acosh", "atanh",
			"fma", "frexp", "ldexp", "round", "roundEven", "trunc", "modf", "isnan", "isinf",
			"inverse", "determinant", "outerProduct", "transpose",
			"floatBitsToInt", "floatBitsToUint", "intBitsToFloat", "uintBitsToFloat",
			"packUnorm2x16", "unpackUnorm2x16", "packSnorm2x16", "unpackSnorm2x16",
			"packHalf2x16", "unpackHalf2x16",
			"bitfieldExtract", "bitfieldInsert", "bitfieldReverse", "bitCount", "findLSB", "findMSB");

	private static final Map<String, String> NARROW_TYPES = narrowTypes();

	public static final Set<String> TYPE_NAMES = typeNames();

	public static final Set<String> PRECISION_QUALIFIERS = Set.of("lowp", "mediump", "highp");

	public static final Set<String> INTERPOLATION_QUALIFIERS =
			Set.of("flat", "smooth", "noperspective", "centroid", "sample", "invariant", "precise");

	public static final Set<String> MEMORY_QUALIFIERS =
			Set.of("restrict", "coherent", "readonly", "writeonly", "volatile");

	public static final Set<String> OPAQUE_DIRECTIVES =
			Set.of("include", "version", "extension", "line", "pragma", "error");

	public static final Set<String> NAMING_DIRECTIVES = Set.of("define", "undef", "ifdef", "ifndef");

	private static final Set<String> OPAQUE_PREFIXES =
			Set.of("sampler", "isampler", "usampler", "image", "iimage", "uimage",
					"texture", "itexture", "utexture", "subpassInput", "atomic_uint");

	private static final Set<String> IMAGE_PREFIXES = Set.of("image", "iimage", "uimage");

	public static boolean drawsEntities(String program) {
		return ProgramFallbacks.chain(program).stream().anyMatch(ENTITY_ROOTS::contains);
	}

	public static boolean bindsGameTransforms(String program) {
		return ProgramFallbacks.chain(program).stream().anyMatch(GAME_DRAW_ROOTS::contains);
	}

	public static boolean readsDrawModelView(String program) {
		return ProgramFallbacks.chain(program).stream().anyMatch(CAMERA_DRAW_ROOTS::contains);
	}

	public static boolean isOpaqueType(String type) {
		for (String prefix : OPAQUE_PREFIXES) {
			if (type.startsWith(prefix)) {
				return true;
			}
		}

		return false;
	}

	static boolean isImageType(String type) {
		for (String prefix : IMAGE_PREFIXES) {
			if (type.startsWith(prefix)) {
				return true;
			}
		}

		return false;
	}

	private static Set<String> typeNames() {
		Set<String> names = new HashSet<>(Set.of("void", "bool", "int", "uint", "float", "double"));

		for (String prefix : List.of("", "b", "i", "u", "d")) {
			for (int size = 2; size <= 4; size++) {
				names.add(prefix + "vec" + size);
			}
		}

		for (String prefix : List.of("", "d")) {
			for (int rows = 2; rows <= 4; rows++) {
				names.add(prefix + "mat" + rows);
				for (int columns = 2; columns <= 4; columns++) {
					names.add(prefix + "mat" + rows + "x" + columns);
				}
			}
		}

		names.addAll(NARROW_TYPES.keySet());
		names.addAll(List.of("float32_t", "float64_t", "int32_t", "int64_t", "uint32_t", "uint64_t"));
		for (String prefix : List.of("f32", "f64", "i32", "i64", "u32", "u64")) {
			for (int size = 2; size <= 4; size++) {
				names.add(prefix + "vec" + size);
			}
		}

		for (String prefix : List.of("f32", "f64")) {
			for (int rows = 2; rows <= 4; rows++) {
				names.add(prefix + "mat" + rows);
				for (int columns = 2; columns <= 4; columns++) {
					names.add(prefix + "mat" + rows + "x" + columns);
				}
			}
		}

		return Set.copyOf(names);
	}

	private static Map<String, String> narrowTypes() {
		Map<String, String> widened = new LinkedHashMap<>();
		widened.put("float16_t", "float");
		widened.put("int16_t", "int");
		widened.put("uint16_t", "uint");
		widened.put("int8_t", "int");
		widened.put("uint8_t", "uint");

		for (int size = 2; size <= 4; size++) {
			widened.put("f16vec" + size, "vec" + size);
			widened.put("i16vec" + size, "ivec" + size);
			widened.put("u16vec" + size, "uvec" + size);
			widened.put("i8vec" + size, "ivec" + size);
			widened.put("u8vec" + size, "uvec" + size);
		}

		for (int rows = 2; rows <= 4; rows++) {
			widened.put("f16mat" + rows, "mat" + rows);
			for (int columns = 2; columns <= 4; columns++) {
				widened.put("f16mat" + rows + "x" + columns, "mat" + rows + "x" + columns);
			}
		}

		return Collections.unmodifiableMap(widened);
	}

	static String widened(String type) {
		return NARROW_TYPES.getOrDefault(type, type);
	}

	private static Map<String, String> engineAttributes() {
		Map<String, String> attributes = new LinkedHashMap<>();

		attributes.put("dhMaterialId", "int dhMaterialId");

		return Collections.unmodifiableMap(attributes);
	}

	private static Map<String, String> coreMatrices() {
		Map<String, String> matrices = new LinkedHashMap<>();

		matrices.put("modelViewMatrix", "mat4 modelViewMatrix");
		matrices.put("projectionMatrix", "mat4 projectionMatrix");

		return Collections.unmodifiableMap(matrices);
	}

	private static Map<String, String> entityUniforms() {
		Map<String, String> uniforms = new LinkedHashMap<>();

		uniforms.put("entityId", "int entityId");
		uniforms.put("blockEntityId", "int blockEntityId");
		uniforms.put("currentRenderedItemId", "int currentRenderedItemId");

		return Collections.unmodifiableMap(uniforms);
	}

	private static Map<String, String> fixedFunctionMembers() {
		Map<String, String> members = new LinkedHashMap<>();

		members.put("of_ModelViewMatrix", "mat4 of_ModelViewMatrix");
		members.put("of_ModelViewProjectionMatrix", "mat4 of_ModelViewProjectionMatrix");
		members.put("of_ProjectionMatrix", "mat4 of_ProjectionMatrix");
		members.put("of_ModelViewMatrixInverse", "mat4 of_ModelViewMatrixInverse");
		members.put("of_ProjectionMatrixInverse", "mat4 of_ProjectionMatrixInverse");
		members.put("of_NormalMatrix", "mat3 of_NormalMatrix");
		members.put("of_TextureMatrix", "mat4 of_TextureMatrix[8]");
		members.put("of_Fog", "OfFog of_Fog");

		return Collections.unmodifiableMap(members);
	}

	private static Map<String, String> fixedAttributes() {
		Map<String, String> attributes = new LinkedHashMap<>();

		attributes.put("of_Vertex", "vec4 of_Vertex");
		attributes.put("of_Color", "vec4 of_Color");
		attributes.put("of_Normal", "vec3 of_Normal");
		for (int unit = 0; unit <= 7; unit++) {
			attributes.put("of_MultiTexCoord" + unit, "vec4 of_MultiTexCoord" + unit);
		}

		return Collections.unmodifiableMap(attributes);
	}

	private static Map<String, String> fixedFunction() {
		Map<String, String> renames = new LinkedHashMap<>();

		for (String name : fixedFunctionMembers().keySet()) {
			renames.put("gl_" + name.substring(3), name);
		}

		for (String name : fixedAttributes().keySet()) {
			renames.put("gl_" + name.substring(3), name);
		}

		renames.put("gl_FogFragCoord", "of_FogFragCoord");

		return Collections.unmodifiableMap(renames);
	}

	private static Map<String, String> deprecatedFunctions() {
		Map<String, String> functions = new LinkedHashMap<>();

		functions.put("texture1D", "texture");
		functions.put("texture2D", "texture");
		functions.put("texture3D", "texture");
		functions.put("textureCube", "texture");
		functions.put("texture2DLod", "textureLod");
		functions.put("texture3DLod", "textureLod");
		functions.put("textureCubeLod", "textureLod");
		functions.put("texture2DProj", "textureProj");
		functions.put("texture2DProjLod", "textureProjLod");
		functions.put("texture2DGrad", "textureGrad");
		functions.put("texture2DGradARB", "textureGrad");
		functions.put("texture2DOffset", "textureOffset");
		functions.put("texture2DLodOffset", "textureLodOffset");
		functions.put("texelFetch2D", "texelFetch");
		functions.put("texelFetch3D", "texelFetch");

		return Collections.unmodifiableMap(functions);
	}
}
