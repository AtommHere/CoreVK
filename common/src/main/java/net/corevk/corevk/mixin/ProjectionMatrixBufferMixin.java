package net.corevk.corevk.mixin;

import net.corevk.corevk.render.CapturedProjection;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ProjectionMatrixBuffer.class)
public class ProjectionMatrixBufferMixin {

	@Inject(method = "getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;",
			at = @At("HEAD"))
	private void corevk$capture(Matrix4f projectionMatrix,
			CallbackInfoReturnable<GpuBufferSlice> callback) {
		CapturedProjection.capture(projectionMatrix);
	}
}
