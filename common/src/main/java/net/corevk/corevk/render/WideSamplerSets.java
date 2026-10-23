package net.corevk.corevk.render;

import net.corevk.corevk.CoreVK;

import com.mojang.blaze3d.vulkan.VulkanCommandEncoder;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.lwjgl.vulkan.VkWriteDescriptorSet;

import java.nio.IntBuffer;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class WideSamplerSets {

	
	private static final int PUSH = 1;

	
	private static final int[] STAGES = {
			VK10.VK_SHADER_STAGE_VERTEX_BIT,
			VK10.VK_SHADER_STAGE_GEOMETRY_BIT,
			VK10.VK_SHADER_STAGE_FRAGMENT_BIT,
			VK10.VK_SHADER_STAGE_COMPUTE_BIT,
	};

	
	private static final Set<Long> ALLOCATED = ConcurrentHashMap.newKeySet();

	
	private static final Set<String> SAID = ConcurrentHashMap.newKeySet();

	private static volatile boolean moltenVk;

	private static volatile int perStageSamplers = Integer.MAX_VALUE;

	private static volatile int tableSamplers;

	
	private static volatile boolean any;

	private WideSamplerSets() {
	}

	
	public static void serve(boolean moltenVkDriver, int maxPerStageSamplers, int maxTableSamplers) {
		perStageSamplers = maxPerStageSamplers;
		tableSamplers = maxTableSamplers;
		moltenVk = moltenVkDriver;
	}

	
	public static boolean dropPush(VkDescriptorSetLayoutCreateInfo info, String name) {
		if (!moltenVk || (info.flags() & PUSH) == 0) {
			return false;
		}

		int widest = widestStage(info.pBindings());
		if (widest <= perStageSamplers) {
			return false;
		}

		if (widest > tableSamplers) {
			if (SAID.add(name)) {
				CoreVK.logger().warn("{} reads {} samplers in one stage, past the {} MoltenVK can push, "
						+ "and this device binds sets through no argument buffer taking more, so it stays "
						+ "pushed and Metal refuses it", name, widest, perStageSamplers);
			}

			return false;
		}

		info.flags(info.flags() & ~PUSH);
		if (SAID.add(name)) {
			CoreVK.logger().info("{} reads {} samplers in one stage, past the {} MoltenVK can push, so "
					+ "its descriptor set is allocated and bound through an argument buffer", name, widest,
					perStageSamplers);
		}

		return true;
	}

	
	public static void created(long handle, boolean allocated) {
		if (allocated) {
			ALLOCATED.add(handle);
			any = true;
		} else if (any) {
			ALLOCATED.remove(handle);
		}
	}

	
	public static boolean allocated(long setLayout) {
		return any && ALLOCATED.contains(setLayout);
	}

	
	public static void bind(VulkanCommandEncoder encoder, VkCommandBuffer commands, int bindPoint,
			long pipelineLayout, int set, long setLayout, VkWriteDescriptorSet.Buffer writes) {
		long allocated = ((SetAllocator) encoder).corevk$allocateSet(setLayout, writes);
		for (int i = writes.position(); i < writes.limit(); i++) {
			writes.get(i).dstSet(allocated);
		}

		VK10.vkUpdateDescriptorSets(commands.getDevice(), writes, null);
		try (MemoryStack stack = MemoryStack.stackPush()) {
			VK10.vkCmdBindDescriptorSets(commands, bindPoint, pipelineLayout, set,
					stack.longs(allocated), (IntBuffer) null);
		}
	}

	
	private static int widestStage(VkDescriptorSetLayoutBinding.Buffer bindings) {
		if (bindings == null) {
			return 0;
		}

		int widest = 0;
		for (int stage : STAGES) {
			int held = 0;
			for (int i = bindings.position(); i < bindings.limit(); i++) {
				VkDescriptorSetLayoutBinding binding = bindings.get(i);
				if (binding.descriptorType() == VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER
						&& (binding.stageFlags() & stage) != 0) {
					held += binding.descriptorCount();
				}
			}

			widest = Math.max(widest, held);
		}

		return widest;
	}

	
	public interface SetAllocator {

		
		long corevk$allocateSet(long setLayout, VkWriteDescriptorSet.Buffer writes);
	}
}
