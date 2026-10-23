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
