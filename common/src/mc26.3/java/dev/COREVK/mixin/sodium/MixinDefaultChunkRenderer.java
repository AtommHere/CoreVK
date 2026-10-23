package net.corevk.corevk.mixin.sodium;

import net.corevk.corevk.pack.program.TerrainPass;
import net.corevk.corevk.render.GeometryHold;
import net.corevk.corevk.render.LevelPass;
import net.corevk.corevk.render.TerrainDraw;
import net.corevk.corevk.render.timing.RingTimings;
import net.corevk.corevk.sodium.SodiumApi;
import net.corevk.corevk.sodium.SodiumPasses;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.textures.GpuSampler;
import net.caffeinemc.mods.sodium.client.gui.SodiumOptions;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderListIterable;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import net.minecraft.client.renderer.oit.OitStage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import org.jspecify.annotations.Nullable;

@Mixin(value = DefaultChunkRenderer.class, remap = false)
public abstract class MixinDefaultChunkRenderer {

	@Unique
	private @Nullable RenderPass corevk$opened;

	@Inject(method = "rotate", at = @At("HEAD"), require = 1)
	private void corevk$rotateBegin(CallbackInfo ci) {
		RingTimings.beginRotate();
	}

	@Inject(method = "rotate", at = @At("RETURN"), require = 1)
	private void corevk$rotateEnd(CallbackInfo ci) {
		RingTimings.endRotate();
	}

	@WrapOperation(
			method = "prepare",
			at = @At(value = "FIELD",
					target = "Lnet/caffeinemc/mods/sodium/client/gui/SodiumOptions$PerformanceSettings;"
							+ "useBlockFaceCulling:Z"))
	private boolean corevk$shadowFaces(SodiumOptions.PerformanceSettings settings,
			Operation<Boolean> original) {
		return !TerrainDraw.drawingShadow() && original.call(settings);
	}

	@ModifyVariable(method = "render", require = 1, argsOnly = true,
			at = @At(value = "LOAD", ordinal = 0))
	private RenderPass corevk$pass(RenderPass pass, ChunkRenderMatrices matrices,
			ChunkRenderListIterable renderLists, TerrainRenderPass renderPass,
			CameraTransform camera, FogParameters parameters, boolean indexedRenderingEnabled,
			RenderPass handed, GpuSampler terrainSampler, GpuBufferSlice uniformData,
			GpuBuffer sectionTimeInfo, @Nullable OitStage stage) {
		this.corevk$opened = null;
		if (stage != null || !(pass instanceof LevelPass || TerrainDraw.drawingShadow())) {
			return pass;
		}

		TerrainPass ours = SodiumPasses.of(renderPass);
		RenderTarget target = renderPass.getTarget();
		RenderPassDescriptor descriptor = ours == null
				? null
				: TerrainDraw.descriptor(ours, target.getColorTextureView(),
						target.getDepthTextureView());
		if (descriptor == null) {
			return pass;
		}

		this.corevk$opened = GeometryHold.open(RenderSystem.getDevice().createCommandEncoder(),
				descriptor);

		return this.corevk$opened;
	}

	@Inject(method = "render", at = @At("RETURN"), require = 1)
	private void corevk$close(CallbackInfo callback) {
		RenderPass opened = this.corevk$opened;
		this.corevk$opened = null;
		if (opened != null) {
			opened.close();
		}
	}
}
