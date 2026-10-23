package net.corevk.corevk.pack.target;

import net.corevk.corevk.pack.model.TargetFormat;
import net.corevk.corevk.pack.model.TargetName;
import net.corevk.corevk.pack.model.TargetSize;
import net.corevk.corevk.pack.model.TextureStage;
import net.corevk.corevk.pack.option.OptionIndex;
import net.corevk.corevk.pack.option.SettingSet;
import net.corevk.corevk.pack.model.BlendMode;
import net.corevk.corevk.pack.program.ChainFilter;
import net.corevk.corevk.pack.model.ProgramNames;
import net.corevk.corevk.pack.program.ProgramSet;
import net.corevk.corevk.pack.model.ProgramStage;
import net.corevk.corevk.pack.source.BlockComments;
import net.corevk.corevk.pack.source.DimensionSet;
import net.corevk.corevk.pack.source.IncludeExpander;
import net.corevk.corevk.pack.source.ShaderPackSource;
import net.corevk.corevk.pack.source.ShaderProperties;
import net.corevk.corevk.pack.texture.CustomImages;
import net.corevk.corevk.pack.texture.PackTextures;

import net.corevk.corevk.pack.source.IncludeExpander.ExpandedUnit;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class TargetPlan {

	private static final Pattern SAMPLER = Pattern.compile(
			"^\\s*(?:layout\\s*\\([^)]*\\)\\s*)?uniform\\s+(?:layout\\s*\\([^)]*\\)\\s*)?"
					+ "(?:(?:lowp|mediump|highp)\\s+)?([iu]?sampler\\w*)\\s+([^;]*);.*$");

	private static final String IMAGE_QUALIFIER =
			"(?:layout\\s*\\([^)]*\\)\\s*|(?:coherent|volatile|restrict|writeonly)\\s+)";

	private static final Pattern IMAGE = Pattern.compile(
			"^\\s*(?:" + IMAGE_QUALIFIER + ")*uniform\\s+(?:" + IMAGE_QUALIFIER
					+ "|(?:lowp|mediump|highp)\\s+)*([iu]?image\\w*)\\s+([^;]*);.*$");
	private static final Pattern NAME_ALIAS = Pattern.compile(
			"^\\s*#\\s*define\\s+([A-Za-z_]\\w*)\\s+([A-Za-z_]\\w*)(?:\\s*(?://.*)?)?$");
	private static final Pattern NAME_UNDEF = Pattern.compile("^\\s*#\\s*undef\\s+([A-Za-z_]\\w*)\\s*$");

	private static final String FINAL = "final";

	public static final String LEFT_OUT = "passes=";

	public static final String UNBINDABLE = "sampler this backend cannot bind";

	private static final int GEOMETRY_RANK = ProgramNames.GEOMETRY_RANK;

	private final String packName;
	private final String dimension;
	private final String place;
	private final TargetDirectives directives;
	private final TargetSchedule schedule;
	private final Set<Integer> written;
	private final Set<Integer> stored;
	private final Set<Integer> sampled;
	private final Set<Integer> allocated;
	private final int shadowCeiling;
	private final Set<Integer> shadowAllocated;
	private final List<Integer> ordered;
	private final Set<Integer> persistent;
	private final List<String> running;
	private final Map<String, String> disabled;
	private final Map<String, List<Integer>> writes;

	private final Map<String, BlendMode> blend;

	private final Map<String, List<BlendMode>> blendPerTarget;

	private final Set<String> inferred;
	private final Map<String, Set<Integer>> samples;
	private final int geometryAt;
	private final List<String> computes;
	private final List<String> passing;
	private final List<String> unreadable;
	private final List<String> notes;
	private final int programsRead;
	private final long expandMillis;

	private TargetPlan(Draft draft) {
		this.packName = draft.packName;
		this.dimension = draft.dimension;
		this.place = draft.place;
		this.directives = draft.directives;
		this.schedule = draft.schedule;
		this.running = List.copyOf(draft.running);
		this.disabled = Collections.unmodifiableMap(new LinkedHashMap<>(draft.disabled));
		this.writes = Collections.unmodifiableMap(new LinkedHashMap<>(draft.effective));
		Blends blends = blendsOf(draft);
		this.blend = blends.byProgram();
		this.blendPerTarget = blends.byTarget();
		this.inferred = Collections.unmodifiableSet(new TreeSet<>(draft.inferred));
		this.samples = Collections.unmodifiableMap(new LinkedHashMap<>(draft.samples));
		this.geometryAt = draft.geometryAt;
		this.computes = List.copyOf(draft.computes);
		this.passing = List.copyOf(draft.passing);
		this.written = Collections.unmodifiableSet(new TreeSet<>(draft.written));
		this.stored = Collections.unmodifiableSet(new TreeSet<>(draft.stored));
		this.sampled = Collections.unmodifiableSet(new TreeSet<>(draft.sampled));

		TreeSet<Integer> allocated = new TreeSet<>(draft.written);
		allocated.addAll(draft.sampled);
		this.allocated = Collections.unmodifiableSet(allocated);
		this.ordered = List.copyOf(allocated);
		this.shadowCeiling = draft.shadowCeiling;
		this.shadowAllocated = Collections.unmodifiableSet(new TreeSet<>(draft.shadowNamed));

		TreeSet<Integer> persistent = new TreeSet<>();
		allocated.stream().filter(index -> !draft.directives.clears(index)).forEach(persistent::add);
		this.persistent = Collections.unmodifiableSet(persistent);

		this.unreadable = List.copyOf(draft.unreadable);
		this.programsRead = draft.programsRead;
		this.expandMillis = draft.expandMillis;
		this.notes = List.copyOf(notesFor(draft, this.ordered, this.persistent, blends));
	}

	public static TargetPlan build(ShaderPackSource source, OptionIndex options, SettingSet settings,
			ShaderProperties properties, String dimension) throws IOException {
		return build(source, options, settings, properties, dimension, ChainFilter.ALL);
	}

	public static TargetPlan build(ShaderPackSource source, OptionIndex options, SettingSet settings,
			ShaderProperties properties, String dimension, ChainFilter filter) throws IOException {

		DimensionSet dimensions = source.derived(DimensionSet.class,
				() -> DimensionSet.discover(source));
		ProgramSet programs = source.derived(ProgramSet.class,
				() -> ProgramSet.enumerate(source, dimensions));

		boolean present = !dimension.equals(ProgramSet.ROOT)
				&& source.topLevelDirectories().contains(dimension);

		Draft draft = new Draft();
		draft.packName = source.packName();
		draft.dimension = dimension;
		draft.place = present ? dimension : ProgramSet.ROOT;
		draft.properties = properties;
		draft.shadowCeiling =
				PackDirectives.shadowColours(properties.declares(PackDirectives.HIGHER_SHADOWCOLOR));

		List<ProgramSet.ProgramKey> entries = programs.fragmentsOf(draft.place);

		Map<String, String> defines = settings.globalDefines(options);
		draft.blend = properties.blend(defines);

		PackTextures textures = source.derived(List.of(PackTextures.class, defines),
				() -> PackTextures.read(properties, defines, source));

		Set<String> images = CustomImages.namesOf(properties.imageDirectives(defines));

		read(source, options, settings, properties, entries, textures, images, draft);

		computes(properties, options, defines, programs, draft);

		stored(source, settings, programs, draft);

		defaults(source, options, settings, properties, defines, programs, textures, images, draft);
		walk(properties, options, defines, programs, entries, filter, draft);

		return new TargetPlan(draft);
	}

	private static void defaults(ShaderPackSource source, OptionIndex options, SettingSet settings,
			ShaderProperties properties, Map<String, String> defines, ProgramSet programs,
			PackTextures textures, Set<String> images, Draft draft) {
		if (draft.written.contains(SamplerPlan.DEFAULT_TARGET)
				|| draft.sampled.contains(SamplerPlan.DEFAULT_TARGET)) {
			return;
		}

		Map<String, Boolean> toggles = properties.programToggles(defines, options);
		IncludeExpander expander = new IncludeExpander(source, settings);
		for (ProgramSet.ProgramKey key : programs.keys()) {
			String bare = key.name().baseName();
			if (key.stage() == ProgramStage.FRAGMENT || !key.dimension().equals(draft.place)
					|| !SamplerPlan.fullscreen(bare) || off(key, bare, toggles, draft)) {
				continue;
			}

			Set<String> supplied = suppliedTo(textures, bare);
			if (SamplerPlan.byDefault(picturesTo(textures, bare)) != SamplerPlan.Kind.COLORTEX) {
				continue;
			}

			Optional<Path> file = source.file(key.file());
			if (file.isEmpty()) {
				continue;
			}

			ExpandedUnit unit;
			try {
				unit = expander.expand(file.get());
			} catch (IOException | RuntimeException e) {

				draft.unreadable.add(key.file());
				continue;
			}

			for (Declaration sampler : samplers(unit)) {
				if (SamplerPlan.takesDefault(sampler.name(), sampler.type(), supplied, images)) {
					draft.sampled.add(SamplerPlan.DEFAULT_TARGET);
					return;
				}
			}
		}
	}

	private static boolean off(ProgramSet.ProgramKey key, String bare, Map<String, Boolean> toggles,
			Draft draft) {
		if (key.stage() == ProgramStage.COMPUTE) {
			String stem = stemOf(key.file(), draft.place);
			return draft.disabledComputes.containsKey(stem)
					|| draft.unreachableComputes.contains(stem);
		}

		return Boolean.FALSE.equals(toggles.get(pathOf(bare, draft.place)));
	}

	private static String pathOf(String name, String place) {
		return place.isEmpty() ? name : place + "/" + name;
	}

	private static String stemOf(String file, String place) {
		int dot = file.lastIndexOf('.');
		String path = dot < 0 ? file : file.substring(0, dot);
		String prefix = place.isEmpty() ? "" : place + "/";

		return path.startsWith(prefix) ? path.substring(prefix.length()) : path;
	}

	private static Set<String> suppliedTo(PackTextures textures, String program) {
		return TextureStage.of(program).map(textures::suppliedTo).orElse(Set.of());
	}

	private static Set<String> picturesTo(PackTextures textures, String program) {
		return TextureStage.of(program).map(textures::picturesTo).orElse(Set.of());
	}

	private static void stored(ShaderPackSource source, SettingSet settings, ProgramSet programs,
			Draft draft) {
		Set<String> kept = Set.copyOf(draft.computes);
		IncludeExpander expander = new IncludeExpander(source, settings);
		for (ProgramSet.ProgramKey key : programs.computesOf(draft.place)) {

			if (ProgramNames.shadowComposite(key.name().family())
					|| !kept.contains(stemOf(key.file(), draft.place))) {
				continue;
			}

			Optional<Path> file = source.file(key.file());
			if (file.isEmpty()) {
				continue;
			}

			try {

				draft.written.addAll(colourImages(expander.expand(file.get())));
			} catch (IOException | RuntimeException e) {
				draft.unreadable.add(key.file());
			}
		}
	}

	private static void computes(ShaderProperties properties, OptionIndex options,
			Map<String, String> defines, ProgramSet programs, Draft draft) {
		Map<String, Boolean> toggles = properties.programToggles(defines, options);
		Map<String, String> conditions = properties.programConditions(defines);
		Set<String> kept = new TreeSet<>();
		Map<String, String> off = new LinkedHashMap<>();

		for (ProgramSet.ProgramKey key : programs.keys()) {
			if (key.stage() != ProgramStage.COMPUTE || !key.dimension().equals(draft.place)) {
				continue;
			}

			String stem = stemOf(key.file(), draft.place);
			String path = pathOf(stem, draft.place);
			if (Boolean.FALSE.equals(toggles.get(path))) {
				off.put(stem, conditions.getOrDefault(path, "shaders.properties"));
				continue;
			}

			kept.add(stem);
		}

		Map<String, List<String>> byBase = new LinkedHashMap<>();
		for (String stem : kept) {
			byBase.computeIfAbsent(ProgramNames.parse(stem)
					.map(ProgramNames.ProgramName::baseName)
					.orElse(stem), _ -> new ArrayList<>()).add(stem);
		}

		for (List<String> stems : byBase.values()) {
			Set<String> letters = new HashSet<>();
			for (String stem : stems) {
				letters.add(ProgramNames.computeLetter(stem));
			}

			for (String stem : stems) {
				String letter = ProgramNames.computeLetter(stem);
				for (char c = 'a'; c < (letter.isEmpty() ? 'a' : letter.charAt(0)); c++) {
					if (!letters.contains(String.valueOf(c))) {
						kept.remove(stem);
						draft.unreachableComputes.add(stem);
						break;
					}
				}
			}
		}

		draft.computes = List.copyOf(kept);
		draft.disabledComputes.putAll(off);
	}

	private static void walk(ShaderProperties properties, OptionIndex options,
			Map<String, String> defines, ProgramSet programs, List<ProgramSet.ProgramKey> entries,
			ChainFilter filter, Draft draft) {
		Map<String, Boolean> toggles = properties.programToggles(defines, options);
		Map<String, String> conditions = properties.programConditions(defines);
		List<TargetSchedule.Step> steps = new ArrayList<>();
		int rank = 0;

		for (ProgramSet.ProgramKey key : ProgramSet.sorted(entries, ProgramNames::frameRank)) {
			String family = key.name().family();
			String name = key.name().baseName();

			if (draft.geometryAt < 0 && ProgramNames.frameRank(family) >= GEOMETRY_RANK) {
				draft.geometryAt = draft.running.size();
			}

			if (ProgramNames.shadowComposite(family)) {
				draft.shadowComposites.add(name);
				continue;
			}

			boolean fullscreen = !ProgramNames.geometry(family);
			List<Integer> writes = writesOf(name, ProgramNames.shadowGeometry(family), draft);
			draft.effective.put(name, writes);

			String path = draft.place.isEmpty() ? name : draft.place + "/" + name;
			if (Boolean.FALSE.equals(toggles.get(path))) {
				draft.disabled.put(name, conditions.getOrDefault(path, "shaders.properties"));
				continue;
			}

			if (!fullscreen) {
				steps.add(new TargetSchedule.Step(name, writes, false));
				continue;
			}

			if (!name.equals(FINAL)) {

				int position = rank++;
				if (!filter.accepts(name, position)) {
					draft.disabled.put(name, filter.refuses(name) ? UNBINDABLE : LEFT_OUT);
					continue;
				}
			}

			draft.running.add(name);
			steps.add(new TargetSchedule.Step(name, writes, true));
		}

		if (draft.geometryAt < 0) {
			draft.geometryAt = draft.running.size();
		}

		draft.passing = passingOf(programs, toggles, draft);
		draft.schedule = TargetSchedule.of(steps, properties.flips(defines), draft.passing);
	}

	private static List<String> passingOf(ProgramSet programs, Map<String, Boolean> toggles,
			Draft draft) {
		Set<String> fragments = new HashSet<>();
		Set<String> vertices = new HashSet<>();
		for (ProgramSet.ProgramKey key : programs.keys()) {
			if (!key.dimension().equals(draft.place)) {
				continue;
			}

			if (key.stage() == ProgramStage.FRAGMENT) {
				fragments.add(key.name().baseName());
			} else if (key.stage() == ProgramStage.VERTEX) {
				vertices.add(key.name().baseName());
			}
		}

		return draft.computes.stream()
				.map(ProgramNames::computeBase)
				.flatMap(Optional::stream)
				.filter(base -> !base.equals(FINAL))
				.filter(base -> !sourced(base, fragments, vertices, toggles, draft))
				.distinct()
				.sorted(ProgramNames.frameOrder())
				.toList();
	}

	private static boolean sourced(String base, Set<String> fragments, Set<String> vertices,
			Map<String, Boolean> toggles, Draft draft) {
		String path = draft.place.isEmpty() ? base : draft.place + "/" + base;
		return fragments.contains(base) && vertices.contains(base)
				&& !Boolean.FALSE.equals(toggles.get(path));
	}

	private static List<Integer> writesOf(String name, boolean shadowGeometry, Draft draft) {

		if (name.equals(FINAL)) {
			return List.of();
		}

		if (shadowGeometry) {
			return List.of();
		}

		List<Integer> declared = draft.writes.getOrDefault(name, List.of());
		if (!declared.isEmpty() || draft.unexpanded.contains(name)) {
			return declared;
		}

		draft.inferred.add(name);
		draft.written.add(0);

		return List.of(0);
	}

	private static void read(ShaderPackSource source, OptionIndex options, SettingSet settings,
			ShaderProperties properties, List<ProgramSet.ProgramKey> entries, PackTextures textures,
			Set<String> images, Draft draft) {
		IncludeExpander expander = new IncludeExpander(source, settings);
		TargetDirectives.Builder builder = TargetDirectives.builder();
		long began = System.nanoTime();

		for (ProgramSet.ProgramKey key : ProgramSet.sorted(entries, ProgramNames::directiveRank)) {
			Optional<Path> file = source.file(key.file());
			if (file.isEmpty()) {
				draft.unreadable.add(key.file());
				draft.unexpanded.add(key.name().baseName());
				continue;
			}

			ExpandedUnit unit;
			try {
				unit = expander.expand(file.get());
			} catch (IOException | RuntimeException e) {

				draft.unreadable.add(key.file());
				draft.unexpanded.add(key.name().baseName());
				continue;
			}

			draft.programsRead++;

			builder.accept(key.file(), unit);

			String family = key.name().family();
			if (ProgramNames.shadowComposite(family)) {
				continue;
			}

			boolean fromTheLight = ProgramNames.shadowGeometry(family);
			List<Integer> writes = DrawBuffers.parse(unit);

			draft.writes.put(key.name().baseName(), writes);
			if (fromTheLight) {
				draft.fromTheLight.add(key.name().baseName());

				draft.shadowNamed.addAll(DrawBuffers.shadowColours(writes, draft.shadowCeiling));
			} else {
				draft.written.addAll(writes);
			}

			Set<Integer> stores = colourImages(unit);
			draft.written.addAll(stores);
			draft.stored.addAll(stores);

			String bare = key.name().baseName();
			Set<String> supplied = suppliedTo(textures, bare);
			boolean screen = SamplerPlan.fullscreen(bare)
					&& SamplerPlan.byDefault(picturesTo(textures, bare)) == SamplerPlan.Kind.COLORTEX;

			Set<Integer> indices = new TreeSet<>();
			for (Declaration declaration : samplers(unit)) {
				String sampler = declaration.name();
				if (screen
						&& SamplerPlan.takesDefault(sampler, declaration.type(), supplied, images)) {
					draft.sampled.add(SamplerPlan.DEFAULT_TARGET);
				}

				if (SamplerPlan.isShadowColour(sampler)
						&& SamplerPlan.shadowColour(sampler) < draft.shadowCeiling) {
					draft.shadowNamed.add(SamplerPlan.shadowColour(sampler));
				}

				TargetName.index(sampler).ifPresent(index -> {
					draft.sampled.add(index);
					indices.add(index);
				});
			}

			draft.samples.put(key.name().baseName(), indices);
		}

		builder.accept(properties, settings.globalDefines(options));
		draft.directives = builder.build();
		draft.expandMillis = (System.nanoTime() - began) / 1_000_000L;
	}

	private record Declaration(String name, String type) {
	}

	private static List<Declaration> samplers(ExpandedUnit unit) {
		return declared(unit, SAMPLER);
	}

	private static Set<Integer> colourImages(ExpandedUnit unit) {
		Set<Integer> indices = new TreeSet<>();
		for (Declaration declaration : declared(unit, IMAGE)) {
			TargetName.imageIndex(declaration.name()).ifPresent(indices::add);
		}

		return indices;
	}

	private static List<Declaration> declared(ExpandedUnit unit, Pattern pattern) {
		List<Declaration> names = new ArrayList<>();
		List<String> lines = unit.lines();
		Map<String, String> aliases = new LinkedHashMap<>();
		boolean commented = false;

		for (int line = 0; line < lines.size(); line++) {
			boolean opened = commented;
			commented = BlockComments.openAfter(lines.get(line), commented);
			if (opened || !unit.isLive(line)) {
				continue;
			}

			Matcher alias = NAME_ALIAS.matcher(lines.get(line));
			if (alias.matches()) {
				aliases.put(alias.group(1), alias.group(2));
				continue;
			}
			Matcher undefinition = NAME_UNDEF.matcher(lines.get(line));
			if (undefinition.matches()) {
				aliases.remove(undefinition.group(1));
				continue;
			}

			Matcher matcher = pattern.matcher(lines.get(line));
			if (!matcher.matches()) {
				continue;
			}

			String type = matcher.group(1);
			for (String declarator : matcher.group(2).split(",", -1)) {
				String name = declarator.trim();
				int bracket = name.indexOf('[');
				if (bracket >= 0) {
					name = name.substring(0, bracket).trim();
				}

				if (!name.isEmpty()) {
					String original = name;
					Set<String> seen = new HashSet<>();
					boolean cycle = false;
					while (aliases.containsKey(name)) {
						if (!seen.add(name)) {
							cycle = true;
							break;
						}
						name = aliases.get(name);
					}
					names.add(new Declaration(cycle ? original : name, type));
				}
			}
		}

		return names;
	}

	public String packName() {
		return this.packName;
	}

	public String dimension() {
		return this.dimension;
	}

	public String place() {
		return this.place;
	}

	public TargetDirectives directives() {
		return this.directives;
	}

	public TargetSchedule schedule() {
		return this.schedule;
	}

	public Set<Integer> allocated() {
		return this.allocated;
	}

	public int shadowCeiling() {
		return this.shadowCeiling;
	}

	public Set<Integer> shadowAllocated() {
		return this.shadowAllocated;
	}

	public List<Integer> ordered() {
		return this.ordered;
	}

	public Set<Integer> written() {
		return this.written;
	}

	public Set<Integer> stored() {
		return this.stored;
	}

	public Set<Integer> sampled() {
		return this.sampled;
	}

	public Set<Integer> persistent() {
		return this.persistent;
	}

	public List<String> running() {
		return this.running;
	}

	public List<String> passing() {
		return this.passing;
	}

	public List<String> computes() {
		return this.computes;
	}

	public Map<String, String> disabled() {
		return this.disabled;
	}

	public List<Integer> writes(String program) {
		return this.writes.getOrDefault(TargetName.bareName(program), List.of());
	}

	public Set<String> inferredWrites() {
		return this.inferred;
	}

	public Set<Integer> samples(String program) {
		return this.samples.getOrDefault(TargetName.bareName(program), Set.of());
	}

	public int geometryAt() {
		return this.geometryAt;
	}

	public int programsRead() {
		return this.programsRead;
	}

	public long expandMillis() {
		return this.expandMillis;
	}

	public List<String> unreadable() {
		return this.unreadable;
	}

	public List<String> notes() {
		return this.notes;
	}

	public long bytesAt(int screenWidth, int screenHeight, Set<Integer> doubledNow) {
		long total = 0L;

		for (int index : this.ordered) {
			TargetSize size = this.directives.size(index);
			long pixels = (long) size.width(screenWidth) * size.height(screenHeight);
			long bytes = pixels * this.directives.format(index).used().bytesPerPixel();
			total += doubledNow.contains(index) ? bytes * 2L : bytes;
		}

		return total;
	}

	private static List<String> notesFor(Draft draft, List<Integer> ordered, Set<Integer> persistent,
			Blends blends) {
		List<String> notes = new ArrayList<>(draft.directives.notes());
		TargetDirectives directives = draft.directives;

		for (int index : ordered) {
			TargetFormat.Resolution format = directives.format(index);
			if (format.reason() != TargetFormat.Reason.EXACT) {
				notes.add(TargetName.canonical(index) + " asked for " + format.declared()
						+ ", allocated " + format.used() + " (" + format.reason() + ") from "
						+ directives.formatSource(index)
						+ (format.alphaAdded() ? ", clear alpha forced to 1 to match GL" : ""));
			}

			TargetSize size = directives.size(index);
			if (!size.full()) {
				notes.add(TargetName.canonical(index) + " is sized by shaders.properties, "
						+ (size.relative() ? size.width() + " by " + size.height() + " of the screen"
								: (int) size.width() + " by " + (int) size.height() + " pixels")
						+ (size.overCap() ? ", capped at " + TargetSize.MAX_DIMENSION
								+ " texels a side, which is all this engine will allocate" : ""));
			}
		}

		if (!persistent.isEmpty()) {
			notes.add("targets the pack keeps between frames: " + persistent);
		}

		notes.addAll(directives.conflicts());

		if (!directives.mipmapRequests().isEmpty()) {
			notes.add(directives.mipmapRequests().size() + " programs read lods, on "
					+ directives.mipmapped().size() + " targets that carry a mip chain filled by the "
					+ "engine's own reduction before each of those programs draws: "
					+ directives.mipmapRequests());
		}

		if (!blends.unreadable().isEmpty()) {
			notes.add("blend directives in a form this engine does not read, left out, the programs "
					+ "keeping what their other directives give them: " + blends.unreadable());
		}

		if (!blends.honoured().isEmpty()) {
			notes.add("per buffer blend directives read onto the attachment whose rank their target "
					+ "holds, which the pass carries where the device parts its attachments and drops "
					+ "back to the whole program function where it does not: " + blends.honoured());
		}

		if (!blends.fullscreen().isEmpty()) {
			notes.add("per buffer blend directives on a full screen program, which the reference "
					+ "reads for its geometry programs only, so they change nothing on either engine: "
					+ blends.fullscreen());
		}

		if (!blends.dropped().isEmpty()) {
			notes.add("per buffer blend directives naming a target their program does not write, "
					+ "dropped as the reference drops them: " + blends.dropped());
		}

		if (!draft.computes.isEmpty()) {
			List<String> shadow = draft.computes.stream()
					.filter(name -> ProgramNames.shadowComposite(ProgramNames.familyOf(name)))
					.toList();

			List<String> alone = draft.computes.stream()
					.filter(name -> !shadow.contains(name))
					.filter(name -> ProgramNames.computeBase(name)
							.filter(draft.passing::contains).isPresent())
					.toList();

			List<String> chained = draft.computes.stream()
					.filter(name -> !shadow.contains(name) && !alone.contains(name))
					.filter(name -> ProgramNames.computeBase(name)
							.filter(draft.running::contains).isPresent())
					.toList();

			List<String> finals = draft.computes.stream()
					.filter(name -> !shadow.contains(name) && !alone.contains(name)
							&& !chained.contains(name))
					.filter(name -> ProgramNames.computeBase(name)
							.filter(FINAL::equals).isPresent())
					.toList();
			List<String> orphaned = draft.computes.stream()
					.filter(name -> !shadow.contains(name) && !alone.contains(name)
							&& !chained.contains(name) && !finals.contains(name))
					.filter(name -> ProgramNames.computeBase(name).isPresent())
					.toList();
			List<String> other = draft.computes.stream()
					.filter(name -> !shadow.contains(name) && !alone.contains(name)
							&& !chained.contains(name) && !finals.contains(name)
							&& !orphaned.contains(name))
					.toList();
			if (!shadow.isEmpty()) {
				notes.add("shadow compute served: " + shadow);
			}

			if (!chained.isEmpty()) {
				notes.add("compute programs dispatched before the pass they hang off: " + chained);
			}

			if (!alone.isEmpty()) {
				notes.add("compute programs dispatched at the moment of the program they hang off, "
						+ "which this place draws no pass for: " + alone);
			}

			if (!finals.isEmpty()) {
				notes.add("compute programs skipped, they hang off a final this place does not "
						+ "draw and the reference builds a final's computes with the final alone: "
						+ finals);
			}

			if (!orphaned.isEmpty()) {
				notes.add("compute programs skipped, the pass filter took the program they hang "
						+ "off out of the chain, at the user's ask or because a sampler of it is "
						+ "one this backend cannot bind: " + orphaned);
			}

			if (!other.isEmpty()) {
				notes.add("compute programs skipped, no stage exists for them yet: " + other);
			}
		}

		if (!draft.unreachableComputes.isEmpty()) {
			notes.add("compute programs past a gap in their letters, which the reference never "
					+ "opens: " + draft.unreachableComputes);
		}

		if (!draft.disabledComputes.isEmpty()) {
			notes.add("compute programs this place ships and the pack switches off: "
					+ draft.disabledComputes.entrySet().stream()
							.map(entry -> entry.getKey() + " (" + entry.getValue() + ")")
							.collect(Collectors.joining(", ")));
		}

		if (!draft.shadowComposites.isEmpty()) {
			notes.add("shadow composites skipped, they draw full screen over the shadow colour "
					+ "buffers on a flip counter of their own and this engine runs no stage there, "
					+ "so their draw buffers name no colour target of this place: "
					+ draft.shadowComposites);
		}

		if (!draft.disabled.isEmpty()) {
			notes.add("programs this place ships and does not run: "
					+ draft.disabled.entrySet().stream()
							.map(entry -> entry.getKey() + " (" + entry.getValue() + ")")
							.collect(Collectors.joining(", ")));
		}

		if (!draft.inferred.isEmpty()) {
			notes.add("programs declaring no draw buffer, sent to colortex0 as CoreVK does: "
					+ new TreeSet<>(draft.inferred));
		}

		if (!draft.fromTheLight.isEmpty()) {
			notes.add("programs drawn from the light, whose draw buffers would name shadowcolor and "
					+ "are therefore read as no colour target of this place, declared or not: "
					+ new TreeSet<>(draft.fromTheLight));
		}

		List<Integer> declaredOnly = directives.declared().stream()
				.filter(index -> !ordered.contains(index))
				.sorted()
				.toList();
		if (!declaredOnly.isEmpty()) {
			notes.add("targets this pack declares a format for and that no program of this place "
					+ "writes or samples, so nothing is allocated for them: " + declaredOnly);
		}

		List<Integer> neverWritten = draft.sampled.stream()
				.filter(index -> !draft.written.contains(index))
				.sorted()
				.toList();
		if (!neverWritten.isEmpty()) {
			notes.add("sampled and never written by this pack, so they read their clear colour: "
					+ neverWritten);
		}

		if (!draft.unreadable.isEmpty()) {
			notes.add("entry points that could not be expanded and declare nothing: "
					+ draft.unreadable);
		}

		return notes;
	}

	private static Blends blendsOf(Draft draft) {
		Map<String, BlendMode> whole = new LinkedHashMap<>();
		Map<String, List<ShaderProperties.BlendDirective>> perBuffer = new LinkedHashMap<>();
		List<String> unreadable = new ArrayList<>();
		for (ShaderProperties.BlendDirective directive : draft.blend) {
			String program = TargetName.bareName(directive.program());
			Optional<BlendMode> mode = BlendMode.parse(directive.value());
			if (mode.isEmpty()) {
				unreadable.add(spelling(directive) + "=" + directive.value());
			} else if (directive.buffer() == null) {
				whole.put(program, mode.get());
			} else {
				perBuffer.computeIfAbsent(program, key -> new ArrayList<>()).add(directive);
			}
		}

		Map<String, BlendMode> resolved = new LinkedHashMap<>(whole);
		Map<String, List<BlendMode>> byTarget = new LinkedHashMap<>();
		List<String> honoured = new ArrayList<>();
		List<String> fullscreen = new ArrayList<>();
		List<String> dropped = new ArrayList<>();
		perBuffer.forEach((program, directives) -> {
			String family = ProgramNames.familyOf(program);
			if (!ProgramNames.geometry(family)) {
				directives.forEach(directive -> fullscreen.add(spelling(directive)));
				return;
			}

			boolean fromTheLight = ProgramNames.shadowGeometry(family);
			List<Integer> declared = fromTheLight
					? draft.writes.get(program)
					: draft.effective.get(program);
			if (declared == null) {
				return;
			}

			List<Integer> writes = fromTheLight && declared.isEmpty() ? List.of(0) : declared;

			BlendMode[] functions = new BlendMode[writes.size()];
			Arrays.fill(functions, whole.get(program));
			Map<Integer, String> landed = new LinkedHashMap<>();
			for (ShaderProperties.BlendDirective directive : directives) {

				OptionalInt index = TargetName.index(directive.buffer());
				if (index.isEmpty()) {
					unreadable.add(spelling(directive) + "=" + directive.value());
					continue;
				}

				int rank = writes.indexOf(index.getAsInt());
				if (rank < 0) {
					dropped.add(spelling(directive));
					continue;
				}

				functions[rank] = BlendMode.parse(directive.value()).orElseThrow();
				landed.put(rank, spelling(directive) + "=" + directive.value());
			}

			if (landed.isEmpty()) {
				return;
			}

			byTarget.put(program, Collections.unmodifiableList(Arrays.asList(functions)));
			honoured.addAll(landed.values());

			BlendMode first = functions[0];
			if (Arrays.stream(functions).allMatch(function -> Objects.equals(function, first))) {
				resolved.put(program, first);
			}
		});

		return new Blends(Collections.unmodifiableMap(resolved),
				Collections.unmodifiableMap(byTarget), List.copyOf(unreadable),
				List.copyOf(honoured), List.copyOf(fullscreen), List.copyOf(dropped));
	}

	private static String spelling(ShaderProperties.BlendDirective directive) {
		return directive.buffer() == null ? directive.program()
				: directive.program() + "." + directive.buffer();
	}

	private record Blends(Map<String, BlendMode> byProgram, Map<String, List<BlendMode>> byTarget,
			List<String> unreadable, List<String> honoured, List<String> fullscreen,
			List<String> dropped) {
	}

	public Optional<BlendMode> blend(String program) {
		return Optional.ofNullable(this.blend.get(TargetName.bareName(program)));
	}

	public Optional<BlendMode> blend(String program, int rank) {
		List<BlendMode> perTarget = this.blendPerTarget.get(TargetName.bareName(program));
		if (perTarget == null || rank < 0 || rank >= perTarget.size()) {
			return blend(program);
		}

		return Optional.ofNullable(perTarget.get(rank));
	}

	private static final class Draft {

		private final Map<String, List<Integer>> writes = new LinkedHashMap<>();
		private final Map<String, List<Integer>> effective = new LinkedHashMap<>();
		private final Map<String, Set<Integer>> samples = new LinkedHashMap<>();
		private final Set<String> inferred = new TreeSet<>();
		private final Set<String> unexpanded = new TreeSet<>();

		private final Set<String> fromTheLight = new TreeSet<>();

		private final List<String> running = new ArrayList<>();
		private final Map<String, String> disabled = new LinkedHashMap<>();
		private final Map<String, String> disabledComputes = new LinkedHashMap<>();

		private final List<String> unreachableComputes = new ArrayList<>();
		private final Set<String> shadowComposites = new TreeSet<>();
		private final Set<Integer> written = new TreeSet<>();
		private final Set<Integer> stored = new TreeSet<>();
		private final Set<Integer> sampled = new TreeSet<>();

		private final Set<Integer> shadowNamed = new TreeSet<>();

		private int shadowCeiling = PackDirectives.SHADOW_COLOURS;

		private final List<String> unreadable = new ArrayList<>();

		private int geometryAt = -1;
		private String packName;
		private String dimension;
		private String place;
		private ShaderProperties properties;
		private List<ShaderProperties.BlendDirective> blend = List.of();
		private TargetDirectives directives;
		private TargetSchedule schedule;
		private List<String> computes = List.of();
		private List<String> passing = List.of();
		private int programsRead;
		private long expandMillis;
	}
}
