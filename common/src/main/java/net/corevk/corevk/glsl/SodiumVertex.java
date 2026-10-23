package net.corevk.corevk.glsl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SodiumVertex {

	
	public static final String PROLOGUE = "ofSodiumVertex";

	
	public static final String BLOCK_ID = "a_BlockId";

	
	public static final String MID_TEX_COORD = "a_MidTexCoord";

	
	public static final String MID_BLOCK = "a_MidBlock";

	
	public static final String TANGENT_FRAME = "a_TangentFrame";

	
	public static final String TINT_AND_AO = "a_TintAndAo";

	
	public static final List<String> ATTRIBUTES =
			List.of("a_Position", "a_Color", "a_TexCoord", "a_LightAndData", BLOCK_ID, MID_TEX_COORD,
					MID_BLOCK, TANGENT_FRAME, TINT_AND_AO);

	
	private static final Set<String> OURS =
			Set.of(BLOCK_ID, MID_TEX_COORD, MID_BLOCK, TANGENT_FRAME, TINT_AND_AO);

	
	private static final String OWN_NORMAL = "of_Normal";

	
	private static final Map<String, String> OUT_OF = outOf();

	
	public static final Set<String> ANSWERED = OUT_OF.keySet();

	
	private static final Map<String, String> FIXED = fixed();

	private SodiumVertex() {
	}

	
	public static Set<String> reads(Set<String> used, Map<String, String> synthesized,
			boolean separateAo) {
		Set<String> reads = new LinkedHashSet<>();
		for (String name : VertexPrologue.globals(used, synthesized, FIXED).keySet()) {
			String element = OUT_OF.get(name);
			if (element != null) {
				reads.add(element);
			}
		}

		// Not one of the four, because it answers no name the pack writes: a body reads gl_Normal,
		// and the translation has already turned that into of_Normal by the time this is asked. It
		// is the same element at_tangent comes out of, so a pack that reads one of the two pays for
		// both and a pack that reads neither pays for neither.
		if (used.contains(OWN_NORMAL)) {
			reads.add(TANGENT_FRAME);
		}

		// Nor is this, for the other reason: it is a second shape for a word the mesh already
		// carries, and what decides whether a stage reads it is the directive and not the body.
		if (separateAo) {
			reads.add(TINT_AND_AO);
		}

		return reads;
	}

	
	public static List<String> carried(Set<String> reads) {
		return ATTRIBUTES.stream()
				.filter(attribute -> !OURS.contains(attribute) || reads.contains(attribute))
				.toList();
	}

	
	public static List<String> prologue(List<String> carried, Set<String> used,
			Map<String, String> synthesized, boolean separateAo) {
		List<String> lines = new ArrayList<>();

		for (String attribute : carried) {
			lines.add("in " + type(attribute) + " " + attribute + ";");
		}

		// Named apart from the pack's own uniforms on purpose. This block is not in the bind group
		// and never reaches the reflection the engine binds by name: SPIRV-Cross lists push
		// constants under a resource type the game does not ask for, which is why Sodium's own
		// shader gets away with the same declaration.
		lines.add("layout(push_constant) uniform OfSodiumRegion {");
		lines.add("\tvec3 of_RegionOffset;");
		lines.add("\tint of_RegionAge;");
		lines.add("\tuint of_RegionId;");
		lines.add("};");

		lines.add("vec4 of_Vertex;");
		lines.add("vec4 of_Color;");
		lines.add("vec4 of_MultiTexCoord0;");
		lines.add("vec4 of_MultiTexCoord1;");
		lines.add("vec3 of_Normal;");

		Map<String, String> globals = VertexPrologue.globals(used, synthesized, FIXED);

		// A name the mesh answers is left uninitialised here and filled in the prologue below, like
		// of_Vertex above it. A global initialiser that reads an attribute is not a constant
		// expression, and the language does not allow one. A name whose element this pack was not
		// asked to carry takes the constant instead, which is the road every name outside ANSWERED
		// has always taken.
		globals.forEach((name, type) -> lines.add(answered(carried, name)
				? type + " " + name + ";"
				: VertexPrologue.declaration(name, type)));

		// One coordinate out of the pair, given where its ten bit halves sit in each component.
		lines.add("float ofAxis(uint at) {");
		lines.add("\tuint top = (a_Position.x >> at) & 1023u;");
		lines.add("\tuint bottom = (a_Position.y >> at) & 1023u;");
		// Thirty-two blocks across the full twenty bit range, starting eight blocks early.
		lines.add("\treturn float(top * 1024u + bottom) * (32.0 / 1048576.0) - 8.0;");
		lines.add("}");

		// Where the section this draw command belongs to sits inside its region, in blocks. The
		// index packs eight sections across, four up and eight deep. Leaving this out is not
		// subtle: every section of a region lands on the region's own corner.
		lines.add("vec3 ofSectionOrigin(uint index) {");
		lines.add("\treturn vec3(float((index >> 5u) & 7u), float(index & 3u),"
				+ " float((index >> 2u) & 7u)) * 16.0;");
		lines.add("}");

		// Both functions read the element by name, so they are written only where the format carries
		// it: emitted over a mesh without it, they would name an input the stage never declared.
		if (carried.contains(TANGENT_FRAME)) {
			lines.addAll(TangentFrame.decode(globals.containsKey("at_tangent")));
		}

		lines.add("void " + PROLOGUE + "() {");
		lines.add("\tvec3 ofLocal = vec3(ofAxis(0u), ofAxis(10u), ofAxis(20u));");
		lines.add("\tof_Vertex = vec4(ofLocal + of_RegionOffset"
				+ " + ofSectionOrigin(a_LightAndData.w), 1.0);");
		// Sodium's own word holds the tint with the occlusion multiplied into it; ours holds the two
		// apart, and separateAo is the pack saying it means to see them apart. Both are on every
		// vertex, so this line is the whole of what the directive decides and no mesh is rebuilt
		// for it. The word left unread here is the one the game's own shader goes on reading.
		lines.add("\tof_Color = " + (separateAo ? TINT_AND_AO : "a_Color") + ";");
		// The top bit of each texture coordinate says which side of its sprite this corner is on,
		// and the coordinate is pulled that way by a fraction of a texel. Leaving it out is not
		// invisible: a corner that lands exactly on a sprite's edge picks up the neighbouring sprite
		// of the atlas, which shows as a fringe along the top of every block of grass.
		lines.add("\tvec2 ofInward = vec2(a_TexCoord >> 15u) * 2.0 - 1.0;");
		lines.add("\tof_MultiTexCoord0 = vec4(vec2(a_TexCoord & 32767u) / 32768.0"
				+ " + ofInward * of_TexShrink, 0.0, 1.0);");
		// The light map RAW, as the mesh carries it, which is a pair of levels from nought to two
		// hundred and forty. Dividing by 256 here is the same mistake as answering
		// gl_TextureMatrix[1] with the identity, seen from the other end: the scale belongs in that
		// matrix, where the pack applies it itself and where the half texel that centres the sample
		// on its level comes with it. CoreVK carries the raw pair too, SodiumTransformer.java:228,
		// and so does every other family of this engine.
		lines.add("\tof_MultiTexCoord1 = vec4(vec2(a_LightAndData.xy), 0.0, 1.0);");
		// Asked of the carried list and not of ANSWERED, this being the one name of the head that is
		// no spelling of the pack's. A pack no chunk program of which reads gl_Normal leaves the
		// element off the mesh, and what stands here is then the constant every other family hands
		// back for a mesh with no normal in it.
		lines.add("\tof_Normal = " + (carried.contains(TANGENT_FRAME)
				? TangentFrame.NORMAL_OF + "(" + TANGENT_FRAME + ")"
				: VertexPrologue.value(OWN_NORMAL, "vec3")) + ";");
		globals.forEach((name, type) -> {
			if (answered(carried, name)) {
				lines.add("\t" + name + " = " + answer(name, type) + ";");
			}
		});

		lines.add("}");

		return List.copyOf(lines);
	}

	
	private static boolean answered(List<String> carried, String name) {
		String element = OUT_OF.get(name);

		return element != null && carried.contains(element);
	}

	
	private static String answer(String name, String type) {
		return switch (name) {
			case "mc_midTexCoord" -> midTexCoord(type);
			case "at_midBlock" -> midBlock(type);
			case "at_tangent" -> tangent(type);
			default -> entity(type);
		};
	}

	
	private static String midTexCoord(String type) {
		String pair = "vec2(" + MID_TEX_COORD + ") * (1.0 / 32768.0)";

		return switch (type) {
			case "float" -> "float(" + MID_TEX_COORD + ".x) * (1.0 / 32768.0)";
			case "vec2" -> pair;
			case "vec3" -> "vec3(" + pair + ", 0.0)";
			case "vec4" -> "vec4(" + pair + ", 0.0, 1.0)";
			default -> VertexPrologue.zero(type);
		};
	}

	
	private static String tangent(String type) {
		String frame = TangentFrame.TANGENT_OF + "(" + OWN_NORMAL + ", " + TANGENT_FRAME + ")";

		return switch (type) {
			case "float" -> frame + ".x";
			case "vec2" -> frame + ".xy";
			case "vec3" -> frame + ".xyz";
			case "vec4" -> frame;
			// A tangent is an axis and not a zero: a pack normalises what it reads here, and
			// normalize(vec3(0)) puts a NaN in the colour. That rule is carried by the vec3 and vec4
			// arms above, which hand back a whole direction, and by the at_tangent entry of
			// VertexPrologue.BETTER_DEFAULTS, which answers for a vec4 declaration alone, each entry
			// there being matched against the spelling the pack used.
			//
			// It is NOT carried by this arm, which answers zero for every type but a matrix, any
			// more than by the float and vec2 arms, which take a component or two and so answer zero
			// for a tangent along Z - nor, on the road that has no mesh element at all, by anything
			// but that vec4 entry, a pack declaring three components getting the same zero there.
			// Every at_tangent declaration of the corpus is a vec4, so no pack reaches the gap; it
			// is a gap all the same.
			default -> type.startsWith("mat") ? type + "(1.0)" : VertexPrologue.zero(type);
		};
	}

	
	private static String midBlock(String type) {
		return switch (type) {
			case "float" -> "float(" + MID_BLOCK + ".x)";
			case "vec2" -> "vec2(" + MID_BLOCK + ".xy)";
			case "vec3" -> "vec3(" + MID_BLOCK + ".xyz)";
			case "vec4" -> "vec4(" + MID_BLOCK + ")";
			case "ivec2" -> MID_BLOCK + ".xy";
			case "ivec3" -> MID_BLOCK + ".xyz";
			case "ivec4" -> MID_BLOCK;
			default -> VertexPrologue.zero(type);
		};
	}

	
	private static String entity(String type) {
		String id = "int(" + BLOCK_ID + " >> 1u) - 1";
		String fluid = BLOCK_ID + " & 1u";

		return switch (type) {
			case "float", "int" -> id;
			// CoreVK writes the signed form straight into a uint here, which no strict compiler takes.
			case "uint" -> "uint(" + id + ")";
			case "vec2" -> "vec2(" + id + ", " + fluid + ")";
			case "vec3" -> "vec3(" + id + ", " + fluid + ", 0.0)";
			case "vec4" -> "vec4(" + id + ", " + fluid + ", 0.0, 1.0)";
			case "ivec2" -> "ivec2(" + id + ", " + fluid + ")";
			case "ivec3" -> "ivec3(" + id + ", " + fluid + ", 0)";
			case "ivec4" -> "ivec4(" + id + ", " + fluid + ", 0, 1)";
			default -> VertexPrologue.zero(type);
		};
	}

	
	private static String type(String attribute) {
		return switch (attribute) {
			case "a_Position", "a_TexCoord", MID_TEX_COORD -> "uvec2";
			case MID_BLOCK -> "ivec4";
			case TINT_AND_AO -> "vec4";
			case "a_LightAndData" -> "uvec4";
			case BLOCK_ID, TANGENT_FRAME -> "uint";
			default -> "vec4";
		};
	}

	private static Map<String, String> outOf() {
		Map<String, String> names = new LinkedHashMap<>();
		names.put("mc_Entity", BLOCK_ID);
		names.put("mc_midTexCoord", MID_TEX_COORD);
		names.put("at_midBlock", MID_BLOCK);
		names.put("at_tangent", TANGENT_FRAME);

		return Collections.unmodifiableMap(names);
	}

	private static Map<String, String> fixed() {
		Map<String, String> names = new LinkedHashMap<>();
		for (int unit = 2; unit <= 7; unit++) {
			names.put("of_MultiTexCoord" + unit, "vec4");
		}

		return Collections.unmodifiableMap(names);
	}
}
