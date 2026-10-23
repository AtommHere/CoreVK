package net.corevk.corevk.render;

public final class EntityFrame {

	
	public static final int FLAT = pack(1.0F, 0.0F, 0.0F, 1.0F);

	
	private static final float RANGE = 127.0F;

	
	private static final float TINY = 1.0E-12F;

	private EntityFrame() {
	}

	
	public static boolean faceNormal(float[] into, float x0, float y0, float z0, float x1, float y1,
			float z1, float x2, float y2, float z2, float x3, float y3, float z3) {
		float ax = x2 - x0;
		float ay = y2 - y0;
		float az = z2 - z0;
		float bx = x3 - x1;
		float by = y3 - y1;
		float bz = z3 - z1;

		float nx = ay * bz - az * by;
		float ny = az * bx - ax * bz;
		float nz = ax * by - ay * bx;
		float square = nx * nx + ny * ny + nz * nz;
		if (square <= TINY) {
			return false;
		}

		float scale = (float) (1.0 / Math.sqrt(square));
		into[0] = nx * scale;
		into[1] = ny * scale;
		into[2] = nz * scale;

		return true;
	}

	
	public static int tangent(float normalX, float normalY, float normalZ, boolean flatten,
			float x0, float y0, float z0, float u0, float v0,
			float x1, float y1, float z1, float u1, float v1,
			float x2, float y2, float z2, float u2, float v2) {
		float px0 = x0;
		float py0 = y0;
		float pz0 = z0;
		float px1 = x1;
		float py1 = y1;
		float pz1 = z1;
		float px2 = x2;
		float py2 = y2;
		float pz2 = z2;
		if (flatten) {
			float onto0 = x0 * normalX + y0 * normalY + z0 * normalZ;
			float onto1 = x1 * normalX + y1 * normalY + z1 * normalZ;
			float onto2 = x2 * normalX + y2 * normalY + z2 * normalZ;
			px0 -= onto0 * normalX;
			py0 -= onto0 * normalY;
			pz0 -= onto0 * normalZ;
			px1 -= onto1 * normalX;
			py1 -= onto1 * normalY;
			pz1 -= onto1 * normalZ;
			px2 -= onto2 * normalX;
			py2 -= onto2 * normalY;
			pz2 -= onto2 * normalZ;
		}

		float edge1x = px1 - px0;
		float edge1y = py1 - py0;
		float edge1z = pz1 - pz0;
		float edge2x = px2 - px0;
		float edge2y = py2 - py0;
		float edge2z = pz2 - pz0;

		float du1 = u1 - u0;
		float dv1 = v1 - v0;
		float du2 = u2 - u0;
		float dv2 = v2 - v0;

		// Three corners sharing a texture coordinate leave no gradient to invert. CoreVK carries the
		// same guard and the same value for it, and what comes out of it is an edge rather than
		// anything meaningful: the point is to keep the branch out of the division.
		float span = du1 * dv2 - du2 * dv1;
		float scale = span == 0.0F ? 1.0F : 1.0F / span;

		float tx = scale * (dv2 * edge1x - dv1 * edge2x);
		float ty = scale * (dv2 * edge1y - dv1 * edge2y);
		float tz = scale * (dv2 * edge1z - dv1 * edge2z);
		float square = tx * tx + ty * ty + tz * tz;
		if (square <= TINY) {
			return FLAT;
		}

		float unit = (float) (1.0 / Math.sqrt(square));
		tx *= unit;
		ty *= unit;
		tz *= unit;

		// The bitangent the texture really runs along, against the one this frame would predict from
		// the tangent and the normal. Where the two point apart the third axis turns the other way,
		// and that is the whole of what the fourth component says: a mirrored piece of a skin lights
		// its bumps as dents without it.
		float bx = scale * (-du2 * edge1x + du1 * edge2x);
		float by = scale * (-du2 * edge1y + du1 * edge2y);
		float bz = scale * (-du2 * edge1z + du1 * edge2z);
		float side = bx * (ty * normalZ - tz * normalY) + by * (tz * normalX - tx * normalZ)
				+ bz * (tx * normalY - ty * normalX);

		return pack(tx, ty, tz, side < 0.0F ? -1.0F : 1.0F);
	}

	
	public static int pack(float x, float y, float z, float w) {
		return (snorm(x) & 0xFF) | ((snorm(y) & 0xFF) << 8) | ((snorm(z) & 0xFF) << 16)
				| ((snorm(w) & 0xFF) << 24);
	}

	
	public static float unpack(int word, int component) {
		return (byte) (word >> (component * 8)) * (1.0F / RANGE);
	}

	
	private static int snorm(float value) {
		return (int) (Math.clamp(value, -1.0F, 1.0F) * RANGE);
	}
}
