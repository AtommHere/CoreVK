package net.corevk.corevk.render;

import net.corevk.corevk.glsl.GeometryFold;
import net.corevk.corevk.glsl.PackProgram;
import net.corevk.corevk.glsl.TranslatedUnit;
import net.corevk.corevk.mixin.game.IntermediaryShaderModuleAccessor;
import net.corevk.corevk.pack.model.ProgramStage;
import net.corevk.corevk.CoreVK;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.glsl.GlslCompiler;
import com.mojang.blaze3d.vulkan.glsl.IntermediaryShaderModule;
import com.mojang.blaze3d.vulkan.glsl.ShaderCompileException;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.vulkan.VK12;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class GeometryStage {

	
	public static final int STAGE_BIT = VK12.VK_SHADER_STAGE_GEOMETRY_BIT;

	
	private static final Method VARIABLE_NAME;

	static {
		try {
			Class<?> variable = Class.forName("com.mojang.blaze3d.vulkan.glsl.SpvVariable");
			VARIABLE_NAME = variable.getDeclaredMethod("name");
			VARIABLE_NAME.setAccessible(true);
		} catch (ReflectiveOperationException e) {
			throw new ExceptionInInitializerError(e);
		}
	}

	
	private static final Map<RenderPipeline, String> SHIPPED =
			Collections.synchronizedMap(new WeakHashMap<>());

	
	private static volatile boolean noted;

	private static volatile boolean served;

	private static final ThreadLocal<Boolean> COMPILING = new ThreadLocal<>();

	private static final ThreadLocal<InFlight> BUILDING = new ThreadLocal<>();

	private GeometryStage() {
	}

	
	private static final class InFlight {

		private final RenderPipeline pipeline;

		private IntermediaryShaderModule module;

		
		private VulkanDevice device;

		private long handle;

		private InFlight(RenderPipeline pipeline, IntermediaryShaderModule module) {
			this.pipeline = pipeline;
			this.module = module;
		}
	}

	
	public static void serve(boolean answer) {
		served = answer;
	}

	
	public static boolean note(RenderPipeline pipeline, String path, PackProgram.Loaded loaded) {
		TranslatedUnit unit = loaded.program().stages().get(ProgramStage.GEOMETRY);
		if (unit == null) {
			return true;
		}

		if (!served) {
			CoreVK.logger().error("{} ships a geometry stage and this device has no "
					+ "geometryShader feature, so the program is set aside rather than drawn "
					+ "without the stage", path);

			return false;
		}

		file(pipeline, unit);

		return true;
	}

	
	public static String fragment(RenderPipeline pipeline, String path, PackProgram.Loaded loaded) {
		String fragment = loaded.program().stages().get(ProgramStage.FRAGMENT).text();
		TranslatedUnit unit = loaded.program().stages().get(ProgramStage.GEOMETRY);
		if (unit == null) {
			return fragment;
		}

		if (served) {
			file(pipeline, unit);

			return fragment;
		}

		GeometryFold.Result fold = fold(pipeline, path, unit.text(), fragment);
		if (!fold.folded()) {
			CoreVK.logger().error("{} ships a geometry stage and this device has no "
					+ "geometryShader feature, and the stage {}, so the program is set aside rather "
					+ "than drawn without it", path, fold.refusal());

			return null;
		}

		CoreVK.logger().info("{} ships a geometry stage and this device has no geometryShader "
				+ "feature; the stage only hands each corner on, so it is folded into the fragment "
				+ "stage", path);

		return fold.fragment();
	}

	private static void file(RenderPipeline pipeline, TranslatedUnit unit) {
		SHIPPED.put(pipeline, unit.text());
		noted = true;
	}

	
	private static GeometryFold.Result fold(RenderPipeline pipeline, String path, String geometry,
			String fragment) {
		long compiler = Shaderc.shaderc_compiler_initialize();
		if (compiler == 0L) {
			return GeometryFold.Result.refused("could not be read, shaderc giving no compiler");
		}

		// On the heap rather than through the overloads taking text, which encode on the thread's
		// memory stack: a translated stage carries the pack's whole settings header and runs to tens
		// of kilobytes, against a stack of sixty-four.
		ByteBuffer source = MemoryUtil.memUTF8(
				GlslPreprocessor.injectDefines(geometry, pipeline.getShaderDefines()), false);
		ByteBuffer name = MemoryUtil.memUTF8(path);
		ByteBuffer entry = MemoryUtil.memUTF8("main");
		try {
			long result = Shaderc.shaderc_compile_into_preprocessed_text(compiler, source,
					Shaderc.shaderc_glsl_geometry_shader, name, entry, 0L);
			if (result == 0L) {
				return GeometryFold.Result.refused("could not be read, shaderc giving no result");
			}

			try {
				ByteBuffer bytes = Shaderc.shaderc_result_get_bytes(result);
				if (Shaderc.shaderc_result_get_compilation_status(result)
						!= Shaderc.shaderc_compilation_status_success || bytes == null) {
					return GeometryFold.Result.refused("did not preprocess: "
							+ Shaderc.shaderc_result_get_error_message(result));
				}

				return GeometryFold.fold(StandardCharsets.UTF_8.decode(bytes).toString(), fragment);
			} finally {
				Shaderc.shaderc_result_release(result);
			}
		} finally {
			MemoryUtil.memFree(entry);
			MemoryUtil.memFree(name);
			MemoryUtil.memFree(source);
			Shaderc.shaderc_compiler_release(compiler);
		}
	}

	
	public static void noteBeside(RenderPipeline variant, RenderPipeline base) {
		String text = SHIPPED.get(base);
		if (text != null) {
			SHIPPED.put(variant, text);
		}
	}

	
	public static boolean noted() {
		return noted;
	}

	
	public static IntermediaryShaderModule begin(GlslCompiler compiler, RenderPipeline pipeline)
			throws ShaderCompileException {
		abandon();
		if (!noted) {
			return null;
		}

		String text = SHIPPED.get(pipeline);
		if (text == null) {
			return null;
		}

		IntermediaryShaderModule module;
		COMPILING.set(Boolean.TRUE);
		try {
			module = compiler.createIntermediary(pipeline.getLocation().toDebugFileName() + "/gsh",
					GlslPreprocessor.injectDefines(text, pipeline.getShaderDefines()),
					ShaderType.VERTEX);
		} finally {
			COMPILING.remove();
		}

		BUILDING.set(new InFlight(pipeline, module));

		return module;
	}

	
	public static boolean compiling() {
		return Boolean.TRUE.equals(COMPILING.get());
	}

	
	public static IntermediaryShaderModule building() {
		InFlight flight = BUILDING.get();

		return flight == null ? null : flight.module;
	}

	
	public static boolean buildingHere() {
		return BUILDING.get() != null;
	}

	
	public static List<String> outputs(IntermediaryShaderModule module) {
		List<String> names = new ArrayList<>();
		for (Object output : ((IntermediaryShaderModuleAccessor) (Object) module).corevk$outputs()) {
			try {
				names.add((String) VARIABLE_NAME.invoke(output));
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException(e);
			}
		}

		return names;
	}

	
	public static void built(VulkanDevice device) {
		InFlight flight = BUILDING.get();
		if (flight == null || flight.module == null) {
			return;
		}

		flight.device = device;
		flight.handle = flight.module.createVulkanShaderModule(device);
		flight.module.close();
		flight.module = null;
	}

	
	public static long pending(RenderPipeline pipeline) {
		InFlight flight = BUILDING.get();

		return flight != null && flight.pipeline == pipeline ? flight.handle : 0L;
	}

	
	public static void taken(RenderPipeline pipeline) {
		InFlight flight = BUILDING.get();
		if (flight == null || flight.pipeline != pipeline) {
			return;
		}

		BUILDING.remove();
		free(flight);
	}

	
	private static void abandon() {
		InFlight flight = BUILDING.get();
		if (flight == null) {
			return;
		}

		BUILDING.remove();
		free(flight);
	}

	private static void free(InFlight flight) {
		if (flight.module != null) {
			flight.module.close();
		}

		if (flight.handle != 0L && flight.device != null) {
			VK12.vkDestroyShaderModule(flight.device.vkDevice(), flight.handle, null);
		}
	}

	
	public static void close() {
		SHIPPED.clear();
		noted = false;
	}
}
