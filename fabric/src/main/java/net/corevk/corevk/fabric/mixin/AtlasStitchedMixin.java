package net.corevk.corevk.fabric.mixin;

import net.corevk.corevk.render.pbr.PbrAtlases;

import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TextureAtlas.class)
public abstract class AtlasStitchedMixin {

	@Inject(method = "upload", at = @At("RETURN"), require = 1)
	private void corevk$stitched(SpriteLoader.Preparations preparations, CallbackInfo ci) {
		TextureAtlas atlas = (TextureAtlas) (Object) this;
		PbrAtlases.stitched(atlas.location(), atlas.getTexture(), preparations.regions().values());
	}
}
