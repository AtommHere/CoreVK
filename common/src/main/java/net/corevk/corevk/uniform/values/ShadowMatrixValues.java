package net.corevk.corevk.uniform.values;

import net.corevk.corevk.uniform.UniformCatalog;
import net.corevk.corevk.uniform.UniformShape;

public final class ShadowMatrixValues {

	private ShadowMatrixValues() {
	}

	public static void register(UniformCatalog.Builder builder) {
		builder.add("shadowModelView", UniformShape.MAT4,
				(world, out) -> out.set(world.shadowModelView()));
		builder.add("shadowModelViewInverse", UniformShape.MAT4,
				(world, out) -> out.set(world.shadowModelViewInverse()));
		builder.add("shadowProjection", UniformShape.MAT4,
				(world, out) -> out.set(world.shadowProjection()));
		builder.add("shadowProjectionInverse", UniformShape.MAT4,
				(world, out) -> out.set(world.shadowProjectionInverse()));
	}
}
