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

		// The body reads gl_Normal; the translation has already turned it into of_Normal by the time
		// this is asked, which is why the name tested is not the pack's own.
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

		// The six faces of a block, in the order DH's own directions number themselves: DOWN, UP,
		// NORTH, SOUTH, WEST, EAST, which is the faceIndex of EDhDirection and what the builder puts
		// on the vertex (LodQuadBuilder.java:392). CoreVK carries the same six in the same order
		// (DHTerrainTransformer.java:117, read at :133), and the order is read off DH rather than
		// off CoreVK because a table copied out of the second would agree with the first only by luck.
		if (carried.contains(NORMAL)) {
			lines.add("const vec3 ofDistantFaces[6] = vec3[6](vec3(0.0, -1.0, 0.0), "
					+ "vec3(0.0, 1.0, 0.0), vec3(0.0, 0.0, -1.0), vec3(0.0, 0.0, 1.0), "
					+ "vec3(-1.0, 0.0, 0.0), vec3(1.0, 0.0, 0.0));");
		}

		// Nothing of the atlas and nothing above the light map, which is what an LOD has not got.
		lines.add("#define of_MultiTexCoord0 vec4(0.0, 0.0, 0.0, 1.0)");
		lines.add("#define of_MultiTexCoord1 vec4(ofDistantLight(), 0.0, 1.0)");
		lines.add("#define of_MultiTexCoord2 vec4(ofDistantLight(), 0.0, 1.0)");
		lines.addAll(VertexPrologue.blankTexCoords());

		lines.add("vec2 ofDistantLight() {");
		lines.add("\treturn (vec2(float((" + META + " >> 4u) & 15u), float(" + META
				+ " & 15u)) + 0.5) / 16.0;");
		lines.add("}");

		// The nudge, out of the six bits above the light: two a coordinate, the low one saying there
		// is an offset and the high one saying it is negative. The Y pair is decoded by neither
		// engine and is dropped here as well: DH's own stage leaves the line commented out
		// (terrain/blaze/vert.vsh:60-61 and :66) and CoreVK works the term out and then leaves it out of the
		// position (DHTerrainTransformer.java:132). An LOD is a heightmap, so its horizontal seams
		// are the ones a nudge is for.
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
		// The colour DH worked out and nothing multiplied into it. DH's own stage multiplies its light
		// map into it there and then; a pack lights its own fragment out of lmCoord, so the product
		// would be the light applied twice. CoreVK hands the element alone as well.
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
			// META, MATERIAL and NORMAL: an unsigned word and two unsigned bytes.
			default -> "uint";
		};
	}
}
