package net.corevk.corevk.glsl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GlintVertex {

	public static final List<String> ATTRIBUTES = List.of("Position", "UV0");

	private static final String FULL_BRIGHT = "vec4(240.0, 240.0, 0.0, 1.0)";

	private GlintVertex() {
	}

	public static List<String> prologue(Set<String> used, Map<String, String> synthesized) {
		List<String> lines = new ArrayList<>();

		for (String attribute : ATTRIBUTES) {
			lines.add("in " + VertexPrologue.elementType(attribute) + " " + attribute + ";");
		}

		lines.add("#define of_Vertex vec4(Position, 1.0)");
		lines.add("#define of_Color vec4(1.0, 1.0, 1.0, " + LegacyGlsl.GLINT_ALPHA + ")");
		lines.add("#define of_MultiTexCoord0 vec4(UV0, 0.0, 1.0)");
		lines.add("#define of_MultiTexCoord1 " + FULL_BRIGHT);

		lines.add("#define of_MultiTexCoord2 " + FULL_BRIGHT);
		lines.addAll(VertexPrologue.blankTexCoords());

		lines.add("#define of_Normal vec3(0.0, 0.0, 1.0)");

		lines.addAll(VertexPrologue.tail(used, synthesized));

		return List.copyOf(lines);
	}
}
