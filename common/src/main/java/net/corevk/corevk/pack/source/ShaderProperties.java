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

	private static final Pattern CONTINUATION = Pattern.compile("\\\\\\r?\\n[ \\t]*");
	private static final Pattern DIRECTIVE = Pattern.compile("^\\s*#\\s*(if|ifdef|ifndef|else|elif|endif)\\b.*$");

	private static final Pattern PROFILE = Pattern.compile("^\\s*profile\\.(\\w+)\\s*=\\s*(.*)$");
	private static final Pattern PROGRAM_ENABLED = Pattern.compile("^\\s*program\\.(.+?)\\.enabled\\s*=\\s*(.*)$");
	private static final Pattern CUSTOM_UNIFORM =
			Pattern.compile("^\\s*(uniform|variable)\\.(\\w+)\\.(\\w+)\\s*=\\s*(.*)$");
	private static final Pattern SCREEN = Pattern.compile("^\\s*screen(\\.\\w+)?\\s*=\\s*(.*)$");

	private static final Pattern MAIN_COLUMNS = Pattern.compile("^\\s*screen\\.columns\\s*=\\s*(\\d+)\\s*$");
	private static final Pattern PAGE_COLUMNS = Pattern.compile("^\\s*screen\\.(\\w+)\\.columns\\s*=\\s*(\\d+)\\s*$");
	private static final Pattern BLEND = Pattern.compile("^\\s*blend\\.([^=\\s.]+)(?:\\.(\\w+))?\\s*=\\s*(.*)$");
	private static final Pattern ALPHA_TEST = Pattern.compile("^\\s*alphaTest\\.(\\S+)\\s*=\\s*(.*)$");
	private static final Pattern SLIDERS = Pattern.compile("^\\s*sliders\\s*=\\s*(.*)$");

	private static final Pattern COREVK_FEATURES =
			Pattern.compile("^\\s*corevk\\.features\\.(required|optional)\\s*=\\s*(.*)$");
	private static final Pattern END_FLASH_SHADOWS = Pattern.compile("^\\s*endFlashShadows\\s*=\\s*(.*)$");

	private static final Pattern RAIN_DEPTH = Pattern.compile("^\\s*rain\\.depth\\s*=\\s*(.*)$");

	private static final Pattern SHADOW_CASTER = Pattern.compile(
			"^\\s*(shadowTerrain|shadowTranslucent|shadowEntities|shadowPlayer|shadowBlockEntities"
					+ "|shadowLightBlockEntities)\\s*=\\s*(.*)$");

	private static final Pattern SHADOW_CULLING = Pattern.compile("^\\s*shadow\\.culling\\s*=\\s*(.*)$");

	private static final Pattern DH_SHADOW = Pattern.compile("^\\s*dhShadow\\.enabled\\s*=\\s*(.*)$");

	private static final Pattern SEPARATE_AO = Pattern.compile("^\\s*separateAo\\s*=\\s*(.*)$");

	private static final Pattern BREAKS_ANISOTROPY =
			Pattern.compile("^\\s*breaksAnisotropy\\s*=\\s*(.*)$");

	private static final Pattern OLD_HAND_LIGHT = Pattern.compile("^\\s*oldHandLight\\s*=\\s*(.*)$");

	private static final Pattern OLD_LIGHTING = Pattern.compile("^\\s*oldLighting\\s*=\\s*(.*)$");
	private static final Pattern SIZE_BUFFER = Pattern.compile("^\\s*size\\.buffer\\.([^=\\s.]+)\\s*=\\s*(.*)$");
	private static final Pattern SKY_ELEMENT = Pattern.compile("^\\s*(sun|moon|stars|sky)\\s*=\\s*(.*)$");

	private static final Pattern CLOUDS = Pattern.compile("^\\s*clouds\\s*=\\s*(.*)$");

	private static final Pattern DH_CLOUDS = Pattern.compile("^\\s*dhClouds\\s*=\\s*(.*)$");

	private static final Pattern WEATHER = Pattern.compile("^\\s*weather\\s*=\\s*(.*)$");

	private static final Pattern PARTICLE_ORDERING =
			Pattern.compile("^\\s*particles\\.ordering\\s*=\\s*(.*)$");
	private static final Pattern PARTICLES_BEFORE_DEFERRED =
			Pattern.compile("^\\s*particles\\.before\\.deferred\\s*=\\s*(.*)$");

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

		this.profiles = Collections.unmodifiableMap(new LinkedHashMap<>(builder.profiles));
		this.customUniformTypes = Map.copyOf(builder.customUniformTypes);
		this.screenTokens = Set.copyOf(builder.screenTokens);

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

		for (String line : builder.lines) {
			if (DIRECTIVE.matcher(line).matches()) {
				builder.directiveCount++;
			}
		}

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

			String page = screen.group(1) == null ? "" : screen.group(1).substring(1);

			List<ScreenToken> slots = new ArrayList<>();
			builder.screenLayout.put(page, slots);

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

		if (BLEND.matcher(line).matches()) {
			builder.blendCount++;
			return;
		}

		if (ALPHA_TEST.matcher(line).matches()) {
			return;
		}

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

		if (END_FLASH_SHADOWS.matcher(line).matches()) {
			return;
		}

		Matcher rainDepth = RAIN_DEPTH.matcher(line);
		if (rainDepth.matches() && truth(rainDepth.group(1).trim()) != null) {
			return;
		}

		Matcher caster = SHADOW_CASTER.matcher(line);
		if (caster.matches() && truth(caster.group(2).trim()) != null) {
			return;
		}

		Matcher dhShadow = DH_SHADOW.matcher(line);
		if (dhShadow.matches() && truth(dhShadow.group(1).trim()) != null) {
			return;
		}

		Matcher culling = SHADOW_CULLING.matcher(line);
		if (culling.matches()) {
			return;
		}

		Matcher separateAo = SEPARATE_AO.matcher(line);
		if (separateAo.matches()) {
			return;
		}

		if (BREAKS_ANISOTROPY.matcher(line).matches()) {
			return;
		}

		if (OLD_HAND_LIGHT.matcher(line).matches()) {
			return;
		}

		if (OLD_LIGHTING.matcher(line).matches()) {
			return;
		}

		if (SIZE_BUFFER.matcher(line).matches()) {
			return;
		}

		Matcher element = SKY_ELEMENT.matcher(line);
		if (element.matches() && truth(element.group(2).trim()) != null) {
			return;
		}

		Matcher clouds = CLOUDS.matcher(line);
		if (clouds.matches() && CloudSetting.of(clouds.group(1).trim()) != null) {
			return;
		}

		Matcher dhClouds = DH_CLOUDS.matcher(line);
		if (dhClouds.matches() && CloudSetting.ofDistant(dhClouds.group(1).trim()) != null) {
			return;
		}

		Matcher weather = WEATHER.matcher(line);
		if (weather.matches() && reads(weather.group(1).trim().split(" ", 0))) {
			return;
		}

		Matcher ordering = PARTICLE_ORDERING.matcher(line);
		if (ordering.matches() && !ordering.group(1).trim().isEmpty()) {
			return;
		}

		Matcher before = PARTICLES_BEFORE_DEFERRED.matcher(line);
		if (before.matches() && Boolean.TRUE.equals(truth(before.group(1).trim()))) {
			return;
		}

		Matcher flip = FLIP.matcher(line);
		if (flip.matches() && flipValue(flip) != null) {
			return;
		}

		Matcher noise = TEXTURE_NOISE.matcher(line);
		if (noise.matches()) {
			return;
		}

		Matcher shadowEnabled = SHADOW_ENABLED.matcher(line);
		if (shadowEnabled.matches() && truth(shadowEnabled.group(1).trim()) != null) {
			return;
		}

		if (CUSTOM_TEXTURE.matcher(line).matches()) {
			return;
		}

		if (IMAGE.matcher(line).matches()) {
			return;
		}

		if (BUFFER_OBJECT.matcher(line).matches()) {
			return;
		}

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
