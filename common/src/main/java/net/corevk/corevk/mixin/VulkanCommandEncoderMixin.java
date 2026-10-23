package net.corevk.corevk.mixin;

import net.corevk.corevk.render.MipmapCommands;
import net.corevk.corevk.render.timing.PassBarrier;
import net.corevk.corevk.CoreVK;

import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.vulkan.VulkanCommandEncoder;
import com.mojang.blaze3d.vulkan.VulkanConst;
import com.mojang.blaze3d.vulkan.VulkanGpuTexture;
import com.mojang.blaze3d.vulkan.VulkanRenderPass;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRSynchronization2;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDependencyInfo;
import org.lwjgl.vulkan.VkImageBlit;
import org.lwjgl.vulkan.VkImageMemoryBarrier2;
import org.lwjgl.vulkan.VkImageSubresourceRange;
import org.lwjgl.vulkan.VkMemoryBarrier2;
import org.lwjgl.vulkan.VkViewport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

@Mixin(VulkanCommandEncoder.class)
public abstract class VulkanCommandEncoderMixin implements MipmapCommands {

	@Shadow
	private VulkanRenderPass currentRenderPass;

	@Invoker("commandBuffer")
	abstract VkCommandBuffer corevk$commandBuffer();

	@Unique
	private Supplier<String> corevk$closingLabel;

	@Inject(method = "submitRenderPass", at = @At("HEAD"), require = 1)
	private void corevk$rememberLabel(CallbackInfo ci) {
		this.corevk$closingLabel = this.currentRenderPass == null
				? null
				: this.currentRenderPass.getLabel();
	}

	@Redirect(method = "submitRenderPass", at = @At(value = "INVOKE",
			target = "Lcom/mojang/blaze3d/vulkan/VulkanCommandEncoder;memoryBarrier("
					+ "Lorg/lwjgl/system/MemoryStack;)V"),
			require = 1)
	private void corevk$afterPass(VulkanCommandEncoder self, MemoryStack stack) {
		Supplier<String> label = this.corevk$closingLabel;
		this.corevk$closingLabel = null;
		VkCommandBuffer commands = corevk$commandBuffer();

		if (label != null && !PassBarrier.full() && corevk$ours(label)) {
			corevk$framebufferBarrier(commands, stack);
		} else {
			VulkanCommandEncoder.memoryBarrier(commands, stack);
		}
	}

	@Unique
	private static boolean corevk$ours(Supplier<String> label) {
		String name = label.get();
		return name != null && name.startsWith("CoreVK");
	}

	@Unique
	private static void corevk$framebufferBarrier(VkCommandBuffer commands, MemoryStack stack) {
		VkMemoryBarrier2.Buffer barrier = VkMemoryBarrier2.calloc(1, stack)
				.sType$Default()

				.srcStageMask(KHRSynchronization2.VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT_KHR
						| KHRSynchronization2.VK_PIPELINE_STAGE_2_LATE_FRAGMENT_TESTS_BIT_KHR
						| KHRSynchronization2.VK_PIPELINE_STAGE_2_VERTEX_SHADER_BIT_KHR
						| KHRSynchronization2.VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT_KHR)
				.srcAccessMask(KHRSynchronization2.VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT_KHR
						| KHRSynchronization2.VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT_KHR
						| KHRSynchronization2.VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT_KHR)

				.dstStageMask(KHRSynchronization2.VK_PIPELINE_STAGE_2_VERTEX_SHADER_BIT_KHR
						| KHRSynchronization2.VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT_KHR
						| KHRSynchronization2.VK_PIPELINE_STAGE_2_EARLY_FRAGMENT_TESTS_BIT_KHR
						| KHRSynchronization2.VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT_KHR
						| KHRSynchronization2.VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT_KHR
						| KHRSynchronization2.VK_PIPELINE_STAGE_2_ALL_TRANSFER_BIT_KHR)

				.dstAccessMask(KHRSynchronization2.VK_ACCESS_2_SHADER_SAMPLED_READ_BIT_KHR
						| KHRSynchronization2.VK_ACCESS_2_SHADER_STORAGE_READ_BIT_KHR
						| KHRSynchronization2.VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT_KHR
						| KHRSynchronization2.VK_ACCESS_2_COLOR_ATTACHMENT_READ_BIT_KHR
						| KHRSynchronization2.VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT_KHR
						| KHRSynchronization2.VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_READ_BIT_KHR
						| KHRSynchronization2.VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT_KHR
						| KHRSynchronization2.VK_ACCESS_2_TRANSFER_READ_BIT_KHR
						| KHRSynchronization2.VK_ACCESS_2_TRANSFER_WRITE_BIT_KHR);
		VkDependencyInfo info = VkDependencyInfo.calloc(stack)
				.sType$Default()
				.pMemoryBarriers(barrier);
		KHRSynchronization2.vkCmdPipelineBarrier2KHR(commands, info);
	}

	@Override
	public boolean corevk$generateMipmaps(GpuTexture texture) {
		if (this.currentRenderPass != null || !(texture instanceof VulkanGpuTexture image)) {
			return false;
		}

		int levels = texture.getMipLevels();
		if (levels <= 1 || texture.isClosed()) {
			return false;
		}

		int aspect = VulkanConst.formatAspectMask(texture.getFormat());

		boolean colour = (aspect & VK10.VK_IMAGE_ASPECT_COLOR_BIT) != 0;
		int filter = colour && !integer(texture) ? VK10.VK_FILTER_LINEAR : VK10.VK_FILTER_NEAREST;
		long vkImage = image.vkImage();

		boolean wide = PassBarrier.full();

		try (MemoryStack stack = MemoryStack.stackPush()) {
			VkCommandBuffer commands = corevk$commandBuffer();
			for (int level = 1; level < levels; level++) {
				int srcWidth = Math.max(1, texture.getWidth(level - 1));
				int srcHeight = Math.max(1, texture.getHeight(level - 1));
				int dstWidth = Math.max(1, texture.getWidth(level));
				int dstHeight = Math.max(1, texture.getHeight(level));

				VkImageBlit.Buffer region = VkImageBlit.calloc(1, stack);
				region.srcSubresource()
						.aspectMask(aspect)
						.mipLevel(level - 1)
						.baseArrayLayer(0)
						.layerCount(1);
				region.dstSubresource()
						.aspectMask(aspect)
						.mipLevel(level)
						.baseArrayLayer(0)
						.layerCount(1);
				region.srcOffsets(0).set(0, 0, 0);
				region.srcOffsets(1).set(srcWidth, srcHeight, 1);
				region.dstOffsets(0).set(0, 0, 0);
				region.dstOffsets(1).set(dstWidth, dstHeight, 1);

				VK12.vkCmdBlitImage(commands, vkImage, VK10.VK_IMAGE_LAYOUT_GENERAL, vkImage,
						VK10.VK_IMAGE_LAYOUT_GENERAL, region, filter);

				if (wide) {
					VulkanCommandEncoder.memoryBarrier(commands, stack);
				} else if (level + 1 < levels) {
					corevk$transferBarrier(commands, stack, vkImage, aspect, level);
				}
			}

			if (!wide) {
				corevk$chainTailBarrier(commands, stack);
			}
		}

		return true;
	}

	@Override
	public boolean corevk$viewport(int width, int height) {
		if (this.currentRenderPass == null || width <= 0 || height <= 0) {
			return false;
		}

		try (MemoryStack stack = MemoryStack.stackPush()) {
			VkViewport.Buffer viewport = VkViewport.calloc(1, stack)
					.x(0.0F)
					.y(0.0F)
					.width(width)
					.height(height)
					.minDepth(0.0F)
					.maxDepth(1.0F);
			VK12.vkCmdSetViewport(corevk$commandBuffer(), 0, viewport);
		}

		return true;
	}

	@Unique
	private static void corevk$transferBarrier(VkCommandBuffer commands, MemoryStack stack,
			long image, int aspect, int mip) {
		VkImageSubresourceRange range = VkImageSubresourceRange.calloc(stack)
				.aspectMask(aspect)
				.baseMipLevel(mip)
				.levelCount(1)
				.baseArrayLayer(0)
				.layerCount(1);
		VkImageMemoryBarrier2.Buffer barrier = VkImageMemoryBarrier2.calloc(1, stack)
				.sType$Default()
				.srcStageMask(KHRSynchronization2.VK_PIPELINE_STAGE_2_ALL_TRANSFER_BIT_KHR)
				.srcAccessMask(KHRSynchronization2.VK_ACCESS_2_TRANSFER_WRITE_BIT_KHR)
				.dstStageMask(KHRSynchronization2.VK_PIPELINE_STAGE_2_ALL_TRANSFER_BIT_KHR)
				.dstAccessMask(KHRSynchronization2.VK_ACCESS_2_TRANSFER_READ_BIT_KHR)
				.oldLayout(VK10.VK_IMAGE_LAYOUT_GENERAL)
				.newLayout(VK10.VK_IMAGE_LAYOUT_GENERAL)
				.srcQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
				.dstQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
				.image(image)
				.subresourceRange(range);
		VkDependencyInfo info = VkDependencyInfo.calloc(stack)
				.sType$Default()
				.pImageMemoryBarriers(barrier);
		KHRSynchronization2.vkCmdPipelineBarrier2KHR(commands, info);
	}

	@Unique
	private static void corevk$chainTailBarrier(VkCommandBuffer commands, MemoryStack stack) {
		VkMemoryBarrier2.Buffer barrier = VkMemoryBarrier2.calloc(1, stack)
				.sType$Default()
				.srcStageMask(KHRSynchronization2.VK_PIPELINE_STAGE_2_ALL_TRANSFER_BIT_KHR)
				.srcAccessMask(KHRSynchronization2.VK_ACCESS_2_TRANSFER_WRITE_BIT_KHR)
				.dstStageMask(KHRSynchronization2.VK_PIPELINE_STAGE_2_VERTEX_SHADER_BIT_KHR
						| KHRSynchronization2.VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT_KHR
						| KHRSynchronization2.VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT_KHR
						| KHRSynchronization2.VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT_KHR
						| KHRSynchronization2.VK_PIPELINE_STAGE_2_EARLY_FRAGMENT_TESTS_BIT_KHR
						| KHRSynchronization2.VK_PIPELINE_STAGE_2_LATE_FRAGMENT_TESTS_BIT_KHR
						| KHRSynchronization2.VK_PIPELINE_STAGE_2_ALL_TRANSFER_BIT_KHR)
				.dstAccessMask(KHRSynchronization2.VK_ACCESS_2_SHADER_SAMPLED_READ_BIT_KHR
						| KHRSynchronization2.VK_ACCESS_2_SHADER_STORAGE_READ_BIT_KHR
						| KHRSynchronization2.VK_ACCESS_2_COLOR_ATTACHMENT_READ_BIT_KHR
						| KHRSynchronization2.VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT_KHR
						| KHRSynchronization2.VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_READ_BIT_KHR
						| KHRSynchronization2.VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT_KHR
						| KHRSynchronization2.VK_ACCESS_2_TRANSFER_READ_BIT_KHR
						| KHRSynchronization2.VK_ACCESS_2_TRANSFER_WRITE_BIT_KHR);
		VkDependencyInfo info = VkDependencyInfo.calloc(stack)
				.sType$Default()
				.pMemoryBarriers(barrier);
		KHRSynchronization2.vkCmdPipelineBarrier2KHR(commands, info);
	}

	@Unique
	private static boolean integer(GpuTexture texture) {
		return switch (texture.getFormat().componentType()) {
			case UINT_8, SINT_8, UINT_16, SINT_16, UINT_32, SINT_32 -> true;
			default -> false;
		};
	}
}
