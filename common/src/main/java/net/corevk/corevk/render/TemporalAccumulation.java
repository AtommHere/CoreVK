package net.corevk.corevk.render;

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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class TemporalAccumulation {

	
	private static final String SETTING_FILE = "temporal-fold";

	
	public static final boolean DEFAULT_WANTED = false;

	
	private static Boolean wanted;

	private static final Identifier VERTEX_ID =
			Identifier.fromNamespaceAndPath(CoreVK.MOD_ID, "pack/temporal_vertex");

	private static final Identifier FRAGMENT_ID =
			Identifier.fromNamespaceAndPath(CoreVK.MOD_ID, "pack/temporal_fragment");

	private static final String CURRENT = "InSampler";
	private static final String HISTORY = "PrevSampler";
	private static final String VECTORS = "MotionSampler";

	private static final String UNIFORM_BLOCK = "OfTemporal";

	
	private static final int BLOCK_BYTES = 32;

	private static final int VERTICES = 6;
	private static final int SURFACES = 2;

	
	private static final float NEW_WEIGHT = 0.1F;

	
	private static final GpuFormat FORMAT = GpuFormat.RGBA16_FLOAT;

	private static final String LABEL = "CoreVK temporal accumulation";

	private static final String VERTEX = """
			#version 460 core

			in vec3 Position;

			void main() {
				gl_Position = vec4(Position.xy * 2.0 - 1.0, 0.0, 1.0);
			}
			""";

	
	private static final String FRAGMENT = """
			#version 460 core

			uniform sampler2D InSampler;
			uniform sampler2D PrevSampler;
			uniform sampler2D MotionSampler;

			layout(std140) uniform OfTemporal {
				vec2 of_InverseSize;
				vec2 of_InverseRenderSize;
				float of_NewWeight;
			};

			layout(location = 0) out vec4 ofFragData0;

			void main() {
				vec2 uv = gl_FragCoord.xy * of_InverseSize;
				vec4 here = texture(InSampler, uv);

				vec2 motion = texture(MotionSampler, uv).rg * of_InverseRenderSize;
				vec2 was = uv + motion;

				// Off screen a frame ago, so there is no history for this pixel and the fold would
				// be against whatever the clamp to edge hands back, which is a stripe of the border
				// smeared inwards along every edge the camera turns towards.
				//
				// Written as the negation of "inside" rather than as four outside tests, because a
				// NaN compares false against BOTH, so the four-test form lets a NaN coordinate
				// through into the history fetch while this one rejects it.
				if (!(was.x >= 0.0 && was.x <= 1.0 && was.y >= 0.0 && was.y <= 1.0)) {
					ofFragData0 = here;

					return;
				}

				vec3 low = here.rgb;
				vec3 high = here.rgb;
				for (int x = -1; x <= 1; x++) {
					for (int y = -1; y <= 1; y++) {
						vec3 neighbour =
								texture(InSampler, uv + vec2(x, y) * of_InverseSize).rgb;
						low = min(low, neighbour);
						high = max(high, neighbour);
					}
				}

				vec3 before = clamp(texture(PrevSampler, was).rgb, low, high);
				ofFragData0 = vec4(mix(before, here.rgb, of_NewWeight), here.a);
			}
			""";

	private static final ShaderSource SOURCE = GraphicsApi.source((id, type) -> {
		if (type == ShaderType.FRAGMENT) {
			return FRAGMENT_ID.equals(id) ? FRAGMENT : null;
		}

		return VERTEX_ID.equals(id) ? VERTEX : null;
	});

	private RenderPipeline pipeline;
	private TargetSurface[] history;
	private MappableRingBuffer block;

	private boolean refused;
	private int current;

	
	private boolean primed;

	
	private boolean announced;

	
	private boolean announcedMissing;

	
	public static boolean wanted() {
		if (wanted == null) {
			wanted = read();
		}

		return wanted;
	}

	
	public static void setWanted(boolean asked) {
		wanted = asked;
		try {
			Path file = file();
			Files.createDirectories(file.getParent());
			Files.writeString(file, asked + "\n");
		} catch (IOException | RuntimeException e) {
			// Kept live anyway: a value that cannot be stored is still the one the player asked for
			// this session, and a checkbox that springs back says nothing at all.
			CoreVK.logger().warn("Could not store the temporal fold, it holds for this session "
					+ "only", e);
		}
	}

	private static boolean read() {
		try {
			Path file = file();
			if (!Files.isRegularFile(file)) {
				return DEFAULT_WANTED;
			}

			// Anything that is not the word reads as off rather than on: the fold is off by default,
			// so a typo answered with on would turn the picture over.
			return Boolean.parseBoolean(Files.readString(file).trim());
		} catch (IOException | RuntimeException ignored) {
			return DEFAULT_WANTED;
		}
	}

	private static Path file() {
		return CoreVK.platform().gameDirectory().resolve("corevk").resolve(SETTING_FILE);
	}

	
	GpuTextureView fold(CommandEncoder encoder, GpuDevice device, GpuBuffer quad,
			GpuTextureView scene, GpuTextureView vectors, int width, int height, int renderWidth,
			int renderHeight) {
		if (!wanted()) {
			return null;
		}

		if (vectors == null) {
			// The one refusal worth a line of its own, because it is the one that looks like the
			// fold working and changing nothing: asked for, running, and handed no vectors because
			// the chain drew none. Said once, since it is true for every frame until a pack loads.
			if (!this.announcedMissing) {
				this.announcedMissing = true;
				// What it names are the reasons that can actually be true HERE. A pack and a scale
				// under a hundred are not among them: this is only reached from inside the scaled
				// branch, which already required both, so naming them would send a reader to check
				// two things that cannot be the cause.
				CoreVK.logger().warn("Temporal fold asked for but the engine wrote no motion "
						+ "vectors this frame, so the picture is the plain upscale: either the "
						+ "window has just changed size, or the vector pass could not allocate or "
						+ "did not compile, both of which say so on their own line");
			}

			return null;
		}

		if (quad == null || scene == null || renderWidth <= 0 || renderHeight <= 0
				|| !ensure(width, height)) {
			return null;
		}

		RenderPipeline compiled = pipeline(device);
		if (compiled == null) {
			return null;
		}

		int into = 1 - this.current;
		// The first frame at a size has no history, and folding against an unwritten image would
		// hand the clamp a neighbourhood of whatever the driver left. One frame of the scene alone.
		float weight = this.primed ? NEW_WEIGHT : 1.0F;

		this.block.rotate();
		try (GpuBufferSlice.MappedView mapped = this.block.currentBuffer().map(false, true)) {
			Std140Builder.intoBuffer(mapped.data())
					.putVec2(1.0F / width, 1.0F / height)
					.putVec2(1.0F / renderWidth, 1.0F / renderHeight)
					.putFloat(weight);
		}

		try (RenderPass pass = encoder.createRenderPass(() -> LABEL, this.history[into].view(),
				Optional.empty())) {
			GraphicsApi.setPipeline(pass, compiled);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform(UNIFORM_BLOCK, this.block.currentBuffer());
			pass.setVertexBuffer(0, quad.slice());
			GraphicsApi.bindTexture(pass, CURRENT, scene,
					RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
			// LINEAR on the history, because the reprojected coordinate lands between texels and a
			// nearest fetch there is a quarter pixel of jitter added to every frame of the fold.
			GraphicsApi.bindTexture(pass, HISTORY, this.history[this.current].view(),
					RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
			// NEAREST on the vectors: a filtered vector across a silhouette is the average of two
			// surfaces moving differently, which points at neither of them.
			GraphicsApi.bindTexture(pass, VECTORS, vectors,
					RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
			pass.draw(VERTICES, 1, 0, 0);
		}

		this.current = into;
		this.primed = true;
		if (!this.announced) {
			this.announced = true;
			CoreVK.logger().info("Temporal fold armed: {}x{} folded from a {}x{} world, {} of each "
					+ "new frame, reprojected by the engine's motion vectors", width, height,
					renderWidth, renderHeight, NEW_WEIGHT);
		}

		return this.history[into].view();
	}

	void release() {
		if (this.history != null) {
			for (TargetSurface one : this.history) {
				if (one != null) {
					one.close();
				}
			}

			this.history = null;
		}

		if (this.block != null) {
			this.block.close();
			this.block = null;
		}

		this.current = 0;
		this.primed = false;
	}

	private boolean ensure(int width, int height) {
		if (width <= 0 || height <= 0 || this.refused) {
			return false;
		}

		if (this.history != null && this.history[0].width() == width
				&& this.history[0].height() == height) {
			return true;
		}

		try {
			release();
			this.history = new TargetSurface[SURFACES];
			this.history[0] = new TargetSurface(LABEL, FORMAT, false, width, height);
			this.history[1] = new TargetSurface(LABEL + ", the frame before", FORMAT, false, width,
					height);
			this.block = new MappableRingBuffer(() -> LABEL,
					GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, BLOCK_BYTES);
		} catch (GpuDeviceLossException e) {
			throw e;
		} catch (RuntimeException e) {
			release();
			this.refused = true;
			CoreVK.logger().error("CoreVK could not allocate the temporal fold's history at "
					+ "{}x{}, so it stays off for this session", width, height, e);

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

		RuntimeException thrown = null;
		try {
			if (GraphicsApi.valid(GraphicsApi.compile(device, this.pipeline, SOURCE))) {
				return this.pipeline;
			}
		} catch (GpuDeviceLossException e) {
			throw e;
		} catch (RuntimeException e) {
			// A stage the driver refuses throws out of precompilePipeline rather than coming back
			// invalid, and RenderScale.endWorld reaches this outside every catch of the frame.
			thrown = e;
		}

		release();
		this.refused = true;
		this.pipeline = null;
		CoreVK.logger().error("The temporal fold did not compile, so it stays off for this "
				+ "session", thrown);

		return null;
	}

	private static RenderPipeline build() {
		return RenderPipeline.builder()
				.withLocation(Identifier.fromNamespaceAndPath(CoreVK.MOD_ID, "pipeline/temporal"))
				.withVertexShader(VERTEX_ID)
				.withFragmentShader(FRAGMENT_ID)
				.withBindGroupLayout(BindGroupLayouts.GLOBALS)
				.withBindGroupLayout(GraphicsApi.blockAndSamplers(UNIFORM_BLOCK, CURRENT, HISTORY, VECTORS))
				.withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
				.withColorTargetState(new ColorTargetState(Optional.empty(), FORMAT,
						ColorTargetState.WRITE_ALL))
				.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
				.withCull(false)
				.build();
	}
}
