package net.corevk.corevk.mixin.sodium;

import net.corevk.corevk.render.TerrainDraw;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import net.caffeinemc.mods.sodium.client.gpu.device.context.VKDrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = VKDrawContext.class, remap = false)
public abstract class VKDrawContextMixin {

	@Inject(method = "setContext", at = @At("HEAD"))
	private void corevk$bind(RenderPass pass, RenderPipeline pipeline, CallbackInfo callback) {
		TerrainDraw.bind(pass, pipeline);
	}
}
