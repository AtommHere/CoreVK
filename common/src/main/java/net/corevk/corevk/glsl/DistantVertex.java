package net.corevk.corevk.glsl;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DistantVertex {

	public static final String PROLOGUE = "ofDistantVertex";

	public static final String SECTION_BLOCK = "OfDistantSection";

	public static final String SECTION_OFFSET = "of_SectionOffset";

	public static final String POSITION = "vPosition";

	public static final String META = "meta";

	public static final String COLOUR = "vColor";

	public static final String MATERIAL = "corevkMaterial";

	public static final String NORMAL = "corevkNormal";

	public static final List<String> ATTRIBUTES = List.of(POSITION, META, COLOUR, MATERIAL, NORMAL);

	private static final Set<String> ALWAYS = Set.of(POSITION, META);

	public static final Set<String> ANSWERED = Set.of("dhMaterialId");

	private static final String OWN_NORMAL = "of_Normal";

	private static final String MICRO = "0.01";

	private DistantVertex() {
	}

	public static Set<String> reads(Set<String> used, Map<String, String> synthesized) {
		Set<String> reads = new LinkedHashSet<>(ALWAYS);
		if (used.contains("of_Color")) {
			reads.add(COLOUR);
		}

		if (used.contains(OWN_NORMAL)) {
			reads.add(NORMAL);
		}

		if (VertexPrologue.globals(used, synthesized, Map.of()).containsKey("dhMaterialId")) {
			reads.add(MATERIAL);
		}

		return reads;
	}

	public static List<String> carried(Set<String> reads) {
		return ATTRIBUTES.stream().filter(reads::contains).toList();
	}

	public static List<String> prologue(List<String> carried, Set<String> used,
			Map<String, String> synthesized) {
		List<String> lines = new ArrayList<>();

		for (String attribute : carried) {
			lines.add("in " + type(attribute) + " " + attribute + ";");
		}

		lines.add("layout(std140) uniform " + SECTION_BLOCK + " {");
		lines.add("\tvec3 " + SECTION_OFFSET + ";");
		lines.add("};");

		lines.add("vec4 of_Vertex;");
		lines.add("vec4 of_Color;");
		lines.add("vec3 of_Normal;");

		if (carried.contains(NORMAL)) {
			lines.add("const vec3 ofDistantFaces[6] = vec3[6](vec3(0.0, -1.0, 0.0), "
					+ "vec3(0.0, 1.0, 0.0), vec3(0.0, 0.0, -1.0), vec3(0.0, 0.0, 1.0), "
					+ "vec3(-1.0, 0.0, 0.0), vec3(1.0, 0.0, 0.0));");
		}

		lines.add("#define of_MultiTexCoord0 vec4(0.0, 0.0, 0.0, 1.0)");
		lines.add("#define of_MultiTexCoord1 vec4(ofDistantLight(), 0.0, 1.0)");
		lines.add("#define of_MultiTexCoord2 vec4(ofDistantLight(), 0.0, 1.0)");
		lines.addAll(VertexPrologue.blankTexCoords());

		lines.add("vec2 ofDistantLight() {");
		lines.add("\treturn (vec2(float((" + META + " >> 4u) & 15u), float(" + META
				+ " & 15u)) + 0.5) / 16.0;");
		lines.add("}");

		lines.add("vec3 ofDistantNudge() {");
		lines.add("\tuint at = (" + META + " >> 8u) & 63u;");
		lines.add("\tfloat x = (at & 1u) != 0u ? " + MICRO + " : 0.0;");
		lines.add("\tfloat z = (at & 16u) != 0u ? " + MICRO + " : 0.0;");
		lines.add("\treturn vec3((at & 2u) != 0u ? -x : x, 0.0, (at & 32u) != 0u ? -z : z);");
		lines.add("}");

		Map<String, String> globals = VertexPrologue.globals(used, synthesized, Map.of());
		globals.forEach((name, type) -> lines.add(ANSWERED.contains(name) && carried.contains(MATERIAL)
				? type + " " + name + ";"
				: VertexPrologue.declaration(name, type)));

		lines.add("void " + PROLOGUE + "() {");
		lines.add("\tof_Vertex = vec4(vec3(" + POSITION + ") + " + SECTION_OFFSET
				+ " + ofDistantNudge(), 1.0);");

		lines.add("\tof_Color = " + (carried.contains(COLOUR) ? COLOUR : "vec4(1.0)") + ";");
		lines.add("\tof_Normal = " + (carried.contains(NORMAL) ? "ofDistantFaces[" + NORMAL + "]"
				: VertexPrologue.value(OWN_NORMAL, "vec3")) + ";");
		if (globals.containsKey("dhMaterialId") && carried.contains(MATERIAL)) {
			lines.add("\tdhMaterialId = " + material(globals.get("dhMaterialId")) + ";");
		}

		lines.add("}");

		return List.copyOf(lines);
	}

	private static String material(String type) {
		return switch (type) {
			case "int" -> "int(" + MATERIAL + ")";
			case "uint" -> MATERIAL;
			case "float" -> "float(" + MATERIAL + ")";
			default -> type + "(" + MATERIAL + ")";
		};
	}

	private static String type(String attribute) {
		return switch (attribute) {
			case POSITION -> "uvec3";
			case COLOUR -> "vec4";

			default -> "uint";
		};
	}
}
