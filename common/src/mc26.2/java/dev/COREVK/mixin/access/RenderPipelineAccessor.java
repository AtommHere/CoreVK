package net.corevk.corevk.mixin.access;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import org.jspecify.annotations.Nullable;

@Mixin(RenderPipeline.class)
public interface RenderPipelineAccessor {

	@Accessor("vertexFormatPerBuffer")
	@Nullable VertexFormat[] corevk$declaredFormats();
}
