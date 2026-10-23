package net.corevk.corevk.uniform.values;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;

final class Settled {

	private final Matrix4f first = new Matrix4f();
	private final Matrix4f second = new Matrix4f();

	private float a;
	private float b;

	
	private boolean valid;

	
	boolean holds(Matrix4fc matrix) {
		if (this.valid && same(this.first, matrix)) {
			return true;
		}

		this.first.set(matrix);
		this.valid = true;

		return false;
	}

	
	boolean holds(Matrix4fc matrix, float a, float b) {
		if (this.valid && this.a == a && this.b == b && same(this.first, matrix)) {
			return true;
		}

		this.first.set(matrix);
		this.a = a;
		this.b = b;
		this.valid = true;

		return false;
	}

	
	boolean holds(Matrix4fc first, Matrix4fc second) {
		if (this.valid && same(this.first, first) && same(this.second, second)) {
			return true;
		}

		this.first.set(first);
		this.second.set(second);
		this.valid = true;

		return false;
	}

	
	private static boolean same(Matrix4f held, Matrix4fc matrix) {
		return held.equals(matrix, 0.0F);
	}
}
