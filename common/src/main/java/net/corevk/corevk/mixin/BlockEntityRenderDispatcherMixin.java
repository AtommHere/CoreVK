package net.corevk.corevk.mixin;

import net.corevk.corevk.mixin.access.BlockEntityRenderStateAccessor;
import net.corevk.corevk.render.BlockEntityGeometry;
import net.corevk.corevk.render.BlockStateIds;
import net.corevk.corevk.render.EntityIdentifiers;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherMixin {

	@Inject(method = "submit", at = @At("HEAD"), require = 1)
	private void corevk$begin(BlockEntityRenderState state, PoseStack poseStack,
			SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo callback) {
		BlockEntityGeometry.submitting(true);

		// The number is the pack's for the BLOCK STATE this thing stands in, out of block.properties
		// and not out of a table of its own, which is CoreVK's answer as well
		// (MixinBlockEntityRenderDispatcher.java:61). Raw, where the terrain mesh carries the same
		// table packed: the shader unpacks the terrain's own word and reads this one as it stands.
		EntityIdentifiers.blockEntity(
				BlockStateIds.id(((BlockEntityRenderStateAccessor) state).corevk$blockState()));
	}

	@Inject(method = "submit", at = @At("RETURN"), require = 1)
	private void corevk$end(BlockEntityRenderState state, PoseStack poseStack,
			SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo callback) {
		BlockEntityGeometry.submitting(false);
		EntityIdentifiers.blockEntity(0);
	}
}
