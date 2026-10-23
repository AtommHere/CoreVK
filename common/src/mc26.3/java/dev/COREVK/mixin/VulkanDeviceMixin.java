package net.corevk.corevk.mixin;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import net.corevk.corevk.render.GeometryHold;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(VulkanDevice.class)
public abstract class VulkanDeviceMixin {

	@Inject(method = "createTexture(Ljava/lang/String;ILcom/mojang/renderpearl/api/GpuFormat;IIII)"
			+ "Lcom/mojang/renderpearl/api/textures/GpuTexture;", at = @At("HEAD"), require = 1)
	private void corevk$flushBeforeTextureAllocation(CallbackInfoReturnable<GpuTexture> callback) {
		GeometryHold.flushIdle(() -> "texture allocation");
	}

	@Inject(method = "createBuffer(Ljava/util/function/Supplier;ILjava/nio/ByteBuffer;)"
			+ "Lcom/mojang/renderpearl/api/buffers/GpuBuffer;", at = @At("HEAD"), require = 1)
	private void corevk$flushBeforeBufferUpload(CallbackInfoReturnable<GpuBuffer> callback) {
		GeometryHold.flushIdle(() -> "initial buffer upload");
	}
}
