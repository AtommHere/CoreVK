package net.corevk.corevk.render;

import com.mojang.blaze3d.pipeline.PipelineCache;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderSource;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import com.mojang.renderpearl.frontend.FrontendRenderPipeline;
import net.corevk.corevk.CoreVK;
import net.corevk.corevk.mixin.access.RenderPipelineAccessor;
import net.corevk.corevk.mixin.game.PipelineCacheAccessor;
import net.corevk.corevk.mixin.game.RenderSystemAccessor;
import net.minecraft.resources.Identifier;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

public final class GraphicsApi {

	private static final boolean KEEP_ACROSS_RELOAD = Boolean.parseBoolean(
			System.getProperty("corevk.keepPackAcrossReload", "true"));

	private static final Map<RenderPipeline, Held> COMPILED = new ConcurrentHashMap<>();

	private static final Map<Object, RenderPipeline> DESCRIBED = new ConcurrentHashMap<>();

	private static final Map<Same, RenderPipeline> SOURCES = new ConcurrentHashMap<>();

	private static final int SODIUM_PUSH_CONSTANTS = 20;

	private GraphicsApi() {
	}

	private record Held(@Nullable CompiledRenderPipeline compiled) {
	}

	private record Same(Object held) {

		@Override
		public boolean equals(Object other) {
			return other instanceof Same same && same.held == this.held;
		}

		@Override
		public int hashCode() {
			return System.identityHashCode(this.held);
		}
	}

	public static void bindTexture(RenderPass pass, String name, GpuTextureView view,
			GpuSampler sampler) {
		pass.setUniform(name, view, sampler);
	}

	public static void setPipeline(RenderPass pass, RenderPipeline pipeline) {
		Held held = COMPILED.get(pipeline);
		CompiledRenderPipeline compiled = held != null ? held.compiled()
				: RenderSystem.getCompiledPipelineNullable(pipeline);
		if (compiled == null || compiled.isClosed()) {
			throw new IllegalStateException("There is no compiled pipeline for "
					+ pipeline.getLocation());
		}

		pass.setPipeline(compiled);
	}

	public static @Nullable CompiledRenderPipeline compile(GpuDevice device, RenderPipeline pipeline,
			@Nullable ShaderSource source) {
		if (source == null) {
			return RenderSystem.getCompiledPipelineNullable(pipeline);
		}

		Held held = COMPILED.get(pipeline);
		if (held != null) {
			return held.compiled();
		}

		CompiledRenderPipeline built;
		try {
			built = device.compilePipeline(pipeline, source, Runnable::run).join().finishCompile();
		} catch (CompletionException e) {
			if (e.getCause() instanceof RuntimeException cause) {
				throw cause;
			}

			if (e.getCause() instanceof Error cause) {
				throw cause;
			}

			throw e;
		}

		Held raced = COMPILED.putIfAbsent(pipeline, new Held(built));
		if (raced != null) {

			if (built != null) {
				built.close();
			}

			return raced.compiled();
		}

		describe(pipeline, built);
		return built;
	}

	public static boolean valid(@Nullable CompiledRenderPipeline compiled) {
		return compiled != null && !compiled.isClosed();
	}

	static boolean adopt(RenderPipeline pipeline, CompiledRenderPipeline compiled) {
		if (COMPILED.putIfAbsent(pipeline, new Held(compiled)) != null) {
			return false;
		}

		describe(pipeline, compiled);
		return true;
	}

	public static @Nullable CompiledRenderPipeline held(RenderPipeline pipeline) {
		Held held = COMPILED.get(pipeline);
		return held == null ? null : held.compiled();
	}

	public static @Nullable RenderPipeline describing(@Nullable Object backend) {
		return backend == null ? null : DESCRIBED.get(backend);
	}

	private static void describe(RenderPipeline pipeline, @Nullable CompiledRenderPipeline compiled) {
		if (compiled instanceof FrontendRenderPipeline front) {
			DESCRIBED.put(front.backendRenderPipeline(), pipeline);
		}

		if (compiled != null) {
			SOURCES.put(new Same(compiled), pipeline);
		}
	}

	public static void noteGameCompiled(RenderPipeline pipeline,
			@Nullable CompiledRenderPipeline compiled) {
		if (compiled != null) {
			SOURCES.putIfAbsent(new Same(compiled), pipeline);
		}
	}

	public static @Nullable RenderPipeline descriptionOf(@Nullable CompiledRenderPipeline compiled) {
		return compiled == null ? null : SOURCES.get(new Same(compiled));
	}

	public static @Nullable CompiledRenderPipeline compiledFor(RenderPipeline pipeline) {
		CompiledRenderPipeline ours = held(pipeline);
		return ours != null ? ours : RenderSystem.getCompiledPipelineNullable(pipeline);
	}

	private static void undescribe(@Nullable CompiledRenderPipeline compiled) {
		if (compiled instanceof FrontendRenderPipeline front) {
			DESCRIBED.remove(front.backendRenderPipeline());
		}

		if (compiled != null) {
			SOURCES.remove(new Same(compiled));
		}
	}

	public static void forget(RenderPipeline pipeline) {
		Held held = COMPILED.remove(pipeline);
		if (held != null && held.compiled() != null) {
			undescribe(held.compiled());
			held.compiled().close();
		}
	}

	public static void purge() {
		int load = PackChain.liveLoad();
		String live = load == 0 ? null : "pipeline/pack/" + load + "/";
		int ofThePack = 0;
		int carried = 0;

		Iterator<Map.Entry<RenderPipeline, Held>> held = COMPILED.entrySet().iterator();
		while (held.hasNext()) {
			Map.Entry<RenderPipeline, Held> entry = held.next();
			boolean pack = ofAPack(entry.getKey());
			if (pack) {
				ofThePack++;
				if (KEEP_ACROSS_RELOAD && live != null
						&& entry.getKey().getLocation().getPath().startsWith(live)) {
					carried++;
					continue;
				}
			}

			held.remove();
			CompiledRenderPipeline compiled = entry.getValue().compiled();
			if (compiled != null) {
				undescribe(compiled);
				compiled.close();
			}
		}

		SOURCES.keySet().removeIf(key -> COMPILED.values().stream()
				.noneMatch(kept -> kept.compiled() == key.held()));

		CoreVK.logger().info("Pipeline purge: {} pipelines of the pack held, {} carried over it, "
				+ "property=corevk.keepPackAcrossReload", ofThePack, carried);
	}

	private static boolean ofAPack(RenderPipeline pipeline) {
		Identifier location = pipeline.getLocation();
		String namespace = location.getNamespace();

		return (namespace.equals(CoreVK.MOD_ID) || namespace.startsWith(CoreVK.MOD_ID + "_"))
				&& location.getPath().startsWith("pipeline/pack/");
	}

	public static ShaderSource source(BiFunction<Identifier, ShaderType, @Nullable String> shaders) {
		return new ShaderSource() {
			@Override
			public @Nullable String getShader(Identifier id, ShaderType type) {
				return shaders.apply(id, type);
			}

			@Override
			public ShaderSource.@Nullable CachedIncludeSource getInclude(Identifier id) {
				return null;
			}

			@Override
			public void close() {

			}
		};
	}

	public static @Nullable String shaderText(ShaderSource source, Identifier id, ShaderType type) {
		return source.getShader(id, type);
	}

	public static BindGroupLayout samplers(String... names) {
		BindGroupLayout.Builder builder = BindGroupLayout.builder();
		for (String name : names) {
			builder.withUniform(name, UniformType.COMBINED_IMAGE_SAMPLER);
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
		return builder.withUniform(name, UniformType.COMBINED_IMAGE_SAMPLER);
	}

	public static Identifier vertexShader(RenderPipeline pipeline) {
		return pipeline.getShaders().get(ShaderType.VERTEX);
	}

	public static Identifier fragmentShader(RenderPipeline pipeline) {
		return pipeline.getShaders().get(ShaderType.FRAGMENT);
	}

	public static @Nullable ColorTargetState colorTarget(RenderPipeline pipeline) {
		List<@Nullable ColorTargetState> states = pipeline.getColorTargetStates();
		return states.isEmpty() ? null : states.getFirst();
	}

	public static List<@Nullable ColorTargetState> colorTargets(RenderPipeline pipeline) {
		return pipeline.getColorTargetStates();
	}

	public static TextureTarget textureTarget(@Nullable String label, int width, int height,
			boolean depth, GpuFormat colour) {
		return new TextureTarget(label, width, height, colour, depth ? GpuFormat.D32_FLOAT : null);
	}

	public static boolean hasDepth(RenderTarget target) {
		return target.hasDepth();
	}

	public static RenderPass.@Nullable RenderArea renderArea(RenderPassDescriptor descriptor) {
		return descriptor.renderArea();
	}

	public static RenderPassDescriptor withDepthAttachment(RenderPassDescriptor descriptor,
			GpuTextureView view, OptionalDouble clear) {
		return new RenderPassDescriptor(descriptor.label(), descriptor.colorAttachments(),
				new RenderPassDescriptor.Attachment<>(view, clear), descriptor.renderArea());
	}

	public static @Nullable List<RenderPipeline> dropEntityPipelines(GpuDevice device) {
		List<RenderPipeline> dropped = new ArrayList<>();
		for (PipelineCache cache : new PipelineCache[] {RenderSystemAccessor.corevk$current(),
				RenderSystemAccessor.corevk$fallback()}) {
			if (cache == null) {
				continue;
			}

			Iterator<Map.Entry<RenderPipeline, CompiledRenderPipeline>> held =
					((PipelineCacheAccessor) cache).corevk$cache().entrySet().iterator();
			while (held.hasNext()) {
				Map.Entry<RenderPipeline, CompiledRenderPipeline> entry = held.next();
				RenderPipeline pipeline = entry.getKey();
				if (!declaresGameEntity(pipeline)) {
					continue;
				}

				CompiledRenderPipeline compiled = entry.getValue();
				held.remove();
				compiled.close();
				dropped.add(pipeline);
			}
		}

		return dropped;
	}

	private static boolean declaresGameEntity(RenderPipeline pipeline) {
		if (EntityMesh.movingBlock(pipeline)) {
			return true;
		}

		for (VertexFormat format : ((RenderPipelineAccessor) pipeline).corevk$declaredFormats()) {
			@SuppressWarnings("ReferenceEquality")
			boolean entity = format == DefaultVertexFormat.ENTITY;
			if (entity) {
				return true;
			}
		}

		return false;
	}

	public static void suspendLevelPass() {
		LevelPass.suspendCurrent();
	}

	public static void withSodiumPushConstants(RenderPipeline.Builder builder) {
		builder.withPushConstantSize(SODIUM_PUSH_CONSTANTS);
	}

	public static void copyPushConstants(RenderPipeline.Builder builder, RenderPipeline from) {
		builder.withPushConstantSize(from.pushConstantSize());
	}
}
