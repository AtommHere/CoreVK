package net.corevk.corevk.mixin.game;

import net.corevk.corevk.render.HandDraw;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GameRenderer.class)
public abstract class GameRendererHandMixin {

	@WrapOperation(method = "renderItemInHand", require = 1,
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;submitHandsWithItems("
							+ "FLcom/mojang/blaze3d/vertex/PoseStack;"
							+ "Lnet/minecraft/client/renderer/SubmitNodeCollector;"
							+ "Lnet/minecraft/client/player/LocalPlayer;I)V"))
	private void corevk$moveTheHand(ItemInHandRenderer renderer, float partialTick,
			PoseStack poseStack, SubmitNodeCollector collector, LocalPlayer player, int light,
			Operation<Void> original) {
		if (!HandDraw.diverted()) {
			original.call(renderer, partialTick, poseStack, collector, player, light);
		}
	}
}
