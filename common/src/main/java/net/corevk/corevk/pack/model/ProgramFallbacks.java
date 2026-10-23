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

		parents.put("shadow", null);
		parents.put("shadow_solid", "shadow");
		parents.put("shadow_cutout", "shadow");
		parents.put("shadow_water", "shadow");
		parents.put("shadow_entities", "shadow");
		parents.put("shadow_lightning", "shadow_entities");
		parents.put("shadow_block", "shadow");

		parents.put("gbuffers_basic", null);
		parents.put("gbuffers_line", "gbuffers_basic");
		parents.put("gbuffers_textured", "gbuffers_basic");
		parents.put("gbuffers_textured_lit", "gbuffers_textured");
		parents.put("gbuffers_skybasic", "gbuffers_basic");
		parents.put("gbuffers_skytextured", "gbuffers_textured");

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

		parents.put("dh_terrain", null);
		parents.put("dh_water", "dh_terrain");
		parents.put("dh_generic", "dh_terrain");
		parents.put("dh_shadow", null);

		parents.put("final", null);

		return Collections.unmodifiableMap(parents);
	}

	private static Map<String, BlendMode> buildBlendOverrides() {
		Map<String, BlendMode> overrides = new LinkedHashMap<>();

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
