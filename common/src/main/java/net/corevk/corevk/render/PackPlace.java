package net.corevk.corevk.render;

import net.corevk.corevk.pack.source.DimensionSet;
import net.corevk.corevk.pack.source.ShaderPackSource;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.dimension.DimensionType;

import java.io.IOException;

public final class PackPlace {

	private static final String OVERWORLD = "minecraft:overworld";
	private static final String END = "minecraft:the_end";

	
	private static volatile DimensionSet known;

	
	private static volatile String seen = OVERWORLD;

	
	private static volatile String loaded = OVERWORLD;

	
	private static volatile String loadedPlace = "";

	private PackPlace() {
	}

	
	public static String place(ShaderPackSource source) throws IOException {
		known = DimensionSet.discover(source);

		return known.place(world());
	}

	
	public static String world() {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft == null ? null : minecraft.level;
		if (level == null) {
			return seen;
		}

		String world = level.dimension().identifier().toString();
		DimensionSet dimensions = known;
		if (dimensions == null || !dimensions.declares(world)) {
			DimensionType.Skybox sky = level.dimensionType().skybox();
			if (sky == DimensionType.Skybox.END) {
				world = END;
			} else if (sky == DimensionType.Skybox.OVERWORLD) {
				world = OVERWORLD;
			}
		}

		seen = world;

		return world;
	}

	
	public static boolean moved() {
		DimensionSet dimensions = known;

		return dimensions != null && !dimensions.place(world()).equals(loadedPlace);
	}

	
	public static String settled() {
		return loaded;
	}

	
	public static void settle() {
		DimensionSet dimensions = known;
		loaded = world();
		loadedPlace = dimensions == null ? "" : dimensions.place(loaded);
	}
}
