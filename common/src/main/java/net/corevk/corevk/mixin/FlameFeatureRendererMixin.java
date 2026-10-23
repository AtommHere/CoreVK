package net.corevk.corevk.mixin;

import net.corevk.corevk.render.EntityIdentifiers;
import net.corevk.corevk.render.PackNameIds;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.feature.FlameFeatureRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FlameFeatureRenderer.class)
public abstract class FlameFeatureRendererMixin {

	@Inject(method = "prepare", at = @At("HEAD"), require = 1)
	private void corevk$begin(FlameFeatureRenderer.Submit submit, VertexConsumer buffer,
			TextureAtlasSprite first, TextureAtlasSprite second, CallbackInfo callback) {
		EntityIdentifiers.entity(PackNameIds.flame());
	}

	@Inject(method = "prepare", at = @At("RETURN"), require = 1)
	private void corevk$end(FlameFeatureRenderer.Submit submit, VertexConsumer buffer,
			TextureAtlasSprite first, TextureAtlasSprite second, CallbackInfo callback) {
		EntityIdentifiers.clear();
	}
}
