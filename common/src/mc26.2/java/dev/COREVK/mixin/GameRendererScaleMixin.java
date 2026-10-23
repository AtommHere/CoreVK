package net.corevk.corevk.mixin;

import net.corevk.corevk.render.RenderScale;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Vector4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererScaleMixin {

	@Shadow
	public abstract RenderTarget mainRenderTarget();

	
	@Inject(method = "render", at = @At("HEAD"))
	private void corevk$frameIntent(DeltaTracker deltaTracker, boolean advanceGameTime,
			CallbackInfo ci) {
		RenderScale.recover(mainRenderTarget());

		Minecraft minecraft = Minecraft.getInstance();
		RenderScale.frameIntent(minecraft.isGameLoadFinished() && advanceGameTime
				&& minecraft.level != null);
	}

	@WrapOperation(method = "render", at = @At(value = "INVOKE",
			target = "Lcom/mojang/blaze3d/systems/CommandEncoder;clearColorAndDepthTextures("
					+ "Lcom/mojang/blaze3d/textures/GpuTexture;Lorg/joml/Vector4fc;"
					+ "Lcom/mojang/blaze3d/textures/GpuTexture;D)V"))
	private void corevk$scaleWorld(CommandEncoder encoder, GpuTexture colour, Vector4fc clearColour,
			GpuTexture depth, double clearDepth, Operation<Void> original) {
		RenderTarget main = mainRenderTarget();
		if (RenderScale.beginWorld(main)) {
			original.call(encoder, main.getColorTexture(), clearColour, main.getDepthTexture(),
					clearDepth);
		} else {
			original.call(encoder, colour, clearColour, depth, clearDepth);
		}
	}

	@WrapOperation(method = "render", at = @At(value = "INVOKE",
			target = "Lcom/mojang/blaze3d/systems/CommandEncoder;clearDepthTexture("
					+ "Lcom/mojang/blaze3d/textures/GpuTexture;D)V"))
	private void corevk$unscaleWorld(CommandEncoder encoder, GpuTexture depth, double clearDepth,
			Operation<Void> original) {
		RenderTarget main = mainRenderTarget();
		RenderScale.endWorld(main, encoder);
		original.call(encoder, main == null ? depth : main.getDepthTexture(), clearDepth);
	}
}
