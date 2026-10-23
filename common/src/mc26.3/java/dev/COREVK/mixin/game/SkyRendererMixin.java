package net.corevk.corevk.mixin.game;

import net.corevk.corevk.render.GeometryHold;
import net.corevk.corevk.render.GraphicsApi;
import net.corevk.corevk.render.LevelPass;
import net.corevk.corevk.render.SkyDraw;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.renderer.DynamicGpuData;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.world.level.MoonPhase;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;

@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {

	
	@Unique
	private @Nullable Matrix4f corevk$modelView;

	
	@Unique
	private @Nullable Vector4f corevk$colour;

	
	@Unique
	private @Nullable RenderPipeline corevk$pipeline;

	
	@Unique
	private @Nullable RenderPass corevk$opened;

	
	@Inject(method = "renderSunMoonAndStars",
			at = @At(value = "INVOKE", ordinal = 0, shift = At.Shift.AFTER,
					target = "Lcom/mojang/blaze3d/vertex/PoseStack;rotateDegrees(Lcom/mojang/math/Axis;F)V"))
	private void corevk$tilt(RenderPass pass, PoseStack poseStack, float sunAngle, float moonAngle,
			float starAngle, MoonPhase moonPhase, float rainBrightness, float starBrightness,
			CallbackInfo callback) {
		float tilt = SkyDraw.sunPathRotation();
		if (tilt != 0.0F) {
			poseStack.rotate(Axis.ZP.rotationDegrees(tilt));
		}
	}

	
	@WrapOperation(method = "render", require = 1,
			at = @At(value = "INVOKE",
					target = "Lcom/mojang/renderpearl/api/commands/CommandEncoder;createRenderPass("
							+ "Ljava/util/function/Supplier;"
							+ "Lcom/mojang/renderpearl/api/textures/GpuTextureView;"
							+ "Ljava/util/Optional;"
							+ "Lcom/mojang/renderpearl/api/textures/GpuTextureView;"
							+ "Ljava/util/OptionalDouble;"
							+ ")Lcom/mojang/renderpearl/api/commands/RenderPass;"))
	private RenderPass corevk$skyPass(CommandEncoder encoder, Supplier<String> label,
			GpuTextureView colour, Optional<?> clearColour, @Nullable GpuTextureView depth,
			OptionalDouble clearDepth, Operation<RenderPass> original) {
		RenderPass first = original.call(encoder, label, colour, clearColour, depth, clearDepth);
		if (!SkyDraw.serves()) {
			return first;
		}

		// Opened again with nothing emptied, which is what the first opening did too.
		return LevelPass.open(first, colour, depth, () -> original.call(encoder, label, colour,
				Optional.empty(), depth, OptionalDouble.empty()));
	}

	
	@Inject(method = "renderSun", at = @At("HEAD"), cancellable = true, require = 1)
	private void corevk$sun(RenderPass pass, float rainBrightness, PoseStack poseStack,
			CallbackInfo callback) {
		if (!SkyDraw.draws("Sky sun")) {
			callback.cancel();
		}
	}

	
	@Inject(method = "renderMoon", at = @At("HEAD"), cancellable = true, require = 1)
	private void corevk$moon(RenderPass pass, MoonPhase moonPhase, float rainBrightness,
			PoseStack poseStack, CallbackInfo callback) {
		if (!SkyDraw.draws("Sky moon")) {
			callback.cancel();
		}
	}

	
	@WrapOperation(
			method = {"renderSkyDisc", "renderDarkDisc", "renderStars", "renderSunriseAndSunset",
					"renderSun", "renderMoon", "renderEndFlash"},
			require = 7,
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/DynamicGpuData;writeTransform("
							+ "Lorg/joml/Matrix4f;Lorg/joml/Vector4f;"
							+ ")Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;"))
	private GpuBufferSlice corevk$transform(DynamicGpuData uniforms, Matrix4f modelView,
			Vector4f colour, Operation<GpuBufferSlice> original) {
		this.corevk$modelView = modelView;
		this.corevk$colour = colour;

		return original.call(uniforms, modelView, colour);
	}

	
	@WrapOperation(method = "renderEndSky", require = 1,
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/DynamicGpuData;writeTransform("
							+ "Lorg/joml/Matrix4f;)Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;"))
	private GpuBufferSlice corevk$endTransform(DynamicGpuData uniforms, Matrix4f modelView,
			Operation<GpuBufferSlice> original) {
		this.corevk$modelView = modelView;
		this.corevk$colour = new Vector4f(1.0F, 1.0F, 1.0F, 1.0F);

		return original.call(uniforms, modelView);
	}

	@ModifyVariable(method = "renderSkyDisc", require = 1, argsOnly = true,
			at = @At(value = "LOAD", ordinal = 0))
	private RenderPass corevk$disc(RenderPass pass) {
		return corevk$piece(pass, "Sky disc");
	}

	@ModifyVariable(method = "renderDarkDisc", require = 1, argsOnly = true,
			at = @At(value = "LOAD", ordinal = 0))
	private RenderPass corevk$dark(RenderPass pass) {
		return corevk$piece(pass, "Sky dark");
	}

	@ModifyVariable(method = "renderStars", require = 1, argsOnly = true,
			at = @At(value = "LOAD", ordinal = 0))
	private RenderPass corevk$stars(RenderPass pass) {
		return corevk$piece(pass, "Stars");
	}

	@ModifyVariable(method = "renderSunriseAndSunset", require = 1, argsOnly = true,
			at = @At(value = "LOAD", ordinal = 0))
	private RenderPass corevk$sunrise(RenderPass pass) {
		return corevk$piece(pass, "Sunrise sunset");
	}

	@ModifyVariable(method = "renderEndSky", require = 1, argsOnly = true,
			at = @At(value = "LOAD", ordinal = 0))
	private RenderPass corevk$endSky(RenderPass pass) {
		return corevk$piece(pass, "End sky");
	}

	
	@ModifyVariable(method = "drawCelestialBody", require = 1, argsOnly = true,
			at = @At(value = "LOAD", ordinal = 0))
	private RenderPass corevk$body(RenderPass pass, Supplier<String> label) {
		return corevk$piece(pass, switch (label.get()) {
			case "Sun" -> "Sky sun";
			case "Moon" -> "Sky moon";
			default -> label.get();
		});
	}

	@Inject(method = {"renderSkyDisc", "renderDarkDisc", "renderStars", "renderSunriseAndSunset",
			"renderEndSky", "drawCelestialBody"}, require = 6, at = @At("RETURN"))
	private void corevk$done(CallbackInfo callback) {
		RenderPass opened = this.corevk$opened;
		this.corevk$opened = null;
		this.corevk$pipeline = null;
		if (opened != null) {
			// Kept rather than ended where the next piece the pack serves names the same images,
			// which is what GeometryHold is for; the game's pass ends it where the next piece is
			// the game's own.
			opened.close();
		}
	}

	@WrapOperation(
			method = {"renderSkyDisc", "renderDarkDisc", "renderStars", "renderSunriseAndSunset",
					"renderEndSky", "drawCelestialBody"},
			require = 6,
			at = @At(value = "INVOKE",
					target = "Lcom/mojang/renderpearl/api/commands/RenderPass;setPipeline("
							+ "Lcom/mojang/renderpearl/api/pipeline/CompiledRenderPipeline;)V"))
	private void corevk$setPipeline(RenderPass pass, CompiledRenderPipeline compiled,
			Operation<Void> original) {
		RenderPipeline ours = this.corevk$pipeline;
		CompiledRenderPipeline chosen = ours == null ? null : GraphicsApi.compiledFor(ours);
		original.call(pass, chosen == null ? compiled : chosen);
	}

	@WrapOperation(method = {"renderEndSky", "drawCelestialBody"}, require = 2,
			at = @At(value = "INVOKE",
					target = "Lcom/mojang/renderpearl/api/commands/RenderPass;setUniform("
							+ "Ljava/lang/String;"
							+ "Lcom/mojang/renderpearl/api/textures/GpuTextureView;"
							+ "Lcom/mojang/renderpearl/api/textures/GpuSampler;)V"))
	private void corevk$texture(RenderPass pass, String name, @Nullable GpuTextureView view,
			@Nullable GpuSampler sampler, Operation<Void> original) {
		original.call(pass, name, view, sampler);
		if (this.corevk$pipeline != null && view != null && sampler != null) {
			SkyDraw.texture(view, sampler);
		}
	}

	@WrapOperation(
			method = {"renderSkyDisc", "renderDarkDisc", "renderStars", "renderSunriseAndSunset",
					"renderEndSky", "drawCelestialBody"},
			require = 6,
			at = @At(value = "INVOKE",
					target = "Lcom/mojang/renderpearl/api/commands/RenderPass;setVertexBuffer("
							+ "ILcom/mojang/renderpearl/api/buffers/GpuBufferSlice;)V"))
	private void corevk$bind(RenderPass pass, int slot, GpuBufferSlice buffer,
			Operation<Void> original) {
		original.call(pass, slot, buffer);
		RenderPipeline ours = this.corevk$pipeline;
		if (ours != null) {
			SkyDraw.bind(pass, ours);
		}
	}

	@WrapOperation(method = "renderSkyDisc", require = 1,
			at = @At(value = "INVOKE",
					target = "Lcom/mojang/renderpearl/api/commands/RenderPass;draw(IIII)V"))
	private void corevk$horizon(RenderPass pass, int vertices, int instances, int firstVertex,
			int firstInstance, Operation<Void> original) {
		original.call(pass, vertices, instances, firstVertex, firstInstance);
		RenderPipeline ours = this.corevk$pipeline;
		if (ours != null) {
			SkyDraw.horizon(pass, ours);
		}
	}

	
	@Unique
	private RenderPass corevk$piece(RenderPass pass, String label) {
		this.corevk$pipeline = null;
		this.corevk$opened = null;
		Matrix4f modelView = this.corevk$modelView;
		Vector4f colour = this.corevk$colour;
		if (!(pass instanceof LevelPass level) || modelView == null || colour == null) {
			return pass;
		}

		RenderPipeline ours = SkyDraw.element(label, modelView, colour);
		if (ours == null || GraphicsApi.compiledFor(ours) == null) {
			return pass;
		}

		RenderPassDescriptor descriptor = SkyDraw.descriptor(level.colour(), level.depth());
		if (descriptor == null) {
			return pass;
		}

		this.corevk$pipeline = ours;
		this.corevk$opened = GeometryHold.open(RenderSystem.getDevice().createCommandEncoder(),
				descriptor);

		return this.corevk$opened;
	}
}
