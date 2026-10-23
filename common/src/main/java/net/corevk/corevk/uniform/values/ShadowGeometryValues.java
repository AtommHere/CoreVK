package net.corevk.corevk.uniform.values;

import net.corevk.corevk.uniform.UniformCatalog;
import net.corevk.corevk.uniform.UniformShape;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

public final class ShadowGeometryValues {

	private static final Matrix4f MODEL_VIEW_PROJECTION = new Matrix4f();
	private static final Matrix3f NORMAL = new Matrix3f();

	private ShadowGeometryValues() {
	}

	public static void register(UniformCatalog.Builder builder) {
		builder.add("of_ModelViewMatrix", UniformShape.MAT4,
				(world, out) -> out.set(world.drawnShadowModelView()));
		builder.add("of_ModelViewMatrixInverse", UniformShape.MAT4,
				(world, out) -> out.set(world.drawnShadowModelViewInverse()));
		builder.add("of_ProjectionMatrix", UniformShape.MAT4,
				(world, out) -> out.set(world.drawnShadowProjection()));
		builder.add("of_ProjectionMatrixInverse", UniformShape.MAT4,
				(world, out) -> out.set(world.drawnShadowProjectionInverse()));

		builder.add("of_ModelViewProjectionMatrix", UniformShape.MAT4, (world, out) ->
				out.set(MODEL_VIEW_PROJECTION.set(world.drawnShadowProjection())
						.mul(world.drawnShadowModelView())));

		builder.add("of_NormalMatrix", UniformShape.MAT3, (world, out) ->
				out.set(NORMAL.set(world.drawnShadowModelView())));

		builder.add("shadowModelView", UniformShape.MAT4,
				(world, out) -> out.set(world.drawnShadowModelView()));
		builder.add("shadowModelViewInverse", UniformShape.MAT4,
				(world, out) -> out.set(world.drawnShadowModelViewInverse()));
		builder.add("shadowProjection", UniformShape.MAT4,
				(world, out) -> out.set(world.drawnShadowProjection()));
		builder.add("shadowProjectionInverse", UniformShape.MAT4,
				(world, out) -> out.set(world.drawnShadowProjectionInverse()));
	}
}
