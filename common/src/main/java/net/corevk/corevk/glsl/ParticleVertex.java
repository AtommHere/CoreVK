package net.corevk.corevk.glsl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ParticleVertex {

	public static final List<String> ATTRIBUTES = List.of("Position", "UV0", "Color", "UV2");

	private ParticleVertex() {
	}

	public static List<String> prologue(Set<String> used, Map<String, String> synthesized) {
		List<String> lines = new ArrayList<>();

		for (String attribute : ATTRIBUTES) {
			lines.add("in " + VertexPrologue.elementType(attribute) + " " + attribute + ";");
		}

		lines.add("#define of_Vertex vec4(Position, 1.0)");
		lines.add("#define of_Color Color");
		lines.add("#define of_MultiTexCoord0 vec4(UV0, 0.0, 1.0)");

		lines.add("#define of_MultiTexCoord1 vec4(UV2, 0.0, 1.0)");
		lines.add("#define of_MultiTexCoord2 vec4(UV2, 0.0, 1.0)");
		lines.addAll(VertexPrologue.blankTexCoords());

		lines.add("#define of_Normal vec3(0.0, 0.0, 1.0)");

		lines.addAll(VertexPrologue.tail(used, synthesized));

		return List.copyOf(lines);
	}
}
