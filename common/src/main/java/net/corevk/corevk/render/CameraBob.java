package net.corevk.corevk.render;

import net.corevk.corevk.CoreVK;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;

public final class CameraBob {

	private static final Matrix4f IDENTITY = new Matrix4f();

	private static final Matrix4f TAKEN = new Matrix4f();
	private static final Matrix4f CHECK = new Matrix4f();

	
	private static final Matrix4f POSE = new Matrix4f();

	private static boolean taken;
	private static boolean trusted = true;

	
	private static boolean warnedNotTaken;
	private static boolean warnedMismatch;

	private static boolean announced;

	private CameraBob() {
	}

	
	public static void take(Matrix4fc bob) {
		TAKEN.set(bob);
		POSE.set(bob);
		taken = true;
	}

	
	static Matrix4fc pose() {
		return POSE;
	}

	
	public static void rotate(float angle, Vector3fc axis) {
		TAKEN.rotate(angle, axis);
	}

	
	public static void scale(float x, float y, float z) {
		TAKEN.scale(x, y, z);
	}

	
	static Matrix4fc taken() {
		return taken && trusted ? TAKEN : IDENTITY;
	}

	
	static boolean agrees(Matrix4fc camera, Matrix4fc rendered) {
		// Asked before anything else, because the answer below is per frame and the refusal is not.
		// One product that did not match takes taken() down to the identity for good, so a later
		// frame that happened to agree would publish the clean projection against a model view with
		// no bob in it: the four terms would then be in neither of the two matrices the pack is
		// handed, which is worse than the frame that failed.
		if (!trusted) {
			return false;
		}

		if (!taken) {
			// Said once, because the quiet answer and the failed one look alike from here: nothing
			// took the bob either when the game stopped multiplying it in or when this engine
			// stopped being able to see it, and both leave a pack reading a projection that swings.
			if (!warnedNotTaken) {
				warnedNotTaken = true;
				CoreVK.logger().warn("Nothing took the walk bob out of the projection this frame, so "
						+ "a pack reads it where OptiFine never put it and anything it places on "
						+ "screen from a direction will slide as the player walks");
			}

			return false;
		}

		CHECK.set(camera).mul(TAKEN);
		// Loose enough for one matrix product of single precision, tight enough that a whole missing
		// term cannot pass: the smallest of the four, the damage tilt at rest, still moves a
		// coefficient by more than a thousandth.
		if (!CHECK.equals(rendered, 1.0E-4F)) {
			trusted = false;
			if (!warnedMismatch) {
				warnedMismatch = true;
				CoreVK.logger().warn("The camera's projection times the bob is not the projection the "
						+ "level was drawn with, so this engine is missing a term the game applies. "
						+ "The bob stays in the projection, where packs do not expect it, rather than "
						+ "publishing two matrices that do not multiply back to the truth");
			}

			return false;
		}

		if (!announced) {
			announced = true;
			CoreVK.logger().info("The walk bob, the damage tilt, the nausea and the portal are "
					+ "published in gbufferModelView and taken out of gbufferProjection, where a pack "
					+ "expects them. The two multiply back to the matrix the level was drawn with");
		}

		return true;
	}

	
	public static void clear() {
		taken = false;
	}
}
