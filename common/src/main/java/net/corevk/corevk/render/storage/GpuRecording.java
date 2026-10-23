package net.corevk.corevk.render.storage;

import net.corevk.corevk.mixin.access.CommandEncoderAccessor;
import net.corevk.corevk.mixin.access.VulkanCommandEncoderAccessor;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.CommandEncoderBackend;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vulkan.VulkanCommandEncoder;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRSynchronization2;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VK13;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDependencyInfo;
import org.lwjgl.vulkan.VkMemoryBarrier2;

public final class GpuRecording {

	private GpuRecording() {
	}

	
	public static void endPass(CommandEncoder encoder) {
		CommandEncoderBackend backend = ((CommandEncoderAccessor) encoder).corevk$backend();
		if (!(backend instanceof VulkanCommandEncoder vulkan)) {
			return;
		}

		if (((VulkanCommandEncoderAccessor) vulkan).corevk$currentRenderPass() != null) {
			vulkan.submitRenderPass();
		}
	}

	
	public static void destroyLater(Runnable destruction) {
		GpuDevice device = RenderSystem.tryGetDevice();
		CommandEncoderBackend backend = device == null
				? null
				: ((CommandEncoderAccessor) device.createCommandEncoder()).corevk$backend();
		if (backend instanceof VulkanCommandEncoder vulkan) {
			vulkan.queueForDestroy(destruction::run);
		}
	}

	
	static void beforeTransfer(VkCommandBuffer commands, MemoryStack stack) {
		VkMemoryBarrier2.Buffer barrier = VkMemoryBarrier2.calloc(1, stack).sType$Default();
		barrier.srcStageMask(VK13.VK_PIPELINE_STAGE_2_ALL_GRAPHICS_BIT
				| VK13.VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT
				| VK13.VK_PIPELINE_STAGE_2_TRANSFER_BIT);
		barrier.srcAccessMask(VK13.VK_ACCESS_2_SHADER_STORAGE_READ_BIT
				| VK13.VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT
				| VK13.VK_ACCESS_2_SHADER_SAMPLED_READ_BIT
				| VK13.VK_ACCESS_2_TRANSFER_WRITE_BIT);
		barrier.dstStageMask(VK13.VK_PIPELINE_STAGE_2_TRANSFER_BIT);
		barrier.dstAccessMask(VK13.VK_ACCESS_2_TRANSFER_WRITE_BIT
				| VK13.VK_ACCESS_2_TRANSFER_READ_BIT);
		VkDependencyInfo dependency = VkDependencyInfo.calloc(stack).sType$Default();
		dependency.pMemoryBarriers(barrier);
		KHRSynchronization2.vkCmdPipelineBarrier2KHR(commands, dependency);
	}

	
	static void afterTransfer(VkCommandBuffer commands, MemoryStack stack) {
		VkMemoryBarrier2.Buffer barrier = VkMemoryBarrier2.calloc(1, stack).sType$Default();
		barrier.srcStageMask(VK13.VK_PIPELINE_STAGE_2_TRANSFER_BIT);
		barrier.srcAccessMask(VK13.VK_ACCESS_2_TRANSFER_WRITE_BIT);
		barrier.dstStageMask(VK13.VK_PIPELINE_STAGE_2_ALL_GRAPHICS_BIT
				| VK13.VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT);
		barrier.dstAccessMask(VK13.VK_ACCESS_2_SHADER_STORAGE_READ_BIT
				| VK13.VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT
				| VK13.VK_ACCESS_2_SHADER_SAMPLED_READ_BIT);
		VkDependencyInfo dependency = VkDependencyInfo.calloc(stack).sType$Default();
		dependency.pMemoryBarriers(barrier);
		KHRSynchronization2.vkCmdPipelineBarrier2KHR(commands, dependency);
	}

	
	static void betweenTransfers(VkCommandBuffer commands, MemoryStack stack) {
		VkMemoryBarrier2.Buffer barrier = VkMemoryBarrier2.calloc(1, stack).sType$Default();
		barrier.srcStageMask(VK13.VK_PIPELINE_STAGE_2_TRANSFER_BIT);
		barrier.srcAccessMask(VK13.VK_ACCESS_2_TRANSFER_WRITE_BIT);
		barrier.dstStageMask(VK13.VK_PIPELINE_STAGE_2_TRANSFER_BIT);
		barrier.dstAccessMask(VK13.VK_ACCESS_2_TRANSFER_READ_BIT
				| VK13.VK_ACCESS_2_TRANSFER_WRITE_BIT);
		VkDependencyInfo dependency = VkDependencyInfo.calloc(stack).sType$Default();
		dependency.pMemoryBarriers(barrier);
		KHRSynchronization2.vkCmdPipelineBarrier2KHR(commands, dependency);
	}
}
