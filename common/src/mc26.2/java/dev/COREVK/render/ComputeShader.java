package net.corevk.corevk.render;

import net.corevk.corevk.cache.ModuleCache;
import net.corevk.corevk.glsl.LoadClock;
import net.corevk.corevk.mixin.game.IntermediaryShaderModuleAccessor;

import com.mojang.blaze3d.vulkan.VulkanBindGroupLayout;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.glsl.IntermediaryShaderModule;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.spvc.Spvc;
import org.lwjgl.util.spvc.SpvcReflectedResource;

import org.jspecify.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class ComputeShader {

	
	private static final int STORAGE_IMAGE = Spvc.SPVC_RESOURCE_TYPE_STORAGE_IMAGE;

	
	private static final int STORAGE_BUFFER = Spvc.SPVC_RESOURCE_TYPE_STORAGE_BUFFER;

	private static final Constructor<?> SAMPLER;
	private static final Constructor<?> UNIFORM;
	private static final Method SAMPLER_NAME;
	private static final Method UNIFORM_NAME;

	static {
		try {
			Class<?> sampler = Class.forName("com.mojang.blaze3d.vulkan.glsl.SpvSampler");
			SAMPLER = sampler.getDeclaredConstructor(String.class, int.class, int.class);
			SAMPLER.setAccessible(true);
			SAMPLER_NAME = sampler.getDeclaredMethod("name");
			SAMPLER_NAME.setAccessible(true);
			Class<?> uniform = Class.forName("com.mojang.blaze3d.vulkan.glsl.SpvUniformBuffer");
			UNIFORM = uniform.getDeclaredConstructor(String.class, int.class);
			UNIFORM.setAccessible(true);
			UNIFORM_NAME = uniform.getDeclaredMethod("name");
			UNIFORM_NAME.setAccessible(true);
		} catch (ReflectiveOperationException e) {
			throw new ExceptionInInitializerError(e);
		}
	}

	private ComputeShader() {
	}

	
	public record Binding(boolean buffer, String name) {
	}

	
	public record Compiled(long module, List<Binding> entries) {
	}

	
	public static @Nullable Compiled build(VulkanDevice vulkan, String label, String source,
			String stage, Function<String, @Nullable ByteBuffer> compile) throws Exception {
		long began = System.nanoTime();
		IntermediaryShaderModule module = null;
		// One state for the key and the patch, as the game's compiler road takes it.
		RawLocals.begin();
		try {
			String key = ModuleCache.keyOf(source, stage);
			module = ModuleCache.lookup(key, label);
			if (module == null) {
				ByteBuffer spirv = compile.apply(source);
				ModuleCache.building(label);
				if (spirv == null) {
					return null;
				}

				// The same zeroes the game's compiler road gets in GlslCompilerMixin: this road
				// has its own shaderc call, so it has to ask for them itself, and before the
				// reflection and the store, so a served blob carries them too. Compiled at the
				// performance level, this module has mostly values where that road has variables,
				// and its undefined reads are what the pass turns into zeroes here.
				module = IntermediaryShaderModule.createFromSpirv(label,
						RawLocals.patch(label, spirv));
				ModuleCache.store(key, module);
			}

			return compile(vulkan, module);
		} finally {
			RawLocals.end();
			if (module != null) {
				module.close();
			}

			LoadClock.module(System.nanoTime() - began);
		}
	}

	
	@SuppressWarnings({"rawtypes", "unchecked"})
	public static void appendStorageImages(IntermediaryShaderModule module) {
		if (module == null || module.spirv() == null) {
			return;
		}

		List extra = listStorage(module.spirv());
		if (!extra.isEmpty()) {
			List samplers = ((IntermediaryShaderModuleAccessor) (Object) module).corevk$samplers();
			samplers.addAll(extra);
		}
	}

	
	@SuppressWarnings({"rawtypes", "unchecked"})
	public static void appendStorageBuffers(IntermediaryShaderModule module) {
		if (module == null || module.spirv() == null) {
			return;
		}

		List extra = listStorageBuffers(module.spirv());
		if (!extra.isEmpty()) {
			List buffers = ((IntermediaryShaderModuleAccessor) (Object) module)
					.corevk$uniformBuffers();
			buffers.addAll(extra);
		}
	}

	
	private static Compiled compile(VulkanDevice vulkan, IntermediaryShaderModule module) {
		try {
			IntermediaryShaderModuleAccessor access =
					(IntermediaryShaderModuleAccessor) (Object) module;
			List<VulkanBindGroupLayout.Entry> entries = new ArrayList<>();
			for (Object buffer : access.corevk$uniformBuffers()) {
				entries.add(new VulkanBindGroupLayout.Entry(
						VulkanBindGroupLayout.VulkanBindGroupEntryType.UNIFORM_BUFFER,
						nameOf(buffer, UNIFORM_NAME), null));
			}

			for (Object sampler : access.corevk$samplers()) {
				entries.add(new VulkanBindGroupLayout.Entry(
						VulkanBindGroupLayout.VulkanBindGroupEntryType.SAMPLED_IMAGE,
						nameOf(sampler, SAMPLER_NAME), null));
			}

			List<VulkanBindGroupLayout.Entry> frozen = List.copyOf(entries);
			module.rebind(List.of(), frozen);
			List<Binding> bindings = new ArrayList<>(frozen.size());
			for (VulkanBindGroupLayout.Entry entry : frozen) {
				bindings.add(new Binding(
						entry.type() == VulkanBindGroupLayout.VulkanBindGroupEntryType.UNIFORM_BUFFER,
						entry.name()));
			}

			return new Compiled(module.createVulkanShaderModule(vulkan), List.copyOf(bindings));
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static List listStorage(ByteBuffer spirv) {
		List found = new ArrayList();
		try (MemoryStack stack = MemoryStack.stackPush()) {
			PointerBuffer pointer = stack.callocPointer(1);
			IntBuffer offset = stack.callocInt(1);
			if (Spvc.spvc_context_create(pointer) != 0) {
				return found;
			}

			long context = pointer.get(0);
			try {
				if (Spvc.spvc_context_parse_spirv(context, spirv.asIntBuffer(), spirv.remaining() / 4,
						pointer) != 0) {
					return found;
				}

				long ir = pointer.get(0);
				if (Spvc.spvc_context_create_compiler(context, 0, ir, 1, pointer) != 0) {
					return found;
				}

				long compiler = pointer.get(0);
				if (Spvc.spvc_compiler_create_shader_resources(compiler, pointer) != 0) {
					return found;
				}

				long resources = pointer.get(0);
				PointerBuffer countPointer = stack.callocPointer(1);
				if (Spvc.spvc_resources_get_resource_list_for_type(resources, STORAGE_IMAGE, pointer,
						countPointer) != 0) {
					return found;
				}

				long list = pointer.get(0);
				int count = (int) countPointer.get(0);
				SpvcReflectedResource.Buffer reflected = SpvcReflectedResource.create(list, count);
				for (int i = 0; i < count; i++) {
					SpvcReflectedResource resource = reflected.get(i);
					if (!Spvc.spvc_compiler_get_binary_offset_for_decoration(compiler, resource.id(),
							33, offset)) {
						continue;
					}

					String name = resourceName(compiler, resource);
					if (name.isEmpty()) {
						continue;
					}

					long type = Spvc.spvc_compiler_get_type_handle(compiler, resource.type_id());
					int dimension = Spvc.spvc_type_get_image_dimension(type);
					found.add(newSampler(name, offset.get(0), dimension));
				}
			} finally {
				Spvc.spvc_context_destroy(context);
			}
		}

		return found;
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static List listStorageBuffers(ByteBuffer spirv) {
		List found = new ArrayList();
		try (MemoryStack stack = MemoryStack.stackPush()) {
			PointerBuffer pointer = stack.callocPointer(1);
			IntBuffer offset = stack.callocInt(1);
			if (Spvc.spvc_context_create(pointer) != 0) {
				return found;
			}

			long context = pointer.get(0);
			try {
				if (Spvc.spvc_context_parse_spirv(context, spirv.asIntBuffer(), spirv.remaining() / 4,
						pointer) != 0) {
					return found;
				}

				long ir = pointer.get(0);
				if (Spvc.spvc_context_create_compiler(context, 0, ir, 1, pointer) != 0) {
					return found;
				}

				long compiler = pointer.get(0);
				if (Spvc.spvc_compiler_create_shader_resources(compiler, pointer) != 0) {
					return found;
				}

				long resources = pointer.get(0);
				PointerBuffer countPointer = stack.callocPointer(1);
				if (Spvc.spvc_resources_get_resource_list_for_type(resources, STORAGE_BUFFER, pointer,
						countPointer) != 0) {
					return found;
				}

				long list = pointer.get(0);
				int count = (int) countPointer.get(0);
				SpvcReflectedResource.Buffer reflected = SpvcReflectedResource.create(list, count);
				for (int i = 0; i < count; i++) {
					SpvcReflectedResource resource = reflected.get(i);
					if (!Spvc.spvc_compiler_get_binary_offset_for_decoration(compiler, resource.id(),
							33, offset)) {
						continue;
					}

					String name = resourceName(compiler, resource);
					if (name.isEmpty()) {
						continue;
					}

					found.add(newUniform(name, offset.get(0)));
				}
			} finally {
				Spvc.spvc_context_destroy(context);
			}
		}

		return found;
	}

	
	private static String resourceName(long compiler, SpvcReflectedResource resource) {
		String name = resource.nameString();
		if (name != null && !name.isEmpty()) {
			return name;
		}

		String fromId = Spvc.spvc_compiler_get_name(compiler, resource.id());
		if (fromId != null && !fromId.isEmpty()) {
			return fromId;
		}

		String fromType = Spvc.spvc_compiler_get_name(compiler, resource.type_id());
		return fromType == null ? "" : fromType;
	}

	private static Object newSampler(String name, int bindingOffset, int dimension) {
		try {
			return SAMPLER.newInstance(name, bindingOffset, dimension);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private static Object newUniform(String name, int bindingOffset) {
		try {
			return UNIFORM.newInstance(name, bindingOffset);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	
	static String samplerName(Object sampler) {
		return nameOf(sampler, SAMPLER_NAME);
	}

	private static String nameOf(Object record, Method accessor) {
		try {
			return (String) accessor.invoke(record);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}
}
