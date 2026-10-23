package net.corevk.corevk.mixin.game;

import net.corevk.corevk.render.HandDraw;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {

	@Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true, require = 1)
	private void corevk$oneHalfAtATime(AbstractClientPlayer player, float frameInterp, float xRot,
			InteractionHand hand, float attack, ItemStack itemStack, float inverseArmHeight,
			PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords,
			CallbackInfo callback) {
		if (HandDraw.skip(itemStack)) {
			callback.cancel();
		}
	}
}
