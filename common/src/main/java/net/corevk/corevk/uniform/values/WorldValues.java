package net.corevk.corevk.uniform.values;

import net.corevk.corevk.uniform.UniformCatalog;
import net.corevk.corevk.uniform.UniformShape;

public final class WorldValues {

	private WorldValues() {
	}

	public static void register(UniformCatalog.Builder builder) {
		builder.add("bedrockLevel", UniformShape.INT, (world, out) -> out.set(world.bedrockLevel()));
		builder.add("heightLimit", UniformShape.INT, (world, out) -> out.set(world.heightLimit()));
		builder.add("logicalHeightLimit", UniformShape.INT,
				(world, out) -> out.set(world.logicalHeightLimit()));
		builder.add("hasCeiling", UniformShape.INT, (world, out) -> out.set(world.hasCeiling()));
		builder.add("hasSkylight", UniformShape.INT, (world, out) -> out.set(world.hasSkylight()));
		builder.add("ambientLight", UniformShape.FLOAT, (world, out) -> out.set(world.ambientLight()));
		builder.add("cloudHeight", UniformShape.FLOAT, (world, out) -> out.set(world.cloudHeight()));
		builder.add("seaLevel", UniformShape.INT, (world, out) -> out.set(world.seaLevel()));

		builder.add("biome", UniformShape.INT, (world, out) -> out.set(world.biomeId()));
		builder.add("biome_category", UniformShape.INT,
				(world, out) -> out.set(world.biomeCategory()));
		builder.add("biome_precipitation", UniformShape.INT,
				(world, out) -> out.set(world.biomePrecipitation()));
		builder.add("rainfall", UniformShape.FLOAT, (world, out) -> out.set(world.rainfall()));
		builder.add("temperature", UniformShape.FLOAT, (world, out) -> out.set(world.temperature()));
	}
}
