package net.corevk.corevk.mixin;

import net.corevk.corevk.screen.CompileCard;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class HudCompileIconMixin {

	@Inject(method = "extractRenderState", at = @At("TAIL"), require = 1)
	private void corevk$compileIcon(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker,
			CallbackInfo callback) {
		CompileCard.extract(graphics);
	}
}
