package net.corevk.corevk.uniform.values;

import net.corevk.corevk.glsl.LegacyGlsl;
import net.corevk.corevk.uniform.UniformCatalog;
import net.corevk.corevk.uniform.UniformShape;
import net.corevk.corevk.uniform.UniformSource;
import net.corevk.corevk.uniform.Val;
import net.corevk.corevk.uniform.WorldState;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

public final class GeometryValues {

	private static final float SUB_TEXEL_OFFSET = 3.0517578E-5F;
	private static final float SUB_TEXEL_PRECISION = 256.0F;

	private static boolean lightmapUnit(int element) {
		return element == 1 || element == 2;
	}

	private static final Matrix4f LIGHTMAP_TEXTURE_MATRIX = new Matrix4f(
			1.0F / 256.0F, 0.0F, 0.0F, 0.0F,
			0.0F, 1.0F / 256.0F, 0.0F, 0.0F,
			0.0F, 0.0F, 1.0F / 256.0F, 0.0F,
			1.0F / 32.0F, 1.0F / 32.0F, 1.0F / 32.0F, 1.0F);

	private static final Matrix4f IDENTITY = new Matrix4f();

	private static final Matrix4f MODEL_VIEW_PROJECTION = new Matrix4f();
	private static final Settled MODEL_VIEW_PROJECTION_INPUTS = new Settled();
	private static final Matrix3f NORMAL = new Matrix3f();
	private static final Settled NORMAL_INPUTS = new Settled();

	private GeometryValues() {
	}

	private static float shrink(int size) {
		return SUB_TEXEL_OFFSET - 1.0F / Math.max(1, size) / SUB_TEXEL_PRECISION;
	}

	public static void register(UniformCatalog.Builder builder) {
		builder.add("of_TexShrink", UniformShape.VEC2, (world, out) ->
				out.set(shrink(world.atlasWidth()), shrink(world.atlasHeight())));

		builder.add("centerDepthSmooth", UniformShape.FLOAT, (_, out) -> out.set(0.0F));

		builder.add(LegacyGlsl.GLINT_ALPHA, UniformShape.FLOAT,
				(world, out) -> out.set(world.glintAlpha()));

		builder.add(LegacyGlsl.CAMERA_BOB, UniformShape.MAT4,
				(world, out) -> out.set(world.cameraBob()));

		builder.add("of_PassColour", UniformShape.VEC4, (world, out) -> out.set(
				world.passColour().x(), world.passColour().y(), world.passColour().z(),
				world.passColour().w()));

		builder.add("of_ModelViewMatrix", UniformShape.MAT4,
				(world, out) -> out.set(world.passModelView()));
		builder.add("of_ModelViewMatrixInverse", UniformShape.MAT4,
				(world, out) -> out.set(world.passModelViewInverse()));

		builder.add("of_ProjectionMatrix", UniformShape.MAT4,
				(world, out) -> out.set(world.passProjection()));
		builder.add("of_ProjectionMatrixInverse", UniformShape.MAT4,
				(world, out) -> out.set(world.passProjectionInverse()));

		builder.add("of_ModelViewProjectionMatrix", UniformShape.MAT4, (world, out) -> {
			if (!MODEL_VIEW_PROJECTION_INPUTS.holds(world.passProjection(), world.passModelView())) {
				MODEL_VIEW_PROJECTION.set(world.passProjection()).mul(world.passModelView());
			}

			out.set(MODEL_VIEW_PROJECTION);
		});

		builder.add("of_NormalMatrix", UniformShape.MAT3, (world, out) -> {
			if (!NORMAL_INPUTS.holds(world.passModelView())) {
				NORMAL.set(world.passModelView()).invert().transpose();
			}

			out.set(NORMAL);
		});

		builder.add("of_TextureMatrix", UniformShape.MAT4, new UniformSource() {

			@Override
			public void read(WorldState world, Val out) {
				out.set(IDENTITY);
			}

			@Override
			public void read(WorldState world, Val out, int element) {
				out.set(lightmapUnit(element) ? LIGHTMAP_TEXTURE_MATRIX : IDENTITY);
			}
		});
	}
}
