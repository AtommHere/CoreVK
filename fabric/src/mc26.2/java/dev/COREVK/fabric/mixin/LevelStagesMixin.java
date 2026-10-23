package net.corevk.corevk.fabric.mixin;

import net.corevk.corevk.platform.EngineStages;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.textures.GpuSampler;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelRenderer.class)
public abstract class LevelStagesMixin {

	@WrapOperation(method = "lambda$addMainPass$0",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;"
							+ "renderGroup(Lnet/minecraft/client/renderer/chunk/ChunkSectionLayerGroup;"
							+ "Lcom/mojang/blaze3d/textures/GpuSampler;)V",
					ordinal = 0),
			require = 1)
	private void corevk$afterOpaqueBlocks(ChunkSectionsToRender sections,
			ChunkSectionLayerGroup group, GpuSampler sampler, Operation<Void> original) {
		original.call(sections, group, sampler);
		EngineStages.afterOpaqueBlocks();
	}

	@WrapOperation(method = "lambda$addMainPass$0",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;"
							+ "executeSolid()V"),
			require = 1)
	private void corevk$afterOpaqueFeatures(FeatureRenderDispatcher.PreparedFrame frame,
			Operation<Void> original) {
		original.call(frame);
		EngineStages.afterOpaqueFeatures();
	}

	@WrapOperation(method = "lambda$addMainPass$0",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;"
							+ "executeTranslucent()V"),
			require = 1)
	private void corevk$afterTranslucentFeatures(FeatureRenderDispatcher.PreparedFrame frame,
			Operation<Void> original) {
		original.call(frame);
		EngineStages.afterTranslucentFeatures();
	}
}
