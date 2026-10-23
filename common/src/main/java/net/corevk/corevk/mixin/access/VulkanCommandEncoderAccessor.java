package net.corevk.corevk.mixin.access;

import com.mojang.blaze3d.vulkan.VulkanCommandEncoder;
import com.mojang.blaze3d.vulkan.VulkanRenderPass;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(VulkanCommandEncoder.class)
public interface VulkanCommandEncoderAccessor {

	@Invoker("commandBuffer")
	VkCommandBuffer corevk$commandBuffer();

	@Accessor("currentRenderPass")
	VulkanRenderPass corevk$currentRenderPass();
}
