package net.corevk.corevk.mixin;

import net.corevk.corevk.render.GlyphIntensity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vulkan.VulkanGpuTextureView;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(VulkanGpuTextureView.class)
public abstract class VulkanGpuTextureViewMixin {

	@WrapOperation(method = "<init>", require = 1,
			at = @At(value = "INVOKE",
					target = "Lorg/lwjgl/vulkan/VkImageViewCreateInfo;format(I)"
							+ "Lorg/lwjgl/vulkan/VkImageViewCreateInfo;"))
	private VkImageViewCreateInfo corevk$intensity(VkImageViewCreateInfo info, int format,
			Operation<VkImageViewCreateInfo> original) {
		VkImageViewCreateInfo built = original.call(info, format);
		if (GlyphIntensity.swizzling()) {
			built.components()
					.r(VK10.VK_COMPONENT_SWIZZLE_R)
					.g(VK10.VK_COMPONENT_SWIZZLE_R)
					.b(VK10.VK_COMPONENT_SWIZZLE_R)
					.a(VK10.VK_COMPONENT_SWIZZLE_R);
		}

		return built;
	}
}
