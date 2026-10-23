package net.corevk.corevk.render;

import net.corevk.corevk.uniform.ClipSpace;
import net.corevk.corevk.uniform.WorldState;
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
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.MappableRingBuffer;

import org.joml.Matrix4f;
import org.joml.Vector3dc;

import java.util.Optional;

final class MotionVectors {

	private static final Identifier VERTEX_ID =
			Identifier.fromNamespaceAndPath(CoreVK.MOD_ID, "pack/motion_vectors_vertex");

	private static final Identifier FRAGMENT_ID =
			Identifier.fromNamespaceAndPath(CoreVK.MOD_ID, "pack/motion_vectors_fragment");

	
	private static final String SCENE = "DepthSampler";

	private static final String UNIFORM_BLOCK = "OfMotionVectors";

	
	private static final int BLOCK_BYTES = 144;

	
	private static final int VERTICES = 6;

	
	private static final GpuFormat FORMAT = GpuFormat.RG16_FLOAT;

	private static final String LABEL = "CoreVK motion vectors";

	private static final String VERTEX = """
			#version 460 core

			in vec3 Position;

			void main() {
				gl_Position = vec4(Position.xy * 2.0 - 1.0, 0.0, 1.0);
			}
			""";

	
	private static final String FRAGMENT = String.format("""
			#version 460 core

			uniform sampler2D DepthSampler;

			layout(std140) uniform OfMotionVectors {
				mat4 of_FromScreen;
				mat4 of_ToPreviousClip;
				vec2 of_Size;
				vec2 of_InverseSize;
			};

			layout(location = 0) out vec2 ofFragData0;

			void main() {
				vec2 uv = gl_FragCoord.xy * of_InverseSize;
				float depth = (texture(DepthSampler, uv).r - %s) / %s;

				vec4 here = of_FromScreen * vec4(uv * 2.0 - 1.0, depth, 1.0);
				vec4 before = of_ToPreviousClip * (here / here.w);

				// Behind the previous camera, so there is no previous pixel to point at. Nought says
				// the pixel did not move, which is wrong and bounded; anything else off screen would
				// be a coordinate an upscaler would go and sample.
				if (before.w <= 0.0) {
					ofFragData0 = vec2(0.0);
					return;
				}

				vec2 was = (before.xy / before.w) * 0.5 + 0.5;
				ofFragData0 = (was - uv) * of_Size;
			}
			""", ClipSpace.REVERSED.w, ClipSpace.REVERSED.z);

	private static final ShaderSource SOURCE = GraphicsApi.source((id, type) -> {
		if (type == ShaderType.FRAGMENT) {
			return FRAGMENT_ID.equals(id) ? FRAGMENT : null;
		}

		return VERTEX_ID.equals(id) ? VERTEX : null;
	});

	private final Matrix4f fromScreen = new Matrix4f();
	private final Matrix4f toPreviousClip = new Matrix4f();

	private RenderPipeline pipeline;
	private TargetSurface vectors;
	private MappableRingBuffer block;

	
	private boolean broken;
	private int brokenWidth;
	private int brokenHeight;

	
	private boolean refused;

	
	private boolean drawn;

	
	private boolean fresh;

	
	void standDown() {
		this.drawn = false;
		release();
	}

	
	GpuTextureView view() {
		return this.drawn ? this.vectors.view() : null;
	}

	
	void draw(CommandEncoder encoder, GpuDevice device, GpuBuffer quad, GpuTextureView depth,
			int width, int height, ViewMatrices view, WorldState world) {
		this.drawn = false;
		if (quad == null || depth == null || !ensure(width, height)) {
			return;
		}

		// The first frame at a new size has no history at that size: the matrices still describe a
		// camera looking through a differently shaped window, and the vectors drawn from them are
		// that reshaping rather than any movement. Skipped outright rather than drawn and ignored,
		// so what reads this is handed nothing and falls back for the one frame it takes.
		if (this.fresh) {
			this.fresh = false;

			return;
		}

		RenderPipeline compiled = pipeline(device);
		if (compiled == null) {
			return;
		}

		// Composed and inverted here rather than in the shader: it is two multiplies and one inverse
		// a frame against one of each per pixel, and the shader stays a line of arithmetic anybody
		// can check against the quoted convention.
		this.fromScreen.set(view.rendered()).mul(view.gbufferModelView()).invert();

		// Post-multiplied, so the offset reaches a point BEFORE the previous frame's rotation turns
		// it: both are distances in player space, and one applied on the far side of the rotation
		// would be a different place in the world. The same order the shadow pair is corrected in.
		Vector3dc now = world.cameraPosition();
		Vector3dc before = world.previousCameraPosition();
		this.toPreviousClip.set(view.previousRendered()).mul(view.gbufferPreviousModelView())
				.translate((float) (now.x() - before.x()), (float) (now.y() - before.y()),
						(float) (now.z() - before.z()));

		this.block.rotate();
		try (GpuBufferSlice.MappedView mapped = this.block.currentBuffer().map(false, true)) {
			Std140Builder.intoBuffer(mapped.data())
					.putMat4f(this.fromScreen)
					.putMat4f(this.toPreviousClip)
					.putVec2(width, height)
					.putVec2(1.0F / width, 1.0F / height);
		}

		try (RenderPass pass = encoder.createRenderPass(() -> LABEL, this.vectors.view(),
				Optional.empty())) {
			GraphicsApi.setPipeline(pass, compiled);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform(UNIFORM_BLOCK, this.block.currentBuffer());
			pass.setVertexBuffer(0, quad.slice());
			// NEAREST, because a filtered depth between two surfaces is a position on neither of
			// them and the vector drawn from it points at nothing.
			GraphicsApi.bindTexture(pass, SCENE, depth,
					RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
			pass.draw(VERTICES, 1, 0, 0);
		}

		this.drawn = true;
	}

	
	void release() {
		if (this.vectors != null) {
			this.vectors.close();
			this.vectors = null;
		}

		if (this.block != null) {
			this.block.close();
			this.block = null;
		}

		this.drawn = false;
	}

	private boolean ensure(int width, int height) {
		// Before the latch and not after, the order PackDepth.ensure keeps and for the reason
		// written there: a minimised window is another size, and lifting a refusal on a size nothing
		// is ever allocated at only makes the real size pay the failure twice.
		if (width <= 0 || height <= 0) {
			return false;
		}

		if (this.broken && (width != this.brokenWidth || height != this.brokenHeight)) {
			this.broken = false;
		}

		if (this.broken) {
			return false;
		}

		if (this.block == null) {
			try {
				this.block = new MappableRingBuffer(() -> LABEL,
						GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, BLOCK_BYTES);
			} catch (GpuDeviceLossException e) {
				throw e;
			} catch (RuntimeException e) {
				this.broken = true;
				this.brokenWidth = width;
				this.brokenHeight = height;
				CoreVK.logger().error("CoreVK could not allocate the uniform buffer the motion "
						+ "vector pass writes its two matrices into, so nothing reprojects", e);

				return false;
			}
		}

		if (this.vectors != null && this.vectors.width() == width
				&& this.vectors.height() == height) {
			return true;
		}

		try {
			// The old one first: a resize that allocates before freeing holds two full screen images
			// at once, and the size that fails is the size that was already tight.
			if (this.vectors != null) {
				this.vectors.close();
				this.vectors = null;
			}

			this.vectors = new TargetSurface(LABEL, FORMAT, false, width, height);
		} catch (GpuDeviceLossException e) {
			throw e;
		} catch (RuntimeException e) {
			this.broken = true;
			this.brokenWidth = width;
			this.brokenHeight = height;
			this.drawn = false;
			CoreVK.logger().error("CoreVK could not allocate the motion vector image at {}x{}, so "
					+ "nothing reprojects until the screen is another size", width, height, e);

			return false;
		}

		this.fresh = true;

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

		release();
		this.refused = true;
		this.pipeline = null;
		CoreVK.logger().error("The motion vector pass did not compile, so nothing reprojects for "
				+ "the rest of this session");

		return null;
	}

	private static RenderPipeline build() {
		return RenderPipeline.builder()
				.withLocation(Identifier.fromNamespaceAndPath(CoreVK.MOD_ID,
						"pipeline/motion_vectors"))
				.withVertexShader(VERTEX_ID)
				.withFragmentShader(FRAGMENT_ID)
				.withBindGroupLayout(BindGroupLayouts.GLOBALS)
				.withBindGroupLayout(GraphicsApi.blockAndSamplers(UNIFORM_BLOCK, SCENE))
				.withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
				.withColorTargetState(new ColorTargetState(Optional.empty(), FORMAT,
						ColorTargetState.WRITE_ALL))
				.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
				.withCull(false)
				.build();
	}
}
