package net.corevk.corevk.mixin;

import net.corevk.corevk.render.EntityIdentifiers;
import net.corevk.corevk.render.PackNameIds;
import net.corevk.corevk.render.TerrainDraw;

import java.util.List;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.ZombieVillagerRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {

	@Inject(method = "submit", at = @At("HEAD"), require = 1)
	private <S extends EntityRenderState> void corevk$begin(S state, CameraRenderState camera,
			double x, double y, double z, PoseStack poseStack, SubmitNodeCollector collector,
			CallbackInfo callback) {
		EntityIdentifiers.entity(corevk$identify(state));
	}

	@Inject(method = "submit", at = @At("RETURN"), require = 1)
	private <S extends EntityRenderState> void corevk$end(S state, CameraRenderState camera,
			double x, double y, double z, PoseStack poseStack, SubmitNodeCollector collector,
			CallbackInfo callback) {
		EntityIdentifiers.entity(0);

		// The held item goes down with the entity that was holding it, and not at the end of the item
		// itself: an item is submitted inside this call and its own window closes first, so leaving
		// this out would keep the last item's number over everything after it. CoreVK drops the two
		// together here for the same reason (MixinEntityRenderDispatcher.java:88-89).
		EntityIdentifiers.item(0);
	}

	
	@WrapWithCondition(method = "submit",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;"
							+ "submitShadow(Lcom/mojang/blaze3d/vertex/PoseStack;FLjava/util/List;)V"),
			require = 1)
	private static boolean corevk$keepGroundOval(SubmitNodeCollector collector, PoseStack poseStack,
			float radius, List<EntityRenderState.ShadowPiece> pieces) {
		return !TerrainDraw.shadowMapServed();
	}

	
	private static int corevk$identify(EntityRenderState state) {
		if (state instanceof ZombieVillagerRenderState villager && villager.isConverting
				&& PackNameIds.namesConvertingVillager()) {
			return PackNameIds.convertingVillager();
		}

		if (state instanceof AvatarRenderState avatar && PackNameIds.namesCurrentPlayer()
				&& Minecraft.getInstance().getCameraEntity() instanceof AbstractClientPlayer camera
				&& camera.getId() == avatar.id) {
			return PackNameIds.currentPlayer();
		}

		return PackNameIds.entity(state.entityType);
	}
}
