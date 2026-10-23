package net.corevk.corevk.pack.program;

import net.corevk.corevk.pack.model.ProgramNames;
import net.corevk.corevk.pack.model.ProgramStage;
import net.corevk.corevk.pack.source.DimensionSet;
import net.corevk.corevk.pack.source.ShaderPackSource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.ToIntFunction;

public final class ProgramSet {

	
	public static final String ROOT = "";

	private final List<ProgramKey> keys;
	private final Map<String, Integer> skippedNames;
	private final Map<String, Integer> skippedDirectories;

	private ProgramSet(List<ProgramKey> keys, Map<String, Integer> skippedNames,
			Map<String, Integer> skippedDirectories) {
		this.keys = List.copyOf(keys);
		this.skippedNames = Map.copyOf(skippedNames);
		this.skippedDirectories = Map.copyOf(skippedDirectories);
	}

	public static ProgramSet enumerate(ShaderPackSource source, DimensionSet dimensions) throws IOException {
		List<ProgramKey> keys = new ArrayList<>();
		Map<String, Integer> skippedNames = new LinkedHashMap<>();
		Map<String, Integer> skippedDirectories = new LinkedHashMap<>();

		for (Path file : source.sourceFiles()) {
			String relative = source.rel(file);
			int slash = relative.lastIndexOf('/');
			String directory = slash < 0 ? ROOT : relative.substring(0, slash);
			String fileName = slash < 0 ? relative : relative.substring(slash + 1);

			ProgramStage stage = ProgramStage.ofFile(fileName).orElse(null);
			if (stage == null) {
				// A shared body, not an entry point. Every .glsl lands here.
				continue;
			}

			if (!directory.equals(ROOT) && !dimensions.isDimensionDirectory(directory)) {
				skippedDirectories.merge(directory, 1, Integer::sum);
				continue;
			}

			int dot = fileName.lastIndexOf('.');
			String baseName = dot < 0 ? fileName : fileName.substring(0, dot);
			ProgramNames.ProgramName name = ProgramNames.parse(baseName).orElse(null);
			if (name == null) {
				skippedNames.merge(baseName, 1, Integer::sum);
				continue;
			}

			keys.add(new ProgramKey(directory, name, stage, relative));
		}

		return new ProgramSet(keys, skippedNames, skippedDirectories);
	}

	public List<ProgramKey> keys() {
		return this.keys;
	}

	public int count() {
		return this.keys.size();
	}

	
	public Map<String, Integer> countByDimension() {
		Map<String, Integer> counts = new TreeMap<>();
		for (ProgramKey key : this.keys) {
			counts.merge(key.dimension().isEmpty() ? "(root)" : key.dimension(), 1, Integer::sum);
		}

		return counts;
	}

	public Map<String, Integer> countByStage() {
		Map<String, Integer> counts = new TreeMap<>();
		for (ProgramKey key : this.keys) {
			counts.merge(key.stage().extension(), 1, Integer::sum);
		}

		return counts;
	}

	public int distinctNames() {
		return (int) this.keys.stream().map(key -> key.name().baseName()).distinct().count();
	}

	
	public Map<String, Integer> skippedNames() {
		return new TreeMap<>(this.skippedNames);
	}

	
	public Map<String, Integer> skippedDirectories() {
		return new TreeMap<>(this.skippedDirectories);
	}

	
	public List<ProgramKey> fragmentsOf(String place) {
		return this.keys.stream()
				.filter(key -> key.stage() == ProgramStage.FRAGMENT)
				.filter(key -> key.dimension().equals(place))
				.toList();
	}

	
	public List<ProgramKey> computesOf(String place) {
		return this.keys.stream()
				.filter(key -> key.stage() == ProgramStage.COMPUTE)
				.filter(key -> key.dimension().equals(place))
				.toList();
	}

	
	public static List<ProgramKey> sorted(List<ProgramKey> entries, ToIntFunction<String> rank) {
		return entries.stream()
				.sorted(Comparator
						.comparingInt((ProgramKey key) -> rank.applyAsInt(key.name().family()))
						.thenComparingInt(key -> key.name().slot())
						.thenComparing(ProgramKey::file))
				.toList();
	}

	
	public record ProgramKey(String dimension, ProgramNames.ProgramName name, ProgramStage stage, String file) {
	}
}
