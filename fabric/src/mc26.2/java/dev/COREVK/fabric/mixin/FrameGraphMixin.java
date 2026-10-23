package net.corevk.corevk.fabric.mixin;

import net.corevk.corevk.platform.EngineStages;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class FrameGraphMixin {

	@Inject(method = "render", at = @At("HEAD"), require = 1)
	private void corevk$frameGraphSetup(GraphicsResourceAllocator allocator, DeltaTracker deltaTracker,
			boolean renderOutline, CameraRenderState cameraState, Matrix4fc modelViewMatrix,
			GpuBufferSlice terrainFog, Vector4f fogColor, boolean shouldRenderSky, CallbackInfo ci) {
		EngineStages.frameGraphSetup(modelViewMatrix, cameraState.pos);
	}
}
