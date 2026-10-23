package net.corevk.corevk.mixin;

import net.corevk.corevk.platform.PatchedMethods;
import net.corevk.corevk.render.GeometryHold;
import net.corevk.corevk.render.WeatherDraw;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.state.GameRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;

@Mixin(WeatherEffectRenderer.class)
public abstract class WeatherEffectRendererMixin {

	
	@Unique
	private RenderPipeline corevk$pipeline;

	
	@Inject(method = { PatchedMethods.WEATHER_RENDER, PatchedMethods.WEATHER_RENDER_WIDENED },
			require = 1, at = @At("HEAD"), cancellable = true)
	private void corevk$refuse(CallbackInfo callback) {
		if (!WeatherDraw.draws()) {
			callback.cancel();
		}
	}

	
	@WrapOperation(method = { PatchedMethods.WEATHER_RENDER, PatchedMethods.WEATHER_RENDER_WIDENED },
			require = 1,
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/state/GameRenderState;"
							+ "useShaderTransparency()Z"))
	private boolean corevk$rainDepth(GameRenderState state, Operation<Boolean> original) {
		return original.call(state) || WeatherDraw.depth();
	}

	
	@WrapOperation(method = { PatchedMethods.WEATHER_RENDER, PatchedMethods.WEATHER_RENDER_WIDENED },
			require = 1,
			at = @At(value = "INVOKE",
					target = "Lcom/mojang/blaze3d/systems/CommandEncoder;createRenderPass("
							+ "Ljava/util/function/Supplier;"
							+ "Lcom/mojang/blaze3d/textures/GpuTextureView;"
							+ "Ljava/util/Optional;"
							+ "Lcom/mojang/blaze3d/textures/GpuTextureView;"
							+ "Ljava/util/OptionalDouble;"
							+ ")Lcom/mojang/blaze3d/systems/RenderPass;"))
	private RenderPass corevk$open(CommandEncoder encoder, Supplier<String> label,
			GpuTextureView colour, Optional<?> clearColour, GpuTextureView depth,
			OptionalDouble clearDepth, Operation<RenderPass> original, @Local RenderPipeline game) {
		this.corevk$pipeline = WeatherDraw.element(game, colour, depth);
		RenderPassDescriptor descriptor =
				this.corevk$pipeline == null ? null : WeatherDraw.descriptor();

		return descriptor == null
				? original.call(encoder, label, colour, clearColour, depth, clearDepth)
				: GeometryHold.open(encoder, descriptor);
	}

	@WrapOperation(method = { PatchedMethods.WEATHER_RENDER, PatchedMethods.WEATHER_RENDER_WIDENED },
			require = 1,
			at = @At(value = "INVOKE",
					target = "Lcom/mojang/blaze3d/systems/RenderPass;setPipeline("
							+ "Lcom/mojang/blaze3d/pipeline/RenderPipeline;)V"))
	private void corevk$pipeline(RenderPass pass, RenderPipeline pipeline,
			Operation<Void> original) {
		original.call(pass, this.corevk$pipeline == null ? pipeline : this.corevk$pipeline);
	}

	
	@WrapOperation(method = "renderWeather", require = 1,
			at = @At(value = "INVOKE",
					target = "Lcom/mojang/blaze3d/systems/RenderPass;bindTexture("
							+ "Ljava/lang/String;"
							+ "Lcom/mojang/blaze3d/textures/GpuTextureView;"
							+ "Lcom/mojang/blaze3d/textures/GpuSampler;)V"))
	private void corevk$texture(RenderPass pass, String name, GpuTextureView view,
			GpuSampler sampler, Operation<Void> original) {
		original.call(pass, name, view, sampler);
		if (this.corevk$pipeline != null) {
			WeatherDraw.texture(view, sampler);
			WeatherDraw.bind(pass, this.corevk$pipeline);
		}
	}
}
