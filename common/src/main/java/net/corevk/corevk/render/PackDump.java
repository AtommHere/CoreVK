package net.corevk.corevk.render;

import net.corevk.corevk.pack.option.EngineDefines;
import net.corevk.corevk.uniform.BiomeCategory;
import net.corevk.corevk.uniform.WorldState;
import net.corevk.corevk.CoreVK;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class PackDump {

	private static volatile String wanted = "";
	private static volatile Path file;
	private static long lastNanos;

	private static volatile boolean said;

	private PackDump() {
	}

	static void configure(String program, Path into) {
		wanted = program;
		file = into;
		said = false;
	}

	static void take(String place, int load, List<PackPass> passes, WorldState world,
			Collection<? extends DumpedProgram> terrain, List<? extends FamilyDraw> families) {
		if (wanted.isEmpty() || file == null) {
			return;
		}

		long now = System.nanoTime();
		if (lastNanos != 0L && now - lastNanos < 1_000_000_000L) {
			return;
		}

		lastNanos = now;
		List<String> running = new ArrayList<>();
		String path = null;
		String decoded = null;

		List<Collection<? extends DumpedProgram>> drawn = new ArrayList<>(1 + families.size());
		drawn.add(terrain);
		for (FamilyDraw family : families) {
			drawn.add(family.programs());
		}

		for (Collection<? extends DumpedProgram> family : drawn) {
			for (DumpedProgram program : family) {
				running.add(program.label());
				if (decoded == null && (names(program.path()) || names(program.label()))) {
					path = program.label();
					decoded = program.decoded(world);
				}
			}
		}

		for (PackPass pass : passes) {
			running.add(pass.path());
			if (decoded == null && names(pass.path())) {
				path = pass.path();
				decoded = pass.decoded(world);
			}
		}

		if (decoded == null) {

			if (running.isEmpty() || said) {
				return;
			}

			said = true;
			CoreVK.logger().warn("{}={} names nothing read so far, so nothing is dumped yet. Read so "
					+ "far: {}", EngineOptions.DUMP_KEY, wanted, running);

			return;
		}

		write("# " + place + ", " + path + ", load " + load + "\n" + situation(world) + decoded);
	}

	private static boolean names(String path) {
		return path.toLowerCase(Locale.ROOT).endsWith(wanted);
	}

	private static void write(String text) {
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, text, StandardCharsets.UTF_8);
		} catch (IOException e) {
			CoreVK.logger().warn("Could not write the decoded dump to {}: {}", file, e.toString());
			wanted = "";
		}
	}

	private static String situation(WorldState world) {
		int id = world.biomeId();

		String name = EngineDefines.machine().biomes().entrySet().stream()
				.filter(entry -> entry.getValue() == id)
				.map(Map.Entry::getKey)
				.findFirst()
				.orElse("!! no BIOME_ define carries this number");

		BiomeCategory[] categories = BiomeCategory.values();
		int category = world.biomeCategory();

		return "# biome " + id + " is BIOME_" + name + ", category " + category + " "
				+ (category >= 0 && category < categories.length ? categories[category] : "?") + "\n"
				+ "# camera " + Math.round(world.cameraPosition().x()) + ", "
				+ Math.round(world.cameraPosition().y()) + ", "
				+ Math.round(world.cameraPosition().z()) + "\n";
	}
}
