package net.corevk.corevk.render;

import net.corevk.corevk.glsl.GeometryFold;
import net.corevk.corevk.glsl.PackProgram;
import net.corevk.corevk.glsl.TranslatedUnit;
import net.corevk.corevk.pack.model.ProgramStage;
import net.corevk.corevk.CoreVK;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.ShaderDefines;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.shaderc.Shaderc;

import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class GeometryStage {

	
	private static volatile boolean served;

	private GeometryStage() {
	}

	
	public static void serve(boolean answer) {
		served = answer;
	}

	
	public static boolean note(RenderPipeline pipeline, String path, PackProgram.Loaded loaded) {
		if (loaded.program().stages().get(ProgramStage.GEOMETRY) == null) {
			return true;
		}

		CoreVK.logger().error("{} ships a geometry stage, which this engine does not bind on "
				+ "Minecraft 26.3 yet, so the program is set aside rather than drawn without it", path);

		return false;
	}

	
	public static @Nullable String fragment(RenderPipeline pipeline, String path,
			PackProgram.Loaded loaded) {
		String fragment = loaded.program().stages().get(ProgramStage.FRAGMENT).text();
		TranslatedUnit unit = loaded.program().stages().get(ProgramStage.GEOMETRY);
		if (unit == null) {
			return fragment;
		}

		GeometryFold.Result fold = fold(pipeline, path, unit.text(), fragment);
		if (!fold.folded()) {
			CoreVK.logger().error("{} ships a geometry stage, which this engine does not bind on "
					+ "Minecraft 26.3 yet{}, and the stage {}, so the program is set aside rather "
					+ "than drawn without it", path,
					served ? "" : " nor could this device run", fold.refusal());

			return null;
		}

		CoreVK.logger().info("{} ships a geometry stage; the stage only hands each corner on, so it "
				+ "is folded into the fragment stage", path);

		return fold.fragment();
	}

	
	private static GeometryFold.Result fold(RenderPipeline pipeline, String path, String geometry,
			String fragment) {
		long compiler = Shaderc.shaderc_compiler_initialize();
		if (compiler == 0L) {
			return GeometryFold.Result.refused("could not be read, shaderc giving no compiler");
		}

		long options = Shaderc.shaderc_compile_options_initialize();
		ShaderDefines defines = pipeline.getShaderDefines();
		for (Map.Entry<String, String> macro : defines.values().entrySet()) {
			Shaderc.shaderc_compile_options_add_macro_definition(options, macro.getKey(),
					macro.getValue());
		}

		for (String flag : defines.flags()) {
			Shaderc.shaderc_compile_options_add_macro_definition(options, flag, "");
		}

		// On the heap rather than through the overloads taking text, which encode on the thread's
		// memory stack: a translated stage carries the pack's whole settings header and runs to tens
		// of kilobytes, against a stack of sixty-four.
		ByteBuffer source = MemoryUtil.memUTF8(geometry, false);
		ByteBuffer name = MemoryUtil.memUTF8(path);
		ByteBuffer entry = MemoryUtil.memUTF8("main");
		try {
			long result = Shaderc.shaderc_compile_into_preprocessed_text(compiler, source,
					Shaderc.shaderc_glsl_geometry_shader, name, entry, options);
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
			Shaderc.shaderc_compile_options_release(options);
			Shaderc.shaderc_compiler_release(compiler);
		}
	}

	
	public static void noteBeside(RenderPipeline variant, RenderPipeline base) {
		// Nothing to carry: no geometry stage is bound, so no variant owes one.
	}

	
	public static boolean noted() {
		return false;
	}

	
	public static boolean compiling() {
		return false;
	}

	
	public static void close() {
		// Holds nothing between loads.
	}
}
