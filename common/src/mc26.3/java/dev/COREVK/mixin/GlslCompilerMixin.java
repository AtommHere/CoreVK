package net.corevk.corevk.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.renderpearl.api.pipeline.ShaderSource;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import com.mojang.renderpearl.backend.api.SpvModule;
import com.mojang.renderpearl.frontend.shaders.GlslCompiler;
import com.mojang.renderpearl.frontend.shaders.SPIRVModule;
import net.corevk.corevk.cache.ModuleCache;
import net.corevk.corevk.glsl.LoadClock;
import net.corevk.corevk.render.PackNames;
import net.corevk.corevk.render.RawLocals;
import net.corevk.corevk.render.SamplerReach;
import net.corevk.corevk.render.ShaderDebugInfo;
import net.minecraft.client.renderer.ShaderDefines;
import org.lwjgl.util.shaderc.Shaderc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.nio.ByteBuffer;
import java.util.Map;

@Mixin(GlslCompiler.class)
public abstract class GlslCompilerMixin {

	@WrapOperation(method = "createBaseShaderOptions", require = 1,
			at = @At(value = "INVOKE",
					target = "Lorg/lwjgl/util/shaderc/Shaderc;"
							+ "shaderc_compile_options_set_generate_debug_info(J)V"))
	private void corevk$skipDebugInfo(long options, Operation<Void> original) {
		ShaderDebugInfo.announce();
		if (ShaderDebugInfo.asked()) {
			original.call(options);
		}
	}

	@WrapOperation(method = "compileToSpv", require = 1,
			at = @At(value = "INVOKE",
					target = "Lcom/mojang/renderpearl/frontend/shaders/GlslCompiler;"
							+ "createBaseShaderOptions()J"))
	private long corevk$legacyOptions(GlslCompiler compiler, Operation<Long> original,
			@Local(argsOnly = true, ordinal = 0) String name) {
		long options = original.call(compiler);
		if (RawLocals.ours(debugName(name))) {
			Shaderc.shaderc_compile_options_set_auto_map_locations(options, true);
			Shaderc.shaderc_compile_options_add_macro_definition(options, "gl_VertexID",
					"gl_VertexIndex");
			Shaderc.shaderc_compile_options_add_macro_definition(options, "gl_InstanceID",
					"gl_InstanceIndex");
		}

		return options;
	}

	@WrapOperation(method = "compileToSpv", require = 1,
			at = @At(value = "NEW",
					target = "com/mojang/renderpearl/frontend/shaders/SPIRVModule"))
	private SPIRVModule corevk$zeroLocals(ByteBuffer spirv, ShaderType type,
			Operation<SPIRVModule> original, @Local(argsOnly = true, ordinal = 0) String name) {
		String filename = debugName(name);
		ByteBuffer patched = PackNames.patch(filename, RawLocals.patch(filename, spirv));
		SPIRVModule module = original.call(patched, type);

		SamplerReach.narrow(filename, patched, module);

		return module;
	}

	@WrapMethod(method = "compileToSpv", require = 1)
	private SpvModule corevk$module(String name, String source, ShaderType type,
			ShaderDefines defines, ShaderSource shaderSource, Operation<SpvModule> original) {
		long began = System.nanoTime();
		RawLocals.begin();
		try {
			String filename = debugName(name);
			String key = ModuleCache.keyOf(source, type.name(), corevk$defines(defines));
			ByteBuffer served = ModuleCache.lookup(key);
			if (served != null) {
				SPIRVModule module = new SPIRVModule(served, type);
				SamplerReach.narrow(filename, served, module);

				return module;
			}

			ModuleCache.building(filename);
			SpvModule built = original.call(name, source, type, defines, shaderSource);
			ModuleCache.store(key, built.spv());

			return built;
		} finally {
			RawLocals.end();
			LoadClock.module(System.nanoTime() - began);
		}
	}

	@Unique
	private static String corevk$defines(ShaderDefines defines) {
		StringBuilder described = new StringBuilder();
		defines.values().entrySet().stream()
				.sorted(Map.Entry.comparingByKey())
				.forEach(define -> described.append(define.getKey()).append('=')
						.append(define.getValue()).append('\n'));
		defines.flags().stream().sorted().forEach(flag -> described.append(flag).append('\n'));

		return described.toString();
	}

	@Unique
	private static String debugName(String name) {
		return name.replace('/', '_').replace(':', '_');
	}
}
