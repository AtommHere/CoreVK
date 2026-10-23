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

		this.bob.set(bob);
		this.modelView.set(bob).mul(view);
		this.modelView.invert(this.modelViewInverse);

		this.rendered.set(projection);
		ClipSpace.toLegacyDepth(projection, this.projection);
		this.projection.invert(this.projectionInverse);

		if (!this.seeded) {

			this.previousModelView.set(this.modelView);
			this.previousProjection.set(this.projection);
			this.previousRendered.set(this.rendered);
			this.seeded = true;
		}

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

		this.distantProjection.set(this.rendered);
		this.distantVolume = offset != 0.0F;
		if (this.distantVolume) {

			this.distantProjection.m22(scale);
			this.distantProjection.m32(offset);

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

		if (this.shadowSeeded) {

			if (drewLastFrame) {
				this.anchorShadowModelView.set(this.shadowModelView);
				this.anchorShadowProjection.set(this.shadowProjection);
				this.anchorShadowProjectionInverse.set(this.shadowProjectionInverse);
				this.anchorShadowCamera.set(this.shadowCamera);
			}

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

		float reach = dhRenderDistance() * 16.0F;
		float near = plane(nearPlane, -reach);
		float far = plane(farPlane, reach);

		this.shadowProjection.setOrthoSymmetric(distance * 2.0F, distance * 2.0F, near, far, false);
		this.shadowProjection.invert(this.shadowProjectionInverse);

		if (!this.shadowSeeded) {

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
