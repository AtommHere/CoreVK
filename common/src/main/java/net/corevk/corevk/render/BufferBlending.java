package net.corevk.corevk.render;

public final class BufferBlending {

	
	private static final ThreadLocal<Boolean> BUILDING = new ThreadLocal<>();

	private static volatile boolean served;

	private BufferBlending() {
	}

	
	public static boolean served() {
		return served;
	}

	
	public static void serve(boolean independentBlend) {
		served = independentBlend;
	}

	
	static void building(boolean building) {
		if (building) {
			BUILDING.set(Boolean.TRUE);
		} else {
			BUILDING.remove();
		}
	}

	
	public static boolean parting() {
		return served && Boolean.TRUE.equals(BUILDING.get());
	}
}
