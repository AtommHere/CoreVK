package net.corevk.corevk.fabric.mixin;

import net.corevk.corevk.platform.EngineStages;

import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class AfterLevelMixin {

	@Inject(method = "renderLevel",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/LevelRenderer;"
							+ "render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Z"
							+ "Lnet/minecraft/client/renderer/state/level/CameraRenderState;"
							+ "Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;"
							+ "Lorg/joml/Vector4f;ZZ)V",
					shift = At.Shift.AFTER),
			require = 1)
	private void corevk$afterLevel(CallbackInfo ci) {
		EngineStages.afterLevel();
	}
}
