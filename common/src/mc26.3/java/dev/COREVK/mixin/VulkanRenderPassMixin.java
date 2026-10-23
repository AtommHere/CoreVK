package net.corevk.corevk.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.backend.vulkan.VulkanCommandEncoder;
import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import com.mojang.renderpearl.backend.vulkan.VulkanRenderPass;
import com.mojang.renderpearl.backend.vulkan.VulkanRenderPipeline;
import net.corevk.corevk.mixin.game.VulkanRenderPipelineAccessor;
import net.corevk.corevk.pack.model.TargetName;
import net.corevk.corevk.render.GraphicsApi;
import net.corevk.corevk.render.PushedDescriptor;
import net.corevk.corevk.render.ShadowCompare;
import net.corevk.corevk.render.WideSamplerSets;
import net.corevk.corevk.render.storage.StorageBuffers;
import net.corevk.corevk.render.storage.StorageImages;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDescriptorBufferInfo;
import org.lwjgl.vulkan.VkDescriptorImageInfo;
import org.lwjgl.vulkan.VkWriteDescriptorSet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;

@Mixin(VulkanRenderPass.class)
public abstract class VulkanRenderPassMixin {

	@Shadow
	protected VulkanRenderPipeline pipeline;

	@Shadow
	@Final
	private VulkanCommandEncoder encoder;

	@Shadow
	@Final
	private VulkanDevice device;

	
	@Unique
	private @Nullable VulkanRenderPipeline corevk$comparedFor;

	@Unique
	private Set<String> corevk$compared = Set.of();

	@WrapOperation(method = "pushDescriptors", require = 1,
			at = @At(value = "INVOKE", target = "Ljava/util/List;get(I)Ljava/lang/Object;", ordinal = 0))
	private Object corevk$entry(List<?> uniforms, int index, Operation<Object> original) {
		Object uniform = original.call(uniforms, index);
		if (uniform instanceof BindGroupLayout.UniformDescription described) {
			PushedDescriptor.begin(described.name());
		}

		return uniform;
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
		String name = pushed.name();
		if ((bound != null && bound.storage())
				|| (name != null && TargetName.imageIndex(name).isPresent())) {
			sampler = 0L;
		} else if (ShadowCompare.noted() && name != null && corevk$compared().contains(name)) {
			// Behind the one flag, as on 26.2: until the first pack that compares is loaded, every
			// pass of the game's own pays a volatile read here and nothing else.
			sampler = ShadowCompare.sampler(this.device);
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

	@WrapOperation(method = "pushDescriptors", require = 3,
			at = @At(value = "INVOKE",
					target = "Lorg/lwjgl/vulkan/VkWriteDescriptorSet;descriptorType(I)"
							+ "Lorg/lwjgl/vulkan/VkWriteDescriptorSet;"))
	private VkWriteDescriptorSet corevk$type(VkWriteDescriptorSet set, int type,
			Operation<VkWriteDescriptorSet> original) {
		PushedDescriptor pushed = PushedDescriptor.current();
		StorageImages.Bound image = pushed.image();
		String name = pushed.name();
		if (type == VK12.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER
				&& ((image != null && image.storage())
						|| (name != null && TargetName.imageIndex(name).isPresent()))) {
			type = VK12.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE;
		}

		if (type == VK12.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER && pushed.buffer() != null) {
			type = VK12.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
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
		long setLayout = this.pipeline == null ? 0L
				: ((VulkanRenderPipelineAccessor) (Object) this.pipeline).corevk$setLayout();
		if (WideSamplerSets.allocated(setLayout)) {
			WideSamplerSets.bind(this.encoder, commands, bindPoint, layout, set, setLayout, writes);
			return;
		}

		original.call(commands, bindPoint, layout, set, writes);
	}

	@Unique
	private Set<String> corevk$compared() {
		if (this.pipeline != this.corevk$comparedFor) {
			this.corevk$comparedFor = this.pipeline;
			RenderPipeline described = GraphicsApi.describing(this.pipeline);
			this.corevk$compared = described == null ? Set.of() : ShadowCompare.compared(described);
		}

		return this.corevk$compared;
	}
}
