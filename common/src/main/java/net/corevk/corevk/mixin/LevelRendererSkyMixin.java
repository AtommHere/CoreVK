package net.corevk.corevk.mixin;

import net.corevk.corevk.platform.PatchedMethods;
import net.corevk.corevk.render.SkyDraw;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererSkyMixin {

	
	@Inject(method = { PatchedMethods.SKY_PASS, PatchedMethods.SKY_PASS_WIDENED },
			require = 1, at = @At("HEAD"), cancellable = true)
	private void corevk$sky(CallbackInfo callback,
			@Local(argsOnly = true) CameraRenderState cameraState) {
		if (cameraState.fogType != FogType.NONE && !SkyDraw.serves()) {
			callback.cancel();
		}
	}
}
