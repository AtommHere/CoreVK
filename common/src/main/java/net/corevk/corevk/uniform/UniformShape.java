package net.corevk.corevk.uniform;

public enum UniformShape {

	FLOAT(1),
	INT(1),
	VEC2(2),
	VEC3(3),
	VEC4(4),
	IVEC2(2),
	IVEC3(3),
	IVEC4(4),
	MAT3(9),
	MAT4(16),
	FOG(8);

	
	private static final Val ZERO = new Val();

	private final int rank;

	UniformShape(int rank) {
		this.rank = rank;
	}

	
	public static UniformShape of(String glslType) {
		return switch (glslType) {
			case "float" -> FLOAT;
			case "int", "uint", "bool" -> INT;
			case "vec2" -> VEC2;
			case "vec3" -> VEC3;
			case "vec4" -> VEC4;
			case "ivec2", "uvec2", "bvec2" -> IVEC2;
			case "ivec3", "uvec3", "bvec3" -> IVEC3;
			case "ivec4", "uvec4", "bvec4" -> IVEC4;
			case "mat3", "mat3x3" -> MAT3;
			case "mat4", "mat4x4" -> MAT4;
			case "OfFog" -> FOG;
			default -> null;
		};
	}

	
	public int rank() {
		return this.rank;
	}

	public void zero(UniformSink sink) {
		write(sink, ZERO);
	}

	public void write(UniformSink sink, Val value) {
		switch (this) {
			case FLOAT -> sink.putFloat(value.f(0));
			case INT -> sink.putInt(value.i(0));
			case VEC2 -> sink.putVec2(value.f(0), value.f(1));
			case VEC3 -> sink.putVec3(value.f(0), value.f(1), value.f(2));
			case VEC4 -> sink.putVec4(value.f(0), value.f(1), value.f(2), value.f(3));
			case IVEC2 -> sink.putIVec2(value.i(0), value.i(1));
			case IVEC3 -> sink.putIVec3(value.i(0), value.i(1), value.i(2));
			case IVEC4 -> sink.putIVec4(value.i(0), value.i(1), value.i(2), value.i(3));
			case MAT3 -> mat3(sink, value);
			case MAT4 -> mat4(sink, value);
			// The two alignments are not decoration. The struct is a vec4 followed by four floats,
			// so it starts on a sixteen byte boundary and ends on one, and dropping either of them
			// costs sixteen bytes of offset to every member that follows.
			case FOG -> sink.align(16)
					.putVec4(value.f(0), value.f(1), value.f(2), value.f(3))
					.putFloat(value.f(4))
					.putFloat(value.f(5))
					.putFloat(value.f(6))
					.putFloat(value.f(7))
					.align(16);
		}
	}

	
	private static void mat4(UniformSink sink, Val value) {
		if (value.rank() == MAT4.rank) {
			sink.putMat4(value.mat4());

			return;
		}

		if (value.rank() == MAT3.rank) {
			sink.putVec4(value.f(0), value.f(1), value.f(2), 0.0F)
					.putVec4(value.f(3), value.f(4), value.f(5), 0.0F)
					.putVec4(value.f(6), value.f(7), value.f(8), 0.0F)
					.putVec4(0.0F, 0.0F, 0.0F, 1.0F);

			return;
		}

		sink.putVec4(value.f(0), value.f(1), value.f(2), value.f(3))
				.putVec4(value.f(4), value.f(5), value.f(6), value.f(7))
				.putVec4(value.f(8), value.f(9), value.f(10), value.f(11))
				.putVec4(value.f(12), value.f(13), value.f(14), value.f(15));
	}

	
	private static void mat3(UniformSink sink, Val value) {
		if (value.rank() == MAT3.rank) {
			sink.putMat3(value.mat3());

			return;
		}

		if (value.rank() == MAT4.rank) {
			// The upper left of a matrix held as a four by four, written as the three by three the
			// pack declared. The alignment between the columns and after the last one is what makes
			// it a matrix rather than three loose vectors: a column's stride is sixteen while a
			// vec3 on its own consumes twelve, so without these the three would pack at nought,
			// twelve and twenty four and the member after them would start at thirty six.
			sink.putVec3(value.f(0), value.f(1), value.f(2)).align(16)
					.putVec3(value.f(4), value.f(5), value.f(6)).align(16)
					.putVec3(value.f(8), value.f(9), value.f(10)).align(16);

			return;
		}

		// A value held flat, nine components in a row. Same padding as the two branches above and
		// for the same reason: these are the columns of a matrix, whose stride is sixteen, not
		// three vectors of twelve laid end to end. This is the branch a zeroed member takes.
		sink.putVec3(value.f(0), value.f(1), value.f(2)).align(16)
				.putVec3(value.f(3), value.f(4), value.f(5)).align(16)
				.putVec3(value.f(6), value.f(7), value.f(8)).align(16);
	}
}
