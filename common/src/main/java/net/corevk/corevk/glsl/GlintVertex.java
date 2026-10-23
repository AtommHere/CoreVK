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
		// Unit two is a second name for the light map and not a unit of its own, exactly as it is on
		// the entity mesh: CoreVK renames one into the other before it substitutes anything,
		// VanillaTransformer.java:77.
		lines.add("#define of_MultiTexCoord2 " + FULL_BRIGHT);
		lines.addAll(VertexPrologue.blankTexCoords());

		// Towards the viewer, which is CoreVK's answer for a mesh with no normal in it
		// (VanillaTransformer.java:154) and not VertexPrologue's own axis: that one is for a name the
		// pack declared for itself, where nobody knows what it meant.
		lines.add("#define of_Normal vec3(0.0, 0.0, 1.0)");

		lines.addAll(VertexPrologue.tail(used, synthesized));

		return List.copyOf(lines);
	}
}
