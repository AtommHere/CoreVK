package net.corevk.corevk.mixin;

import net.corevk.corevk.render.BlockEntityGeometry;
import net.corevk.corevk.render.BlockEntityOrigin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.TextFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(TextFeatureRenderer.class)
public abstract class TextFeatureRendererMixin {

	@Inject(method = "buildGroup", require = 1,
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/gui/Font$PreparedText;"
							+ "visit(Lnet/minecraft/client/gui/Font$GlyphVisitor;)V"))
	private void corevk$begin(FeatureFrameContext context, List<TextFeatureRenderer.Submit> submits,
			CallbackInfo callback, @Local TextFeatureRenderer.Submit submit) {
		BlockEntityGeometry.building(((BlockEntityOrigin) (Object) submit).corevk$fromBlockEntity());
	}

	@Inject(method = "buildGroup", at = @At("RETURN"), require = 1)
	private void corevk$end(FeatureFrameContext context, List<TextFeatureRenderer.Submit> submits,
			CallbackInfo callback) {
		BlockEntityGeometry.building(false);
	}
}
