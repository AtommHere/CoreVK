package net.corevk.corevk.render;

import net.corevk.corevk.render.storage.StorageBuffers;
import net.corevk.corevk.render.storage.StorageImages;

import org.jspecify.annotations.Nullable;

public final class PushedDescriptor {

	private static final ThreadLocal<PushedDescriptor> CURRENT =
			ThreadLocal.withInitial(PushedDescriptor::new);

	private @Nullable String name;
	private StorageImages.@Nullable Bound image;
	private StorageBuffers.@Nullable Bound buffer;

	private PushedDescriptor() {
	}

	
	public static void begin(String name) {
		PushedDescriptor current = CURRENT.get();
		current.name = name;
		current.image = StorageImages.bound(name);
		current.buffer = StorageBuffers.bound(name);
	}

	
	public static PushedDescriptor current() {
		return CURRENT.get();
	}

	
	public @Nullable String name() {
		return this.name;
	}

	
	public StorageImages.@Nullable Bound image() {
		return this.image;
	}

	
	public StorageBuffers.@Nullable Bound buffer() {
		return this.buffer;
	}
}
