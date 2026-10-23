package net.corevk.corevk.render;

import net.corevk.corevk.mixin.access.GpuDeviceAccessor;
import net.corevk.corevk.pack.model.TargetFormat;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vulkan.VulkanConst;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkFormatProperties;

final class GpuFormats {

	static {
		// The two enumerations are compiled separately, so a format added on the pack side after
		// this class was built would otherwise surface as a failed draw rather than as a hole.
		for (TargetFormat format : TargetFormat.values()) {
			try {
				of(format);
			} catch (RuntimeException e) {
				throw new IllegalStateException("No device format for " + format, e);
			}
		}
	}

	private GpuFormats() {
	}

	static GpuFormat of(TargetFormat format) {
		return switch (format) {
			case R8_UNORM -> GpuFormat.R8_UNORM;
			case R8_SNORM -> GpuFormat.R8_SNORM;
			case RG8_UNORM -> GpuFormat.RG8_UNORM;
			case RG8_SNORM -> GpuFormat.RG8_SNORM;
			case RGBA8_UNORM -> GpuFormat.RGBA8_UNORM;
			case RGBA8_SNORM -> GpuFormat.RGBA8_SNORM;
			case R16_UNORM -> GpuFormat.R16_UNORM;
			case R16_SNORM -> GpuFormat.R16_SNORM;
			case RG16_UNORM -> GpuFormat.RG16_UNORM;
			case RG16_SNORM -> GpuFormat.RG16_SNORM;
			case RGBA16_UNORM -> GpuFormat.RGBA16_UNORM;
			case RGBA16_SNORM -> GpuFormat.RGBA16_SNORM;
			case R8_UINT -> GpuFormat.R8_UINT;
			case R8_SINT -> GpuFormat.R8_SINT;
			case RG8_UINT -> GpuFormat.RG8_UINT;
			case RG8_SINT -> GpuFormat.RG8_SINT;
			case RGBA8_UINT -> GpuFormat.RGBA8_UINT;
			case RGBA8_SINT -> GpuFormat.RGBA8_SINT;
			case R16_UINT -> GpuFormat.R16_UINT;
			case R16_SINT -> GpuFormat.R16_SINT;
			case RG16_UINT -> GpuFormat.RG16_UINT;
			case RG16_SINT -> GpuFormat.RG16_SINT;
			case RGBA16_UINT -> GpuFormat.RGBA16_UINT;
			case RGBA16_SINT -> GpuFormat.RGBA16_SINT;
			case R32_UINT -> GpuFormat.R32_UINT;
			case R32_SINT -> GpuFormat.R32_SINT;
			case RG32_UINT -> GpuFormat.RG32_UINT;
			case RG32_SINT -> GpuFormat.RG32_SINT;
			case RGBA32_UINT -> GpuFormat.RGBA32_UINT;
			case RGBA32_SINT -> GpuFormat.RGBA32_SINT;
			case R16_FLOAT -> GpuFormat.R16_FLOAT;
			case RG16_FLOAT -> GpuFormat.RG16_FLOAT;
			case RGBA16_FLOAT -> GpuFormat.RGBA16_FLOAT;
			case R32_FLOAT -> GpuFormat.R32_FLOAT;
			case RG32_FLOAT -> GpuFormat.RG32_FLOAT;
			case RGBA32_FLOAT -> GpuFormat.RGBA32_FLOAT;
			case RGB10A2_UNORM -> GpuFormat.RGB10A2_UNORM;
			case RGB10A2_UINT -> GpuFormat.RGB10A2_UINT;
			case RG11B10_FLOAT -> GpuFormat.RG11B10_FLOAT;
		};
	}

	
	static FilterMode filterFor(TargetFormat format) {
		return format.integer() ? FilterMode.NEAREST : FilterMode.LINEAR;
	}

	
	static boolean storageCapable(GpuFormat format) {
		return feature(format, VK10.VK_FORMAT_FEATURE_STORAGE_IMAGE_BIT, false);
	}

	
	static boolean filtersLinearly(GpuFormat format) {
		return feature(format, VK10.VK_FORMAT_FEATURE_SAMPLED_IMAGE_FILTER_LINEAR_BIT, true);
	}

	
	static boolean blitsBothWays(GpuFormat format) {
		return feature(format, VK10.VK_FORMAT_FEATURE_BLIT_SRC_BIT, false)
				&& feature(format, VK10.VK_FORMAT_FEATURE_BLIT_DST_BIT, false);
	}

	
	private static boolean feature(GpuFormat format, int bit, boolean absent) {
		GpuDevice device = RenderSystem.tryGetDevice();
		if (device == null
				|| !(((GpuDeviceAccessor) device).corevk$backend() instanceof VulkanDevice vulkan)) {
			return absent;
		}

		try (MemoryStack stack = MemoryStack.stackPush()) {
			VkFormatProperties properties = VkFormatProperties.calloc(stack);
			VK10.vkGetPhysicalDeviceFormatProperties(vulkan.vkDevice().getPhysicalDevice(),
					VulkanConst.toVk(format), properties);

			return (properties.optimalTilingFeatures() & bit) != 0;
		}
	}
}
