package net.corevk.corevk.uniform;

import org.joml.Matrix3fc;
import org.joml.Matrix4fc;

public interface UniformSink {

	
	default UniformSink member(String name, int elements, boolean supplied) {
		return this;
	}

	UniformSink align(int alignment);

	UniformSink putFloat(float v);

	UniformSink putInt(int v);

	UniformSink putVec2(float x, float y);

	UniformSink putVec3(float x, float y, float z);

	UniformSink putVec4(float x, float y, float z, float w);

	UniformSink putIVec2(int x, int y);

	UniformSink putIVec3(int x, int y, int z);

	UniformSink putIVec4(int x, int y, int z, int w);

	UniformSink putMat3(Matrix3fc m);

	UniformSink putMat4(Matrix4fc m);
}
