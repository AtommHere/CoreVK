package net.corevk.corevk.mixin;

import net.corevk.corevk.render.GeometryHold;
import net.corevk.corevk.render.ParticleDraw;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderPass.class)
public abstract class RenderPassMixin {

	@Inject(method = "close", at = @At("HEAD"), cancellable = true, require = 1)
	private void corevk$keep(CallbackInfo callback) {
		if (GeometryHold.keep((RenderPass) (Object) this)) {
			callback.cancel();
		}
	}

	@ModifyVariable(method = "setPipeline", at = @At("HEAD"), argsOnly = true, require = 1)
	private RenderPipeline corevk$particlePipeline(RenderPipeline pipeline) {
		return ParticleDraw.pipeline((RenderPass) (Object) this, pipeline);
	}

	@Inject(method = "bindTexture", at = @At("TAIL"), require = 1)
	private void corevk$particleAtlas(String name, GpuTextureView view, GpuSampler sampler,
			CallbackInfo callback) {
		ParticleDraw.texture((RenderPass) (Object) this, name, view, sampler);
	}
}
