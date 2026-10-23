package net.corevk.corevk.mixin.game;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vulkan.glsl.IntermediaryShaderModule;
import net.corevk.corevk.render.ComputeShader;
import net.corevk.corevk.render.SamplerReach;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.ByteBuffer;

@Mixin(IntermediaryShaderModule.class)
public abstract class IntermediaryShaderModuleMixin {

	@Inject(method = "createFromSpirv", at = @At("RETURN"), require = 1)
	private static void corevk$storageImages(String filename, ByteBuffer spirv,
			CallbackInfoReturnable<IntermediaryShaderModule> callback) {
		SamplerReach.narrow(filename, callback.getReturnValue());
		ComputeShader.appendStorageImages(callback.getReturnValue());
		ComputeShader.appendStorageBuffers(callback.getReturnValue());
	}

	@WrapOperation(method = "rebind", require = 1,
			at = @At(value = "INVOKE",
					target = "Lcom/mojang/blaze3d/vulkan/glsl/SpvSampler;dimensions()I"))
	private int corevk$allowLineAndVolume(@Coerce Object sampler, Operation<Integer> original) {
		int dimension = original.call(sampler);
		return dimension == 0 || dimension == 2 ? 1 : dimension;
	}
}
