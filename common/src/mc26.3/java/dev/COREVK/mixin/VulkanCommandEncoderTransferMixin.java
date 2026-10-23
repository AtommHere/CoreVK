package net.corevk.corevk.mixin;

import net.corevk.corevk.render.timing.TransferProbe;

import com.mojang.renderpearl.backend.vulkan.VulkanCommandEncoder;
import com.mojang.renderpearl.backend.vulkan.VulkanRenderPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Supplier;

@Mixin(VulkanCommandEncoder.class)
public abstract class VulkanCommandEncoderTransferMixin {

	@Shadow
	private VulkanRenderPass currentRenderPass;

	@Unique
	private Supplier<String> corevk$open() {
		return this.currentRenderPass == null ? null : this.currentRenderPass.getLabel();
	}

	@Inject(method = "writeToBuffer(Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Ljava/nio/ByteBuffer;)V",
			at = @At("HEAD"))
	private void corevk$writeToBuffer(CallbackInfo ci) {
		TransferProbe.seen("writeToBuffer", corevk$open());
	}

	@Inject(method = "copyToBuffer(Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;"
			+ "Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;)V", at = @At("HEAD"))
	private void corevk$copyToBuffer(CallbackInfo ci) {
		TransferProbe.seen("copyToBuffer", corevk$open());
	}

	@Inject(method = "writeToTexture(Lcom/mojang/renderpearl/api/textures/GpuTexture;Ljava/nio/ByteBuffer;IIIIII)V",
			at = @At("HEAD"))
	private void corevk$writeToTexture(CallbackInfo ci) {
		TransferProbe.seen("writeToTexture", corevk$open());
	}

	@Inject(method = "copyBufferToTexture(Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;IIII"
			+ "Lcom/mojang/renderpearl/api/textures/GpuTexture;IIIIII)V", at = @At("HEAD"))
	private void corevk$copyBufferToTexture(CallbackInfo ci) {
		TransferProbe.seen("copyBufferToTexture", corevk$open());
	}

	@Inject(method = "copyTextureToBuffer(Lcom/mojang/renderpearl/api/textures/GpuTexture;"
			+ "Lcom/mojang/renderpearl/api/buffers/GpuBuffer;JLjava/lang/Runnable;IIIII)V", at = @At("HEAD"))
	private void corevk$copyTextureToBuffer(CallbackInfo ci) {
		TransferProbe.seen("copyTextureToBuffer", corevk$open());
	}

	@Inject(method = "copyTextureToTexture(Lcom/mojang/renderpearl/api/textures/GpuTexture;"
			+ "Lcom/mojang/renderpearl/api/textures/GpuTexture;IIIIIII)V", at = @At("HEAD"))
	private void corevk$copyTextureToTexture(CallbackInfo ci) {
		TransferProbe.seen("copyTextureToTexture", corevk$open());
	}

	@Inject(method = "clearColorTexture(Lcom/mojang/renderpearl/api/textures/GpuTexture;Lorg/joml/Vector4fc;)V",
			at = @At("HEAD"))
	private void corevk$clearColorTexture(CallbackInfo ci) {
		TransferProbe.seen("clearColorTexture", corevk$open());
	}

	@Inject(method = "clearDepthTexture(Lcom/mojang/renderpearl/api/textures/GpuTexture;D)V", at = @At("HEAD"))
	private void corevk$clearDepthTexture(CallbackInfo ci) {
		TransferProbe.seen("clearDepthTexture", corevk$open());
	}

	@Inject(method = "clearColorAndDepthTextures(Lcom/mojang/renderpearl/api/textures/GpuTexture;"
			+ "Lorg/joml/Vector4fc;Lcom/mojang/renderpearl/api/textures/GpuTexture;D)V", at = @At("HEAD"))
	private void corevk$clearColorAndDepthTextures(CallbackInfo ci) {
		TransferProbe.seen("clearColorAndDepthTextures", corevk$open());
	}

	@Inject(method = "clearColorAndDepthTextures(Lcom/mojang/renderpearl/api/textures/GpuTexture;"
			+ "Lorg/joml/Vector4fc;Lcom/mojang/renderpearl/api/textures/GpuTexture;DIIIII)V", at = @At("HEAD"))
	private void corevk$clearColorAndDepthRegion(CallbackInfo ci) {
		TransferProbe.seen("clearColorAndDepthTextures (region)", corevk$open());
	}

	@Inject(method = "createRenderPass(Lcom/mojang/renderpearl/api/commands/RenderPassDescriptor;)"
			+ "Lcom/mojang/renderpearl/backend/api/RenderPassBackend;", at = @At("HEAD"))
	private void corevk$createRenderPass(CallbackInfoReturnable<?> cir) {
		TransferProbe.seen("createRenderPass", corevk$open());
	}
}
