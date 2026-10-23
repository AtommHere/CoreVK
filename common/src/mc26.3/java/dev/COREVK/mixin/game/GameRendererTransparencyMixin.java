package net.corevk.corevk.mixin.game;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.corevk.corevk.render.PackChain;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GameRenderer.class)
public abstract class GameRendererTransparencyMixin {

	@ModifyReturnValue(method = "useImprovedTransparency", at = @At("RETURN"), require = 1)
	private boolean corevk$classicUnderPack(boolean improved) {
		return improved && !PackChain.drawingPack();
	}
}
