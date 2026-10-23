package net.corevk.corevk.render;

public final class BlockEntityGeometry {

	
	private static volatile boolean submitting;

	
	private static volatile boolean building;

	
	private static volatile boolean drawing;

	private BlockEntityGeometry() {
	}

	public static void submitting(boolean blockEntity) {
		submitting = blockEntity;
	}

	public static boolean submitting() {
		return submitting;
	}

	public static void building(boolean blockEntity) {
		building = blockEntity;
	}

	public static boolean building() {
		return building;
	}

	public static void drawing(boolean blockEntity) {
		drawing = blockEntity;
	}

	public static boolean drawing() {
		return drawing;
	}

	
	static void clear() {
		submitting = false;
		building = false;
		drawing = false;
	}
}
