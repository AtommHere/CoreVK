package net.corevk.corevk.fabric.mixin;

import net.corevk.corevk.platform.EngineStages;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class ClientSetupMixin {

	@Inject(method = "onGameLoadFinished", at = @At("HEAD"), require = 1)
	private void corevk$clientSetup(CallbackInfo ci) {
		EngineStages.clientSetup();
	}
}
