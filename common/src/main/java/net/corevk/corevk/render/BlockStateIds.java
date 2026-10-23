package net.corevk.corevk.render;

import net.corevk.corevk.pack.id.BlockIds;
import net.corevk.corevk.CoreVK;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class BlockStateIds {

	public static final int NONE = 0;

	public static final int PACKED_MASK = 0x7FFFFF;

	private static final int MAX_ID = (PACKED_MASK >> 1) - 1;

	private static final int FLUID = 1;

	private static volatile Object2IntMap<BlockState> table = empty();

	private static volatile int generation;

	private BlockStateIds() {
	}

	@SuppressWarnings("NonAtomicVolatileUpdate")
	static void install(BlockIds ids) {
		Object2IntMap<BlockState> built = empty();
		List<String> unknown = new ArrayList<>();

		Set<String> widened = new TreeSet<>();
		for (BlockIds.Entry entry : ids.entries()) {
			if (entry.id() > MAX_ID) {

				unknown.add("block." + entry.id() + ", too large for the mesh to carry");
			} else if (entry.tag()) {
				addTag(entry, built, unknown, widened);
			} else {
				addBlock(entry, built, unknown, widened);
			}
		}

		Object2IntMap<BlockState> previous = table;
		table = built;
		generation++;
		if (!ids.isEmpty()) {

			CoreVK.logger().info("{} of this pack's {} block declarations reach {} block states, in "
					+ "block table {}", ids.entries().size() - unknown.size(), ids.entries().size(),
					built.size(), generation);
		}

		if (!unknown.isEmpty()) {
			CoreVK.logger().info("{} name nothing this game has and are dropped: {}", unknown.size(),
					unknown);
		}

		if (!widened.isEmpty()) {

			CoreVK.logger().warn("{} filters name a property their block has not got, so the "
					+ "declarations that carry them bind more states than the pack named: {}",
					widened.size(), widened);
		}

		ids.problems().forEach(problem -> CoreVK.logger().warn("{}", problem));
		rebuildIfMoved(previous, built);
	}

	private static void rebuildIfMoved(Object2IntMap<BlockState> previous,
			Object2IntMap<BlockState> built) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null || minecraft.level == null || previous.equals(built)) {
			return;
		}

		CoreVK.logger().info("The block ids moved, from {} states to {}. The sections carry them, so "
				+ "they are all built again", previous.size(), built.size());
		minecraft.levelExtractor.allChanged();
	}

	public static int packed(BlockState state) {
		int id = table.getInt(state);

		return id < 0 ? NONE : packedFrom(id);
	}

	public static int packedFluid(BlockState state) {
		return (state == null ? NONE : packed(state)) | FLUID;
	}

	public static int id(BlockState state) {
		return table.getInt(state);
	}

	public static int generation() {
		return generation;
	}

	private static int packedFrom(int id) {
		return (id + 1) << 1;
	}

	private static void addBlock(BlockIds.Entry entry, Object2IntMap<BlockState> into,
			List<String> unknown, Set<String> widened) {
		Identifier identifier;
		try {
			identifier = Identifier.fromNamespaceAndPath(entry.namespace(), entry.path());
		} catch (RuntimeException e) {
			unknown.add(entry.name());

			return;
		}

		Block block = BuiltInRegistries.BLOCK.get(identifier).map(Holder::value).orElse(null);
		if (block == null) {
			unknown.add(entry.name());

			return;
		}

		add(block, entry, into, widened);
	}

	private static void addTag(BlockIds.Entry entry, Object2IntMap<BlockState> into,
			List<String> unknown, Set<String> widened) {
		List<HolderSet.Named<Block>> found = BuiltInRegistries.BLOCK.getTags()
				.filter(tag -> tag.key().location().getNamespace().equalsIgnoreCase(entry.namespace())
						&& tag.key().location().getPath().equalsIgnoreCase(entry.path()))
				.toList();
		if (found.isEmpty()) {
			unknown.add("%" + entry.name());

			return;
		}

		found.forEach(tag -> tag.forEach(block -> add(block.value(), entry, into, widened)));
	}

	private static void add(Block block, BlockIds.Entry entry, Object2IntMap<BlockState> into,
			Set<String> widened) {
		StateDefinition<Block, BlockState> states = block.getStateDefinition();
		if (entry.predicates().isEmpty()) {
			states.getPossibleStates().forEach(state -> into.putIfAbsent(state, entry.id()));

			return;
		}

		Map<Property<?>, String> wanted = new LinkedHashMap<>();
		entry.predicates().forEach((key, value) -> {
			Property<?> property = states.getProperty(key);
			if (property == null) {

				widened.add("block." + entry.id() + " " + entry.name() + " on " + key);

				return;
			}

			wanted.put(property, value);
		});

		for (BlockState state : states.getPossibleStates()) {
			if (matches(state, wanted)) {
				into.putIfAbsent(state, entry.id());
			}
		}
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static boolean matches(BlockState state, Map<Property<?>, String> wanted) {
		for (Map.Entry<Property<?>, String> condition : wanted.entrySet()) {
			Property property = condition.getKey();
			if (!condition.getValue().equals(property.getName(state.getValue(property)))) {
				return false;
			}
		}

		return true;
	}

	private static Object2IntMap<BlockState> empty() {
		Object2IntMap<BlockState> map = new Object2IntOpenHashMap<>();
		map.defaultReturnValue(-1);

		return map;
	}
}
