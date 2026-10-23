package net.corevk.corevk.mixin;

import net.corevk.corevk.render.EntityIdentifiers;
import net.corevk.corevk.render.SubmittedIdentifiers;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ItemFeatureRenderer.Submit.class)
public abstract class ItemSubmitMixin implements SubmittedIdentifiers {

	@Unique
	private long corevk$identifiers;

	@Inject(method = "<init>", at = @At("RETURN"), require = 1)
	private void corevk$capture(PoseStack.Pose pose, ItemDisplayContext displayContext,
			int lightCoords, int overlayCoords, int outlineColor, int[] tintLayers,
			List<BakedQuad> quads, ItemStackRenderState.FoilType foilType, CallbackInfo callback) {
		this.corevk$identifiers = EntityIdentifiers.packed();
	}

	@Override
	public long corevk$identifiers() {
		return this.corevk$identifiers;
	}
}
