package net.corevk.corevk.uniform;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector2f;
import org.joml.Vector4f;

public final class ClipSpace {

	
	public static final Vector4f REVERSED = new Vector4f(-0.5F, 0.5F, -1.0F, 1.0F);

	
	public static final Vector4f FORWARD = new Vector4f(0.5F, 0.5F, 1.0F, 0.0F);

	private ClipSpace() {
	}

	
	public static Matrix4f toLegacyDepth(Matrix4fc rendered, Matrix4f dest) {
		float m02 = rendered.m03() - 2.0F * rendered.m02();
		float m12 = rendered.m13() - 2.0F * rendered.m12();
		float m22 = rendered.m23() - 2.0F * rendered.m22();
		float m32 = rendered.m33() - 2.0F * rendered.m32();

		// Read out of the source before anything is written, so that converting a matrix onto
		// itself is allowed.
		dest.set(rendered);
		dest.m02(m02);
		dest.m12(m12);
		dest.m22(m22);
		dest.m32(m32);

		return dest;
	}

	
	public static boolean distantDepth(Matrix4fc rendered, float scale, float offset,
			Vector2f dest) {
		float worldOffset = rendered.m32();
		if (offset == 0.0F || worldOffset == 0.0F || rendered.m02() != 0.0F
				|| rendered.m12() != 0.0F || rendered.m23() != -1.0F || rendered.m33() != 0.0F) {
			return false;
		}

		float multiply = offset / worldOffset;
		dest.set(multiply, rendered.m22() * multiply - scale);

		return true;
	}
}
