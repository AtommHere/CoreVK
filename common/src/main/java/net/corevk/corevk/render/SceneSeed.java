package net.corevk.corevk.render;

import net.corevk.corevk.pack.target.ChainPlan;
import net.corevk.corevk.pack.model.TargetName;
import net.corevk.corevk.pack.target.TargetSchedule;
import net.corevk.corevk.uniform.ClipSpace;
import net.corevk.corevk.CoreVK;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;
import org.joml.Vector4fc;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

final class SceneSeed {

	private static final Identifier VERTEX_ID =
			Identifier.fromNamespaceAndPath(CoreVK.MOD_ID, "pack/seed_vertex");
	private static final Identifier FRAGMENT_ID =
			Identifier.fromNamespaceAndPath(CoreVK.MOD_ID, "pack/seed_fragment");
	private static final Identifier EMPTY_FRAGMENT_ID =
			Identifier.fromNamespaceAndPath(CoreVK.MOD_ID, "pack/seed_empty_fragment");

	private static final String SAMPLER = "InSampler";

	private static final String COVERAGE = "CoverageSampler";

	private static final String DEPTH = "DepthSampler";

	private static final String DISTANT = "DistantSampler";

	private static final String LABEL = "CoreVK scene seed";

	private static final String EMPTY_LABEL = "CoreVK scene seed gbuffer";

	private static final int SLACK_EPSILONS = 4;

	private static final String IN_FRONT = String.format(Locale.ROOT, """
			bool ofInFront(float live, float mask) {
				return %s > abs(mask) * (%d.0 * 1.1920929e-7);
			}
			""", ClipSpace.REVERSED.z < 0.0F ? "live - mask" : "mask - live", SLACK_EPSILONS);

	private static final String WRITTEN = ClipSpace.REVERSED.z < 0.0F ? ">= 0.0" : "<= 1.0";

	private static final String REAL = ClipSpace.REVERSED.z < 0.0F ? "> 0.0" : "< 1.0";

	private static final int VERTICES = 6;

	private static final String VERTEX = """
			#version 460 core

			in vec3 Position;
			in vec2 UV0;

			out vec2 ofTexCoord;

			void main() {
				ofTexCoord = UV0;
				gl_Position = vec4(Position.xy * 2.0 - 1.0, 0.0, 1.0);
			}
			""";

	private static final String FRAGMENT = String.format(Locale.ROOT, """
			#version 460 core

			uniform sampler2D InSampler;
			uniform sampler2D CoverageSampler;
			uniform sampler2D DepthSampler;
			uniform sampler2D DistantSampler;

			in vec2 ofTexCoord;

			layout(location = 0) out vec4 ofFragData0;

			%s
			void main() {
				float live = texture(DepthSampler, ofTexCoord).r;
				bool infront = ofInFront(live, texture(CoverageSampler, ofTexCoord).r);
				if (!infront) {
					discard;
				}

				bool farTerrain = texture(DistantSampler, ofTexCoord).r %s;
				bool gameDrew = live %s;
				if (farTerrain && !gameDrew) {
					discard;
				}

				ofFragData0 = texture(InSampler, ofTexCoord);
			}
			""", IN_FRONT, REAL, REAL);

	record Extra(int target, TargetSchedule.Side side, GpuFormat format, Vector4fc empty) {
	}

	private final ChainPlan.Seed seed;

	private final List<Extra> extras;

	private final RenderPipeline pipeline;

	private final RenderPipeline empties;

	private final ShaderSource source;

	private final String emptyFragment;

	private boolean emptying;

	private boolean reported;
	private boolean saidNoEmptying;

	SceneSeed(ChainPlan.Seed seed, GpuFormat destination, List<Extra> extras) {
		this.seed = seed;
		this.extras = List.copyOf(extras);
		this.emptyFragment = this.extras.isEmpty() ? null : emptyFragmentOf(this.extras);
		this.source = GraphicsApi.source((id, type) -> {
			if (type == ShaderType.FRAGMENT) {
				if (FRAGMENT_ID.equals(id)) {
					return FRAGMENT;
				}

				return EMPTY_FRAGMENT_ID.equals(id) ? this.emptyFragment : null;
			}

			return VERTEX_ID.equals(id) ? VERTEX : null;
		});

		this.pipeline = RenderPipeline.builder()
				.withLocation(Identifier.fromNamespaceAndPath(CoreVK.MOD_ID, "pipeline/scene_seed"))
				.withVertexShader(VERTEX_ID)
				.withFragmentShader(FRAGMENT_ID)
				.withBindGroupLayout(BindGroupLayouts.GLOBALS)
				.withBindGroupLayout(GraphicsApi.samplers(SAMPLER, COVERAGE, DEPTH, DISTANT))
				.withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)

				.withColorTargetState(new ColorTargetState(Optional.empty(), destination,
						ColorTargetState.WRITE_ALL))
				.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
				.withCull(false)
				.build();

		this.empties = this.extras.isEmpty() ? null : emptiesPipeline(this.extras);
	}

	private static RenderPipeline emptiesPipeline(List<Extra> extras) {
		RenderPipeline.Builder builder = RenderPipeline.builder()
				.withLocation(Identifier.fromNamespaceAndPath(CoreVK.MOD_ID, "pipeline/seed_empty"))
				.withVertexShader(VERTEX_ID)
				.withFragmentShader(EMPTY_FRAGMENT_ID)
				.withBindGroupLayout(BindGroupLayouts.GLOBALS)
				.withBindGroupLayout(GraphicsApi.samplers(COVERAGE, DEPTH))
				.withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
				.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
				.withCull(false);

		for (int slot = 0; slot < extras.size(); slot++) {
			builder.withColorTargetState(slot, new ColorTargetState(Optional.empty(),
					extras.get(slot).format(), ColorTargetState.WRITE_ALL));
		}

		return builder.build();
	}

	private static String emptyFragmentOf(List<Extra> extras) {
		StringBuilder outputs = new StringBuilder();
		StringBuilder empties = new StringBuilder();
		for (int slot = 0; slot < extras.size(); slot++) {
			outputs.append("layout(location = ").append(slot).append(") out vec4 ofFragData")
					.append(slot).append(";\n");
			empties.append("\n\tofFragData").append(slot).append(" = ")
					.append(literal(extras.get(slot).empty())).append(';');
		}

		return String.format(Locale.ROOT, """
				#version 460 core

				uniform sampler2D CoverageSampler;
				uniform sampler2D DepthSampler;

				in vec2 ofTexCoord;

				%s
				%s
				void main() {
					float mask = texture(CoverageSampler, ofTexCoord).r;
					bool mine = mask %s;
					bool infront = ofInFront(texture(DepthSampler, ofTexCoord).r, mask);
					if (!mine || !infront) {
						discard;
					}
				%s
				}
				""", outputs, IN_FRONT, WRITTEN, empties);
	}

	private static String literal(Vector4fc colour) {
		return String.format(Locale.ROOT, "vec4(%s, %s, %s, %s)", colour.x(), colour.y(), colour.z(),
				colour.w());
	}

	List<Integer> emptied() {
		return this.extras.stream().map(Extra::target).toList();
	}

	boolean prepare(GpuDevice device) {
		if (!GraphicsApi.valid(GraphicsApi.compile(device, this.pipeline, this.source))) {
			if (!this.reported) {
				this.reported = true;
				CoreVK.logger().error("The scene seed did not compile, {} keeps its clear colour",
						TargetName.canonical(this.seed.target()));
			}

			return false;
		}

		this.emptying = this.empties != null
				&& GraphicsApi.valid(GraphicsApi.compile(device, this.empties, this.source));

		if (this.empties != null && !this.emptying && !this.saidNoEmptying) {
			this.saidNoEmptying = true;
			CoreVK.logger().error("The scene seed's gbuffer pass did not compile, so {} keep what "
					+ "the pack's own terrain wrote where the game drew in front of it, and the "
					+ "deferred stage lights the game's colour with them",
					emptied().stream().map(TargetName::canonical).toList());
		}

		return true;
	}

	boolean draw(CommandEncoder encoder, GpuBuffer quad, GpuTextureView scene,
			GpuTextureView covered, GpuTextureView live, GpuTextureView distant,
			ColorTargets targets) {
		GpuTextureView into = targets.view(this.seed.target(), this.seed.side());
		if (quad == null || scene == null || covered == null || live == null || distant == null
				|| into == null) {
			return false;
		}

		try (RenderPass pass = encoder.createRenderPass(() -> LABEL, into, Optional.empty())) {
			GraphicsApi.setPipeline(pass, this.pipeline);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setVertexBuffer(0, quad.slice());
			GraphicsApi.bindTexture(pass, SAMPLER, scene,
					RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));

			GraphicsApi.bindTexture(pass, COVERAGE, covered,
					RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
			GraphicsApi.bindTexture(pass, DEPTH, live,
					RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
			GraphicsApi.bindTexture(pass, DISTANT, distant,
					RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
			pass.draw(VERTICES, 1, 0, 0);
		}

		return !this.emptying || empty(encoder, quad, covered, live, targets);
	}

	private boolean empty(CommandEncoder encoder, GpuBuffer quad, GpuTextureView covered,
			GpuTextureView live, ColorTargets targets) {
		PassDescriptor descriptor = PassDescriptor.create(() -> EMPTY_LABEL);
		int width = 0;
		int height = 0;
		for (Extra extra : this.extras) {
			GpuTextureView view = targets.view(extra.target(), extra.side());
			if (view == null) {
				return false;
			}

			if (width == 0) {
				width = view.getWidth(0);
				height = view.getHeight(0);
			} else if (view.getWidth(0) != width || view.getHeight(0) != height) {
				return false;
			}

			descriptor.withColorAttachment(view);
		}

		descriptor.withRenderArea(new RenderPass.RenderArea(0, 0, width, height));

		try (RenderPass pass = encoder.createRenderPass(descriptor.build())) {
			GraphicsApi.setPipeline(pass, this.empties);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setVertexBuffer(0, quad.slice());

			GraphicsApi.bindTexture(pass, COVERAGE, covered,
					RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
			GraphicsApi.bindTexture(pass, DEPTH, live,
					RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
			pass.draw(VERTICES, 1, 0, 0);
		}

		return true;
	}

}
