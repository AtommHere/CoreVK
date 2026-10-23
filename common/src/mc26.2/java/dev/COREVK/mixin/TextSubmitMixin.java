package net.corevk.corevk.mixin;

import net.corevk.corevk.render.BlockEntityGeometry;
import net.corevk.corevk.render.BlockEntityOrigin;

import net.minecraft.client.gui.Font;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.client.renderer.feature.TextFeatureRenderer.Submit.class)
public abstract class TextSubmitMixin implements BlockEntityOrigin {

	@Unique
	private boolean corevk$blockEntity;

	@Inject(method = "<init>", at = @At("RETURN"), require = 1)
	private void corevk$capture(Matrix4fc pose, float x, float y, FormattedCharSequence string,
			boolean dropShadow, Font.DisplayMode displayMode, int lightCoords, int color,
			int backgroundColor, int outlineColor, CallbackInfo callback) {
		this.corevk$blockEntity = BlockEntityGeometry.submitting();
	}

	@Override
	public boolean corevk$fromBlockEntity() {
		return this.corevk$blockEntity;
	}
}
