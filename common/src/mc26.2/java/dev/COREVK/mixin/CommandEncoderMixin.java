package net.corevk.corevk.mixin;

import net.corevk.corevk.render.GeometryHold;
import net.corevk.corevk.render.timing.PassTimings;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CommandEncoder.class)
public abstract class CommandEncoderMixin {

	@Inject(method = "createRenderPass(Lcom/mojang/blaze3d/systems/RenderPassDescriptor;)"
			+ "Lcom/mojang/blaze3d/systems/RenderPass;",
			at = @At("HEAD"),
			cancellable = true,
			require = 1)
	private void corevk$flushHold(RenderPassDescriptor descriptor,
			CallbackInfoReturnable<RenderPass> cir) {
		RenderPass leftover = GeometryHold.leftover(descriptor);
		if (leftover != null) {
			cir.setReturnValue(leftover);

			return;
		}

		if (!GeometryHold.opening()) {
			// The pass's own label is the cause, and it is already a supplier: naming it costs
			// nothing until a census asks, which is why the prefix is added there and not here.
			GeometryHold.flush(descriptor.label());
		}
	}

	@Inject(method = "createRenderPass(Lcom/mojang/blaze3d/systems/RenderPassDescriptor;)"
			+ "Lcom/mojang/blaze3d/systems/RenderPass;",
			at = @At(value = "INVOKE",
					target = "Lcom/mojang/blaze3d/systems/CommandEncoderBackend;createRenderPass("
							+ "Lcom/mojang/blaze3d/systems/RenderPassDescriptor;)"
							+ "Lcom/mojang/blaze3d/systems/RenderPassBackend;"),
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
		GeometryHold.flush(() -> "a texture clear");
		PassTimings.censusClear();
	}

	@Inject(method = "clearDepthTexture", at = @At("HEAD"), require = 1)
	private void corevk$clearDepth(CallbackInfo ci) {
		GeometryHold.flush(() -> "a texture clear");
		PassTimings.censusClear();
	}

	@Inject(method = "clearColorAndDepthTextures("
			+ "Lcom/mojang/blaze3d/textures/GpuTexture;Lorg/joml/Vector4fc;"
			+ "Lcom/mojang/blaze3d/textures/GpuTexture;D)V",
			at = @At("HEAD"), require = 1)
	private void corevk$clearColourAndDepth(CallbackInfo ci) {
		GeometryHold.flush(() -> "a texture clear");
		PassTimings.censusClear();
	}

	@Inject(method = "clearColorAndDepthTextures("
			+ "Lcom/mojang/blaze3d/textures/GpuTexture;Lorg/joml/Vector4fc;"
			+ "Lcom/mojang/blaze3d/textures/GpuTexture;DIIII)V",
			at = @At("HEAD"), require = 1)
	private void corevk$clearColourAndDepthRegion(CallbackInfo ci) {
		GeometryHold.flush(() -> "a texture clear");
		PassTimings.censusClear();
	}

	@Inject(method = "copyTextureToTexture", at = @At("HEAD"), require = 1)
	private void corevk$copyTexture(CallbackInfo ci) {
		GeometryHold.flush(() -> "a texture copy");
		PassTimings.censusCopy();
	}

	
	@Inject(method = {
			"writeToBuffer(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Ljava/nio/ByteBuffer;)V",
			"copyToBuffer(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V",
			"writeToTexture(Lcom/mojang/blaze3d/textures/GpuTexture;Lcom/mojang/blaze3d/platform/NativeImage;"
					+ "IIII)V",
			"writeToTexture(Lcom/mojang/blaze3d/textures/GpuTexture;Ljava/nio/ByteBuffer;IIIIII)V",
			"copyBufferToTexture(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;IIII"
					+ "Lcom/mojang/blaze3d/textures/GpuTexture;IIIIII)V",
			"copyTextureToBuffer(Lcom/mojang/blaze3d/textures/GpuTexture;Lcom/mojang/blaze3d/buffers/GpuBuffer;"
					+ "JLjava/lang/Runnable;I)V",
			"copyTextureToBuffer(Lcom/mojang/blaze3d/textures/GpuTexture;Lcom/mojang/blaze3d/buffers/GpuBuffer;"
					+ "JLjava/lang/Runnable;IIIII)V"},
			at = @At("HEAD"), require = 7)
	private void corevk$transfer(CallbackInfo ci) {
		GeometryHold.flushIdle(() -> "a buffer or texture transfer");
	}
}
