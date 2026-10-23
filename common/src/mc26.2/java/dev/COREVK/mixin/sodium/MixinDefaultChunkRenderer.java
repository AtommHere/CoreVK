package net.corevk.corevk.mixin.sodium;

import net.corevk.corevk.pack.program.TerrainPass;
import net.corevk.corevk.render.GeometryHold;
import net.corevk.corevk.render.TerrainDraw;
import net.corevk.corevk.render.timing.RingTimings;
import net.corevk.corevk.sodium.SodiumPasses;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.caffeinemc.mods.sodium.client.gui.SodiumOptions;
import net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;

@Mixin(value = DefaultChunkRenderer.class, remap = false)
public abstract class MixinDefaultChunkRenderer {

	@Inject(method = "rotate", at = @At("HEAD"), require = 1)
	private void corevk$rotateBegin(CallbackInfo ci) {
		RingTimings.beginRotate();
	}

	@Inject(method = "rotate", at = @At("RETURN"), require = 1)
	private void corevk$rotateEnd(CallbackInfo ci) {
		RingTimings.endRotate();
	}

	@WrapOperation(
			method = "render",
			require = 2,
			at = @At(value = "INVOKE",
					target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/terrain/TerrainRenderPass;"
							+ "getTarget()Lcom/mojang/blaze3d/pipeline/RenderTarget;"))
	private RenderTarget corevk$target(TerrainRenderPass pass, Operation<RenderTarget> original) {
		if (!TerrainDraw.drawingShadow()) {
			return original.call(pass);
		}

		Minecraft minecraft = Minecraft.getInstance();

		return minecraft == null ? original.call(pass) : minecraft.gameRenderer.mainRenderTarget();
	}

	@WrapOperation(
			method = "render",
			at = @At(value = "FIELD",
					target = "Lnet/caffeinemc/mods/sodium/client/gui/SodiumOptions$PerformanceSettings;"
							+ "useBlockFaceCulling:Z"))
	private boolean corevk$shadowFaces(SodiumOptions.PerformanceSettings settings,
			Operation<Boolean> original) {
		return !TerrainDraw.drawingShadow() && original.call(settings);
	}

	@WrapOperation(
			method = "render",
			at = @At(value = "INVOKE",
					target = "Lcom/mojang/blaze3d/systems/CommandEncoder;createRenderPass("
							+ "Ljava/util/function/Supplier;"
							+ "Lcom/mojang/blaze3d/textures/GpuTextureView;"
							+ "Ljava/util/Optional;"
							+ "Lcom/mojang/blaze3d/textures/GpuTextureView;"
							+ "Ljava/util/OptionalDouble;"
							+ ")Lcom/mojang/blaze3d/systems/RenderPass;"))
	private RenderPass corevk$pass(CommandEncoder encoder, Supplier<String> label,
			GpuTextureView colour, Optional<?> clearColour, GpuTextureView depth,
			OptionalDouble clearDepth, Operation<RenderPass> original,
			@Local(argsOnly = true) TerrainRenderPass pass) {
		TerrainPass ours = SodiumPasses.of(pass);
		RenderPassDescriptor descriptor = ours == null
				? null
				: TerrainDraw.descriptor(ours, colour, depth);

		return descriptor == null
				? original.call(encoder, label, colour, clearColour, depth, clearDepth)
				: GeometryHold.open(encoder, descriptor);
	}
}
