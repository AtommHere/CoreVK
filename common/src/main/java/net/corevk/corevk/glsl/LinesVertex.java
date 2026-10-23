package net.corevk.corevk.glsl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class LinesVertex {

	
	public static final List<String> ATTRIBUTES = List.of("Position", "Color", "Normal", "LineWidth");

	
	public static final Set<String> ANSWERED = Set.of("vaPosition", "vaNormal", "vaColor");

	
	public static final String OFFSET = "of_LineOffset";

	
	public static final String WIDEN = "of_WidenLine";

	private LinesVertex() {
	}

	
	public static List<String> prologue(Set<String> used, Map<String, String> synthesized) {
		List<String> lines = new ArrayList<>();

		for (String attribute : ATTRIBUTES) {
			lines.add("in " + VertexPrologue.elementType(attribute) + " " + attribute + ";");
		}

		lines.add("vec3 " + OFFSET + " = vec3(0.0);");
		lines.add("#define of_Vertex vec4(Position + " + OFFSET + ", 1.0)");
		lines.add("#define of_Color Color");
		lines.add("#define of_MultiTexCoord0 vec4(0.5, 0.5, 0.0, 1.0)");
		lines.add("#define of_MultiTexCoord1 " + EntityVertex.FULL_LIGHT);
		lines.add("#define of_MultiTexCoord2 " + EntityVertex.FULL_LIGHT);
		lines.addAll(VertexPrologue.blankTexCoords());

		// The element is the edge's direction and not a surface normal, so what a pack takes for
		// the normal is the constant every mesh without one gets, which is CoreVK's own answer for
		// this mesh (VanillaTransformer.java:146-147).
		lines.add("#define of_Normal vec3(0.0, 0.0, 1.0)");

		VertexPrologue.globals(used, synthesized, Map.of()).forEach((name, type) -> lines.add(
				ANSWERED.contains(name)
						? "#define " + name + " " + answer(name)
						: VertexPrologue.declaration(name, type)));

		return List.copyOf(lines);
	}

	
	public static List<String> widen() {
		return List.of(
				"void " + WIDEN + "(inout vec4 lineStart, vec4 lineEnd) {",
				"    vec2 screenSize = vec2(viewWidth, viewHeight);",
				"    vec3 ndc1 = lineStart.xyz / lineStart.w;",
				"    vec3 ndc2 = lineEnd.xyz / lineEnd.w;",
				"    vec2 along = (ndc2.xy - ndc1.xy) * screenSize;",
				"    if (dot(along, along) < 1.0e-12) {",
				"        return;",
				"    }",
				"    vec2 lineScreenDirection = normalize(along);",
				"    vec2 lineOffset = vec2(-lineScreenDirection.y, lineScreenDirection.x) * LineWidth"
						+ " / screenSize;",
				"    if (lineOffset.x < 0.0) {",
				"        lineOffset *= -1.0;",
				"    }",
				"    if (gl_VertexIndex % 2 == 0) {",
				"        lineStart = vec4((ndc1 + vec3(lineOffset, 0.0)) * lineStart.w, lineStart.w);",
				"    } else {",
				"        lineStart = vec4((ndc1 - vec3(lineOffset, 0.0)) * lineStart.w, lineStart.w);",
				"    }",
				"}");
	}

	private static String answer(String name) {
		return switch (name) {
			case "vaPosition" -> "Position";
			case "vaNormal" -> "Normal.xyz";
			default -> "Color";
		};
	}
}
