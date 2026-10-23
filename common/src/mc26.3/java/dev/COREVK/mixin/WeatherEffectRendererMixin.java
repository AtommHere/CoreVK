package net.corevk.corevk.mixin;

import net.corevk.corevk.render.GamePipelines;
import net.corevk.corevk.render.GeometryHold;
import net.corevk.corevk.render.GraphicsApi;
import net.corevk.corevk.render.LevelPass;
import net.corevk.corevk.render.WeatherDraw;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.state.level.WeatherRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import org.jspecify.annotations.Nullable;

@Mixin(WeatherEffectRenderer.class)
public abstract class WeatherEffectRendererMixin {

	@Unique
	private @Nullable RenderPipeline corevk$pipeline;

	@Unique
	private @Nullable RenderPass corevk$opened;

	@Inject(method = "render(Lnet/minecraft/client/renderer/state/level/WeatherRenderState;"
			+ "Lcom/mojang/renderpearl/api/commands/RenderPass;"
			+ "Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;)V",
			require = 1, at = @At("HEAD"), cancellable = true)
	private void corevk$refuse(CallbackInfo callback) {
		if (!WeatherDraw.draws()) {
			callback.cancel();
		}
	}

	@ModifyVariable(method = "render(Lnet/minecraft/client/renderer/state/level/WeatherRenderState;"
			+ "Lcom/mojang/renderpearl/api/commands/RenderPass;"
			+ "Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;)V",
			require = 1, argsOnly = true, at = @At(value = "LOAD", ordinal = 0))
	private RenderPass corevk$open(RenderPass pass, WeatherRenderState state, RenderPass handed,
			RenderPipeline game) {
		this.corevk$pipeline = null;
		this.corevk$opened = null;

		if (!(pass instanceof LevelPass level)) {
			return pass;
		}

		RenderPipeline asked = WeatherDraw.depth() ? GamePipelines.weatherDepthWrite() : game;
		this.corevk$pipeline = WeatherDraw.element(asked, level.colour(), level.depth());
		RenderPassDescriptor descriptor =
				this.corevk$pipeline == null ? null : WeatherDraw.descriptor();
		if (descriptor == null) {
			return pass;
		}

		this.corevk$opened = GeometryHold.open(RenderSystem.getDevice().createCommandEncoder(),
				descriptor);

		return this.corevk$opened;
	}

	@Inject(method = "render(Lnet/minecraft/client/renderer/state/level/WeatherRenderState;"
			+ "Lcom/mojang/renderpearl/api/commands/RenderPass;"
			+ "Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;)V",
			require = 1, at = @At("RETURN"))
	private void corevk$close(CallbackInfo callback) {
		RenderPass opened = this.corevk$opened;
		this.corevk$opened = null;
		this.corevk$pipeline = null;
		if (opened != null) {
			opened.close();
		}
	}

	@WrapOperation(method = "render(Lnet/minecraft/client/renderer/state/level/WeatherRenderState;"
			+ "Lcom/mojang/renderpearl/api/commands/RenderPass;"
			+ "Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;)V",
			require = 1, at = @At(value = "INVOKE",
					target = "Lcom/mojang/renderpearl/api/commands/RenderPass;setPipeline("
							+ "Lcom/mojang/renderpearl/api/pipeline/CompiledRenderPipeline;)V"))
	private void corevk$pipeline(RenderPass pass, CompiledRenderPipeline compiled,
			Operation<Void> original) {
		RenderPipeline ours = this.corevk$pipeline;
		CompiledRenderPipeline chosen = ours == null ? null : GraphicsApi.compiledFor(ours);
		original.call(pass, chosen == null ? compiled : chosen);
	}

	@WrapOperation(method = "renderWeather", require = 1,
			at = @At(value = "INVOKE",
					target = "Lcom/mojang/renderpearl/api/commands/RenderPass;setUniform("
							+ "Ljava/lang/String;"
							+ "Lcom/mojang/renderpearl/api/textures/GpuTextureView;"
							+ "Lcom/mojang/renderpearl/api/textures/GpuSampler;)V"))
	private void corevk$texture(RenderPass pass, String name, GpuTextureView view,
			GpuSampler sampler, Operation<Void> original) {
		original.call(pass, name, view, sampler);
		RenderPipeline ours = this.corevk$pipeline;
		if (ours != null) {
			WeatherDraw.texture(view, sampler);
			WeatherDraw.bind(pass, ours);
		}
	}
}
