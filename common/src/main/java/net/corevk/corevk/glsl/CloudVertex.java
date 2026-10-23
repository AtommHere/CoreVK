package net.corevk.corevk.glsl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CloudVertex {

	public static final List<String> ATTRIBUTES = List.of("CloudInfo", "CloudFaces");

	private static final List<String> CORNERS = List.of(

			"vec3(1.0, 0.0, 0.0)", "vec3(1.0, 0.0, 1.0)", "vec3(0.0, 0.0, 1.0)", "vec3(0.0, 0.0, 0.0)",

			"vec3(0.0, 1.0, 0.0)", "vec3(0.0, 1.0, 1.0)", "vec3(1.0, 1.0, 1.0)", "vec3(1.0, 1.0, 0.0)",

			"vec3(0.0, 0.0, 0.0)", "vec3(0.0, 1.0, 0.0)", "vec3(1.0, 1.0, 0.0)", "vec3(1.0, 0.0, 0.0)",

			"vec3(1.0, 0.0, 1.0)", "vec3(1.0, 1.0, 1.0)", "vec3(0.0, 1.0, 1.0)", "vec3(0.0, 0.0, 1.0)",

			"vec3(0.0, 0.0, 1.0)", "vec3(0.0, 1.0, 1.0)", "vec3(0.0, 1.0, 0.0)", "vec3(0.0, 0.0, 0.0)",

			"vec3(1.0, 0.0, 0.0)", "vec3(1.0, 1.0, 0.0)", "vec3(1.0, 1.0, 1.0)", "vec3(1.0, 0.0, 1.0)");

	private static final List<String> NORMALS = List.of(
			"vec3(0.0, -1.0, 0.0)", "vec3(0.0, 1.0, 0.0)", "vec3(0.0, 0.0, -1.0)",
			"vec3(0.0, 0.0, 1.0)", "vec3(-1.0, 0.0, 0.0)", "vec3(1.0, 0.0, 0.0)");

	private static final List<String> SHADES = List.of(
			"vec4(0.7, 0.7, 0.7, 1.0)", "vec4(1.0, 1.0, 1.0, 1.0)", "vec4(0.8, 0.8, 0.8, 1.0)",
			"vec4(0.8, 0.8, 0.8, 1.0)", "vec4(0.9, 0.9, 0.9, 1.0)", "vec4(0.9, 0.9, 0.9, 1.0)");

	private CloudVertex() {
	}

	public static List<String> prologue(Set<String> used, Map<String, String> synthesized) {
		List<String> lines = new ArrayList<>();

		lines.add("layout(std140) uniform CloudInfo {");
		lines.add("\tvec4 of_CloudColour;");
		lines.add("\tvec3 of_CloudOffset;");
		lines.add("\tvec3 of_CellSize;");
		lines.add("};");
		lines.add("uniform isamplerBuffer CloudFaces;");

		lines.add("const vec3 of_cloudCorners[24] = vec3[24](" + String.join(", ", CORNERS) + ");");
		lines.add("const vec3 of_cloudNormals[6] = vec3[6](" + String.join(", ", NORMALS) + ");");
		lines.add("const vec4 of_cloudShades[6] = vec4[6](" + String.join(", ", SHADES) + ");");

		lines.add("int of_cloudWord() { return texelFetch(CloudFaces, (gl_VertexID / 4) * 3 + 2).r; }");
		lines.add("int of_cloudFacing() { return of_cloudWord() & 7; }");

		lines.add("vec3 of_cloudPosition() {");
		lines.add("\tint face = (gl_VertexID / 4) * 3;");
		lines.add("\tint word = of_cloudWord();");
		lines.add("\tint cellX = (texelFetch(CloudFaces, face).r << 1) | ((word & 128) >> 7);");
		lines.add("\tint cellZ = (texelFetch(CloudFaces, face + 1).r << 1) | ((word & 64) >> 6);");

		lines.add("\tint corner = gl_VertexID % 4;");
		lines.add("\tvec3 at = of_cloudCorners[(word & 7) * 4 + ((word & 16) != 0 ? 3 - corner : corner)];");
		lines.add("\treturn (at * of_CellSize) + (vec3(cellX, 0.0, cellZ) * of_CellSize) + of_CloudOffset;");
		lines.add("}");

		lines.add("#define of_Vertex vec4(of_cloudPosition(), 1.0)");

		lines.add("#define of_Color (((of_cloudWord() & 32) != 0 ? of_cloudShades[1] "
				+ ": of_cloudShades[of_cloudFacing()]) * of_CloudColour)");
		lines.add("#define of_Normal of_cloudNormals[of_cloudFacing()]");

		lines.add("#define of_MultiTexCoord0 vec4(0.5, 0.5, 0.0, 1.0)");

		for (int unit = 1; unit <= 2; unit++) {
			lines.add("#define of_MultiTexCoord" + unit + " vec4(240.0, 240.0, 0.0, 1.0)");
		}

		lines.addAll(VertexPrologue.blankTexCoords());
		lines.addAll(VertexPrologue.tail(used, synthesized));

		return List.copyOf(lines);
	}
}
