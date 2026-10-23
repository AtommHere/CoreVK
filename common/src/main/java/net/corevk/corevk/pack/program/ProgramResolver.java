package net.corevk.corevk.pack.program;

import net.corevk.corevk.pack.model.ProgramFallbacks;
import net.corevk.corevk.pack.model.ProgramStage;
import net.corevk.corevk.pack.source.DimensionSet;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class ProgramResolver {

	private final Map<String, List<Resolution>> byDimension;

	private ProgramResolver(Map<String, List<Resolution>> byDimension) {
		this.byDimension = Map.copyOf(byDimension);
	}

	public static ProgramResolver resolve(ProgramSet programs, DimensionSet dimensions,
			Set<String> switchedOff) {

		Map<String, Map<String, String>> shipped = new LinkedHashMap<>();
		for (ProgramSet.ProgramKey key : programs.keys()) {
			if (key.stage() == ProgramStage.FRAGMENT && !switchedOff.contains(pathOf(key))) {
				shipped.computeIfAbsent(key.dimension(), _ -> new LinkedHashMap<>())
						.put(key.name().baseName(), key.file());
			}
		}

		List<String> places = new ArrayList<>();
		places.add(ProgramSet.ROOT);
		places.addAll(dimensions.names());

		Map<String, List<Resolution>> resolved = new LinkedHashMap<>();
		for (String place : places) {
			Map<String, String> here = shipped.getOrDefault(place, Map.of());
			List<Resolution> resolutions = new ArrayList<>();

			for (String program : ProgramFallbacks.names()) {
				List<String> chain = ProgramFallbacks.chain(program);
				for (int depth = 0; depth < chain.size(); depth++) {
					String candidate = chain.get(depth);
					String file = here.get(candidate);
					if (file != null) {
						resolutions.add(new Resolution(program, candidate, file, depth));
						break;
					}
				}
			}

			resolved.put(place, List.copyOf(resolutions));
		}

		return new ProgramResolver(resolved);
	}

	private static String pathOf(ProgramSet.ProgramKey key) {
		String file = key.file();
		int dot = file.lastIndexOf('.');
		return dot < 0 ? file : file.substring(0, dot);
	}

	public List<Resolution> resolutions(String dimension) {
		return this.byDimension.getOrDefault(dimension, List.of());
	}

	public Optional<Resolution> lookup(String dimension, String program) {
		return resolutions(dimension).stream()
				.filter(resolution -> resolution.requested().equals(program))
				.findFirst();
	}

	public int directCount(String dimension) {
		return (int) resolutions(dimension).stream().filter(Resolution::direct).count();
	}

	public int inheritedCount(String dimension) {
		return (int) resolutions(dimension).stream().filter(resolution -> !resolution.direct()).count();
	}

	public int unservedCount(String dimension) {
		return ProgramFallbacks.names().size() - resolutions(dimension).size();
	}

	public record Resolution(String requested, String servedBy, String file, int depth) {

		public boolean direct() {
			return this.depth == 0;
		}
	}
}
