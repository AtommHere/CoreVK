package net.corevk.corevk.mixin.game;

import com.mojang.blaze3d.pipeline.PipelineCache;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.corevk.corevk.render.GraphicsApi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import org.jspecify.annotations.Nullable;

@Mixin(RenderSystem.class)
public abstract class RenderSystemMixin {

	@Inject(method = "setCurrentPipelineCache", at = @At("HEAD"), require = 1)
	private static void corevk$purge(PipelineCache cache,
			CallbackInfoReturnable<@Nullable PipelineCache> callback) {
		GraphicsApi.purge();
	}

	
	@Inject(method = "getCompiledPipelineNullable", at = @At("HEAD"), cancellable = true,
			require = 1)
	private static void corevk$ours(RenderPipeline pipeline,
			CallbackInfoReturnable<@Nullable CompiledRenderPipeline> callback) {
		CompiledRenderPipeline ours = GraphicsApi.held(pipeline);
		if (ours != null) {
			callback.setReturnValue(ours);
		}
	}

	
	@Inject(method = "getCompiledPipelineNullable", at = @At("RETURN"), require = 1)
	private static void corevk$note(RenderPipeline pipeline,
			CallbackInfoReturnable<@Nullable CompiledRenderPipeline> callback) {
		GraphicsApi.noteGameCompiled(pipeline, callback.getReturnValue());
	}
}
