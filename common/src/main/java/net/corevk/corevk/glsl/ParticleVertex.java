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
		// The light map, on both units that name it. It is UV2 here as it is on the entity mesh, and
		// there is no UV1 at all to be confused with it: the overlay is what an entity has and a
		// particle has not.
		lines.add("#define of_MultiTexCoord1 vec4(UV2, 0.0, 1.0)");
		lines.add("#define of_MultiTexCoord2 vec4(UV2, 0.0, 1.0)");
		lines.addAll(VertexPrologue.blankTexCoords());

		lines.add("#define of_Normal vec3(0.0, 0.0, 1.0)");

		// mc_Entity among them, and a pack branching on it here is branching on a constant. That is
		// what the mesh says rather than a gap in this head: a particle has no block state and no
		// entity to travel on, and CoreVK answers it the same way by declaring the entity uniforms off
		// the fallback root, which neither of this family's two names is.
		lines.addAll(VertexPrologue.tail(used, synthesized));

		return List.copyOf(lines);
	}
}
