package net.corevk.corevk.pack.target;

import net.corevk.corevk.pack.model.TargetName;
import net.corevk.corevk.pack.model.ProgramNames;
import net.corevk.corevk.pack.texture.CustomImages;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class SamplerPlan {

	private static final Set<String> DEPTH = Set.of("depthtex0", "depthtex1", "depthtex2", "gdepthtex");

	
	private static final Set<String> SHADOW_DEPTH = Set.of("shadowtex0", "shadowtex1", "shadow",
			"watershadow", "shadowtex0HW", "shadowtex1HW");

	
	private static final Set<String> DISTANT_DEPTH =
			Set.of("dhDepthTex", "dhDepthTex0", "dhDepthTex1");
	private static final String SHADOW_COLOUR_PREFIX = "shadowcolor";
	private static final String NOISE = "noisetex";
	private static final String WATER_SHADOW = "watershadow";

	
	private static final String FORGED = "ofPackTexture_";

	
	private static final String CENTER_DEPTH = "ofCenterDepthSmooth";

	
	private static final int MAX_SHADOW_COLOURS = PackDirectives.MAX_SHADOW_COLOURS;

	
	public static final int DEFAULT_TARGET = 0;

	
	private static final String FLAT = "sampler2D";

	private final List<Binding> bindings;
	private final Map<String, Binding> byName;
	private final boolean waterShadow;

	private SamplerPlan(List<Binding> bindings) {
		this.bindings = List.copyOf(bindings);

		Map<String, Binding> byName = new LinkedHashMap<>();
		bindings.forEach(binding -> byName.putIfAbsent(binding.sampler(), binding));
		this.byName = Map.copyOf(byName);
		this.waterShadow = byName.containsKey(WATER_SHADOW);
	}

	
	public enum Kind {
		COLORTEX, DEPTH, SHADOW_DEPTH, SHADOW_COLOUR, NOISE, PACK_TEXTURE, CENTER_DEPTH,
		DISTANT_DEPTH, CUSTOM_IMAGE, COLOUR_IMAGE, UNSERVED, UNBINDABLE
	}

	
	public record Binding(String sampler, Kind kind, int index, TargetSchedule.Side side,
			boolean defaulted) {
	}

	
	public static Kind classify(String name, String type) {
		return classify(name, type, Set.of());
	}

	
	public static Kind classify(String name, String type, Set<String> supplied) {
		return classify(name, type, supplied, CustomImages.names());
	}

	
	public static Kind classify(String name, String type, Set<String> supplied, Set<String> images) {
		if (images.contains(CustomImages.originalName(name))) {
			return Kind.CUSTOM_IMAGE;
		}

		if (type != null && SamplerTypes.refused(type)) {
			return Kind.UNBINDABLE;
		}

		return supplied.contains(name) ? Kind.PACK_TEXTURE : classify(name);
	}

	
	public static boolean depthCopy(String name) {
		return name.equals("depthtex1") || name.equals("depthtex2");
	}

	
	public static boolean preHandCopy(String name) {
		return name.equals("depthtex2");
	}

	
	public static boolean distantWithoutWater(String name) {
		return name.equals("dhDepthTex1");
	}

	
	public static String forged(String sampler) {
		return FORGED + sampler;
	}

	
	public static String centerDepth() {
		return CENTER_DEPTH;
	}

	
	public static String behind(String sampler) {
		return sampler.startsWith(FORGED) ? sampler.substring(FORGED.length()) : sampler;
	}

	public static Kind classify(String name) {
		// First, because it is the only answer that can be right: nothing but the translation
		// produces this prefix, and it only produces it for a name it has already moved.
		if (name.startsWith(FORGED)) {
			return Kind.PACK_TEXTURE;
		}

		// And for the same reason: only the translation writes this name, and only where it has taken
		// centerDepthSmooth off its declaration.
		if (name.equals(CENTER_DEPTH)) {
			return Kind.CENTER_DEPTH;
		}

		if (TargetName.index(name).isPresent()) {
			return Kind.COLORTEX;
		}

		if (TargetName.imageIndex(name).isPresent()) {
			return Kind.COLOUR_IMAGE;
		}

		if (DEPTH.contains(name)) {
			return Kind.DEPTH;
		}

		if (SHADOW_DEPTH.contains(name)) {
			return Kind.SHADOW_DEPTH;
		}

		if (DISTANT_DEPTH.contains(name)) {
			return Kind.DISTANT_DEPTH;
		}

		if (isShadowColour(name)) {
			return Kind.SHADOW_COLOUR;
		}

		return name.equals(NOISE) ? Kind.NOISE : Kind.UNSERVED;
	}

	
	public static SamplerPlan of(List<String> declared, TargetPlan plan, String program) {
		return of(declared, Map.of(), plan, program);
	}

	
	public static SamplerPlan of(List<String> declared, Map<String, String> types, TargetPlan plan,
			String program) {
		return of(declared, types, plan, program, Set.of(), Set.of());
	}

	
	public static SamplerPlan of(List<String> declared, Map<String, String> types, TargetPlan plan,
			String program, Set<String> supplied) {
		return of(declared, types, plan, program, supplied, Set.of());
	}

	
	public static SamplerPlan of(List<String> declared, Map<String, String> types, TargetPlan plan,
			String program, Set<String> supplied, Set<String> pictures) {
		// The picture is narrowed the way the declared override is, and by the same walk: CoreVK
		// deactivates an override once its target has been flipped, whichever road reads it
		// (gl/program/ProgramSamplers.java:258).
		return of(declared, types, plan, plan.schedule().step(program),
				standing(plan, program, supplied),
				byDefault(standing(plan, program, pictures)), fullscreen(program));
	}

	
	public static SamplerPlan of(List<String> declared, Map<String, String> types, TargetPlan plan,
			Optional<TargetSchedule.Bound> step) {
		return of(declared, types, plan, step, Set.of());
	}

	
	public static SamplerPlan of(List<String> declared, Map<String, String> types, TargetPlan plan,
			Optional<TargetSchedule.Bound> step, Set<String> supplied) {
		return of(declared, types, plan, step, supplied, Kind.UNSERVED, false);
	}

	
	private static SamplerPlan of(List<String> declared, Map<String, String> types, TargetPlan plan,
			Optional<TargetSchedule.Bound> step, Set<String> supplied, Kind stands,
			boolean fullscreen) {
		List<Binding> bindings = new ArrayList<>();
		Kind byDefault = fullscreen ? stands : Kind.UNSERVED;
		Set<String> images = CustomImages.names();

		for (String name : declared) {
			String type = types.get(name);
			Kind kind = classify(name, type, supplied, images);
			if (fullscreen && takesDefault(name, type, supplied, images)) {
				// Through the same allocation test the target's own name goes through below, and
				// for the same reason: a plan that decides everything before a frame has nothing to
				// bind for an index nothing allocated. TargetPlan allocates it wherever a full
				// screen program of the place declares a name that lands here, which is where CoreVK
				// creates it on demand (samplers/CoreVKSamplers.java:60-62 and
				// targets/RenderTargets.java:118), so the target is there whenever a pack asks for
				// it; the name falls back to the black texel where it is not, on the pass road and
				// on the compute road both, rather than the plan handing out an index the binding
				// then has to refuse.
				if (byDefault == Kind.COLORTEX && plan.allocated().contains(DEFAULT_TARGET)) {
					bindings.add(new Binding(name, Kind.COLORTEX, DEFAULT_TARGET,
							side(step, DEFAULT_TARGET), true));
					continue;
				}

				if (byDefault == Kind.PACK_TEXTURE) {
					bindings.add(new Binding(name, Kind.PACK_TEXTURE, -1,
							TargetSchedule.Side.MAIN, true));
					continue;
				}
			}

			if (kind == Kind.SHADOW_COLOUR) {
				bindings.add(new Binding(name, kind, shadowColour(name), TargetSchedule.Side.MAIN,
						false));
				continue;
			}

			if (kind != Kind.COLORTEX && kind != Kind.COLOUR_IMAGE) {
				bindings.add(new Binding(name, kind, -1, TargetSchedule.Side.MAIN, false));
				continue;
			}

			int index = (kind == Kind.COLORTEX ? TargetName.index(name) : TargetName.imageIndex(name))
					.orElse(-1);

			// A target no program of this dimension writes or samples was never allocated, so
			// there is nothing to bind. Saying so by name is the whole difference between a gap
			// and a wrong image.
			if (!plan.allocated().contains(index)) {
				bindings.add(new Binding(name, Kind.UNSERVED, -1, TargetSchedule.Side.MAIN, false));
				continue;
			}

			bindings.add(new Binding(name, kind, index, side(step, index), false));
		}

		return new SamplerPlan(bindings);
	}

	
	private static TargetSchedule.Side side(Optional<TargetSchedule.Bound> step, int index) {
		return step.map(bound -> bound.read(index)).orElse(TargetSchedule.Side.MAIN);
	}

	
	private static boolean flat(String type) {
		return FLAT.equals(type);
	}

	
	public static boolean takesDefault(String name, String type, Set<String> supplied,
			Set<String> images) {
		return classify(name, type, supplied, images) == Kind.UNSERVED && flat(type);
	}

	
	public static Kind byDefault(Set<String> pictures) {
		return pictures.contains(TargetName.canonical(DEFAULT_TARGET))
				? Kind.PACK_TEXTURE
				: Kind.COLORTEX;
	}

	
	public static boolean fullscreen(String program) {
		return stageOf(TargetName.bareName(program)) != null;
	}

	
	private static Set<String> standing(TargetPlan plan, String program, Set<String> supplied) {
		String bare = TargetName.bareName(program);
		String stage = stageOf(bare);
		if (supplied.isEmpty() || stage == null) {
			return supplied;
		}

		// The pass a compute hangs off, because a compute is in no running order: composite1_a is
		// not a pass and a walk looking for its own name would run to the end of the stage, count
		// the writes of every pass AFTER it, and drop an override the pass beside it keeps. CoreVK
		// snapshots what has been flipped once it reaches the index and hands that same snapshot to
		// the pass (CompositeRenderer.java:134 taken, :151 given it), to the computes hanging off
		// it (:154) and to a compute whose program draws nothing (:141), so all of them stop here.
		String upTo = ProgramNames.computeBase(bare).orElse(bare);

		// Stopped where the name FALLS in the frame rather than where it is found, because the
		// program a compute hangs off may draw nothing and be in no running order either: Pegasus
		// ships prepare_a and no prepare.fsh, and a walk looking for that name would again run to
		// the end of the stage. This is the moment the chain dispatches such a compute at
		// (PackChain.standaloneOf places it before the first pass that does not sort ahead of it),
		// and the two have to be one answer.
		Set<String> abandoned = new LinkedHashSet<>();
		for (String earlier : plan.running()) {
			if (!ProgramNames.before(earlier, upTo)) {
				break;
			}

			if (stage.equals(stageOf(earlier))) {
				plan.writes(earlier).forEach(index -> {
					abandoned.add(TargetName.canonical(index));
					TargetName.legacyAlias(index).ifPresent(abandoned::add);
				});
			}
		}

		if (abandoned.isEmpty()) {
			return supplied;
		}

		Set<String> left = new LinkedHashSet<>(supplied);
		left.removeAll(abandoned);

		return left;
	}

	
	private static String stageOf(String program) {
		String family = ProgramNames.familyOf(program);

		return switch (family) {
			case "begin", "prepare", "deferred", "composite" -> family;
			case "final" -> "composite";
			default -> null;
		};
	}

	
	public List<Binding> bindings() {
		return this.bindings;
	}

	
	public Binding binding(String sampler) {
		Binding found = this.byName.get(sampler);

		return found == null
				? new Binding(sampler, Kind.UNSERVED, -1, TargetSchedule.Side.MAIN, false)
				: found;
	}

	public Map<Kind, List<String>> byKind() {
		Map<Kind, List<String>> grouped = new EnumMap<>(Kind.class);
		for (Binding binding : this.bindings) {
			grouped.computeIfAbsent(binding.kind(), _ -> new ArrayList<>()).add(binding.sampler());
		}

		grouped.replaceAll((_, names) -> List.copyOf(names));

		return grouped;
	}

	public List<String> unserved() {
		return named(Kind.UNSERVED);
	}

	
	public List<String> defaulted() {
		return this.bindings.stream()
				.filter(Binding::defaulted)
				.map(Binding::sampler)
				.toList();
	}

	
	public List<String> unbindable() {
		return named(Kind.UNBINDABLE);
	}

	private List<String> named(Kind kind) {
		return this.bindings.stream()
				.filter(binding -> binding.kind() == kind)
				.map(Binding::sampler)
				.toList();
	}

	
	public boolean withoutTranslucents(String name) {
		return withoutTranslucents(name, this.waterShadow);
	}

	
	public static boolean withoutTranslucents(String name, boolean waterShadow) {
		return name.equals("shadowtex1") || name.equals("shadowtex1HW")
				|| (waterShadow && name.equals("shadow"));
	}

	
	public static int shadowColour(String name) {
		return name.equals(SHADOW_COLOUR_PREFIX)
				? 0
				: name.charAt(SHADOW_COLOUR_PREFIX.length()) - '0';
	}

	
	public static boolean isShadowColour(String name) {
		if (name.equals(SHADOW_COLOUR_PREFIX)) {
			return true;
		}

		if (!name.startsWith(SHADOW_COLOUR_PREFIX)) {
			return false;
		}

		String digits = name.substring(SHADOW_COLOUR_PREFIX.length());
		if (digits.length() != 1 || !Character.isDigit(digits.charAt(0))) {
			return false;
		}

		return digits.charAt(0) - '0' < MAX_SHADOW_COLOURS;
	}
}
