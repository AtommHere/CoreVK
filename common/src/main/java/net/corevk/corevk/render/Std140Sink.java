package net.corevk.corevk.render;

import net.corevk.corevk.uniform.UniformSink;

import com.mojang.blaze3d.buffers.Std140Builder;
import org.joml.Matrix3fc;
import org.joml.Matrix4fc;

final class Std140Sink implements UniformSink {

	private final Std140Builder builder;

	Std140Sink(Std140Builder builder) {
		this.builder = builder;
	}

	@Override
	public UniformSink align(int alignment) {
		this.builder.align(alignment);

		return this;
	}

	@Override
	public UniformSink putFloat(float v) {
		this.builder.putFloat(v);

		return this;
	}

	@Override
	public UniformSink putInt(int v) {
		this.builder.putInt(v);

		return this;
	}

	@Override
	public UniformSink putVec2(float x, float y) {
		this.builder.putVec2(x, y);

		return this;
	}

	
	@Override
	public UniformSink putVec3(float x, float y, float z) {
		this.builder.align(16);
		this.builder.putFloat(x).putFloat(y).putFloat(z);

		return this;
	}

	@Override
	public UniformSink putVec4(float x, float y, float z, float w) {
		this.builder.putVec4(x, y, z, w);

		return this;
	}

	@Override
	public UniformSink putIVec2(int x, int y) {
		this.builder.putIVec2(x, y);

		return this;
	}

	
	@Override
	public UniformSink putIVec3(int x, int y, int z) {
		this.builder.align(16);
		this.builder.putInt(x).putInt(y).putInt(z);

		return this;
	}

	@Override
	public UniformSink putIVec4(int x, int y, int z, int w) {
		this.builder.putIVec4(x, y, z, w);

		return this;
	}

	
	@Override
	public UniformSink putMat3(Matrix3fc m) {
		this.builder.putVec4(m.m00(), m.m01(), m.m02(), 0.0F)
				.putVec4(m.m10(), m.m11(), m.m12(), 0.0F)
				.putVec4(m.m20(), m.m21(), m.m22(), 0.0F);

		return this;
	}

	@Override
	public UniformSink putMat4(Matrix4fc m) {
		this.builder.putMat4f(m);

		return this;
	}
}
