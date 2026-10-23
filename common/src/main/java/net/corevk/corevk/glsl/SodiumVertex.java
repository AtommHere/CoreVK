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

		if (used.contains(OWN_NORMAL)) {
			reads.add(TANGENT_FRAME);
		}

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

		globals.forEach((name, type) -> lines.add(answered(carried, name)
				? type + " " + name + ";"
				: VertexPrologue.declaration(name, type)));

		lines.add("float ofAxis(uint at) {");
		lines.add("\tuint top = (a_Position.x >> at) & 1023u;");
		lines.add("\tuint bottom = (a_Position.y >> at) & 1023u;");

		lines.add("\treturn float(top * 1024u + bottom) * (32.0 / 1048576.0) - 8.0;");
		lines.add("}");

		lines.add("vec3 ofSectionOrigin(uint index) {");
		lines.add("\treturn vec3(float((index >> 5u) & 7u), float(index & 3u),"
				+ " float((index >> 2u) & 7u)) * 16.0;");
		lines.add("}");

		if (carried.contains(TANGENT_FRAME)) {
			lines.addAll(TangentFrame.decode(globals.containsKey("at_tangent")));
		}

		lines.add("void " + PROLOGUE + "() {");
		lines.add("\tvec3 ofLocal = vec3(ofAxis(0u), ofAxis(10u), ofAxis(20u));");
		lines.add("\tof_Vertex = vec4(ofLocal + of_RegionOffset"
				+ " + ofSectionOrigin(a_LightAndData.w), 1.0);");

		lines.add("\tof_Color = " + (separateAo ? TINT_AND_AO : "a_Color") + ";");

		lines.add("\tvec2 ofInward = vec2(a_TexCoord >> 15u) * 2.0 - 1.0;");
		lines.add("\tof_MultiTexCoord0 = vec4(vec2(a_TexCoord & 32767u) / 32768.0"
				+ " + ofInward * of_TexShrink, 0.0, 1.0);");

		lines.add("\tof_MultiTexCoord1 = vec4(vec2(a_LightAndData.xy), 0.0, 1.0);");

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
