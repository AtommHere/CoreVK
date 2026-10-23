package net.corevk.corevk.mixin;

import net.corevk.corevk.render.PackChain;

import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

	@Inject(method = "executeAlwaysOnTop", at = @At("HEAD"), require = 1)
	private void corevk$scene(CallbackInfo callback) {
		PackChain.markSceneDepth();
	}
}
