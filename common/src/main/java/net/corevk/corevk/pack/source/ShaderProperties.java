package net.corevk.corevk.pack.source;

import net.corevk.corevk.pack.option.OptionIndex;
import net.corevk.corevk.pack.option.OptionValue;
import net.corevk.corevk.pack.option.PackOption;
import net.corevk.corevk.pack.model.AlphaTest;
import net.corevk.corevk.pack.model.TargetSize;
import net.corevk.corevk.pack.model.BufferObject;
import net.corevk.corevk.pack.model.ImageInformation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ShaderProperties {

	private static final String FILE_NAME = "shaders.properties";

	// Only the indentation of the joined line is swallowed, never a blank line: {@code \\s*}
	// would cross one and make a continued key absorb whatever block follows it. Bliss ends
	// three of its continuations on a blank line, and its main screen swallowed the commented
	// block underneath.
	private static final Pattern CONTINUATION = Pattern.compile("\\\\\\r?\\n[ \\t]*");
	private static final Pattern DIRECTIVE = Pattern.compile("^\\s*#\\s*(if|ifdef|ifndef|else|elif|endif)\\b.*$");

	private static final Pattern PROFILE = Pattern.compile("^\\s*profile\\.(\\w+)\\s*=\\s*(.*)$");
	private static final Pattern PROGRAM_ENABLED = Pattern.compile("^\\s*program\\.(.+?)\\.enabled\\s*=\\s*(.*)$");
	private static final Pattern CUSTOM_UNIFORM =
			Pattern.compile("^\\s*(uniform|variable)\\.(\\w+)\\.(\\w+)\\s*=\\s*(.*)$");
	private static final Pattern SCREEN = Pattern.compile("^\\s*screen(\\.\\w+)?\\s*=\\s*(.*)$");
	// Both are read before SCREEN, and in this order. "screen.columns=2" matches SCREEN as well,
	// as a page named columns, which is how a page nobody can reach appears in the one pack that
	// writes the line; and "screen.NAME.columns=1" matches neither, so it would otherwise be counted
	// as an unknown key. Packs indent these lines and put spaces around the equals sign, both of which
	// are why the pattern has to be this loose.
	private static final Pattern MAIN_COLUMNS = Pattern.compile("^\\s*screen\\.columns\\s*=\\s*(\\d+)\\s*$");
	private static final Pattern PAGE_COLUMNS = Pattern.compile("^\\s*screen\\.(\\w+)\\.columns\\s*=\\s*(\\d+)\\s*$");
	private static final Pattern BLEND = Pattern.compile("^\\s*blend\\.([^=\\s.]+)(?:\\.(\\w+))?\\s*=\\s*(.*)$");
	private static final Pattern ALPHA_TEST = Pattern.compile("^\\s*alphaTest\\.(\\S+)\\s*=\\s*(.*)$");
	private static final Pattern SLIDERS = Pattern.compile("^\\s*sliders\\s*=\\s*(.*)$");
	// Two lines that say what a pack cannot draw without, and what it would use if it were there.
	// Space separated lists, and the last line of each wins, which is how CoreVK reads them.
	private static final Pattern COREVK_FEATURES =
			Pattern.compile("^\\s*corevk\\.features\\.(required|optional)\\s*=\\s*(.*)$");
	private static final Pattern END_FLASH_SHADOWS = Pattern.compile("^\\s*endFlashShadows\\s*=\\s*(.*)$");
	// Whether the rain and the snow write the world's depth. Read on the live lines like the family
	// below, and that is what CoreVK does too, by another road: it runs the whole file through its own
	// preprocessor with the pack's settings as macros before its parser sees a line at all
	// (CoreVK shaderpack/properties/ShaderProperties.java:132 and preprocessor/PropertiesPreprocessor
	// .java:28-63), so a dead branch is gone by then. Read flat, a pack writing this under an #if it
	// leaves off would get depth-writing rain here and none there.
	private static final Pattern RAIN_DEPTH = Pattern.compile("^\\s*rain\\.depth\\s*=\\s*(.*)$");
	// Which of the world's six families are drawn into the shadow map, the six CoreVK parses
	// (shaderpack/properties/ShaderProperties.java:192-197). One pattern for the lot, because they
	// are read together and answered together: a caster the pack asked to keep out and that casts
	// anyway is worse than no shadow at all, so they are served as a set or not.
	// Live lines only, and here that is not a nicety. Two packs of the eight tested write one of
	// these words twice with two different values, both Complementary packs writing shadowPlayer
	// true and then false in the two arms of one conditional: read flat, the answer is whichever
	// line the file happens to end with. BSL is the sharper case and it is not a repeat at all - it
	// writes shadowEntities and shadowBlockEntities once each, under an #ifndef its own settings
	// file defines, so both lines are dead and a flat read turns the most used pack of the corpus
	// into one with no entity shadows.
	private static final Pattern SHADOW_CASTER = Pattern.compile(
			"^\\s*(shadowTerrain|shadowTranslucent|shadowEntities|shadowPlayer|shadowBlockEntities"
					+ "|shadowLightBlockEntities)\\s*=\\s*(.*)$");
	// Which shape the light measures a section against. Of the same family by its name, and read on
	// the live lines for the same reason, but it is not a caster word and it is not a boolean: the
	// value is one of four states, and ShadowCullState carries what each of the written words means.
	private static final Pattern SHADOW_CULLING = Pattern.compile("^\\s*shadow\\.culling\\s*=\\s*(.*)$");
	// Whether Distant Horizons' far terrain is drawn into the map. A seventh caster word by what it
	// decides, kept out of SHADOW_CASTER because its default is the other way round: the six above
	// are answered by a set the engine ships, and this one is on for a pack that says nothing
	// (pipeline/CoreVKRenderingPipeline.java:408). Six of the eight packs of the corpus write it and
	// five of the six write false, so a flat default would put the far terrain in five maps that
	// asked to keep it out.
	private static final Pattern DH_SHADOW = Pattern.compile("^\\s*dhShadow\\.enabled\\s*=\\s*(.*)$");
	// Whether the terrain's ambient occlusion is kept out of the vertex colour. Read on the live
	// lines like every other directive of this file, and the note on RAIN_DEPTH says why that is
	// what the reference does too.
	private static final Pattern SEPARATE_AO = Pattern.compile("^\\s*separateAo\\s*=\\s*(.*)$");
	// Whether the pack declares that anisotropy breaks the way it reads the block atlas. Read on the
	// live lines and on separateAo's rule in every respect, and answered the same way.
	private static final Pattern BREAKS_ANISOTROPY =
			Pattern.compile("^\\s*breaksAnisotropy\\s*=\\s*(.*)$");
	// Whether the main hand reports the brighter of the two hands' light, which is what a pack
	// written before the off hand had a light of its own expects. Read on the live lines and on
	// separateAo's rule in every respect, the default alone being the other way round. Seven of the
	// eleven packs of the corpus write it, all seven write false, and none writes true, so a flat
	// default would have an off hand torch answering for seven main hands that asked it not to.
	private static final Pattern OLD_HAND_LIGHT = Pattern.compile("^\\s*oldHandLight\\s*=\\s*(.*)$");
	// Whether the game's own per face brightness stays in the chunk mesh. Read on the live
	// lines and on separateAo's rule, and the default is off, which is the reference's
	// (shaderpack/properties/PackDirectives.java:92, getOldLighting().orElse(false)): a pack
	// shades from the normal it is handed, so the game's factor underneath it shades twice.
	private static final Pattern OLD_LIGHTING = Pattern.compile("^\\s*oldLighting\\s*=\\s*(.*)$");
	private static final Pattern SIZE_BUFFER = Pattern.compile("^\\s*size\\.buffer\\.([^=\\s.]+)\\s*=\\s*(.*)$");
	private static final Pattern SKY_ELEMENT = Pattern.compile("^\\s*(sun|moon|stars|sky)\\s*=\\s*(.*)$");
	// The fifth word of that family, kept apart because it is the one that takes neither a yes nor a
	// no: a pack writes off, fast or fancy, which are the game's own three cloud settings.
	private static final Pattern CLOUDS = Pattern.compile("^\\s*clouds\\s*=\\s*(.*)$");
	// The word for the clouds Distant Horizons draws past the world's own, which are a generic object
	// of that mod's rather than anything of the game's cloud renderer, so the line above never reaches
	// them. It is not that line's vocabulary: a pack writes off, or on, which fancy also means.
	private static final Pattern DH_CLOUDS = Pattern.compile("^\\s*dhClouds\\s*=\\s*(.*)$");
	// And the one of that same family that carries two words rather than one, so it has a pattern
	// of its own: the first says whether the rain and the snow are drawn at all, the second whether
	// the splashes they leave on the ground are.
	private static final Pattern WEATHER = Pattern.compile("^\\s*weather\\s*=\\s*(.*)$");
	// Where the pack wants its particles drawn about the deferred stage. Two spellings, and the
	// second is the older one: CoreVK reads particles.before.deferred=true as an ordering of BEFORE
	// where nothing has already been said, so it is folded into the same answer here rather than
	// being a directive of its own that a reader would have to know about separately.
	private static final Pattern PARTICLE_ORDERING =
			Pattern.compile("^\\s*particles\\.ordering\\s*=\\s*(.*)$");
	private static final Pattern PARTICLES_BEFORE_DEFERRED =
			Pattern.compile("^\\s*particles\\.before\\.deferred\\s*=\\s*(.*)$");
	// The noise image is answered here because everything else about it is settled: one path, one
	// sampler, every stage. The general family is not, and is read by customTextures instead.
	private static final Pattern TEXTURE_NOISE = Pattern.compile("^\\s*texture\\.noise\\s*=\\s*(.*)$");
	private static final Pattern SHADOW_ENABLED =
			Pattern.compile("^\\s*shadow\\.enabled\\s*=\\s*(.*)$");
	private static final Pattern CUSTOM_TEXTURE =
			Pattern.compile("^\\s*((?:texture|customTexture)\\.[^=\\s]+)\\s*=\\s*(.*)$");
	private static final Pattern IMAGE = Pattern.compile("^\\s*image\\.([^=\\s.]+)\\s*=\\s*(.*)$");
	private static final Pattern BUFFER_OBJECT =
			Pattern.compile("^\\s*bufferObject\\.(\\d+)\\s*=\\s*(.*)$");
	private static final Pattern FLIP = Pattern.compile("^\\s*flip\\.([^=\\s.]+)\\.([^=\\s.]+)\\s*=\\s*(.*)$");
	private static final Pattern OTHER_KEY = Pattern.compile("^\\s*([A-Za-z_][\\w]*)[.=].*$");

	private static final Pattern SCREEN_TOKEN = Pattern.compile("^[A-Za-z_]\\w*$");

	
	private static final int MAX_PROFILE_DEPTH = 8;

	
	private static final int MAX_PROFILE_EXPANSIONS = 10_000;

	private final List<String> lines;
	private final Map<String, String> profiles;
	private final Map<String, String> customUniformTypes;
	private final Set<String> screenTokens;
	private final Map<String, List<ScreenToken>> screenLayout;
	private final Map<String, Integer> columns;
	private final List<String> sliders;
	private final List<String> requiredFeatures;
	private final List<String> optionalFeatures;
	private final Map<String, Integer> ignoredPrefixes;
	private final int directiveCount;
	private final int blendCount;
	private final int continuationCount;
	private final boolean present;

	private ShaderProperties(Builder builder) {
		this.lines = List.copyOf(builder.lines);
		// Ordered, like the layout below: a profile selector walks these in the order the pack
		// wrote them, and BSL's five run from MINIMUM to ULTRA rather than in any order.
		this.profiles = Collections.unmodifiableMap(new LinkedHashMap<>(builder.profiles));
		this.customUniformTypes = Map.copyOf(builder.customUniformTypes);
		this.screenTokens = Set.copyOf(builder.screenTokens);
		// Not Map.copyOf: the order pages are declared in is the order a screen offers them, and
		// an immutable map does not keep one.
		Map<String, List<ScreenToken>> layout = new LinkedHashMap<>();
		builder.screenLayout.forEach((page, tokens) -> layout.put(page, List.copyOf(tokens)));
		this.screenLayout = Collections.unmodifiableMap(layout);
		this.columns = Map.copyOf(builder.columns);
		this.sliders = List.copyOf(builder.sliders);
		this.requiredFeatures = List.copyOf(builder.requiredFeatures);
		this.optionalFeatures = List.copyOf(builder.optionalFeatures);
		this.ignoredPrefixes = Map.copyOf(builder.ignoredPrefixes);
		this.directiveCount = builder.directiveCount;
		this.blendCount = builder.blendCount;
		this.continuationCount = builder.continuationCount;
		this.present = builder.present;
	}

	public static ShaderProperties parse(ShaderPackSource source) throws IOException {
		Builder builder = new Builder();
		Optional<Path> file = source.file(FILE_NAME);
		if (file.isEmpty()) {
			return new ShaderProperties(builder);
		}

		builder.present = true;

		builder.lines = List.copyOf(source.readLines(file.get()));

		String text = String.join("\n", builder.lines);
		Matcher continuation = CONTINUATION.matcher(text);
		while (continuation.find()) {
			builder.continuationCount++;
		}

		// Counted on the lines as written and not on the fold below, since those are the lines the
		// conditional readers apply: a directive a continued value swallows is one the file carries
		// and one that decides a branch, so a census taken after the fold reports fewer directives
		// than the pack has. Photon's #else at shaders.properties:495 is the one in the corpus.
		for (String line : builder.lines) {
			if (DIRECTIVE.matcher(line).matches()) {
				builder.directiveCount++;
			}
		}

		// The flat road of the two the class comment describes: no setting exists yet to decide a
		// conditional with, so this reads the file folded and straight through.
		for (String line : CONTINUATION.matcher(text).replaceAll(" ").split("\n", -1)) {
			read(line, builder);
		}

		return new ShaderProperties(builder);
	}

	private static void read(String line, Builder builder) {
		if (DIRECTIVE.matcher(line).matches()) {
			return;
		}

		Matcher profile = PROFILE.matcher(line);
		if (profile.matches()) {
			builder.profiles.put(profile.group(1), profile.group(2).trim());
			return;
		}

		// Read flat here and re-read conditionally later. The keys have to be known before the
		// conditionals can be evaluated, because a profile may decide them.
		if (PROGRAM_ENABLED.matcher(line).matches()) {
			return;
		}

		Matcher uniform = CUSTOM_UNIFORM.matcher(line);
		if (uniform.matches()) {
			builder.customUniformTypes.put(uniform.group(3), uniform.group(2));
			return;
		}

		Matcher mainColumns = MAIN_COLUMNS.matcher(line);
		if (mainColumns.matches()) {
			putColumns(builder, "", mainColumns.group(1));
			return;
		}

		Matcher pageColumns = PAGE_COLUMNS.matcher(line);
		if (pageColumns.matches()) {
			putColumns(builder, pageColumns.group(1), pageColumns.group(2));
			return;
		}

		Matcher screen = SCREEN.matcher(line);
		if (screen.matches()) {
			// The name of the page, "" for the one the pack opens on. A sub page is referred to
			// from its parent by its own name, so the two are joined by name rather than nested.
			String page = screen.group(1) == null ? "" : screen.group(1).substring(1);
			// A second line for the same page REPLACES the first, which is how the reference
			// reads the pair: the last assignment of a key is the key. Only the layout starts
			// over; the token census below keeps counting what the pack wrote, dead line or not.
			List<ScreenToken> slots = new ArrayList<>();
			builder.screenLayout.put(page, slots);

			// A run of SPACES and not a run of whitespace. The list directives are the only ones CoreVK
			// splits on more than one space, and even there a tab is not a separator; every other
			// directive of this file is split on a single space, which is why the two forms sit side
			// by side here rather than one being used throughout.
			for (String token : screen.group(2).trim().split(" +", -1)) {
				if (token.isEmpty()) {
					continue;
				}

				if (SCREEN_TOKEN.matcher(token).matches()) {
					builder.screenTokens.add(token);
				}

				slots.add(slotOf(token));
			}

			return;
		}

		// Consumed here for the census only; the values come out of blend, which walks the
		// conditionals for the reason alphaTests gives just below.
		if (BLEND.matcher(line).matches()) {
			builder.blendCount++;
			return;
		}

		// Consumed here for the census only; the values come out of alphaTests, which walks the
		// conditionals, because the reference reads this family off its preprocessed copy of the
		// file. Not every family: it reads the screens, the sliders, the profiles and the feature
		// lists off the raw one.
		if (ALPHA_TEST.matcher(line).matches()) {
			return;
		}

		// Replaced and not added to: a second line of the same name is the one that counts, which is
		// how CoreVK reads both of them, and not how the sliders below are read, where the tokens of a
		// second line join the first. Read so the refusal can say something: a pack that names
		// what it cannot do without is answered with the names this engine has not built, instead
		// of falling over later on whichever sampler its storage buffers happened to sit behind.
		// What is served is settled at the chain, not here: this only carries the names.
		Matcher features = COREVK_FEATURES.matcher(line);
		if (features.matches()) {
			List<String> named = new ArrayList<>();
			for (String token : features.group(2).trim().split(" +", -1)) {
				if (!token.isEmpty()) {
					named.add(token);
				}
			}

			if (features.group(1).equals("required")) {
				builder.requiredFeatures = named;
			} else {
				builder.optionalFeatures = named;
			}

			return;
		}

		Matcher sliders = SLIDERS.matcher(line);
		if (sliders.matches()) {
			for (String token : sliders.group(1).trim().split(" +", -1)) {
				if (SCREEN_TOKEN.matcher(token).matches()) {
					builder.sliders.add(token);
				}
			}

			return;
		}

		// Consumed here for the census only; the value comes out of endFlashShadows, which walks
		// the conditionals.
		if (END_FLASH_SHADOWS.matcher(line).matches()) {
			return;
		}

		// Consumed here and read by rainDepth, which walks the conditionals. Consumed only where the
		// value is one this engine reads, which is the rule the branches below follow: anything else
		// belongs among the keys nothing reads, where the pack's author can see it.
		Matcher rainDepth = RAIN_DEPTH.matcher(line);
		if (rainDepth.matches() && truth(rainDepth.group(1).trim()) != null) {
			return;
		}

		// Same rule for the shadow casters: consumed where the word is one this engine acts on, and
		// left among the keys nothing reads where it is not, so a pack writing a fifth spelling can
		// see that it was not understood.
		Matcher caster = SHADOW_CASTER.matcher(line);
		if (caster.matches() && truth(caster.group(2).trim()) != null) {
			return;
		}

		// The seventh word of that family, on the casters' rule and not the culling state's: a value
		// this cannot read leaves the default standing, and the default is ON, so a line nothing
		// could read has to stay visible rather than pass for a refusal that was honoured.
		Matcher dhShadow = DH_SHADOW.matcher(line);
		if (dhShadow.matches() && truth(dhShadow.group(1).trim()) != null) {
			return;
		}

		// The culling state goes with separateAo below rather than with the casters above, and for
		// that entry's reason: a word this cannot read is not stepped over, it is what puts the
		// state back to the default, so the line is read whether the word is one of the four or not.
		Matcher culling = SHADOW_CULLING.matcher(line);
		if (culling.matches()) {
			return;
		}

		// And NOT the same rule for this one, whatever its neighbour above does: a word this cannot
		// read is not ignored here, it is what leaves the default standing, so the line is read
		// whether the word is one or not. Left among the keys nothing reads, it would tell its
		// author that nothing looks at a line which had just overruled the one before it.
		Matcher separateAo = SEPARATE_AO.matcher(line);
		if (separateAo.matches()) {
			return;
		}

		// On that same rule, and read for the same reason: a word this cannot read leaves the
		// default standing rather than being stepped over.
		if (BREAKS_ANISOTROPY.matcher(line).matches()) {
			return;
		}

		// And on that rule too, for that reason: a word this cannot read is what puts the answer back
		// to the default, so the line is read whether the word is one of the four or not.
		if (OLD_HAND_LIGHT.matcher(line).matches()) {
			return;
		}

		// The same rule again, and the same reason.
		if (OLD_LIGHTING.matcher(line).matches()) {
			return;
		}

		// Consumed here for the census only; the values come out of sizeBuffers, which walks the
		// conditionals.
		if (SIZE_BUFFER.matcher(line).matches()) {
			return;
		}

		// Consumed only when the value is one of the four words that mean something, and read again
		// conditionally by skyElements. A line that says anything else falls through to the ignored
		// keys, which is where a reader has to be able to find it: one pack of the corpus writes
		// sun=off and moon=off, which neither CoreVK nor OptiFine reads as false, so its sun and its
		// moon keep being drawn and
		// nothing else would say why.
		Matcher element = SKY_ELEMENT.matcher(line);
		if (element.matches() && truth(element.group(2).trim()) != null) {
			return;
		}

		// The same rule for the fifth word, on its own three values: a clouds= line naming something
		// else is a line nothing here reads, and it belongs among the keys that say so.
		Matcher clouds = CLOUDS.matcher(line);
		if (clouds.matches() && CloudSetting.of(clouds.group(1).trim()) != null) {
			return;
		}

		Matcher dhClouds = DH_CLOUDS.matcher(line);
		if (dhClouds.matches() && CloudSetting.ofDistant(dhClouds.group(1).trim()) != null) {
			return;
		}

		// The same rule for the two word member of that family, and it takes EITHER word: a line is
		// honoured as soon as one of the two says something this engine reads, so testing the first
		// alone would count "weather = on false" among the keys nothing reads while its second word
		// really did take the splashes away. A line whose two words are both unreadable falls
		// through, which is where a reader has to be able to find it.
		Matcher weather = WEATHER.matcher(line);
		if (weather.matches() && reads(weather.group(1).trim().split(" ", 0))) {
			return;
		}

		// Consumed and not kept: the two are read by particleOrdering, which walks the conditionals,
		// and falling through here would leave them among the keys nothing reads. That is not
		// cosmetic on this pair. Five packs of the corpus write particles.ordering, so the count
		// would say the engine ignores the very directive it now reads and writes a line about.
		//
		// And consumed only where the value is one that is really read, which is the rule the three
		// branches above follow and not the custom textures' rule: an ordering with no word after
		// the equals sign, or a before.deferred that is not one of the four booleans, is honoured
		// nowhere, so it has to stay visible among the keys nothing reads.
		Matcher ordering = PARTICLE_ORDERING.matcher(line);
		if (ordering.matches() && !ordering.group(1).trim().isEmpty()) {
			return;
		}

		// TRUE alone, and not any boolean: particleOrdering acts on the yes and does nothing at all
		// with the no, CoreVK reading the same word the same way. A false left consumed here would be
		// a line neither honoured nor counted.
		Matcher before = PARTICLES_BEFORE_DEFERRED.matcher(line);
		if (before.matches() && Boolean.TRUE.equals(truth(before.group(1).trim()))) {
			return;
		}

		// Consumed here for the census only, and only when the value is a boolean: the lines come
		// out of flips, which walks the conditionals, and anything else falls through to the
		// ignored keys.
		Matcher flip = FLIP.matcher(line);
		if (flip.matches() && flipValue(flip) != null) {
			return;
		}

		// Consumed here for the census only; the path comes out of noiseTexturePath, which walks
		// the conditionals like the other directive readers below.
		Matcher noise = TEXTURE_NOISE.matcher(line);
		if (noise.matches()) {
			return;
		}

		// Consumed on the casters' rule: a value that is not a boolean is honoured nowhere, so
		// it has to stay visible among the keys nothing reads. The state itself comes out of
		// shadowEnabled, under the conditionals.
		Matcher shadowEnabled = SHADOW_ENABLED.matcher(line);
		if (shadowEnabled.matches() && truth(shadowEnabled.group(1).trim()) != null) {
			return;
		}

		// Consumed and not kept: the rest of the family is read by customTextures, which walks the
		// conditionals. Falling through here would leave those lines among the keys nothing reads
		// and make that count say the engine ignores what it now honours.
		if (CUSTOM_TEXTURE.matcher(line).matches()) {
			return;
		}

		// Same reason as the texture family: the live lines are walked later, with the
		// conditionals, and falling through would count {@code image} as a prefix nobody reads.
		if (IMAGE.matcher(line).matches()) {
			return;
		}

		// Same reason as the image family: the live lines are walked later, and falling through
		// would count {@code bufferObject} as a prefix nobody reads.
		if (BUFFER_OBJECT.matcher(line).matches()) {
			return;
		}

		// Anything left is counted by its first segment rather than dropped. That is what makes
		// a typo visible: one pack writes "progam.<name>.enabled" and loses the program.
		Matcher other = OTHER_KEY.matcher(line);
		if (other.matches()) {
			builder.ignoredPrefixes.merge(other.group(1), 1, Integer::sum);
		}
	}

	
	private static void putColumns(Builder builder, String page, String text) {
		try {
			int count = Integer.parseInt(text);
			if (count > 0) {
				builder.columns.put(page, count);
			}
		} catch (NumberFormatException e) {
			// More digits than an int holds. The page keeps the default.
		}
	}

	
	private static ScreenToken slotOf(String token) {
		if (token.equals("<empty>")) {
			return new ScreenToken.Blank();
		}

		if (token.equals("<profile>")) {
			return new ScreenToken.Profiles();
		}

		if (token.startsWith("[") && token.endsWith("]")) {
			return new ScreenToken.Link(token.substring(1, token.length() - 1));
		}

		if (token.equals("*")) {
			return new ScreenToken.Rest();
		}

		return new ScreenToken.Name(token);
	}

	
	public Map<String, Boolean> programToggles(Map<String, String> defines, OptionIndex options) {
		Map<String, Boolean> toggles = new LinkedHashMap<>();
		programConditions(defines).forEach((program, expression) ->
				toggles.put(program, enabled(expression, defines, options)));

		return toggles;
	}

	
	public Set<String> switchedOff(Map<String, String> defines, OptionIndex options) {
		Set<String> off = new LinkedHashSet<>();
		programToggles(defines, options).forEach((program, on) -> {
			if (!on) {
				off.add(program);
			}
		});

		return Set.copyOf(off);
	}

	
	public Map<String, String> programConditions(Map<String, String> defines) {
		Map<String, String> expressions = new LinkedHashMap<>();

		PropertiesFile.walk(this.lines, defines, line -> {
			Matcher program = PROGRAM_ENABLED.matcher(line);
			if (program.matches()) {
				expressions.put(program.group(1), program.group(2).trim());
			}
		});

		return expressions;
	}

	
	private static boolean enabled(String expression, Map<String, String> defines,
			OptionIndex options) {
		if (expression.isEmpty()) {
			return true;
		}

		Boolean value = new EnabledExpression(expression, defines, options).parse();

		return value == null || value;
	}

	
	private static final class EnabledExpression {

		
		private static final int MAX_DEPTH = 64;

		private final String text;
		private final Map<String, String> defines;
		private final OptionIndex options;

		private int position;
		private int depth;
		private boolean failed;

		private EnabledExpression(String text, Map<String, String> defines, OptionIndex options) {
			this.text = text;
			this.defines = defines;
			this.options = options;
		}

		
		private Boolean parse() {
			boolean value = or();
			skipSpace();

			return (this.failed || this.position < this.text.length()) ? null : value;
		}

		private boolean or() {
			boolean left = and();
			while (accept("||") || accept("|")) {
				// Both sides are read whatever the left one said: short circuiting here would
				// leave the right one in the text and the whole expression unreadable.
				boolean right = and();
				left = left || right;
			}

			return left;
		}

		private boolean and() {
			boolean left = unary();
			while (accept("&&") || accept("&")) {
				boolean right = unary();
				left = left && right;
			}

			return left;
		}

		
		private boolean unary() {
			this.depth++;
			try {
				if (this.depth > MAX_DEPTH) {
					this.failed = true;
					return false;
				}

				if (accept("!")) {
					return !unary();
				}

				if (accept("(")) {
					boolean value = or();
					if (!accept(")")) {
						this.failed = true;
					}

					return value;
				}

				return truth(name());
			} finally {
				this.depth--;
			}
		}

		private String name() {
			skipSpace();
			int start = this.position;
			while (this.position < this.text.length() && isNamePart(this.text.charAt(this.position))) {
				this.position++;
			}

			if (start == this.position) {
				this.failed = true;
			}

			return this.text.substring(start, this.position);
		}

		private static boolean isNamePart(char c) {
			return Character.isLetterOrDigit(c) || c == '_' || c == '.';
		}

		
		private boolean truth(String name) {
			switch (name) {
				case "true", "1" -> {
					return true;
				}
				case "false", "0" -> {
					return false;
				}
				default -> {
				}
			}

			PackOption option = this.options.get(name).orElse(null);
			if (option == null) {
				return true;
			}

			if (option.kind() == PackOption.Kind.TOGGLE) {
				return this.defines.containsKey(name);
			}

			// A constant the index does not offer is not a setting, so its name answers here
			// the way an undeclared one does. Its declared value must not answer either: the
			// reference never carries it into this table, whatever the declaration says.
			if (option.kind() != PackOption.Kind.CONST || !"bool".equals(option.constType())
					|| !this.options.offers(option)) {
				return true;
			}

			String value = this.defines.get(name);

			return value != null && (value.trim().equals("true") || value.trim().equals("1"));
		}

		private boolean accept(String token) {
			skipSpace();
			if (!this.text.startsWith(token, this.position)) {
				return false;
			}

			this.position += token.length();

			return true;
		}

		private void skipSpace() {
			while (this.position < this.text.length()
					&& Character.isWhitespace(this.text.charAt(this.position))) {
				this.position++;
			}
		}
	}

	
	public List<CustomUniform> customUniforms(Map<String, String> defines) {
		List<CustomUniform> uniforms = new ArrayList<>();

		PropertiesFile.walkDefining(this.lines, defines, (line, live) -> {
			Matcher uniform = CUSTOM_UNIFORM.matcher(line);
			if (uniform.matches()) {
				uniforms.add(new CustomUniform(uniform.group(1).equals("uniform"), uniform.group(2),
						uniform.group(3), Macros.expand(uniform.group(4).trim(), live)));
			}
		});

		return uniforms;
	}

	
	public Map<String, String> customTextures(Map<String, String> defines) {
		Map<String, String> declared = new LinkedHashMap<>();

		PropertiesFile.walk(this.lines, defines, line -> {
			if (TEXTURE_NOISE.matcher(line).matches()) {
				return;
			}

			Matcher texture = CUSTOM_TEXTURE.matcher(line);
			if (texture.matches()) {
				declared.put(texture.group(1), texture.group(2).trim());
			}
		});

		return declared;
	}

	
	public SkyElements skyElements(Map<String, String> defines) {
		Map<String, Boolean> drawn = new LinkedHashMap<>();

		for (Matcher element : live(SKY_ELEMENT, defines)) {
			Boolean value = truth(element.group(2).trim());
			if (value != null) {
				drawn.put(element.group(1), value);
			}
		}

		// A walk of its own for the fifth word rather than a second test inside the first: what the
		// four words answer is a boolean and what this one answers is not, and one loop reading both
		// would carry the difference in the middle of it.
		CloudSetting clouds = CloudSetting.DEFAULT;
		for (Matcher asked : live(CLOUDS, defines)) {
			CloudSetting setting = CloudSetting.of(asked.group(1).trim());
			if (setting != null) {
				clouds = setting;
			}
		}

		return new SkyElements(drawn.getOrDefault("sun", true), drawn.getOrDefault("moon", true),
				drawn.getOrDefault("stars", true), drawn.getOrDefault("sky", true), clouds);
	}

	
	public Weather weather(Map<String, String> defines) {
		boolean drawn = true;
		boolean particles = true;

		for (Matcher line : live(WEATHER, defines)) {
			// Split on one space, the first word being the rain and the second the splashes, and a
			// second word left out leaves the splashes alone. CoreVK splits this value the same way and
			// reads anything that is not "true" as false; this reads the four words the rest of the
			// file takes and leaves the piece as it was on anything else, which is the rule every
			// other boolean of this class already follows.
			String[] words = line.group(1).trim().split(" ", 0);
			Boolean rain = truth(words[0]);
			if (rain != null) {
				drawn = rain;
			}

			if (words.length > 1) {
				Boolean splashes = truth(words[1]);
				if (splashes != null) {
					particles = splashes;
				}
			}
		}

		return new Weather(drawn, particles);
	}

	
	public Optional<String> particleOrdering(Map<String, String> defines) {
		String ordering = null;

		for (Matcher before : live(PARTICLES_BEFORE_DEFERRED, defines)) {
			if (Boolean.TRUE.equals(truth(before.group(1).trim()))) {
				ordering = "before";
			}
		}

		for (Matcher line : live(PARTICLE_ORDERING, defines)) {
			String word = line.group(1).trim().toLowerCase(Locale.ROOT);
			if (!word.isEmpty()) {
				ordering = word;
			}
		}

		return Optional.ofNullable(ordering);
	}

	
	private List<Matcher> live(Pattern pattern, Map<String, String> defines) {
		List<Matcher> matched = new ArrayList<>();

		PropertiesFile.walk(this.lines, defines, line -> {
			Matcher match = pattern.matcher(line);
			if (match.matches()) {
				matched.add(match);
			}
		});

		return matched;
	}

	
	private static boolean reads(String[] words) {
		for (String word : words) {
			if (truth(word) != null) {
				return true;
			}
		}

		return false;
	}

	
	private static Boolean truth(String value) {
		if (value.equals("true") || value.equals("1")) {
			return Boolean.TRUE;
		}

		return (value.equals("false") || value.equals("0")) ? Boolean.FALSE : null;
	}

	
	public Set<String> imageSamplers(Map<String, String> defines) {
		Set<String> samplers = new LinkedHashSet<>();
		for (ImageInformation image : imageDirectives(defines).images()) {
			image.sampler().ifPresent(samplers::add);
		}

		return samplers;
	}

	
	public ImageInformation.Reading imageDirectives(Map<String, String> defines) {
		List<ImageInformation> images = new ArrayList<>();
		List<String> dropped = new ArrayList<>();

		PropertiesFile.walkDefining(this.lines, defines, (line, live) -> {
			Matcher image = IMAGE.matcher(line);
			if (!image.matches()) {
				return;
			}

			String name = image.group(1);
			String value = image.group(2).trim();
			if (images.size() >= ImageInformation.LIMIT) {
				dropped.add("image." + name + ": only " + ImageInformation.LIMIT
						+ " storage images are allowed");
				return;
			}

			String reason = ImageInformation.parse(name, value, live, images);
			if (reason != null) {
				dropped.add("image." + name + " = " + value + ": " + reason);
			}
		});

		return new ImageInformation.Reading(images, dropped);
	}

	
	public BufferObject.Reading bufferObjects(Map<String, String> defines) {
		Map<Integer, BufferObject> buffers = new LinkedHashMap<>();
		List<String> dropped = new ArrayList<>();

		PropertiesFile.walk(this.lines, defines, line -> {
			Matcher buffer = BUFFER_OBJECT.matcher(line);
			if (!buffer.matches()) {
				return;
			}

			String reason = BufferObject.parse(buffer.group(1), buffer.group(2).trim(), buffers);
			if (reason != null) {
				dropped.add("bufferObject." + buffer.group(1) + " = " + buffer.group(2).trim()
						+ ": " + reason);
			}
		});

		return new BufferObject.Reading(List.copyOf(buffers.values()), dropped);
	}

	
	public Map<String, OptionValue> expandProfile(String name) {
		return expandProfile(name, _ -> true);
	}

	
	public Map<String, OptionValue> expandProfile(String name, Predicate<String> known) {
		Map<String, OptionValue> chosen = new LinkedHashMap<>();
		expandProfile(name, chosen, 0, new int[1], known);

		return chosen;
	}

	
	private void expandProfile(String name, Map<String, OptionValue> chosen, int depth, int[] budget,
			Predicate<String> known) {
		String body = this.profiles.get(name);
		if (body == null || depth > MAX_PROFILE_DEPTH || ++budget[0] > MAX_PROFILE_EXPANSIONS) {
			return;
		}

		// A profile body is one of the lists, so it is split on a run of spaces like the others.
		for (String token : body.trim().split(" +", -1)) {
			if (token.isEmpty()) {
				continue;
			}

			if (token.startsWith("profile.")) {
				expandProfile(token.substring("profile.".length()), chosen, depth + 1, budget, known);
			} else if (token.startsWith("!")) {
				chosen.put(token.substring(1), OptionValue.off());
			} else {
				int equals = token.indexOf('=');
				if (equals >= 0) {
					chosen.put(token.substring(0, equals), OptionValue.of(token.substring(equals + 1)));
				} else if (known.test(token)) {
					chosen.put(token, OptionValue.on());
				}
			}
		}
	}

	public boolean present() {
		return this.present;
	}

	
	public boolean endFlashShadows(Map<String, String> defines) {
		boolean asked = false;
		for (Matcher line : live(END_FLASH_SHADOWS, defines)) {
			Boolean value = truth(line.group(1).trim());
			if (value != null) {
				asked = value;
			}
		}

		return asked;
	}

	
	public boolean rainDepth(Map<String, String> defines) {
		boolean asked = false;
		for (Matcher line : live(RAIN_DEPTH, defines)) {
			Boolean value = truth(line.group(1).trim());
			if (value != null) {
				asked = value;
			}
		}

		return asked;
	}

	
	public boolean dhShadow(Map<String, String> defines) {
		boolean asked = true;
		for (Matcher line : live(DH_SHADOW, defines)) {
			Boolean value = truth(line.group(1).trim());
			if (value != null) {
				asked = value;
			}
		}

		return asked;
	}

	
	public CloudSetting dhClouds(Map<String, String> defines) {
		CloudSetting asked = CloudSetting.DEFAULT;
		for (Matcher line : live(DH_CLOUDS, defines)) {
			CloudSetting setting = CloudSetting.ofDistant(line.group(1).trim());
			if (setting != null) {
				asked = setting;
			}
		}

		return asked;
	}

	
	public ShadowCasters shadowCasters(Map<String, String> defines) {
		boolean terrain = ShadowCasters.DEFAULT.terrain();
		boolean translucent = ShadowCasters.DEFAULT.translucent();
		boolean entities = ShadowCasters.DEFAULT.entities();
		boolean player = ShadowCasters.DEFAULT.player();
		boolean blockEntities = ShadowCasters.DEFAULT.blockEntities();
		boolean lightBlockEntities = ShadowCasters.DEFAULT.lightBlockEntities();

		for (Matcher line : live(SHADOW_CASTER, defines)) {
			Boolean value = truth(line.group(2).trim());
			if (value == null) {
				continue;
			}

			switch (line.group(1)) {
				case "shadowTerrain" -> terrain = value;
				case "shadowTranslucent" -> translucent = value;
				case "shadowEntities" -> entities = value;
				case "shadowPlayer" -> player = value;
				case "shadowBlockEntities" -> blockEntities = value;
				case "shadowLightBlockEntities" -> lightBlockEntities = value;
				// Named one by one rather than letting the last word be the catch-all: a seventh
				// alternative added to the pattern without its own arm would be written into the
				// sixth family in silence, and a caster read into the wrong family is a caster in a
				// map the pack asked to keep it out of.
				default -> { }
			}
		}

		return new ShadowCasters(terrain, translucent, entities, player, blockEntities,
				lightBlockEntities);
	}

	
	public ShadowCullState shadowCull(Map<String, String> defines) {
		ShadowCullState asked = ShadowCullState.DEFAULT;
		for (Matcher line : live(SHADOW_CULLING, defines)) {
			ShadowCullState state = ShadowCullState.of(line.group(1).trim());
			asked = state == null ? ShadowCullState.DEFAULT : state;
		}

		return asked;
	}

	
	public boolean separateAo(Map<String, String> defines) {
		Boolean asked = null;
		for (Matcher line : live(SEPARATE_AO, defines)) {
			asked = truth(line.group(1).trim());
		}

		return Boolean.TRUE.equals(asked);
	}

	
	public boolean breaksAnisotropy(Map<String, String> defines) {
		Boolean asked = null;
		for (Matcher line : live(BREAKS_ANISOTROPY, defines)) {
			asked = truth(line.group(1).trim());
		}

		return Boolean.TRUE.equals(asked);
	}

	
	public boolean oldHandLight(Map<String, String> defines) {
		Boolean asked = null;
		for (Matcher line : live(OLD_HAND_LIGHT, defines)) {
			asked = truth(line.group(1).trim());
		}

		return asked == null || asked;
	}

	
	public boolean oldLighting(Map<String, String> defines) {
		Boolean asked = null;
		for (Matcher line : live(OLD_LIGHTING, defines)) {
			asked = truth(line.group(1).trim());
		}

		return Boolean.TRUE.equals(asked);
	}

	
	public Map<String, String> profiles() {
		return this.profiles;
	}

	public Map<String, String> customUniformTypes() {
		return this.customUniformTypes;
	}

	public Set<String> screenTokens() {
		return this.screenTokens;
	}

	
	public Map<String, List<ScreenToken>> screenLayout() {
		return this.screenLayout;
	}

	
	public OptionalInt columns(String page) {
		Integer count = this.columns.get(page);

		return count == null ? OptionalInt.empty() : OptionalInt.of(count);
	}

	public List<String> sliders() {
		return this.sliders;
	}

	
	public List<String> requiredFeatures() {
		return this.requiredFeatures;
	}

	
	public boolean declares(String feature) {
		return names(this.requiredFeatures, feature) || names(this.optionalFeatures, feature);
	}

	private static boolean names(List<String> declared, String feature) {
		return declared.stream().anyMatch(name -> name.equalsIgnoreCase(feature));
	}

	
	public List<BlendDirective> blend(Map<String, String> defines) {
		List<BlendDirective> directives = new ArrayList<>();
		for (Matcher line : live(BLEND, defines)) {
			directives.add(new BlendDirective(line.group(1), line.group(2), line.group(3).trim()));
		}

		return directives;
	}

	
	public Map<String, String> sizeBuffers(Map<String, String> defines) {
		Map<String, String> sizes = new LinkedHashMap<>();
		for (Matcher line : live(SIZE_BUFFER, defines)) {
			sizes.put(line.group(1), line.group(2).trim());
		}

		return sizes;
	}

	
	public List<FlipDirective> flips(Map<String, String> defines) {
		List<FlipDirective> directives = new ArrayList<>();
		for (Matcher line : live(FLIP, defines)) {
			Boolean value = flipValue(line);
			if (value != null) {
				directives.add(new FlipDirective(line.group(1), line.group(2), value));
			}
		}

		return directives;
	}

	
	private static Boolean flipValue(Matcher flip) {
		return truth(flip.group(3).trim());
	}

	
	public Map<String, AlphaTest> alphaTests(Map<String, String> defines) {
		Map<String, AlphaTest> overrides = new LinkedHashMap<>();
		for (Matcher line : live(ALPHA_TEST, defines)) {
			AlphaTest.parse(line.group(2).trim())
					.ifPresent(test -> overrides.put(line.group(1), test));
		}

		return overrides;
	}

	
	public Optional<Boolean> shadowEnabled(Map<String, String> defines) {
		Boolean state = null;
		for (Matcher line : live(SHADOW_ENABLED, defines)) {
			Boolean value = truth(line.group(1).trim());
			if (value != null) {
				state = value;
			}
		}

		return Optional.ofNullable(state);
	}

	public int directiveCount() {
		return this.directiveCount;
	}

	
	public int blendCount() {
		return this.blendCount;
	}

	public int continuationCount() {
		return this.continuationCount;
	}

	public Map<String, Integer> ignoredPrefixes() {
		return new TreeMap<>(this.ignoredPrefixes);
	}

	
	public Optional<String> noiseTexturePath(Map<String, String> defines) {
		String path = null;
		for (Matcher line : live(TEXTURE_NOISE, defines)) {
			path = line.group(1).trim();
		}

		return Optional.ofNullable(path);
	}

	
	public sealed interface ScreenToken {

		
		record Blank() implements ScreenToken {
		}

		
		record Name(String name) implements ScreenToken {
		}

		
		record Link(String page) implements ScreenToken {
		}

		
		record Profiles() implements ScreenToken {
		}

		
		record Rest() implements ScreenToken {
		}
	}

	
	public record CustomUniform(boolean exposed, String type, String name, String expression) {
	}

	
	public record BlendDirective(String program, String buffer, String value) {

		public boolean off() {
			return this.value.equalsIgnoreCase("off");
		}
	}

	
	public record FlipDirective(String program, String buffer, boolean value) {
	}

	
	public record Weather(boolean drawn, boolean particles) {
	}

	
	public enum CloudSetting {

		
		DEFAULT,
		OFF,
		FAST,
		FANCY;

		
		static CloudSetting of(String value) {
			return switch (value.toLowerCase(Locale.ROOT)) {
				case "off" -> OFF;
				case "fast" -> FAST;
				case "fancy" -> FANCY;
				default -> null;
			};
		}

		
		static CloudSetting ofDistant(String value) {
			return switch (value.toLowerCase(Locale.ROOT)) {
				case "off" -> OFF;
				case "on", "fancy" -> FANCY;
				default -> null;
			};
		}
	}

	
	public record SkyElements(boolean sun, boolean moon, boolean stars, boolean sky,
			CloudSetting clouds) {

		
		public boolean allows(String directive) {
			return switch (directive) {
				case "sun" -> this.sun;
				case "moon" -> this.moon;
				case "stars" -> this.stars;
				case "sky" -> this.sky;
				default -> true;
			};
		}

	}

	private static final class Builder {

		private List<String> lines = List.of();
		private final Map<String, String> profiles = new LinkedHashMap<>();
		private final Map<String, String> customUniformTypes = new LinkedHashMap<>();
		private final Set<String> screenTokens = new LinkedHashSet<>();
		private final Map<String, List<ScreenToken>> screenLayout = new LinkedHashMap<>();
		private final Map<String, Integer> columns = new LinkedHashMap<>();
		private final List<String> sliders = new ArrayList<>();
		private List<String> requiredFeatures = List.of();
		private List<String> optionalFeatures = List.of();
		private final Map<String, Integer> ignoredPrefixes = new LinkedHashMap<>();
		private int directiveCount;
		private int blendCount;
		private int continuationCount;
		private boolean present;
	}
}
