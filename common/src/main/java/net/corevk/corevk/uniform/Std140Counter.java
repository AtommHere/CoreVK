package net.corevk.corevk.uniform;

import org.joml.Matrix3fc;
import org.joml.Matrix4fc;

public final class Std140Counter implements UniformSink {

	private int size;

	public int size() {
		return this.size;
	}

	@Override
	public UniformSink align(int alignment) {
		int remainder = this.size % alignment;
		if (remainder != 0) {
			this.size += alignment - remainder;
		}

		return this;
	}

	@Override
	public UniformSink putFloat(float v) {
		return advance(4, 4);
	}

	@Override
	public UniformSink putInt(int v) {
		return advance(4, 4);
	}

	@Override
	public UniformSink putVec2(float x, float y) {
		return advance(8, 8);
	}

	
	@Override
	public UniformSink putVec3(float x, float y, float z) {
		return advance(16, 12);
	}

	@Override
	public UniformSink putVec4(float x, float y, float z, float w) {
		return advance(16, 16);
	}

	@Override
	public UniformSink putIVec2(int x, int y) {
		return advance(8, 8);
	}

	
	@Override
	public UniformSink putIVec3(int x, int y, int z) {
		return advance(16, 12);
	}

	@Override
	public UniformSink putIVec4(int x, int y, int z, int w) {
		return advance(16, 16);
	}

	@Override
	public UniformSink putMat3(Matrix3fc m) {
		return advance(16, 16).advance(16, 16).advance(16, 16);
	}

	@Override
	public UniformSink putMat4(Matrix4fc m) {
		return advance(16, 64);
	}

	private Std140Counter advance(int alignment, int bytes) {
		align(alignment);
		this.size += bytes;

		return this;
	}
}
