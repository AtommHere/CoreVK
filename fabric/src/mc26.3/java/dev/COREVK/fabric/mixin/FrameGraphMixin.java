package net.corevk.corevk.fabric.mixin;

import net.corevk.corevk.platform.EngineStages;

import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class FrameGraphMixin {

	@Inject(method = "render", at = @At("HEAD"), require = 1)
	private void corevk$frameGraphSetup(GraphicsResourceAllocator allocator, boolean renderOutline,
			CameraRenderState cameraState, GpuBufferSlice terrainFog, Vector4f fogColor,
			boolean shouldRenderSky, boolean consistentDepthRequired, CallbackInfo ci) {
		EngineStages.frameGraphSetup(cameraState.viewRotationMatrix, cameraState.pos);
	}
}
