package net.corevk.corevk.mixin;

import net.corevk.corevk.render.PackChoice;

import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelExtractor.class)
public abstract class LevelExtractorMixin {

	@Inject(method = "allChanged", at = @At("HEAD"), require = 1)
	private void corevk$fabulous(CallbackInfo ci) {
		PackChoice.turnOffImprovedTransparency();
	}
}
