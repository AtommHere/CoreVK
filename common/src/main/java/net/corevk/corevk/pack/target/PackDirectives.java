package net.corevk.corevk.pack.target;

import net.corevk.corevk.pack.model.TargetFormat;
import net.corevk.corevk.pack.option.SettingSet;
import net.corevk.corevk.pack.model.ProgramNames;
import net.corevk.corevk.pack.program.ProgramSet;
import net.corevk.corevk.pack.source.DimensionSet;
import net.corevk.corevk.pack.source.IncludeExpander;
import net.corevk.corevk.pack.source.ShaderPackSource;

import net.corevk.corevk.pack.source.IncludeExpander.ExpandedUnit;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

public final class PackDirectives {

	
	public static final String HIGHER_SHADOWCOLOR = "HIGHER_SHADOWCOLOR";

	
	public static final int MAX_SHADOW_COLOURS = 8;

	
	public static final int SHADOW_COLOURS = 2;

	
	public static int shadowColours(boolean declared) {
		return declared ? MAX_SHADOW_COLOURS : SHADOW_COLOURS;
	}

	private final float sunPathRotation;
	private final float ambientOcclusionLevel;
	private final float wetnessHalflife;
	private final float eyeBrightnessHalflife;
	private final float centerDepthHalflife;
	private final int noiseTextureResolution;
	private final int shadowMapResolution;
	private final float shadowDistance;
	private final float shadowDistanceRenderMul;
	private final boolean forcesShadowRenderDistance;
	private final float voxelDistance;
	private final float entityShadowDistanceMul;
	private final float shadowNearPlane;
	private final float shadowFarPlane;
	private final float shadowIntervalSize;
	private final Map<Integer, ShadowColour> shadowColours;
	private final List<ShadowDepth> shadowDepths;

	
	public record ShadowColour(TargetFormat.Resolution format, boolean clear,
			TargetDirectives.Colour clearColour, boolean declaresClearColour) {
	}

	
	public record ShadowDepth(boolean nearest, boolean mipmap) {
	}

	private PackDirectives(Builder builder) {
		this.sunPathRotation = builder.sunPathRotation;
		this.ambientOcclusionLevel = builder.ambientOcclusionLevel;
		this.wetnessHalflife = builder.wetnessHalflife;
		this.eyeBrightnessHalflife = builder.eyeBrightnessHalflife;
		this.centerDepthHalflife = builder.centerDepthHalflife;
		this.noiseTextureResolution = builder.noiseTextureResolution;
		this.shadowMapResolution = builder.shadowMapResolution;
		this.shadowDistance = builder.shadowDistance;
		this.shadowDistanceRenderMul = builder.shadowDistanceRenderMul;
		this.forcesShadowRenderDistance = builder.forcesShadowRenderDistance;
		this.voxelDistance = builder.voxelDistance;
		this.entityShadowDistanceMul = builder.entityShadowDistanceMul;
		this.shadowNearPlane = builder.shadowNearPlane;
		this.shadowFarPlane = builder.shadowFarPlane;
		this.shadowIntervalSize = builder.shadowIntervalSize;

		Map<Integer, ShadowColour> colours = new TreeMap<>();
		builder.shadowColours.forEach((index, setting) -> colours.put(index, setting.build()));
		this.shadowColours = Collections.unmodifiableMap(colours);
		this.shadowDepths = List.of(
				new ShadowDepth(builder.depthNearest[0], builder.depthMipmap[0]),
				new ShadowDepth(builder.depthNearest[1], builder.depthMipmap[1]));
	}

	
	public static PackDirectives defaults() {
		return builder().build();
	}

	public static Builder builder() {
		return new Builder();
	}

	
	public static PackDirectives read(ShaderPackSource source, SettingSet settings, String dimension)
			throws IOException {
		ProgramSet programs = ProgramSet.enumerate(source, DimensionSet.discover(source));
		boolean present = !dimension.equals(ProgramSet.ROOT)
				&& source.topLevelDirectories().contains(dimension);
		List<ProgramSet.ProgramKey> entries =
				programs.fragmentsOf(present ? dimension : ProgramSet.ROOT);

		IncludeExpander expander = new IncludeExpander(source, settings);
		Builder builder = builder();

		for (ProgramSet.ProgramKey key : ProgramSet.sorted(entries, ProgramNames::directiveRank)) {
			Optional<Path> file = source.file(key.file());
			if (file.isEmpty()) {
				continue;
			}

			try {
				builder.accept(expander.expand(file.get()));
			} catch (IOException | RuntimeException e) {
				// One unreadable composite must not cost the pack every other setting it declares.
			}
		}

		return builder.build();
	}

	public float sunPathRotation() {
		return this.sunPathRotation;
	}

	public float ambientOcclusionLevel() {
		return this.ambientOcclusionLevel;
	}

	
	public float wetnessHalflife() {
		return this.wetnessHalflife;
	}

	
	public float drynessHalflife() {
		return Builder.DRYNESS_HALFLIFE;
	}

	public float eyeBrightnessHalflife() {
		return this.eyeBrightnessHalflife;
	}

	public float centerDepthHalflife() {
		return this.centerDepthHalflife;
	}

	
	public int noiseTextureResolution() {
		return this.noiseTextureResolution;
	}

	
	public int shadowMapResolution() {
		return this.shadowMapResolution;
	}

	public float shadowDistance() {
		return this.shadowDistance;
	}

	
	public float shadowDistanceRenderMul() {
		return this.shadowDistanceRenderMul;
	}

	
	public boolean forcesShadowRenderDistance() {
		return this.forcesShadowRenderDistance && this.shadowDistanceRenderMul >= 0.0F;
	}

	
	public float voxelDistance() {
		return this.voxelDistance;
	}

	
	public float entityShadowDistanceMul() {
		return this.entityShadowDistanceMul;
	}

	public float shadowNearPlane() {
		return this.shadowNearPlane;
	}

	public float shadowFarPlane() {
		return this.shadowFarPlane;
	}

	public float shadowIntervalSize() {
		return this.shadowIntervalSize;
	}

	
	public ShadowColour shadowColour(int index) {
		ShadowColour declared = this.shadowColours.get(index);

		return declared == null ? Builder.SHADOW_COLOUR_DEFAULT : declared;
	}

	
	public Set<Integer> shadowColoursDeclared() {
		return this.shadowColours.keySet();
	}

	
	public ShadowDepth shadowDepth(int index) {
		return index >= 0 && index < this.shadowDepths.size()
				? this.shadowDepths.get(index)
				: new ShadowDepth(false, false);
	}

	
	public boolean shadowDepthNearest(int index) {
		return shadowDepth(index).nearest();
	}

	
	public boolean shadowDepthMipmap(int index) {
		return shadowDepth(index).mipmap();
	}

	public static final class Builder {

		private static final float DRYNESS_HALFLIFE = 200.0F;

		
		private static final int MAX_NOISE_RESOLUTION = 4096;

		private static final String SHADOW_COLOUR = "shadowcolor";

		
		private static final int SHADOW_DEPTHS = 2;

		private final boolean[] depthNearest = new boolean[SHADOW_DEPTHS];
		private final boolean[] depthMipmap = new boolean[SHADOW_DEPTHS];

		private static final ShadowColour SHADOW_COLOUR_DEFAULT = new ShadowColour(
				TargetFormat.defaultFormat(), true, new TargetDirectives.Colour(1.0F, 1.0F, 1.0F, 1.0F),
				false);

		private final Map<Integer, ShadowSetting> shadowColours = new TreeMap<>();

		private float sunPathRotation;
		private float ambientOcclusionLevel = 1.0F;
		private float wetnessHalflife = 600.0F;
		private float eyeBrightnessHalflife = 10.0F;
		private float centerDepthHalflife = 1.0F;
		private int noiseTextureResolution = 256;
		private int shadowMapResolution = 1024;
		private float shadowDistance = 160.0F;
		private float shadowDistanceRenderMul = -1.0F;
		private boolean forcesShadowRenderDistance;
		private float voxelDistance;
		private float entityShadowDistanceMul = 1.0F;
		private float shadowNearPlane = -100.05F;
		private float shadowFarPlane = 156.0F;
		private float shadowIntervalSize = 2.0F;

		private Builder() {
		}

		
		public Builder accept(ExpandedUnit unit) {
			for (ConstDirectives.Directive directive : ConstDirectives.read(unit)) {
				apply(directive);
			}

			return this;
		}

		public PackDirectives build() {
			return new PackDirectives(this);
		}

		private void apply(ConstDirectives.Directive directive) {
			switch (directive.name()) {
				case "sunPathRotation" -> asFloat(directive, value -> this.sunPathRotation = value);
				case "ambientOcclusionLevel" -> asFloat(directive,
						value -> this.ambientOcclusionLevel = Math.clamp(value, 0.0F, 1.0F));
				case "wetnessHalflife" -> asFloat(directive, value -> this.wetnessHalflife = value);
				// Not a typo and not ours. CoreVK registers the dryness directive against the rise as
				// well, so the two names are one setting and the last one read wins. Reproduced
				// rather than fixed, because correcting it would make our image diverge from the
				// reference the packs were tuned against.
				case "drynessHalflife" -> asFloat(directive, value -> this.wetnessHalflife = value);
				case "eyeBrightnessHalflife" -> asFloat(directive, value -> this.eyeBrightnessHalflife = value);
				case "centerDepthHalflife" -> asFloat(directive, value -> this.centerDepthHalflife = value);
				case "noiseTextureResolution" -> asInt(directive,
						value -> this.noiseTextureResolution = Math.clamp(value, 1, MAX_NOISE_RESOLUTION));
				case "shadowMapResolution" -> asInt(directive, value -> this.shadowMapResolution = value);
				case "shadowDistance" -> asFloat(directive, value -> this.shadowDistance = value);
				// The flag is raised inside the setter and not beside it, so that it follows the
				// value: a line whose type or number does not parse is dropped without a word here
				// as it is in CoreVK, and dropped means the pack never said anything at all.
				case "shadowDistanceRenderMul" -> asFloat(directive, value -> {
					this.shadowDistanceRenderMul = value;
					this.forcesShadowRenderDistance = true;
				});
				case "voxelDistance" -> asFloat(directive, value -> this.voxelDistance = value);
				case "entityShadowDistanceMul" -> asFloat(directive,
						value -> this.entityShadowDistanceMul = value);
				case "shadowNearPlane" -> asFloat(directive, value -> this.shadowNearPlane = value);
				case "shadowFarPlane" -> asFloat(directive, value -> this.shadowFarPlane = value);
				case "shadowIntervalSize" -> asFloat(directive, value -> this.shadowIntervalSize = value);
				// How the depth pair is read back. The family is folded in text order like everything
				// else here: CoreVK registers one consumer per name and then walks the declarations of
				// every fragment stage in order, so a per-image spelling wins over the blanket one
				// only by standing after it ({@code shaderpack/programs/ProgramSet.java:255-268}).
				//
				// shadowtexNearest is the older spelling of shadowtex0Nearest and has no shadowtex1
				// equivalent, which is CoreVK's shape as well: it hands the name to the first sampler
				// of the list ({@code shaderpack/properties/PackShadowDirectives.java:185-187}).
				case "shadowtexNearest", "shadowtex0Nearest", "shadow0MinMagNearest" ->
						asBool(directive, value -> this.depthNearest[0] = value);
				case "shadowtex1Nearest", "shadow1MinMagNearest" ->
						asBool(directive, value -> this.depthNearest[1] = value);
				// Whether the map carries a chain, folded the same way and with the same shape of
				// spellings: generateShadowMipmap covers both images at once where the per-image
				// names cover one, and shadowtexMipmap is the older spelling of shadowtex0Mipmap
				// with no shadowtex1 equivalent (PackShadowDirectives.java:147-168). The blanket
				// name wins or loses on where it stands and not on being the blanket, which is
				// CoreVK's own shape: it registers one consumer per name and walks the declarations
				// in text order.
				case "generateShadowMipmap" -> asBool(directive, value -> {
					this.depthMipmap[0] = value;
					this.depthMipmap[1] = value;
				});
				case "shadowtexMipmap", "shadowtex0Mipmap" ->
						asBool(directive, value -> this.depthMipmap[0] = value);
				case "shadowtex1Mipmap" -> asBool(directive, value -> this.depthMipmap[1] = value);
				default -> shadowColour(directive);
			}
		}

		
		private void shadowColour(ConstDirectives.Directive directive) {
			String name = directive.name();
			if (!name.startsWith(SHADOW_COLOUR)) {
				return;
			}

			int cursor = SHADOW_COLOUR.length();
			while (cursor < name.length() && Character.isDigit(name.charAt(cursor))) {
				cursor++;
			}

			int digits = cursor - SHADOW_COLOUR.length();
			if (digits < 1 || digits > 2 || cursor == name.length()) {
				return;
			}

			int index = Integer.parseInt(name.substring(SHADOW_COLOUR.length(), cursor));
			String suffix = name.substring(cursor);
			String type = directive.type();
			String value = directive.value().trim();

			// The declared type is checked on every one of them, as it is on the colour targets and
			// for the same reason: CoreVK ignores a directive written under the wrong type, so a pack
			// neither of us has seen has to be ignored here too.
			switch (suffix) {
				case "Format" -> {
					if (type.equals("int")) {
						setting(index).format = TargetFormat.resolve(value);
					}
				}
				case "Clear" -> {
					if (type.equals("bool") && (value.equals("true") || value.equals("false"))) {
						setting(index).clear = value.equals("true");
					}
				}
				case "ClearColor" -> {
					if (type.equals("vec4")) {
						TargetDirectives.parseColour(value)
								.ifPresent(colour -> setting(index).clearColour = colour);
					}
				}
				default -> {
				}
			}
		}

		private ShadowSetting setting(int index) {
			return this.shadowColours.computeIfAbsent(index, _ -> new ShadowSetting());
		}

		private static void asFloat(ConstDirectives.Directive directive, FloatSetter setter) {
			if (!directive.type().equals("float")) {
				return;
			}

			// A pack writes 2.0f as often as 2.0, and Float.parseFloat takes both; what it also
			// takes is a bare name, which is why the type is checked first and the failure is
			// silent afterwards.
			try {
				setter.set(Float.parseFloat(directive.value().trim().toLowerCase(Locale.ROOT)));
			} catch (NumberFormatException e) {
				// A directive left standing behind a setting nobody expanded. CoreVK drops it too.
			}
		}

		private static void asInt(ConstDirectives.Directive directive, IntSetter setter) {
			if (!directive.type().equals("int")) {
				return;
			}

			try {
				setter.set(Integer.parseInt(directive.value().trim()));
			} catch (NumberFormatException e) {
				// As above.
			}
		}

		
		private static void asBool(ConstDirectives.Directive directive, BooleanSetter setter) {
			if (!directive.type().equals("bool")) {
				return;
			}

			String value = directive.value().trim();
			if (value.equals("true") || value.equals("false")) {
				setter.set(value.equals("true"));
			}
		}

		
		private static final class ShadowSetting {

			private TargetFormat.Resolution format;
			private Boolean clear;
			private TargetDirectives.Colour clearColour;

			private ShadowColour build() {
				return new ShadowColour(
						this.format == null ? SHADOW_COLOUR_DEFAULT.format() : this.format,
						this.clear == null ? SHADOW_COLOUR_DEFAULT.clear() : this.clear,
						this.clearColour == null ? SHADOW_COLOUR_DEFAULT.clearColour() : this.clearColour,
						this.clearColour != null);
			}
		}

		@FunctionalInterface
		private interface FloatSetter {
			void set(float value);
		}

		@FunctionalInterface
		private interface BooleanSetter {
			void set(boolean value);
		}

		@FunctionalInterface
		private interface IntSetter {
			void set(int value);
		}
	}
}
