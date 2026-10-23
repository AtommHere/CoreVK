package net.corevk.corevk.render;

import net.corevk.corevk.pack.option.OptionValue;
import net.corevk.corevk.pack.program.ChainFilter;
import net.corevk.corevk.pack.target.ChainPlan;
import net.corevk.corevk.settings.PackSession;
import net.corevk.corevk.settings.SettingsFile;
import net.corevk.corevk.settings.SettingsLayers;
import net.corevk.corevk.CoreVK;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class EngineOptions {

	
	private static final String SEED_KEY = "seed";

	
	private static final String PASSES_KEY = "passes";

	
	private static final String SCREEN_KEY = "screen";

	
	private static final String ON_PACKS = "packs";
	private static final String ON_SETTINGS = "settings";

	
	static final String DUMP_KEY = "dump";

	
	private static final String TERRAIN_KEY = "terrain";

	
	private static final String SHADOW_KEY = "shadow";

	
	private static final String CHAIN_KEY = "chain";

	
	private static final String SKY_KEY = "sky";

	
	private static final String ENTITIES_KEY = "entities";

	
	private static final String HAND_KEY = "hand";

	
	private static final String CLOUDS_KEY = "clouds";

	
	private static final String WEATHER_KEY = "weather";

	
	private static final String PARTICLES_KEY = "particles";

	
	private static final Set<String> RESERVED = Set.of(SettingsFile.PROFILE_KEY, SEED_KEY,
			PASSES_KEY, SCREEN_KEY, DUMP_KEY, TERRAIN_KEY, CHAIN_KEY, SHADOW_KEY, SKY_KEY,
			ENTITIES_KEY, HAND_KEY, CLOUDS_KEY, WEATHER_KEY, PARTICLES_KEY);

	private EngineOptions() {
	}

	
	record Read(boolean seed, ChainFilter passes, boolean packsFirst, String dump, boolean terrain,
			boolean chain, boolean shadow, boolean sky, boolean entities, boolean hand,
			boolean clouds, boolean weather, boolean particles) {

		
		ChainPlan.Families families() {
			return new ChainPlan.Families(this.terrain, this.entities, this.particles, this.seed);
		}
	}

	
	static Read take(Map<String, OptionValue> chosen) {
		// The seed goes through the same reading as the other nine rather than keeping one of its
		// own. It had one, and it was the only line here that took an unreadable word in silence:
		// seed=0 left the seed drawing, and an experiment run to see the clears on their own would
		// have concluded from a picture the seed had painted.
		return new Read(asked(chosen.remove(SEED_KEY), SEED_KEY, true),
				filterOf(chosen.remove(PASSES_KEY)),
				packsFirst(chosen.remove(SCREEN_KEY)),
				named(chosen.remove(DUMP_KEY)),
				asked(chosen.remove(TERRAIN_KEY), TERRAIN_KEY, true),
				asked(chosen.remove(CHAIN_KEY), CHAIN_KEY, true),
				asked(chosen.remove(SHADOW_KEY), SHADOW_KEY, true),
				asked(chosen.remove(SKY_KEY), SKY_KEY, true),
				asked(chosen.remove(ENTITIES_KEY), ENTITIES_KEY, true),
				asked(chosen.remove(HAND_KEY), HAND_KEY, true),
				asked(chosen.remove(CLOUDS_KEY), CLOUDS_KEY, true),
				asked(chosen.remove(WEATHER_KEY), WEATHER_KEY, true),
				asked(chosen.remove(PARTICLES_KEY), PARTICLES_KEY, true));
	}

	
	static void announceForced(Path gameDirectory, PackSession opened) {
		Path file = SettingsLayers.file(gameDirectory);
		List<String> settings = opened.forced().keySet().stream()
				.filter(name -> !RESERVED.contains(name) && opened.declared().contains(name))
				.toList();
		List<String> reserved = opened.forced().keySet().stream()
				.filter(RESERVED::contains)
				.toList();
		List<String> unknown = opened.forced().keySet().stream()
				.filter(name -> !RESERVED.contains(name) && !opened.declared().contains(name))
				.toList();

		if (!settings.isEmpty()) {
			CoreVK.logger().info("Forcing {} pack settings from {}: {}", settings.size(), file,
					settings);
		}

		if (!reserved.isEmpty()) {
			CoreVK.logger().info("{} lines of {} name this engine rather than a setting of the "
					+ "pack: {}", reserved.size(), file, reserved);
		}

		unknown.forEach(name -> CoreVK.logger().warn("'{}={}' in {} names neither a line this engine"
				+ " reads nor a setting {} declares, so it forces nothing and the pack keeps its own"
				+ " defaults. A setting is applied where the pack declares it, and this one has no"
				+ " declaration to apply it to", name, opened.forced().get(name).asText(), file,
				opened.packFileName()));
	}

	
	static void announceEntitiesOff(Path gameDirectory) {
		CoreVK.logger().info("{}=off, so the game draws its own entities and the scene seed carries "
				+ "them in, already lit and already tone mapped. The glint an enchantment puts over "
				+ "what they hold or wear goes back with them, this switch carrying the two halves of "
				+ "it that are drawn in the world. Remove that line from {} to have the pack draw "
				+ "them, which is the default", ENTITIES_KEY, SettingsLayers.file(gameDirectory));
	}

	
	static void announceHandOff(Path gameDirectory) {
		CoreVK.logger().info("{}=off, so the player's own hand stays where the game draws it, after "
				+ "the pack's chain: painted over the finished image by the game's own shader, absent "
				+ "from every gbuffer and from every depth a composite reads, and the glint over what "
				+ "it holds with it. Remove that line from {} to have it drawn inside the level with "
				+ "the pack's two hand programs, which is the default", HAND_KEY,
				SettingsLayers.file(gameDirectory));
	}

	
	static void announceCloudsOff(Path gameDirectory) {
		CoreVK.logger().info("{}=off in {}, so the game draws its own clouds and the full screen "
				+ "layer carries them in flat. The pack's own clouds directive goes unread with them, "
				+ "which for most of the corpus means the game's clouds are drawn over the ones the "
				+ "pack draws itself", CLOUDS_KEY, SettingsLayers.file(gameDirectory));
	}

	
	static void announceWeatherOff(Path gameDirectory) {
		CoreVK.logger().info("{}=off in {}, so the game draws its own rain and snow with its own "
				+ "shader, into its own target and lit as the game lights them. The pack's own "
				+ "weather directive goes unread with them, so a pack that draws its own curtain gets "
				+ "the game's back in front of it", WEATHER_KEY, SettingsLayers.file(gameDirectory));
	}

	
	static void announceChainOff() {
		CoreVK.logger().info("{}=off, so nothing of the chain is drawn and the game keeps its own "
				+ "image. The pack is still read, which is what lets a terrain program be judged on "
				+ "its own", CHAIN_KEY);
	}

	
	private static ChainFilter filterOf(OptionValue value) {
		if (value == null) {
			return ChainFilter.ALL;
		}

		// off means none of them, which is the final alone, and on means the lot. A count and a
		// list of names go through untouched.
		String text = value.isBoolean() ? (value.asBoolean() ? "" : "0") : value.text();
		ChainFilter filter = ChainFilter.parse(text);
		// By value, because the question is which chain was asked for and not which object came
		// back. No filter parse can build today is equal to ALL without being ALL, so this changes
		// nothing on screen; it stops depending on that. SettingsScreen answers the other way on
		// its own record, and says there why identity is what it needs.
		if (filter.equals(ChainFilter.ALL) && !text.isBlank()) {
			CoreVK.logger().warn("'{}={}' is neither a count nor a list of program names, so the "
					+ "whole chain runs", PASSES_KEY, text);
		} else if (!filter.equals(ChainFilter.ALL)) {
			CoreVK.logger().info("Running only part of the chain, {}={}", PASSES_KEY, text);
		}

		return filter;
	}

	
	private static boolean packsFirst(OptionValue value) {
		if (value == null) {
			return true;
		}

		// asText rather than text: a line written screen=on is a boolean, whose text is null.
		String text = value.asText().trim().toLowerCase(Locale.ROOT);
		if (ON_SETTINGS.equals(text)) {
			return false;
		}

		if (!ON_PACKS.equals(text)) {
			CoreVK.logger().warn("'{}={}' is neither {} nor {}, so the settings screen opens on the "
					+ "pack list", SCREEN_KEY, value.asText(), ON_PACKS, ON_SETTINGS);
		}

		return true;
	}

	
	private static boolean asked(OptionValue value, String key, boolean byDefault) {
		if (value == null) {
			return byDefault;
		}

		if (value.isBoolean()) {
			return value.asBoolean();
		}

		String text = value.asText().trim().toLowerCase(Locale.ROOT);
		if (text.equals("on") || text.equals("true") || text.equals("1")) {
			return true;
		}

		if (text.equals("off") || text.equals("false") || text.equals("0")) {
			return false;
		}

		// Named, like the two readings above do it: ten lines share this one, so the value on its
		// own leaves whoever fixes the typo looking for which of them carries it.
		CoreVK.logger().warn("'{}={}' is neither on nor off, so this line is ignored and {} stays "
				+ "{}", key, value.asText(), key, byDefault ? "on" : "off");

		return byDefault;
	}

	
	private static String named(OptionValue value) {
		// asText rather than text, for the same reason as above: dump=1 parses as a boolean.
		return value == null ? "" : value.asText().trim().toLowerCase(Locale.ROOT);
	}
}
