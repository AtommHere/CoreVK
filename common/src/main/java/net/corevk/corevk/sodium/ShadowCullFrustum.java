package net.corevk.corevk.sodium;

import net.corevk.corevk.pack.source.ShadowCullState;
import net.corevk.corevk.render.ShadowCullPlan;
import net.corevk.corevk.render.BoxShadowCull;

import net.caffeinemc.mods.sodium.client.render.viewport.frustum.Frustum;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;

import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;

public final class ShadowCullFrustum implements Frustum {

	private static final int MAX_PLANES = 13;

	private static final int[][] NEIGHBOURS = {
		{2, 3, 4, 5},
		{0, 1, 4, 5},
		{0, 1, 2, 3},
	};

	private static final float SECTION_HALF_SIZE = Viewport.CHUNK_SECTION_PADDED_RADIUS;

	private static final Matrix4f TRANSPOSED = new Matrix4f();
	private static final Vector4f[] FACES = {
			new Vector4f(), new Vector4f(), new Vector4f(),
			new Vector4f(), new Vector4f(), new Vector4f()
	};
	private static final Vector3f EDGE = new Vector3f();
	private static final Vector3f NORMAL = new Vector3f();
	private static final Vector3f POINT = new Vector3f();
	private static final Vector3f FRONT_NORMAL = new Vector3f();

	private final float[][] planes = new float[MAX_PLANES][4];
	private final boolean[] back = new boolean[6];
	private final Vector3f light = new Vector3f();

	private final float safeZone;

	private int planeCount;

	private ShadowCullFrustum(Matrix4fc camera, Vector3fc light, float safeZone) {
		this.safeZone = safeZone;
		this.light.set(light);

		build(camera);
	}

	public static Chosen of(ShadowCullPlan plan) {
		boolean boxAsked = BoxShadowCull.asked();
		boolean box = plan.state() == ShadowCullState.DISTANCE
				|| (plan.state() == ShadowCullState.DEFAULT && plan.voxelised())
				|| ((plan.state() == ShadowCullState.DEFAULT
						|| plan.state() == ShadowCullState.ADVANCED) && boxAsked);

		Frustum frustum;
		String shape;
		if (box) {
			frustum = AlwaysVisible.INSTANCE;
			shape = plan.bound() < 0.0F ? "NONE" : "BOX";
		} else if (plan.state() == ShadowCullState.SAFE_ZONE) {
			frustum = new ShadowCullFrustum(plan.camera(), plan.light(), plan.safeZone());
			shape = "SWEPT";
		} else {
			frustum = new ShadowCullFrustum(plan.camera(), plan.light(), -1.0F);
			shape = "SWEPT";
		}

		String token = token(plan, shape);
		return plan.bound() < 0.0F ? new Chosen(frustum, token)
				: new Chosen(new ShadowCull(frustum, plan.bound(), plan.safeZone()), token);
	}

	public record Chosen(Frustum frustum, String culling) {
	}

	private static String token(ShadowCullPlan plan, String shape) {
		StringBuilder line = new StringBuilder();
		line.append(plan.state().name()).append(' ').append(shape);
		if (plan.bound() >= 0.0F) {
			line.append(" r=").append(num(plan.bound()));
		}
		if (plan.state() == ShadowCullState.SAFE_ZONE && plan.safeZone() >= 0.0F) {
			line.append(" z=").append(num(plan.safeZone()));
		}
		return line.toString();
	}

	private static String num(float value) {
		int whole = (int) value;
		return whole == value ? Integer.toString(whole) : Float.toString(value);
	}

	private void build(Matrix4fc camera) {
		Vector4f[] faces = faces(camera);

		for (int index = 0; index < faces.length; index++) {
			Vector4f face = faces[index];
			float towards = face.x * this.light.x + face.y * this.light.y + face.z * this.light.z;
			this.back[index] = towards > 0.0F;

			if (towards >= 0.0F) {
				add(face.x, face.y, face.z, face.w);
			}
		}

		for (int index = 0; index < faces.length; index++) {
			if (!this.back[index]) {
				continue;
			}

			for (int neighbour : NEIGHBOURS[index >>> 1]) {
				if (!this.back[neighbour]) {
					edge(faces[index], faces[neighbour]);
				}
			}
		}
	}

	private static Vector4f[] faces(Matrix4fc camera) {
		TRANSPOSED.set(camera).transpose();
		face(FACES[0], TRANSPOSED, -1.0F, 0.0F, 0.0F, 1.0F);
		face(FACES[1], TRANSPOSED, 1.0F, 0.0F, 0.0F, 1.0F);
		face(FACES[2], TRANSPOSED, 0.0F, -1.0F, 0.0F, 1.0F);
		face(FACES[3], TRANSPOSED, 0.0F, 1.0F, 0.0F, 1.0F);

		face(FACES[4], TRANSPOSED, 0.0F, 0.0F, -1.0F, 1.0F);
		face(FACES[5], TRANSPOSED, 0.0F, 0.0F, 1.0F, 1.0F);

		return FACES;
	}

	private static void face(Vector4f into, Matrix4fc transposed, float x, float y, float z,
			float w) {
		into.set(x, y, z, w).mul(transposed).normalize();
	}

	private void edge(Vector4f back, Vector4f front) {
		EDGE.set(back.x, back.y, back.z).cross(front.x, front.y, front.z);
		NORMAL.set(EDGE).cross(this.light);
		FRONT_NORMAL.set(front.x, front.y, front.z);
		POINT.set(EDGE).cross(back.x, back.y, back.z).mul(-front.w)
				.add(FRONT_NORMAL.cross(EDGE).mul(-back.w))
				.mul(1.0F / EDGE.lengthSquared());

		add(NORMAL.x, NORMAL.y, NORMAL.z, -NORMAL.dot(POINT));
	}

	private void add(float x, float y, float z, float w) {
		float[] plane = this.planes[this.planeCount];
		plane[0] = x;
		plane[1] = y;
		plane[2] = z;
		plane[3] = w;
		this.planeCount += 1;
	}

	@Override
	public boolean testAab(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {

		return within(minX, minY, minZ, maxX, maxY, maxZ)
				|| visible(minX, minY, minZ, maxX, maxY, maxZ);
	}

	@Override
	public int intersectAab(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {

		if (this.safeZone >= 0.0F && minX >= -this.safeZone && maxX <= this.safeZone
				&& minY >= -this.safeZone && maxY <= this.safeZone
				&& minZ >= -this.safeZone && maxZ <= this.safeZone) {
			return FrustumIntersection.INSIDE;
		}

		if (within(minX, minY, minZ, maxX, maxY, maxZ)) {
			return FrustumIntersection.INTERSECT;
		}

		return corners(minX, minY, minZ, maxX, maxY, maxZ);
	}

	@Override
	public boolean testSection(float originX, float originY, float originZ) {
		return testSectionExpanded(originX, originY, originZ, 0.0F);
	}

	@Override
	public boolean testSectionExpanded(float originX, float originY, float originZ, float extend) {
		float half = SECTION_HALF_SIZE + extend;

		return testAab(originX - half, originY - half, originZ - half, originX + half,
				originY + half, originZ + half);
	}

	private boolean within(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
		return this.safeZone >= 0.0F
				&& maxX >= -this.safeZone && minX <= this.safeZone
				&& maxY >= -this.safeZone && minY <= this.safeZone
				&& maxZ >= -this.safeZone && minZ <= this.safeZone;
	}

	private boolean visible(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
		for (int index = 0; index < this.planeCount; index++) {
			float[] plane = this.planes[index];
			float x = plane[0] < 0.0F ? minX : maxX;
			float y = plane[1] < 0.0F ? minY : maxY;
			float z = plane[2] < 0.0F ? minZ : maxZ;

			if (plane[0] * x + plane[1] * y + plane[2] * z < -plane[3]) {
				return false;
			}
		}

		return true;
	}

	private int corners(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
		boolean inside = true;

		for (int index = 0; index < this.planeCount; index++) {
			float[] plane = this.planes[index];
			float x = plane[0] < 0.0F ? minX : maxX;
			float y = plane[1] < 0.0F ? minY : maxY;
			float z = plane[2] < 0.0F ? minZ : maxZ;

			if (plane[0] * x + plane[1] * y + plane[2] * z < -plane[3]) {
				return FrustumIntersection.OUTSIDE;
			}

			if (inside) {
				inside = plane[0] * (plane[0] < 0.0F ? maxX : minX)
						+ plane[1] * (plane[1] < 0.0F ? maxY : minY)
						+ plane[2] * (plane[2] < 0.0F ? maxZ : minZ) + plane[3] >= 0.0F;
			}
		}

		return inside ? FrustumIntersection.INSIDE : FrustumIntersection.INTERSECT;
	}

	private static final class AlwaysVisible implements Frustum {

		private static final AlwaysVisible INSTANCE = new AlwaysVisible();

		@Override
		public boolean testAab(float minX, float minY, float minZ, float maxX, float maxY,
				float maxZ) {
			return true;
		}

		@Override
		public int intersectAab(float minX, float minY, float minZ, float maxX, float maxY,
				float maxZ) {
			return FrustumIntersection.INSIDE;
		}

		@Override
		public boolean testSection(float originX, float originY, float originZ) {
			return true;
		}

		@Override
		public boolean testSectionExpanded(float originX, float originY, float originZ,
				float extend) {
			return true;
		}
	}
}
