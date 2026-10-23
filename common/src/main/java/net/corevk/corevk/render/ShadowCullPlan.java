package net.corevk.corevk.render;

import net.corevk.corevk.pack.source.ShadowCullState;

import org.joml.Matrix4f;
import org.joml.Vector3f;

public record ShadowCullPlan(ShadowCullState state, boolean voxelised, Vector3f light,
		Matrix4f camera, float bound, float safeZone) {
}
