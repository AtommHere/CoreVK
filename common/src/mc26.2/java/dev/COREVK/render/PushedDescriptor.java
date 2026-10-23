package net.corevk.corevk.render;

import net.corevk.corevk.render.storage.StorageBuffers;
import net.corevk.corevk.render.storage.StorageImages;

import com.mojang.blaze3d.vulkan.VulkanBindGroupLayout;

public final class PushedDescriptor {

	private static final ThreadLocal<PushedDescriptor> CURRENT =
			ThreadLocal.withInitial(PushedDescriptor::new);

	private VulkanBindGroupLayout.Entry entry;
	private StorageImages.Bound image;
	private StorageBuffers.Bound buffer;

	private PushedDescriptor() {
	}

	public static void begin(VulkanBindGroupLayout.Entry entry) {
		PushedDescriptor current = CURRENT.get();
		current.entry = entry;
		current.image = StorageImages.bound(entry.name());
		current.buffer = StorageBuffers.bound(entry.name());
	}

	public static PushedDescriptor current() {
		return CURRENT.get();
	}

	public VulkanBindGroupLayout.Entry entry() {
		return this.entry;
	}

	public StorageImages.Bound image() {
		return this.image;
	}

	public StorageBuffers.Bound buffer() {
		return this.buffer;
	}
}
