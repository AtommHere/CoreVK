package net.corevk.corevk.mixin.game;

import net.corevk.corevk.render.GeometryHold;
import net.corevk.corevk.render.SkyDraw;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.DynamicUniforms;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.world.level.MoonPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;

@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {

	private RenderPipeline corevk$pipeline;

	private Matrix4f corevk$modelView;
	private Vector4f corevk$colour;

	@Inject(method = "renderSunMoonAndStars",
			at = @At(value = "INVOKE", ordinal = 0, shift = At.Shift.AFTER,
					target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionfc;)V"))
	private void corevk$tilt(PoseStack poseStack, float sunAngle, float moonAngle, float starAngle,
			MoonPhase moonPhase, float rainBrightness, float starBrightness, CallbackInfo callback) {
		float tilt = SkyDraw.sunPathRotation();
		if (tilt != 0.0F) {
			poseStack.mulPose(Axis.ZP.rotationDegrees(tilt));
		}
	}

	@Inject(method = "renderSun", at = @At("HEAD"), cancellable = true)
	private void corevk$sun(float rainBrightness, PoseStack poseStack, CallbackInfo callback) {
		corevk$refuse("Sky sun", callback);
	}

	@Inject(method = "renderMoon", at = @At("HEAD"), cancellable = true)
	private void corevk$moon(MoonPhase moonPhase, float rainBrightness, PoseStack poseStack,
			CallbackInfo callback) {
		corevk$refuse("Sky moon", callback);
	}

	private static void corevk$refuse(String label, CallbackInfo callback) {
		if (!SkyDraw.draws(label)) {
			callback.cancel();
		}
	}

	@WrapOperation(
			method = {"renderSkyDisc", "renderDarkDisc", "renderStars", "renderSunriseAndSunset", "renderSun",
					"renderMoon", "renderEndFlash"},
			require = 7,
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/DynamicUniforms;writeTransform("
							+ "Lorg/joml/Matrix4f;"
							+ "Lorg/joml/Vector4f;"
							+ ")Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"))
	private GpuBufferSlice corevk$transform(DynamicUniforms uniforms, Matrix4f modelView,
			Vector4f colour, Operation<GpuBufferSlice> original) {
		this.corevk$modelView = modelView;
		this.corevk$colour = colour;

		return original.call(uniforms, modelView, colour);
	}

	@WrapOperation(
			method = "renderEndSky",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/DynamicUniforms;writeTransform("
							+ "Lorg/joml/Matrix4f;"
							+ ")Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"))
	private GpuBufferSlice corevk$endTransform(DynamicUniforms uniforms, Matrix4f modelView,
			Operation<GpuBufferSlice> original) {
		this.corevk$modelView = modelView;
		this.corevk$colour = new Vector4f(1.0F, 1.0F, 1.0F, 1.0F);

		return original.call(uniforms, modelView);
	}

	@WrapOperation(
			method = {"renderSkyDisc", "renderDarkDisc", "renderStars", "renderSunriseAndSunset", "renderSun",
					"renderMoon", "renderEndSky", "renderEndFlash"},
			require = 8,
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
			OptionalDouble clearDepth, Operation<RenderPass> original) {
		this.corevk$pipeline = SkyDraw.element(label.get(), this.corevk$modelView,
				this.corevk$colour);
		RenderPassDescriptor descriptor = this.corevk$pipeline == null
				? null
				: SkyDraw.descriptor(colour, depth);

		return descriptor == null
				? original.call(encoder, label, colour, clearColour, depth, clearDepth)
				: GeometryHold.open(encoder, descriptor);
	}

	@WrapOperation(
			method = {"renderSkyDisc", "renderDarkDisc", "renderStars", "renderSunriseAndSunset", "renderSun",
					"renderMoon", "renderEndSky", "renderEndFlash"},
			require = 8,
			at = @At(value = "INVOKE",
					target = "Lcom/mojang/blaze3d/systems/RenderPass;setPipeline("
							+ "Lcom/mojang/blaze3d/pipeline/RenderPipeline;)V"))
	private void corevk$pipeline(RenderPass pass, RenderPipeline pipeline,
			Operation<Void> original) {
		original.call(pass, this.corevk$pipeline == null ? pipeline : this.corevk$pipeline);
	}

	@WrapOperation(
			method = {"renderSun", "renderMoon", "renderEndSky", "renderEndFlash"},
			require = 4,
			at = @At(value = "INVOKE",
					target = "Lcom/mojang/blaze3d/systems/RenderPass;bindTexture("
							+ "Ljava/lang/String;"
							+ "Lcom/mojang/blaze3d/textures/GpuTextureView;"
							+ "Lcom/mojang/blaze3d/textures/GpuSampler;)V"))
	private void corevk$texture(RenderPass pass, String name, GpuTextureView view,
			GpuSampler sampler, Operation<Void> original) {
		original.call(pass, name, view, sampler);
		SkyDraw.texture(view, sampler);
	}

	@WrapOperation(method = "renderSkyDisc",
			at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderPass;draw(IIII)V"))
	private void corevk$horizon(RenderPass pass, int vertices, int instances, int firstVertex,
			int firstInstance, Operation<Void> original) {
		original.call(pass, vertices, instances, firstVertex, firstInstance);
		if (this.corevk$pipeline != null) {
			SkyDraw.horizon(pass, this.corevk$pipeline);
		}
	}

	@WrapOperation(
			method = {"renderSkyDisc", "renderDarkDisc", "renderStars", "renderSunriseAndSunset", "renderSun",
					"renderMoon", "renderEndSky", "renderEndFlash"},
			require = 8,
			at = @At(value = "INVOKE",
					target = "Lcom/mojang/blaze3d/systems/RenderPass;setVertexBuffer("
							+ "ILcom/mojang/blaze3d/buffers/GpuBufferSlice;)V"))
	private void corevk$bind(RenderPass pass, int slot, GpuBufferSlice buffer,
			Operation<Void> original) {
		original.call(pass, slot, buffer);
		if (this.corevk$pipeline != null) {
			SkyDraw.bind(pass, this.corevk$pipeline);
		}
	}
}
