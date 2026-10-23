package net.corevk.corevk.render;

import net.corevk.corevk.cache.ModuleCache;
import net.corevk.corevk.glsl.LoadClock;

import com.mojang.renderpearl.api.pipeline.ShaderType;
import com.mojang.renderpearl.backend.api.SpvModule;
import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import com.mojang.renderpearl.frontend.shaders.SPIRVModule;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.spvc.Spvc;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;

import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class ComputeShader {

	private ComputeShader() {
	}

	
	public record Binding(boolean buffer, String name) {
	}

	
	public record Compiled(long module, List<Binding> entries) {
	}

	
	public static @Nullable Compiled build(VulkanDevice vulkan, String label, String source,
			String stage, Function<String, @Nullable ByteBuffer> compile) throws Exception {
		long began = System.nanoTime();
		RawLocals.begin();
		try {
			ByteBuffer spirv = compile.apply(source);
			ModuleCache.building(label);
			if (spirv == null) {
				return null;
			}

			// The same zeroes the game's compiler road gets in GlslCompilerMixin, and before the
			// reflection, which has to read the module the driver will read.
			try (SPIRVModule module = new SPIRVModule(RawLocals.patch(label, spirv),
					ShaderType.VERTEX)) {
				return compile(vulkan, module);
			}
		} finally {
			RawLocals.end();
			LoadClock.module(System.nanoTime() - began);
		}
	}

	
	private static Compiled compile(VulkanDevice vulkan, SpvModule module) throws Exception {
		SpvModule.Reflection reflection = module.reflect();
		List<Binding> bindings = new ArrayList<>();
		int binding = 0;
		for (int type : new int[] {Spvc.SPVC_RESOURCE_TYPE_UNIFORM_BUFFER,
				Spvc.SPVC_RESOURCE_TYPE_STORAGE_BUFFER, Spvc.SPVC_RESOURCE_TYPE_STORAGE_IMAGE,
				Spvc.SPVC_RESOURCE_TYPE_SAMPLED_IMAGE}) {
			boolean buffer = type == Spvc.SPVC_RESOURCE_TYPE_UNIFORM_BUFFER
					|| type == Spvc.SPVC_RESOURCE_TYPE_STORAGE_BUFFER;
			for (SpvModule.Reflection.Descriptor descriptor : reflection.descriptors(type)) {
				descriptor.descriptorSetIndex(0);
				descriptor.binding(binding++);
				bindings.add(new Binding(buffer, descriptor.name()));
			}
		}

		for (int type : new int[] {Spvc.SPVC_RESOURCE_TYPE_SEPARATE_IMAGE,
				Spvc.SPVC_RESOURCE_TYPE_SEPARATE_SAMPLERS}) {
			if (!reflection.descriptors(type).isEmpty()) {
				throw new IllegalStateException("a separate image or sampler, which the engine "
						+ "binds on no road, is declared by " + reflection.descriptors(type)
						.getFirst().name());
			}
		}

		try (MemoryStack stack = MemoryStack.stackPush()) {
			VkShaderModuleCreateInfo info = VkShaderModuleCreateInfo.calloc(stack).sType$Default()
					.pCode(module.spv());
			LongBuffer handle = stack.callocLong(1);
			int result = VK12.vkCreateShaderModule(vulkan.vkDevice(), info, null, handle);
			if (result != VK12.VK_SUCCESS) {
				throw new IllegalStateException("vkCreateShaderModule answered " + result);
			}

			return new Compiled(handle.get(0), List.copyOf(bindings));
		}
	}
}
