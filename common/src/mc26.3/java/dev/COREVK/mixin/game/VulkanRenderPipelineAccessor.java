package net.corevk.corevk.mixin.game;

import com.mojang.renderpearl.backend.vulkan.VulkanRenderPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(VulkanRenderPipeline.class)
public interface VulkanRenderPipelineAccessor {

	@Accessor("descriptorSetLayout")
	long corevk$setLayout();
}
