package net.corevk.corevk.mixin;

import net.corevk.corevk.render.BlockEntityGeometry;
import net.corevk.corevk.render.BlockEntityOrigin;
import net.corevk.corevk.render.EntityIdentifiers;
import net.corevk.corevk.render.SubmittedIdentifiers;

import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelFeatureRenderer.class)
public abstract class ModelFeatureRendererMixin {

	@Inject(method = "prepareModel", at = @At("HEAD"), require = 1)
	private void corevk$begin(ModelFeatureRenderer.Submit<?> submit, CallbackInfo callback) {
		BlockEntityGeometry.building(((BlockEntityOrigin) (Object) submit).corevk$fromBlockEntity());
		EntityIdentifiers.restore(((SubmittedIdentifiers) (Object) submit).corevk$identifiers());
	}

	@Inject(method = "prepareModel", at = @At("RETURN"), require = 1)
	private void corevk$end(ModelFeatureRenderer.Submit<?> submit, CallbackInfo callback) {
		BlockEntityGeometry.building(false);
		EntityIdentifiers.clear();
	}
}
