package net.corevk.corevk.mixin;

import net.corevk.corevk.render.FaceShading;

import net.minecraft.core.Direction;
import net.minecraft.world.level.CardinalLighting;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(CardinalLighting.class)
public class CardinalLightingMixin {

	@ModifyVariable(method = "byFace", at = @At("HEAD"), argsOnly = true, require = 1)
	private Direction corevk$topFaceWhileAPackShades(Direction face) {
		return FaceShading.dropped() ? Direction.UP : face;
	}
}
