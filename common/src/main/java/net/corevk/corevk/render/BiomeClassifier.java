package net.corevk.corevk.render;

import net.corevk.corevk.uniform.BiomeCategory;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class BiomeClassifier {

	private static final Map<Identifier, Integer> IDS = new HashMap<>();
	private static final List<String> NAMES = new ArrayList<>();

	private BiomeClassifier() {
	}

	
	public static void record(ResourceKey<Biome> key) {
		Identifier identifier = key.identifier();
		IDS.put(identifier, NAMES.size());
		NAMES.add(identifier.getPath().toUpperCase(Locale.ROOT));
	}

	
	public static int identify(Holder<Biome> biome) {
		Identifier key = biome.unwrapKey().map(k -> k.identifier()).orElse(null);
		Integer id = key == null ? null : IDS.get(key);

		return id == null ? 0 : id;
	}

	
	public static List<String> names() {
		return List.copyOf(NAMES);
	}

	
	@SuppressWarnings("EnumOrdinal")
	public static int categoryOf(Holder<Biome> holder) {
		return category(holder).ordinal();
	}

	private static BiomeCategory category(Holder<Biome> holder) {
		if (holder.is(BiomeTags.WITHOUT_WANDERING_TRADER_SPAWNS)) {
			// Literally only the void carries this one.
			return BiomeCategory.NONE;
		} else if (holder.is(BiomeTags.HAS_VILLAGE_SNOWY)) {
			return BiomeCategory.ICY;
		} else if (holder.is(BiomeTags.IS_HILL)) {
			return BiomeCategory.EXTREME_HILLS;
		} else if (holder.is(BiomeTags.IS_TAIGA)) {
			return BiomeCategory.TAIGA;
		} else if (holder.is(BiomeTags.IS_OCEAN)) {
			return BiomeCategory.OCEAN;
		} else if (holder.is(BiomeTags.IS_JUNGLE)) {
			return BiomeCategory.JUNGLE;
		} else if (holder.is(BiomeTags.IS_FOREST)) {
			return BiomeCategory.FOREST;
		} else if (holder.is(BiomeTags.IS_BADLANDS)) {
			return BiomeCategory.MESA;
		} else if (holder.is(BiomeTags.IS_NETHER)) {
			return BiomeCategory.NETHER;
		} else if (holder.is(BiomeTags.IS_END)) {
			return BiomeCategory.THE_END;
		} else if (holder.is(BiomeTags.IS_BEACH)) {
			return BiomeCategory.BEACH;
		} else if (holder.is(BiomeTags.HAS_DESERT_PYRAMID)) {
			return BiomeCategory.DESERT;
		} else if (holder.is(BiomeTags.IS_RIVER)) {
			return BiomeCategory.RIVER;
		} else if (holder.is(BiomeTags.ALLOWS_SURFACE_SLIME_SPAWNS)) {
			return BiomeCategory.SWAMP;
		} else if (holder.is(BiomeTags.WITHOUT_ZOMBIE_SIEGES)) {
			return BiomeCategory.MUSHROOM;
		} else if (holder.is(BiomeTags.IS_MOUNTAIN)) {
			return BiomeCategory.MOUNTAIN;
		} else {
			return BiomeCategory.PLAINS;
		}
	}
}
