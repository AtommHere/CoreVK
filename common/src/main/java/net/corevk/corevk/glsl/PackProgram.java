package net.corevk.corevk.glsl;

import net.corevk.corevk.pack.option.OptionIndex;
import net.corevk.corevk.pack.option.OptionValue;
import net.corevk.corevk.pack.option.SettingSet;
import net.corevk.corevk.pack.model.AlphaTest;
import net.corevk.corevk.pack.program.ChainFilter;
import net.corevk.corevk.pack.program.ProgramResolver;
import net.corevk.corevk.pack.program.ProgramSet;
import net.corevk.corevk.pack.model.ProgramStage;
import net.corevk.corevk.pack.program.TerrainPass;
import net.corevk.corevk.pack.source.DimensionSet;
import net.corevk.corevk.pack.source.IncludeExpander.ExpandedUnit;
import net.corevk.corevk.pack.source.IncludeExpander;
import net.corevk.corevk.pack.source.OpenedPack;
import net.corevk.corevk.pack.source.ShaderPackSource;
import net.corevk.corevk.pack.source.SourceMentions;
import net.corevk.corevk.pack.source.ShaderProperties;
import net.corevk.corevk.pack.target.ChainPlan;
import net.corevk.corevk.pack.target.SamplerPlan;
import net.corevk.corevk.pack.target.SamplerTypes;
import net.corevk.corevk.pack.target.TargetPlan;
import net.corevk.corevk.pack.target.TargetSchedule;
import net.corevk.corevk.pack.texture.CustomImages;
import net.corevk.corevk.pack.texture.CustomStorage;
import net.corevk.corevk.pack.texture.PackTextures;
import net.corevk.corevk.pack.model.TextureStage;
import net.corevk.corevk.pack.texture.VolumeAtlas;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class PackProgram {

	private static final List<ProgramStage> STAGES =
			List.of(ProgramStage.VERTEX, ProgramStage.GEOMETRY, ProgramStage.FRAGMENT);

	private static final String FINAL = "final";

	private static final Set<String> SETTLED_EARLY =
			Set.of("shadowtex1", "watershadow", "depthtex2", "dhDepthTex");

	private static final String CLOUD_PROGRAM = "gbuffers_clouds";

	private PackProgram() {
	}

	public record Loaded(String packName, String path, ProgramTranslator.TranslatedProgram program,
			TargetPlan targets, SamplerPlan samplers, AlphaTest alphaTest, Set<String> supplied) {

		public Loaded {
			supplied = Set.copyOf(supplied);
		}

		public boolean voxelises() {
			if (this.program.stages().containsKey(ProgramStage.GEOMETRY)) {
				return true;
			}

			return this.program.samplers().stream().anyMatch(Loaded::imageUniform);
		}

		private static boolean imageUniform(TranslatedUnit.Uniform sampler) {
			String type = sampler.type();

			return type.startsWith("image") || type.startsWith("iimage")
					|| type.startsWith("uimage");
		}

		public List<TranslatedUnit.Uniform> unbindable() {
			return this.program.samplers().stream()
					.filter(sampler -> SamplerTypes.refused(sampler.type()))
					.filter(sampler -> !CustomImages.named(sampler.name()))
					.toList();
		}

		public List<String> storageBlocks() {
			return this.program.stages().values().stream()
					.flatMap(stage -> stage.notes().storageBlocks().stream())
					.map(TranslatedUnit.StorageBlock::name)
					.distinct()
					.toList();
		}

		public boolean fullbright() {
			return this.program.inputs().fullbright();
		}

		public boolean readsGameTransforms() {
			return this.program.stages().values().stream()
					.anyMatch(stage -> stage.notes().gameTextureMatrix() > 0
							|| stage.notes().gameModelView() > 0);
		}

		public Loaded rebind(TargetPlan plan, Optional<TargetSchedule.Bound> step) {
			return new Loaded(this.packName, this.path, this.program, plan,
					SamplerPlan.of(declaredIn(this.program), typesIn(this.program), plan, step,
							this.supplied), this.alphaTest, this.supplied);
		}
	}

	public record Refusal(List<TranslatedUnit.Uniform> unbindable, List<String> storage) {

		public Refusal {
			unbindable = List.copyOf(unbindable);
			storage = List.copyOf(storage);
		}

		public boolean any() {
			return !this.unbindable.isEmpty() || !this.storage.isEmpty();
		}

		public String reason() {
			List<String> said = new ArrayList<>();
			if (!this.unbindable.isEmpty()) {
				said.add("declares " + describe(this.unbindable) + ", a shape this backend cannot bind, "
						+ "with nothing behind that name this engine knows how to serve");
			}

			if (!this.storage.isEmpty()) {
				said.add("declares the storage block " + String.join(", ", this.storage)
						+ ", which nothing binds: the game lists a module's uniform buffers and its "
						+ "sampled images and neither includes one, so its descriptor stays on the "
						+ "binding the pack wrote");
			}

			return String.join(", and ", said);
		}

		private static String describe(List<TranslatedUnit.Uniform> samplers) {
			return samplers.stream()
					.map(sampler -> sampler.name() + " as " + sampler.type())
					.collect(Collectors.joining(", "));
		}
	}

	public record Chain(String packName, String place, TargetPlan targets, ChainPlan chain,
			Map<String, Loaded> programs, Map<String, Refusal> removed, SourceMentions mentions) {

		public Chain {

			programs = Collections.unmodifiableMap(new LinkedHashMap<>(programs));
			removed = Collections.unmodifiableMap(new LinkedHashMap<>(removed));
		}
	}

	public static Optional<Loaded> load(Path packPath, String path, boolean fullscreen) throws IOException {
		return load(packPath, path, fullscreen, Map.of());
	}

	public static Optional<Loaded> load(Path packPath, String path, boolean fullscreen,
			Map<String, OptionValue> chosen) throws IOException {
		return load(packPath, path, fullscreen, chosen, "");
	}

	public static Optional<Loaded> load(Path packPath, String path, boolean fullscreen,
			Map<String, OptionValue> chosen, String profile) throws IOException {
		return load(packPath, path, fullscreen ? VertexInputs.FULLSCREEN : VertexInputs.WORLD,
				chosen, profile);
	}

	public static Optional<Loaded> load(Path packPath, String path, VertexInputs inputs,
			Map<String, OptionValue> chosen, String profile) throws IOException {
		return load(packPath, path, inputs, inputs.elements(), chosen, profile);
	}

	public static Optional<Loaded> load(Path packPath, String path, VertexInputs inputs,
			List<String> boundElements, Map<String, OptionValue> chosen, String profile)
			throws IOException {
		try (ShaderPackSource source = ShaderPackSource.open(packPath)) {
			OptionIndex options = source.options();
			ShaderProperties properties = ShaderProperties.parse(source);
			Map<String, OptionValue> fromProfile = profile.isEmpty()
					? Map.of()
					: properties.expandProfile(profile);
			SettingSet settings = SettingSet.resolve(fromProfile, chosen, profile.isEmpty() ? "chosen" : profile);
			IncludeExpander expander = new IncludeExpander(source, settings);

			Map<ProgramStage, ExpandedUnit> units = read(source, expander, path);

			if (!units.containsKey(ProgramStage.VERTEX) || !units.containsKey(ProgramStage.FRAGMENT)) {
				return Optional.empty();
			}

			TargetPlan targets = TargetPlan.build(source, options, settings, properties, dimensionOf(path));
			PackTextures textures = textures(source, properties, options, settings);
			ProgramTranslator.TranslatedProgram program = ProgramTranslator.translate(units, inputs,
					boundElements, AlphaTest.OFF, false, programOf(path), textures.volumes());

			return Optional.of(bind(source.packName(), path, program, targets, AlphaTest.OFF,
					textures));
		}
	}

	public record Compute(Loaded loaded, int groupsX, int groupsY, int groupsZ, float renderX,
			float renderY, int localX, int localY, List<String> unresolved) {

		public Compute {
			unresolved = List.copyOf(unresolved);
		}

		public boolean fixed() {
			return this.groupsX >= 0;
		}

		public boolean relative() {
			return this.renderX >= 0.0F;
		}

		public boolean sized() {
			return fixed() || this.localX > 0;
		}

		public int[] groupsAt(int width, int height) {
			if (fixed()) {
				return new int[] { this.groupsX, this.groupsY, this.groupsZ };
			}

			int spanX = relative() ? (int) Math.ceil(width * (double) this.renderX) : width;
			int spanY = relative() ? (int) Math.ceil(height * (double) this.renderY) : height;
			return new int[] { cover(spanX, this.localX), cover(spanY, this.localY), 1 };
		}

		private static int cover(int span, int local) {
			return span <= 0 ? 0 : (span + local - 1) / local;
		}
	}

	private static final Pattern WORK_GROUPS = Pattern.compile(
			"^\\s*const\\s+ivec3\\s+workGroups\\s*=\\s*ivec3\\s*\\(\\s*([^,()]+?)\\s*,"
					+ "\\s*([^,()]+?)\\s*,\\s*([^,()]+?)\\s*\\)");

	private static final Pattern WORK_GROUPS_RENDER = Pattern.compile(
			"^\\s*const\\s+vec2\\s+workGroupsRender\\s*=\\s*vec2\\s*\\(\\s*([^,()]+?)\\s*,"
					+ "\\s*([^,()]+?)\\s*\\)");

	private static final Pattern LOCAL_SIZE = Pattern.compile("local_size_([xy])\\s*=\\s*");

	private static final Pattern MACRO = Pattern.compile("[A-Za-z_]\\w*");

	private static final Pattern WHOLE = Pattern.compile("-?\\d+");

	private static final Pattern FRACTION = Pattern.compile("-?(?:\\d+\\.?\\d*|\\.\\d+)[fF]?");

	private static final Pattern LITERAL = Pattern.compile("\\d+");

	private static final int MACRO_HOPS = 8;

	public static Optional<Compute> loadCompute(OpenedPack pack, String path) throws IOException {
		ShaderPackSource source = pack.source();
		OptionIndex options = pack.options();
		ShaderProperties properties = pack.properties();
		SettingSet settings = pack.settings();
		IncludeExpander expander = new IncludeExpander(source, settings);
		Optional<Path> file = source.file(path + "." + ProgramStage.COMPUTE.extension());
		if (file.isEmpty()) {
			return Optional.empty();
		}

		ExpandedUnit unit = expander.expand(file.get());
		TargetPlan targets = TargetPlan.build(source, options, settings, properties,
				dimensionOf(path));
		PackTextures textures = textures(source, properties, options, settings);
		Map<ProgramStage, ExpandedUnit> units = new LinkedHashMap<>();
		units.put(ProgramStage.COMPUTE, unit);
		ProgramTranslator.TranslatedProgram program = ProgramTranslator.translate(units,
				VertexInputs.FULLSCREEN, VertexInputs.FULLSCREEN.elements(), AlphaTest.OFF, false,
				programOf(path), textures.volumes());
		Set<String> unresolved = new LinkedHashSet<>();
		List<String> text = uncommented(unit.lines());
		int[] groups = workGroupsOf(unit, text, unresolved);
		float[] render = workGroupsRenderOf(unit, text, unresolved);
		int[] local = localSizeOf(unit, text, unresolved);
		return Optional.of(new Compute(
				bind(source.packName(), path, program, targets, AlphaTest.OFF, textures),
				groups[0], groups[1], groups[2], render[0], render[1], local[0], local[1],
				List.copyOf(unresolved)));
	}

	private static int[] workGroupsOf(ExpandedUnit unit, List<String> lines,
			Collection<String> unresolved) {
		int[] groups = { -1, -1, -1 };
		for (int i = 0; i < lines.size(); i++) {
			if (!unit.isLive(i)) {
				continue;
			}

			Matcher matcher = WORK_GROUPS.matcher(lines.get(i));
			if (matcher.find()) {
				int[] read = new int[3];
				boolean whole = true;

				for (int axis = 0; axis < 3; axis++) {
					Integer count = valueOf(matcher.group(axis + 1), unit.defines(), WHOLE,
							Integer::valueOf, "workGroups", unresolved);
					whole &= count != null;
					if (count != null) {
						read[axis] = Math.max(0, count);
					}
				}

				if (whole) {
					groups = read;
				}
			}
		}

		return groups;
	}

	private static float[] workGroupsRenderOf(ExpandedUnit unit, List<String> lines,
			Collection<String> unresolved) {
		float[] render = { -1.0F, -1.0F };
		for (int i = 0; i < lines.size(); i++) {
			if (!unit.isLive(i)) {
				continue;
			}

			Matcher matcher = WORK_GROUPS_RENDER.matcher(lines.get(i));
			if (matcher.find()) {
				Float width = valueOf(matcher.group(1), unit.defines(), FRACTION, Float::valueOf,
						"workGroupsRender", unresolved);
				Float height = valueOf(matcher.group(2), unit.defines(), FRACTION, Float::valueOf,
						"workGroupsRender", unresolved);
				if (width != null && height != null) {
					render[0] = Math.max(0.0F, width);
					render[1] = Math.max(0.0F, height);
				}
			}
		}

		return render;
	}

	private static <T> T valueOf(String argument, Map<String, String> defines, Pattern shape,
			Function<String, T> number, String directive, Collection<String> unresolved) {
		String value = argument.trim();
		for (int hop = 0; hop < MACRO_HOPS && MACRO.matcher(value).matches(); hop++) {
			String stood = defines.get(value);
			if (stood == null) {
				break;
			}

			value = stood.trim();
		}

		if (shape.matcher(value).matches()) {
			try {
				return number.apply(value);
			} catch (NumberFormatException wide) {

			}
		}

		unresolved.add(argument.trim() + " in " + directive);

		return null;
	}

	private static String argumentAt(String line, int from) {
		int depth = 0;
		for (int at = from; at < line.length(); at++) {
			char letter = line.charAt(at);
			if (letter == '(') {
				depth++;
			} else if (letter == ')' && depth > 0) {
				depth--;
			} else if (letter == ')' || (letter == ',' && depth == 0)) {
				return line.substring(from, at).trim();
			}
		}

		return line.substring(from).trim();
	}

	private static List<String> uncommented(List<String> lines) {
		List<String> read = new ArrayList<>(lines.size());
		boolean commented = false;
		for (String line : lines) {
			char[] text = line.toCharArray();
			for (int at = 0; at < text.length; at++) {
				boolean pair = at + 1 < text.length;
				if (commented) {
					boolean closes = pair && text[at] == '*' && text[at + 1] == '/';
					text[at] = ' ';
					if (closes) {
						text[++at] = ' ';
						commented = false;
					}
				} else if (pair && text[at] == '/' && text[at + 1] == '/') {
					Arrays.fill(text, at, text.length, ' ');
					break;
				} else if (pair && text[at] == '/' && text[at + 1] == '*') {
					text[at] = ' ';
					text[++at] = ' ';
					commented = true;
				}
			}

			read.add(new String(text));
		}

		return read;
	}

	private static int[] localSizeOf(ExpandedUnit unit, List<String> lines,
			Collection<String> unresolved) {
		int[] local = { -1, 1 };
		boolean read = true;
		for (int i = 0; i < lines.size(); i++) {
			if (!unit.isLive(i)) {
				continue;
			}

			String line = lines.get(i);
			Matcher matcher = LOCAL_SIZE.matcher(line);
			while (matcher.find()) {
				String axis = matcher.group(1);
				Integer size = valueOf(argumentAt(line, matcher.end()), unit.defines(), LITERAL,
						Integer::valueOf, "local_size_" + axis, unresolved);
				read &= size != null;
				if (size != null) {
					local["x".equals(axis) ? 0 : 1] = Math.max(1, size);
				}
			}
		}

		return read ? local : new int[] { -1, -1 };
	}

	private record Reading(IncludeExpander expander, TargetPlan targets, PackTextures textures,
			ProgramResolver resolver) {
	}

	private record ReadingKey(SettingSet settings, String place) {
	}

	private record ReadsKey(ExpandedUnit vertex, VertexInputs inputs, String program,
			Map<String, VolumeAtlas> volumes) {
	}

	private static Set<String> reads(ShaderPackSource source, ExpandedUnit vertex,
			VertexInputs inputs, AlphaTest alphaTest, boolean coverage, String program,
			Map<String, VolumeAtlas> volumes) throws IOException {
		return source.derived(new ReadsKey(vertex, inputs, program, volumes),
				() -> ProgramTranslator.reads(vertex, inputs, alphaTest, coverage, program, volumes));
	}

	private static Reading reading(OpenedPack pack, String place) throws IOException {
		ShaderPackSource source = pack.source();
		return source.derived(new ReadingKey(pack.settings(), place), () -> {
			IncludeExpander expander = new IncludeExpander(source, pack.settings());
			TargetPlan targets = TargetPlan.build(source, pack.options(), pack.settings(),
					pack.properties(), place);
			PackTextures textures = textures(source, pack.properties(), pack.options(), pack.settings());
			DimensionSet dimensions = source.derived(DimensionSet.class,
					() -> DimensionSet.discover(source));
			ProgramResolver resolver = ProgramResolver.resolve(
					source.derived(ProgramSet.class, () -> ProgramSet.enumerate(source, dimensions)),
					dimensions, pack.properties().switchedOff(pack.settings().globalDefines(pack.options()),
							pack.options()));

			return new Reading(expander, targets, textures, resolver);
		});
	}

	public static Terrain loadTerrain(Path packPath, String place,
			Map<String, OptionValue> chosen, String profile, VertexInputs inputs)
			throws IOException {
		try (OpenedPack pack = OpenedPack.open(packPath, chosen, profile)) {
			return loadTerrain(pack, place, inputs);
		}
	}

	public static Terrain loadTerrain(OpenedPack pack, String place, VertexInputs inputs)
			throws IOException {
		if (!inputs.terrain()) {
			throw new IllegalArgumentException("The chunk passes are drawn from Sodium's own mesh, so "
					+ inputs + " is not one of the contracts they may be written against");
		}

		ShaderPackSource source = pack.source();
		OptionIndex options = pack.options();
		ShaderProperties properties = pack.properties();
		SettingSet settings = pack.settings();
		Reading reading = reading(pack, place);
		IncludeExpander expander = reading.expander();
		TargetPlan targets = reading.targets();
		PackTextures textures = reading.textures();
		ProgramResolver resolver = reading.resolver();

		Map<String, AlphaTest> overrides = properties.alphaTests(settings.globalDefines(options));

		boolean shadowOff =
				properties.shadowEnabled(settings.globalDefines(options)).equals(Optional.of(false));

		record Served(String path, Map<ProgramStage, ExpandedUnit> units, AlphaTest alphaTest) {
		}

		Map<TerrainPass, Served> served = new LinkedHashMap<>();
		Set<String> reads = new LinkedHashSet<>();
		for (TerrainPass pass : TerrainPass.values()) {
			if (pass.shadow() && shadowOff) {
				continue;
			}

			Optional<ProgramResolver.Resolution> resolution = resolver.lookup(place, pass.program());
			if (resolution.isEmpty()) {
				continue;
			}

			String servedBy = resolution.get().servedBy();
			String path = pathOf(place, servedBy);
			Map<ProgramStage, ExpandedUnit> units = read(source, expander, path);
			if (!units.containsKey(ProgramStage.VERTEX) || !units.containsKey(ProgramStage.FRAGMENT)) {
				continue;
			}

			AlphaTest alphaTest = pass.alphaTest(overrides, servedBy);
			served.put(pass, new Served(path, units, alphaTest));
			reads.addAll(reads(source, units.get(ProgramStage.VERTEX), inputs, alphaTest,
					pass.covers(), pass.program(), textures.volumes()));
		}

		if (served.isEmpty()) {
			return new Terrain(Map.of(), List.of());
		}

		List<String> carried = SodiumVertex.carried(reads);
		Map<TerrainPass, Loaded> loaded = new LinkedHashMap<>();
		for (Map.Entry<TerrainPass, Served> entry : served.entrySet()) {
			TerrainPass pass = entry.getKey();
			Served one = entry.getValue();

			loaded.put(pass, bind(source.packName(), one.path(),
					ProgramTranslator.translate(one.units(), inputs, carried, one.alphaTest(),
							pass.covers(), pass.program(), textures.volumes()),
					targets, one.alphaTest(), textures));
		}

		return new Terrain(loaded, carried);
	}

	public record Terrain(Map<TerrainPass, Loaded> programs, List<String> carried) {

		public Terrain {
			programs = Map.copyOf(programs);
			carried = List.copyOf(carried);
		}
	}

	public static Distant loadDistant(Path packPath, String place, List<GeometryElement> elements,
			Map<String, OptionValue> chosen, String profile) throws IOException {
		try (OpenedPack pack = OpenedPack.open(packPath, chosen, profile)) {
			return loadDistant(pack, place, elements);
		}
	}

	public static Distant loadDistant(OpenedPack pack, String place, List<GeometryElement> elements)
			throws IOException {
		for (GeometryElement element : elements) {
			if (element.inputs() != VertexInputs.DISTANT) {
				throw new IllegalArgumentException("The far terrain is drawn from Distant Horizons' own "
						+ "mesh, so " + element.inputs() + " is not a contract its programs may be "
						+ "written against");
			}
		}

		ShaderPackSource source = pack.source();
		OptionIndex options = pack.options();
		ShaderProperties properties = pack.properties();
		SettingSet settings = pack.settings();
		Reading reading = reading(pack, place);
		IncludeExpander expander = reading.expander();
		TargetPlan targets = reading.targets();
		PackTextures textures = reading.textures();
		ProgramResolver resolver = reading.resolver();

		Map<String, AlphaTest> overrides = properties.alphaTests(settings.globalDefines(options));

		record Served(String path, Map<ProgramStage, ExpandedUnit> units, AlphaTest alphaTest,
				GeometryElement element) {
		}

		Map<String, Served> served = new LinkedHashMap<>();
		Set<String> reads = new LinkedHashSet<>();
		for (GeometryElement element : elements) {
			Optional<ProgramResolver.Resolution> resolution =
					resolver.lookup(place, element.program());
			if (resolution.isEmpty()) {
				continue;
			}

			String servedBy = resolution.get().servedBy();
			String path = pathOf(place, servedBy);
			Map<ProgramStage, ExpandedUnit> units = read(source, expander, path);
			if (!units.containsKey(ProgramStage.VERTEX)
					|| !units.containsKey(ProgramStage.FRAGMENT)) {
				continue;
			}

			AlphaTest alphaTest = overrides.getOrDefault(servedBy, element.alphaTest());
			served.put(element.element(), new Served(path, units, alphaTest, element));
			reads.addAll(reads(source, units.get(ProgramStage.VERTEX), element.inputs(), alphaTest,
					element.coverage(), element.program(), textures.volumes()));
		}

		if (served.isEmpty()) {
			return new Distant(Map.of(), List.of());
		}

		List<String> carried = DistantVertex.carried(reads);
		Map<String, Loaded> loaded = new LinkedHashMap<>();
		served.forEach((key, one) -> loaded.put(key, bind(source.packName(), one.path(),
				ProgramTranslator.translate(one.units(), one.element().inputs(), carried,
						one.alphaTest(), one.element().coverage(), one.element().program(),
						textures.volumes()),
				targets, one.alphaTest(), textures)));

		return new Distant(loaded, carried);
	}

	public record Distant(Map<String, Loaded> programs, List<String> carried) {

		public Distant {
			programs = Map.copyOf(programs);
			carried = List.copyOf(carried);
		}
	}

	public record SkyElement(String element, String program, List<String> bound, boolean coverage) {

		public SkyElement {
			bound = List.copyOf(bound);
		}

		private String translation() {
			return this.program + "|" + String.join(",", this.bound) + "|" + this.coverage;
		}
	}

	public static Map<String, Loaded> loadSky(Path packPath, String place, List<SkyElement> elements,
			Map<String, OptionValue> chosen, String profile) throws IOException {
		try (OpenedPack pack = OpenedPack.open(packPath, chosen, profile)) {
			return loadSky(pack, place, elements);
		}
	}

	public static Map<String, Loaded> loadSky(OpenedPack pack, String place,
			List<SkyElement> elements) throws IOException {
		ShaderPackSource source = pack.source();
		OptionIndex options = pack.options();
		ShaderProperties properties = pack.properties();
		SettingSet settings = pack.settings();
		Reading reading = reading(pack, place);
		IncludeExpander expander = reading.expander();
		TargetPlan targets = reading.targets();
		PackTextures textures = reading.textures();
		ProgramResolver resolver = reading.resolver();

		Map<String, Map<ProgramStage, ExpandedUnit>> expanded = new LinkedHashMap<>();
		Map<String, Loaded> translated = new LinkedHashMap<>();
		Map<String, Loaded> loaded = new LinkedHashMap<>();
		for (SkyElement element : elements) {
			Optional<ProgramResolver.Resolution> resolution =
					resolver.lookup(place, element.program());
			if (resolution.isEmpty()) {
				continue;
			}

			String path = pathOf(place, resolution.get().servedBy());
			if (!expanded.containsKey(path)) {
				expanded.put(path, read(source, expander, path));
			}

			Map<ProgramStage, ExpandedUnit> units = expanded.get(path);
			if (!units.containsKey(ProgramStage.VERTEX) || !units.containsKey(ProgramStage.FRAGMENT)) {
				continue;
			}

			translated.computeIfAbsent(element.translation(), _ -> bind(source.packName(), path,
					ProgramTranslator.translate(units, VertexInputs.SKY, element.bound(),
							AlphaTest.OFF, element.coverage(), element.program(),
							textures.volumes()),
					targets, AlphaTest.OFF, textures));
			loaded.put(element.element(), translated.get(element.translation()));
		}

		return loaded;
	}

	public static Optional<Loaded> loadClouds(Path packPath, String place,
			Map<String, OptionValue> chosen, String profile) throws IOException {
		try (OpenedPack pack = OpenedPack.open(packPath, chosen, profile)) {
			return loadClouds(pack, place);
		}
	}

	public static Optional<Loaded> loadClouds(OpenedPack pack, String place) throws IOException {
		ShaderPackSource source = pack.source();
		OptionIndex options = pack.options();
		ShaderProperties properties = pack.properties();
		SettingSet settings = pack.settings();
		Reading reading = reading(pack, place);
		IncludeExpander expander = reading.expander();
		TargetPlan targets = reading.targets();
		PackTextures textures = reading.textures();
		ProgramResolver resolver = reading.resolver();

		Optional<ProgramResolver.Resolution> resolution = resolver.lookup(place, CLOUD_PROGRAM);
		if (resolution.isEmpty()) {
			return Optional.empty();
		}

		String path = pathOf(place, resolution.get().servedBy());
		Map<ProgramStage, ExpandedUnit> units = read(source, expander, path);
		if (!units.containsKey(ProgramStage.VERTEX) || !units.containsKey(ProgramStage.FRAGMENT)) {
			return Optional.empty();
		}

		return Optional.of(bind(source.packName(), path,
				ProgramTranslator.translate(units, VertexInputs.CLOUDS, List.of(), AlphaTest.OFF,
						false, CLOUD_PROGRAM, textures.volumes()),
				targets, AlphaTest.OFF, textures));
	}

	public record GeometryElement(String element, String program, AlphaTest alphaTest,
			VertexInputs inputs, List<String> bound, boolean coverage) {

		public GeometryElement {
			bound = List.copyOf(bound);
		}

		public GeometryElement(String element, String program, AlphaTest alphaTest,
				VertexInputs inputs, boolean coverage) {
			this(element, program, alphaTest, inputs, inputs.elements(), coverage);
		}
	}

	public static Map<String, Loaded> loadGeometry(Path packPath, String place,
			List<GeometryElement> elements, Map<String, OptionValue> chosen, String profile)
			throws IOException {
		try (OpenedPack pack = OpenedPack.open(packPath, chosen, profile)) {
			return loadGeometry(pack, place, elements);
		}
	}

	public static Map<String, Loaded> loadGeometry(OpenedPack pack, String place,
			List<GeometryElement> elements) throws IOException {
		ShaderPackSource source = pack.source();
		OptionIndex options = pack.options();
		ShaderProperties properties = pack.properties();
		SettingSet settings = pack.settings();
		Reading reading = reading(pack, place);
		IncludeExpander expander = reading.expander();
		TargetPlan targets = reading.targets();
		PackTextures textures = reading.textures();
		ProgramResolver resolver = reading.resolver();

		Map<String, AlphaTest> overrides = properties.alphaTests(settings.globalDefines(options));

		Map<String, Map<ProgramStage, ExpandedUnit>> expanded = new LinkedHashMap<>();
		Map<String, Loaded> translated = new LinkedHashMap<>();
		Map<String, Loaded> loaded = new LinkedHashMap<>();
		for (GeometryElement element : elements) {
			Optional<ProgramResolver.Resolution> resolution =
					resolver.lookup(place, element.program());
			if (resolution.isEmpty()) {
				continue;
			}

			String servedBy = resolution.get().servedBy();
			String path = pathOf(place, servedBy);
			if (!expanded.containsKey(path)) {
				expanded.put(path, read(source, expander, path));
			}

			Map<ProgramStage, ExpandedUnit> units = expanded.get(path);
			if (!units.containsKey(ProgramStage.VERTEX) || !units.containsKey(ProgramStage.FRAGMENT)) {
				continue;
			}

			AlphaTest alphaTest = overrides.getOrDefault(servedBy, element.alphaTest());

			VertexInputs inputs = element.inputs();
			String key = path + "|" + alphaTest + "|" + LegacyGlsl.drawsEntities(element.program())
					+ "|" + LegacyGlsl.bindsGameTransforms(element.program()) + "|" + inputs
					+ "|" + String.join(",", element.bound()) + "|" + element.coverage();
			translated.computeIfAbsent(key, _ -> bind(source.packName(), path,
					ProgramTranslator.translate(units, inputs, element.bound(), alphaTest,
							element.coverage(), element.program(), textures.volumes()),
					targets, alphaTest, textures));
			loaded.put(element.element(), translated.get(key));
		}

		return loaded;
	}

	public static Chain loadChain(Path packPath, String dimension,
			Map<String, OptionValue> chosen, String profile, ChainFilter filter) throws IOException {
		return loadChain(packPath, dimension, chosen, profile, filter, ChainPlan.Families.DEFAULT);
	}

	public static Chain loadChain(Path packPath, String dimension,
			Map<String, OptionValue> chosen, String profile, ChainFilter filter,
			ChainPlan.Families families) throws IOException {
		try (OpenedPack pack = OpenedPack.open(packPath, chosen, profile)) {
			return loadChain(pack, dimension, filter, families);
		}
	}

	public static Chain loadChain(OpenedPack pack, String dimension, ChainFilter filter,
			ChainPlan.Families families) throws IOException {
		ShaderPackSource source = pack.source();
		OptionIndex options = pack.options();
		ShaderProperties properties = pack.properties();
		SettingSet settings = pack.settings();
		IncludeExpander expander = new IncludeExpander(source, settings);

		TargetPlan targets = TargetPlan.build(source, options, settings, properties, dimension, filter);
		String place = targets.place();

		if (targets.running().contains(FINAL) && !serves(source, pathOf(place, FINAL))) {
			targets = TargetPlan.build(source, options, settings, properties, dimension,
					filter.without(Set.of(FINAL)));
			place = targets.place();
		}

		PackTextures textures = textures(source, properties, options, settings);

		SourceMentions mentions = SourceMentions.of(source, SETTLED_EARLY);
		Map<String, ProgramTranslator.TranslatedProgram> translated = new LinkedHashMap<>();
		for (String name : targets.running()) {
			String path = pathOf(place, name);
			Map<ProgramStage, ExpandedUnit> units = read(source, expander, path);
			if (!units.containsKey(ProgramStage.VERTEX) || !units.containsKey(ProgramStage.FRAGMENT)) {
				throw new IOException(path + " is meant to run and " + source.packName()
						+ " does not serve both of its stages");
			}

			translated.put(name, translate(path, units, textures.volumes()));
		}

		Map<String, Refusal> refused =
				unbindable(translated, properties.imageSamplers(settings.globalDefines(options)));
		List<String> refusals = new ArrayList<>();
		if (refused.containsKey(FINAL)) {

			refusals.add(refusal(source.packName(), pathOf(place, FINAL), refused));
		} else if (!refused.isEmpty()) {
			targets = TargetPlan.build(source, options, settings, properties, dimension,
					filter.without(refused.keySet()));
		}

		DimensionSet dimensions = DimensionSet.discover(source);
		ProgramSet programs = ProgramSet.enumerate(source, dimensions);
		ChainPlan chain = ChainPlan.of(targets, ProgramResolver.resolve(programs, dimensions,
				properties.switchedOff(settings.globalDefines(options), options)),
				refusals, families);

		Map<String, Loaded> loaded = new LinkedHashMap<>();
		for (String name : targets.running()) {
			ProgramTranslator.TranslatedProgram program = translated.get(name);
			if (program == null) {

				throw new IllegalStateException(name + " is in the rebuilt chain of "
						+ source.packName() + " and was never translated");
			}

			loaded.put(name, bind(source.packName(), pathOf(place, name), program, targets,
					AlphaTest.OFF, textures));
		}

		return new Chain(source.packName(), place, targets, chain, loaded, refused, mentions);
	}

	private static Map<String, Refusal> unbindable(
			Map<String, ProgramTranslator.TranslatedProgram> translated, Set<String> filled) {
		Map<String, Refusal> refused = new LinkedHashMap<>();
		translated.forEach((name, program) -> {

			Map<String, TranslatedUnit.Uniform> found = new LinkedHashMap<>();
			List<String> storage = new ArrayList<>();
			program.stages().values().forEach(stage -> {
				stage.samplers().stream()
						.filter(sampler -> SamplerTypes.refused(sampler.type()))
						.forEach(sampler -> found.putIfAbsent(sampler.name(), sampler));
				stage.notes().storageBlocks().stream()
						.map(TranslatedUnit.StorageBlock::name)
						.filter(block -> !storage.contains(block))
						.filter(block -> !CustomStorage.named(block))
						.forEach(storage::add);
			});

			List<TranslatedUnit.Uniform> plain = found.values().stream()
					.filter(sampler -> !filled.contains(sampler.name())
							&& !CustomImages.named(sampler.name()))
					.toList();

			Refusal refusal = new Refusal(plain, storage);
			if (refusal.any()) {
				refused.put(name, refusal);
			}
		});

		return refused;
	}

	private static String refusal(String packName, String path, Map<String, Refusal> refused) {
		String line = path + " " + refused.get(FINAL).reason()
				+ ", and a final cannot be taken out of a chain, so nothing of " + packName
				+ " can be drawn";

		return refused.size() == 1
				? line
				: line + " (" + (refused.size() - 1) + " other passes of this place are refused as "
						+ "well: " + names(refused) + ")";
	}

	private static String names(Map<String, Refusal> refused) {
		return refused.keySet().stream()
				.filter(name -> !name.equals(FINAL))
				.collect(Collectors.joining(", "));
	}

	private static ProgramTranslator.TranslatedProgram translate(String path,
			Map<ProgramStage, ExpandedUnit> units, Map<String, VolumeAtlas> volumes) {
		try {
			return ProgramTranslator.translate(units, VertexInputs.FULLSCREEN,
					VertexInputs.FULLSCREEN.elements(), AlphaTest.OFF, false, programOf(path), volumes);
		} catch (RuntimeException e) {

			throw new IllegalStateException(path + " could not be translated", e);
		}
	}

	private static Loaded bind(String packName, String path,
			ProgramTranslator.TranslatedProgram program, TargetPlan targets, AlphaTest alphaTest,
			PackTextures textures) {

		Optional<TextureStage> stage = TextureStage.of(programOf(path));
		Set<String> supplied = stage.map(textures::suppliedTo).orElse(Set.of());

		return new Loaded(packName, path, program, targets,
				SamplerPlan.of(declaredIn(program), typesIn(program), targets, path, supplied,
						stage.map(textures::picturesTo).orElse(Set.of())),
				alphaTest, supplied);
	}

	private static List<String> declaredIn(ProgramTranslator.TranslatedProgram program) {
		return program.samplers().stream().map(TranslatedUnit.Uniform::name).toList();
	}

	private static Map<String, String> typesIn(ProgramTranslator.TranslatedProgram program) {
		Set<String> retyped = program.stages().values().stream()
				.flatMap(stage -> stage.notes().retypedSamplers().stream())
				.collect(Collectors.toSet());

		Map<String, String> types = new LinkedHashMap<>();
		for (TranslatedUnit.Uniform sampler : program.samplers()) {
			if (!retyped.contains(sampler.name())) {
				types.putIfAbsent(sampler.name(), sampler.type());
			}
		}

		return types;
	}

	private static PackTextures textures(ShaderPackSource source, ShaderProperties properties,
			OptionIndex options, SettingSet settings) throws IOException {
		CustomImages.install(properties.imageDirectives(settings.globalDefines(options)));
		CustomStorage.install(properties.bufferObjects(settings.globalDefines(options)));
		return PackTextures.read(properties, settings.globalDefines(options), source);
	}

	private static boolean serves(ShaderPackSource source, String path) {
		return source.file(path + "." + ProgramStage.VERTEX.extension()).isPresent()
				&& source.file(path + "." + ProgramStage.FRAGMENT.extension()).isPresent();
	}

	private static Map<ProgramStage, ExpandedUnit> read(ShaderPackSource source,
			IncludeExpander expander, String path) throws IOException {
		Map<ProgramStage, ExpandedUnit> units = new LinkedHashMap<>();
		for (ProgramStage stage : STAGES) {
			Optional<Path> file = source.file(path + "." + stage.extension());
			if (file.isPresent()) {
				units.put(stage, expander.expand(file.get()));
			}
		}

		return units;
	}

	private static String pathOf(String place, String program) {
		return place.isEmpty() ? program : place + "/" + program;
	}

	private static String dimensionOf(String path) {
		int slash = path.indexOf('/');

		return slash < 0 ? ProgramSet.ROOT : path.substring(0, slash);
	}

	private static String programOf(String path) {
		return path.substring(path.lastIndexOf('/') + 1);
	}
}
