package net.corevk.corevk.mixin;

import net.corevk.corevk.render.EntityIdentifiers;
import net.corevk.corevk.render.SubmittedIdentifiers;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemFeatureRenderer.class)
public abstract class ItemFeatureRendererMixin {

	@Inject(method = "prepareSubmit", at = @At("HEAD"), require = 1)
	private void corevk$begin(CallbackInfo callback,
			@Local(argsOnly = true) ItemFeatureRenderer.Submit submit) {
		EntityIdentifiers.restore(((SubmittedIdentifiers) (Object) submit).corevk$identifiers());
	}

	@Inject(method = "prepareSubmit", at = @At("RETURN"), require = 1)
	private void corevk$end(CallbackInfo callback) {
		EntityIdentifiers.clear();
	}
}
