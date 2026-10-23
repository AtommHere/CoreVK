package net.corevk.corevk.render;

public final class TextureUsage {

	private static final ThreadLocal<Boolean> STORAGE_REQUESTED = ThreadLocal.withInitial(() -> false);

	private TextureUsage() {
	}

	
	public static boolean storageRequested() {
		return STORAGE_REQUESTED.get();
	}

	
	static void requestStorage(boolean requested) {
		STORAGE_REQUESTED.set(requested);
	}
}
