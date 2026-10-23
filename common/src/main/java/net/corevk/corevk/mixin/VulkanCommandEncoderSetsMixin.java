package net.corevk.corevk.mixin;

import net.corevk.corevk.render.DescriptorSetPools;
import net.corevk.corevk.render.WideSamplerSets;

import com.mojang.blaze3d.vulkan.VulkanCommandEncoder;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import org.lwjgl.vulkan.VkWriteDescriptorSet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VulkanCommandEncoder.class)
public abstract class VulkanCommandEncoderSetsMixin implements WideSamplerSets.SetAllocator {

	@Shadow
	@Final
	private VulkanDevice device;

	@Shadow
	private long currentSubmitIndex;

	@Unique
	private DescriptorSetPools corevk$setPools;

	@Override
	public long corevk$allocateSet(long setLayout, VkWriteDescriptorSet.Buffer writes) {
		if (this.corevk$setPools == null) {
			this.corevk$setPools = new DescriptorSetPools(this.device,
					VulkanCommandEncoder.MAX_SUBMITS_IN_FLIGHT);
		}

		return this.corevk$setPools.allocate(corevk$slot(), setLayout, writes);
	}

	@Inject(method = "submit", require = 1,
			at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vulkan/VulkanCommandPool;reset()V"))
	private void corevk$resetSets(CallbackInfo callback) {
		if (this.corevk$setPools != null) {
			this.corevk$setPools.reset(corevk$slot());
		}
	}

	@Inject(method = "destroy", at = @At("TAIL"), require = 1)
	private void corevk$destroySets(CallbackInfo callback) {
		if (this.corevk$setPools != null) {
			this.corevk$setPools.destroy();
			this.corevk$setPools = null;
		}
	}

	@Unique
	private int corevk$slot() {
		return (int) (this.currentSubmitIndex % VulkanCommandEncoder.MAX_SUBMITS_IN_FLIGHT);
	}
}
