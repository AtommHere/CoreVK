package net.corevk.corevk.uniform.values;

import net.corevk.corevk.uniform.UniformCatalog;
import net.corevk.corevk.uniform.UniformShape;

public final class DhValues {

	private DhValues() {
	}

	public static void register(UniformCatalog.Builder builder) {
		builder.add("dhProjection", UniformShape.MAT4, (world, out) -> out.set(world.dhProjection()));
		builder.add("dhProjectionInverse", UniformShape.MAT4,
				(world, out) -> out.set(world.dhProjectionInverse()));
		builder.add("dhPreviousProjection", UniformShape.MAT4,
				(world, out) -> out.set(world.dhPreviousProjection()));

		builder.add("dhNearPlane", UniformShape.FLOAT, (world, out) -> out.set(world.dhNearPlane()));
		builder.add("dhFarPlane", UniformShape.FLOAT, (world, out) -> out.set(world.dhFarPlane()));
		builder.add("dhRenderDistance", UniformShape.INT,
				(world, out) -> out.set(world.dhRenderDistance()));
	}
}
