package net.corevk.corevk.mixin;

import net.corevk.corevk.pack.source.ShaderProperties;
import net.corevk.corevk.render.CloudDraw;

import net.minecraft.client.CloudStatus;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Options.class, priority = 1010)
public abstract class OptionsCloudsMixin {

	@Inject(method = "getCloudStatus", at = @At("HEAD"), cancellable = true)
	private void corevk$clouds(CallbackInfoReturnable<CloudStatus> callback) {
		CloudDraw.setting().map(OptionsCloudsMixin::corevk$status).ifPresent(callback::setReturnValue);
	}

	
	private static CloudStatus corevk$status(ShaderProperties.CloudSetting asked) {
		return switch (asked) {
			case OFF -> CloudStatus.OFF;
			case FAST -> CloudStatus.FAST;
			case FANCY, DEFAULT -> CloudStatus.FANCY;
		};
	}
}
