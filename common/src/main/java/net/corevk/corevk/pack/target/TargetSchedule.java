package net.corevk.corevk.pack.target;

import net.corevk.corevk.pack.model.TargetName;
import net.corevk.corevk.pack.model.ProgramNames;
import net.corevk.corevk.pack.source.ShaderProperties;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

public final class TargetSchedule {

	private static final List<String> PRE_STAGES =
			List.of("begin", "prepare", "deferred", "composite");

	private static final String PRE = "_pre";

	private final List<Bound> steps;
	private final Set<Integer> doubled;
	private final Set<Integer> flippedAtEnd;
	private final Set<Integer> afterDeferred;
	private final Map<String, Map<Integer, Boolean>> forced;
	private final Map<String, Set<Integer>> passingHalves;

	private TargetSchedule(List<Bound> steps, Set<Integer> doubled, Set<Integer> flippedAtEnd,
			Set<Integer> afterDeferred, Map<String, Map<Integer, Boolean>> forced,
			Map<String, Set<Integer>> passingHalves) {
		this.steps = List.copyOf(steps);

		this.doubled = sortedCopy(doubled);
		this.flippedAtEnd = sortedCopy(flippedAtEnd);
		this.afterDeferred = sortedCopy(afterDeferred);

		Map<String, Map<Integer, Boolean>> copied = new LinkedHashMap<>();
		forced.forEach((program, indices) -> copied.put(program, Map.copyOf(indices)));
		this.forced = Map.copyOf(copied);

		Map<String, Set<Integer>> halves = new LinkedHashMap<>();
		passingHalves.forEach((program, indices) -> halves.put(program, sortedCopy(indices)));
		this.passingHalves = Map.copyOf(halves);
	}

	public enum Side { MAIN, ALT }

	public record Step(String program, List<Integer> writes, boolean fullscreen) {
	}

	public record Bound(String program, List<Integer> writes, boolean fullscreen,
			Set<Integer> readsAlt, Set<Integer> writesAlt) {

		public Side read(int index) {
			return this.readsAlt.contains(index) ? Side.ALT : Side.MAIN;
		}

		public Side write(int index) {
			return this.writesAlt.contains(index) ? Side.ALT : Side.MAIN;
		}
	}

	public static TargetSchedule of(List<Step> steps, List<ShaderProperties.FlipDirective> explicit) {
		return of(steps, explicit, List.of());
	}

	public static TargetSchedule of(List<Step> steps, List<ShaderProperties.FlipDirective> explicit,
			List<String> passing) {
		Map<String, Map<Integer, Boolean>> forced = byProgram(explicit);
		Set<Integer> flipped = new LinkedHashSet<>();
		Set<Integer> doubled = new TreeSet<>();
		List<Bound> bound = new ArrayList<>();
		Map<String, Set<Integer>> halves = new LinkedHashMap<>();
		Set<Integer> afterDeferred = null;
		int deferredRank = ProgramNames.frameRank("deferred");
		int opened = 0;

		for (Item item : merge(steps, passing)) {
			int reached =
					ProgramNames.frameRank(ProgramNames.familyOf(TargetName.bareName(item.program())));

			if (afterDeferred == null && reached > deferredRank) {
				opened = openStages(opened, deferredRank, forced, flipped, doubled);
				afterDeferred = sortedCopy(flipped);
			}

			opened = openStages(opened, reached, forced, flipped, doubled);

			if (item.step() == null) {
				halves.put(TargetName.bareName(item.program()), sortedCopy(flipped));
				continue;
			}

			Step step = item.step();
			Set<Integer> readsAlt = sortedCopy(flipped);
			Set<Integer> writesAlt = new TreeSet<>();

			for (int index : step.writes()) {

				if (step.fullscreen() != flipped.contains(index)) {
					writesAlt.add(index);
				}
			}

			bound.add(new Bound(step.program(), List.copyOf(step.writes()), step.fullscreen(),
					readsAlt, sortedCopy(writesAlt)));

			if (!step.fullscreen()) {
				continue;
			}

			doubled.addAll(step.writes());

			Map<Integer, Boolean> here =
					forced.getOrDefault(TargetName.bareName(step.program()), Map.of());
			for (int index : step.writes()) {
				if (!Boolean.FALSE.equals(here.get(index))) {
					flip(flipped, index);
				}
			}

			here.forEach((index, shouldFlip) -> {
				if (shouldFlip) {
					flip(flipped, index);

					doubled.add(index);
				}
			});
		}

		if (afterDeferred == null) {
			opened = openStages(opened, deferredRank, forced, flipped, doubled);
			afterDeferred = sortedCopy(flipped);
		}

		openStages(opened, Integer.MAX_VALUE, forced, flipped, doubled);

		return new TargetSchedule(bound, doubled, flipped, afterDeferred, forced, halves);
	}

	private record Item(String program, Step step) {
	}

	private static List<Item> merge(List<Step> steps, List<String> passing) {
		if (passing.isEmpty()) {
			return steps.stream().map(step -> new Item(step.program(), step)).toList();
		}

		Comparator<String> order = ProgramNames.frameOrder();
		List<Item> items = new ArrayList<>();
		int next = 0;
		for (Step step : steps) {
			String against = TargetName.bareName(step.program());
			while (next < passing.size()
					&& order.compare(TargetName.bareName(passing.get(next)), against) < 0) {
				items.add(new Item(passing.get(next++), null));
			}

			items.add(new Item(step.program(), step));
		}

		while (next < passing.size()) {
			items.add(new Item(passing.get(next++), null));
		}

		return List.copyOf(items);
	}

	private static int openStages(int opened, int reached,
			Map<String, Map<Integer, Boolean>> forced, Set<Integer> flipped, Set<Integer> doubled) {
		int next = opened;
		while (next < PRE_STAGES.size() && ProgramNames.frameRank(PRE_STAGES.get(next)) <= reached) {
			forced.getOrDefault(PRE_STAGES.get(next) + PRE, Map.of()).forEach((index, shouldFlip) -> {
				if (shouldFlip) {
					flip(flipped, index);

					doubled.add(index);
				}
			});

			next++;
		}

		return next;
	}

	public List<Bound> steps() {
		return this.steps;
	}

	public Optional<Bound> step(String program) {
		String wanted = TargetName.bareName(program);

		return this.steps.stream().filter(step -> TargetName.bareName(step.program()).equals(wanted))
				.findFirst();
	}

	public Optional<Bound> passing(String program) {
		return Optional.ofNullable(this.passingHalves.get(TargetName.bareName(program)))
				.map(readsAlt -> new Bound(program, List.of(), true, readsAlt, Set.of()));
	}

	public Optional<Bound> stepAfterDeferred(String program) {
		return step(program)
				.filter(step -> !step.fullscreen())
				.map(step -> new Bound(step.program(), step.writes(), false, this.afterDeferred,
						within(this.afterDeferred, step.writes())));
	}

	private static Set<Integer> within(Set<Integer> flipped, List<Integer> writes) {
		Set<Integer> found = new TreeSet<>();
		for (int index : writes) {
			if (flipped.contains(index)) {
				found.add(index);
			}
		}

		return sortedCopy(found);
	}

	public Set<Integer> doubled() {
		return this.doubled;
	}

	public Set<Integer> flippedAtEnd() {
		return this.flippedAtEnd;
	}

	public Set<Integer> doubledFor(Set<String> programs) {
		Set<String> wanted = new LinkedHashSet<>();
		programs.forEach(program -> wanted.add(TargetName.bareName(program)));

		Set<Integer> found = new TreeSet<>();
		for (Bound step : this.steps) {
			String name = TargetName.bareName(step.program());
			if (!step.fullscreen() || !wanted.contains(name)) {
				continue;
			}

			found.addAll(step.writes());
			this.forced.getOrDefault(name, Map.of()).forEach((index, shouldFlip) -> {
				if (shouldFlip) {
					found.add(index);
				}
			});
		}

		return sortedCopy(found);
	}

	private static Set<Integer> sortedCopy(Set<Integer> indices) {
		return Collections.unmodifiableSet(new TreeSet<>(indices));
	}

	private static void flip(Set<Integer> flipped, int index) {
		if (!flipped.remove(index)) {
			flipped.add(index);
		}
	}

	private static Map<String, Map<Integer, Boolean>> byProgram(
			List<ShaderProperties.FlipDirective> explicit) {
		Map<String, Map<Integer, Boolean>> forced = new LinkedHashMap<>();

		for (ShaderProperties.FlipDirective directive : explicit) {
			TargetName.index(directive.buffer()).ifPresent(index ->
					forced.computeIfAbsent(TargetName.bareName(directive.program()),
							_ -> new LinkedHashMap<>())
							.put(index, directive.value()));
		}

		return forced;
	}
}
