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

		// A nought, and it is the value rather than a stand-in for one. CoreVK makes the smoothed
		// centre depth available to the full screen stages alone, its CompositeDepthTransformer
		// running under Patch.COMPOSITE, so the uniform a gbuffers program declares is one nothing
		// ever writes and reads as a zero there too. Answered here rather than left to the block's
		// own zero because the two are the same bytes and only one of them says why: unanswered,
		// the name is reported as a value this engine owes, which it does not.
		builder.add("centerDepthSmooth", UniformShape.FLOAT, (_, out) -> out.set(0.0F));

		// The whole of a glint's vertex colour, and answered here rather than in the engine table
		// because only a family drawn over the world has a glint: it is a name no full screen pass can
		// reach. What multiplies it in CoreVK is the draw's colour modulator, which is white for every
		// draw the game prepares from a render type, so the product is this number
		// (VanillaTransformer.java:134, against GameTransforms.BLOCK).
		builder.add(LegacyGlsl.GLINT_ALPHA, UniformShape.FLOAT,
				(world, out) -> out.set(world.glintAlpha()));

		// The left factor of every pass model view of the frame, published for the passes that form
		// that product in the shader rather than read it ready made, which LegacyGlsl.CAMERA_BOB
		// names. Here for the same reason as the line above: a full screen pass has no model view to
		// build.
		builder.add(LegacyGlsl.CAMERA_BOB, UniformShape.MAT4,
				(world, out) -> out.set(world.cameraBob()));

		// The colour the game modulates a whole draw by, which for the sky is where its colour is:
		// the mesh of a sky disc carries a position and nothing else, and the mesh of the sunrise
		// band carries only the fade. White for every pass that has not set one.
		builder.add("of_PassColour", UniformShape.VEC4, (world, out) -> out.set(
				world.passColour().x(), world.passColour().y(), world.passColour().z(),
				world.passColour().w()));

		// The PASS's model view and not the frame's, and the two are one matrix for every pass but
		// the sky's and the hand's. What separates them is written out in ViewSource.passModelView:
		// the game puts the sun where it is by pushing a rotation onto its own stack, and a pack reads
		// that rotation here while reading the camera under gbufferModelView, using both at once.
		//
		// The families the entity door records from the camera do not read this name at all: their
		// gl_ModelViewMatrix goes to the game's own per draw block, LegacyGlsl.readsDrawModelView
		// saying which and why. What they still take from here is the inverse and the normal matrix
		// below, which CoreVK also leaves on its own per pass answer.
		builder.add("of_ModelViewMatrix", UniformShape.MAT4,
				(world, out) -> out.set(world.passModelView()));
		builder.add("of_ModelViewMatrixInverse", UniformShape.MAT4,
				(world, out) -> out.set(world.passModelViewInverse()));
		// The PASS's projection and not the frame's, on the same argument as the model view above and
		// with one family behind it rather than two: the hand is drawn under a head-up field of view
		// and a clip depth squeezed to an eighth, and a pack reads that squeeze here while reading the
		// camera's volume under gbufferProjection. ViewSource.passProjection has the two ways round it
		// breaks. Every other pass sets none and is answered with the frame's, as it always was.
		builder.add("of_ProjectionMatrix", UniformShape.MAT4,
				(world, out) -> out.set(world.passProjection()));
		builder.add("of_ProjectionMatrixInverse", UniformShape.MAT4,
				(world, out) -> out.set(world.passProjectionInverse()));

		// Composed here rather than published, because nothing else in the engine reads it: a full
		// screen pass is handed the quad projection for it and a pack calling ftransform() is the
		// only reader. Left to right, so that the pack's own gl_ProjectionMatrix * gl_ModelViewMatrix
		// and its ftransform() are the same matrix, which is why this reads the pass's projection and
		// not the frame's: the two factors have to be the two the pack would have multiplied.
		builder.add("of_ModelViewProjectionMatrix", UniformShape.MAT4, (world, out) -> {
			if (!MODEL_VIEW_PROJECTION_INPUTS.holds(world.passProjection(), world.passModelView())) {
				MODEL_VIEW_PROJECTION.set(world.passProjection()).mul(world.passModelView());
			}

			out.set(MODEL_VIEW_PROJECTION);
		});

		// The inverse transpose of the model view's rotation. The level's model view is a pure
		// rotation, so this is its transpose, but it is computed rather than assumed: a shadow pass
		// or a pack directive could put a scale in it, and normalising a normal afterwards hides
		// the difference exactly until it does not.
		builder.add("of_NormalMatrix", UniformShape.MAT3, (world, out) -> {
			if (!NORMAL_INPUTS.holds(world.passModelView())) {
				NORMAL.set(world.passModelView()).invert().transpose();
			}

			out.set(NORMAL);
		});

		// Six identities and the light map's twice, where the engine table answers eight identities.
		// That is the whole of the difference between a pass drawn over the world and one drawn over a
		// quad: a quad has no light map, and CoreVK replaces all eight of its texture matrices with the
		// identity for exactly that reason (CompositeTransformer.java:43).
		//
		// A read of the identity here is not a small error. The idiom every pack writes is
		// (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy, and with the identity that lands at 240 on
		// both axes, which the sampler clamps to the corner of the light map: full block light and
		// full sky light, on every surface, whatever the world is doing.
		//
		// A LITERAL READ OF UNIT NOUGHT DOES NOT REACH THIS TABLE ON THE ENTITY FAMILY. What a pack
		// means by it is the matrix that draw was prepared with, which belongs to the draw and not to
		// the run this block is written for, so the translation sends the read to the game's own
		// transforms instead (glsl/LegacyGlsl.GAME_TEXTURE_MATRIX). A read of unit nought through an
		// index that is not that literal still lands here, and on the identity below; the rewrite says
		// so and no pack of the corpus writes one.
		//
		// The identity below is what is left, and the two halves of that are not alike. On the terrain
		// it is CoreVK's own answer, mat4(1.0) at SodiumTransformer.java:31. On the sky, the clouds, the
		// weather and the particles it is not: those are vanilla programs under CoreVK and read the
		// game's matrix at VanillaTransformer.java:163. They come out at the same number all the same,
		// every render type of theirs leaving the transform at DEFAULT_TEXTURING, which is the
		// identity; LegacyGlsl.GAME_TEXTURE_MATRIX carries the measure and what would end it.
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
