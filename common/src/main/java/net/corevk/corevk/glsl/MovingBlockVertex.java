package net.corevk.corevk.glsl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public final class MovingBlockVertex {

	
	public static final String NORMAL = "Normal";

	
	public static final String MID_BLOCK = "MidBlock";

	
	public static final List<String> APPENDED =
			List.of(NORMAL, EntityVertex.MID_TEX_COORD, EntityVertex.TANGENT, MID_BLOCK);

	
	public static final List<String> ATTRIBUTES = Stream.concat(
			Stream.of("Position", "Color", "UV0", "UV2"), APPENDED.stream()).toList();

	
	public static final Set<String> ANSWERED = Set.of("mc_midTexCoord", "at_tangent", "at_midBlock");

	private MovingBlockVertex() {
	}

	
	public static List<String> prologue(Set<String> used, Map<String, String> synthesized) {
		List<String> lines = new ArrayList<>();

		for (String attribute : ATTRIBUTES) {
			lines.add("in " + VertexPrologue.elementType(attribute) + " " + attribute + ";");
		}

		lines.add("#define of_Vertex vec4(Position, 1.0)");
		lines.add("#define of_Color Color");
		lines.add("#define of_MultiTexCoord0 vec4(UV0, 0.0, 1.0)");
		// Not full light: CoreVK keys this draw on the light map (ShaderKey.java:50), and the element
		// carries the block's own.
		lines.add("#define of_MultiTexCoord1 vec4(UV2, 0.0, 1.0)");
		lines.add("#define of_MultiTexCoord2 vec4(UV2, 0.0, 1.0)");
		lines.addAll(VertexPrologue.blankTexCoords());
		lines.add("#define of_Normal " + NORMAL + ".xyz");

		VertexPrologue.globals(used, synthesized, Map.of()).forEach((name, type) -> lines.add(
				switch (name) {
					case "mc_midTexCoord" -> "#define " + name + " " + EntityVertex.midTexCoord(type);
					case "at_tangent" -> "#define " + name + " " + EntityVertex.tangent(type);
					case "at_midBlock" -> "#define " + name + " " + midBlock(type);
					case "mc_Entity" -> type + " " + name + " = " + noBlock(type) + ";";
					default -> VertexPrologue.declaration(name, type);
				}));

		return List.copyOf(lines);
	}

	
	private static String midBlock(String type) {
		return switch (type) {
			case "float" -> "(" + MID_BLOCK + ".x * 127.0)";
			case "vec2" -> "(" + MID_BLOCK + ".xy * 127.0)";
			case "vec3" -> "(" + MID_BLOCK + ".xyz * 127.0)";
			case "vec4" -> "(" + MID_BLOCK + " * 127.0)";
			default -> VertexPrologue.zero(type);
		};
	}

	
	private static String noBlock(String type) {
		return switch (type) {
			case "float" -> "-1.0";
			case "int" -> "-1";
			case "vec2" -> "vec2(-1.0)";
			case "vec3" -> "vec3(-1.0, -1.0, 0.0)";
			case "vec4" -> "vec4(-1.0, -1.0, 0.0, 1.0)";
			case "ivec2" -> "ivec2(-1)";
			case "ivec3" -> "ivec3(-1, -1, 0)";
			case "ivec4" -> "ivec4(-1, -1, 0, 1)";
			default -> VertexPrologue.zero(type);
		};
	}
}
