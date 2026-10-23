package net.corevk.corevk.mixin;

import net.corevk.corevk.render.PackChain;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GameRenderer.class)
public abstract class GameRendererWarmupMixin {

	@WrapOperation(method = "render", require = 1,
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel("
							+ "Lnet/minecraft/client/DeltaTracker;)V"))
	private void corevk$skipLevelWhilePackWarms(GameRenderer renderer, DeltaTracker delta,
			Operation<Void> original) {

		if (PackChain.beforeLevel() || PackChain.warming()) {
			PackChain.pumpWarmup();
			return;
		}

		original.call(renderer, delta);
	}
}
