package net.corevk.corevk.mixin;

import net.corevk.corevk.render.timing.PassTimings;
import net.corevk.corevk.render.timing.RingTimings;

import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererFrameMixin {

	@Inject(method = "endFrame", at = @At("HEAD"), require = 1)
	private void corevk$endFrame(CallbackInfo ci) {
		PassTimings.endFrame();
		RingTimings.endFrame();
	}
}
