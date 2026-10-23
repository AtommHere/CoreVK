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
		// Before the branch and not inside one of its arms: the question is owed whether the pack
		// is compiling or drawing, and this is the one line both roads pass through. A frame that
		// reads the pack again owes the level nothing, for the reason beforeLevel gives: the chunk
		// format of this frame was settled from the pack that has just been replaced.
		if (PackChain.beforeLevel() || PackChain.warming()) {
			PackChain.pumpWarmup();
			return;
		}

		original.call(renderer, delta);
	}
}
