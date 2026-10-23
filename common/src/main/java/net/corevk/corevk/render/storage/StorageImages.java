package net.corevk.corevk.render.storage;

import net.corevk.corevk.mixin.access.CommandEncoderAccessor;
import net.corevk.corevk.mixin.access.GpuDeviceAccessor;
import net.corevk.corevk.mixin.access.VulkanCommandEncoderAccessor;
import net.corevk.corevk.pack.model.ImageInformation;
import net.corevk.corevk.pack.model.PackTexture;
import net.corevk.corevk.pack.model.TargetFormat;
import net.corevk.corevk.pack.texture.CustomImageView;
import net.corevk.corevk.pack.texture.CustomImages;
import net.corevk.corevk.CoreVK;

import com.mojang.blaze3d.GpuDeviceLossException;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.GpuDeviceBackend;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vulkan.VulkanCommandEncoder;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.Vma;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkClearColorValue;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkImageCopy;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkImageSubresourceRange;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkImageFormatListCreateInfo;
import org.lwjgl.vulkan.VkFormatProperties;

import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class StorageImages implements AutoCloseable {

	private static volatile StorageImages current = none();

	private static final Map<TargetFormat, Boolean> VIEWABLE = new ConcurrentHashMap<>();

	private static final boolean CLEAR_AT_BIRTH = Boolean.parseBoolean(
			System.getProperty("corevk.clearStorageAtBirth", "true"));

	private static final long CLEAR_AT_BIRTH_BUDGET = 64L * 1024L * 1024L;

	private final ImageInformation.Reading declared;
	private final List<Allocated> allocated = new ArrayList<>();

	private volatile Map<String, Bound> bindings = Map.of();
	private volatile Map<String, Allocated> imagesByName = Map.of();
	private final Map<String, Bound> typedBindings = new ConcurrentHashMap<>();
	private int lastWidth;
	private int lastHeight;
	private boolean laidOut;

	private boolean refusedForGood;

	public StorageImages(ImageInformation.Reading declared) {
		this.declared = declared;
	}

	static StorageImages none() {
		return new StorageImages(ImageInformation.Reading.empty());
	}

	public static Bound bound(String name) {
		return current.lookup(name);
	}

	public static boolean storageBinding(String name) {
		Bound bound = bound(name);
		return bound != null && bound.storage();
	}

	public static List<String> unusableViews(Collection<String> names) {
		List<String> unusable = new ArrayList<>();
		VulkanDevice device = vulkan();
		if (device == null) {
			return unusable;
		}

		for (String name : names) {
			CustomImageView.parse(name).ifPresent(view -> CustomImages.image(view.original())
					.map(image -> image.internalFormat().used())
					.filter(base -> !viewable(device, base) || !viewable(device, view.format()))
					.ifPresent(_ -> unusable.add(name)));
		}

		return unusable;
	}

	private static boolean viewable(VulkanDevice device, TargetFormat format) {
		return VIEWABLE.computeIfAbsent(format, _ -> supportsView(device, format));
	}

	void install() {
		current = this;
	}

	private Bound lookup(String name) {
		Bound ordinary = this.bindings.get(name);
		if (ordinary != null) return ordinary;
		Bound cached = this.typedBindings.get(name);
		if (cached != null) return cached;
		var request = CustomImageView.parse(name);
		if (request.isEmpty()) return null;
		Allocated image = this.imagesByName.get(request.get().original());
		if (image == null) return null;
		return this.typedBindings.computeIfAbsent(name, _ -> new Bound(
				image.typedView(request.get().format()),
				image.declared.name().equals(request.get().original()), request.get().format().integer()));
	}

	private void rebind() {
		Map<String, Bound> bound = new HashMap<>();
		Map<String, Allocated> named = new HashMap<>();
		for (Allocated image : this.allocated) {
			boolean integer = image.declared.internalFormat().used().integer();
			bound.putIfAbsent(image.declared.name(), new Bound(image.view, true, integer));
			image.declared.sampler().ifPresent(sampler ->
					bound.putIfAbsent(sampler, new Bound(image.view, false, integer)));
			named.putIfAbsent(image.declared.name(), image);
			image.declared.sampler().ifPresent(sampler -> named.putIfAbsent(sampler, image));
		}

		this.bindings = Map.copyOf(bound);
		this.imagesByName = Map.copyOf(named);
		this.typedBindings.clear();
	}

	public record Bound(long view, boolean storage, boolean integer) {
	}

	public boolean refusedForGood() {
		return this.refusedForGood;
	}

	public void ensure(int screenWidth, int screenHeight) {
		install();
		if (this.declared.images().isEmpty()) {
			return;
		}

		boolean first = this.allocated.isEmpty();
		boolean resized = screenWidth != this.lastWidth || screenHeight != this.lastHeight;
		if (!first && !resized) {
			return;
		}

		VulkanDevice vulkan = vulkan();
		if (vulkan == null) {
			return;
		}

		try {
			allocate(vulkan, first, resized, screenWidth, screenHeight);
		} catch (RuntimeException e) {
			this.allocated.forEach(image -> image.destroy(vulkan));
			this.allocated.clear();
			this.laidOut = false;
			rebind();
			throw e;
		}

		this.lastWidth = screenWidth;
		this.lastHeight = screenHeight;
		rebind();
		layoutIfNeeded();
	}

	private void allocate(VulkanDevice vulkan, boolean first, boolean resized, int screenWidth,
			int screenHeight) {
		if (first) {
			for (ImageInformation image : this.declared.images()) {
				if (image.relative()) {
					continue;
				}

				boolean movable = movable(image);
				try {
					this.allocated.add(Allocated.create(vulkan, image, image.width(), image.height(),
							Math.max(image.depth(), 1), movable));

					CoreVK.logger().info("storage image {}{}", image.describe(), movable
							? ", moved onto each frame's camera block for " + scratchCost(image)
									+ " more"
							: image.clear() && image.depth() > 1
									? ", left on the frame that filled it"
									: "");
				} catch (GpuDeviceLossException e) {
					throw e;
				} catch (RuntimeException e) {
					this.refusedForGood = true;
					throw refused(image, e);
				}
			}
		}

		if (first || resized) {
			List<Allocated> kept = new ArrayList<>();
			for (Allocated image : this.allocated) {
				if (image.relative) {
					image.destroy(vulkan);
					this.laidOut = false;
				} else {
					kept.add(image);
				}
			}

			this.allocated.clear();
			this.allocated.addAll(kept);
			for (ImageInformation image : this.declared.images()) {
				if (!image.relative()) {
					continue;
				}

				int width = Math.max(1, (int) (screenWidth * image.relativeWidth()));
				int height = Math.max(1, (int) (screenHeight * image.relativeHeight()));
				try {

					this.allocated.add(Allocated.create(vulkan, image, width, height, 1, false));
					CoreVK.logger().info("storage image {} at {}x{}", image.describe(), width,
							height);
				} catch (GpuDeviceLossException e) {
					throw e;
				} catch (RuntimeException e) {
					throw refused(image, e);
				}
			}
		}
	}

	private static IllegalStateException refused(ImageInformation image, RuntimeException cause) {
		return new IllegalStateException("storage image " + image.describe()
				+ " could not be allocated, and a program declaring it cannot be drawn without it",
				cause);
	}

	public void clearMarked(CommandEncoder encoder) {
		GpuRecording.endPass(encoder);
		VkCommandBuffer commands = commands(encoder);
		if (commands == null) {
			return;
		}

		try (MemoryStack stack = MemoryStack.stackPush()) {

			GpuRecording.beforeTransfer(commands, stack);
			for (Allocated image : this.allocated) {
				if (!image.declared.clear()) {
					continue;
				}

				clearImage(commands, stack, image);
			}

			GpuRecording.afterTransfer(commands, stack);
		}
	}

	private static String scratchCost(ImageInformation image) {
		long texels = (long) Math.max(image.width(), 1) * Math.max(image.height(), 1)
				* Math.max(image.depth(), 1);
		long bytes = texels * image.internalFormat().used().bytesPerPixel();

		return bytes >= 1024L * 1024L
				? bytes / (1024L * 1024L) + " MiB"
				: bytes / 1024L + " KiB";
	}

	private boolean movable(ImageInformation image) {
		if (!image.clear() || image.relative() || image.depth() <= 1) {
			return false;
		}

		for (ImageInformation other : this.declared.images()) {
			if (!other.clear() && !other.relative() && other.width() == image.width()
					&& other.height() == image.height() && other.depth() == image.depth()) {
				return true;
			}
		}

		return false;
	}

	public void reanchor(CommandEncoder encoder, int dx, int dy, int dz) {
		if (dx == 0 && dy == 0 && dz == 0) {
			return;
		}

		GpuRecording.endPass(encoder);
		VkCommandBuffer commands = commands(encoder);
		if (commands == null) {
			return;
		}

		try (MemoryStack stack = MemoryStack.stackPush()) {
			GpuRecording.beforeTransfer(commands, stack);
			for (Allocated image : this.allocated) {
				if (image.scratch == 0L) {
					continue;
				}

				if (Math.abs(dx) >= image.width || Math.abs(dy) >= image.height
						|| Math.abs(dz) >= image.depth) {
					clearImage(commands, stack, image);
					continue;
				}

				move(commands, stack, image, dx, dy, dz);
			}

			GpuRecording.afterTransfer(commands, stack);
		}
	}

	private static void move(VkCommandBuffer commands, MemoryStack stack, Allocated image,
			int dx, int dy, int dz) {

		int fromX = Math.max(dx, 0);
		int fromY = Math.max(dy, 0);
		int fromZ = Math.max(dz, 0);
		int spanX = image.width - Math.abs(dx);
		int spanY = image.height - Math.abs(dy);
		int spanZ = image.depth - Math.abs(dz);

		VkImageCopy.Buffer kept = VkImageCopy.calloc(1, stack);
		VkImageCopy carried = kept.get(0);
		layers(carried);
		carried.srcOffset().set(fromX, fromY, fromZ);
		carried.dstOffset().set(fromX, fromY, fromZ);
		carried.extent().set(spanX, spanY, spanZ);
		VK12.vkCmdCopyImage(commands, image.image, VK12.VK_IMAGE_LAYOUT_GENERAL,
				image.scratch, VK12.VK_IMAGE_LAYOUT_GENERAL, kept);

		GpuRecording.betweenTransfers(commands, stack);

		VkImageCopy.Buffer shifted = VkImageCopy.calloc(1, stack);
		VkImageCopy region = shifted.get(0);
		layers(region);
		region.srcOffset().set(fromX, fromY, fromZ);
		region.dstOffset().set(Math.max(-dx, 0), Math.max(-dy, 0), Math.max(-dz, 0));
		region.extent().set(spanX, spanY, spanZ);
		VK12.vkCmdCopyImage(commands, image.scratch, VK12.VK_IMAGE_LAYOUT_GENERAL,
				image.image, VK12.VK_IMAGE_LAYOUT_GENERAL, shifted);
	}

	private static void layers(VkImageCopy region) {
		region.srcSubresource().set(VK12.VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1);
		region.dstSubresource().set(VK12.VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1);
	}

	private void layoutIfNeeded() {
		if (this.laidOut || this.allocated.isEmpty()) {
			return;
		}

		GpuDevice device = RenderSystem.tryGetDevice();
		if (device == null) {
			return;
		}

		CommandEncoder encoder = device.createCommandEncoder();
		GpuRecording.endPass(encoder);
		VkCommandBuffer commands = commands(encoder);
		if (commands == null) {
			return;
		}

		List<Allocated> born = new ArrayList<>();
		try (MemoryStack stack = MemoryStack.stackPush()) {
			for (Allocated image : this.allocated) {

				if (image.laidOut) {
					continue;
				}

				image.laidOut = true;
				born.add(image);

				int count = image.scratch == 0L ? 1 : 2;
				VkImageMemoryBarrier.Buffer barriers = VkImageMemoryBarrier.calloc(count, stack)
						.sType$Default();
				for (int at = 0; at < count; at++) {
					VkImageMemoryBarrier barrier = barriers.get(at);
					barrier.sType$Default();
					barrier.oldLayout(VK12.VK_IMAGE_LAYOUT_UNDEFINED);
					barrier.newLayout(VK12.VK_IMAGE_LAYOUT_GENERAL);
					barrier.srcAccessMask(0);
					barrier.dstAccessMask(VK12.VK_ACCESS_SHADER_READ_BIT
							| VK12.VK_ACCESS_SHADER_WRITE_BIT
							| VK12.VK_ACCESS_TRANSFER_WRITE_BIT
							| VK12.VK_ACCESS_TRANSFER_READ_BIT);
					barrier.srcQueueFamilyIndex(VK12.VK_QUEUE_FAMILY_IGNORED);
					barrier.dstQueueFamilyIndex(VK12.VK_QUEUE_FAMILY_IGNORED);
					barrier.image(at == 0 ? image.image : image.scratch);
					barrier.subresourceRange().set(VK12.VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1);
				}

				VK12.vkCmdPipelineBarrier(commands, VK12.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
						VK12.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, 0, null, null, barriers);
			}

			clearBornImages(commands, stack, born);
		}

		this.laidOut = true;
	}

	private void clearBornImages(VkCommandBuffer commands, MemoryStack stack, List<Allocated> born) {
		List<Allocated> emptying = new ArrayList<>();
		long spent = 0L;
		for (Allocated image : born) {

			if (image.declared.clear()) {
				continue;
			}

			if (!CLEAR_AT_BIRTH) {
				CoreVK.logger().info("Storage volume {} is left as the allocator handed it back, "
						+ "property=corevk.clearStorageAtBirth", image.declared.name());
				continue;
			}

			long bytes = bytes(image);

			if (spent + bytes > CLEAR_AT_BIRTH_BUDGET) {
				CoreVK.logger().info("Storage volume {} is {} MiB and this pass has {} MiB of its "
						+ "{} MiB left, so it is left as the allocator handed it back and a pack "
						+ "reading it before writing it whole reads that",
						image.declared.name(), bytes / (1024L * 1024L),
						(CLEAR_AT_BIRTH_BUDGET - spent) / (1024L * 1024L),
						CLEAR_AT_BIRTH_BUDGET / (1024L * 1024L));
				continue;
			}

			spent += bytes;
			emptying.add(image);
		}

		if (emptying.isEmpty()) {
			return;
		}

		for (Allocated image : emptying) {
			clearImage(commands, stack, image);
		}

		GpuRecording.afterTransfer(commands, stack);

		CoreVK.logger().info("{} storage volume(s) emptied as they were created, "
				+ "property=corevk.clearStorageAtBirth", emptying.size());
	}

	private static long bytes(Allocated image) {
		return (long) image.width * image.height * image.depth
				* image.declared.internalFormat().used().bytesPerPixel();
	}

	private static void clearImage(VkCommandBuffer commands, MemoryStack stack, Allocated image) {
		VkClearColorValue colour = VkClearColorValue.calloc(stack);
		if (image.declared.internalFormat().used().integer()) {
			IntBuffer ints = colour.int32();
			ints.put(0, 0).put(1, 0).put(2, 0).put(3, 0);
		} else {
			colour.float32().put(0, 0.0F).put(1, 0.0F).put(2, 0.0F).put(3, 0.0F);
		}

		VkImageSubresourceRange range = VkImageSubresourceRange.calloc(stack);
		range.set(VK12.VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1);
		VK12.vkCmdClearColorImage(commands, image.image, VK12.VK_IMAGE_LAYOUT_GENERAL, colour, range);
	}

	private static VkCommandBuffer commands(CommandEncoder encoder) {
		return ((CommandEncoderAccessor) encoder).corevk$backend() instanceof VulkanCommandEncoder vulkan
				? ((VulkanCommandEncoderAccessor) vulkan).corevk$commandBuffer()
				: null;
	}

	int count() {
		return this.allocated.size();
	}

	@Override
	public void close() {
		if (current == this) {
			current = none();
		}

		VulkanDevice vulkan = vulkan();
		if (vulkan != null) {
			this.allocated.forEach(image -> image.destroy(vulkan));
		}

		this.allocated.clear();
		this.bindings = Map.of();
		this.imagesByName = Map.of();
		this.typedBindings.clear();
		this.lastWidth = 0;
		this.lastHeight = 0;
		this.laidOut = false;
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

		private final ImageInformation declared;
		private final boolean relative;
		private final int width;
		private final int height;
		private final int depth;
		private long image;
		private long allocation;
		private long view;
		private final Map<TargetFormat, Long> alternateViews = new HashMap<>();
		private List<TargetFormat> allowedViews = List.of();

		private long scratch;
		private long scratchAllocation;

		private boolean laidOut;

		private Allocated(ImageInformation declared, boolean relative, int width, int height,
				int depth, long image, long allocation, long view) {
			this.declared = declared;
			this.relative = relative;
			this.width = width;
			this.height = height;
			this.depth = depth;
			this.image = image;
			this.allocation = allocation;
			this.view = view;
		}

		private static Allocated create(VulkanDevice vulkan, ImageInformation declared, int width,
				int height, int depth, boolean movable) {
			int vkFormat = vkFormat(declared.internalFormat().used());
			int type = imageType(declared.shape());
			int viewType = viewType(declared.shape());
			int extentWidth = Math.max(width, 1);
			int extentHeight = Math.max(height, 1);
			int extentDepth = Math.max(depth, 1);
			try (MemoryStack stack = MemoryStack.stackPush()) {
				TargetFormat base = declared.internalFormat().used();
				List<TargetFormat> compatible = Arrays.stream(TargetFormat.values())
						.filter(format -> CustomImageView.compatible(base, format))
						.filter(format -> supportsView(vulkan, format)).toList();
				if (!compatible.contains(base)) {

					CoreVK.logger().warn("Storage image {} is declared {}, which this device does "
							+ "not both sample and store, so it takes no view in another format",
							declared.name(), base);
					compatible = List.of(base);
				}
				VkImageCreateInfo imageInfo = VkImageCreateInfo.calloc(stack).sType$Default();

				if (compatible.size() > 1) {
					VkImageFormatListCreateInfo formats = VkImageFormatListCreateInfo.calloc(stack)
							.sType$Default().pViewFormats(stack.ints(compatible.stream()
									.mapToInt(StorageImages::vkFormat).toArray()));
					imageInfo.flags(VK12.VK_IMAGE_CREATE_MUTABLE_FORMAT_BIT).pNext(formats.address());
				}
				imageInfo.imageType(type);
				imageInfo.extent().set(extentWidth, extentHeight, extentDepth);
				imageInfo.mipLevels(1);
				imageInfo.arrayLayers(1);
				imageInfo.format(vkFormat);
				imageInfo.tiling(VK12.VK_IMAGE_TILING_OPTIMAL);
				imageInfo.initialLayout(VK12.VK_IMAGE_LAYOUT_UNDEFINED);
				imageInfo.usage(VK12.VK_IMAGE_USAGE_STORAGE_BIT
						| VK12.VK_IMAGE_USAGE_SAMPLED_BIT
						| VK12.VK_IMAGE_USAGE_TRANSFER_SRC_BIT
						| VK12.VK_IMAGE_USAGE_TRANSFER_DST_BIT);
				imageInfo.sharingMode(VK12.VK_SHARING_MODE_EXCLUSIVE);
				imageInfo.samples(VK12.VK_SAMPLE_COUNT_1_BIT);
				VmaAllocationCreateInfo allocationInfo = VmaAllocationCreateInfo.calloc(stack);
				allocationInfo.usage(8);
				LongBuffer imagePtr = stack.callocLong(1);
				PointerBuffer allocationPtr = stack.callocPointer(1);
				VulkanUtils.crashIfFailure(vulkan,
						Vma.vmaCreateImage(vulkan.vma(), imageInfo, allocationInfo, imagePtr,
								allocationPtr, null),
						"storage image " + declared.name());
				long image = imagePtr.get(0);
				long allocation = allocationPtr.get(0);

				VkImageViewCreateInfo viewInfo = VkImageViewCreateInfo.calloc(stack).sType$Default();
				viewInfo.image(image);
				viewInfo.viewType(viewType);
				viewInfo.format(vkFormat);
				viewInfo.subresourceRange().set(VK12.VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1);
				LongBuffer viewPtr = stack.callocLong(1);
				try {
					VulkanUtils.crashIfFailure(vulkan,
							VK12.vkCreateImageView(vulkan.vkDevice(), viewInfo, null, viewPtr),
							"storage image view " + declared.name());
				} catch (RuntimeException e) {
					Vma.vmaDestroyImage(vulkan.vma(), image, allocation);
					throw e;
				}

				Allocated allocated = new Allocated(declared, declared.relative(), extentWidth,
						extentHeight, extentDepth, image, allocation, viewPtr.get(0));
				allocated.allowedViews = compatible;

				if (movable) {
					try {
						VulkanUtils.crashIfFailure(vulkan,
								Vma.vmaCreateImage(vulkan.vma(), imageInfo, allocationInfo, imagePtr,
										allocationPtr, null),
								"storage image scratch " + declared.name());
						allocated.scratch = imagePtr.get(0);
						allocated.scratchAllocation = allocationPtr.get(0);
					} catch (GpuDeviceLossException e) {
						throw e;
					} catch (RuntimeException e) {
						CoreVK.logger().warn("storage image {} keeps a frame of lag, its scratch "
								+ "could not be allocated: {}", declared.name(), e.toString());
					}
				}

				return allocated;
			}
		}

		private synchronized long typedView(TargetFormat format) {
			if (format == this.declared.internalFormat().used()) return this.view;
			Long cached = this.alternateViews.get(format);
			if (cached != null) return cached;

			VulkanDevice device = vulkan();
			if (!this.allowedViews.contains(format) || this.image == 0L || device == null) {
				CoreVK.logger().warn("Custom image {} has no {} view here, so its own is bound",
						this.declared.name(), format);
				return this.view;
			}
			try (MemoryStack stack = MemoryStack.stackPush()) {
				VkImageViewCreateInfo info = VkImageViewCreateInfo.calloc(stack).sType$Default()
						.image(this.image).viewType(viewType(this.declared.shape())).format(vkFormat(format));
				info.subresourceRange().set(VK12.VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1);
				LongBuffer result = stack.callocLong(1);
				VulkanUtils.crashIfFailure(device,
						VK12.vkCreateImageView(device.vkDevice(), info, null, result),
						"typed storage image view " + this.declared.name());
				long created = result.get(0);
				this.alternateViews.put(format, created);
				CoreVK.logger().info("Custom image {} uses a {} view of {} memory",
						this.declared.name(), format, this.declared.internalFormat().used());
				return created;
			}
		}

		private synchronized void destroy(VulkanDevice vulkan) {
			long view = this.view;
			List<Long> alternate = List.copyOf(this.alternateViews.values());
			this.alternateViews.clear();
			long image = this.image;
			long allocation = this.allocation;
			long scratch = this.scratch;
			long scratchAllocation = this.scratchAllocation;
			this.view = 0L;
			this.image = 0L;
			this.allocation = 0L;
			this.scratch = 0L;
			this.scratchAllocation = 0L;
			GpuRecording.destroyLater(() -> {
				for (long typed : alternate) VK12.vkDestroyImageView(vulkan.vkDevice(), typed, null);
				if (view != 0L) {
					VK12.vkDestroyImageView(vulkan.vkDevice(), view, null);
				}

				if (image != 0L) {
					Vma.vmaDestroyImage(vulkan.vma(), image, allocation);
				}

				if (scratch != 0L) {
					Vma.vmaDestroyImage(vulkan.vma(), scratch, scratchAllocation);
				}
			});
		}
	}

	private static boolean supportsView(VulkanDevice device, TargetFormat format) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			VkFormatProperties properties = VkFormatProperties.calloc(stack);
			VK12.vkGetPhysicalDeviceFormatProperties(device.vkDevice().getPhysicalDevice(),
					vkFormat(format), properties);
			int required = VK12.VK_FORMAT_FEATURE_STORAGE_IMAGE_BIT | VK12.VK_FORMAT_FEATURE_SAMPLED_IMAGE_BIT;
			if (format == TargetFormat.R32_UINT || format == TargetFormat.R32_SINT) {
				required |= VK12.VK_FORMAT_FEATURE_STORAGE_IMAGE_ATOMIC_BIT;
			}
			return (properties.optimalTilingFeatures() & required) == required;
		}
	}

	private static int imageType(PackTexture.Shape shape) {
		return switch (shape) {
			case TEXTURE_1D -> VK12.VK_IMAGE_TYPE_1D;
			case TEXTURE_3D -> VK12.VK_IMAGE_TYPE_3D;
			case TEXTURE_2D, TEXTURE_RECTANGLE -> VK12.VK_IMAGE_TYPE_2D;
		};
	}

	private static int viewType(PackTexture.Shape shape) {
		return switch (shape) {
			case TEXTURE_1D -> VK12.VK_IMAGE_VIEW_TYPE_1D;
			case TEXTURE_3D -> VK12.VK_IMAGE_VIEW_TYPE_3D;
			case TEXTURE_2D, TEXTURE_RECTANGLE -> VK12.VK_IMAGE_VIEW_TYPE_2D;
		};
	}

	private static int vkFormat(TargetFormat format) {
		return switch (format) {
			case R8_UNORM -> VK12.VK_FORMAT_R8_UNORM;
			case R8_SNORM -> VK12.VK_FORMAT_R8_SNORM;
			case RG8_UNORM -> VK12.VK_FORMAT_R8G8_UNORM;
			case RG8_SNORM -> VK12.VK_FORMAT_R8G8_SNORM;
			case RGBA8_UNORM -> VK12.VK_FORMAT_R8G8B8A8_UNORM;
			case RGBA8_SNORM -> VK12.VK_FORMAT_R8G8B8A8_SNORM;
			case R16_UNORM -> VK12.VK_FORMAT_R16_UNORM;
			case R16_SNORM -> VK12.VK_FORMAT_R16_SNORM;
			case RG16_UNORM -> VK12.VK_FORMAT_R16G16_UNORM;
			case RG16_SNORM -> VK12.VK_FORMAT_R16G16_SNORM;
			case RGBA16_UNORM -> VK12.VK_FORMAT_R16G16B16A16_UNORM;
			case RGBA16_SNORM -> VK12.VK_FORMAT_R16G16B16A16_SNORM;
			case R8_UINT -> VK12.VK_FORMAT_R8_UINT;
			case R8_SINT -> VK12.VK_FORMAT_R8_SINT;
			case RG8_UINT -> VK12.VK_FORMAT_R8G8_UINT;
			case RG8_SINT -> VK12.VK_FORMAT_R8G8_SINT;
			case RGBA8_UINT -> VK12.VK_FORMAT_R8G8B8A8_UINT;
			case RGBA8_SINT -> VK12.VK_FORMAT_R8G8B8A8_SINT;
			case R16_UINT -> VK12.VK_FORMAT_R16_UINT;
			case R16_SINT -> VK12.VK_FORMAT_R16_SINT;
			case RG16_UINT -> VK12.VK_FORMAT_R16G16_UINT;
			case RG16_SINT -> VK12.VK_FORMAT_R16G16_SINT;
			case RGBA16_UINT -> VK12.VK_FORMAT_R16G16B16A16_UINT;
			case RGBA16_SINT -> VK12.VK_FORMAT_R16G16B16A16_SINT;
			case R32_UINT -> VK12.VK_FORMAT_R32_UINT;
			case R32_SINT -> VK12.VK_FORMAT_R32_SINT;
			case RG32_UINT -> VK12.VK_FORMAT_R32G32_UINT;
			case RG32_SINT -> VK12.VK_FORMAT_R32G32_SINT;
			case RGBA32_UINT -> VK12.VK_FORMAT_R32G32B32A32_UINT;
			case RGBA32_SINT -> VK12.VK_FORMAT_R32G32B32A32_SINT;
			case R16_FLOAT -> VK12.VK_FORMAT_R16_SFLOAT;
			case RG16_FLOAT -> VK12.VK_FORMAT_R16G16_SFLOAT;
			case RGBA16_FLOAT -> VK12.VK_FORMAT_R16G16B16A16_SFLOAT;
			case R32_FLOAT -> VK12.VK_FORMAT_R32_SFLOAT;
			case RG32_FLOAT -> VK12.VK_FORMAT_R32G32_SFLOAT;
			case RGBA32_FLOAT -> VK12.VK_FORMAT_R32G32B32A32_SFLOAT;
			case RGB10A2_UNORM -> VK12.VK_FORMAT_A2B10G10R10_UNORM_PACK32;
			case RGB10A2_UINT -> VK12.VK_FORMAT_A2B10G10R10_UINT_PACK32;
			case RG11B10_FLOAT -> VK12.VK_FORMAT_B10G11R11_UFLOAT_PACK32;
		};
	}
}
