package net.corevk.corevk.mixin.game;

import net.corevk.corevk.render.EntityDraw;
import net.corevk.corevk.render.GameRender;
import net.corevk.corevk.render.LevelPass;
import net.corevk.corevk.render.PackChain;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererFeaturesMixin {

	@WrapOperation(method = "executeClassicTransparency", require = 1,
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;"
							+ "executeTranslucent(Lcom/mojang/renderpearl/api/commands/RenderPass;)V"))
	private void corevk$layer(FeatureRenderDispatcher.PreparedFrame frame, RenderPass pass,
			Operation<Void> original) {
		RenderPass layer = GameRender.featuresPass();
		if (layer == null) {
			original.call(frame, pass);

			return;
		}

		try {
			original.call(frame, layer);
		} finally {
			layer.close();
		}
	}

	@WrapMethod(method = "executeSeeThrough", require = 1)
	private void corevk$seeThrough(FeatureRenderDispatcher.PreparedFrame frame, RenderTarget target,
			Operation<Void> original) {
		if (!PackChain.drawingPack()) {
			original.call(frame, target);

			return;
		}

		GpuTextureView colour = target.getColorTextureView();
		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		Supplier<String> label = () -> "See through features";
		Supplier<RenderPass> opener = () -> encoder.createRenderPass(label, colour, Optional.empty(),
				null, OptionalDouble.empty());

		EntityDraw.translucentFeatures(true);
		try (RenderPass pass = LevelPass.open(opener.get(), colour, null, opener)) {
			RenderSystem.bindDefaultUniforms(pass);
			frame.executeSeeThrough(pass);
		} finally {
			EntityDraw.translucentFeatures(false);
		}
	}
}
