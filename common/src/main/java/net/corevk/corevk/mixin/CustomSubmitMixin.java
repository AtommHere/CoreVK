package net.corevk.corevk.mixin;

import net.corevk.corevk.render.BlockEntityGeometry;
import net.corevk.corevk.render.BlockEntityOrigin;
import net.corevk.corevk.render.EntityIdentifiers;
import net.corevk.corevk.render.SubmittedIdentifiers;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.CustomFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CustomFeatureRenderer.Submit.class)
public abstract class CustomSubmitMixin implements BlockEntityOrigin, SubmittedIdentifiers {

	@Unique
	private boolean corevk$blockEntity;

	@Unique
	private long corevk$identifiers;

	@Inject(method = "<init>", at = @At("RETURN"), require = 1)
	private void corevk$capture(PoseStack.Pose pose, RenderType renderType,
			SubmitNodeCollector.CustomGeometryRenderer renderer, CallbackInfo callback) {
		this.corevk$blockEntity = BlockEntityGeometry.submitting();
		this.corevk$identifiers = EntityIdentifiers.packed();
	}

	@Override
	public boolean corevk$fromBlockEntity() {
		return this.corevk$blockEntity;
	}

	@Override
	public long corevk$identifiers() {
		return this.corevk$identifiers;
	}
}
