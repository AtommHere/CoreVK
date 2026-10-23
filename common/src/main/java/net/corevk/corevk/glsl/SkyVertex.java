package net.corevk.corevk.glsl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SkyVertex {

	public static final List<String> ATTRIBUTES = List.of("Position", "Color", "UV0");

	private SkyVertex() {
	}

	public static List<String> prologue(List<String> bound, Set<String> used,
			Map<String, String> synthesized) {
		List<String> lines = new ArrayList<>();

		for (String attribute : bound) {
			lines.add("in " + VertexPrologue.elementType(attribute) + " " + attribute + ";");
		}

		lines.add("#define of_Vertex vec4(Position, 1.0)");

		lines.add(bound.contains("Color")
				? "#define of_Color (Color * of_PassColour)"
				: "#define of_Color of_PassColour");

		String texture = bound.contains("UV0") ? "vec4(UV0, 0.0, 1.0)" : "vec4(0.5, 0.5, 0.0, 1.0)";
		lines.add("#define of_MultiTexCoord0 " + texture);

		for (int unit = 1; unit <= 2; unit++) {
			lines.add("#define of_MultiTexCoord" + unit + " vec4(240.0, 240.0, 0.0, 1.0)");
		}

		lines.addAll(VertexPrologue.blankTexCoords());

		lines.add("#define of_Normal vec3(0.0, 0.0, 1.0)");
		lines.addAll(VertexPrologue.tail(used, synthesized));

		return List.copyOf(lines);
	}
}
