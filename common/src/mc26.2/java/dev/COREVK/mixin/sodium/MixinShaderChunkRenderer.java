package net.corevk.corevk.mixin.sodium;

import net.corevk.corevk.sodium.SodiumPasses;
import net.corevk.corevk.pack.program.TerrainPass;
import net.corevk.corevk.render.LegacyTerrainFilter;
import net.corevk.corevk.render.TerrainDraw;
import net.corevk.corevk.render.TerrainSampler;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.caffeinemc.mods.sodium.client.gpu.device.backend.DrawBackend;
import net.caffeinemc.mods.sodium.client.render.chunk.ShaderChunkRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(value = ShaderChunkRenderer.class, remap = false)
public abstract class MixinShaderChunkRenderer {

	@Shadow
	protected VertexFormat vertexFormat;

	
	@Inject(method = "begin", at = @At("HEAD"))
	private void corevk$sampler(CallbackInfo callback,
			@Local(argsOnly = true) GpuSampler terrainSampler) {
		TerrainDraw.sampler(terrainSampler);
	}

	@Inject(method = "compileProgram", at = @At("HEAD"), cancellable = true)
	private void corevk$terrain(CallbackInfoReturnable<RenderPipeline> callback,
			@Local(argsOnly = true) TerrainRenderPass pass) {
		// The region offset arrives through push constants, which only the Vulkan backend pushes at
		// all: under OpenGL Sodium sets it as an ordinary uniform and our shader would read nothing.
		if (DrawBackend.BACKEND == DrawBackend.OPENGL) {
			return;
		}

		TerrainPass ours = SodiumPasses.of(pass);
		if (ours == null) {
			return;
		}

		RenderPipeline pipeline = TerrainDraw.pipeline(ours, this.vertexFormat, pass.getAtlas());
		if (pipeline != null) {
			callback.setReturnValue(pipeline);
		}
	}

	
	@Redirect(method = "compileProgram",
			at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"),
			require = 1)
	private Object corevk$ofThisFormat(Map<?, ?> memo, Object pass) {
		Object found = memo.get(pass);
		// A pipeline binding nothing at nought is no more usable than one binding another format, and
		// the renderer builds none: every road into this map goes through its own vertex binding.
		if (found instanceof RenderPipeline pipeline
				&& !this.vertexFormat.equals(pipeline.getVertexFormatBinding(0))) {
			return null;
		}

		return found;
	}
}
