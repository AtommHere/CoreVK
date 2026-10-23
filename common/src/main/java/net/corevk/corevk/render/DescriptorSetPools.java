package net.corevk.corevk.render;

import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanUtils;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VK11;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;
import org.lwjgl.vulkan.VkWriteDescriptorSet;

import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;

public final class DescriptorSetPools {

	private static final int SETS_PER_POOL = 64;

	private static final int TYPES = VK10.VK_DESCRIPTOR_TYPE_INPUT_ATTACHMENT + 1;

	private final VulkanDevice device;

	private final List<List<Long>> slots = new ArrayList<>();

	private final int[] cursors;

	public DescriptorSetPools(VulkanDevice device, int slots) {
		this.device = device;
		this.cursors = new int[slots];
		for (int i = 0; i < slots; i++) {
			this.slots.add(new ArrayList<>());
		}
	}

	public long allocate(int slot, long setLayout, VkWriteDescriptorSet.Buffer writes) {
		List<Long> pools = this.slots.get(slot);
		try (MemoryStack stack = MemoryStack.stackPush()) {
			LongBuffer set = stack.callocLong(1);
			VkDescriptorSetAllocateInfo info = VkDescriptorSetAllocateInfo.calloc(stack)
					.sType$Default()
					.pSetLayouts(stack.longs(setLayout));
			while (true) {
				boolean fresh = this.cursors[slot] == pools.size();
				if (fresh) {
					pools.add(create(writes));
				}

				info.descriptorPool(pools.get(this.cursors[slot]));
				int result = VK10.vkAllocateDescriptorSets(this.device.vkDevice(), info, set);
				if (result == VK10.VK_SUCCESS) {
					return set.get(0);
				}

				boolean full = result == VK11.VK_ERROR_OUT_OF_POOL_MEMORY
						|| result == VK10.VK_ERROR_FRAGMENTED_POOL;
				if (!full || fresh) {
					String message = "Can't allocate a descriptor set";
					VulkanUtils.crashIfFailure(this.device, result, message);
					throw new IllegalStateException(VulkanUtils.resultToString(result) + ": " + message);
				}

				this.cursors[slot]++;
			}
		}
	}

	public void reset(int slot) {
		for (long pool : this.slots.get(slot)) {
			VK10.vkResetDescriptorPool(this.device.vkDevice(), pool, 0);
		}

		this.cursors[slot] = 0;
	}

	public void destroy() {
		for (int slot = 0; slot < this.slots.size(); slot++) {
			for (long pool : this.slots.get(slot)) {
				VK10.vkDestroyDescriptorPool(this.device.vkDevice(), pool, null);
			}

			this.slots.get(slot).clear();
			this.cursors[slot] = 0;
		}
	}

	private long create(VkWriteDescriptorSet.Buffer writes) {
		int[] counts = new int[TYPES];
		int kinds = 0;
		for (int i = writes.position(); i < writes.limit(); i++) {
			VkWriteDescriptorSet write = writes.get(i);
			int type = write.descriptorType();
			if (type < 0 || type >= TYPES) {
				throw new IllegalStateException("No pool size for descriptor type " + type);
			}

			if (counts[type] == 0) {
				kinds++;
			}

			counts[type] += write.descriptorCount();
		}

		try (MemoryStack stack = MemoryStack.stackPush()) {
			VkDescriptorPoolSize.Buffer sizes = VkDescriptorPoolSize.calloc(kinds, stack);
			int at = 0;
			for (int type = 0; type < TYPES; type++) {
				if (counts[type] > 0) {
					sizes.get(at).type(type).descriptorCount(counts[type] * SETS_PER_POOL);
					at++;
				}
			}

			VkDescriptorPoolCreateInfo info = VkDescriptorPoolCreateInfo.calloc(stack)
					.sType$Default()
					.maxSets(SETS_PER_POOL)
					.pPoolSizes(sizes);
			LongBuffer pool = stack.callocLong(1);
			VulkanUtils.crashIfFailure(this.device,
					VK10.vkCreateDescriptorPool(this.device.vkDevice(), info, null, pool),
					"Can't create a descriptor pool");

			return pool.get(0);
		}
	}
}
