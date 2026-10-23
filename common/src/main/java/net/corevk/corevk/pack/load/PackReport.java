package net.corevk.corevk.pack.load;

import net.corevk.corevk.pack.option.PackOption;
import net.corevk.corevk.pack.program.ProgramSet;
import net.corevk.corevk.pack.source.ShaderProperties;

import net.corevk.corevk.CoreVK;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class PackReport {

	private static final String PREFIX = "[pack] ";

	
	private static final int SAMPLE = 8;

	private PackReport() {
	}

	
	public static void log(LoadedPack pack) {
		CoreVK.logger().info("{}{}", PREFIX, PackStats.tsvHeader());
		CoreVK.logger().info("{}{}", PREFIX, pack.stats().tsvLine(pack.packName()));
		detail(pack);
	}

	
	public static void couldNotRead(String name, Exception cause) {
		CoreVK.logger().warn("{}{} could not be read, so there is no report of it", PREFIX, name,
				cause);
	}

	private static void detail(LoadedPack pack) {
		PackStats stats = pack.stats();
		ProgramSet programs = pack.programs();

		CoreVK.logger().info("{}{}: {} in {} ms, {} files{}", PREFIX, pack.packName(),
				pack.fromZip() ? "zip" : "directory", pack.loadMillis(), stats.files(),
				pack.caseInsensitiveHits() == 0 ? "" : ", " + pack.caseInsensitiveHits() + " resolved ignoring case");

		CoreVK.logger().info("{}  files by extension {}", PREFIX, stats.filesByExtension());

		// Two counts, deliberately. The first is every file that carries a stage extension,
		// which is what the reference measurements counted. The second is how many of those
		// this engine can actually place, which is lower whenever a pack files programs under a
		// directory of its own or invents a name.
		CoreVK.logger().info("{}  stage files {}, shared bodies {}", PREFIX,
				stats.stageFiles(), stats.includeFiles());

		CoreVK.logger().info("{}  programs recognised {} across {}, by stage {}, {} distinct names", PREFIX,
				programs.count(), programs.countByDimension(), programs.countByStage(), programs.distinctNames());

		CoreVK.logger().info("{}  options {}: toggle {}, value {}, const {}, commented out {}, with a value list {}",
				PREFIX, pack.options().count(),
				pack.options().countByKind(PackOption.Kind.TOGGLE),
				pack.options().countByKind(PackOption.Kind.VALUE),
				pack.options().countByKind(PackOption.Kind.CONST),
				pack.options().disabledCount(), pack.options().withValueListCount());

		CoreVK.logger().info("{}  dimensions {}{}", PREFIX, pack.dimensions().names(),
				pack.dimensions().hasDimensionProperties() ? ", declared in dimension.properties" : "");

		ShaderProperties properties = pack.properties();
		if (properties.present()) {
			CoreVK.logger().info(
					"{}  shaders.properties: {} directives, {} continuations, {} profiles, {} custom uniforms, "
							+ "{} screen tokens, {} sliders, {} blend directives, {} programs switched off",
					PREFIX, properties.directiveCount(), properties.continuationCount(),
					properties.profiles().size(), properties.customUniformTypes().size(),
					properties.screenTokens().size(), properties.sliders().size(),
					properties.blendCount(), pack.disabledPrograms().size());

			logIfAny("  keys not read", properties.ignoredPrefixes());
		} else {
			CoreVK.logger().info("{}  no shaders.properties", PREFIX);
		}

		List<String> places = new ArrayList<>();
		places.add(ProgramSet.ROOT);
		places.addAll(pack.dimensions().names());

		for (String place : places) {
			// A dimension with nothing of its own is a dimension the pack does not touch, and
			// saying so for every one of them buries the ones that matter.
			if (pack.resolved().resolutions(place).isEmpty()) {
				continue;
			}

			CoreVK.logger().info("{}  {}: {} programs served directly, {} through a fallback, {} not served",
					PREFIX, place.isEmpty() ? "(root)" : place,
					pack.resolved().directCount(place), pack.resolved().inheritedCount(place),
					pack.resolved().unservedCount(place));
		}

		CoreVK.logger().info("{}  expansion of {} units: {}", PREFIX, pack.expandedUnits(), pack.expansion());
		if (!pack.expansion().clean()) {
			CoreVK.logger().warn("{}  expansion did not come out clean, see the counters above", PREFIX);
		}

		// One line per shape and not per program, because the file it is written in is shared: the
		// pack builds either way, and what is said is which of its own lines this engine read for it.
		pack.looseDirectives().forEach(loose -> CoreVK.logger().warn(
				"{}  directive read as the reference reads it: {}", PREFIX, loose));

		logIfAny("  ignored directories", programs.skippedDirectories());
		logIfAny("  unrecognised program names", programs.skippedNames());

		if (!pack.options().caseCollisions().isEmpty()) {
			CoreVK.logger().warn("{}  settings differing only by case: {}", PREFIX,
					pack.options().caseCollisions());
		}
	}

	private static void logIfAny(String label, Map<String, Integer> counts) {
		if (counts.isEmpty()) {
			return;
		}

		String sample = counts.entrySet().stream()
				.limit(SAMPLE)
				.map(entry -> entry.getKey() + " x" + entry.getValue())
				.reduce((a, b) -> a + ", " + b)
				.orElse("");

		CoreVK.logger().info("{}{} {}: {}{}", PREFIX, label, counts.size(), sample,
				counts.size() > SAMPLE ? ", ..." : "");
	}
}
