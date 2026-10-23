package net.corevk.corevk.pack.source;

import net.corevk.corevk.pack.option.EngineDefines;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DimensionSet {

	
	private static final Pattern CONVENTIONAL = Pattern.compile("^world-?\\d+$");

	private static final Pattern DECLARATION =
			Pattern.compile("^\\s*dimension\\.([A-Za-z0-9_-]+)\\s*=(.*)$");

	
	private static final Pattern ANY_KEY = Pattern.compile("^\\s*[^#!\\s][^=]*=.*$");

	private static final String PROPERTIES = "dimension.properties";

	
	private static final Map<String, String> CONVENTION = Map.of(
			"world0", "minecraft:overworld",
			"world-1", "minecraft:the_nether",
			"world1", "minecraft:the_end");

	
	private static final String ANY = "*";

	private final List<String> names;
	private final Map<String, String> places;
	private final boolean declared;

	private DimensionSet(List<String> names, Map<String, String> places, boolean declared) {
		this.names = List.copyOf(names);
		this.places = Map.copyOf(places);
		this.declared = declared;
	}

	public static DimensionSet discover(ShaderPackSource source) throws IOException {
		Set<String> found = new LinkedHashSet<>();
		Map<String, String> places = new LinkedHashMap<>();
		boolean declared = false;

		PropertiesFile properties = PropertiesFile.read(source, PROPERTIES);
		if (properties.present()) {
			// The engine's table and never the pack's settings: the reference preprocesses this
			// file before the include graph exists, so a conditional here can only see the
			// environment defines (ShaderPack.java:125-126). A declaration in a dead branch is
			// no declaration.
			Map<String, String> defines = EngineDefines.table(EngineDefines.machine());

			// A folder to the LAST worlds it was assigned, in first-assignment order. The
			// reference reads this file as ordered properties, so re-assigning dimension.<folder>
			// keeps the key's place, drops the earlier line's worlds entirely, and it is the
			// FOLDER that is the key: two lines about one folder are one entry, two folders
			// claiming one world are settled by whichever entry sits later.
			Map<String, String> assignments = new LinkedHashMap<>();

			// The shared walk, for the continuations as much as for the conditionals: both
			// Complementary variants list the modded worlds of a dimension one continued line per
			// mod, and the reference joins them because it loads the preprocessed file as
			// properties (ShaderPack.java:376-385). Read line by line, every mod but the first is
			// lost and the backslash is left behind as a world of its own.
			List<String> live = new ArrayList<>();
			properties.walk(defines, live::add);

			for (String line : live) {
				Matcher declaration = DECLARATION.matcher(line);
				if (declaration.matches()) {
					assignments.put(declaration.group(1), declaration.group(2));
				}

				// ANY live key suppresses the conventional folders, well formed or not: the
				// reference's test is that the parsed file is not empty (ShaderPack.java:127),
				// not that it holds a dimension line.
				if (ANY_KEY.matcher(line).matches()) {
					declared = true;
				}
			}

			assignments.forEach((folder, worlds) -> {
				found.add(folder);
				for (String world : worlds.trim().split("\\s+", -1)) {
					if (!world.isEmpty()) {
						places.put(normalise(world), folder);
					}
				}
			});
		}

		for (String directory : source.topLevelDirectories()) {
			if (CONVENTIONAL.matcher(directory).matches()) {
				found.add(directory);
			}
		}

		if (!declared) {
			// Ported from CoreVK ShaderPack.java:132-148, defaults included: the overworld folder is
			// also what every world the pack never heard of is drawn from, and the other two answer
			// for themselves alone.
			CONVENTION.forEach((directory, world) -> {
				if (found.contains(directory)) {
					places.put(world, directory);
				}
			});

			if (found.contains("world0")) {
				places.put(ANY, "world0");
			}
		}

		return new DimensionSet(found.stream().sorted().toList(), places, declared);
	}

	public boolean isDimensionDirectory(String name) {
		return this.names.contains(name);
	}

	public List<String> names() {
		return this.names;
	}

	public boolean hasDimensionProperties() {
		return this.declared;
	}

	
	public String place(String world) {
		String named = this.places.get(world);

		return named != null ? named : this.places.getOrDefault(ANY, "");
	}

	
	public boolean declares(String world) {
		return this.places.containsKey(world);
	}

	
	private static String normalise(String world) {
		if (ANY.equals(world)) {
			return ANY;
		}

		return world.indexOf(':') < 0 ? "minecraft:" + world : world;
	}
}
