package net.corevk.corevk.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vulkan.VulkanBindGroupLayout;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanRenderPipeline;
import net.corevk.corevk.render.GeometryStage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.Struct;
import org.lwjgl.system.StructBuffer;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(VulkanRenderPipeline.class)
public abstract class VulkanRenderPipelineMixin {

	@WrapOperation(method = "compile", require = 1,
			at = @At(value = "INVOKE",
					target = "Lorg/lwjgl/vulkan/VkPipelineShaderStageCreateInfo;calloc("
							+ "ILorg/lwjgl/system/MemoryStack;)"
							+ "Lorg/lwjgl/vulkan/VkPipelineShaderStageCreateInfo$Buffer;"))
	private static VkPipelineShaderStageCreateInfo.Buffer corevk$roomForGeometry(int stages,
			MemoryStack stack, Operation<VkPipelineShaderStageCreateInfo.Buffer> original,
			@Local(argsOnly = true) RenderPipeline pipeline) {
		return original.call(GeometryStage.pending(pipeline) == 0L ? stages : stages + 1, stack);
	}

	@SuppressWarnings("rawtypes")
	@WrapOperation(method = "compile", require = 1,
			at = @At(value = "INVOKE", ordinal = 1,
					target = "Lorg/lwjgl/vulkan/VkPipelineShaderStageCreateInfo$Buffer;put("
							+ "Lorg/lwjgl/system/Struct;)Lorg/lwjgl/system/StructBuffer;"))
	private static StructBuffer corevk$putGeometry(VkPipelineShaderStageCreateInfo.Buffer stages,
			Struct fragment, Operation<StructBuffer> original,
			@Local(argsOnly = true) RenderPipeline pipeline, @Local MemoryStack stack) {
		StructBuffer put = original.call(stages, fragment);
		long geometry = GeometryStage.pending(pipeline);
		if (geometry != 0L) {
			stages.put(VkPipelineShaderStageCreateInfo.calloc(stack)
					.sType$Default()
					.stage(GeometryStage.STAGE_BIT)
					.module(geometry)
					.pName(stack.UTF8("main")));
		}

		return put;
	}

	@Inject(method = "compile", at = @At("RETURN"), require = 1)
	private static void corevk$geometryTaken(VulkanDevice device, VulkanBindGroupLayout layout,
			RenderPipeline pipeline, long vertexModule, long fragmentModule,
			CallbackInfoReturnable<VulkanRenderPipeline> callback) {
		GeometryStage.taken(pipeline);
	}
}
