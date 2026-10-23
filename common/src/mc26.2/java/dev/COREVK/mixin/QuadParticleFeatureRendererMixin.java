package net.corevk.corevk.mixin;

import net.corevk.corevk.render.GeometryHold;
import net.corevk.corevk.render.ParticleDraw;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.QuadParticleFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;

@Mixin(QuadParticleFeatureRenderer.class)
public abstract class QuadParticleFeatureRendererMixin {

	@WrapOperation(method = "executeGroup", require = 1,
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
			OptionalDouble clearDepth, Operation<RenderPass> original,
			@Local(argsOnly = true) List<QuadParticleFeatureRenderer.Submit> submits) {
		RenderPipeline pipeline = submits.isEmpty()
				? null
				: ParticleDraw.group(submits.getFirst().translucent(), colour, depth);
		RenderPassDescriptor descriptor = pipeline == null ? null : ParticleDraw.descriptor();

		RenderPass pass = descriptor == null
				? original.call(encoder, label, colour, clearColour, depth, clearDepth)
				: GeometryHold.open(encoder, descriptor);
		ParticleDraw.opened(pass);

		return pass;
	}

	@WrapMethod(method = "executeGroup", require = 1)
	private void corevk$group(FeatureFrameContext context, int groupIndex,
			List<QuadParticleFeatureRenderer.Submit> submits, boolean strictlyOrdered,
			Operation<Void> original) {
		try {
			original.call(context, groupIndex, submits, strictlyOrdered);
		} finally {
			ParticleDraw.endGroup();
		}
	}
}
