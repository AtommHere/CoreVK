package net.corevk.corevk.neoforge.mixin;

import net.corevk.corevk.neoforge.EarlyWindow;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftEarlyWindowMixin {

	@Inject(method = "<init>", at = @At(value = "INVOKE",
			target = "Lnet/neoforged/neoforge/client/loading/ClientModLoader;finish()V"),
			require = 1)
	private void corevk$takeTheEarlyWindowOver(CallbackInfo ci) {
		EarlyWindow.takeOver();
	}
}
