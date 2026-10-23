package net.corevk.corevk.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.vulkan.VulkanBindGroupLayout;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.glsl.GlslCompiler;
import com.mojang.blaze3d.vulkan.glsl.IntermediaryShaderModule;
import com.mojang.blaze3d.vulkan.glsl.ShaderCompileException;
import net.corevk.corevk.cache.ModuleCache;
import net.corevk.corevk.glsl.LoadClock;
import net.corevk.corevk.render.GeometryStage;
import net.corevk.corevk.render.PackNames;
import net.corevk.corevk.render.RawLocals;
import net.corevk.corevk.render.ShaderDebugInfo;
import net.corevk.corevk.render.storage.StorageImages;
import org.lwjgl.util.shaderc.Shaderc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.nio.ByteBuffer;
import java.util.List;

@Mixin(GlslCompiler.class)
public abstract class GlslCompilerMixin {

	@Unique
	private static final String GEOMETRY_STAGE = "GEOMETRY/shaderc-kind3";

	@Unique
	private static final int GEOMETRY_KIND = Shaderc.shaderc_glsl_geometry_shader;

	@Shadow
	private static void addToBindGroup(List<VulkanBindGroupLayout.Entry> entries,
			IntermediaryShaderModule shader, RenderPipeline pipeline) throws ShaderCompileException {
		throw new AssertionError();
	}

	@ModifyArg(method = "createIntermediary", require = 1, index = 2,
			at = @At(value = "INVOKE",
					target = "Lorg/lwjgl/util/shaderc/Shaderc;shaderc_compile_into_spv("
							+ "JLjava/nio/ByteBuffer;ILjava/nio/ByteBuffer;Ljava/nio/ByteBuffer;J)J"))
	private int corevk$geometryKind(int kind) {
		return GeometryStage.compiling() ? GEOMETRY_KIND : kind;
	}

	@WrapOperation(method = "compile", require = 1,
			at = @At(value = "INVOKE", ordinal = 1,
					target = "Lcom/mojang/blaze3d/vulkan/glsl/GlslCompiler;addToBindGroup("
							+ "Ljava/util/List;Lcom/mojang/blaze3d/vulkan/glsl/IntermediaryShaderModule;"
							+ "Lcom/mojang/blaze3d/pipeline/RenderPipeline;)V"))
	private void corevk$geometryResources(List<VulkanBindGroupLayout.Entry> entries,
			IntermediaryShaderModule fragment, RenderPipeline pipeline, Operation<Void> original)
			throws ShaderCompileException {
		original.call(entries, fragment, pipeline);
		IntermediaryShaderModule geometry =
				GeometryStage.begin((GlslCompiler) (Object) this, pipeline);
		if (geometry != null) {
			addToBindGroup(entries, geometry, pipeline);
		}
	}

	@WrapOperation(method = "compile", require = 1,
			at = @At(value = "INVOKE", ordinal = 1,
					target = "Lcom/mojang/blaze3d/vulkan/glsl/IntermediaryShaderModule;rebind("
							+ "Ljava/util/List;Ljava/util/List;)V"))
	private void corevk$rebindBetween(IntermediaryShaderModule fragment, List<String> written,
			List<VulkanBindGroupLayout.Entry> entries, Operation<Void> original)
			throws ShaderCompileException {
		IntermediaryShaderModule geometry = GeometryStage.building();
		if (geometry == null) {
			original.call(fragment, written, entries);

			return;
		}

		geometry.rebind(written, entries);
		original.call(fragment, GeometryStage.outputs(geometry), entries);
	}

	@WrapOperation(method = "compile", require = 1,
			at = @At(value = "INVOKE", ordinal = 1,
					target = "Lcom/mojang/blaze3d/vulkan/glsl/IntermediaryShaderModule;"
							+ "createVulkanShaderModule(Lcom/mojang/blaze3d/vulkan/VulkanDevice;)J"))
	private long corevk$geometryModule(IntermediaryShaderModule fragment, VulkanDevice device,
			Operation<Long> original) {
		long id = original.call(fragment, device);
		GeometryStage.built(device);

		return id;
	}

	@WrapOperation(method = "<init>", require = 1,
			at = @At(value = "INVOKE",
					target = "Lorg/lwjgl/util/shaderc/Shaderc;"
							+ "shaderc_compile_options_set_generate_debug_info(J)V"))
	private void corevk$skipDebugInfo(long options, Operation<Void> original) {
		ShaderDebugInfo.announce();
		if (ShaderDebugInfo.asked()) {
			original.call(options);
		}
	}

	@WrapOperation(method = "addToBindGroup", require = 1,
			at = @At(value = "INVOKE",
					target = "Lcom/mojang/blaze3d/vulkan/glsl/SpvSampler;dimensions()I"))
	private static int corevk$allowLineAndVolume(@Coerce Object sampler, Operation<Integer> original) {
		int dimension = original.call(sampler);
		return dimension == 0 || dimension == 2 ? 1 : dimension;
	}

	@WrapOperation(method = "createIntermediary", require = 1,
			at = @At(value = "INVOKE",
					target = "Lcom/mojang/blaze3d/vulkan/glsl/IntermediaryShaderModule;createFromSpirv("
							+ "Ljava/lang/String;Ljava/nio/ByteBuffer;)"
							+ "Lcom/mojang/blaze3d/vulkan/glsl/IntermediaryShaderModule;"))
	private IntermediaryShaderModule corevk$zeroLocals(String filename, ByteBuffer spirv,
			Operation<IntermediaryShaderModule> original) {

		return original.call(filename, PackNames.patch(filename, RawLocals.patch(filename, spirv)));
	}

	@WrapMethod(method = "createIntermediary", require = 1)
	private IntermediaryShaderModule corevk$module(String filename, String source, ShaderType type,
			Operation<IntermediaryShaderModule> original) {
		long began = System.nanoTime();

		RawLocals.begin();
		try {

			String key = ModuleCache.keyOf(source,
					GeometryStage.compiling() ? GEOMETRY_STAGE : type.name());
			IntermediaryShaderModule served = ModuleCache.lookup(key, filename);
			if (served != null) {
				return served;
			}

			ModuleCache.building(filename);
			IntermediaryShaderModule built = original.call(filename, source, type);
			ModuleCache.store(key, built);

			return built;
		} finally {
			RawLocals.end();
			LoadClock.module(System.nanoTime() - began);
		}
	}
}
