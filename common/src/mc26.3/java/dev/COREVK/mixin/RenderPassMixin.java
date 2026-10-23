package net.corevk.corevk.mixin;

import net.corevk.corevk.render.GeometryHold;
import net.corevk.corevk.render.GraphicsApi;
import net.corevk.corevk.render.ParticleDraw;

import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.frontend.FrontendRenderPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FrontendRenderPass.class)
public abstract class RenderPassMixin {

	@Inject(method = "close", at = @At("HEAD"), cancellable = true, require = 1)
	private void corevk$keep(CallbackInfo callback) {
		if (GeometryHold.keep((RenderPass) (Object) this)) {
			callback.cancel();
		}
	}

	@ModifyVariable(method = "setPipeline", at = @At("HEAD"), argsOnly = true, require = 1)
	private CompiledRenderPipeline corevk$particlePipeline(CompiledRenderPipeline compiled) {
		RenderPipeline game = GraphicsApi.descriptionOf(compiled);
		if (game == null) {
			return compiled;
		}

		RenderPipeline chosen = ParticleDraw.pipeline((RenderPass) (Object) this, game);
		if (chosen == game) {
			return compiled;
		}

		CompiledRenderPipeline ours = GraphicsApi.compiledFor(chosen);
		return ours == null ? compiled : ours;
	}

	@Inject(method = "setUniform(Ljava/lang/String;"
			+ "Lcom/mojang/renderpearl/api/textures/GpuTextureView;"
			+ "Lcom/mojang/renderpearl/api/textures/GpuSampler;)V", at = @At("TAIL"), require = 1)
	private void corevk$particleAtlas(String name, GpuTextureView view, GpuSampler sampler,
			CallbackInfo callback) {
		ParticleDraw.texture((RenderPass) (Object) this, name, view, sampler);
	}
}
