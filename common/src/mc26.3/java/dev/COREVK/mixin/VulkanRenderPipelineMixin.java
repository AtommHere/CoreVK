package net.corevk.corevk.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.backend.api.BackendRenderPipeline;
import com.mojang.renderpearl.backend.vulkan.VulkanRenderPipeline;
import net.corevk.corevk.pack.model.TargetName;
import net.corevk.corevk.pack.texture.CustomImages;
import net.corevk.corevk.render.WideSamplerSets;
import net.corevk.corevk.render.storage.StorageBuffers;
import net.corevk.corevk.render.storage.StorageImages;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkAllocationCallbacks;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.nio.LongBuffer;
import java.util.List;

@Mixin(VulkanRenderPipeline.class)
public abstract class VulkanRenderPipelineMixin {

	
	@Unique
	private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

	@WrapOperation(method = "compile", require = 1,
			at = @At(value = "INVOKE", target = "Ljava/util/List;get(I)Ljava/lang/Object;",
					ordinal = 0))
	private static Object corevk$uniform(List<?> uniforms, int index, Operation<Object> original) {
		Object uniform = original.call(uniforms, index);
		if (uniform instanceof BindGroupLayout.UniformDescription described) {
			CURRENT.set(described.name());
		}

		return uniform;
	}

	@WrapOperation(method = "compile", require = 1,
			at = @At(value = "INVOKE",
					target = "Lorg/lwjgl/vulkan/VkDescriptorSetLayoutBinding;descriptorType(I)"
							+ "Lorg/lwjgl/vulkan/VkDescriptorSetLayoutBinding;"))
	private static VkDescriptorSetLayoutBinding corevk$storageType(
			VkDescriptorSetLayoutBinding binding, int type,
			Operation<VkDescriptorSetLayoutBinding> original) {
		String name = CURRENT.get();
		if (type == VK12.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER && name != null
				&& (StorageImages.storageBinding(name) || CustomImages.storage(name)
						|| TargetName.imageIndex(name).isPresent())) {
			type = VK12.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE;
		}

		if (type == VK12.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER && name != null
				&& StorageBuffers.named(name)) {
			type = VK12.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
		}

		return original.call(binding, type);
	}

	
	@WrapOperation(method = "compile", require = 1,
			at = @At(value = "INVOKE",
					target = "Lorg/lwjgl/vulkan/VK12;vkCreateDescriptorSetLayout("
							+ "Lorg/lwjgl/vulkan/VkDevice;Lorg/lwjgl/vulkan/VkDescriptorSetLayoutCreateInfo;"
							+ "Lorg/lwjgl/vulkan/VkAllocationCallbacks;Ljava/nio/LongBuffer;)I"))
	private static int corevk$allocatedSets(VkDevice device, VkDescriptorSetLayoutCreateInfo info,
			VkAllocationCallbacks allocator, LongBuffer handle, Operation<Integer> original,
			@Local(argsOnly = true) BackendRenderPipeline.CreateInfo pipeline) {
		CURRENT.remove();
		boolean allocated = WideSamplerSets.dropPush(info, pipeline.name());
		int result = original.call(device, info, allocator, handle);
		if (result == VK12.VK_SUCCESS) {
			WideSamplerSets.created(handle.get(0), allocated);
		}

		return result;
	}
}
