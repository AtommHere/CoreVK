package net.corevk.corevk.mixin.sodium;

import net.corevk.corevk.sodium.SodiumPasses;
import net.corevk.corevk.pack.program.TerrainPass;
import net.corevk.corevk.render.LegacyTerrainFilter;
import net.corevk.corevk.render.TerrainDraw;
import net.corevk.corevk.render.TerrainSampler;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.caffeinemc.mods.sodium.client.gpu.device.backend.DrawBackend;
import net.caffeinemc.mods.sodium.client.render.chunk.ShaderChunkRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import net.minecraft.client.renderer.oit.OitStage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import org.jspecify.annotations.Nullable;

import java.util.Map;

@Mixin(value = ShaderChunkRenderer.class, remap = false)
public abstract class MixinShaderChunkRenderer {

	@Shadow
	protected VertexFormat vertexFormat;

	@Inject(method = "begin", at = @At("HEAD"))
	private void corevk$sampler(TerrainRenderPass pass, FogParameters parameters,
			GpuSampler terrainSampler, @Nullable OitStage stage, CallbackInfo callback) {
		TerrainDraw.sampler(terrainSampler);
	}

	@Inject(method = "compileProgram", at = @At("HEAD"), cancellable = true)
	private void corevk$terrain(TerrainRenderPass pass, @Nullable OitStage stage,
			CallbackInfoReturnable<RenderPipeline> callback) {

		if (DrawBackend.BACKEND == DrawBackend.OPENGL || stage != null) {
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
			at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;",
					ordinal = 0),
			require = 1)
	private Object corevk$ofThisFormat(Map<?, ?> memo, Object pass) {
		Object found = memo.get(pass);

		if (found instanceof RenderPipeline pipeline
				&& !this.vertexFormat.equals(pipeline.getVertexFormatBinding(0))) {
			return null;
		}

		return found;
	}
}
