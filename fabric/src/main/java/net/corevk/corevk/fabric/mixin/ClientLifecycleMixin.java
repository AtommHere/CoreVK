package net.corevk.corevk.fabric.mixin;

import net.corevk.corevk.platform.EngineStages;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class ClientLifecycleMixin {

	@Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V",
			at = @At("HEAD"), require = 1)
	private void corevk$leaveWorld(Screen screen, boolean keepResourcePacks, boolean stopSound,
			CallbackInfo ci) {
		if (((Minecraft) (Object) this).level == null) {
			return;
		}

		EngineStages.leaveWorld();
	}

	@Inject(method = "close", at = @At("HEAD"), require = 1)
	private void corevk$closeClient(CallbackInfo ci) {
		EngineStages.closeClient();
	}
}
