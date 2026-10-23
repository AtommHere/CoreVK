package net.corevk.corevk.glsl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CloudVertex {

	
	public static final List<String> ATTRIBUTES = List.of("CloudInfo", "CloudFaces");

	
	private static final List<String> CORNERS = List.of(
			// Down
			"vec3(1.0, 0.0, 0.0)", "vec3(1.0, 0.0, 1.0)", "vec3(0.0, 0.0, 1.0)", "vec3(0.0, 0.0, 0.0)",
			// Up
			"vec3(0.0, 1.0, 0.0)", "vec3(0.0, 1.0, 1.0)", "vec3(1.0, 1.0, 1.0)", "vec3(1.0, 1.0, 0.0)",
			// North
			"vec3(0.0, 0.0, 0.0)", "vec3(0.0, 1.0, 0.0)", "vec3(1.0, 1.0, 0.0)", "vec3(1.0, 0.0, 0.0)",
			// South
			"vec3(1.0, 0.0, 1.0)", "vec3(1.0, 1.0, 1.0)", "vec3(0.0, 1.0, 1.0)", "vec3(0.0, 0.0, 1.0)",
			// West
			"vec3(0.0, 0.0, 1.0)", "vec3(0.0, 1.0, 1.0)", "vec3(0.0, 1.0, 0.0)", "vec3(0.0, 0.0, 0.0)",
			// East
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

		// The members are ours to name and the block is not. std140 matches by offset, so what has
		// to hold is the order and the padding: a vec4 then two vec3, which is what
		// CloudRenderer.render fills through Std140Builder in exactly that order.
		lines.add("layout(std140) uniform CloudInfo {");
		lines.add("\tvec4 of_CloudColour;");
		lines.add("\tvec3 of_CloudOffset;");
		lines.add("\tvec3 of_CellSize;");
		lines.add("};");
		lines.add("uniform isamplerBuffer CloudFaces;");

		lines.add("const vec3 of_cloudCorners[24] = vec3[24](" + String.join(", ", CORNERS) + ");");
		lines.add("const vec3 of_cloudNormals[6] = vec3[6](" + String.join(", ", NORMALS) + ");");
		lines.add("const vec4 of_cloudShades[6] = vec4[6](" + String.join(", ", SHADES) + ");");

		// One word per face rather than one read per name: the three below all want it, and a
		// compiler that fails to fold three identical fetches into one still reads the same value.
		//
		// The buffer is signed bytes and the arithmetic below depends on it. The engine packs the low
		// bit of each cell into bits seven and six of this word, so bit seven is the sign, and the
		// word arrives negative wherever the cell is odd in x. Masking first is what makes that
		// harmless: `word & 128` is 128 whatever the sign extension put above it.
		lines.add("int of_cloudWord() { return texelFetch(CloudFaces, (gl_VertexID / 4) * 3 + 2).r; }");
		lines.add("int of_cloudFacing() { return of_cloudWord() & 7; }");

		lines.add("vec3 of_cloudPosition() {");
		lines.add("\tint face = (gl_VertexID / 4) * 3;");
		lines.add("\tint word = of_cloudWord();");
		lines.add("\tint cellX = (texelFetch(CloudFaces, face).r << 1) | ((word & 128) >> 7);");
		lines.add("\tint cellZ = (texelFetch(CloudFaces, face + 1).r << 1) | ((word & 64) >> 6);");
		// A face the camera stands inside is wound the other way round, which is the whole of what
		// bit four says. Read as anything else it is a face culled exactly where it has to be drawn.
		lines.add("\tint corner = gl_VertexID % 4;");
		lines.add("\tvec3 at = of_cloudCorners[(word & 7) * 4 + ((word & 16) != 0 ? 3 - corner : corner)];");
		lines.add("\treturn (at * of_CellSize) + (vec3(cellX, 0.0, cellZ) * of_CellSize) + of_CloudOffset;");
		lines.add("}");

		lines.add("#define of_Vertex vec4(of_cloudPosition(), 1.0)");
		// Bit five says this face takes the top shade whatever way it points, which is how a flat
		// cloud is drawn: one downward face a cell, lit as though it were the top.
		lines.add("#define of_Color (((of_cloudWord() & 32) != 0 ? of_cloudShades[1] "
				+ ": of_cloudShades[of_cloudFacing()]) * of_CloudColour)");
		lines.add("#define of_Normal of_cloudNormals[of_cloudFacing()]");

		// The middle of the sprite, since nothing textures a cloud. The corner instead would send a
		// pack that samples gtexture to one texel of whatever is bound rather than to the one the
		// name stands for, and three packs of the corpus do sample it here.
		lines.add("#define of_MultiTexCoord0 vec4(0.5, 0.5, 0.0, 1.0)");

		// The light map, which the cloud buffer has not got. Both channels at the top of the range,
		// in the raw coordinates a pack divides down itself, exactly as the sky answers it.
		for (int unit = 1; unit <= 2; unit++) {
			lines.add("#define of_MultiTexCoord" + unit + " vec4(240.0, 240.0, 0.0, 1.0)");
		}

		lines.addAll(VertexPrologue.blankTexCoords());
		lines.addAll(VertexPrologue.tail(used, synthesized));

		return List.copyOf(lines);
	}
}
