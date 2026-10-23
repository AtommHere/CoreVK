package net.corevk.corevk.render;

import net.corevk.corevk.uniform.Smoothed;
import net.corevk.corevk.CoreVK;

import com.mojang.blaze3d.GpuDeviceLossException;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.ColorTargetState;
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
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.resources.Identifier;

import java.util.Optional;

final class CenterDepth {

	private static final Identifier VERTEX_ID =
			Identifier.fromNamespaceAndPath(CoreVK.MOD_ID, "pack/center_depth_vertex");
	private static final Identifier FRAGMENT_ID =
			Identifier.fromNamespaceAndPath(CoreVK.MOD_ID, "pack/center_depth_fragment");

	
	private static final String SCENE = "InSampler";

	
	private static final String BEFORE = "PrevSampler";

	private static final String UNIFORM_BLOCK = "OfCenterDepth";

	
	private static final int BLOCK_BYTES = 16;

	
	private static final int VERTICES = 6;

	
	private static final GpuFormat FORMAT = GpuFormat.R32_FLOAT;

	
	private static final int SURFACES = 2;

	private static final String LABEL = "CoreVK centre depth";

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
			uniform sampler2D PrevSampler;

			layout(std140) uniform OfCenterDepth {
				float of_CenterBlend;
			};

			in vec2 ofTexCoord;

			layout(location = 0) out vec4 ofFragData0;

			void main() {
				float here = texture(InSampler, ofTexCoord).r;
				float before = texture(PrevSampler, ofTexCoord).r;

				if (isnan(before)) {
					before = here;
				}

				ofFragData0 = vec4(mix(before, here, of_CenterBlend));
			}
			""";

	private static final ShaderSource SOURCE = GraphicsApi.source((id, type) -> {
		if (type == ShaderType.FRAGMENT) {
			return FRAGMENT_ID.equals(id) ? FRAGMENT : null;
		}

		return VERTEX_ID.equals(id) ? VERTEX : null;
	});

	private RenderPipeline pipeline;

	
	private boolean refused;

	private TargetSurface[] texels;
	private MappableRingBuffer factor;

	
	private int current;

	
	private boolean primed;

	
	GpuTextureView view() {
		return this.primed ? this.texels[this.current].view() : null;
	}

	
	void sample(CommandEncoder encoder, GpuDevice device, GpuBuffer quad, GpuTextureView opaque,
			float halfLife, float seconds) {
		if (quad == null || opaque == null || !ensure()) {
			return;
		}

		RenderPipeline compiled = pipeline(device);
		if (compiled == null) {
			return;
		}

		int into = 1 - this.current;
		float blend = blend(halfLife, seconds);

		// Written through the game's own builder rather than into the buffer by hand, which is what
		// every other block of this engine does: one float is a layout too, and the two would drift.
		this.factor.rotate();
		try (GpuBufferSlice.MappedView view = this.factor.currentBuffer().map(false, true)) {
			Std140Builder.intoBuffer(view.data()).putFloat(blend);
		}

		// Loaded rather than cleared: the draw covers the one texel, so a clear would be one more
		// write of the same texel.
		try (RenderPass pass = encoder.createRenderPass(() -> LABEL, this.texels[into].view(),
				Optional.empty())) {
			GraphicsApi.setPipeline(pass, compiled);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform(UNIFORM_BLOCK, this.factor.currentBuffer());
			pass.setVertexBuffer(0, quad.slice());
			// NEAREST on both, as CoreVK binds both: one is read at its own centre and the other is one
			// texel wide, so there is nothing for a filter to average either way.
			GraphicsApi.bindTexture(pass, SCENE, opaque,
					RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
			GraphicsApi.bindTexture(pass, BEFORE, this.texels[this.current].view(),
					RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
			pass.draw(VERTICES, 1, 0, 0);
		}

		this.current = into;
		this.primed = true;
	}

	
	private float blend(float halfLife, float seconds) {
		if (!this.primed) {
			return 1.0F;
		}

		return seconds > 0.0F ? Smoothed.blend(halfLife, seconds) : 0.0F;
	}

	
	void release() {
		if (this.texels != null) {
			for (TargetSurface texel : this.texels) {
				if (texel != null) {
					texel.close();
				}
			}

			this.texels = null;
		}

		if (this.factor != null) {
			this.factor.close();
			this.factor = null;
		}

		this.current = 0;
		this.primed = false;
	}

	
	private boolean ensure() {
		if (this.refused) {
			return false;
		}

		if (this.texels != null) {
			return true;
		}

		try {
			// Into the field one at a time, and not through an array expression: that evaluates both
			// constructors before anything holds either, so a second one that throws leaks the first
			// past a release() that has nothing to look at.
			this.texels = new TargetSurface[SURFACES];
			this.texels[0] = new TargetSurface("CoreVK centre depth", FORMAT, false, 1, 1);
			this.texels[1] = new TargetSurface("CoreVK centre depth, the frame before", FORMAT,
					false, 1, 1);
			// Three buffers and a fence per turn, so a frame never writes over what the previous one
			// is still being read for.
			this.factor = new MappableRingBuffer(() -> LABEL,
					GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, BLOCK_BYTES);
		} catch (GpuDeviceLossException e) {
			throw e;
		} catch (RuntimeException e) {
			release();
			this.refused = true;
			CoreVK.logger().error("CoreVK could not allocate the two texels the smoothed centre "
					+ "depth is kept in, so every read of centerDepthSmooth in this pack stands at "
					+ "the far plane", e);

			return false;
		}

		return true;
	}

	
	private RenderPipeline pipeline(GpuDevice device) {
		if (this.refused) {
			return null;
		}

		if (this.pipeline == null) {
			this.pipeline = build();
		}

		if (GraphicsApi.valid(GraphicsApi.compile(device, this.pipeline, SOURCE))) {
			return this.pipeline;
		}

		// Released and not merely refused, which is what makes the line below true. A compile is
		// re-asked every frame, so this road is reached after frames have drawn: the pair would
		// otherwise stand primed and every read would go on answering the last value folded, frozen
		// for the session, while this says the reads stand at the far plane.
		release();
		this.refused = true;
		this.pipeline = null;
		CoreVK.logger().error("The centre depth pass did not compile, so every read of "
				+ "centerDepthSmooth in this pack stands at the far plane");

		return null;
	}

	private static RenderPipeline build() {
		return RenderPipeline.builder()
				.withLocation(Identifier.fromNamespaceAndPath(CoreVK.MOD_ID, "pipeline/center_depth"))
				.withVertexShader(VERTEX_ID)
				.withFragmentShader(FRAGMENT_ID)
				.withBindGroupLayout(BindGroupLayouts.GLOBALS)
				.withBindGroupLayout(GraphicsApi.blockAndSamplers(UNIFORM_BLOCK, SCENE, BEFORE))
				.withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
				.withColorTargetState(new ColorTargetState(Optional.empty(), FORMAT,
						ColorTargetState.WRITE_ALL))
				.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
				.withCull(false)
				.build();
	}
}
