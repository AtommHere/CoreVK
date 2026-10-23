package net.corevk.corevk.uniform.values;

import net.corevk.corevk.uniform.UniformCatalog;
import net.corevk.corevk.uniform.UniformShape;
import net.corevk.corevk.uniform.Val;
import net.corevk.corevk.uniform.WorldState;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

public final class CelestialValues {

	private static final Matrix4f SCRATCH = new Matrix4f();
	private static final Vector4f POSITION = new Vector4f();

	private static final Settled SUN = new Settled();
	private static final Settled MOON = new Settled();
	private static final Settled UP = new Settled();
	private static final Settled FLASH = new Settled();

	private static final Vector3f SUN_POSITION = new Vector3f();
	private static final Vector3f MOON_POSITION = new Vector3f();
	private static final Vector3f UP_POSITION = new Vector3f();
	private static final Vector3f FLASH_POSITION = new Vector3f();

	private CelestialValues() {
	}

	public static void register(UniformCatalog.Builder builder) {
		builder.add("sunAngle", UniformShape.FLOAT,
				(world, out) -> out.set(sunAngle(world, true) / 360.0F));
		builder.add("shadowAngle", UniformShape.FLOAT,
				(world, out) -> out.set(sunAngle(world, isDay(world)) / 360.0F));

		builder.add("sunPosition", UniformShape.VEC3, (world, out) -> celestial(world, true, out));
		builder.add("moonPosition", UniformShape.VEC3, (world, out) -> celestial(world, false, out));
		builder.add("upPosition", UniformShape.VEC3, CelestialValues::up);

		builder.add("shadowLightPosition", UniformShape.VEC3, (world, out) -> {
			if (inEndFlash(world) && world.endFlashShadows()) {
				endFlash(world, out);
			} else {
				celestial(world, isDay(world), out);
			}
		});

		builder.add("endFlashPosition", UniformShape.VEC3, (world, out) -> {
			if (inEndFlash(world)) {
				endFlash(world, out);
			} else {
				out.set(0.0F, 0.0F, 0.0F);
			}
		});

		builder.add("endFlashIntensity", UniformShape.FLOAT,
				(world, out) -> out.set(world.endFlashIntensity()));
		builder.add("previousEndFlashIntensity", UniformShape.FLOAT,
				(world, out) -> out.set(world.previousEndFlashIntensity()));
	}

	public static Vector3f shadowLightVector(WorldState world, Vector3f dest) {

		if (inEndFlash(world) && world.endFlashShadows()) {
			SCRATCH.identity()
					.rotateY((float) Math.toRadians(180.0F - world.endFlashYAngleDegrees()))
					.rotateX((float) Math.toRadians(-90.0F - world.endFlashXAngleDegrees()));
		} else {
			SCRATCH.identity()
					.rotateY((float) Math.toRadians(-90.0F))
					.rotateZ((float) Math.toRadians(world.sunPathRotation()))
					.rotateX((float) Math.toRadians(isDay(world)
							? world.sunAngleDegrees() : world.moonAngleDegrees()));
		}

		POSITION.set(0.0F, 100.0F, 0.0F, 0.0F);
		SCRATCH.transform(POSITION);

		return dest.set(POSITION.x, POSITION.y, POSITION.z).normalize();
	}

	private static float sunAngle(WorldState world, boolean sun) {
		float angle = (sun ? world.sunAngleDegrees() : world.moonAngleDegrees()) + 90.0F;
		if (angle < 0.0F) {
			angle += 360.0F;
		} else if (angle > 360.0F) {
			angle -= 360.0F;
		}

		return angle;
	}

	private static boolean isDay(WorldState world) {
		return sunAngle(world, true) < 180.0F;
	}

	private static boolean inEndFlash(WorldState world) {
		return world.dimensionOrdinal() == 1 && world.hasEndFlash();
	}

	private static void up(WorldState world, Val out) {
		if (!UP.holds(world.gbufferModelView())) {
			SCRATCH.set(world.gbufferModelView()).rotateY((float) Math.toRadians(-90.0F));
			POSITION.set(0.0F, 100.0F, 0.0F, 0.0F);
			SCRATCH.transform(POSITION);
			UP_POSITION.set(POSITION.x, POSITION.y, POSITION.z);
		}

		out.set(UP_POSITION);
	}

	private static void celestial(WorldState world, boolean sun, Val out) {
		Settled settled = sun ? SUN : MOON;
		Vector3f held = sun ? SUN_POSITION : MOON_POSITION;
		float angle = sun ? world.sunAngleDegrees() : world.moonAngleDegrees();

		if (!settled.holds(world.gbufferModelView(), world.sunPathRotation(), angle)) {
			SCRATCH.set(world.gbufferModelView())
					.rotateY((float) Math.toRadians(-90.0F))
					.rotateZ((float) Math.toRadians(world.sunPathRotation()))
					.rotateX((float) Math.toRadians(angle));

			POSITION.set(0.0F, 100.0F, 0.0F, 1.0F);
			SCRATCH.transform(POSITION);
			held.set(POSITION.x, POSITION.y, POSITION.z);
		}

		out.set(held);
	}

	private static void endFlash(WorldState world, Val out) {
		if (!FLASH.holds(world.gbufferModelView(),
				world.endFlashXAngleDegrees(), world.endFlashYAngleDegrees())) {
			SCRATCH.set(world.gbufferModelView())
					.rotateY((float) Math.toRadians(180.0F - world.endFlashYAngleDegrees()))
					.rotateX((float) Math.toRadians(-90.0F - world.endFlashXAngleDegrees()));

			POSITION.set(0.0F, 100.0F, 0.0F, 0.0F);
			SCRATCH.transform(POSITION);
			FLASH_POSITION.set(POSITION.x, POSITION.y, POSITION.z);
		}

		out.set(FLASH_POSITION);
	}
}
