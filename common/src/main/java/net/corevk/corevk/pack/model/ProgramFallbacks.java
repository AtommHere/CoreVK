package net.corevk.corevk.pack.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ProgramFallbacks {

	private static final Map<String, String> PARENTS = buildParents();

	private static final Map<String, BlendMode> BLEND_OVERRIDES = buildBlendOverrides();

	private ProgramFallbacks() {
	}

	private static Map<String, String> buildParents() {
		Map<String, String> parents = new LinkedHashMap<>();

		// The shadow family. Everything falls back to the one shadow program, except lightning,
		// which is an entity first.
		parents.put("shadow", null);
		parents.put("shadow_solid", "shadow");
		parents.put("shadow_cutout", "shadow");
		parents.put("shadow_water", "shadow");
		parents.put("shadow_entities", "shadow");
		parents.put("shadow_lightning", "shadow_entities");
		parents.put("shadow_block", "shadow");

		// The gbuffers family. Two roots: basic for untextured geometry, and textured_lit for
		// everything that is lit by the world.
		parents.put("gbuffers_basic", null);
		parents.put("gbuffers_line", "gbuffers_basic");
		parents.put("gbuffers_textured", "gbuffers_basic");
		parents.put("gbuffers_textured_lit", "gbuffers_textured");
		parents.put("gbuffers_skybasic", "gbuffers_basic");
		parents.put("gbuffers_skytextured", "gbuffers_textured");
		// The one edge the two authorities disagree on: CoreVK sends clouds to textured, while
		// OptiFine's own documentation gives them no fallback at all. CoreVK wins here because it
		// is the implementation packs are actually written against, but a pack shipping no
		// cloud program is the case to look at first if clouds ever come out wrong.
		parents.put("gbuffers_clouds", "gbuffers_textured");
		parents.put("gbuffers_terrain", "gbuffers_textured_lit");
		parents.put("gbuffers_terrain_solid", "gbuffers_terrain");
		parents.put("gbuffers_terrain_cutout", "gbuffers_terrain");
		parents.put("gbuffers_damagedblock", "gbuffers_terrain");
		parents.put("gbuffers_block", "gbuffers_terrain");
		parents.put("gbuffers_block_translucent", "gbuffers_block");
		parents.put("gbuffers_beaconbeam", "gbuffers_textured");
		parents.put("gbuffers_item", "gbuffers_textured_lit");
		parents.put("gbuffers_entities", "gbuffers_textured_lit");
		parents.put("gbuffers_entities_translucent", "gbuffers_entities");
		parents.put("gbuffers_entities_glowing", "gbuffers_entities");
		parents.put("gbuffers_lightning", "gbuffers_entities");
		parents.put("gbuffers_particles", "gbuffers_textured_lit");
		parents.put("gbuffers_particles_translucent", "gbuffers_particles");
		parents.put("gbuffers_armor_glint", "gbuffers_textured");
		parents.put("gbuffers_spidereyes", "gbuffers_textured");
		parents.put("gbuffers_hand", "gbuffers_textured_lit");
		parents.put("gbuffers_weather", "gbuffers_textured_lit");
		parents.put("gbuffers_water", "gbuffers_terrain");
		parents.put("gbuffers_hand_water", "gbuffers_hand");

		// Distant Horizons. Its shadow program stands alone rather than joining the shadow
		// family above.
		parents.put("dh_terrain", null);
		parents.put("dh_water", "dh_terrain");
		parents.put("dh_generic", "dh_terrain");
		parents.put("dh_shadow", null);

		// Composition has no fallback at all. A missing composite pass is simply a pass that
		// does not run, which is a normal thing for a pack to want.
		parents.put("final", null);

		// Not Map.copyOf: the roots of the tree map to null, and the immutable factories reject
		// a null value rather than storing it.
		return Collections.unmodifiableMap(parents);
	}

	
	private static Map<String, BlendMode> buildBlendOverrides() {
		Map<String, BlendMode> overrides = new LinkedHashMap<>();

		// Additive, and it is what makes an eye a light rather than a decal: the source is weighted by
		// its own alpha and the destination is kept whole, so the pupil adds to the mob's skin instead
		// of replacing it. The game's own pipeline blends the ordinary translucent way, which is the
		// value this one displaces; alpha keeps ZERO, ONE for the same reason CoreVK gives it that.
		overrides.put("gbuffers_spidereyes", new BlendMode(false, "SRC_ALPHA", "ONE", "ZERO", "ONE"));

		return Collections.unmodifiableMap(overrides);
	}

	
	public static BlendMode blendOverride(String program) {
		return BLEND_OVERRIDES.get(program);
	}

	
	public static Set<String> names() {
		return PARENTS.keySet();
	}

	
	public static List<String> chain(String program) {
		List<String> chain = new ArrayList<>();
		String current = program;

		while (current != null && !chain.contains(current)) {
			chain.add(current);
			current = PARENTS.get(current);
		}

		return List.copyOf(chain);
	}
}
