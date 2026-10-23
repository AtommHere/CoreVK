package net.corevk.corevk.uniform.values;

import net.corevk.corevk.uniform.UniformCatalog;
import net.corevk.corevk.uniform.UniformShape;

public final class MatrixValues {

	private MatrixValues() {
	}

	public static void register(UniformCatalog.Builder builder) {
		builder.add("gbufferModelView", UniformShape.MAT4,
				(world, out) -> out.set(world.gbufferModelView()));
		builder.add("gbufferModelViewInverse", UniformShape.MAT4,
				(world, out) -> out.set(world.gbufferModelViewInverse()));
		builder.add("gbufferPreviousModelView", UniformShape.MAT4,
				(world, out) -> out.set(world.gbufferPreviousModelView()));

		builder.add("gbufferProjection", UniformShape.MAT4,
				(world, out) -> out.set(world.gbufferProjection()));
		builder.add("gbufferProjectionInverse", UniformShape.MAT4,
				(world, out) -> out.set(world.gbufferProjectionInverse()));
		builder.add("gbufferPreviousProjection", UniformShape.MAT4,
				(world, out) -> out.set(world.gbufferPreviousProjection()));
	}
}
