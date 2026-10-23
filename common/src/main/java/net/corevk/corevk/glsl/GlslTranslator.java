package net.corevk.corevk.glsl;

import net.corevk.corevk.glsl.GlslLexer.Kind;
import net.corevk.corevk.glsl.GlslLexer.Token;
import net.corevk.corevk.glsl.TokenStream.Closing;
import net.corevk.corevk.pack.option.EngineDefines;
import net.corevk.corevk.pack.model.AlphaTest;
import net.corevk.corevk.pack.model.ProgramNames;
import net.corevk.corevk.pack.model.ProgramStage;
import net.corevk.corevk.pack.source.IncludeExpander.ExpandedUnit;
import net.corevk.corevk.pack.target.ConstDirectives;
import net.corevk.corevk.pack.target.DrawBuffers;
import net.corevk.corevk.pack.target.SamplerPlan;
import net.corevk.corevk.pack.target.SamplerTypes;
import net.corevk.corevk.pack.model.TargetName;
import net.corevk.corevk.pack.texture.CustomImages;
import net.corevk.corevk.pack.texture.CustomImageView;
import net.corevk.corevk.pack.texture.VolumeAtlas;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public final class GlslTranslator {

	static final String FOG_COORD = "of_FogFragCoord";

	static final String FRONT_COLOUR = "of_FrontColor";

	static final String ENTITY_COLOR = "entityColor";

	static final List<String> ENTITY_IDS = List.copyOf(LegacyGlsl.ENTITY_UNIFORMS.keySet());

	static final String OVERLAY = LegacyGlsl.OVERLAY_SAMPLER;

	private static final int MAX_FRAGMENT_OUTPUTS = 8;

	static final String ORDER_OUTPUTS = "ofOrderOutputs";

	static final String PACK_MAIN = "ofPackMain";

	static final String DEPTH_CONV = "of_DepthConv";

	static final String SHADOW_COMPARE = "ofShadowCompare";

	private static final Set<String> COMPARABLE_LOOKUPS = Set.of("texture", "textureLod",
			"textureOffset", "textureLodOffset");

	private static final boolean SOFT_COMPARE = Boolean.getBoolean("corevk.softShadowCompare");

	private static volatile boolean softCompareArmed;

	private static volatile boolean shadowChainZero;
	private static volatile boolean shadowChainOne;

	static final String REDUCED_SIN = "ofReducedSin";

	static final String REDUCED_COS = "ofReducedCos";

	private static volatile boolean reduceTrig = true;

	private static final AtomicInteger TRIG_SITES = new AtomicInteger();

	public static void reduceTrig(boolean on) {
		reduceTrig = on;
		TRIG_SITES.set(0);
	}

	public static int trigSites() {
		return TRIG_SITES.get();
	}

	static final String HASH = "ofHash";

	private static final String CENTER_DEPTH = "centerDepthSmooth";

	private static final String CENTER_DEPTH_TYPE = "float";

	private static final String SAMPLER_2D = "sampler2D";

	private static final String DRAW_MODEL_VIEW =
			"(" + LegacyGlsl.CAMERA_BOB + " * " + LegacyGlsl.GAME_MODEL_VIEW + ")";

	static final String LOOKUP = "texture";

	static final String LEVELLED_LOOKUP = "textureLod";

	private static final String SHADOW_SHAPE = "Shadow";

	private static final Set<String> LEVELLED_SHADOW_SHAPES =
			Set.of("1DShadow", "1DArrayShadow", "2DShadow");

	private static final String BASE_LEVEL = "0.0";

	private static final String MIPMAP_SUFFIX = "MipmapEnabled";

	private static final String TEX_SHRINK = "of_TexShrink";

	private static final String PASS_COLOUR = "of_PassColour";

	private static final int MAX_SOURCE_CHARACTERS = 4_000_000;

	private static final int MAX_MACRO_HOPS = 8;

	private final ExpandedUnit unit;
	private final ProgramStage stage;
	private final Map<String, String> engineDefines;

	private final VertexInputs inputs;

	private final List<String> bound;

	private final String program;

	private final VolumeFlattening volumes;

	private final AlphaTest alphaTest;

	private final boolean coverage;
	private final TokenStream tokens;

	private final VaryingSplit splits;

	private final Set<String> packMacros = new HashSet<>();

	private final Set<Integer> macroParameterTokens = new HashSet<>();

	private final Map<String, Set<String>> macroBodies = new HashMap<>();

	private final Map<String, String> macroAliases = new HashMap<>();

	private final Map<String, Boolean> macroConstant = new HashMap<>();

	private final Set<String> declaredNames = new HashSet<>();

	private final Set<String> shadowedBuiltins = new HashSet<>();

	private final Map<String, String> blockMembers = new LinkedHashMap<>();
	private final Map<String, String> samplers = new LinkedHashMap<>();

	private final Map<String, String> memoryQualifiers = new LinkedHashMap<>();

	private final Map<String, String> imageFormats = new LinkedHashMap<>();

	private final List<TranslatedUnit.StorageBlock> storageBlocks = new ArrayList<>();

	private final List<Scoped> comparisonSamplers = new ArrayList<>();

	private final List<Scoped> hardwareComparisonSamplers = new ArrayList<>();

	private final List<Scoped> retypedSamplers = new ArrayList<>();

	private final List<Scoped> samplerParameters = new ArrayList<>();

	private final Set<Scoped> unlevelledParameters = new LinkedHashSet<>();

	private final List<SamplerParameter> typedParameters = new ArrayList<>();

	private final Map<Integer, Output> packOutputs = new TreeMap<>();

	private final Set<String> injectedNames = new HashSet<>();
	private final List<String> conflicts = new ArrayList<>();
	private final List<Integer> drawBuffers = new ArrayList<>();

	private final Map<String, String> synthesized = new LinkedHashMap<>();

	private boolean coreProfile;

	private final List<FileScope> declaredInputs = new ArrayList<>();

	private final Set<String> declaredOutputs = new LinkedHashSet<>();

	private final List<FileScope> declaredOutputScopes = new ArrayList<>();

	private final Set<String> droppedInputs = new LinkedHashSet<>();

	private final Map<String, String> unprovidedInputs = new LinkedHashMap<>();

	private final Map<String, String> owedOutputs = new LinkedHashMap<>();

	private Set<String> declaredAfter = Set.of();
	private Set<String> used = Set.of();

	private boolean mainWrapped;

	private int maxFragmentOutput = -1;

	private boolean legacySlotZero;

	private boolean ordered;
	private int dynamicFragData;
	private int shadowCalls;
	private int unwrappedShadowCalls;

	private int softRewrites;

	private int trigCalls;

	private int trigSites;

	private int hashCalls;

	private final Set<String> packBuiltinCalls = new HashSet<>();

	private int gameTextureMatrix;

	private int gameModelView;

	private int strippedExtensions;

	private final Set<String> extensions = new LinkedHashSet<>();
	private boolean depthEpilogue;
	private boolean terrainPrologue;

	private boolean distantPrologue;
	private boolean alphaEpilogue;

	private boolean entityWrapped;

	private boolean linesWrapped;

	private boolean makesOverlayColour;

	private boolean covers;

	private boolean namesFragDepth;

	private int packMainName = -1;

	private int[] regions = new int[0];

	private int depthLookups;
	private int parameterLookups;

	private int pinnedLookups;

	private int unpinnedParameterLookups;
	private int fragCoordZ;
	private int fragCoordXyz;
	private int fragCoordUnhandled;
	private int fragDepthWrites;
	private int fragDepthUnhandled;

	private GlslTranslator(ExpandedUnit unit, ProgramStage stage, Map<String, String> engineDefines,
			VertexInputs inputs, List<String> bound, AlphaTest alphaTest, boolean coverage,
			String program, Map<String, VolumeAtlas> volumes) {
		this.unit = unit;
		this.stage = stage;
		this.engineDefines = engineDefines;
		this.inputs = inputs;
		this.bound = List.copyOf(bound);
		this.alphaTest = alphaTest;
		this.coverage = coverage;
		this.program = program;

		String text = unit.text();
		if (text.length() > MAX_SOURCE_CHARACTERS) {
			throw new IllegalStateException(unit.entry() + " expands to " + text.length()
					+ " characters, past the " + MAX_SOURCE_CHARACTERS + " a unit is allowed");
		}

		this.tokens = new TokenStream(GlslLexer.lex(text));
		this.splits = new VaryingSplit(this.tokens, unit, stage, this::fileScopeDeclaration,
				this.macroAliases);
		this.volumes = new VolumeFlattening(this.tokens, unit, volumes, this.packMacros,
				this.macroAliases);
	}

	public static TranslatedUnit translate(ExpandedUnit unit, ProgramStage stage) {
		return translate(unit, stage, "");
	}

	public static TranslatedUnit translate(ExpandedUnit unit, ProgramStage stage, String program) {
		Stage prepared = prepare(unit, stage, VertexInputs.WORLD, AlphaTest.OFF, program);

		return prepared.render(prepared.uniforms(), prepared.samplers(), prepared.varyings());
	}

	public static Stage prepare(ExpandedUnit unit, ProgramStage stage, VertexInputs inputs,
			AlphaTest alphaTest, String program) {
		return prepare(unit, stage, inputs, inputs.elements(), alphaTest, false, program);
	}

	public static Stage prepare(ExpandedUnit unit, ProgramStage stage, VertexInputs inputs,
			List<String> bound, AlphaTest alphaTest, boolean coverage, String program) {
		return prepare(unit, stage, inputs, bound, alphaTest, coverage, program, Map.of());
	}

	public static Stage prepare(ExpandedUnit unit, ProgramStage stage, VertexInputs inputs,
			List<String> bound, AlphaTest alphaTest, boolean coverage, String program,
			Map<String, VolumeAtlas> volumes) {
		GlslTranslator translator = new GlslTranslator(unit, stage,
				EngineDefines.table(EngineDefines.machine()), inputs, bound, alphaTest, coverage,
				program, volumes);
		translator.rewrite();

		return new Stage(translator);
	}

	public static void askSoftCompare(boolean asked) {
		softCompareArmed = asked;
	}

	public static void askShadowChains(boolean shadowtex0, boolean shadowtex1) {
		shadowChainZero = shadowtex0;
		shadowChainOne = shadowtex1;
	}

	private static boolean softCompare() {
		return SOFT_COMPARE || softCompareArmed;
	}

	public static String emissionSwitches() {
		return (reduceTrig ? "trig-reduced" : "trig-driver")
				+ (softCompare() ? " compare-in-shader" : " compare-on-sampler")
				+ " shadow-chain-" + (shadowChainZero ? '1' : '0') + (shadowChainOne ? '1' : '0')
				+ " custom-views-v1:" + CustomImages.key();
	}

	private void rewrite() {
		collectMacroNames();
		collectDeclarations();

		collectComparisonSamplers();

		collectSamplerParameters();
		collectStorageBlocks();
		collectDrawBuffers();
		synthesizeAttributes();
		dropVersionAndExtensions();
		settleRedefinedMacros();
		dropRedeclaredVertexBlock();
		flattenInterfaceBlocks();
		hideAbsentExtensionMacros();
		rewriteIdentifiers();

		rewriteGoldbergHash();

		this.volumes.flatten();
		convertDepth();
		dropPrecision();

		demoteNonConstantInitialisers();
		rewriteFragmentOutputs();
		liftFragmentOutputs();

		moveCenterDepth();
		liftUniforms();
		nameCustomImageViews();

		pinLookupLevels();

		planAlphaEpilogue();
		planCoverage();

		this.regions = regions();
		orderFragmentOutputs();
		wrapMain();

		this.splits.split();
		wrapMainForSplits();

		collectVaryings();

		this.used = usedNames();
		this.declaredAfter = declaredUnderAType();

		TRIG_SITES.addAndGet(this.trigSites);
	}

	public static final class Stage {

		private final GlslTranslator translator;

		private Stage(GlslTranslator translator) {
			this.translator = translator;
		}

		public ProgramStage stage() {
			return this.translator.stage;
		}

		public List<TranslatedUnit.Uniform> uniforms() {
			return this.translator.ownBlock();
		}

		public List<TranslatedUnit.Uniform> samplers() {
			return asUniforms(this.translator.samplers);
		}

		public Set<String> sampled() {
			return this.translator.sampledNames();
		}

		public Map<String, String> memoryQualifiers() {
			return Collections.unmodifiableMap(new LinkedHashMap<>(this.translator.memoryQualifiers));
		}

		public Map<String, String> imageFormats() {
			return Collections.unmodifiableMap(new LinkedHashMap<>(this.translator.imageFormats));
		}

		public Map<String, String> synthesized() {
			return Collections.unmodifiableMap(new LinkedHashMap<>(this.translator.synthesized));
		}

		public Set<String> reads() {
			if (this.translator.inputs == VertexInputs.DISTANT) {
				return DistantVertex.reads(this.translator.used, this.translator.synthesized);
			}

			return this.translator.inputs.terrain()
					? SodiumVertex.reads(this.translator.used, this.translator.synthesized,
							this.translator.inputs.separateAo())
					: Set.of();
		}

		public Set<String> varyings() {
			Set<String> named = new LinkedHashSet<>();
			if (this.translator.used.contains(FOG_COORD)) {
				named.add(FOG_COORD);
			}

			if (this.translator.inputs.overlay()) {
				if (this.translator.used.contains(ENTITY_COLOR)) {
					named.add(ENTITY_COLOR);
				}

				for (String identifier : ENTITY_IDS) {
					if (this.translator.used.contains(identifier)) {
						named.add(identifier);
					}
				}
			}

			return Set.copyOf(named);
		}

		public void makesOverlayColour(Set<String> varyings) {
			if (this.translator.entityWrapped && varyings.contains(ENTITY_COLOR)) {
				this.translator.makesOverlayColour = true;
				this.translator.samplers.put(OVERLAY, SAMPLER_2D + " " + OVERLAY);
			}
		}

		public Set<String> declared() {
			return this.translator.declaredAfter;
		}

		public Set<String> lifted() {
			return this.translator.blockMembers.keySet();
		}

		public Set<String> provides() {
			Set<String> names = new LinkedHashSet<>(this.translator.declaredOutputs);

			names.addAll(this.translator.splits.interfaceNames(false));

			return Set.copyOf(names);
		}

		public Set<String> requires() {
			Set<String> names = new LinkedHashSet<>();
			this.translator.declaredInputs.forEach(declared -> names.addAll(declared.names()));

			names.addAll(this.translator.splits.interfaceNames(true));
			names.removeAll(this.translator.droppedInputs);

			return Set.copyOf(names);
		}

		public void withhold(Set<String> declared) {
			this.translator.withholdUndeclaredOutputs(declared);
		}

		public void dropUnprovidedInputs(Set<String> provided) {
			this.translator.dropUnprovidedInputs(provided);
		}

		public Map<String, String> unprovided() {
			return Collections.unmodifiableMap(new LinkedHashMap<>(this.translator.unprovidedInputs));
		}

		public void owe(Map<String, String> declarations) {
			declarations.forEach((name, qualified) -> {
				if (!name.startsWith("gl_") && !this.translator.declaredOutputs.contains(name)) {
					this.translator.owedOutputs.put(name, qualified);
					this.translator.declaredOutputs.add(name);
				}
			});

			if (!this.translator.owedOutputs.isEmpty()) {
				this.translator.wrapMainForOwedOutputs();
			}
		}

		public TranslatedUnit render(List<TranslatedUnit.Uniform> block,
				List<TranslatedUnit.Uniform> samplers, Set<String> varyings) {
			return render(block, samplers, varyings, Set.of());
		}

		public TranslatedUnit render(List<TranslatedUnit.Uniform> block,
				List<TranslatedUnit.Uniform> samplers, Set<String> varyings, Set<String> shadowed) {
			return this.translator.render(block, samplers, varyings, shadowed,
					this.translator.memoryQualifiers, this.translator.imageFormats);
		}

		public TranslatedUnit render(List<TranslatedUnit.Uniform> block,
				List<TranslatedUnit.Uniform> samplers, Set<String> varyings, Set<String> shadowed,
				Map<String, String> sharedMemory, Map<String, String> sharedFormats) {
			return this.translator.render(block, samplers, varyings, shadowed, sharedMemory, sharedFormats);
		}
	}

	private TranslatedUnit render(List<TranslatedUnit.Uniform> block,
			List<TranslatedUnit.Uniform> samplers, Set<String> varyings, Set<String> shadowed,
			Map<String, String> sharedMemory, Map<String, String> sharedFormats) {
		Map<String, String> memory = new LinkedHashMap<>(sharedMemory);
		memory.putAll(this.memoryQualifiers);
		Map<String, String> formats = new LinkedHashMap<>(sharedFormats);
		formats.putAll(this.imageFormats);
		Emitter emitter = emitter(memory, formats);

		return new TranslatedUnit(this.unit.entry(), this.stage,
				emitter.header(block, samplers, varyings, shadowed) + body(shadowed) + "\n"
						+ emitter.wrapper(varyings, shadowed), notes(),
				List.copyOf(this.drawBuffers), List.copyOf(block), List.copyOf(samplers));
	}

	private String body(Set<String> shadowed) {
		undefineRedefinedMacros();
		if (shadowed.isEmpty()) {
			return this.tokens.join();
		}

		StringBuilder text = new StringBuilder();
		for (int index = 0; index < this.tokens.size(); index++) {
			Token token = this.tokens.get(index);
			boolean rename = token.kind() == Kind.IDENTIFIER
					&& shadowed.contains(token.text())
					&& !token.macroName()
					&& (token.directive() == null
							|| !LegacyGlsl.OPAQUE_DIRECTIVES.contains(token.directive()));

			text.append(rename ? "ofOwn_" + token.text() : token.text());
		}

		return text.toString();
	}

	private List<TranslatedUnit.Uniform> ownBlock() {
		List<TranslatedUnit.Uniform> block = new ArrayList<>();

		for (Map.Entry<String, String> member : LegacyGlsl.FIXED_FUNCTION_MEMBERS.entrySet()) {
			if (this.used.contains(member.getKey())) {
				block.add(TranslatedUnit.Uniform.of(member.getKey(), member.getValue()));
			}
		}

		for (Map.Entry<String, String> member : LegacyGlsl.CORE_MATRICES.entrySet()) {
			if (this.used.contains(member.getKey()) && !this.declaredNames.contains(member.getKey())) {
				block.add(TranslatedUnit.Uniform.of(member.getKey(), member.getValue()));
			}
		}

		if (LegacyGlsl.drawsEntities(this.program) && !this.inputs.overlay()) {
			for (Map.Entry<String, String> member : LegacyGlsl.ENTITY_UNIFORMS.entrySet()) {
				if (this.used.contains(member.getKey())
						&& !this.declaredNames.contains(member.getKey())) {
					block.add(TranslatedUnit.Uniform.of(member.getKey(), member.getValue()));
				}
			}
		}

		if (this.inputs == VertexInputs.GLINT && this.used.contains("of_Color")) {
			block.add(TranslatedUnit.Uniform.of(LegacyGlsl.GLINT_ALPHA,
					"float " + LegacyGlsl.GLINT_ALPHA));
		}

		if (this.used.contains(LegacyGlsl.CAMERA_BOB)) {
			block.add(TranslatedUnit.Uniform.of(LegacyGlsl.CAMERA_BOB,
					"mat4 " + LegacyGlsl.CAMERA_BOB));
		}

		if (this.linesWrapped) {
			for (String name : List.of("viewWidth", "viewHeight")) {
				if (!this.declaredNames.contains(name)) {
					block.add(TranslatedUnit.Uniform.of(name, "float " + name));
				}
			}
		}

		block.addAll(asUniforms(this.blockMembers));

		return block;
	}

	static List<TranslatedUnit.Uniform> asUniforms(Map<String, String> declarations) {
		return declarations.entrySet().stream()
				.map(entry -> TranslatedUnit.Uniform.of(entry.getKey(), entry.getValue()))
				.toList();
	}

	private void collectMacroNames() {
		int[] lines = this.tokens.lineNumbers();
		for (int index = 0; index < this.tokens.size(); index++) {
			Token token = this.tokens.get(index);
			if (token.kind() != Kind.HASH || !LegacyGlsl.NAMING_DIRECTIVES.contains(token.directive())) {
				continue;
			}

			int name = this.tokens.macroNameAfter(index);
			if (name < 0) {
				continue;
			}

			this.tokens.naming(name);
			if (token.directive().equals("define")) {
				markMacroParameters(name);
				String macro = this.tokens.get(name).text();
				this.packMacros.add(macro);
				this.macroBodies.computeIfAbsent(macro, _ -> new HashSet<>()).addAll(bodyNames(name));

				if (this.unit.isLive(lines[index])) {
					this.macroAliases.merge(macro, aliasOf(name).orElse(""),
							(first, second) -> first.equals(second) ? first : "");
				}
			}
		}
	}

	private Optional<String> aliasOf(int name) {
		String target = null;
		for (int scan = name + 1; scan < this.tokens.size(); scan++) {
			Token token = this.tokens.get(scan);
			if (token.kind() == Kind.NEWLINE) {
				break;
			}

			if (token.trivia()) {
				continue;
			}

			if (token.kind() != Kind.IDENTIFIER || target != null) {
				return Optional.empty();
			}

			target = token.text();
		}

		return Optional.ofNullable(target);
	}

	static String storageWord(Token token, Map<String, String> aliases) {
		if (token.kind() != Kind.IDENTIFIER) {
			return null;
		}

		String word = token.text().equals("in") || token.text().equals("out")
				? token.text()
				: aliases.getOrDefault(token.text(), "");

		return word.equals("in") || word.equals("out") ? word : null;
	}

	private Set<String> bodyNames(int name) {
		Set<String> parameters = new HashSet<>();
		int scan = macroParameters(name, parameters);

		Set<String> names = new HashSet<>();
		for (; scan < this.tokens.size(); scan++) {
			Token token = this.tokens.get(scan);
			if (token.kind() == Kind.NEWLINE) {
				break;
			}

			if (token.kind() == Kind.IDENTIFIER && !parameters.contains(token.text())) {
				names.add(token.text());
			}
		}

		return names;
	}

	private int macroParameters(int name, Set<String> parameters) {
		int scan = name + 1;
		if (scan >= this.tokens.size() || !this.tokens.get(scan).operator("(")) {
			return scan;
		}

		for (scan++; scan < this.tokens.size(); scan++) {
			Token token = this.tokens.get(scan);
			if (token.kind() == Kind.NEWLINE || token.operator(")")) {
				break;
			}

			if (token.kind() == Kind.IDENTIFIER) {
				parameters.add(token.text());
			}
		}

		return scan;
	}

	private void markMacroParameters(int name) {
		Set<String> parameters = new HashSet<>();
		int scan = macroParameters(name, parameters);
		if (parameters.isEmpty()) {
			return;
		}

		for (int index = name + 1; index < scan; index++) {
			this.macroParameterTokens.add(index);
		}

		for (; scan < this.tokens.size(); scan++) {
			Token token = this.tokens.get(scan);
			if (token.kind() == Kind.NEWLINE) {
				return;
			}

			if (token.kind() == Kind.IDENTIFIER && parameters.contains(token.text())) {
				this.macroParameterTokens.add(scan);
			}
		}
	}

	private void collectDeclarations() {
		for (int index = 0; index < this.tokens.size(); index++) {
			Token token = this.tokens.get(index);
			if (token.kind() != Kind.IDENTIFIER || token.directive() != null) {
				continue;
			}

			int before = this.tokens.significantBefore(index);
			if (before < 0 || !LegacyGlsl.TYPE_NAMES.contains(this.tokens.get(before).text())) {
				continue;
			}

			this.declaredNames.add(token.text());
			this.declaredNames.addAll(continuationDeclarators(index));
			if (LegacyGlsl.POST_120_BUILTINS.contains(token.text()) && this.tokens.callOpener(index) >= 0) {
				this.shadowedBuiltins.add(token.text());
			}
		}
	}

	private List<String> continuationDeclarators(int first) {
		int end = this.tokens.statementEnd(first);
		if (end < 0) {
			return List.of();
		}

		List<String> names = new ArrayList<>();
		int depth = 0;
		boolean expectName = false;

		for (int index = this.tokens.significantAfter(first); index >= 0 && index <= end;
				index = this.tokens.significantAfter(index)) {
			Token token = this.tokens.get(index);
			if (token.operator("(") || token.operator("[") || token.operator("{")) {
				depth++;
			} else if (token.operator(")") || token.operator("]") || token.operator("}")) {
				if (depth == 0) {
					return names;
				}

				depth--;
			} else if (depth == 0 && token.operator(",")) {
				expectName = true;
			} else if (expectName) {
				if (token.kind() != Kind.IDENTIFIER || !declaratorFollows(index)) {
					return names;
				}

				names.add(token.text());
				expectName = false;
			}
		}

		return names;
	}

	private boolean declaratorFollows(int name) {
		int next = this.tokens.significantAfter(name);
		if (next < 0) {
			return false;
		}

		Token token = this.tokens.get(next);

		return token.operator(",") || token.operator(";") || token.operator("=")
				|| token.operator("[");
	}

	private void collectDrawBuffers() {
		DrawBuffers.parse(this.unit).stream().limit(MAX_FRAGMENT_OUTPUTS).forEach(this.drawBuffers::add);
	}

	private void dropVersionAndExtensions() {
		for (int index = 0; index < this.tokens.size(); index++) {
			Token token = this.tokens.get(index);
			if (token.kind() != Kind.HASH) {
				continue;
			}

			if (token.directive().equals("extension")) {
				this.strippedExtensions++;
				String named = extensionNamed(index);

				if (named != null && !VendorExtensions.absent(named, this.stage)) {
					this.extensions.add(named);
				}
			} else if (!token.directive().equals("version")) {
				continue;
			} else {
				this.coreProfile = declaresCoreProfile(index);
			}

			this.tokens.blankDirective(index);
		}
	}

	private void settleRedefinedMacros() {
		Map<String, List<Integer>> sites = new LinkedHashMap<>();
		Map<String, List<String>> bodies = new LinkedHashMap<>();

		int[] lines = this.tokens.lineNumbers();
		for (int index = 0; index < this.tokens.size(); index++) {
			Token token = this.tokens.get(index);
			if (token.kind() != Kind.HASH || !"define".equals(token.directive())
					|| !this.unit.isLive(lines[index])) {
				continue;
			}

			int name = this.tokens.macroNameAfter(index);
			if (name < 0) {
				continue;
			}

			String named = this.tokens.get(name).text();
			sites.computeIfAbsent(named, ignored -> new ArrayList<>()).add(index);
			bodies.computeIfAbsent(named, ignored -> new ArrayList<>()).add(macroBody(name));
		}

		sites.forEach((named, at) -> {
			List<String> written = bodies.get(named);
			if (at.size() < 2 || written.stream().allMatch(written.get(0)::equals)
					|| usedBetween(named, at.get(0), at.get(at.size() - 1))) {
				return;
			}

			for (int which = 0; which < at.size() - 1; which++) {
				this.tokens.blankDirective(at.get(which));
			}
		});
	}

	private void undefineRedefinedMacros() {
		int[] lines = this.tokens.lineNumbers();
		Map<String, String> inForce = new HashMap<>();
		Map<Integer, String> redefinitions = new LinkedHashMap<>();
		for (int index = 0; index < this.tokens.size(); index++) {
			Token token = this.tokens.get(index);
			if (token.kind() != Kind.HASH || !this.unit.isLive(lines[index])) {
				continue;
			}

			boolean define = "define".equals(token.directive());
			if (!define && !"undef".equals(token.directive())) {
				continue;
			}

			int name = this.tokens.macroNameAfter(index);
			if (name < 0) {
				continue;
			}

			String named = this.tokens.get(name).text();
			if (!define) {
				inForce.remove(named);
				continue;
			}

			String body = macroBody(name);
			String previous = inForce.put(named, body);

			if (previous != null && !previous.equals(body) && token.text().equals("#")) {
				redefinitions.put(index, named);
			}
		}

		redefinitions.forEach((index, named) ->
				this.tokens.replace(index, "#undef " + named + "\n" + this.tokens.get(index).text()));
	}

	private boolean usedBetween(String named, int first, int last) {
		int[] lines = this.tokens.lineNumbers();
		for (int index = first + 1; index < last; index++) {
			Token token = this.tokens.get(index);
			if (token.kind() == Kind.IDENTIFIER && !token.macroName() && named.equals(token.text())
					&& this.unit.isLive(lines[index])) {
				return true;
			}
		}

		return false;
	}

	private void dropRedeclaredVertexBlock() {
		if (this.stage != ProgramStage.VERTEX) {
			return;
		}

		int[] lines = this.tokens.lineNumbers();
		for (int index = 0; index < this.tokens.size(); index++) {
			Token token = this.tokens.get(index);
			if (token.directive() != null || !token.identifier("gl_PerVertex")
					|| !this.unit.isLive(lines[index])) {
				continue;
			}

			int keyword = this.tokens.significantBefore(index);
			if (keyword < 0 || !this.tokens.get(keyword).identifier("out")) {
				continue;
			}

			int brace = this.tokens.significantAfter(index);
			if (brace < 0 || !this.tokens.get(brace).operator("{")) {
				continue;
			}

			int close = this.tokens.matchingBracket(brace);
			int end = close < 0 ? -1 : this.tokens.significantAfter(close);
			while (end >= 0 && !this.tokens.get(end).operator(";")) {
				end = this.tokens.significantAfter(end);
			}

			if (end >= 0) {
				this.tokens.blankRange(keyword, end);
			}
		}
	}

	private static final Set<String> INTERPOLATION_QUALIFIERS = Set.of("flat", "smooth", "noperspective");
	private static final Set<String> OTHER_QUALIFIERS = Set.of("centroid", "sample", "invariant", "precise",
			"highp", "mediump", "lowp");

	private record BlockMember(int typeAt, List<Integer> names, int layoutAt, Set<String> qualifiers) {
	}

	private void flattenInterfaceBlocks() {
		int[] lines = this.tokens.lineNumbers();
		List<TokenStream.Insertion> insertions = new ArrayList<>();
		for (int index = 0; index < this.tokens.size(); index++) {
			Token token = this.tokens.get(index);
			if (token.directive() != null || !(token.identifier("in") || token.identifier("out"))
					|| !this.unit.isLive(lines[index])) {
				continue;
			}

			int name = blockNameAfter(index);
			if (name < 0 || this.tokens.get(name).text().startsWith("gl_")) {
				continue;
			}

			int brace = this.tokens.significantAfter(name);
			if (brace < 0 || !this.tokens.get(brace).operator("{")) {
				continue;
			}

			int close = this.tokens.matchingBracket(brace);
			int after = close < 0 ? -1 : this.tokens.significantAfter(close);
			if (after < 0) {
				continue;
			}

			String instance = null;
			int end = after;
			List<Token> corners = List.of();
			if (this.tokens.get(after).kind() == Kind.IDENTIFIER) {
				instance = this.tokens.get(after).text();
				end = this.tokens.significantAfter(after);

				if (end >= 0 && this.tokens.get(end).operator("[") && token.identifier("in")
						&& this.stage == ProgramStage.GEOMETRY) {
					int closing = this.tokens.matchingBracket(end);
					if (closing < 0) {
						index = close;
						continue;
					}

					List<Token> suffix = new ArrayList<>();
					for (int at = end; at <= closing; at++) {
						Token piece = this.tokens.get(at);
						if (!piece.trivia() && piece.kind() != Kind.NEWLINE) {
							suffix.add(piece);
						}
					}

					corners = List.copyOf(suffix);
					end = this.tokens.significantAfter(closing);
				}
			}

			if (end < 0 || !this.tokens.get(end).operator(";")) {
				index = close;
				continue;
			}

			int start = index;
			List<String> carried = new ArrayList<>();
			boolean patch = false;
			for (int before = this.tokens.significantBefore(index); before >= 0;
					before = this.tokens.significantBefore(before)) {
				Token qualifier = this.tokens.get(before);
				if (qualifier.identifier("patch")) {
					patch = true;
				} else if (qualifier.kind() == Kind.IDENTIFIER && (INTERPOLATION_QUALIFIERS.contains(qualifier.text())
						|| OTHER_QUALIFIERS.contains(qualifier.text()))) {
					carried.add(0, qualifier.text());
				} else if (qualifier.operator(")")) {
					int layout = layoutBefore(before);
					if (layout < 0) {
						break;
					}

					before = layout;
				} else {
					break;
				}

				start = before;
			}

			if (patch) {
				index = close;
				continue;
			}

			List<BlockMember> members = new ArrayList<>();
			List<Integer> declaration = new ArrayList<>();
			boolean readable = true;
			for (int at = brace + 1; at < close && readable; at++) {
				Token piece = this.tokens.get(at);
				if (piece.directive() != null || piece.trivia() || piece.kind() == Kind.NEWLINE
						|| piece.text().isEmpty()) {
					continue;
				}

				if (!piece.operator(";")) {
					declaration.add(at);
					continue;
				}

				BlockMember member = readBlockMember(declaration);
				readable = member != null;
				members.add(member);
				declaration.clear();
			}

			if (!corners.isEmpty() && members.stream().flatMap(member -> member.names().stream())
					.anyMatch(at -> {
						int next = this.tokens.significantAfter(at);
						return next >= 0 && this.tokens.get(next).operator("[");
					})) {
				readable = false;
			}

			if (!readable || !declaration.isEmpty()) {
				index = close;
				continue;
			}

			String storage = token.text();
			String prefix = instance == null ? "" : "of_" + this.tokens.get(name).text() + "_";
			List<String> names = new ArrayList<>();
			for (BlockMember member : members) {
				flattenMember(member, storage, carried, prefix, corners, names, insertions);
			}

			blankCode(start, brace);
			blankCode(close, end);
			if (instance != null && corners.isEmpty()) {
				rewriteBlockReads(instance, prefix, names);
			} else if (instance != null) {
				rewriteCornerReads(instance, prefix, names);
			}

			index = close;
		}

		this.tokens.insertTokens(insertions);
	}

	private int blockNameAfter(int storage) {
		for (int scan = storage + 1; scan < this.tokens.size(); scan++) {
			Token token = this.tokens.get(scan);
			if (token.trivia() || token.kind() == Kind.NEWLINE || token.directive() != null
					|| token.identifier("in") || token.identifier("out")) {
				continue;
			}

			return token.kind() == Kind.IDENTIFIER ? scan : -1;
		}

		return -1;
	}

	private int layoutBefore(int closing) {
		int depth = 0;
		for (int scan = closing; scan >= 0; scan--) {
			Token token = this.tokens.get(scan);
			if (token.kind() != Kind.OPERATOR || token.directive() != null) {
				continue;
			}

			if (token.operator(")")) {
				depth++;
			} else if (token.operator("(")) {
				depth--;
				if (depth == 0) {
					int keyword = this.tokens.significantBefore(scan);
					return keyword >= 0 && this.tokens.get(keyword).identifier("layout") ? keyword : -1;
				}
			}
		}

		return -1;
	}

	private BlockMember readBlockMember(List<Integer> declaration) {
		int part = 0;
		int layoutAt = -1;
		Set<String> qualifiers = new HashSet<>();
		while (part < declaration.size()) {
			int at = declaration.get(part);
			Token piece = this.tokens.get(at);
			if (piece.identifier("layout")) {
				int closing = this.tokens.matchingBracket(this.tokens.callOpener(at));
				if (closing < 0 || layoutAt >= 0) {
					return null;
				}

				layoutAt = at;
				while (part < declaration.size() && declaration.get(part) <= closing) {
					part++;
				}
			} else if (piece.kind() == Kind.IDENTIFIER && (INTERPOLATION_QUALIFIERS.contains(piece.text())
					|| OTHER_QUALIFIERS.contains(piece.text()))) {
				qualifiers.add(piece.text());
				part++;
			} else {
				break;
			}
		}

		if (part >= declaration.size() || this.tokens.get(declaration.get(part)).kind() != Kind.IDENTIFIER) {
			return null;
		}

		int typeAt = declaration.get(part++);
		List<Integer> names = new ArrayList<>();
		boolean expectName = true;
		for (; part < declaration.size(); part++) {
			int at = declaration.get(part);
			Token piece = this.tokens.get(at);
			if (expectName) {
				if (piece.kind() != Kind.IDENTIFIER) {
					return null;
				}

				names.add(at);
				expectName = false;
			} else if (piece.operator("[")) {
				int closing = this.tokens.matchingBracket(at);
				if (closing < 0) {
					return null;
				}

				while (part + 1 < declaration.size() && declaration.get(part + 1) <= closing) {
					part++;
				}
			} else if (piece.operator(",")) {
				expectName = true;
			} else {
				return null;
			}
		}

		return names.isEmpty() || expectName ? null : new BlockMember(typeAt, names, layoutAt, qualifiers);
	}

	private void flattenMember(BlockMember member, String storage, List<String> carried, String prefix,
			List<Token> corners, List<String> names, List<TokenStream.Insertion> insertions) {
		if (member.layoutAt() >= 0) {
			dropLocationLayout(member.layoutAt());
		}

		for (int nameAt : member.names()) {
			String name = this.tokens.get(nameAt).text();
			names.add(name);
			this.tokens.replace(nameAt, prefix + name);

			for (Token piece : corners) {
				insertions.add(new TokenStream.Insertion(nameAt + 1, piece));
			}
		}

		boolean interpolated = member.qualifiers().stream().anyMatch(INTERPOLATION_QUALIFIERS::contains);
		for (String qualifier : carried) {
			boolean interpolation = INTERPOLATION_QUALIFIERS.contains(qualifier);
			if (interpolation ? !interpolated : !member.qualifiers().contains(qualifier)) {
				insertions.add(new TokenStream.Insertion(member.typeAt(), new Token(Kind.IDENTIFIER, qualifier, null)));
				insertions.add(new TokenStream.Insertion(member.typeAt(), new Token(Kind.SPACE, " ", null)));
			}
		}

		insertions.add(new TokenStream.Insertion(member.typeAt(), new Token(Kind.IDENTIFIER, storage, null)));
		insertions.add(new TokenStream.Insertion(member.typeAt(), new Token(Kind.SPACE, " ", null)));
	}

	private void blankCode(int start, int end) {
		for (int at = start; at <= end; at++) {
			if (this.tokens.get(at).directive() == null) {
				this.tokens.blankRange(at, at);
			}
		}
	}

	private void rewriteCornerReads(String instance, String prefix, List<String> members) {
		for (int index = 0; index < this.tokens.size(); index++) {
			Token token = this.tokens.get(index);
			if (token.macroName() || !token.identifier(instance) || fieldAccess(index)) {
				continue;
			}

			int open = this.tokens.significantAfter(index);
			if (open < 0 || !this.tokens.get(open).operator("[")) {
				continue;
			}

			int closing = this.tokens.matchingBracket(open);
			int dot = closing < 0 ? -1 : this.tokens.significantAfter(closing);
			if (dot < 0 || !this.tokens.get(dot).operator(".")) {
				continue;
			}

			int member = this.tokens.significantAfter(dot);
			if (member < 0 || this.tokens.get(member).kind() != Kind.IDENTIFIER
					|| !members.contains(this.tokens.get(member).text())) {
				continue;
			}

			this.tokens.replace(index, prefix + this.tokens.get(member).text());
			this.tokens.blank(dot);
			this.tokens.blank(member);
		}
	}

	private void rewriteBlockReads(String instance, String prefix, List<String> members) {
		for (int index = 0; index < this.tokens.size(); index++) {
			Token token = this.tokens.get(index);
			if (token.macroName() || !token.identifier(instance) || fieldAccess(index)) {
				continue;
			}

			int dot = this.tokens.significantAfter(index);
			if (dot < 0 || !this.tokens.get(dot).operator(".")) {
				continue;
			}

			int member = this.tokens.significantAfter(dot);
			if (member < 0 || this.tokens.get(member).kind() != Kind.IDENTIFIER
					|| !members.contains(this.tokens.get(member).text())) {
				continue;
			}

			this.tokens.replace(index, prefix + this.tokens.get(member).text());
			this.tokens.blank(dot);
			this.tokens.blank(member);
		}
	}

	private boolean fieldAccess(int index) {
		for (int scan = index - 1; scan >= 0; scan--) {
			Token token = this.tokens.get(scan);
			if (token.kind() == Kind.NEWLINE) {
				return false;
			}

			if (!token.trivia()) {
				return token.operator(".");
			}
		}

		return false;
	}

	private void dropLocationLayout(int layout) {
		dropLayoutOf(layout, Set.of("location", "component"));
	}

	private void hideAbsentExtensionMacros() {
		for (int index = 0; index < this.tokens.size(); index++) {
			Token token = this.tokens.get(index);
			if (token.directive() != null && token.kind() == Kind.IDENTIFIER
					&& VendorExtensions.absent(token.text(), this.stage)) {
				this.tokens.replace(index, VendorExtensions.hidden(token.text()));
			}
		}
	}

	private void dropLayoutOf(int layout, Set<String> keys) {
		int open = this.tokens.callOpener(layout);
		int close = this.tokens.matchingBracket(open);
		if (open < 0 || close < 0) {
			return;
		}

		boolean opensEntry = true;
		for (int part : this.tokens.significantRange(open + 1, close - 1)) {
			Token token = this.tokens.get(part);
			if (opensEntry && !(token.kind() == Kind.IDENTIFIER && keys.contains(token.text()))) {
				return;
			}

			opensEntry = token.operator(",");
		}

		this.tokens.blankRange(layout, close);
	}

	private String macroBody(int name) {
		StringBuilder body = new StringBuilder();
		for (int index = name + 1; index < this.tokens.size(); index++) {
			Token token = this.tokens.get(index);
			if (token.kind() == Kind.NEWLINE) {
				break;
			}

			if (!token.trivia()) {
				body.append(token.text());
			}
		}

		return body.toString();
	}

	private boolean declaresCoreProfile(int directive) {
		int version = 0;
		String profile = null;
		for (int index = directive + 1; index < this.tokens.size(); index++) {
			Token token = this.tokens.get(index);
			if (token.kind() == Kind.NEWLINE) {
				break;
			}

			if (token.kind() == Kind.NUMBER && version == 0
					&& token.text().chars().allMatch(Character::isDigit)) {
				version = Integer.parseInt(token.text());
			} else if (token.kind() == Kind.IDENTIFIER && !token.text().equals("version")) {
				profile = token.text();
			}
		}

		return "core".equals(profile) || (profile == null && version >= 150);
	}

	private String extensionNamed(int directive) {
		for (int index = directive + 1; index < this.tokens.size(); index++) {
			Token token = this.tokens.get(index);
			if (token.kind() == Kind.NEWLINE) {
				return null;
			}

			if (token.kind() == Kind.IDENTIFIER && !token.text().equals("extension")) {
				return token.text().equals("all") ? null : token.text();
			}
		}

		return null;
	}

	private void rewriteIdentifiers() {
		List<Closing> closings = new ArrayList<>();
		int[] lines = this.tokens.lineNumbers();

		for (int index = 0; index < this.tokens.size(); index++) {
			Token token = this.tokens.get(index);
			if (token.kind() != Kind.IDENTIFIER) {
				continue;
			}

			String directive = token.directive();
			if (directive != null
					&& (LegacyGlsl.OPAQUE_DIRECTIVES.contains(directive) || token.macroName())) {
				continue;
			}

			String name = token.text();

			if (this.packMacros.contains(name)) {
				continue;
			}

			if (this.shadowedBuiltins.contains(name)) {
				this.tokens.replace(index, "of_" + name);
				continue;
			}

			if (name.equals("gl_TextureMatrix") && LegacyGlsl.bindsGameTransforms(this.program)
					&& rewriteGameTextureMatrix(index)) {
				continue;
			}

			if (name.equals("gl_TextureMatrix") && this.inputs == VertexInputs.DISTANT
					&& rewriteDistantTextureMatrix(index)) {
				continue;
			}

			if (LegacyGlsl.readsDrawModelView(this.program) && rewriteGameModelView(index, name)) {
				continue;
			}

			if (name.equals("gl_FrontColor") && this.stage == ProgramStage.VERTEX) {
				this.tokens.replace(index, FRONT_COLOUR);
				continue;
			}

			String fixed = LegacyGlsl.FIXED_FUNCTION.get(name);
			if (fixed != null) {
				this.tokens.replace(index, fixed);
				continue;
			}

			if (name.equals("gl_VertexID") || name.equals("gl_InstanceID")) {
				this.tokens.replace(index,
						name.equals("gl_VertexID") ? "gl_VertexIndex" : "gl_InstanceIndex");
				continue;
			}

			if (name.equals("ftransform") && rewriteFtransform(index)) {
				continue;
			}

			String shadow = LegacyGlsl.SHADOW_FUNCTIONS.get(name);
			if (shadow != null && this.tokens.callOpener(index) >= 0) {
				int close = this.tokens.matchingBracket(this.tokens.callOpener(index));
				if (close < 0) {

					this.unwrappedShadowCalls++;
				}

				if (close >= 0) {

					int first = this.tokens.significantAfter(this.tokens.callOpener(index));
					String argument = first >= 0
							&& this.tokens.get(first).kind() == Kind.IDENTIFIER
							? this.tokens.get(first).text() : null;
					boolean hardware = argument != null
							&& hardwareComparisonAt(argument, lines[index]);
					boolean compared = argument != null && !hardware
							&& comparisonAt(argument, lines[index]);

					if (compared && shadow.equals("textureProj")) {
						this.unwrappedShadowCalls++;
						compared = false;
					}

					if (compared) {
						this.softRewrites++;
					}

					closings.add(new Closing(index, "vec4(", directive));
					this.tokens.replace(index, compared ? SHADOW_COMPARE : shadow);
					closings.add(new Closing(close + 1, ")", null));
					this.shadowCalls++;
					continue;
				}
			}

			String modern = LegacyGlsl.DEPRECATED_FUNCTIONS.get(name);
			if (modern != null && this.tokens.callOpener(index) >= 0) {
				this.tokens.replace(index, modern);
				continue;
			}

			if ((name.equals("sin") || name.equals("cos")) && this.tokens.callOpener(index) >= 0
					&& !this.declaredNames.contains(name)) {
				this.trigSites++;
				if (reduceTrig) {
					this.tokens.replace(index, name.equals("sin") ? REDUCED_SIN : REDUCED_COS);
					this.trigCalls++;
					continue;
				}
			}

			String packed = PackBuiltins.helper(name);
			if (packed != null && VendorExtensions.moltenVk() && this.tokens.callOpener(index) >= 0
					&& !this.declaredNames.contains(name)) {
				this.tokens.replace(index, packed);
				this.packBuiltinCalls.add(name);
				continue;
			}

			String named = LegacyGlsl.RESERVED_NAMES.get(name);
			if (named != null && "define".equals(directive)
					&& !this.macroParameterTokens.contains(index)
					&& this.tokens.callOpener(index) < 0) {
				this.tokens.replace(index, named);
				continue;
			}

			if (directive != null) {
				continue;
			}

			if (name.equals("varying")) {

				this.tokens.replace(index, this.stage == ProgramStage.VERTEX ? "out" : "in");
				continue;
			}

			if (name.equals("attribute")) {
				this.tokens.replace(index, "in");
				continue;
			}

			String reserved = LegacyGlsl.RESERVED_NAMES.get(name);
			if (reserved != null && this.tokens.callOpener(index) < 0) {
				this.tokens.replace(index, reserved);
			}
		}

		this.tokens.insertClosings(closings);
	}

	private void rewriteGoldbergHash() {
		for (int index = 0; index < this.tokens.size(); index++) {
			Token token = this.tokens.get(index);
			if (!token.identifier("fract") || token.directive() != null) {
				continue;
			}

			int fractOpen = this.tokens.callOpener(index);
			int sine = fractOpen < 0 ? -1 : this.tokens.significantAfter(fractOpen);
			if (sine < 0 || !goldbergSine(this.tokens.get(sine))) {
				continue;
			}

			int sineOpen = this.tokens.callOpener(sine);
			int dot = sineOpen < 0 ? -1 : this.tokens.significantAfter(sineOpen);
			if (dot < 0 || !this.tokens.get(dot).identifier("dot")) {
				continue;
			}

			int dotOpen = this.tokens.callOpener(dot);
			int dotClose = this.tokens.matchingBracket(dotOpen);
			int comma = firstCallComma(dotOpen);
			int argStart = dotOpen < 0 ? -1 : this.tokens.significantAfter(dotOpen);
			int argEnd = comma < 0 ? -1 : this.tokens.significantBefore(comma);
			int sineClose = this.tokens.matchingBracket(sineOpen);
			int times = sineClose < 0 ? -1 : this.tokens.significantAfter(sineClose);
			int scale = times < 0 ? -1 : this.tokens.significantAfter(times);
			int fractClose = this.tokens.matchingBracket(fractOpen);
			if (argStart < 0 || argEnd < argStart || times < 0 || scale < 0 || fractClose < 0
					|| !this.tokens.get(times).operator("*")
					|| !goldbergScale(this.tokens.get(scale))
					|| !literalVector(comma + 1, dotClose - 1)) {
				continue;
			}

			int afterScale = this.tokens.significantAfter(scale);
			if (afterScale != fractClose) {
				continue;
			}

			boolean reduced = this.tokens.get(sine).identifier(REDUCED_SIN);
			String argument = tokenText(argStart, argEnd);
			this.tokens.blankRange(index, fractClose);
			this.tokens.inject(index, HASH + "(" + argument + ")");

			if (this.trigSites > 0) {
				this.trigSites--;
			}

			if (reduced && this.trigCalls > 0) {
				this.trigCalls--;
			}

			this.hashCalls++;
		}
	}

	private boolean goldbergSine(Token token) {

		return token.identifier(REDUCED_SIN)
				|| (token.identifier("sin") && !this.declaredNames.contains("sin")
						&& !this.packMacros.contains("sin"));
	}

	private boolean literalVector(int start, int end) {
		boolean number = false;
		for (int at = start; at <= end; at++) {
			Token token = this.tokens.get(at);
			if (token.trivia() || token.kind() == Kind.NEWLINE) {
				continue;
			}

			if (token.kind() == Kind.NUMBER) {
				number = true;
			} else if (token.kind() == Kind.IDENTIFIER) {
				if (!token.identifier("vec2") && !token.identifier("vec3") && !token.identifier("vec4")) {
					return false;
				}
			} else if (!token.operator("(") && !token.operator(")") && !token.operator(",")
					&& !token.operator("-") && !token.operator("+")) {
				return false;
			}
		}

		return number;
	}

	private static boolean goldbergScale(Token token) {
		if (token.kind() != Kind.NUMBER) {
			return false;
		}

		String text = token.text();
		if (text.endsWith("f") || text.endsWith("F")) {
			text = text.substring(0, text.length() - 1);
		}

		try {
			return Math.abs(Double.parseDouble(text) - 43758.5453) < 2.0;
		} catch (NumberFormatException ignored) {
			return false;
		}
	}

	private int firstCallComma(int open) {
		int close = this.tokens.matchingBracket(open);
		if (open < 0 || close < 0) {
			return -1;
		}

		int depth = 0;
		for (int scan = open; scan < close; scan++) {
			Token inner = this.tokens.get(scan);
			if (inner.kind() != Kind.OPERATOR) {
				continue;
			}

			if (inner.operator("(") || inner.operator("[") || inner.operator("{")) {
				depth++;
			} else if (inner.operator(")") || inner.operator("]") || inner.operator("}")) {
				depth--;
			} else if (inner.operator(",") && depth == 1) {
				return scan;
			}
		}

		return -1;
	}

	private String tokenText(int start, int end) {
		StringBuilder text = new StringBuilder();
		for (int at = start; at <= end; at++) {
			text.append(this.tokens.get(at).text());
		}

		return text.toString();
	}

	private boolean rewriteGameTextureMatrix(int index) {
		int open = this.tokens.significantAfter(index);
		if (open < 0 || !this.tokens.get(open).operator("[")) {
			return false;
		}

		int unit = this.tokens.significantAfter(open);
		int close = this.tokens.matchingBracket(open);
		if (unit < 0 || close < 0 || this.tokens.significantAfter(unit) != close
				|| !this.tokens.get(unit).text().equals("0")) {
			return false;
		}

		this.tokens.inject(index, LegacyGlsl.GAME_TEXTURE_MATRIX);
		this.tokens.blank(open);
		this.tokens.blank(unit);
		this.tokens.blank(close);
		this.injectedNames.add(LegacyGlsl.GAME_TEXTURE_MATRIX);
		this.gameTextureMatrix++;

		return true;
	}

	private boolean rewriteDistantTextureMatrix(int index) {
		int open = this.tokens.significantAfter(index);
		if (open < 0 || !this.tokens.get(open).operator("[")) {
			return false;
		}

		int unit = this.tokens.significantAfter(open);
		int close = this.tokens.matchingBracket(open);
		if (unit < 0 || close < 0 || this.tokens.significantAfter(unit) != close
				|| !(this.tokens.get(unit).text().equals("0")
						|| this.tokens.get(unit).text().equals("1")
						|| this.tokens.get(unit).text().equals("2"))) {
			return false;
		}

		this.tokens.inject(index, "mat4(1.0)");
		this.tokens.blank(open);
		this.tokens.blank(unit);
		this.tokens.blank(close);

		return true;
	}

	private boolean rewriteGameModelView(int index, String name) {
		if (name.equals("gl_ModelViewMatrix")) {
			this.tokens.inject(index, DRAW_MODEL_VIEW);
		} else if (name.equals("modelViewMatrix")) {

			if (declaring(index)) {
				return false;
			}

			this.tokens.inject(index, DRAW_MODEL_VIEW);
		} else if (name.equals("gl_ModelViewProjectionMatrix")) {
			this.tokens.inject(index, "(of_ProjectionMatrix * " + DRAW_MODEL_VIEW + ")");
			this.injectedNames.add("of_ProjectionMatrix");
		} else {
			return false;
		}

		this.injectedNames.add(LegacyGlsl.CAMERA_BOB);
		this.injectedNames.add(LegacyGlsl.GAME_MODEL_VIEW);
		this.gameModelView++;

		return true;
	}

	private boolean declaring(int index) {
		int before = this.tokens.significantBefore(index);

		return before >= 0 && LegacyGlsl.TYPE_NAMES.contains(this.tokens.get(before).text());
	}

	private boolean rewriteFtransform(int index) {
		int open = this.tokens.callOpener(index);
		int close = this.tokens.matchingBracket(open);
		if (close < 0 || this.tokens.significantAfter(open) != close) {
			return false;
		}

		if (LegacyGlsl.readsDrawModelView(this.program)) {
			this.tokens.inject(index, "(of_ProjectionMatrix * " + DRAW_MODEL_VIEW + " * of_Vertex)");
			this.injectedNames.add("of_ProjectionMatrix");
			this.injectedNames.add(LegacyGlsl.CAMERA_BOB);
			this.injectedNames.add(LegacyGlsl.GAME_MODEL_VIEW);
			this.gameModelView++;
		} else {
			this.tokens.inject(index, "(of_ModelViewProjectionMatrix * of_Vertex)");
			this.injectedNames.add("of_ModelViewProjectionMatrix");
		}

		this.injectedNames.add("of_Vertex");
		this.tokens.blank(open);
		this.tokens.blank(close);

		return true;
	}

	private record SamplerParameter(String function, int position, Scoped scope) {
	}

	private record Scoped(String name, int from, int to) {
	}

	private void convertDepth() {
		List<Closing> closings = new ArrayList<>();
		int[] lines = this.tokens.lineNumbers();

		for (int index = 0; index < this.tokens.size(); index++) {
			Token token = this.tokens.get(index);
			if (token.kind() != Kind.IDENTIFIER) {
				continue;
			}

			if (this.stage == ProgramStage.FRAGMENT && token.identifier("gl_FragDepth")) {
				this.namesFragDepth = true;
			}

			String directive = token.directive();
			if (directive != null
					&& (LegacyGlsl.OPAQUE_DIRECTIVES.contains(directive) || token.macroName())) {
				continue;
			}

			if (this.packMacros.contains(token.text())) {
				continue;
			}

			if (LegacyGlsl.DEPTH_LOOKUPS.contains(token.text())) {

				if (!rewriteShadowCompare(index, lines[index])) {
					countDepthLookup(index, lines[index]);
				}

				continue;
			}

			if (this.stage != ProgramStage.FRAGMENT) {
				continue;
			}

			if (token.identifier("gl_FragCoord")) {
				convertFragCoord(index);
			} else if (token.identifier("gl_FragDepth")) {
				convertFragDepth(index, closings);
			}
		}

		this.tokens.insertClosings(closings);
	}

	private boolean rewriteShadowCompare(int index, int line) {
		if (this.comparisonSamplers.isEmpty() && this.hardwareComparisonSamplers.isEmpty()) {
			return false;
		}

		int open = this.tokens.callOpener(index);
		int first = open < 0 ? -1 : this.tokens.significantAfter(open);
		if (first < 0 || this.tokens.get(first).kind() != Kind.IDENTIFIER) {
			return false;
		}

		String argument = this.tokens.get(first).text();
		if (hardwareComparisonAt(argument, line)) {
			return true;
		}

		if (!comparisonAt(argument, line)) {
			return false;
		}

		String name = this.tokens.get(index).text();
		if (!COMPARABLE_LOOKUPS.contains(name)) {
			this.unwrappedShadowCalls++;

			return true;
		}

		this.tokens.replace(index, SHADOW_COMPARE);
		this.softRewrites++;

		return true;
	}

	private void countDepthLookup(int index, int line) {
		int open = this.tokens.callOpener(index);
		int first = open < 0 ? -1 : this.tokens.significantAfter(open);
		if (first < 0 || this.tokens.get(first).kind() != Kind.IDENTIFIER) {
			return;
		}

		String name = this.tokens.get(first).text();
		if (SamplerPlan.classify(name) == SamplerPlan.Kind.DEPTH) {
			this.depthLookups++;
		} else if (scoped(this.samplerParameters, name, line)) {
			this.parameterLookups++;
		}
	}

	private void pinLookupLevels() {
		if (this.stage != ProgramStage.FRAGMENT || this.program.isEmpty()) {
			return;
		}

		boolean fullScreen = fullScreenPass();
		Set<String> chained = chainedSamplers();
		Set<String> pinned = new HashSet<>();
		for (Map.Entry<String, String> sampler : this.samplers.entrySet()) {
			String name = sampler.getKey();
			if (chained.contains(name) || !levelled(sampler.getValue())) {
				continue;
			}

			if (fullScreen || servedOutOfATarget(name)) {
				pinned.add(name);
			}
		}

		Set<Scoped> proven = provenParameters(pinned, fullScreen && chained.isEmpty());
		int[] lines = this.tokens.lineNumbers();
		List<Closing> levels = new ArrayList<>();

		for (int index = 0; index < this.tokens.size(); index++) {
			Token token = this.tokens.get(index);
			if (token.kind() != Kind.IDENTIFIER || token.directive() != null || token.macroName()
					|| !LegacyGlsl.LEVELLED_LOOKUPS.contains(token.text())
					|| this.packMacros.contains(token.text())) {
				continue;
			}

			int open = this.tokens.callOpener(index);
			int close = this.tokens.matchingBracket(open);
			int first = this.tokens.significantAfter(open);
			if (close < 0 || first < 0 || this.tokens.get(first).kind() != Kind.IDENTIFIER) {
				continue;
			}

			String name = this.tokens.get(first).text();
			int line = lines[index];

			if (this.packMacros.contains(name) || comparisonAt(name, line)) {
				continue;
			}

			if (scoped(this.samplerParameters, name, line)) {
				if (!scoped(proven, name, line)) {
					this.unpinnedParameterLookups++;
					continue;
				}
			} else if (!pinned.contains(name)) {
				continue;
			} else if (writesItsOwnLevel(token.text()) && keepsWrittenLevel(name)) {

				continue;
			}

			if (pinLookup(index, open, close, levels)) {
				this.pinnedLookups++;
			}
		}

		this.tokens.insertClosings(levels);
	}

	private Set<Scoped> provenParameters(Set<String> pinned, boolean all) {
		Set<Scoped> proven = new LinkedHashSet<>();
		if (all) {
			for (Scoped parameter : this.samplerParameters) {
				if (!this.unlevelledParameters.contains(parameter)) {
					proven.add(parameter);
				}
			}

			return proven;
		}

		List<SamplerParameter> waiting = new ArrayList<>();
		Set<String> functions = new HashSet<>();
		for (SamplerParameter parameter : this.typedParameters) {
			if (!this.unlevelledParameters.contains(parameter.scope())) {
				waiting.add(parameter);
				functions.add(parameter.function());
			}
		}

		Map<String, List<Integer>> sites = sitesOf(functions);
		int[] lines = this.tokens.lineNumbers();
		boolean grew = true;
		while (grew) {
			grew = false;
			for (Iterator<SamplerParameter> pending = waiting.iterator(); pending.hasNext(); ) {
				SamplerParameter parameter = pending.next();
				if (proven.contains(parameter.scope())) {
					pending.remove();
					continue;
				}

				if (!callsHandOver(parameter, pinned, proven, lines,
						sites.getOrDefault(parameter.function(), List.of()))) {
					continue;
				}

				proven.add(parameter.scope());
				pending.remove();
				grew = true;
			}
		}

		return proven;
	}

	private Map<String, List<Integer>> sitesOf(Set<String> names) {
		Map<String, List<Integer>> found = new HashMap<>();
		for (int index = 0; index < this.tokens.size(); index++) {
			Token token = this.tokens.get(index);
			if (token.kind() == Kind.IDENTIFIER && names.contains(token.text())) {
				found.computeIfAbsent(token.text(), _ -> new ArrayList<>()).add(index);
			}
		}

		return found;
	}

	private boolean callsHandOver(SamplerParameter parameter, Set<String> pinned,
			Set<Scoped> proven, int[] lines, List<Integer> sites) {
		boolean called = false;
		for (int index : sites) {
			Token token = this.tokens.get(index);
			if (token.directive() != null) {
				return false;
			}

			int open = this.tokens.callOpener(index);
			int before = this.tokens.significantBefore(index);
			if (open < 0 || (before >= 0 && this.tokens.get(before).kind() == Kind.IDENTIFIER
					&& returnsAType(this.tokens.get(before).text()))) {
				continue;
			}

			int close = this.tokens.matchingBracket(open);
			if (close < 0) {
				return false;
			}

			List<Integer> commas = topLevelCommas(open, close);
			if (commas.size() < parameter.position()) {
				return false;
			}

			int start = parameter.position() == 0 ? open : commas.get(parameter.position() - 1);
			int end = commas.size() > parameter.position() ? commas.get(parameter.position()) : close;
			int argument = this.tokens.significantAfter(start);
			if (argument < 0 || this.tokens.significantAfter(argument) != end
					|| this.tokens.get(argument).kind() != Kind.IDENTIFIER) {
				return false;
			}

			String name = this.tokens.get(argument).text();
			int line = lines[argument];
			boolean handed = scoped(this.samplerParameters, name, line)
					? scoped(proven, name, line)
					: pinned.contains(name);
			if (!handed) {
				return false;
			}

			called = true;
		}

		return called;
	}

	private static boolean returnsAType(String word) {
		return LegacyGlsl.TYPE_NAMES.contains(word);
	}

	private Set<String> chainedSamplers() {
		Set<Integer> targets = new HashSet<>();
		for (ConstDirectives.Directive directive : ConstDirectives.read(this.unit)) {
			boolean on = directive.value().equals("true");
			if (!directive.type().equals("bool") || !(on || directive.value().equals("false"))) {
				continue;
			}

			Optional<TargetName.Suffixed> split = TargetName.split(directive.name());
			if (split.isPresent() && split.get().suffix().equals(MIPMAP_SUFFIX)) {
				if (on) {
					targets.add(split.get().index());
				} else {
					targets.remove(split.get().index());
				}
			}
		}

		Set<String> chained = new HashSet<>();
		for (String name : this.samplers.keySet()) {
			OptionalInt index = TargetName.index(name);
			if (index.isPresent() && targets.contains(index.getAsInt())) {
				chained.add(name);
			}
		}

		return chained;
	}

	private static boolean writesItsOwnLevel(String lookup) {
		return lookup.equals("textureLod") || lookup.equals("textureLodOffset");
	}

	private boolean keepsWrittenLevel(String name) {
		if (SamplerPlan.classify(name) != SamplerPlan.Kind.SHADOW_DEPTH) {
			return false;
		}

		if (name.equals("shadow")) {
			return shadowChainZero && shadowChainOne;
		}

		return SamplerPlan.withoutTranslucents(name, false) ? shadowChainOne : shadowChainZero;
	}

	private static boolean servedOutOfATarget(String name) {
		return switch (SamplerPlan.classify(name)) {
			case COLORTEX, DEPTH, NOISE, DISTANT_DEPTH, SHADOW_DEPTH, SHADOW_COLOUR -> true;
			default -> false;
		};
	}

	private static boolean levelled(String declaration) {
		for (String word : declaration.split(" ", -1)) {
			String shape = SamplerTypes.shapeOf(word);
			if (shape != null) {
				return !shape.contains("Rect") && !shape.contains("Buffer") && !shape.contains("MS")
						&& (!shape.endsWith(SHADOW_SHAPE)
								|| LEVELLED_SHADOW_SHAPES.contains(shape));
			}
		}

		return false;
	}

	private boolean pinLookup(int index, int open, int close, List<Closing> levels) {
		List<Integer> commas = topLevelCommas(open, close);
		String directive = this.tokens.get(index).directive();

		switch (this.tokens.get(index).text()) {
			case "texture", "textureGrad" -> {
				if (commas.isEmpty()) {
					return false;
				}

				this.tokens.replace(index, "textureLod");
				dropArgumentsFrom(commas, 2, close);
				levels.add(new Closing(close, ", " + BASE_LEVEL, directive));
			}
			case "textureOffset" -> {
				if (commas.size() < 2) {
					return false;
				}

				this.tokens.replace(index, "textureLodOffset");
				dropArgumentsFrom(commas, 3, close);
				levels.add(new Closing(commas.get(1), ", " + BASE_LEVEL, directive));
			}
			case "textureGradOffset" -> {
				if (commas.size() != 4) {
					return false;
				}

				this.tokens.replace(index, "textureLodOffset");
				setArgument(commas, 2, close, BASE_LEVEL);
				dropArgument(commas, 3, close);
			}
			default -> {
				if (commas.size() < 2) {
					return false;
				}

				setArgument(commas, 2, close, BASE_LEVEL);
			}
		}

		return true;
	}

	private List<Integer> topLevelCommas(int open, int close) {
		List<Integer> commas = new ArrayList<>();
		int depth = 0;

		for (int scan = open + 1; scan < close; scan++) {
			Token token = this.tokens.get(scan);
			if (token.kind() != Kind.OPERATOR) {
				continue;
			}

			if (token.operator("(") || token.operator("[") || token.operator("{")) {
				depth++;
			} else if (token.operator(")") || token.operator("]") || token.operator("}")) {
				depth--;
			} else if (depth == 0 && token.operator(",")) {
				commas.add(scan);
			}
		}

		return commas;
	}

	private void dropArgumentsFrom(List<Integer> commas, int at, int close) {
		if (commas.size() >= at) {
			this.tokens.blankRange(commas.get(at - 1), close - 1);
		}
	}

	private void dropArgument(List<Integer> commas, int at, int close) {
		int end = commas.size() > at ? commas.get(at) - 1 : close - 1;
		this.tokens.blankRange(commas.get(at - 1), end);
	}

	private void setArgument(List<Integer> commas, int at, int close, String text) {
		int first = this.tokens.significantAfter(commas.get(at - 1));
		int end = commas.size() > at ? commas.get(at) - 1 : close - 1;
		this.tokens.blankRange(commas.get(at - 1) + 1, end);
		this.tokens.inject(first, text);
	}

	private void convertFragCoord(int index) {
		int dot = this.tokens.significantAfter(index);
		int swizzle = dot >= 0 && this.tokens.get(dot).operator(".")
				? this.tokens.significantAfter(dot)
				: -1;
		if (swizzle < 0 || this.tokens.get(swizzle).kind() != Kind.IDENTIFIER) {

			this.fragCoordUnhandled++;
			return;
		}

		String field = this.tokens.get(swizzle).text();
		if (field.equals("z")) {
			this.tokens.inject(index, "(" + DEPTH_CONV + ".z * gl_FragCoord.z + " + DEPTH_CONV + ".w)");
			this.fragCoordZ++;
		} else if (field.equals("xyz")) {

			this.tokens.inject(index, "vec3(gl_FragCoord.xy, " + DEPTH_CONV + ".z * gl_FragCoord.z + "
					+ DEPTH_CONV + ".w)");
			this.fragCoordXyz++;
		} else {
			if (namesDepth(field)) {
				this.fragCoordUnhandled++;
			}

			return;
		}

		this.tokens.blank(dot);
		this.tokens.blank(swizzle);
		takeDepthConv();
	}

	private static boolean namesDepth(String field) {
		return field.indexOf('z') >= 0 || field.indexOf('b') >= 0 || field.indexOf('p') >= 0;
	}

	private void convertFragDepth(int index, List<Closing> closings) {
		if (this.tokens.get(index).directive() != null) {

			this.fragDepthUnhandled++;
			return;
		}

		int eq = this.tokens.significantAfter(index);
		int after = this.tokens.significantAfter(eq);
		boolean assignment = eq >= 0 && this.tokens.get(eq).operator("=")
				&& (after < 0 || !this.tokens.get(after).operator("="));
		int end = assignment ? this.tokens.statementEnd(eq) : -1;
		if (end < 0) {

			this.fragDepthUnhandled++;
			return;
		}

		String directive = this.tokens.get(eq).directive();
		this.tokens.inject(eq, "= (" + DEPTH_CONV + ".z * (");
		closings.add(new Closing(end, ") + " + DEPTH_CONV + ".w)", directive));
		takeDepthConv();
		this.fragDepthWrites++;
	}

	private void takeDepthConv() {
		record(this.blockMembers, DEPTH_CONV, "vec4 " + DEPTH_CONV);
		this.injectedNames.add(DEPTH_CONV);
	}

	private void takeTexShrink() {
		record(this.blockMembers, TEX_SHRINK, "vec2 " + TEX_SHRINK);
		this.injectedNames.add(TEX_SHRINK);
	}

	private void takePassColour() {
		record(this.blockMembers, PASS_COLOUR, "vec4 " + PASS_COLOUR);
		this.injectedNames.add(PASS_COLOUR);
	}

	private void demoteNonConstantInitialisers() {
		int parens = 0;
		for (int index = 0; index < this.tokens.size(); index++) {
			Token token = this.tokens.get(index);
			if (token.directive() != null) {
				continue;
			}

			if (token.operator("(")) {
				parens++;
				continue;
			}

			if (token.operator(")")) {
				parens--;
				continue;
			}

			if (parens != 0 || !token.identifier("const")) {
				continue;
			}

			int end = this.tokens.statementEnd(index);
			if (end >= 0 && nonConstantInitialiser(index, end)) {
				this.tokens.blank(index);
			}
		}
	}

	private boolean nonConstantInitialiser(int keyword, int end) {
		boolean seenEquals = false;
		for (int scan = keyword; scan <= end; scan++) {
			Token token = this.tokens.get(scan);
			if (token.operator("=")) {
				seenEquals = true;
				continue;
			}

			if (!seenEquals || token.kind() != Kind.IDENTIFIER) {
				continue;
			}

			if (!constantName(token.text(), new HashSet<>())) {
				return true;
			}
		}

		return false;
	}

	private boolean constantName(String name, Set<String> path) {
		if (LegacyGlsl.TYPE_NAMES.contains(name)) {
			return true;
		}

		if (!this.packMacros.contains(name)) {
			return false;
		}

		Boolean judged = this.macroConstant.get(name);
		if (judged != null) {
			return judged;
		}

		if (path.contains(name)) {
			return true;
		}

		if (path.size() >= MAX_MACRO_HOPS) {
			return false;
		}

		path.add(name);
		try {
			boolean constant = true;
			for (String inside : this.macroBodies.getOrDefault(name, Set.of())) {
				if (!constantName(inside, path)) {
					constant = false;
					break;
				}
			}

			this.macroConstant.put(name, constant);

			return constant;
		} finally {
			path.remove(name);
		}
	}

	private void dropPrecision() {
		for (int index = 0; index < this.tokens.size(); index++) {
			Token token = this.tokens.get(index);
			if (token.kind() != Kind.IDENTIFIER || token.directive() != null) {
				continue;
			}

			if (LegacyGlsl.PRECISION_QUALIFIERS.contains(token.text())) {
				this.tokens.blank(index);
				continue;
			}

			if (!token.identifier("precision")) {
				continue;
			}

			int qualifier = this.tokens.significantAfter(index);
			if (qualifier >= 0
					&& LegacyGlsl.PRECISION_QUALIFIERS.contains(this.tokens.get(qualifier).text())) {
				int end = this.tokens.statementEnd(index);
				if (end >= 0) {
					this.tokens.blankRange(index, end);
				}
			}
		}
	}

	private void rewriteFragmentOutputs() {
		if (this.stage != ProgramStage.FRAGMENT) {
			return;
		}

		boolean fragColor = false;

		for (int index = 0; index < this.tokens.size(); index++) {
			Token token = this.tokens.get(index);
			if (token.kind() != Kind.IDENTIFIER) {
				continue;
			}

			if (token.identifier("gl_FragColor")) {
				this.tokens.inject(index, "ofFragData0");
				fragColor = true;
				continue;
			}

			if (!token.identifier("gl_FragData")) {
				continue;
			}

			int slot = literalIndexAfter(index);
			if (slot < 0) {

				this.dynamicFragData++;
				continue;
			}

			int open = this.tokens.significantAfter(index);
			int number = this.tokens.significantAfter(open);
			this.tokens.inject(index, "ofFragData" + slot);
			this.tokens.blank(open);
			this.tokens.blank(number);
			this.tokens.blank(this.tokens.significantAfter(number));
			this.maxFragmentOutput = Math.max(this.maxFragmentOutput, slot);
			this.legacySlotZero |= slot == 0;
		}

		if (fragColor) {
			this.maxFragmentOutput = Math.max(this.maxFragmentOutput, 0);
			this.legacySlotZero = true;
		}
	}

	private int literalIndexAfter(int index) {
		int open = this.tokens.significantAfter(index);
		if (open < 0 || !this.tokens.get(open).operator("[")) {
			return -1;
		}

		int number = this.tokens.significantAfter(open);
		if (number < 0 || this.tokens.get(number).kind() != Kind.NUMBER) {
			return -1;
		}

		int close = this.tokens.significantAfter(number);
		if (close < 0 || !this.tokens.get(close).operator("]")) {
			return -1;
		}

		String text = this.tokens.get(number).text();
		if (text.length() > 2 || !text.chars().allMatch(Character::isDigit)) {
			return -1;
		}

		int slot = Integer.parseInt(text);

		return slot < MAX_FRAGMENT_OUTPUTS ? slot : -1;
	}

	record Output(String name, String type) {
	}

	private void liftFragmentOutputs() {
		if (this.stage != ProgramStage.FRAGMENT) {
			return;
		}

		int[] lines = this.tokens.lineNumbers();
		for (int index = 0; index < this.tokens.size(); index++) {
			Token token = this.tokens.get(index);
			if (!token.identifier("out") || token.directive() != null) {
				continue;
			}

			if (this.unit.isLive(lines[index]) && opensDeclaration(index)) {
				liftOutput(index);
			}
		}

		for (int location : this.packOutputs.keySet()) {
			this.maxFragmentOutput = Math.max(this.maxFragmentOutput, location);
		}
	}

	private boolean opensDeclaration(int index) {
		int before = this.tokens.significantBefore(index);
		if (before < 0) {
			return true;
		}

		Token token = this.tokens.get(before);

		return token.operator(";") || token.operator("}") || token.operator(")");
	}

	private void liftOutput(int keyword) {
		int start = this.tokens.statementStart(keyword);
		int end = this.tokens.statementEnd(keyword);
		if (start < 0 || end < 0) {
			return;
		}

		List<Integer> parts = this.tokens.significantRange(start, end);
		int cursor = parts.indexOf(keyword);

		if (cursor < 0 || parts.size() != cursor + 4) {
			return;
		}

		String type = this.tokens.get(parts.get(cursor + 1)).text();
		Token name = this.tokens.get(parts.get(cursor + 2));
		if (!LegacyGlsl.TYPE_NAMES.contains(type) || name.kind() != Kind.IDENTIFIER
				|| !this.tokens.get(parts.get(cursor + 3)).operator(";")) {
			return;
		}

		int location = literalLocation(parts, cursor);
		if (location < 0 || location >= MAX_FRAGMENT_OUTPUTS) {
			return;
		}

		Output output = new Output(name.text(), LegacyGlsl.widened(type));
		if (this.packOutputs.putIfAbsent(location, output) == null) {
			this.tokens.blankRange(start, end);
		}
	}

	private int literalLocation(List<Integer> parts, int keyword) {
		for (int at = 0; at + 2 < keyword; at++) {
			if (!this.tokens.get(parts.get(at)).identifier("location")) {
				continue;
			}

			Token number = this.tokens.get(parts.get(at + 2));
			String text = number.text();
			if (this.tokens.get(parts.get(at + 1)).operator("=") && number.kind() == Kind.NUMBER
					&& text.length() <= 2 && text.chars().allMatch(Character::isDigit)) {
				return Integer.parseInt(text);
			}
		}

		return -1;
	}

	private void orderFragmentOutputs() {
		if (this.stage != ProgramStage.FRAGMENT || this.maxFragmentOutput < 0) {
			return;
		}

		if (wrapsFragment()) {
			this.ordered = true;
			return;
		}

		int brace = mainBrace();
		if (brace >= 0) {
			this.tokens.inject(brace, "{ " + ORDER_OUTPUTS + "();");
			this.ordered = true;
		}
	}

	private void planAlphaEpilogue() {

		if (this.stage != ProgramStage.FRAGMENT || !this.alphaTest.tests() || !this.legacySlotZero) {
			return;
		}

		Output first = this.packOutputs.get(0);
		if (first != null && !first.type().equals("vec4")) {
			return;
		}

		this.packMainName = mainName();
		this.alphaEpilogue = this.packMainName >= 0;
	}

	private void planCoverage() {
		if (this.stage != ProgramStage.FRAGMENT || !this.coverage || this.maxFragmentOutput < 0
				|| this.maxFragmentOutput + 1 >= MAX_FRAGMENT_OUTPUTS) {
			return;
		}

		if (this.packMainName < 0) {
			this.packMainName = mainName();
		}

		this.covers = this.packMainName >= 0;
	}

	private boolean wrapsFragment() {
		return this.alphaEpilogue || this.covers;
	}

	private void wrapMain() {
		if (this.stage == ProgramStage.FRAGMENT) {
			if (wrapsFragment()) {
				this.tokens.replace(this.packMainName, PACK_MAIN);
				this.mainWrapped = true;
			}

			return;
		}

		if (this.stage != ProgramStage.VERTEX) {
			return;
		}

		if (this.inputs == VertexInputs.SKY) {
			takePassColour();
		}

		boolean terrain = this.inputs.terrain();
		boolean distant = this.inputs == VertexInputs.DISTANT;
		boolean depth = namesClipPosition();
		boolean overlay = this.inputs.overlay();
		boolean lines = this.inputs == VertexInputs.LINES;
		if (!terrain && !distant && !depth && !overlay && !lines) {
			return;
		}

		int name = mainName();
		if (name < 0) {
			return;
		}

		this.tokens.replace(name, PACK_MAIN);
		this.terrainPrologue = terrain;
		this.distantPrologue = distant;
		this.entityWrapped = overlay;
		this.linesWrapped = lines;
		if (lines) {

			this.injectedNames.add("viewWidth");
			this.injectedNames.add("viewHeight");
		}

		if (terrain) {
			takeTexShrink();
		}

		if (depth) {
			takeDepthConv();
			this.depthEpilogue = true;
		}

		this.mainWrapped = true;
	}

	private void wrapMainForSplits() {
		if (this.mainWrapped || !this.splits.any()) {
			return;
		}

		int name = mainName();
		if (name < 0) {
			name = mainNameAfterOutputOrder();
		}

		if (name < 0) {
			return;
		}

		this.tokens.replace(name, PACK_MAIN);
		this.mainWrapped = true;
	}

	private int mainNameAfterOutputOrder() {
		int[] lines = this.tokens.lineNumbers();
		for (int index = 0; index < this.tokens.size(); index++) {
			Token token = this.tokens.get(index);
			if (!token.identifier("main") || token.directive() != null
					|| !this.unit.isLive(lines[index])) {
				continue;
			}

			int close = this.tokens.matchingBracket(this.tokens.callOpener(index));
			int brace = this.tokens.significantAfter(close);
			if (brace >= 0) {
				Token after = this.tokens.get(brace);
				if (after.kind() == Kind.RAW && after.text().startsWith("{")) {
					return index;
				}
			}
		}

		return -1;
	}

	private void wrapMainForOwedOutputs() {
		if (this.mainWrapped) {
			return;
		}

		int name = mainName();
		if (name < 0) {

			return;
		}

		this.tokens.replace(name, PACK_MAIN);
		this.mainWrapped = true;
	}

	private void synthesizeAttributes() {
		if (this.stage != ProgramStage.VERTEX || !this.inputs.synthesizes()) {
			return;
		}

		int[] lines = this.tokens.lineNumbers();
		for (int index = 0; index < this.tokens.size(); index++) {
			Token token = this.tokens.get(index);
			if (token.directive() != null || !this.unit.isLive(lines[index])) {
				continue;
			}

			if (token.identifier("attribute")) {
				synthesizeOne(index, false);
			} else if (token.identifier("in")) {
				synthesizeOne(index, true);
			}
		}
	}

	private void synthesizeOne(int keyword, boolean known) {
		FileScope declared = fileScopeDeclaration(keyword);
		if (declared == null
				|| (known && declared.names().stream().noneMatch(VertexPrologue.SYNTHESIZED::contains))) {
			return;
		}

		declared.names().forEach(name -> {
			if (!headerDeclares(name)) {
				this.synthesized.putIfAbsent(name, declared.type());
			}
		});
		this.tokens.blankRange(declared.start(), declared.end());
	}

	private boolean headerDeclares(String name) {
		return this.inputs.overlay() && (ENTITY_COLOR.equals(name) || ENTITY_IDS.contains(name));
	}

	private void collectVaryings() {
		if (this.stage == ProgramStage.COMPUTE) {
			return;
		}

		int[] lines = this.tokens.lineNumbers();
		for (int index = 0; index < this.tokens.size(); index++) {
			Token token = this.tokens.get(index);
			if (token.directive() != null || !this.unit.isLive(lines[index])) {
				continue;
			}

			String storage = storageWord(token, this.macroAliases);
			if ("out".equals(storage)) {
				FileScope declared = fileScopeDeclaration(index);
				if (declared != null) {
					this.declaredOutputs.addAll(declared.names());
					this.declaredOutputScopes.add(declared);
				}
			} else if ("in".equals(storage) && this.stage != ProgramStage.VERTEX) {
				FileScope declared = fileScopeDeclaration(index);
				if (declared != null) {
					this.declaredInputs.add(declared);
				}
			}
		}
	}

	private void dropUnprovidedInputs(Set<String> provided) {

		dropUnprovidedSplits(provided);

		List<FileScope> unprovided = this.declaredInputs.stream()
				.filter(declared -> declared.names().stream().noneMatch(provided::contains))
				.toList();
		if (unprovided.isEmpty()) {
			return;
		}

		Set<String> candidates = new HashSet<>();
		unprovided.forEach(declared -> candidates.addAll(declared.names()));
		Set<String> read = readNames(candidates);

		boolean dropped = false;
		for (FileScope declared : unprovided) {
			if (declared.names().stream().noneMatch(read::contains)) {
				this.tokens.blankRange(declared.start(), declared.end());
				this.droppedInputs.addAll(declared.names());
				dropped = true;
				continue;
			}

			for (String name : declared.names()) {

				if (declared.declarators().getOrDefault(name, "").equals(declared.type() + " " + name)) {
					this.unprovidedInputs.put(name, declared.qualifier().isEmpty()
							? declared.type()
							: declared.qualifier() + " " + declared.type());
				}
			}
		}

		if (dropped) {
			this.used = usedNames();
			this.declaredAfter = declaredUnderAType();
		}
	}

	private void dropUnprovidedSplits(Set<String> provided) {
		for (String name : this.splits.splitInputs()) {
			if (this.splits.interfaceNamesOf(name).stream().anyMatch(provided::contains)
					|| !readNames(Set.of(name)).isEmpty()) {
				continue;
			}

			if (this.splits.forgetInput(name)) {
				this.droppedInputs.add(name);
			}
		}
	}

	private void withholdUndeclaredOutputs(Set<String> declared) {
		for (FileScope scope : this.declaredOutputScopes) {
			if (scope.names().stream().anyMatch(declared::contains)) {
				continue;
			}

			for (int index : this.tokens.significantRange(scope.start(), scope.end())) {
				String text = this.tokens.get(index).text();
				if (!text.equals("out") && !LegacyGlsl.INTERPOLATION_QUALIFIERS.contains(text)) {
					break;
				}

				this.tokens.blank(index);
			}

			this.declaredOutputs.removeAll(scope.names());
		}
	}

	private Set<String> readNames(Set<String> candidates) {
		boolean[] declaration = new boolean[this.tokens.size()];
		for (FileScope declared : this.declaredInputs) {
			for (int index = declared.start(); index <= declared.end(); index++) {
				declaration[index] = true;
			}
		}

		int[] region = this.regions;
		int[] lines = this.tokens.lineNumbers();
		Map<String, Set<Integer>> shadowed = new HashMap<>();
		Set<String> read = new HashSet<>();

		for (int index = 0; index < this.tokens.size(); index++) {
			Token token = this.tokens.get(index);

			if (token.kind() != Kind.IDENTIFIER || declaration[index]
					|| !this.unit.isLive(lines[index]) || !candidates.contains(token.text())) {
				continue;
			}

			int before = this.tokens.significantBefore(index);
			boolean declares = before >= 0
					&& LegacyGlsl.TYPE_NAMES.contains(this.tokens.get(before).text());
			if (declares && region[index] >= 0) {

				shadowed.computeIfAbsent(token.text(), ignored -> new HashSet<>()).add(region[index]);
			} else if (!declares && !shadowed.getOrDefault(token.text(), Set.of()).contains(region[index])) {
				read.add(token.text());
			}
		}

		return read;
	}

	private int[] regions() {
		int[] region = new int[this.tokens.size()];
		Arrays.fill(region, -1);

		int depth = 0;
		int current = -1;
		for (int index = 0; index < this.tokens.size(); index++) {
			Token token = this.tokens.get(index);
			if (token.directive() == null && token.operator("{")) {
				if (depth == 0) {
					current++;

					int from = this.tokens.statementStart(index);
					for (int back = from < 0 ? index : from; back <= index; back++) {
						region[back] = current;
					}
				}

				depth++;
			} else if (token.directive() == null && token.operator("}")) {
				depth--;
			}

			if (depth > 0) {
				region[index] = current;
			}
		}

		return region;
	}

	record FileScope(List<String> names, String type, String qualifier,
			Map<String, String> declarators, int start, int end) {

		FileScope {
			names = List.copyOf(names);
			declarators = Map.copyOf(declarators);
		}
	}

	private FileScope fileScopeDeclaration(int keyword) {
		return fileScopeDeclaration(keyword, LegacyGlsl.TYPE_NAMES);
	}

	private FileScope fileScopeDeclaration(int keyword, Set<String> types) {
		int end = this.tokens.statementEnd(keyword);
		int start = end < 0 ? -1 : this.tokens.statementStart(keyword);
		if (start < 0) {
			return null;
		}

		List<Integer> parts = this.tokens.significantRange(start, end);
		int cursor = parts.indexOf(keyword);
		if (cursor < 0) {
			return null;
		}

		List<String> qualifiers = new ArrayList<>();
		for (int before = 0; before < cursor; before++) {
			String text = this.tokens.get(parts.get(before)).text();
			if (!LegacyGlsl.INTERPOLATION_QUALIFIERS.contains(text)) {
				return null;
			}

			qualifiers.add(text);
		}

		cursor++;
		while (cursor < parts.size() && (isQualifier(this.tokens.get(parts.get(cursor)))
				|| LegacyGlsl.INTERPOLATION_QUALIFIERS
						.contains(this.tokens.get(parts.get(cursor)).text()))) {
			String text = this.tokens.get(parts.get(cursor)).text();
			if (LegacyGlsl.INTERPOLATION_QUALIFIERS.contains(text)) {
				qualifiers.add(text);
			}

			cursor++;
		}

		if (cursor >= parts.size() || this.tokens.get(parts.get(cursor)).kind() != Kind.IDENTIFIER) {
			return null;
		}

		String type = this.tokens.get(parts.get(cursor)).text();
		if (!types.contains(type)) {
			return null;
		}

		Map<String, String> found = new LinkedHashMap<>();
		if (!readDeclarators(parts, cursor + 1, type, "", "", found)) {
			return null;
		}

		return new FileScope(List.copyOf(found.keySet()), type, String.join(" ", qualifiers), found,
				start, end);
	}

	private boolean namesClipPosition() {
		for (Token token : this.tokens) {
			if (token.identifier("gl_Position")) {
				return true;
			}
		}

		return false;
	}

	private int mainBrace() {
		int name = mainName();

		return name < 0
				? -1
				: this.tokens.significantAfter(
						this.tokens.matchingBracket(this.tokens.callOpener(name)));
	}

	private int mainName() {
		int[] lines = this.tokens.lineNumbers();
		for (int index = 0; index < this.tokens.size(); index++) {
			Token token = this.tokens.get(index);
			if (!token.identifier("main") || token.directive() != null
					|| !this.unit.isLive(lines[index])) {
				continue;
			}

			int close = this.tokens.matchingBracket(this.tokens.callOpener(index));
			int brace = this.tokens.significantAfter(close);
			if (brace >= 0 && this.tokens.get(brace).operator("{")) {
				return index;
			}
		}

		return -1;
	}

	private void liftUniforms() {
		int[] lines = this.tokens.lineNumbers();
		Map<String, String> aliases = new HashMap<>();

		for (int index = 0; index < this.tokens.size(); index++) {
			Token token = this.tokens.get(index);
			if (token.kind() == Kind.HASH && this.unit.isLive(lines[index])) {
				int name = this.tokens.macroNameAfter(index);
				if (name >= 0 && "define".equals(token.directive())) {
					String alias = aliasOf(name).orElse("");
					if (alias.isEmpty()) {
						aliases.remove(this.tokens.get(name).text());
					} else {
						aliases.put(this.tokens.get(name).text(), alias);
					}
				} else if (name >= 0 && "undef".equals(token.directive())) {
					aliases.remove(this.tokens.get(name).text());
				}
			}
			if (!token.identifier("uniform") || token.directive() != null) {
				continue;
			}

			if (this.unit.isLive(lines[index])) {
				liftOne(index, aliases);
			}
		}

		if (this.inputs.overlay()) {
			this.blockMembers.remove(ENTITY_COLOR);
			ENTITY_IDS.forEach(this.blockMembers::remove);
		}
	}

	private void liftOne(int keyword, Map<String, String> aliases) {
		int end = this.tokens.statementEnd(keyword);
		if (end < 0) {

			return;
		}

		int start = this.tokens.statementStart(keyword);
		if (start < 0) {
			return;
		}

		List<Integer> parts = this.tokens.significantRange(start, end);
		int keywordAt = parts.indexOf(keyword);
		if (keywordAt < 0) {
			return;
		}

		List<String> memory = new ArrayList<>();
		for (int part = 0; part < keywordAt; part++) {
			rememberMemoryQualifier(memory, this.tokens.get(parts.get(part)));
		}

		int cursor = keywordAt + 1;
		while (cursor < parts.size() && isQualifier(this.tokens.get(parts.get(cursor)))) {
			rememberMemoryQualifier(memory, this.tokens.get(parts.get(cursor)));
			cursor++;
		}

		if (cursor >= parts.size() || this.tokens.get(parts.get(cursor)).kind() != Kind.IDENTIFIER) {
			return;
		}

		String type = this.tokens.get(parts.get(cursor)).text();

		boolean opaque = LegacyGlsl.isOpaqueType(type);

		String format = opaque && LegacyGlsl.isImageType(type) ? imageFormat(parts, keywordAt) : "";
		if (!readDeclarators(parts, cursor + 1, type, opaque ? String.join(" ", memory) : "", format,
				opaque ? this.samplers : this.blockMembers, aliases)) {
			return;
		}

		this.tokens.blankRange(start, end);
	}

	private void nameCustomImageViews() {
		Map<String, String> renames = new LinkedHashMap<>();
		Map<String, String> renamed = new LinkedHashMap<>();
		this.samplers.forEach((name, declaration) -> {
			String emitted = name;
			var image = CustomImages.image(name);
			if (image.isPresent()) {
				String type = declaration.substring(0, declaration.indexOf(' '));
				var base = image.get().internalFormat().used();
				var requested = CustomImageView.requested(base, type, this.imageFormats.get(name));
				if (requested.isPresent() && requested.get() != base) {
					if (!CustomImageView.compatible(base, requested.get())) {
						throw new IllegalArgumentException("Incompatible image view for " + name
								+ ": " + base + " as " + requested.get());
					}
					emitted = new CustomImageView(name, requested.get()).name();
					renames.put(name, emitted);
					String qualifiers = this.memoryQualifiers.remove(name);
					if (qualifiers != null) this.memoryQualifiers.put(emitted, qualifiers);
					String format = this.imageFormats.remove(name);
					if (format != null) this.imageFormats.put(emitted, format);
					declaration = type + " " + emitted
							+ declaration.substring(type.length() + 1 + name.length());
				}
			}
			renamed.put(emitted, declaration);
		});
		if (renames.isEmpty()) return;
		this.samplers.clear();
		this.samplers.putAll(renamed);
		for (int at = 0; at < this.tokens.size(); at++) {
			Token token = this.tokens.get(at);
			String replacement = renames.get(token.text());
			if (replacement != null && token.kind() == Kind.IDENTIFIER && !token.macroName()
					&& (token.directive() == null || "define".equals(token.directive()))) {
				this.tokens.replace(at, replacement);
			}
		}
		this.macroAliases.replaceAll((_, value) -> renames.getOrDefault(value, value));
	}

	private String imageFormat(List<Integer> parts, int keywordAt) {
		for (int part = 0; part < keywordAt; part++) {
			Token token = this.tokens.get(parts.get(part));
			if (token.kind() == Kind.IDENTIFIER && CustomImages.isLayoutFormat(token.text())) {
				return token.text();
			}
		}

		return "";
	}

	private void collectComparisonSamplers() {
		int[] lines = this.tokens.lineNumbers();
		int depth = 0;
		int parameters = -1;
		boolean arithmetic = softCompare() || this.stage == ProgramStage.COMPUTE;
		List<Integer> parameterTypes = new ArrayList<>();
		List<Scoped> parameterNames = new ArrayList<>();

		for (int index = 0; index < this.tokens.size(); index++) {
			Token token = this.tokens.get(index);
			if (token.directive() == null && token.operator("(")) {
				if (depth == 0) {
					parameters = index;
				}

				depth++;
			} else if (token.directive() == null && token.operator(")")) {
				depth--;
			}

			if (token.kind() != Kind.IDENTIFIER || token.directive() != null
					|| !this.unit.isLive(lines[index])) {
				continue;
			}

			String plain = withoutComparison(token.text());
			if (plain.equals(token.text()) || !LegacyGlsl.isOpaqueType(token.text())) {
				continue;
			}

			int last = depth > 0 ? this.tokens.functionEnd(parameters) : this.tokens.size() - 1;
			List<Scoped> introduced = new ArrayList<>();
			boolean bindable = !arithmetic;
			for (int scan = this.tokens.significantAfter(index); scan >= 0;
					scan = this.tokens.significantAfter(scan)) {
				Token next = this.tokens.get(scan);
				if (next.operator(";") || (depth > 0 && (next.operator(",") || next.operator(")")))) {
					break;
				}

				if (next.kind() == Kind.IDENTIFIER) {
					introduced.add(new Scoped(next.text(), lines[index], lines[last]));
					if (depth == 0 && (SamplerPlan.classify(next.text())
							!= SamplerPlan.Kind.SHADOW_DEPTH
							|| CustomImages.named(next.text()))) {
						bindable = false;
					}
				}
			}

			if (depth > 0) {
				parameterTypes.add(index);
				parameterNames.addAll(introduced);
			} else if (bindable) {
				this.hardwareComparisonSamplers.addAll(introduced);
			} else {
				this.tokens.replace(index, plain);
				this.comparisonSamplers.addAll(introduced);
				this.retypedSamplers.addAll(introduced);
			}
		}

		if (!this.hardwareComparisonSamplers.isEmpty()) {
			this.hardwareComparisonSamplers.addAll(parameterNames);

			return;
		}

		for (int index : parameterTypes) {
			this.tokens.replace(index, withoutComparison(this.tokens.get(index).text()));
		}

		this.comparisonSamplers.addAll(parameterNames);
	}

	private void collectSamplerParameters() {
		int[] lines = this.tokens.lineNumbers();
		int depth = 0;
		int parameters = -1;
		int position = 0;

		for (int index = 0; index < this.tokens.size(); index++) {
			Token token = this.tokens.get(index);
			if (token.directive() == null && token.operator("(")) {
				if (depth == 0) {
					parameters = index;
					position = 0;
				}

				depth++;
			} else if (token.directive() == null && token.operator(")")) {
				depth--;
			} else if (token.directive() == null && depth == 1 && token.operator(",")) {
				position++;
			}

			if (depth <= 0 || parameters < 0 || token.directive() != null
					|| token.kind() != Kind.IDENTIFIER || !LegacyGlsl.isOpaqueType(token.text())) {
				continue;
			}

			int name = this.tokens.significantAfter(index);
			if (name >= 0 && this.tokens.get(name).kind() == Kind.IDENTIFIER) {
				Scoped parameter = new Scoped(this.tokens.get(name).text(), lines[index],
						lines[this.tokens.functionEnd(parameters)]);
				this.samplerParameters.add(parameter);
				if (!levelled(token.text())) {
					this.unlevelledParameters.add(parameter);
				}

				int function = this.tokens.significantBefore(parameters);
				if (function >= 0 && this.tokens.get(function).kind() == Kind.IDENTIFIER) {
					this.typedParameters.add(new SamplerParameter(this.tokens.get(function).text(),
							position, parameter));
				}
			}
		}
	}

	private void moveCenterDepth() {
		if (this.stage == ProgramStage.COMPUTE || !movesCenterDepth()
				|| this.packMacros.contains(CENTER_DEPTH)) {
			return;
		}

		int[] lines = this.tokens.lineNumbers();
		List<Integer> members = new ArrayList<>();
		List<Integer> reads = new ArrayList<>();

		for (int index = 0; index < this.tokens.size(); index++) {
			Token token = this.tokens.get(index);
			if (token.kind() != Kind.IDENTIFIER || !token.text().equals(CENTER_DEPTH)
					|| token.directive() != null || !this.unit.isLive(lines[index])) {
				continue;
			}

			int type = declarationType(index);
			if (type < 0) {
				reads.add(index);
			} else if (uniformFloat(type)) {
				members.add(index);
			}
		}

		if (members.isEmpty()) {
			return;
		}

		String texel = SamplerPlan.centerDepth();
		members.forEach(this::detachMember);

		this.samplers.put(texel, SAMPLER_2D + " " + texel);
		reads.forEach(read -> this.tokens.inject(read, LOOKUP + "(" + texel + ", vec2(0.5)).r"));
	}

	private boolean movesCenterDepth() {
		return fullScreenPass();
	}

	private boolean fullScreenPass() {
		String family = ProgramNames.familyOf(this.program);

		return !this.program.isEmpty() && !ProgramNames.geometry(family)
				&& !ProgramNames.shadowComposite(family);
	}

	private int declarationType(int name) {
		int cursor = name;
		while (cursor > 0) {
			int before = this.tokens.significantBefore(cursor);
			if (before < 0) {
				return -1;
			}

			Token token = this.tokens.get(before);
			if (LegacyGlsl.TYPE_NAMES.contains(token.text())) {
				return before;
			}

			if (!token.operator(",")) {
				return -1;
			}

			int previous = this.tokens.significantBefore(before);
			if (previous < 0 || this.tokens.get(previous).kind() != Kind.IDENTIFIER) {
				return -1;
			}

			cursor = previous;
		}

		return -1;
	}

	private boolean uniformFloat(int type) {
		if (!this.tokens.get(type).text().equals(CENTER_DEPTH_TYPE)) {
			return false;
		}

		int cursor = this.tokens.significantBefore(type);
		while (cursor >= 0 && isQualifier(this.tokens.get(cursor))) {
			cursor = this.tokens.significantBefore(cursor);
		}

		return cursor >= 0 && this.tokens.get(cursor).identifier("uniform");
	}

	private void detachMember(int name) {
		int before = this.tokens.significantBefore(name);
		if (before >= 0 && this.tokens.get(before).operator(",")) {
			this.tokens.blank(before);
			this.tokens.blank(name);

			return;
		}

		int after = this.tokens.significantAfter(name);
		if (after >= 0 && this.tokens.get(after).operator(",")) {
			this.tokens.blank(name);
			this.tokens.blank(after);

			return;
		}

		int start = this.tokens.statementStart(name);
		int end = this.tokens.statementEnd(name);
		if (start < 0 || end < 0) {

			return;
		}

		this.tokens.blankRange(start, end);
	}

	private void collectStorageBlocks() {
		int[] lines = this.tokens.lineNumbers();

		for (int index = 0; index < this.tokens.size(); index++) {
			Token token = this.tokens.get(index);
			if (!token.identifier("buffer") || token.directive() != null
					|| !this.unit.isLive(lines[index])) {
				continue;
			}

			int name = this.tokens.significantAfter(index);
			if (name < 0 || this.tokens.get(name).kind() != Kind.IDENTIFIER) {
				continue;
			}

			int brace = this.tokens.significantAfter(name);
			if (brace < 0 || !this.tokens.get(brace).operator("{")) {
				continue;
			}

			int binding = layoutBinding(index);
			note(this.tokens.get(name).text(), binding);
			int close = this.tokens.matchingBracket(brace);
			int instance = close < 0 ? -1 : this.tokens.significantAfter(close);
			if (instance >= 0 && this.tokens.get(instance).kind() == Kind.IDENTIFIER) {
				note(this.tokens.get(instance).text(), binding);
			}
		}
	}

	private void note(String name, int binding) {
		if (this.storageBlocks.stream().noneMatch(block -> block.name().equals(name))) {
			this.storageBlocks.add(new TranslatedUnit.StorageBlock(name, binding));
		}
	}

	private int layoutBinding(int bufferIndex) {
		int start = 0;
		for (int scan = bufferIndex - 1; scan >= 0; scan--) {
			if (this.tokens.get(scan).operator(";")) {
				start = scan + 1;
				break;
			}
		}

		for (int scan = start; scan < bufferIndex; scan++) {
			if (!this.tokens.get(scan).identifier("binding")) {
				continue;
			}

			int equals = this.tokens.significantAfter(scan);
			if (equals < 0 || !this.tokens.get(equals).operator("=")) {
				continue;
			}

			int number = this.tokens.significantAfter(equals);
			if (number < 0 || this.tokens.get(number).kind() != Kind.NUMBER) {
				continue;
			}

			try {
				return Integer.parseInt(this.tokens.get(number).text());
			} catch (NumberFormatException ignored) {
				return -1;
			}
		}

		return -1;
	}

	private boolean comparisonAt(String name, int line) {
		return scoped(this.comparisonSamplers, name, line);
	}

	private boolean hardwareComparisonAt(String name, int line) {
		return scoped(this.hardwareComparisonSamplers, name, line);
	}

	private static boolean scoped(Collection<Scoped> names, String name, int line) {
		for (Scoped scoped : names) {
			if (scoped.name().equals(name) && line >= scoped.from() && line <= scoped.to()) {
				return true;
			}
		}

		return false;
	}

	private static String withoutComparison(String type) {
		String shape = SamplerTypes.shapeOf(type);
		if (shape == null || !shape.endsWith(SHADOW_SHAPE)) {
			return type;
		}

		return type.substring(0, type.length() - SHADOW_SHAPE.length());
	}

	private boolean readDeclarators(List<Integer> parts, int from, String type, String memory,
			String format, Map<String, String> target) {
		return readDeclarators(parts, from, type, memory, format, target, Map.of());
	}

	private boolean readDeclarators(List<Integer> parts, int from, String type, String memory,
			String format, Map<String, String> target, Map<String, String> aliases) {
		int cursor = from;
		boolean any = false;

		while (cursor < parts.size()) {
			Token token = this.tokens.get(parts.get(cursor));
			if (token.operator(";")) {
				break;
			}

			if (token.kind() != Kind.IDENTIFIER) {
				return false;
			}

			String name = liftedName(token.text(), aliases);
			if (!memory.isEmpty()) {
				this.memoryQualifiers.put(name, memory);
			}

			if (!format.isEmpty()) {
				this.imageFormats.put(name, format);
			}

			StringBuilder declaration = new StringBuilder(type).append(' ').append(name);
			cursor++;

			while (cursor < parts.size() && this.tokens.get(parts.get(cursor)).operator("[")) {
				int close = this.tokens.matchingBracket(parts.get(cursor));
				if (close < 0) {
					return false;
				}

				while (cursor < parts.size() && parts.get(cursor) <= close) {
					declaration.append(this.tokens.get(parts.get(cursor)).text());
					cursor++;
				}
			}

			if (cursor < parts.size() && this.tokens.get(parts.get(cursor)).operator("=")) {
				while (cursor < parts.size()) {
					Token skipped = this.tokens.get(parts.get(cursor));
					if (skipped.operator(",") || skipped.operator(";")) {
						break;
					}

					cursor++;
				}
			}

			record(target, name, declaration.toString());
			any = true;

			if (cursor < parts.size() && this.tokens.get(parts.get(cursor)).operator(",")) {
				cursor++;
			}
		}

		return any;
	}

	private static String liftedName(String name, Map<String, String> aliases) {
		String original = name;
		Set<String> seen = new HashSet<>();
		while (aliases.containsKey(name)) {
			if (!seen.add(name)) {
				return original;
			}
			name = aliases.get(name);
		}
		return name;
	}

	private void record(Map<String, String> target, String name, String declaration) {
		String existing = target.putIfAbsent(name, declaration);
		if (existing != null && !existing.equals(declaration)) {
			this.conflicts.add(name);
		}
	}

	private Emitter emitter(Map<String, String> memory, Map<String, String> formats) {
		return new Emitter(this.stage, this.inputs, this.bound, this.alphaTest, this.extensions,
				this.engineDefines,
				memory, formats, this.used, this.declaredNames,
				this.synthesized,
				this.volumes.read(), this.packOutputs, this.maxFragmentOutput, this.owedOutputs,
				this.splits, this.gameTextureMatrix,
				this.gameModelView, this.softRewrites, this.trigCalls, this.hashCalls,
				this.packBuiltinCalls, this.mainWrapped, this.depthEpilogue, this.terrainPrologue,
				this.distantPrologue, this.entityWrapped, this.linesWrapped, this.alphaEpilogue, this.covers,
				wrapsFragment(), this.ordered, this.namesFragDepth, this.makesOverlayColour,
				this.coreProfile);
	}

	private Set<String> declaredUnderAType() {
		Set<String> names = new HashSet<>();
		this.packOutputs.values().forEach(output -> names.add(output.name()));

		for (int index = 0; index < this.tokens.size(); index++) {
			Token token = this.tokens.get(index);
			if (token.kind() != Kind.IDENTIFIER || token.directive() != null) {
				continue;
			}

			int before = this.tokens.significantBefore(index);
			if (before >= 0 && LegacyGlsl.TYPE_NAMES.contains(this.tokens.get(before).text())) {
				names.add(token.text());
			}
		}

		return names;
	}

	private Set<String> sampledNames() {
		boolean[] declared = new boolean[this.tokens.size()];
		int[] lines = this.tokens.lineNumbers();
		for (int index = 0; index < this.tokens.size(); index++) {
			Token token = this.tokens.get(index);
			if (!token.identifier("uniform") || token.directive() != null
					|| !this.unit.isLive(lines[index])) {
				continue;
			}

			FileScope scope = fileScopeDeclaration(index);
			if (scope == null) {
				continue;
			}

			for (int at = scope.start(); at <= scope.end() && at < declared.length; at++) {
				declared[at] = true;
			}
		}

		Set<String> sampled = new LinkedHashSet<>();
		for (int index = 0; index < this.tokens.size(); index++) {
			if (declared[index]) {
				continue;
			}

			Token token = this.tokens.get(index);
			if (token.kind() == Kind.IDENTIFIER) {
				String name = liftedName(token.text(), this.macroAliases);
				if (this.samplers.containsKey(name)) {
					sampled.add(name);
				}
			}
		}

		return sampled;
	}

	private Set<String> usedNames() {
		Set<String> names = new HashSet<>(this.injectedNames);
		for (Token token : this.tokens) {
			if (token.kind() == Kind.IDENTIFIER) {
				names.add(token.text());
			}
		}

		return names;
	}

	private TranslatedUnit.Notes notes() {
		return new TranslatedUnit.Notes(this.maxFragmentOutput + 1, this.dynamicFragData,
				this.conflicts.size(), this.shadowCalls, this.unwrappedShadowCalls,
				this.strippedExtensions, this.depthEpilogue ? 1 : 0, this.alphaEpilogue ? 1 : 0,
				this.covers ? 1 : 0, this.depthLookups,
				this.parameterLookups, this.pinnedLookups, this.unpinnedParameterLookups,
				this.fragCoordZ, this.fragCoordXyz,
				this.fragCoordUnhandled, this.fragDepthWrites, this.fragDepthUnhandled,
				List.copyOf(this.conflicts), comparedSamplers(), hardwareComparedSamplers(),
				retypedSamplers(), List.copyOf(this.storageBlocks),
				this.volumes.lookups(), this.volumes.leftAlone(), this.trigCalls, this.gameTextureMatrix,
				this.gameModelView);
	}

	private List<String> comparedSamplers() {
		return descriptors(Stream.concat(this.comparisonSamplers.stream(),
				this.hardwareComparisonSamplers.stream()));
	}

	private List<String> hardwareComparedSamplers() {
		return descriptors(this.hardwareComparisonSamplers.stream());
	}

	private List<String> retypedSamplers() {
		return descriptors(this.retypedSamplers.stream());
	}

	private List<String> descriptors(Stream<Scoped> scoped) {
		return scoped
				.map(Scoped::name)
				.distinct()
				.filter(this.samplers::containsKey)
				.toList();
	}

	private static void rememberMemoryQualifier(List<String> memory, Token token) {
		String text = token.text();
		if (token.kind() == Kind.IDENTIFIER && LegacyGlsl.MEMORY_QUALIFIERS.contains(text)
				&& !memory.contains(text)) {
			memory.add(text);
		}
	}

	static boolean isQualifier(Token token) {
		return token.kind() == Kind.IDENTIFIER
				&& (LegacyGlsl.MEMORY_QUALIFIERS.contains(token.text())
						|| LegacyGlsl.PRECISION_QUALIFIERS.contains(token.text()));
	}
}
