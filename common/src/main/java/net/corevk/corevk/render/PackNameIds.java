package net.corevk.corevk.render;

import net.corevk.corevk.pack.id.NameIds;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;

public final class PackNameIds {

	private static final Identifier CURRENT_PLAYER =
			Identifier.fromNamespaceAndPath("minecraft", "current_player");

	private static final Identifier CONVERTING_VILLAGER =
			Identifier.fromNamespaceAndPath("minecraft", "zombie_villager_converting");

	private static final Identifier ENTITY_FLAME =
			Identifier.fromNamespaceAndPath("minecraft", "entity_flame");

	private static volatile NameIds entities = NameIds.empty();

	private static volatile NameIds items = NameIds.empty();

	private static final int UNASKED = Integer.MIN_VALUE;

	private static volatile Object2IntMap<EntityType<?>> byType = emptyCache();

	private static volatile Object2IntMap<Identifier> byName = emptyCache();

	private PackNameIds() {
	}

	static void install(NameIds entityIds, NameIds itemIds) {
		entities = entityIds;
		items = itemIds;
		byType = emptyCache();
		byName = emptyCache();
	}

	public static boolean namesCurrentPlayer() {
		return entities.id(CURRENT_PLAYER.toString()) != NameIds.NONE;
	}

	public static boolean namesConvertingVillager() {
		return entities.id(CONVERTING_VILLAGER.toString()) != NameIds.NONE;
	}

	public static int currentPlayer() {
		return entities.id(CURRENT_PLAYER.toString());
	}

	public static int convertingVillager() {
		return entities.id(CONVERTING_VILLAGER.toString());
	}

	public static int flame() {
		return id(entities, ENTITY_FLAME);
	}

	public static int entity(EntityType<?> type) {
		Object2IntMap<EntityType<?>> cache = byType;
		int known = cache.getInt(type);
		if (known != UNASKED) {
			return known;
		}

		Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
		int id = key == null ? NameIds.NONE : id(entities, key);
		cache.put(type, id);

		return id;
	}

	public static int item(Identifier name) {
		Object2IntMap<Identifier> cache = byName;
		int known = cache.getInt(name);
		if (known != UNASKED) {
			return known;
		}

		int id = id(items, name);
		cache.put(name, id);

		return id;
	}

	private static int id(NameIds table, Identifier name) {
		return table.present() ? table.id(name.toString()) : 0;
	}

	private static <K> Object2IntMap<K> emptyCache() {
		Object2IntMap<K> cache = new Object2IntOpenHashMap<>();
		cache.defaultReturnValue(UNASKED);

		return cache;
	}
}
