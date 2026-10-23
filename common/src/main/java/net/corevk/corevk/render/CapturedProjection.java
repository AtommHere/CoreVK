package net.corevk.corevk.render;

import net.corevk.corevk.CoreVK;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;

public final class CapturedProjection {

	private static final Matrix4f CAPTURED = new Matrix4f();

	private static boolean captured;
	private static boolean warned;

	private CapturedProjection() {
	}

	public static void capture(Matrix4fc rendered) {

		if (HandDraw.drawing() || rendered.m00() == 0.0F || !perspective(rendered)) {
			return;
		}

		CAPTURED.set(rendered);
		captured = true;
	}

	public static Matrix4fc rendered(Matrix4fc fallback) {
		if (captured) {
			return CAPTURED;
		}

		if (!warned) {
			warned = true;
			CoreVK.logger().warn("Nothing captured the level's projection, so the walk bob, the "
					+ "damage tilt, the nausea rotation and the portal are missing from every "
					+ "matrix a pack reads. Anything rebuilding a world position from depth will "
					+ "drift while the player moves");
		}

		return fallback;
	}

	private static boolean perspective(Matrix4fc matrix) {
		return matrix.m03() * matrix.m03() + matrix.m13() * matrix.m13()
				+ matrix.m23() * matrix.m23() > 0.25F;
	}

	public static void clear() {
		captured = false;
	}
}
