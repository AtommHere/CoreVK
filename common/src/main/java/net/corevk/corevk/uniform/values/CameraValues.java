package net.corevk.corevk.uniform.values;

import net.corevk.corevk.uniform.UniformCatalog;
import net.corevk.corevk.uniform.UniformShape;
import net.corevk.corevk.uniform.Val;

import org.joml.Vector3dc;

public final class CameraValues {

	private CameraValues() {
	}

	public static void register(UniformCatalog.Builder builder) {
		builder.add("near", UniformShape.FLOAT, (world, out) -> out.set(world.near()));
		builder.add("far", UniformShape.FLOAT, (world, out) -> out.set(world.far()));

		builder.add("cameraPosition", UniformShape.VEC3,
				(world, out) -> out.set(world.cameraPosition()));
		builder.add("previousCameraPosition", UniformShape.VEC3,
				(world, out) -> out.set(world.previousCameraPosition()));

		// The Y of the shifted position, which is the raw Y: only X and Z are ever shifted.
		builder.add("eyeAltitude", UniformShape.FLOAT,
				(world, out) -> out.set((float) world.cameraPosition().y()));

		builder.add("cameraPositionInt", UniformShape.IVEC3,
				(world, out) -> whole(world.cameraPositionUnshifted(), out));
		builder.add("cameraPositionFract", UniformShape.VEC3,
				(world, out) -> fraction(world.cameraPositionUnshifted(), out));
		builder.add("previousCameraPositionInt", UniformShape.IVEC3,
				(world, out) -> whole(world.previousCameraPositionUnshifted(), out));
		builder.add("previousCameraPositionFract", UniformShape.VEC3,
				(world, out) -> fraction(world.previousCameraPositionUnshifted(), out));

		builder.add("eyePosition", UniformShape.VEC3, (world, out) -> out.set(world.eyePosition()));
		// Camera minus eye, in that order. The other way round is a sign error that only shows up
		// in third person, where the two are far enough apart to notice.
		builder.add("relativeEyePosition", UniformShape.VEC3, (world, out) -> {
			Vector3dc camera = world.cameraPositionUnshifted();
			Vector3dc eye = world.eyePosition();
			out.set((float) (camera.x() - eye.x()), (float) (camera.y() - eye.y()),
					(float) (camera.z() - eye.z()));
		});
	}

	private static void whole(Vector3dc position, Val out) {
		out.set((int) Math.floor(position.x()), (int) Math.floor(position.y()),
				(int) Math.floor(position.z()));
	}

	private static void fraction(Vector3dc position, Val out) {
		out.set((float) (position.x() - Math.floor(position.x())),
				(float) (position.y() - Math.floor(position.y())),
				(float) (position.z() - Math.floor(position.z())));
	}
}
