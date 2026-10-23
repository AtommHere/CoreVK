package net.corevk.corevk.glsl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GlyphVertex {

	
	public static final List<String> WORLD = List.of("Position", "UV0", "UV2", "Color");

	
	public static final List<String> SEE_THROUGH = List.of("Position", "UV0", "Color");

	
	public static final List<String> BACKGROUND = List.of("Position", "Color", "UV2");

	
	public static final List<String> BACKGROUND_SEE_THROUGH = List.of("Position", "Color");

	
	public static final List<String> ATTRIBUTES = WORLD;

	private GlyphVertex() {
	}

	
	public static List<String> prologue(List<String> bound, Set<String> used,
			Map<String, String> synthesized) {
		List<String> lines = new ArrayList<>();

		for (String attribute : bound) {
			lines.add("in " + VertexPrologue.elementType(attribute) + " " + attribute + ";");
		}

		lines.add("#define of_Vertex vec4(Position, 1.0)");
		lines.add("#define of_Color Color");

		// The middle of a texel where the format has no coordinate, which is the two background
		// quads: nothing is bound behind gtexture for them, the game's own pipeline declaring no
		// Sampler0, so what a pack samples there is the one white texel GeometryProgram falls back
		// on and the corner would read the same value. LinesVertex answers its own mesh the same way.
		lines.add("#define of_MultiTexCoord0 " + (bound.contains("UV0")
				? "vec4(UV0, 0.0, 1.0)"
				: "vec4(0.5, 0.5, 0.0, 1.0)"));

		// Full light where 26.2 dropped the element, which is all three see-through pipelines. Unit
		// two is a second name for the light map and not a unit of its own, which is what CoreVK makes
		// of it as well (transform/transformer/VanillaTransformer.java:77 renaming one into the other).
		String light = bound.contains("UV2") ? "vec4(UV2, 0.0, 1.0)" : EntityVertex.FULL_LIGHT;
		lines.add("#define of_MultiTexCoord1 " + light);
		lines.add("#define of_MultiTexCoord2 " + light);
		lines.addAll(VertexPrologue.blankTexCoords());

		// The constant every mesh of the game without a normal gets here, and CoreVK's own answer for
		// such a mesh (transform/transformer/VanillaTransformer.java:152-154). A glyph really has a
		// facing and this head cannot know it: the class comment says what that costs.
		lines.add("#define of_Normal vec3(0.0, 0.0, 1.0)");

		lines.addAll(VertexPrologue.tail(used, synthesized));

		return List.copyOf(lines);
	}
}
