package net.corevk.corevk.render;

import net.corevk.corevk.mixin.access.GpuDeviceAccessor;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanRenderPipeline;
import com.mojang.blaze3d.vulkan.glsl.GlslCompiler;
import com.mojang.blaze3d.vulkan.glsl.IntermediaryShaderModule;
import com.mojang.blaze3d.vulkan.glsl.ShaderCompileException;
import net.minecraft.resources.Identifier;

public final class AheadCompiler implements AutoCloseable {

	private final VulkanDevice device;
	private final GlslCompiler compiler;

	private AheadCompiler(VulkanDevice device) {
		this.device = device;
		this.compiler = new GlslCompiler();
	}

	public static boolean available(GpuDevice device) {
		return ((GpuDeviceAccessor) device).corevk$backend() instanceof VulkanDevice;
	}

	public static AheadCompiler open(GpuDevice device) {
		return new AheadCompiler((VulkanDevice) ((GpuDeviceAccessor) device).corevk$backend());
	}

	public Ahead build(RenderPipeline pipeline, ShaderSource source) throws Refused {
		try {
			IntermediaryShaderModule vertex = intermediary(pipeline, source,
					GraphicsApi.vertexShader(pipeline), ShaderType.VERTEX);
			try {
				IntermediaryShaderModule fragment = intermediary(pipeline, source,
						GraphicsApi.fragmentShader(pipeline), ShaderType.FRAGMENT);
				try {
					GlslCompiler.CompiledModules modules =
							this.compiler.compile(this.device, pipeline, vertex, fragment);
					return new Ahead(VulkanRenderPipeline.compile(this.device, modules.layout(),
							pipeline, modules.vertex(), modules.fragment()));
				} finally {

					fragment.close();
				}
			} finally {
				vertex.close();
			}
		} catch (ShaderCompileException e) {
			throw new Refused(e.getMessage());
		}
	}

	private IntermediaryShaderModule intermediary(RenderPipeline pipeline, ShaderSource source,
			Identifier id, ShaderType type) throws ShaderCompileException {
		String text = GraphicsApi.shaderText(source, id, type);
		if (text == null) {
			throw new ShaderCompileException("no source for " + id);
		}

		return this.compiler.createIntermediary(id.toDebugFileName(),
				GlslPreprocessor.injectDefines(text, pipeline.getShaderDefines()), type);
	}

	@Override
	public void close() {
		this.compiler.close();
	}

	public static final class Ahead {

		private final VulkanRenderPipeline built;

		private Ahead(VulkanRenderPipeline built) {
			this.built = built;
		}

		public boolean adopt(GpuDevice device, RenderPipeline pipeline) {
			return ((GpuDeviceAccessor) device).corevk$backend() instanceof StalePipelines cache
					&& cache.corevk$adopt(pipeline, this.built);
		}

		public void destroy() {
			this.built.destroy();
		}
	}

	public static final class Refused extends Exception {

		Refused(String message) {
			super(message);
		}
	}
}
