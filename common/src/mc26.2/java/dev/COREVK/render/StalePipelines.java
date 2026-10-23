package net.corevk.corevk.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vulkan.VulkanRenderPipeline;

import java.util.List;

public interface StalePipelines {

	
	List<RenderPipeline> corevk$dropEntityPipelines();

	
	boolean corevk$adopt(RenderPipeline pipeline, VulkanRenderPipeline compiled);
}
