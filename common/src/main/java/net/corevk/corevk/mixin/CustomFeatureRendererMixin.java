package net.corevk.corevk.mixin;

import net.corevk.corevk.render.BlockEntityGeometry;
import net.corevk.corevk.render.BlockEntityOrigin;
import net.corevk.corevk.render.EntityIdentifiers;
import net.corevk.corevk.render.SubmittedIdentifiers;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.feature.CustomFeatureRenderer;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(CustomFeatureRenderer.class)
public abstract class CustomFeatureRendererMixin {

	@Inject(method = "buildGroup", require = 1,
			at = @At(value = "INVOKE",
					target = "getVertexBuilder(Lnet/minecraft/client/renderer/rendertype/RenderType;)"
							+ "Lcom/mojang/blaze3d/vertex/VertexConsumer;"))
	private void corevk$begin(FeatureFrameContext context, List<CustomFeatureRenderer.Submit> submits,
			CallbackInfo callback, @Local CustomFeatureRenderer.Submit submit) {
		BlockEntityGeometry.building(((BlockEntityOrigin) (Object) submit).corevk$fromBlockEntity());
		EntityIdentifiers.restore(((SubmittedIdentifiers) (Object) submit).corevk$identifiers());
	}

	@Inject(method = "buildGroup", at = @At("RETURN"), require = 1)
	private void corevk$end(FeatureFrameContext context, List<CustomFeatureRenderer.Submit> submits,
			CallbackInfo callback) {
		BlockEntityGeometry.building(false);
		EntityIdentifiers.clear();
	}
}
