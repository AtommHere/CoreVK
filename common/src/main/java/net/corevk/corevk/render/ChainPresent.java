package net.corevk.corevk.render;

import net.corevk.corevk.pack.model.TargetName;
import net.corevk.corevk.pack.target.ChainPlan;
import net.corevk.corevk.CoreVK;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;

import java.util.Optional;

final class ChainPresent {

	private static final Identifier VERTEX_ID =
			Identifier.fromNamespaceAndPath(CoreVK.MOD_ID, "pack/present_vertex");
	private static final Identifier FRAGMENT_ID =
			Identifier.fromNamespaceAndPath(CoreVK.MOD_ID, "pack/present_fragment");

	private static final String SAMPLER = "InSampler";

	private static final String LABEL = "CoreVK chain present";

	
	private static final GpuFormat SCREEN_FORMAT = GpuFormat.RGBA8_UNORM;

	
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

	
	private static final String FRAGMENT = """
			#version 460 core

			uniform sampler2D InSampler;

			in vec2 ofTexCoord;

			layout(location = 0) out vec4 ofFragData0;

			void main() {
				ofFragData0 = vec4(texture(InSampler, ofTexCoord).rgb, 1.0);
			}
			""";

	private final ChainPlan.Attachment from;
	private final RenderPipeline pipeline;
	private final ShaderSource source;

	private boolean reported;

	ChainPresent(ChainPlan.Attachment from) {
		this.from = from;
		this.source = GraphicsApi.source((id, type) -> {
			if (type == ShaderType.FRAGMENT) {
				return FRAGMENT_ID.equals(id) ? FRAGMENT : null;
			}

			return VERTEX_ID.equals(id) ? VERTEX : null;
		});

		this.pipeline = RenderPipeline.builder()
				.withLocation(Identifier.fromNamespaceAndPath(CoreVK.MOD_ID, "pipeline/chain_present"))
				.withVertexShader(VERTEX_ID)
				.withFragmentShader(FRAGMENT_ID)
				.withBindGroupLayout(BindGroupLayouts.GLOBALS)
				.withBindGroupLayout(GraphicsApi.samplers(SAMPLER))
				.withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
				.withColorTargetState(new ColorTargetState(Optional.empty(), SCREEN_FORMAT,
						ColorTargetState.WRITE_COLOR))
				.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
				.withCull(false)
				.build();
	}

	
	CompiledRenderPipeline compiled(GpuDevice device) {
		return GraphicsApi.compile(device, this.pipeline, this.source);
	}

	
	boolean prepare(GpuDevice device) {
		if (GraphicsApi.valid(compiled(device))) {
			return true;
		}

		if (!this.reported) {
			this.reported = true;
			CoreVK.logger().error("The pass that brings {} to the screen did not compile, and this "
					+ "pack serves no final, so nothing the chain wrote is shown",
					TargetName.canonical(this.from.target()));
		}

		return false;
	}

	
	boolean draw(CommandEncoder encoder, GpuBuffer quad, GpuTextureView into, ColorTargets targets) {
		GpuTextureView view = targets.view(this.from.target(), this.from.side());
		if (quad == null || into == null || view == null) {
			return false;
		}

		try (RenderPass pass = encoder.createRenderPass(() -> LABEL, into, Optional.empty())) {
			GraphicsApi.setPipeline(pass, this.pipeline);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setVertexBuffer(0, quad.slice());
			// NEAREST, the target being the size of the screen: one texel is one pixel and the
			// value wanted is the one the chain wrote, not a blend of it with its neighbour.
			GraphicsApi.bindTexture(pass, SAMPLER, view,
					RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
			pass.draw(VERTICES, 1, 0, 0);
		}

		return true;
	}
}
