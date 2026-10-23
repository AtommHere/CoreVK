package net.corevk.corevk.mixin;

import net.corevk.corevk.render.CloudDraw;
import net.corevk.corevk.render.GeometryHold;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;

@Mixin(CloudRenderer.class)
public abstract class CloudRendererMixin {

	
	private RenderPipeline corevk$pipeline;

	
	private boolean corevk$fancy;

	
	@Inject(method = "render", at = @At("HEAD"))
	private void corevk$status(int colour, CloudStatus cloudStatus, float bottomY, int range,
			Vec3 cameraPosition, long gameTime, float partialTicks, CallbackInfo callback) {
		this.corevk$fancy = cloudStatus == CloudStatus.FANCY;
	}

	@WrapOperation(method = "render",
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
		this.corevk$pipeline = CloudDraw.pipeline(this.corevk$fancy);
		RenderPassDescriptor descriptor = this.corevk$pipeline == null
				? null
				: CloudDraw.descriptor(colour, depth);

		return descriptor == null
				? original.call(encoder, label, colour, clearColour, depth, clearDepth)
				: GeometryHold.open(encoder, descriptor);
	}

	@WrapOperation(method = "render",
			at = @At(value = "INVOKE",
					target = "Lcom/mojang/blaze3d/systems/RenderPass;setPipeline("
							+ "Lcom/mojang/blaze3d/pipeline/RenderPipeline;)V"))
	private void corevk$pipeline(RenderPass pass, RenderPipeline pipeline,
			Operation<Void> original) {
		original.call(pass, this.corevk$pipeline == null ? pipeline : this.corevk$pipeline);
	}

	
	@WrapOperation(method = "render",
			at = @At(value = "INVOKE",
					target = "Lcom/mojang/blaze3d/systems/RenderPass;drawIndexed(IIIII)V"))
	private void corevk$draw(RenderPass pass, int indexCount, int instanceCount, int firstIndex,
			int vertexOffset, int firstInstance, Operation<Void> original) {
		if (this.corevk$pipeline != null) {
			CloudDraw.bind(pass, this.corevk$pipeline);
		}

		original.call(pass, indexCount, instanceCount, firstIndex, vertexOffset, firstInstance);
	}
}
