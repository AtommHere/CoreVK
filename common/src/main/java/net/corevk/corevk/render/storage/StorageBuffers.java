package net.corevk.corevk.render.storage;

import net.corevk.corevk.mixin.access.CommandEncoderAccessor;
import net.corevk.corevk.mixin.access.GpuDeviceAccessor;
import net.corevk.corevk.mixin.access.VulkanCommandEncoderAccessor;
import net.corevk.corevk.pack.model.BufferObject;
import net.corevk.corevk.pack.texture.CustomStorage;
import net.corevk.corevk.CoreVK;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.GpuDeviceBackend;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vulkan.VulkanCommandEncoder;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.Vma;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class StorageBuffers implements AutoCloseable {

	private static volatile StorageBuffers current = none();

	private final BufferObject.Reading declared;
	private final List<Allocated> allocated = new ArrayList<>();

	
	private Map<Integer, Bound> bindings = Map.of();
	private GpuBuffer dummy;
	private int lastWidth;
	private int lastHeight;

	public StorageBuffers(BufferObject.Reading declared) {
		this.declared = declared;
	}

	static StorageBuffers none() {
		return new StorageBuffers(BufferObject.Reading.empty());
	}

	
	public static boolean named(String name) {
		return CustomStorage.named(name);
	}

	
	public static Bound bound(String name) {
		return current.lookup(name);
	}

	private Bound lookup(String name) {
		if (this.bindings.isEmpty()) {
			return null;
		}

		int index = CustomStorage.indexOf(name);
		return index < 0 ? null : this.bindings.get(index);
	}

	private void rebind() {
		Map<Integer, Bound> bound = new HashMap<>();
		for (Allocated buffer : this.allocated) {
			bound.putIfAbsent(buffer.declared.index(), new Bound(buffer.buffer, buffer.bytes));
		}

		this.bindings = Map.copyOf(bound);
	}

	
	public record Bound(long buffer, long range) {
	}

	void install() {
		current = this;
	}

	
	public void ensure(int screenWidth, int screenHeight) {
		install();
		if (this.declared.buffers().isEmpty()) {
			return;
		}

		boolean first = this.allocated.isEmpty();
		boolean resized = screenWidth != this.lastWidth || screenHeight != this.lastHeight;
		if (!first && !resized) {
			return;
		}

		VulkanDevice vulkan = vulkan();
		GpuDevice device = RenderSystem.tryGetDevice();
		if (vulkan == null || device == null) {
			return;
		}

		ensurePlaceholder(device);
		// The map follows the list whatever happens below: an allocation that throws halfway
		// through a resize has already destroyed the relative buffers and dropped them from the
		// list, and a map left standing would hand a destroyed handle to the next descriptor push.
		try {
			allocate(vulkan, first, resized, screenWidth, screenHeight);
		} finally {
			rebind();
		}

		this.lastWidth = screenWidth;
		this.lastHeight = screenHeight;
		zero(device);
	}

	private void allocate(VulkanDevice vulkan, boolean first, boolean resized, int screenWidth,
			int screenHeight) {
		if (first) {
			for (BufferObject buffer : this.declared.buffers()) {
				if (buffer.relative()) {
					continue;
				}

				this.allocated.add(Allocated.create(vulkan, buffer, aligned(buffer.size())));
				CoreVK.logger().info("storage buffer {}", buffer.describe());
			}
		}

		if (first || resized) {
			List<Allocated> kept = new ArrayList<>();
			for (Allocated buffer : this.allocated) {
				if (buffer.relative) {
					buffer.destroy(vulkan);
				} else {
					kept.add(buffer);
				}
			}

			this.allocated.clear();
			this.allocated.addAll(kept);
			for (BufferObject buffer : this.declared.buffers()) {
				if (!buffer.relative()) {
					continue;
				}

				long bytes = aligned((long) (screenWidth * buffer.scaleX())
						* (long) (screenHeight * buffer.scaleY())
						* buffer.size());
				this.allocated.add(Allocated.create(vulkan, buffer, Math.max(bytes, 4L)));
				CoreVK.logger().info("storage buffer {} at {} bytes", buffer.describe(), bytes);
			}
		}
	}

	private void ensurePlaceholder(GpuDevice device) {
		if (this.dummy == null) {
			this.dummy = device.createBuffer(() -> "corevk ssbo placeholder",
					GpuBuffer.USAGE_UNIFORM, 16);
		}
	}

	
	public static void bind(RenderPass pass, List<String> names) {
		if (names.isEmpty()) {
			return;
		}

		GpuBufferSlice slice = current.placeholder();
		if (slice == null) {
			return;
		}

		for (String name : names) {
			pass.setUniform(name, slice);
		}
	}

	private GpuBufferSlice placeholder() {
		return this.dummy == null ? null : this.dummy.slice(0, 16);
	}

	
	private void zero(GpuDevice device) {
		CommandEncoder encoder = device.createCommandEncoder();
		GpuRecording.endPass(encoder);
		VkCommandBuffer commands = commands(encoder);
		if (commands == null) {
			return;
		}

		boolean filled = false;
		for (Allocated buffer : this.allocated) {
			if (buffer.zeroed) {
				continue;
			}

			VK12.vkCmdFillBuffer(commands, buffer.buffer, 0L, buffer.bytes, 0);
			buffer.zeroed = true;
			filled = true;
			CoreVK.logger().info("storage buffer {} zeroed whole at {} bytes",
					buffer.declared.describe(), buffer.bytes);
		}

		if (filled) {
			try (MemoryStack stack = MemoryStack.stackPush()) {
				GpuRecording.afterTransfer(commands, stack);
			}
		}
	}

	private static long aligned(long size) {
		return Math.max(4L, (size + 3L) & ~3L);
	}

	private static VkCommandBuffer commands(CommandEncoder encoder) {
		return ((CommandEncoderAccessor) encoder).corevk$backend() instanceof VulkanCommandEncoder vulkan
				? ((VulkanCommandEncoderAccessor) vulkan).corevk$commandBuffer()
				: null;
	}

	@Override
	public void close() {
		if (current == this) {
			current = none();
		}

		VulkanDevice vulkan = vulkan();
		if (vulkan != null) {
			this.allocated.forEach(buffer -> buffer.destroy(vulkan));
		}

		this.allocated.clear();
		this.bindings = Map.of();
		if (this.dummy != null) {
			this.dummy.close();
			this.dummy = null;
		}

		this.lastWidth = 0;
		this.lastHeight = 0;
	}

	private static VulkanDevice vulkan() {
		GpuDevice device = RenderSystem.tryGetDevice();
		if (device == null) {
			return null;
		}

		GpuDeviceBackend backend = ((GpuDeviceAccessor) device).corevk$backend();
		return backend instanceof VulkanDevice vulkan ? vulkan : null;
	}

	private static final class Allocated {

		private final BufferObject declared;
		private final boolean relative;
		private final long bytes;
		private long buffer;
		private long allocation;
		private boolean zeroed;

		private Allocated(BufferObject declared, boolean relative, long bytes, long buffer,
				long allocation) {
			this.declared = declared;
			this.relative = relative;
			this.bytes = bytes;
			this.buffer = buffer;
			this.allocation = allocation;
		}

		private static Allocated create(VulkanDevice vulkan, BufferObject declared, long bytes) {
			try (MemoryStack stack = MemoryStack.stackPush()) {
				VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc(stack).sType$Default();
				bufferInfo.size(bytes);
				bufferInfo.usage(VK12.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT
						| VK12.VK_BUFFER_USAGE_TRANSFER_DST_BIT);
				bufferInfo.sharingMode(VK12.VK_SHARING_MODE_EXCLUSIVE);
				VmaAllocationCreateInfo allocationInfo = VmaAllocationCreateInfo.calloc(stack);
				allocationInfo.usage(8);
				LongBuffer bufferPtr = stack.callocLong(1);
				PointerBuffer allocationPtr = stack.callocPointer(1);
				VulkanUtils.crashIfFailure(vulkan,
						Vma.vmaCreateBuffer(vulkan.vma(), bufferInfo, allocationInfo, bufferPtr,
								allocationPtr, null),
						"storage buffer " + declared.index());
				return new Allocated(declared, declared.relative(), bytes, bufferPtr.get(0),
						allocationPtr.get(0));
			}
		}

		
		private void destroy(VulkanDevice vulkan) {
			long buffer = this.buffer;
			long allocation = this.allocation;
			this.buffer = 0L;
			this.allocation = 0L;
			if (buffer != 0L) {
				GpuRecording.destroyLater(() -> Vma.vmaDestroyBuffer(vulkan.vma(), buffer, allocation));
			}
		}
	}
}
