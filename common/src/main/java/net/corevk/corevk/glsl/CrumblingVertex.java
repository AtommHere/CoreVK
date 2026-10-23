package net.corevk.corevk.glsl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CrumblingVertex {

	
	public static final List<String> ATTRIBUTES = List.of("Position", "Color", "UV0", "UV2");

	private CrumblingVertex() {
	}

	
	public static List<String> prologue(Set<String> used, Map<String, String> synthesized) {
		List<String> lines = new ArrayList<>();

		for (String attribute : ATTRIBUTES) {
			lines.add("in " + VertexPrologue.elementType(attribute) + " " + attribute + ";");
		}

		lines.add("#define of_Vertex vec4(Position, 1.0)");
		lines.add("#define of_Color Color");
		lines.add("#define of_MultiTexCoord0 vec4(UV0, 0.0, 1.0)");
		// Both units that name the light map, answered at full light like every other piece the
		// game draws at the camera: CoreVK keys this draw FULLBRIGHT (ShaderKey.java:61), and no
		// other side of the stage reaches this head, the map filing no twin for the overlay.
		lines.add("#define of_MultiTexCoord1 " + EntityVertex.FULL_LIGHT);
		lines.add("#define of_MultiTexCoord2 " + EntityVertex.FULL_LIGHT);
		lines.addAll(VertexPrologue.blankTexCoords());

		lines.add("#define of_Normal vec3(0.0, 0.0, 1.0)");

		// mc_Entity among them, and it is a constant under CoreVK too on this draw, at -1 rather than at
		// the nought this hands back. A pack branching on it here is branching on a constant either
		// way.
		lines.addAll(VertexPrologue.tail(used, synthesized));

		return List.copyOf(lines);
	}
}
