package net.corevk.corevk.mixin;

import net.corevk.corevk.render.GeometryHold;
import net.corevk.corevk.render.LevelPass;
import net.corevk.corevk.render.timing.PassTimings;

import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.frontend.FrontendCommandEncoder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FrontendCommandEncoder.class)
public abstract class CommandEncoderMixin {

	@Inject(method = "createRenderPass(Lcom/mojang/renderpearl/api/commands/RenderPassDescriptor;)"
			+ "Lcom/mojang/renderpearl/api/commands/RenderPass;",
			at = @At("HEAD"),
			cancellable = true,
			require = 1)
	private void corevk$flushHold(RenderPassDescriptor descriptor,
			CallbackInfoReturnable<RenderPass> cir) {

		LevelPass.suspendCurrent();
		RenderPass leftover = GeometryHold.leftover(descriptor);
		if (leftover != null) {
			cir.setReturnValue(leftover);

			return;
		}

		if (!GeometryHold.opening()) {

			GeometryHold.flush(descriptor.label());
		}
	}

	@Inject(method = "createRenderPass(Lcom/mojang/renderpearl/api/commands/RenderPassDescriptor;)"
			+ "Lcom/mojang/renderpearl/api/commands/RenderPass;",
			at = @At(value = "INVOKE",
					target = "Lcom/mojang/renderpearl/backend/api/CommandEncoderBackend;createRenderPass("
							+ "Lcom/mojang/renderpearl/api/commands/RenderPassDescriptor;)"
							+ "Lcom/mojang/renderpearl/backend/api/RenderPassBackend;"),
			require = 1)
	private void corevk$openPass(RenderPassDescriptor descriptor, CallbackInfoReturnable<RenderPass> cir) {
		PassTimings.open((CommandEncoder) (Object) this, descriptor.label());
	}

	@Inject(method = "submitRenderPass", at = @At("TAIL"), require = 1)
	private void corevk$closePass(CallbackInfo ci) {
		PassTimings.close((CommandEncoder) (Object) this);
	}

	@Inject(method = "clearColorTexture", at = @At("HEAD"), require = 1)
	private void corevk$clearColour(CallbackInfo ci) {
		LevelPass.suspendCurrent();
		GeometryHold.flush(() -> "a texture clear");
		PassTimings.censusClear();
	}

	@Inject(method = "clearDepthTexture", at = @At("HEAD"), require = 1)
	private void corevk$clearDepth(CallbackInfo ci) {
		LevelPass.suspendCurrent();
		GeometryHold.flush(() -> "a texture clear");
		PassTimings.censusClear();
	}

	@Inject(method = "clearColorAndDepthTextures("
			+ "Lcom/mojang/renderpearl/api/textures/GpuTexture;Lorg/joml/Vector4fc;"
			+ "Lcom/mojang/renderpearl/api/textures/GpuTexture;D)V",
			at = @At("HEAD"), require = 1)
	private void corevk$clearColourAndDepth(CallbackInfo ci) {
		LevelPass.suspendCurrent();
		GeometryHold.flush(() -> "a texture clear");
		PassTimings.censusClear();
	}

	@Inject(method = "clearColorAndDepthTextures("
			+ "Lcom/mojang/renderpearl/api/textures/GpuTexture;Lorg/joml/Vector4fc;"
			+ "Lcom/mojang/renderpearl/api/textures/GpuTexture;DIIIII)V",
			at = @At("HEAD"), require = 1)
	private void corevk$clearColourAndDepthRegion(CallbackInfo ci) {
		LevelPass.suspendCurrent();
		GeometryHold.flush(() -> "a texture clear");
		PassTimings.censusClear();
	}

	@Inject(method = "createFence", at = @At("HEAD"), require = 1)
	private void corevk$fence(CallbackInfoReturnable<?> cir) {
		LevelPass.suspendCurrent();
		GeometryHold.flush(() -> "a fence");
	}

	@Inject(method = "copyTextureToTexture", at = @At("HEAD"), require = 1)
	private void corevk$copyTexture(CallbackInfo ci) {
		LevelPass.suspendCurrent();
		GeometryHold.flush(() -> "a texture copy");
		PassTimings.censusCopy();
	}

	@Inject(method = {
			"writeToBuffer(Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Ljava/nio/ByteBuffer;)V",
			"copyToBuffer(Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;)V",
			"writeToTexture(Lcom/mojang/renderpearl/api/textures/GpuTexture;Lcom/mojang/blaze3d/platform/NativeImage;"
					+ "IIII)V",
			"writeToTexture(Lcom/mojang/renderpearl/api/textures/GpuTexture;Ljava/nio/ByteBuffer;IIIIII)V",
			"copyBufferToTexture(Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;IIII"
					+ "Lcom/mojang/renderpearl/api/textures/GpuTexture;IIIIII)V",
			"copyTextureToBuffer(Lcom/mojang/renderpearl/api/textures/GpuTexture;Lcom/mojang/renderpearl/api/buffers/GpuBuffer;"
					+ "JLjava/lang/Runnable;I)V",
			"copyTextureToBuffer(Lcom/mojang/renderpearl/api/textures/GpuTexture;Lcom/mojang/renderpearl/api/buffers/GpuBuffer;"
					+ "JLjava/lang/Runnable;IIIII)V"},
			at = @At("HEAD"), require = 7)
	private void corevk$transfer(CallbackInfo ci) {
		LevelPass.suspendCurrent();
		GeometryHold.flushIdle(() -> "a buffer or texture transfer");
	}
}
