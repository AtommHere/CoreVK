package net.corevk.corevk.mixin.game;

import net.corevk.corevk.render.HandDraw;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GameRenderer.class)
public abstract class GameRendererHandMixin {

	
	@WrapOperation(method = "renderItemInHand", require = 1,
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/FirstPersonHandsAndItemsRenderer;"
							+ "submitHandsWithItems("
							+ "FLcom/mojang/blaze3d/vertex/PoseStack;"
							+ "Lnet/minecraft/client/renderer/SubmitNodeCollector;"
							+ "Lnet/minecraft/client/renderer/state/level/PlayerRenderState;"
							+ "Lnet/minecraft/client/renderer/state/level/FirstPersonHandsAndItemsRenderState;"
							+ ")V"))
	private void corevk$moveTheHand(FirstPersonHandsAndItemsRenderer renderer, float partialTicks,
			PoseStack poseStack, SubmitNodeCollector collector, PlayerRenderState playerState,
			FirstPersonHandsAndItemsRenderState state, Operation<Void> original) {
		if (!HandDraw.diverted()) {
			original.call(renderer, partialTicks, poseStack, collector, playerState, state);
		}
	}
}
