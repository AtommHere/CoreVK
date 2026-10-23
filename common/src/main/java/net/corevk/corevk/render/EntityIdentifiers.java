package net.corevk.corevk.render;

public final class EntityIdentifiers {

	
	private static final int NONE = 0;

	
	private static final int LANE = 16;

	private static final long LANE_MASK = 0xFFFFL;

	private static volatile int entity = NONE;

	private static volatile int blockEntity = NONE;

	private static volatile int item = NONE;

	private EntityIdentifiers() {
	}

	public static void entity(int id) {
		entity = id;
	}

	public static void blockEntity(int id) {
		blockEntity = id;
	}

	public static void item(int id) {
		item = id;
	}

	public static int entity() {
		return entity;
	}

	public static int blockEntity() {
		return blockEntity;
	}

	public static int item() {
		return item;
	}

	
	public static long packed() {
		return (entity & LANE_MASK)
				| ((blockEntity & LANE_MASK) << LANE)
				| ((item & LANE_MASK) << (2 * LANE));
	}

	
	public static void restore(long packed) {
		// Sign extended back out of the lane, so that the -1 a table answers for an unmapped name is
		// still -1 here and is turned into 0xFFFF once, by the short that is written on the vertex.
		// Keeping it as 65535 from here on would be the same bytes and a different number in the log.
		entity = (short) packed;
		blockEntity = (short) (packed >>> LANE);
		item = (short) (packed >>> (2 * LANE));
	}

	
	public static void clear() {
		entity = NONE;
		blockEntity = NONE;
		item = NONE;
	}
}
