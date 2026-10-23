package net.corevk.corevk.glsl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public final class EntityVertex {

	
	public static final String IDENTIFIERS = "EntityIds";

	
	public static final String MID_TEX_COORD = "MidTexCoord";

	
	public static final String TANGENT = "Tangent";

	
	public static final List<String> APPENDED = List.of(IDENTIFIERS, MID_TEX_COORD, TANGENT);

	
	public static final List<String> ATTRIBUTES = Stream.concat(
			Stream.of("Position", "Color", "UV0", "UV1", "UV2", "Normal"), APPENDED.stream())
			.toList();

	
	public static final Set<String> ANSWERED = Set.of("mc_midTexCoord", "at_tangent");

	
	public static final String FULL_LIGHT = "vec4(240.0, 240.0, 0.0, 1.0)";

	private EntityVertex() {
	}

	
	public static List<String> prologue(Set<String> used, Map<String, String> synthesized,
			boolean fullbright) {
		List<String> lines = new ArrayList<>();

		for (String attribute : ATTRIBUTES) {
			lines.add("in " + VertexPrologue.elementType(attribute) + " " + attribute + ";");
		}

		// Still declared under full light, and it has to be: the element is in the format whatever
		// the piece does with it, and rebind matches by name over the whole format, so a head that
		// dropped it would move every name after it one location down without a word. CoreVK keeps it
		// for the same reason, renaming vaUV2 in both branches (VanillaCoreTransformer.java:115,119).
		String light = fullbright ? FULL_LIGHT : "vec4(UV2, 0.0, 1.0)";

		lines.add("#define of_Vertex vec4(Position, 1.0)");
		lines.add("#define of_Color Color");
		lines.add("#define of_MultiTexCoord0 vec4(UV0, 0.0, 1.0)");
		lines.add("#define of_MultiTexCoord1 " + light);
		// Unit two is a second name for the light map and not a unit of its own, which is what CoreVK
		// makes of it as well, VanillaTransformer.java:77 renaming one into the other.
		lines.add("#define of_MultiTexCoord2 " + light);
		lines.addAll(VertexPrologue.blankTexCoords());

		lines.add("#define of_Normal Normal.xyz");

		// The two the mesh really carries are macros over their element, like every name above them
		// and for the same reason: a global initialised from a vertex input is not a constant
		// expression. The rest stay constants, mc_Entity among them, and what the picture is then
		// wrong about is what the caller has to name in the log.
		VertexPrologue.globals(used, synthesized, Map.of()).forEach((name, type) -> lines.add(
				ANSWERED.contains(name)
						? "#define " + name + " " + answer(name, type)
						: VertexPrologue.declaration(name, type)));

		return List.copyOf(lines);
	}

	
	private static String answer(String name, String type) {
		return name.equals("at_tangent") ? tangent(type) : midTexCoord(type);
	}

	
	static String midTexCoord(String type) {
		return switch (type) {
			case "float" -> MID_TEX_COORD + ".x";
			case "vec2" -> MID_TEX_COORD;
			case "vec3" -> "vec3(" + MID_TEX_COORD + ", 0.0)";
			case "vec4" -> "vec4(" + MID_TEX_COORD + ", 0.0, 1.0)";
			default -> VertexPrologue.zero(type);
		};
	}

	
	static String tangent(String type) {
		return switch (type) {
			case "float" -> TANGENT + ".x";
			case "vec2" -> TANGENT + ".xy";
			case "vec3" -> TANGENT + ".xyz";
			case "vec4" -> TANGENT;
			default -> VertexPrologue.zero(type);
		};
	}
}
