package net.corevk.corevk.mixin;

import net.corevk.corevk.screen.SettingsScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(GameRenderer.class)
public abstract class GameRendererBlurMixin {

	@ModifyArg(method = "render",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/GlobalSettingsUniform;"
							+ "update(IIDJFI"
							+ "Lnet/minecraft/world/phys/Vec3;Z)V"),
			index = 5, require = 1)
	private int corevk$fadeMenuBlur(int radius) {
		return Minecraft.getInstance().gui.screen() instanceof SettingsScreen settings
				? Math.min(radius, settings.blurRadius())
				: radius;
	}
}
