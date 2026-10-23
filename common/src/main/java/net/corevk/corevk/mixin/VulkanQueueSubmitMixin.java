package net.corevk.corevk.mixin;

import net.corevk.corevk.render.timing.PassTimings;

import com.mojang.blaze3d.vulkan.VulkanQueue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VulkanQueue.Submission.class)
public abstract class VulkanQueueSubmitMixin {

	@Inject(method = "close",
			at = @At(value = "INVOKE",
					target = "Lorg/lwjgl/vulkan/KHRSynchronization2;vkQueueSubmit2KHR("
							+ "Lorg/lwjgl/vulkan/VkQueue;"
							+ "Lorg/lwjgl/vulkan/VkSubmitInfo2$Buffer;J)I"),
			require = 1)
	private void corevk$countSubmit(CallbackInfo callback) {
		PassTimings.censusSubmit();
	}
}
