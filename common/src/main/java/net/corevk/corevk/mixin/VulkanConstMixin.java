package net.corevk.corevk.mixin;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.vulkan.VulkanConst;
import net.corevk.corevk.render.TextureUsage;
import org.lwjgl.vulkan.VK10;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(VulkanConst.class)
public abstract class VulkanConstMixin {

	@Inject(method = "textureUsageToVk(ILcom/mojang/blaze3d/GpuFormat;)I", at = @At("RETURN"),
			cancellable = true)
	private static void corevk$storageUsage(int usage, GpuFormat format,
			CallbackInfoReturnable<Integer> info) {
		if (TextureUsage.storageRequested()) {
			info.setReturnValue(info.getReturnValueI() | VK10.VK_IMAGE_USAGE_STORAGE_BIT);
		}
	}
}
