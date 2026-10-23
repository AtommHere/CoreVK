package net.corevk.corevk.pack.load;

import net.corevk.corevk.pack.option.OptionIndex;
import net.corevk.corevk.pack.option.SettingSet;
import net.corevk.corevk.pack.program.ProgramResolver;
import net.corevk.corevk.pack.program.ProgramSet;
import net.corevk.corevk.pack.source.DimensionSet;
import net.corevk.corevk.pack.source.ExpansionStats;
import net.corevk.corevk.pack.source.IncludeExpander;
import net.corevk.corevk.pack.source.ShaderPackSource;
import net.corevk.corevk.pack.source.ShaderProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

public final class PackLoader {

	public static final String DIRECTORY_NAME = "shaderpacks";

	
	private static final Comparator<String> BY_NAME =
			String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder());

	private PackLoader() {
	}

	
	public static ShaderProperties properties(Path packPath) throws IOException {
		try (ShaderPackSource source = ShaderPackSource.open(packPath)) {
			return ShaderProperties.parse(source);
		}
	}

	public static LoadedPack load(Path packPath) throws IOException {
		long start = System.nanoTime();

		try (ShaderPackSource source = ShaderPackSource.open(packPath)) {
			DimensionSet dimensions = DimensionSet.discover(source);
			ShaderProperties properties = ShaderProperties.parse(source);
			OptionIndex options = source.options();
			ProgramSet programs = ProgramSet.enumerate(source, dimensions);
			PackStats stats = PackStats.measure(source, options);

			// Every entry point is flattened, and the text is then thrown away. This stage is
			// judged on whether the graph resolves, not on what comes out of it, and holding
			// two hundred flattened units would cost a lot of memory to prove nothing.
			SettingSet settings = SettingSet.defaults();

			// The file has conditionals of its own, and they read the settings, so the toggles
			// can only be known once the settings are.
			Set<String> disabled = properties.switchedOff(settings.globalDefines(options), options);

			// Resolved as the pack SHIPS, with no toggle applied, and the empty set says so rather
			// than standing for one nobody worked out. This report runs before the machine's own
			// symbols are installed and against the pack's defaults rather than the player's
			// settings, so the answer it would give to a toggle is not the answer the chain gets:
			// a pack branching a program on DISTANT_HORIZONS reads it off here and on there. The
			// count above carries the same reserve and carried it before this line existed.
			ProgramResolver resolved = ProgramResolver.resolve(programs, dimensions, Set.of());

			// This walk is a diagnosis of a pack and not a step of a load: it is made the first
			// time a pack is read and never again for the same one, so what it costs is left out
			// of the load's clock rather than doubling a figure the log invites the two to be
			// compared on. It leaves nothing in the opening's memo either, which is what keeps
			// the note above true: a key here is a file of the pack and each of them comes up
			// once, so there would be nothing to find there and every unit to hold.
			IncludeExpander expander = IncludeExpander.forTheReport(source, settings);
			ExpansionStats expansion = ExpansionStats.NONE;
			int expanded = 0;
			for (ProgramSet.ProgramKey key : programs.keys()) {
				Path file = source.file(key.file()).orElse(null);
				if (file != null) {
					expansion = expansion.plus(expander.expand(file).stats());
					expanded++;
				}
			}

			return new LoadedPack(source.packName(), source.isZip(), dimensions, properties, options,
					programs, resolved, stats, expansion, expanded, expander.looseDirectives(),
					Set.copyOf(disabled), source.caseInsensitiveHits(),
					(System.nanoTime() - start) / 1_000_000L);
		}
	}

	
	public static Path directory(Path gameDirectory) {
		return gameDirectory.resolve(DIRECTORY_NAME);
	}

	
	public static List<Path> candidates(Path gameDirectory) throws IOException {
		Path directory = directory(gameDirectory);
		if (!Files.isDirectory(directory)) {
			return List.of();
		}

		try (Stream<Path> entries = Files.list(directory)) {
			List<Path> found = new ArrayList<>(entries.filter(PackLoader::looksLikeAPack).toList());
			found.sort(Comparator.comparing(path -> withoutFormatting(path.getFileName().toString()),
					BY_NAME));

			return List.copyOf(found);
		}
	}

	
	private static String withoutFormatting(String name) {
		StringBuilder kept = new StringBuilder(name.length());
		for (int i = 0; i < name.length(); i++) {
			if (name.charAt(i) == '§') {
				i++;
			} else {
				kept.append(name.charAt(i));
			}
		}

		return kept.toString();
	}

	
	public static boolean looksLikeAPack(Path entry) {
		if (Files.isDirectory(entry)) {
			return true;
		}

		Path name = entry.getFileName();

		return name != null && name.toString().toLowerCase(Locale.ROOT).endsWith(".zip");
	}
}
