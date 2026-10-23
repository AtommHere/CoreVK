package net.corevk.corevk.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vulkan.VulkanBindGroupLayout;
import com.mojang.blaze3d.vulkan.VulkanCommandEncoder;
import com.mojang.blaze3d.vulkan.VulkanRenderPass;
import com.mojang.blaze3d.vulkan.VulkanRenderPipeline;
import net.corevk.corevk.pack.model.TargetName;
import net.corevk.corevk.render.PushedDescriptor;
import net.corevk.corevk.render.ShadowCompare;
import net.corevk.corevk.render.WideSamplerSets;
import net.corevk.corevk.render.storage.StorageBuffers;
import net.corevk.corevk.render.storage.StorageImages;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDescriptorBufferInfo;
import org.lwjgl.vulkan.VkDescriptorImageInfo;
import org.lwjgl.vulkan.VkWriteDescriptorSet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.Set;

@Mixin(VulkanRenderPass.class)
public abstract class VulkanRenderPassMixin {

	@Shadow
	protected VulkanRenderPipeline pipeline;

	@Shadow
	@Final
	private VulkanCommandEncoder encoder;

	
	@Unique
	private RenderPipeline corevk$comparedFor;

	@Unique
	private Set<String> corevk$compared = Set.of();

	@WrapOperation(method = "pushDescriptors", require = 1,
			at = @At(value = "INVOKE", target = "Ljava/util/List;get(I)Ljava/lang/Object;", ordinal = 0))
	private Object corevk$entry(List<?> entries, int index, Operation<Object> original) {
		Object entry = original.call(entries, index);
		if (entry instanceof VulkanBindGroupLayout.Entry named) {
			PushedDescriptor.begin(named);
		}

		return entry;
	}

	@WrapOperation(method = "pushDescriptors", require = 1,
			at = @At(value = "INVOKE",
					target = "Lorg/lwjgl/vulkan/VkDescriptorImageInfo$Buffer;imageView(J)"
							+ "Lorg/lwjgl/vulkan/VkDescriptorImageInfo$Buffer;"))
	private VkDescriptorImageInfo.Buffer corevk$view(VkDescriptorImageInfo.Buffer info, long view,
			Operation<VkDescriptorImageInfo.Buffer> original) {
		StorageImages.Bound bound = PushedDescriptor.current().image();
		if (bound != null) {
			view = bound.view();
		}

		return original.call(info, view);
	}

	@WrapOperation(method = "pushDescriptors", require = 1,
			at = @At(value = "INVOKE",
					target = "Lorg/lwjgl/vulkan/VkDescriptorImageInfo$Buffer;sampler(J)"
							+ "Lorg/lwjgl/vulkan/VkDescriptorImageInfo$Buffer;"))
	private VkDescriptorImageInfo.Buffer corevk$sampler(VkDescriptorImageInfo.Buffer info,
			long sampler, Operation<VkDescriptorImageInfo.Buffer> original) {
		PushedDescriptor pushed = PushedDescriptor.current();
		StorageImages.Bound bound = pushed.image();
		if ((bound != null && bound.storage())
				|| (pushed.entry() != null && TargetName.imageIndex(pushed.entry().name()).isPresent())) {
			sampler = 0L;
		} else if (ShadowCompare.noted()) {
			// Behind the one flag: until the first pack that compares is loaded, every pass of the
			// game's own pays a volatile read here and nothing else. Once one has been, the flag
			// stays up for the session and the per-name lookup is the price of having the road.
			VulkanBindGroupLayout.Entry entry = pushed.entry();
			if (entry != null && this.pipeline != null && corevk$compared().contains(entry.name())) {
				sampler = ShadowCompare.sampler(this.pipeline.device());
			}
		}

		return original.call(info, sampler);
	}

	@WrapOperation(method = "pushDescriptors", require = 1,
			at = @At(value = "INVOKE",
					target = "Lorg/lwjgl/vulkan/VkDescriptorBufferInfo$Buffer;buffer(J)"
							+ "Lorg/lwjgl/vulkan/VkDescriptorBufferInfo$Buffer;"))
	private VkDescriptorBufferInfo.Buffer corevk$buffer(VkDescriptorBufferInfo.Buffer info,
			long buffer, Operation<VkDescriptorBufferInfo.Buffer> original) {
		StorageBuffers.Bound bound = PushedDescriptor.current().buffer();
		if (bound != null) {
			buffer = bound.buffer();
		}

		return original.call(info, buffer);
	}

	@WrapOperation(method = "pushDescriptors", require = 1,
			at = @At(value = "INVOKE",
					target = "Lorg/lwjgl/vulkan/VkDescriptorBufferInfo$Buffer;range(J)"
							+ "Lorg/lwjgl/vulkan/VkDescriptorBufferInfo$Buffer;"))
	private VkDescriptorBufferInfo.Buffer corevk$range(VkDescriptorBufferInfo.Buffer info,
			long range, Operation<VkDescriptorBufferInfo.Buffer> original) {
		StorageBuffers.Bound bound = PushedDescriptor.current().buffer();
		if (bound != null) {
			range = bound.range();
		}

		return original.call(info, range);
	}

	@WrapOperation(method = "pushDescriptors", require = 1,
			at = @At(value = "INVOKE",
					target = "Lorg/lwjgl/vulkan/VkWriteDescriptorSet;descriptorType(I)"
							+ "Lorg/lwjgl/vulkan/VkWriteDescriptorSet;"))
	private VkWriteDescriptorSet corevk$type(VkWriteDescriptorSet set, int type,
			Operation<VkWriteDescriptorSet> original) {
		PushedDescriptor pushed = PushedDescriptor.current();
		StorageImages.Bound image = pushed.image();
		if (type == 1 && ((image != null && image.storage())
				|| (pushed.entry() != null && TargetName.imageIndex(pushed.entry().name()).isPresent()))) {
			type = 3;
		}

		if (type == 6 && pushed.buffer() != null) {
			type = 7;
		}

		return original.call(set, type);
	}

	
	@WrapOperation(method = "pushDescriptors", require = 1,
			at = @At(value = "INVOKE",
					target = "Lorg/lwjgl/vulkan/KHRPushDescriptor;vkCmdPushDescriptorSetKHR("
							+ "Lorg/lwjgl/vulkan/VkCommandBuffer;IJI"
							+ "Lorg/lwjgl/vulkan/VkWriteDescriptorSet$Buffer;)V"))
	private void corevk$pushOrBind(VkCommandBuffer commands, int bindPoint, long layout, int set,
			VkWriteDescriptorSet.Buffer writes, Operation<Void> original) {
		long setLayout = this.pipeline == null ? 0L : this.pipeline.layout().handle();
		if (WideSamplerSets.allocated(setLayout)) {
			WideSamplerSets.bind(this.encoder, commands, bindPoint, layout, set, setLayout, writes);
			return;
		}

		original.call(commands, bindPoint, layout, set, writes);
	}

	@Unique
	private Set<String> corevk$compared() {
		RenderPipeline info = this.pipeline.info();
		if (info != this.corevk$comparedFor) {
			this.corevk$comparedFor = info;
			this.corevk$compared = ShadowCompare.compared(info);
		}

		return this.corevk$compared;
	}
}
