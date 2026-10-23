package net.corevk.corevk.render;

import net.corevk.corevk.uniform.ClipSpace;
import net.corevk.corevk.uniform.ViewSource;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector2f;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector4f;
import org.joml.Vector4fc;

public final class ViewMatrices implements ViewSource {

	
	private static final float NEAR = 0.05F;

	
	private static final float RENDER_DISTANCE_SENTINEL = -1.0F;

	
	static final float FALLBACK_PLANE = 0.01F;

	private final Matrix4f modelView = new Matrix4f();
	private final Matrix4f modelViewInverse = new Matrix4f();
	private final Matrix4f projection = new Matrix4f();
	private final Matrix4f projectionInverse = new Matrix4f();

	
	private final Matrix4f rendered = new Matrix4f();
	private final Matrix4f previousModelView = new Matrix4f();
	private final Matrix4f previousProjection = new Matrix4f();

	
	private final Matrix4f previousRendered = new Matrix4f();

	private final Matrix4f shadowModelView = new Matrix4f();
	private final Matrix4f shadowModelViewInverse = new Matrix4f();
	private final Matrix4f shadowProjection = new Matrix4f();
	private final Matrix4f shadowProjectionInverse = new Matrix4f();

	
	private final Matrix4f mapShadowModelView = new Matrix4f();
	private final Matrix4f mapShadowModelViewInverse = new Matrix4f();
	private final Matrix4f mapShadowProjection = new Matrix4f();
	private final Matrix4f mapShadowProjectionInverse = new Matrix4f();

	
	private final Matrix4f anchorShadowModelView = new Matrix4f();
	private final Matrix4f anchorShadowProjection = new Matrix4f();
	private final Matrix4f anchorShadowProjectionInverse = new Matrix4f();
	private final Vector3d anchorShadowCamera = new Vector3d();

	
	private final Vector3d shadowCamera = new Vector3d();

	private final Vector4f convention = new Vector4f(ClipSpace.REVERSED);

	
	private final Matrix4f passModelView = new Matrix4f();
	private final Matrix4f passModelViewInverse = new Matrix4f();
	private boolean passSet;

	
	private final Matrix4f passProjection = new Matrix4f();
	private final Matrix4f passProjectionInverse = new Matrix4f();
	private boolean passProjectionSet;

	
	private final Matrix4f bob = new Matrix4f();

	
	private final Matrix4f passBob = new Matrix4f();
	private boolean passBobSet;

	
	private static final Vector4f OPAQUE_WHITE = new Vector4f(1.0F, 1.0F, 1.0F, 1.0F);
	private final Vector4f passColour = new Vector4f(1.0F, 1.0F, 1.0F, 1.0F);
	
	private float passAlphaTest;
	private float far;
	private int renderDistanceChunks;
	private boolean seeded;
	private boolean shadowSeeded;

	
	private final Matrix4f distantProjection = new Matrix4f();
	private final Matrix4f publishedDistant = new Matrix4f();
	private final Matrix4f publishedDistantInverse = new Matrix4f();

	
	private final Matrix4f previousPublishedDistant = new Matrix4f();
	private boolean distantSeeded;

	
	private boolean distantVolume;

	
	private float dhNear = FALLBACK_PLANE;
	private float dhFar = FALLBACK_PLANE;
	private int dhRenderDistanceBlocks = -1;

	
	void advance(Matrix4fc view, Matrix4fc bob, Matrix4fc projection, float far,
			int renderDistanceChunks) {
		if (this.seeded) {
			this.previousModelView.set(this.modelView);
			this.previousProjection.set(this.projection);
			this.previousRendered.set(this.rendered);
		}

		// Pre-multiplied and not appended: the bob is applied to the world after the camera has
		// turned, so it stands to the left. The other way round would bob along the player's own
		// axes and swing hardest when looking down.
		this.bob.set(bob);
		this.modelView.set(bob).mul(view);
		this.modelView.invert(this.modelViewInverse);

		this.rendered.set(projection);
		ClipSpace.toLegacyDepth(projection, this.projection);
		this.projection.invert(this.projectionInverse);

		if (!this.seeded) {
			// The first frame has no history. Handing it a zero matrix would make every motion
			// vector in the first frame the whole screen.
			this.previousModelView.set(this.modelView);
			this.previousProjection.set(this.projection);
			this.previousRendered.set(this.rendered);
			this.seeded = true;
		}

		// The pass matrix and the pass colour go back to the camera's and to white here, because
		// they are set by whoever is about to write a block and nobody owes them a clear
		// afterwards. A geometry program that has locked broken never
		// reaches its writeBlock, so the last matrix any pass set, which is the moon's on a frame
		// that drew the sky, would stand in for the camera's in everything read after it, the
		// decoded dump first of all.
		//
		// Here and not beside the two things FrameState clears at the same boundary, which are
		// cleared on both its paths: a frame with no level returns before this is reached, and
		// nothing draws in one, so there is nothing for a stale matrix to reach. The chain drops
		// these same two again before it writes its own blocks, and that is not this one written
		// twice: this one covers whatever reads between the boundary and the first geometry pass,
		// and that one covers the composites, which run after every geometry family has written its
		// own. The render stage is the third value a pass sets and is dropped by the chain alone,
		// the only reader left holding a stale one being the decoded dump, and that is said where
		// the dump is taken.
		this.passSet = false;
		this.passBobSet = false;
		this.passProjectionSet = false;
		this.passColour.set(1.0F, 1.0F, 1.0F, 1.0F);

		this.far = far;
		this.renderDistanceChunks = renderDistanceChunks;
	}

	
	void advanceDistant(float near, float far, int distance) {
		this.dhNear = near;
		this.dhFar = far;
		this.dhRenderDistanceBlocks = distance;
	}

	
	void advanceDistantVolume(float scale, float offset) {
		if (this.distantSeeded) {
			this.previousPublishedDistant.set(this.publishedDistant);
		}

		// The RENDERED matrix and not the published one, and the difference is a defect a review
		// caught: the published matrix is already in the pack's window, so building the volume on
		// it and converting below handed a doubly converted pair to every frame without a row,
		// which is every session without Distant Horizons. On the frames with a row the two bases
		// agree, the row being replaced either way.
		this.distantProjection.set(this.rendered);
		this.distantVolume = offset != 0.0F;
		if (this.distantVolume) {
			// Row z, column z and column w, in JOML's column first spelling: what DH calls m22 and
			// m23 are m22 and m32 here, and the two conventions number the pair the other way round.
			this.distantProjection.m22(scale);
			this.distantProjection.m32(offset);
			// The rest of the row is nought in every perspective the game builds, and DH leaves it
			// alone as well; set here rather than assumed, so that a matrix carrying anything there
			// does not leave half of DH's row standing beside half of the game's.
			this.distantProjection.m02(0.0F);
			this.distantProjection.m12(0.0F);
		}

		ClipSpace.toLegacyDepth(this.distantProjection, this.publishedDistant);
		this.publishedDistant.invert(this.publishedDistantInverse);

		if (!this.distantSeeded) {
			this.previousPublishedDistant.set(this.publishedDistant);
			this.distantSeeded = true;
		}
	}

	
	void advanceShadow(float shadowAngle, float sunPathRotation, float intervalSize,
			Vector3dc camera, float distance, float nearPlane, float farPlane, boolean endFlash,
			float flashXAngle, float flashYAngle, boolean drewLastFrame) {
		// Shifted down before the fresh pair is built, the same move advance makes for previous:
		// what was drawn with last frame is what the map on hand holds.
		if (this.shadowSeeded) {
			// The anchor follows the map and not the frame. It moves on the frames that really drew
			// one, so with the amortisation off it moves every frame and holds the previous frame's
			// pair, which is what this block published before the anchor existed.
			if (drewLastFrame) {
				this.anchorShadowModelView.set(this.shadowModelView);
				this.anchorShadowProjection.set(this.shadowProjection);
				this.anchorShadowProjectionInverse.set(this.shadowProjectionInverse);
				this.anchorShadowCamera.set(this.shadowCamera);
			}

			// On the right, where the grid snap already stands, so that the motion is added to the
			// point before the light turns it rather than after: both offsets are distances in
			// player space, and one applied on the far side of the rotation would be a different
			// place on the ground.
			this.mapShadowModelView.set(this.anchorShadowModelView)
					.translate((float) (camera.x() - this.anchorShadowCamera.x),
							(float) (camera.y() - this.anchorShadowCamera.y),
							(float) (camera.z() - this.anchorShadowCamera.z));
			this.mapShadowModelView.invert(this.mapShadowModelViewInverse);
			this.mapShadowProjection.set(this.anchorShadowProjection);
			this.mapShadowProjectionInverse.set(this.anchorShadowProjectionInverse);
		}

		this.shadowCamera.set(camera);

		this.shadowModelView.identity();
		if (endFlash) {
			this.shadowModelView.rotateX((float) Math.toRadians(-flashXAngle));
			this.shadowModelView.rotateY((float) Math.toRadians(flashYAngle));
		} else {
			float skyAngle = shadowAngle < 0.25F ? shadowAngle + 0.75F : shadowAngle - 0.25F;
			this.shadowModelView.rotateX((float) Math.toRadians(90.0F));
			this.shadowModelView.rotateZ((float) Math.toRadians(skyAngle * -360.0F));
			this.shadowModelView.rotateX((float) Math.toRadians(sunPathRotation));
		}

		snapToGrid(this.shadowModelView, intervalSize, camera);
		this.shadowModelView.invert(this.shadowModelViewInverse);

		// Both planes off the same reach, and the reach is the one CoreVK resolves a -1 with:
		// ShadowMatrices.createOrthoMatrix is handed -DHCompat.getRenderDistance() * 16 and
		// +the same (shadows/ShadowRenderer.java:429), and that call answers the game's render
		// distance in CHUNKS while Distant Horizons is not drawing and that mod's own in BLOCKS
		// while it is (compat/dh/DHCompatInternal.java:102-109, reached through :111-113). The
		// sixteen therefore multiplies
		// two different units, which is exactly what dhRenderDistance already carries for the
		// uniform of the same name, so the two cannot part company here.
		//
		// It is not a rounding: without the mod this is the render distance in blocks, as it has
		// always been, and with it the box along the light grows by that mod's own reach times
		// sixteen. AND THE PACKS ASK FOR IT. A shadow plane of -1 is what a pack writes to hand the
		// choice back, and Bliss writes both of them as -1 in the one branch its Distant Horizons
		// shadow map setting opens: at the game's own render distance the box would reach a hundred
		// and twenty eight blocks either side of the camera, and a far terrain that BEGINS where the
		// game's chunks end would be clipped out of the map the pack just asked to have it in.
		float reach = dhRenderDistance() * 16.0F;
		float near = plane(nearPlane, -reach);
		float far = plane(farPlane, reach);
		// False, and deliberately so. CoreVK asks the device whether it wants 0..1 here, which is
		// right for the matrix it draws the shadow map with and wrong for the one it publishes.
		// We only publish, so this one is always the legacy volume.
		this.shadowProjection.setOrthoSymmetric(distance * 2.0F, distance * 2.0F, near, far, false);
		this.shadowProjection.invert(this.shadowProjectionInverse);

		if (!this.shadowSeeded) {
			// The first frame has no map and no history, so the published pair is the fresh one:
			// wrong by nothing, since there is nothing to sample yet.
			this.mapShadowModelView.set(this.shadowModelView);
			this.mapShadowModelViewInverse.set(this.shadowModelViewInverse);
			this.mapShadowProjection.set(this.shadowProjection);
			this.mapShadowProjectionInverse.set(this.shadowProjectionInverse);
			this.anchorShadowModelView.set(this.shadowModelView);
			this.anchorShadowProjection.set(this.shadowProjection);
			this.anchorShadowProjectionInverse.set(this.shadowProjectionInverse);
			this.anchorShadowCamera.set(camera);
			this.shadowSeeded = true;
		}
	}

	
	void convention(Vector4fc convention) {
		this.convention.set(convention);
	}

	
	void passModelView(Matrix4fc matrix, Matrix4fc bob) {
		this.passSet = matrix != null;
		this.passBobSet = this.passSet && bob != null;
		if (this.passBobSet) {
			this.passBob.set(bob);
		}

		if (this.passSet) {
			this.passModelView.set(cameraBob()).mul(matrix);
			this.passModelView.invert(this.passModelViewInverse);
		}
	}

	
	void passProjection(Matrix4fc matrix) {
		this.passProjectionSet = matrix != null;
		if (this.passProjectionSet) {
			ClipSpace.toLegacyDepth(matrix, this.passProjection);
			this.passProjection.invert(this.passProjectionInverse);
		}
	}

	
	void passColour(Vector4fc colour) {
		this.passColour.set(colour == null ? OPAQUE_WHITE : colour);
	}

	
	void passAlphaTest(float reference) {
		this.passAlphaTest = reference;
	}

	
	void reset() {
		this.seeded = false;
		this.shadowSeeded = false;
		this.distantSeeded = false;
		// The map goes with the history: one drawn in the overworld is not a map of the nether, and
		// keeping it for three frames there would show the old world through the new one.
		ShadowAmortisation.forget();
	}

	private static float plane(float declared, float fallback) {
		return declared == RENDER_DISTANCE_SENTINEL ? fallback : declared;
	}

	
	private static void snapToGrid(Matrix4f target, float intervalSize, Vector3dc camera) {
		if (Math.abs(intervalSize) == 0.0F) {
			return;
		}

		float half = intervalSize / 2.0F;
		float offsetX = (float) camera.x() % intervalSize - half;
		float offsetY = (float) camera.y() % intervalSize - half;
		float offsetZ = (float) camera.z() % intervalSize - half;

		target.translate(offsetX, offsetY, offsetZ);
	}

	@Override
	public Matrix4fc gbufferModelView() {
		return this.modelView;
	}

	@Override
	public Matrix4fc gbufferModelViewInverse() {
		return this.modelViewInverse;
	}

	@Override
	public Matrix4fc passModelView() {
		return this.passSet ? this.passModelView : this.modelView;
	}

	
	@Override
	public Matrix4fc cameraBob() {
		return this.passBobSet ? this.passBob : this.bob;
	}

	@Override
	public Matrix4fc passModelViewInverse() {
		return this.passSet ? this.passModelViewInverse : this.modelViewInverse;
	}

	@Override
	public Matrix4fc passProjection() {
		return this.passProjectionSet ? this.passProjection : this.projection;
	}

	@Override
	public Matrix4fc passProjectionInverse() {
		return this.passProjectionSet ? this.passProjectionInverse : this.projectionInverse;
	}

	@Override
	public Vector4fc passColour() {
		return this.passColour;
	}

	@Override
	public float passAlphaTest() {
		return this.passAlphaTest;
	}

	@Override
	public Matrix4fc gbufferProjection() {
		return this.projection;
	}

	@Override
	public Matrix4fc gbufferProjectionInverse() {
		return this.projectionInverse;
	}

	@Override
	public Matrix4fc gbufferPreviousModelView() {
		return this.previousModelView;
	}

	@Override
	public Matrix4fc gbufferPreviousProjection() {
		return this.previousProjection;
	}

	
	Matrix4fc rendered() {
		return this.rendered;
	}

	
	Matrix4fc previousRendered() {
		return this.previousRendered;
	}

	@Override
	public Matrix4fc shadowModelView() {
		return this.mapShadowModelView;
	}

	@Override
	public Matrix4fc shadowModelViewInverse() {
		return this.mapShadowModelViewInverse;
	}

	@Override
	public Matrix4fc shadowProjection() {
		return this.mapShadowProjection;
	}

	@Override
	public Matrix4fc shadowProjectionInverse() {
		return this.mapShadowProjectionInverse;
	}

	
	@Override
	public Matrix4fc drawnShadowModelView() {
		return keeping() ? this.mapShadowModelView : this.shadowModelView;
	}

	@Override
	public Matrix4fc drawnShadowModelViewInverse() {
		return keeping() ? this.mapShadowModelViewInverse : this.shadowModelViewInverse;
	}

	@Override
	public Matrix4fc drawnShadowProjection() {
		return keeping() ? this.mapShadowProjection : this.shadowProjection;
	}

	@Override
	public Matrix4fc drawnShadowProjectionInverse() {
		return keeping() ? this.mapShadowProjectionInverse : this.shadowProjectionInverse;
	}

	
	private boolean keeping() {
		return this.shadowSeeded && !ShadowAmortisation.drawTerrainThisFrame();
	}

	
	@Override
	public Matrix4fc dhProjection() {
		return this.publishedDistant;
	}

	@Override
	public Matrix4fc dhProjectionInverse() {
		return this.publishedDistantInverse;
	}

	@Override
	public Matrix4fc drawnDistantProjection() {
		return this.distantProjection;
	}

	
	@Override
	public boolean distantDepthPair(Vector2f dest) {
		return this.distantVolume && ClipSpace.distantDepth(this.rendered,
				this.distantProjection.m22(), this.distantProjection.m32(), dest);
	}

	
	@Override
	public Matrix4fc dhPreviousProjection() {
		return this.previousPublishedDistant;
	}

	@Override
	public float dhNearPlane() {
		return this.dhNear;
	}

	@Override
	public float dhFarPlane() {
		return this.dhFar;
	}

	@Override
	public int dhRenderDistance() {
		return this.dhRenderDistanceBlocks < 0 ? this.renderDistanceChunks
				: this.dhRenderDistanceBlocks;
	}

	@Override
	public float near() {
		return NEAR;
	}

	@Override
	public float far() {
		return this.far;
	}

	@Override
	public Vector4fc depthConvention() {
		return this.convention;
	}
}
