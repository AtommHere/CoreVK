package net.corevk.corevk.mixin.access;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import org.jspecify.annotations.Nullable;

import java.util.List;

@Mixin(RenderPipeline.class)
public interface RenderPipelineAccessor {

	@Accessor("vertexFormatPerBuffer")
	List<@Nullable VertexFormat> corevk$declaredFormats();
}
