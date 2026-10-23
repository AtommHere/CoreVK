package net.corevk.corevk.pack.target;

import net.corevk.corevk.pack.model.TargetName;
import net.corevk.corevk.pack.model.TargetSize;
import net.corevk.corevk.pack.model.ProgramNames;
import net.corevk.corevk.pack.program.ProgramResolver;
import net.corevk.corevk.pack.program.TerrainPass;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Stream;

public final class ChainPlan {

	public static final int MAX_ATTACHMENTS = 8;

	private static final int DEFERRED_RANK = ProgramNames.frameRank("deferred");

	private static final int PREPARE_RANK = ProgramNames.frameRank("prepare");

	private static final int BEGIN_RANK = ProgramNames.frameRank("begin");

	private static final String FINAL = "final";

	private static final String SEED_PROGRAM = "gbuffers_terrain";

	private final String place;

	private static final String CLOUD_PROGRAM = "gbuffers_clouds";

	private static final List<String> SKY_PROGRAMS =
			List.of("gbuffers_skybasic", "gbuffers_skytextured", CLOUD_PROGRAM);

	private static final Predicate<Families> NOT_EVERYWHERE = _ -> false;

	private static final List<NamedProgram> NAMED_PROGRAMS = Stream.concat(

			SKY_PROGRAMS.stream()
					.map(program -> new NamedProgram(program, CLOUD_PROGRAM.equals(program),
							NOT_EVERYWHERE)),
			Stream.of(

					new NamedProgram("gbuffers_entities", false, Families::entities),
					new NamedProgram("gbuffers_block", false, Families::entities),

					new NamedProgram("gbuffers_entities_translucent", true,
							Families::entities),
					new NamedProgram("gbuffers_block_translucent", true, Families::entities),

					new NamedProgram("gbuffers_block", true, NOT_EVERYWHERE),

					new NamedProgram("gbuffers_block_translucent", false, NOT_EVERYWHERE),
					new NamedProgram("gbuffers_entities_translucent", false, NOT_EVERYWHERE),

					new NamedProgram("gbuffers_entities", true, NOT_EVERYWHERE),

					new NamedProgram("gbuffers_spidereyes", true, NOT_EVERYWHERE),

					new NamedProgram("gbuffers_damagedblock", false, NOT_EVERYWHERE),
					new NamedProgram("gbuffers_damagedblock", true, NOT_EVERYWHERE),

					new NamedProgram("gbuffers_line", true, NOT_EVERYWHERE),

					new NamedProgram("gbuffers_hand", false, NOT_EVERYWHERE),
					new NamedProgram("gbuffers_hand_water", true, NOT_EVERYWHERE),

					new NamedProgram("gbuffers_armor_glint", false, NOT_EVERYWHERE),
					new NamedProgram("gbuffers_armor_glint", true, NOT_EVERYWHERE),

					new NamedProgram("gbuffers_weather", true, NOT_EVERYWHERE),

					new NamedProgram("gbuffers_particles", false, Families::particles),
					new NamedProgram("gbuffers_particles_translucent", true,
							Families::particles),

					new NamedProgram("dh_terrain", false, NOT_EVERYWHERE),
					new NamedProgram("dh_water", true, NOT_EVERYWHERE)))
			.toList();

	private record NamedProgram(String program, boolean afterDeferred,
			Predicate<Families> everywhere) {
	}

	private final List<Pass> passes;
	private final int beginEnd;
	private final int prepareEnd;
	private final int deferredEnd;
	private final Pass last;

	private final Attachment present;

	private final Seed seed;

	private final Map<Key, Pass> attachments;

	private final Map<TerrainPass, Key> terrainKeys;

	private final Map<String, Key> skyKeys;

	private final List<Integer> swapBack;
	private final List<String> refusals;
	private final List<String> notes;
	private final List<String> history;

	private ChainPlan(String place, List<Pass> passes, Pass last, Attachment present, Seed seed,
			Map<Key, Pass> attachments, Map<TerrainPass, Key> terrainKeys, Map<String, Key> skyKeys,
			List<Integer> swapBack, List<String> refusals, List<String> notes,
			List<String> history) {
		this.place = place;
		this.passes = List.copyOf(passes);
		this.beginEnd = pastRank(this.passes, BEGIN_RANK);
		this.prepareEnd = pastRank(this.passes, PREPARE_RANK);
		this.deferredEnd = pastDeferred(this.passes);
		this.last = last;
		this.present = present;
		this.seed = seed;
		this.attachments = Map.copyOf(attachments);
		this.terrainKeys = Map.copyOf(terrainKeys);
		this.skyKeys = Map.copyOf(skyKeys);
		this.swapBack = List.copyOf(swapBack);
		this.refusals = List.copyOf(refusals);
		this.notes = List.copyOf(notes);
		this.history = List.copyOf(history);
	}

	public record Attachment(int target, TargetSchedule.Side side) {
	}

	public record Pass(String program, List<Attachment> attachments, TargetSize size,
			boolean inferred) {

		public Pass {
			attachments = List.copyOf(attachments);
		}

		public List<Integer> targets() {
			return this.attachments.stream().map(Attachment::target).toList();
		}

		public int frameRank() {
			return ProgramNames.frameRank(ProgramNames.familyOf(this.program));
		}

		public boolean deferred() {
			return frameRank() == ProgramNames.frameRank("deferred");
		}
	}

	public record Key(String servedBy, boolean afterDeferred) {
	}

	public record Seed(int target, TargetSchedule.Side side, String from, int at) {
	}

	public record Families(boolean terrain, boolean entities, boolean particles, boolean seed) {

		public static final Families DEFAULT = new Families(true, true, true, true);
	}

	public static ChainPlan of(TargetPlan plan, ProgramResolver resolver) {
		return of(plan, resolver, List.of(), Families.DEFAULT);
	}

	public static ChainPlan of(TargetPlan plan, ProgramResolver resolver, List<String> refused,
			Families families) {
		List<Pass> passes = new ArrayList<>();
		List<String> refusals = new ArrayList<>(refused);
		List<String> notes = new ArrayList<>();
		List<String> history = new ArrayList<>();
		Pass last = null;

		for (String program : plan.running()) {
			if (program.equals(FINAL)) {
				last = new Pass(program, List.of(), TargetSize.ofScreen(), false);
				continue;
			}

			Pass pass = passOf(plan, program, refusals);
			if (pass != null) {
				passes.add(pass);
			}
		}

		Attachment present = last != null ? null : new Attachment(0,
				plan.schedule().flippedAtEnd().contains(0)
						? TargetSchedule.Side.ALT
						: TargetSchedule.Side.MAIN);
		if (present != null) {
			notes.add("this place runs no final, so " + TargetName.canonical(present.target())
					+ " is brought to the screen as it stands, which is what CoreVK does");
		}

		Map<Key, Pass> attachments = new LinkedHashMap<>();
		Map<TerrainPass, Key> terrainKeys = terrainKeysOf(plan, resolver, notes, attachments);
		Map<NamedProgram, Key> namedKeys = namedKeysOf(plan, resolver, notes, attachments);
		Map<String, Key> skyKeys = new LinkedHashMap<>();
		namedKeys.forEach((named, key) -> {
			if (SKY_PROGRAMS.contains(named.program())) {
				skyKeys.put(named.program(), key);
			}
		});

		Seed seed = seedOf(plan, resolver, notes);

		Seed painted = families.seed() ? seed : null;

		Map<Key, Pass> world = new LinkedHashMap<>();
		if (families.terrain()) {
			terrainKeys.values().forEach(key -> world.put(key, attachments.get(key)));
		}

		for (NamedProgram named : NAMED_PROGRAMS) {
			Key key = named.everywhere().test(families) ? namedKeys.get(named) : null;
			Pass drawing = key == null ? null : attachments.get(key);
			if (drawing != null && drawing.size().equals(TargetSize.ofScreen())) {
				world.put(key, drawing);
			}
		}

		verdicts(plan, painted, world, passes, last, notes, history);

		Set<Integer> back = new TreeSet<>(plan.schedule().flippedAtEnd());
		back.retainAll(plan.persistent());

		Map<Key, Pass> answered = new LinkedHashMap<>();
		attachments.forEach((key, pass) -> {
			if (pass != null) {
				answered.put(key, pass);
			}
		});

		return new ChainPlan(plan.place(), passes, last, present, seed, answered, terrainKeys, skyKeys,
				List.copyOf(back), refusals, notes, history);
	}

	private static Map<TerrainPass, Key> terrainKeysOf(TargetPlan plan, ProgramResolver resolver,
			List<String> notes, Map<Key, Pass> into) {
		Map<TerrainPass, Key> keys = new EnumMap<>(TerrainPass.class);
		for (TerrainPass pass : TerrainPass.values()) {

			if (pass.shadow()) {
				continue;
			}

			Optional<String> served = resolver.lookup(plan.place(), pass.program())
					.map(ProgramResolver.Resolution::servedBy);
			if (served.isEmpty()) {
				continue;
			}

			Key key = new Key(served.get(), pass.afterDeferred());
			if (answer(plan, key, notes, into) != null) {
				keys.put(pass, key);
			}
		}

		return keys;
	}

	private static Pass answer(TargetPlan plan, Key key, List<String> notes, Map<Key, Pass> into) {
		if (!into.containsKey(key)) {
			into.put(key, geometryOf(plan, key.servedBy(), notes, key.afterDeferred()));
		}

		return into.get(key);
	}

	private static Map<NamedProgram, Key> namedKeysOf(TargetPlan plan, ProgramResolver resolver,
			List<String> notes, Map<Key, Pass> into) {
		Map<NamedProgram, Key> keys = new LinkedHashMap<>();

		for (NamedProgram named : NAMED_PROGRAMS) {
			Optional<String> served = resolver.lookup(plan.place(), named.program())
					.map(ProgramResolver.Resolution::servedBy);
			if (served.isEmpty()) {
				continue;
			}

			Key key = new Key(served.get(), named.afterDeferred());
			if (answer(plan, key, notes, into) != null) {
				keys.put(named, key);
			}
		}

		return keys;
	}

	private static Pass passOf(TargetPlan plan, String program, List<String> refusals) {
		List<Integer> writes = plan.writes(program);
		if (writes.isEmpty()) {

			refusals.add(program + " is meant to run and this plan knows nothing it writes, which "
					+ "is what an entry point that could not be read looks like");
			return null;
		}

		return attachmentsOf(plan, program, writes, refusals);
	}

	private static Pass geometryOf(TargetPlan plan, String program, List<String> notes,
			boolean afterDeferred) {
		List<Integer> writes = plan.writes(program);
		if (writes.isEmpty()) {
			return null;
		}

		Optional<TargetSchedule.Bound> bound = afterDeferred
				? plan.schedule().stepAfterDeferred(program)
				: plan.schedule().step(program);

		return attachmentsOf(plan, program, writes, notes, bound);
	}

	private static Pass attachmentsOf(TargetPlan plan, String program, List<Integer> writes,
			List<String> problems) {
		return attachmentsOf(plan, program, writes, problems, plan.schedule().step(program));
	}

	private static Pass attachmentsOf(TargetPlan plan, String program, List<Integer> writes,
			List<String> problems, Optional<TargetSchedule.Bound> bound) {
		if (writes.size() > MAX_ATTACHMENTS) {
			problems.add(program + " writes " + writes.size() + " targets and one pass carries at "
					+ "most " + MAX_ATTACHMENTS + ": " + writes);
			return null;
		}

		if (new LinkedHashSet<>(writes).size() != writes.size()) {
			problems.add(program + " names the same target twice in its draw buffers, " + writes
					+ ", and one image cannot be two attachments of one pass");
			return null;
		}

		if (bound.isEmpty()) {
			problems.add(program + " writes " + writes + " and the schedule holds no step for it");
			return null;
		}

		List<Attachment> attachments = new ArrayList<>();
		TargetSize size = null;
		for (int index : writes) {
			if (!plan.allocated().contains(index)) {
				problems.add(program + " writes " + TargetName.canonical(index)
						+ ", which nothing of this place allocates");
				return null;
			}

			TargetSize here = plan.directives().size(index);
			if (size == null) {
				size = here;
			} else if (!size.equals(here)) {

				problems.add(program + " writes targets of two sizes, " + describe(size) + " and "
						+ describe(here) + " for " + TargetName.canonical(index));
				return null;
			}

			attachments.add(new Attachment(index, bound.get().write(index)));
		}

		return new Pass(program, attachments, size, plan.inferredWrites().contains(program));
	}

	private static Seed seedOf(TargetPlan plan, ProgramResolver resolver, List<String> notes) {
		Optional<ProgramResolver.Resolution> resolution = resolver.lookup(plan.place(), SEED_PROGRAM);
		if (resolution.isEmpty()) {
			notes.add("nothing here serves " + SEED_PROGRAM + ", so the game's own frame is painted "
					+ "nowhere and every program of the chain reads clear colours");
			return null;
		}

		String from = resolution.get().servedBy();
		List<Integer> writes = plan.writes(from);
		if (writes.isEmpty()) {
			notes.add(from + " serves " + SEED_PROGRAM + " and declares no draw buffer, so there is "
					+ "nowhere to paint the game's own frame");
			return null;
		}

		int target = writes.get(0);
		if (!plan.allocated().contains(target)) {
			notes.add(from + " serves " + SEED_PROGRAM + " and writes " + TargetName.canonical(target)
					+ ", which nothing of this place allocates, so the game's own frame is painted "
					+ "nowhere");
			return null;
		}

		TargetSchedule.Side side = plan.schedule().step(from)
				.map(step -> step.write(target))
				.orElse(TargetSchedule.Side.MAIN);

		return new Seed(target, side, from, plan.geometryAt());
	}

	private static void verdicts(TargetPlan plan, Seed seed, Map<Key, Pass> world,
			List<Pass> passes, Pass last, List<String> notes, List<String> history) {
		List<Pass> ordered = new ArrayList<>(passes);
		if (last != null) {
			ordered.add(last);
		}

		Set<Integer> undrawn = new TreeSet<>();
		plan.schedule().steps().stream()
				.filter(step -> !step.fullscreen())
				.forEach(step -> undrawn.addAll(step.writes()));

		int beforeWorld = pastRank(ordered, PREPARE_RANK);
		int afterDeferred = pastDeferred(ordered);
		Map<Integer, Set<Pass>> drawn = new LinkedHashMap<>();
		world.forEach((key, drawing) -> {
			drawn.computeIfAbsent(key.afterDeferred() ? afterDeferred : beforeWorld,
					_ -> new LinkedHashSet<>()).add(drawing);
			undrawn.removeAll(drawing.targets());
		});

		Set<Attachment> filled = new LinkedHashSet<>();
		Set<Attachment> told = new LinkedHashSet<>();
		for (int at = 0; at < ordered.size(); at++) {
			if (seed != null && at == seed.at()) {
				filled.add(new Attachment(seed.target(), seed.side()));
			}

			for (Pass drawing : drawn.getOrDefault(at, Set.of())) {
				filled.addAll(drawing.attachments());
			}

			Pass pass = ordered.get(at);
			Optional<TargetSchedule.Bound> bound = plan.schedule().step(pass.program());

			for (int index : plan.samples(pass.program())) {
				Attachment half = new Attachment(index, bound
						.map(step -> step.read(index))
						.orElse(TargetSchedule.Side.MAIN));
				if (filled.contains(half) || !told.add(half)) {
					continue;
				}

				Verdict said = verdict(plan, undrawn, ordered, drawn, at, half, pass.program());
				(said.temporal() ? history : notes).add(said.text());
			}

			filled.addAll(pass.attachments());
		}
	}

	private static int pastDeferred(List<Pass> ordered) {
		return pastRank(ordered, DEFERRED_RANK);
	}

	private static int pastRank(List<Pass> ordered, int rank) {
		int at = 0;
		while (at < ordered.size() && ordered.get(at).frameRank() <= rank) {
			at++;
		}

		return at;
	}

	private static Verdict verdict(TargetPlan plan, Set<Integer> undrawn, List<Pass> ordered,
			Map<Integer, Set<Pass>> drawn, int at, Attachment half, String reader) {
		boolean kept = plan.persistent().contains(half.target());
		String name = TargetName.canonical(half.target());
		String clear = ", so " + reader + " reads what the clear left there";
		String before = ", so " + reader + " reads the frame before, and the clear colour on the "
				+ "first one";

		String later = writtenLater(ordered, drawn, at, half);
		if (later != null) {

			return new Verdict(name + " is not written until " + later + ", later in the same frame"
					+ (kept ? before : clear), kept);
		}

		String off = disabledWriter(plan, half.target());
		if (off != null) {
			return new Verdict(name + " is written by " + off + ", which this place does not run"
					+ clear, false);
		}

		if (undrawn.contains(half.target())) {

			return new Verdict(name + " is written by geometry, none of which this engine draws into "
					+ "the pack's targets" + clear, false);
		}

		return kept
				? new Verdict("nothing writes the half of " + name + " that " + reader + " reads, and "
						+ "the pack keeps that target between frames" + before, true)
				: new Verdict(name + " is written by no program of this place, so it holds its clear "
						+ "colour for ever, and " + reader + " reads that", false);
	}

	private record Verdict(String text, boolean temporal) {
	}

	private static String writtenLater(List<Pass> ordered, Map<Integer, Set<Pass>> drawn, int at,
			Attachment half) {
		for (int next = at + 1; next <= ordered.size(); next++) {

			for (Pass drawing : drawn.getOrDefault(next, Set.of())) {
				if (drawing.attachments().contains(half)) {
					return drawing.program();
				}
			}

			if (next < ordered.size() && ordered.get(next).attachments().contains(half)) {
				return ordered.get(next).program();
			}
		}

		return null;
	}

	private static String disabledWriter(TargetPlan plan, int index) {
		List<String> writers = plan.disabled().entrySet().stream()
				.filter(entry -> plan.writes(entry.getKey()).contains(index))
				.map(entry -> entry.getKey() + " (" + entry.getValue() + ")")
				.toList();

		return writers.isEmpty() ? null : String.join(", ", writers);
	}

	private static String describe(TargetSize size) {
		return size.relative()
				? String.format(Locale.ROOT, "%.3f by %.3f of the screen", size.width(), size.height())
				: (int) size.width() + " by " + (int) size.height() + " pixels";
	}

	public String place() {
		return this.place;
	}

	public List<Pass> passes() {
		return this.passes;
	}

	public int deferredEnd() {
		return this.deferredEnd;
	}

	public int prepareEnd() {
		return this.prepareEnd;
	}

	public int beginEnd() {
		return this.beginEnd;
	}

	public Optional<Pass> last() {
		return Optional.ofNullable(this.last);
	}

	public Optional<Attachment> present() {
		return Optional.ofNullable(this.present);
	}

	public Optional<Pass> geometry(TerrainPass pass) {
		return Optional.ofNullable(this.terrainKeys.get(pass)).map(this.attachments::get);
	}

	public Optional<Pass> geometryOf(String servedBy, boolean afterDeferred) {
		return Optional.ofNullable(this.attachments.get(new Key(servedBy, afterDeferred)));
	}

	public Optional<Pass> sky(String program) {
		return Optional.ofNullable(this.skyKeys.get(program)).map(this.attachments::get);
	}

	public Optional<Seed> seed() {
		return Optional.ofNullable(this.seed);
	}

	public static boolean leadsWithSeed(Seed seed, Pass pass) {
		if (seed == null || pass == null || pass.attachments().isEmpty()) {
			return false;
		}

		Attachment first = pass.attachments().get(0);

		return seed.target() == first.target() && seed.side() == first.side();
	}

	public List<Integer> swapBack() {
		return this.swapBack;
	}

	public List<String> refusals() {
		return this.refusals;
	}

	public List<String> notes() {
		return this.notes;
	}

	public List<String> history() {
		return this.history;
	}
}
