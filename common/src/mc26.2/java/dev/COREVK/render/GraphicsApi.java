package net.corevk.corevk.render;

import net.corevk.corevk.mixin.access.GpuDeviceAccessor;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.resources.Identifier;

import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.OptionalDouble;
import java.util.function.BiFunction;

public final class GraphicsApi {

	private GraphicsApi() {
	}

	
	public static void bindTexture(RenderPass pass, String name, GpuTextureView view,
			GpuSampler sampler) {
		pass.bindTexture(name, view, sampler);
	}

	
	public static void setPipeline(RenderPass pass, RenderPipeline pipeline) {
		pass.setPipeline(pipeline);
	}

	
	public static CompiledRenderPipeline compile(GpuDevice device, RenderPipeline pipeline,
			@Nullable ShaderSource source) {
		return device.precompilePipeline(pipeline, source);
	}

	
	public static boolean valid(@Nullable CompiledRenderPipeline compiled) {
		return compiled != null && compiled.isValid();
	}

	
	public static ShaderSource source(BiFunction<Identifier, ShaderType, @Nullable String> shaders) {
		return shaders::apply;
	}

	
	public static @Nullable String shaderText(ShaderSource source, Identifier id, ShaderType type) {
		return source.get(id, type);
	}

	
	public static BindGroupLayout samplers(String... names) {
		BindGroupLayout.Builder builder = BindGroupLayout.builder();
		for (String name : names) {
			builder.withSampler(name);
		}

		return builder.build();
	}

	
	public static BindGroupLayout blockAndSamplers(String block, String... samplers) {
		BindGroupLayout.Builder builder = BindGroupLayout.builder()
				.withUniform(block, UniformType.UNIFORM_BUFFER);
		for (String name : samplers) {
			withSampler(builder, name);
		}

		return builder.build();
	}

	
	public static BindGroupLayout.Builder withSampler(BindGroupLayout.Builder builder, String name) {
		return builder.withSampler(name);
	}

	
	public static Identifier vertexShader(RenderPipeline pipeline) {
		return pipeline.getVertexShader();
	}

	
	public static Identifier fragmentShader(RenderPipeline pipeline) {
		return pipeline.getFragmentShader();
	}

	
	public static @Nullable ColorTargetState colorTarget(RenderPipeline pipeline) {
		return pipeline.getColorTargetState();
	}

	
	public static List<@Nullable ColorTargetState> colorTargets(RenderPipeline pipeline) {
		@Nullable ColorTargetState[] states = pipeline.getColorTargetStates();
		return states == null ? List.of() : Arrays.asList(states);
	}

	
	public static TextureTarget textureTarget(@Nullable String label, int width, int height,
			boolean depth, GpuFormat colour) {
		return new TextureTarget(label, width, height, depth, colour);
	}

	
	public static boolean hasDepth(RenderTarget target) {
		return target.useDepth;
	}

	
	public static RenderPass.@Nullable RenderArea renderArea(RenderPassDescriptor descriptor) {
		return descriptor.renderArea;
	}

	
	public static RenderPassDescriptor withDepthAttachment(RenderPassDescriptor descriptor,
			GpuTextureView view, OptionalDouble clear) {
		return descriptor.withDepthAttachment(view, clear);
	}

	
	public static @Nullable List<RenderPipeline> dropEntityPipelines(GpuDevice device) {
		return ((GpuDeviceAccessor) device).corevk$backend() instanceof StalePipelines stale
				? stale.corevk$dropEntityPipelines()
				: null;
	}

	
	public static void suspendLevelPass() {
		// Nothing: every phase of the level closes its own pass on this game.
	}

	
	public static void withSodiumPushConstants(RenderPipeline.Builder builder) {
		// Nothing: the backend decides by the pipeline's name on this game.
	}

	
	public static void copyPushConstants(RenderPipeline.Builder builder, RenderPipeline from) {
		// Nothing: the rebuilt pipeline keeps the name that decides them.
	}
}
