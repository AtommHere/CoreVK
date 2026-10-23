package net.corevk.corevk.sodium;

import net.caffeinemc.mods.sodium.client.render.viewport.frustum.Frustum;

import org.joml.FrustumIntersection;

public final class ShadowCull implements Frustum {

	private final Frustum planes;
	private final float distance;

	
	private final float safeZone;

	
	public ShadowCull(Frustum planes, float distance, float safeZone) {
		this.planes = planes;
		this.distance = distance;
		this.safeZone = safeZone;
	}

	@Override
	public boolean testAab(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
		return inside(minX, minY, minZ, maxX, maxY, maxZ)
				&& this.planes.testAab(minX, minY, minZ, maxX, maxY, maxZ);
	}

	
	@Override
	public int intersectAab(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
		if (!inside(minX, minY, minZ, maxX, maxY, maxZ)) {
			return FrustumIntersection.OUTSIDE;
		}

		int shape = this.planes.intersectAab(minX, minY, minZ, maxX, maxY, maxZ);

		if (shape != FrustumIntersection.INSIDE) {
			return shape;
		}

		// The safe zone outranks the distance on a box it holds whole, which is CoreVK's order and not
		// a softening of the line above: its safe zone frustum answers INSIDE off that box alone the
		// moment the distance is anything but OUTSIDE (SafeZoneCullingFrustum.java:74-78).
		return within(this.distance, minX, minY, minZ, maxX, maxY, maxZ)
				|| (this.safeZone >= 0.0F
						&& within(this.safeZone, minX, minY, minZ, maxX, maxY, maxZ))
				? FrustumIntersection.INSIDE
				: FrustumIntersection.INTERSECT;
	}

	@Override
	public boolean testSection(float x, float y, float z) {
		return this.planes.testSection(x, y, z);
	}

	@Override
	public boolean testSectionExpanded(float x, float y, float z, float extend) {
		return this.planes.testSectionExpanded(x, y, z, extend);
	}

	
	private boolean inside(float minX, float minY, float minZ,
			float maxX, float maxY, float maxZ) {
		return maxX >= -this.distance && minX <= this.distance
				&& maxY >= -this.distance && minY <= this.distance
				&& maxZ >= -this.distance && minZ <= this.distance;
	}

	
	private static boolean within(float half, float minX, float minY, float minZ,
			float maxX, float maxY, float maxZ) {
		return minX >= -half && maxX <= half
				&& minY >= -half && maxY <= half
				&& minZ >= -half && maxZ <= half;
	}
}
