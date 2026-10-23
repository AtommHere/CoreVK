package net.corevk.corevk.uniform.values;

import net.corevk.corevk.uniform.UniformCatalog;
import net.corevk.corevk.uniform.UniformShape;

public final class WeatherValues {

	private static final FrameSmoothed WETNESS = new FrameSmoothed();

	private WeatherValues() {
	}

	public static void register(UniformCatalog.Builder builder) {
		builder.add("rainStrength", UniformShape.FLOAT, (world, out) -> out.set(world.rainStrength()));
		builder.add("thunderStrength", UniformShape.FLOAT,
				(world, out) -> out.set(world.thunderStrength()));
		builder.add("wetness", UniformShape.FLOAT, (world, out) -> out.set(WETNESS.get(world,
				world.rainStrength(), world.wetnessHalfLife(), world.drynessHalfLife())));

		builder.add("skyColor", UniformShape.VEC3, (world, out) -> {
			int packed = world.skyColorPacked();
			out.set(((packed >> 16) & 255) / 255.0F, ((packed >> 8) & 255) / 255.0F,
					(packed & 255) / 255.0F);
		});

		builder.add("fogColor", UniformShape.VEC3,
				(world, out) -> out.set(world.fogR(), world.fogG(), world.fogB()));
		builder.add("fogStart", UniformShape.FLOAT, (world, out) -> out.set(world.fogStart()));
		builder.add("fogEnd", UniformShape.FLOAT, (world, out) -> out.set(world.fogEnd()));
		builder.add("fogDensity", UniformShape.FLOAT,
				(world, out) -> out.set(Math.max(0.0F, world.fogDensity())));
		builder.add("fogMode", UniformShape.INT, (world, out) -> out.set(world.fogMode()));
		builder.add("fogShape", UniformShape.INT, (world, out) -> out.set(world.fogShape()));

		builder.add("of_Fog", UniformShape.FOG, (world, out) -> {
			float range = world.fogEnd() - world.fogStart();
			out.setFog(world.fogR(), world.fogG(), world.fogB(), world.fogA(),
					Math.max(0.0F, world.fogDensity()), world.fogStart(), world.fogEnd(),
					range == 0.0F ? 0.0F : 1.0F / range);
		});
	}
}
