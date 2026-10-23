package net.corevk.corevk.uniform.values;

import net.corevk.corevk.uniform.UniformCatalog;
import net.corevk.corevk.uniform.UniformShape;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

public final class DrawValues {

	public static final Matrix4f QUAD_PROJECTION = new Matrix4f().set(
			2.0F, 0.0F, 0.0F, 0.0F,
			0.0F, 2.0F, 0.0F, 0.0F,
			0.0F, 0.0F, 0.0F, 0.0F,
			-1.0F, -1.0F, 0.0F, 1.0F);

	public static final Matrix4f QUAD_PROJECTION_INVERSE = new Matrix4f().set(
			0.5F, 0.0F, 0.0F, 0.0F,
			0.0F, 0.5F, 0.0F, 0.0F,
			0.0F, 0.0F, 0.0F, 0.0F,
			0.5F, 0.5F, 0.0F, 1.0F);

	private static final Matrix4f IDENTITY = new Matrix4f();
	private static final Matrix3f NORMAL_IDENTITY = new Matrix3f();

	private DrawValues() {
	}

	public static void register(UniformCatalog.Builder builder) {
		builder.add("of_ModelViewMatrix", UniformShape.MAT4, (_, out) -> out.set(IDENTITY));
		builder.add("of_ModelViewMatrixInverse", UniformShape.MAT4, (_, out) -> out.set(IDENTITY));
		builder.add("of_ModelViewProjectionMatrix", UniformShape.MAT4, (_, out) -> out.set(QUAD_PROJECTION));
		builder.add("of_ProjectionMatrix", UniformShape.MAT4, (_, out) -> out.set(QUAD_PROJECTION));
		builder.add("of_ProjectionMatrixInverse", UniformShape.MAT4, (_, out) -> out.set(QUAD_PROJECTION_INVERSE));
		builder.add("of_NormalMatrix", UniformShape.MAT3, (_, out) -> out.set(NORMAL_IDENTITY));

		builder.add("of_TextureMatrix", UniformShape.MAT4, (_, out) -> out.set(IDENTITY));

		builder.add("viewWidth", UniformShape.FLOAT, (world, out) -> out.set(world.viewWidth()));
		builder.add("viewHeight", UniformShape.FLOAT, (world, out) -> out.set(world.viewHeight()));
		builder.add("aspectRatio", UniformShape.FLOAT,
				(world, out) -> out.set(world.viewWidth() / Math.max(1.0F, world.viewHeight())));

		builder.add("of_DepthConv", UniformShape.VEC4, (world, out) -> out.set(
				world.depthConvention().x(), world.depthConvention().y(),
				world.depthConvention().z(), world.depthConvention().w()));

		builder.add("pi", UniformShape.FLOAT, (_, out) -> out.set((float) Math.PI));
		builder.add("atlasSize", UniformShape.IVEC2,
				(world, out) -> out.set(world.atlasWidth(), world.atlasHeight()));
		builder.add("renderStage", UniformShape.INT, (world, out) -> out.set(world.renderStage()));
		builder.add("anisotropicFiltering", UniformShape.INT,
				(world, out) -> out.set((int) world.anisotropy()));
		builder.add("currentColorSpace", UniformShape.INT,
				(world, out) -> out.set(world.colorSpace()));
		builder.add("textureFilteringMode", UniformShape.INT,
				(world, out) -> out.set(world.textureFilteringMode()));
		builder.add("chunkFadeTimeInv", UniformShape.FLOAT,
				(world, out) -> out.set(world.chunkFadeTimeInv()));
		builder.add("ambientOcclusionLevel", UniformShape.FLOAT,
				(world, out) -> out.set(world.ambientOcclusionLevel()));
		builder.add("noiseTextureResolution", UniformShape.FLOAT,
				(world, out) -> out.set(world.noiseTextureResolution()));

		builder.add("entityColor", UniformShape.VEC4,
				(_, out) -> out.set(0.0F, 0.0F, 0.0F, 0.0F));

		builder.add("entityId", UniformShape.INT, (_, out) -> out.set(0));
		builder.add("blockEntityId", UniformShape.INT, (_, out) -> out.set(-1));
		builder.add("currentRenderedItemId", UniformShape.INT, (_, out) -> out.set(-1));

		builder.add("alphaTestRef", UniformShape.FLOAT, (world, out) -> out.set(world.passAlphaTest()));
	}
}
