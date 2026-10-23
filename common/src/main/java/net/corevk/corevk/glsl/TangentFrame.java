package net.corevk.corevk.glsl;

import java.util.ArrayList;
import java.util.List;

public final class TangentFrame {

	
	public static final String NORMAL_OF = "ofFrameNormal";

	
	public static final String TANGENT_OF = "ofFrameTangent";

	
	private static final int X_MAX = 2047;

	
	private static final int Y_MAX = 1023;

	
	private static final float FLAT = 1.0E-20F;

	
	private static final ThreadLocal<float[]> DECODED = ThreadLocal.withInitial(() -> new float[3]);

	private TangentFrame() {
	}

	
	public static int pack(float[] frame) {
		int normal = packNormal(frame[0], frame[1], frame[2]);
		float[] decoded = normal(normal, DECODED.get());

		return normal | (frame[6] >= 0.0F ? 1 << 23 : 0)
				| (packTangent(decoded, frame[3], frame[4], frame[5]) << 24);
	}

	
	public static float[] normal(int word) {
		return normal(word, new float[3]);
	}

	
	public static float[] normal(int word, float[] into) {
		float x = (word << 20 >> 20) / (float) X_MAX;
		float y = (word << 9 >> 21) / (float) Y_MAX;
		float z = 1.0F - Math.abs(x) - Math.abs(y);
		if (z >= 0.0F) {
			into[0] = x;
			into[1] = y;
		} else {
			into[0] = (1.0F - Math.abs(y)) * sideOf(x);
			into[1] = (1.0F - Math.abs(x)) * sideOf(y);
		}

		into[2] = z;
		normalise(into);

		return into;
	}

	
	public static float[] tangent(float[] normal, int word) {
		float side = sideOf(normal[2]);
		float scale = -1.0F / (side + normal[2]);
		float across = normal[0] * normal[1] * scale;
		float[] first = {1.0F + side * normal[0] * normal[0] * scale, side * across,
				-side * normal[0]};
		float[] second = {across, side + normal[1] * normal[1] * scale, -normal[1]};

		float turn = (word >>> 24) * (1.0F / 256.0F);
		float along = 1.0F - 4.0F * Math.abs(turn - 0.5F);
		float away = (turn >= 0.5F ? 1.0F : -1.0F) * (1.0F - Math.abs(along));

		float[] tangent = {along * first[0] + away * second[0], along * first[1] + away * second[1],
				along * first[2] + away * second[2], (word >> 23 & 1) != 0 ? 1.0F : -1.0F};
		normalise(tangent);

		return tangent;
	}

	
	public static List<String> decode(boolean tangent) {
		List<String> lines = new ArrayList<>();

		// Twelve bits of octahedral x and eleven of y, each read back as a signed integer and
		// divided by what its own width can hold.
		lines.add("vec3 " + NORMAL_OF + "(uint word) {");
		lines.add("\tvec2 folded = vec2(bitfieldExtract(int(word), 0, 12),"
				+ " bitfieldExtract(int(word), 12, 11)) * vec2(1.0 / " + X_MAX + ".0, 1.0 / "
				+ Y_MAX + ".0);");
		lines.add("\tfloat up = 1.0 - abs(folded.x) - abs(folded.y);");
		lines.add("\tvec2 sides = vec2(folded.x >= 0.0 ? 1.0 : -1.0, folded.y >= 0.0 ? 1.0 : -1.0);");
		// The lower half of the octahedron is reflected over the diagonals, so it is unreflected
		// here. Leaving it out answers the mirror image of every downward normal.
		lines.add("\treturn normalize(vec3(up >= 0.0 ? folded"
				+ " : (1.0 - abs(folded.yx)) * sides, up));");
		lines.add("}");

		if (!tangent) {
			return List.copyOf(lines);
		}

		lines.add("vec4 " + TANGENT_OF + "(vec3 normal, uint word) {");
		// Frisvad: an orthonormal pair for the normal that has no branch on which axis it is nearest
		// and no singularity at either pole, which is what makes the angle below mean one direction.
		lines.add("\tfloat side = normal.z >= 0.0 ? 1.0 : -1.0;");
		lines.add("\tfloat scale = -1.0 / (side + normal.z);");
		lines.add("\tfloat across = normal.x * normal.y * scale;");
		lines.add("\tvec3 first = vec3(1.0 + side * normal.x * normal.x * scale,"
				+ " side * across, -side * normal.x);");
		lines.add("\tvec3 second = vec3(across, side + normal.y * normal.y * scale, -normal.y);");
		// The diamond: an angle round the unit square rather than round the circle, so that the
		// eight bits are spent evenly on a direction without a trigonometric function either side.
		lines.add("\tfloat turn = float(word >> 24u) * (1.0 / 256.0);");
		lines.add("\tfloat along = 1.0 - 4.0 * abs(turn - 0.5);");
		lines.add("\tfloat away = (turn >= 0.5 ? 1.0 : -1.0) * (1.0 - abs(along));");
		lines.add("\treturn vec4(normalize(along * first + away * second),"
				+ " ((word >> 23u) & 1u) != 0u ? 1.0 : -1.0);");
		lines.add("}");

		return List.copyOf(lines);
	}

	
	private static int packNormal(float x, float y, float z) {
		float onto = 1.0F / (Math.abs(x) + Math.abs(y) + Math.abs(z));
		float px = x * onto;
		float py = y * onto;
		float ox = z > 0.0F ? px : (1.0F - Math.abs(py)) * sideOf(px);
		float oy = z > 0.0F ? py : (1.0F - Math.abs(px)) * sideOf(py);

		return (snorm(ox, X_MAX) & 0xFFF) | ((snorm(oy, Y_MAX) & 0x7FF) << 12);
	}

	
	private static int packTangent(float[] normal, float x, float y, float z) {
		float side = sideOf(normal[2]);
		float scale = -1.0F / (side + normal[2]);
		float across = normal[0] * normal[1] * scale;
		float along = x * (1.0F + side * normal[0] * normal[0] * scale) + y * side * across
				+ z * -side * normal[0];
		float away = x * across + y * (side + normal[1] * normal[1] * scale) + z * -normal[1];

		float span = Math.abs(along) + Math.abs(away);
		if (span <= FLAT) {
			return 128;
		}

		float turn = away >= 0.0F
				? 0.25F * (1.0F - along / span) + 0.5F
				: 0.25F * (along / span) + 0.25F;

		return Math.round(turn * 256.0F) & 0xFF;
	}

	
	private static int snorm(float value, int max) {
		return Math.round(Math.clamp(value, -1.0F, 1.0F) * max);
	}

	
	private static float sideOf(float value) {
		return value >= 0.0F ? 1.0F : -1.0F;
	}

	
	private static void normalise(float[] vector) {
		float length = (float) Math.sqrt(
				vector[0] * vector[0] + vector[1] * vector[1] + vector[2] * vector[2]);
		if (length > 0.0F) {
			vector[0] /= length;
			vector[1] /= length;
			vector[2] /= length;
		}
	}
}
