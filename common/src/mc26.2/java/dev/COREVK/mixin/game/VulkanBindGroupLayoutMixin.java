package net.corevk.corevk.mixin.game;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vulkan.VulkanBindGroupLayout;
import com.mojang.blaze3d.vulkan.VulkanBindGroupLayout.Entry;
import net.corevk.corevk.pack.model.TargetName;
import net.corevk.corevk.pack.texture.CustomImages;
import net.corevk.corevk.render.GeometryStage;
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

@Mixin(VulkanBindGroupLayout.class)
public abstract class VulkanBindGroupLayoutMixin {

	@Unique
	private static final ThreadLocal<Entry> CURRENT = new ThreadLocal<>();

	@WrapOperation(method = "create", require = 1,
			at = @At(value = "INVOKE", target = "Ljava/util/List;get(I)Ljava/lang/Object;"))
	private static Object corevk$entry(List<?> entries, int index, Operation<Object> original) {
		Object entry = original.call(entries, index);
		if (entry instanceof Entry named) {
			CURRENT.set(named);
		}

		return entry;
	}

	@WrapOperation(method = "create", require = 1,
			at = @At(value = "INVOKE",
					target = "Lorg/lwjgl/vulkan/VkDescriptorSetLayoutBinding;descriptorType(I)"
							+ "Lorg/lwjgl/vulkan/VkDescriptorSetLayoutBinding;"))
	private static VkDescriptorSetLayoutBinding corevk$storageType(
			VkDescriptorSetLayoutBinding binding, int type, Operation<VkDescriptorSetLayoutBinding> original) {
		Entry entry = CURRENT.get();
		if (type == 1 && entry != null && (StorageImages.storageBinding(entry.name())
				|| CustomImages.storage(entry.name())
				|| TargetName.imageIndex(entry.name()).isPresent())) {
			type = 3;
		}

		if (type == 6 && entry != null && StorageBuffers.named(entry.name())) {
			type = 7;
		}

		return original.call(binding, type);
	}

	
	@WrapOperation(method = "create", require = 1,
			at = @At(value = "INVOKE",
					target = "Lorg/lwjgl/vulkan/VkDescriptorSetLayoutBinding;stageFlags(I)"
							+ "Lorg/lwjgl/vulkan/VkDescriptorSetLayoutBinding;"))
	private static VkDescriptorSetLayoutBinding corevk$geometryStage(
			VkDescriptorSetLayoutBinding binding, int flags,
			Operation<VkDescriptorSetLayoutBinding> original) {
		return original.call(binding,
				GeometryStage.buildingHere() ? flags | GeometryStage.STAGE_BIT : flags);
	}

	
	@WrapOperation(method = "create", require = 1,
			at = @At(value = "INVOKE",
					target = "Lorg/lwjgl/vulkan/VK12;vkCreateDescriptorSetLayout("
							+ "Lorg/lwjgl/vulkan/VkDevice;Lorg/lwjgl/vulkan/VkDescriptorSetLayoutCreateInfo;"
							+ "Lorg/lwjgl/vulkan/VkAllocationCallbacks;Ljava/nio/LongBuffer;)I"))
	private static int corevk$allocatedSets(VkDevice device, VkDescriptorSetLayoutCreateInfo info,
			VkAllocationCallbacks allocator, LongBuffer handle, Operation<Integer> original,
			@Local(argsOnly = true) String name) {
		boolean allocated = WideSamplerSets.dropPush(info, name);
		int result = original.call(device, info, allocator, handle);
		if (result == VK12.VK_SUCCESS) {
			WideSamplerSets.created(handle.get(0), allocated);
		}

		return result;
	}
}
