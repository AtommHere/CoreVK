package net.corevk.corevk.render;

import net.corevk.corevk.uniform.ClipSpace;
import net.corevk.corevk.CoreVK;

import com.mojang.blaze3d.GpuDeviceLossException;
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
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;
import org.joml.Vector4f;

import java.util.Locale;
import java.util.Optional;

final class PackDepth {

	private static final Identifier VERTEX_ID =
			Identifier.fromNamespaceAndPath(CoreVK.MOD_ID, "pack/depth_window_vertex");
	private static final Identifier FRAGMENT_ID =
			Identifier.fromNamespaceAndPath(CoreVK.MOD_ID, "pack/depth_window_fragment");
	private static final Identifier DISTANT_FRAGMENT_ID =
			Identifier.fromNamespaceAndPath(CoreVK.MOD_ID, "pack/distant_window_fragment");

	private static final String SAMPLER = "InSampler";

	private static final String BLENDED = "InBlended";
	private static final String CARRIED = "InCarried";
	private static final String PURE = "InPure";

	private static final int VERTICES = 6;

	private static final GpuFormat FORMAT = GpuFormat.R32_FLOAT;

	private static final Vector4f FAR_PLANE = new Vector4f(1.0F, 1.0F, 1.0F, 1.0F);

	private static final String LABEL = "CoreVK depth window";

	private static final String DISTANT_LABEL = "CoreVK far terrain depth window";

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

			in vec2 ofTexCoord;

			layout(location = 0) out vec4 ofFragData0;

			void main() {
				ofFragData0 = vec4(%s * texture(InSampler, ofTexCoord).r + %s);
			}
			""", ClipSpace.REVERSED.z, ClipSpace.REVERSED.w);

	private static final String DISTANT_FRAGMENT = String.format(Locale.ROOT, """
			#version 460 core

			uniform sampler2D InBlended;
			uniform sampler2D InCarried;
			uniform sampler2D InPure;

			in vec2 ofTexCoord;

			layout(location = 0) out vec4 ofFragData0;

			void main() {
				float blended = texture(InBlended, ofTexCoord).r;
				float far = blended > texture(InCarried, ofTexCoord).r
						? blended
						: texture(InPure, ofTexCoord).r;

				ofFragData0 = vec4(%s * far + %s);
			}
			""", ClipSpace.REVERSED.z, ClipSpace.REVERSED.w);

	private static final ShaderSource SOURCE = GraphicsApi.source((id, type) -> {
		if (type == ShaderType.FRAGMENT) {
			if (DISTANT_FRAGMENT_ID.equals(id)) {
				return DISTANT_FRAGMENT;
			}

			return FRAGMENT_ID.equals(id) ? FRAGMENT : null;
		}

		return VERTEX_ID.equals(id) ? VERTEX : null;
	});

	private RenderPipeline pipeline;

	private boolean refused;

	private RenderPipeline distantPipeline;
	private boolean distantRefused;

	private TargetSurface opaque;
	private TargetSurface scene;

	private TargetSurface preHand;

	private TargetSurface distantOpaque;
	private TargetSurface distantScene;

	private boolean opaqueWritten;
	private boolean sceneWritten;

	private boolean preHandWritten;

	private boolean distantOpaqueWritten;
	private boolean distantSceneWritten;

	private boolean distantHoldsFar;

	private boolean preHandKept;
	private boolean distantOpaqueKept;
	private boolean distantSceneKept;

	private boolean frameBefore;

	private boolean broken;

	private boolean preHandBroken;

	private boolean distantBroken;
	private int distantBrokenWidth;
	private int distantBrokenHeight;

	private int brokenWidth;
	private int brokenHeight;

	boolean takeOpaque(CommandEncoder encoder, GpuDevice device, GpuBuffer quad, GpuTextureView live,
			int width, int height) {
		if (!ensure(width, height) || !fill(encoder, device, quad, live, this.opaque)) {
			return false;
		}

		this.opaqueWritten = true;

		return true;
	}

	boolean takeScene(CommandEncoder encoder, GpuDevice device, GpuBuffer quad, GpuTextureView live,
			int width, int height) {
		if (!ensure(width, height) || !fill(encoder, device, quad, live, this.scene)) {
			return false;
		}

		this.sceneWritten = true;

		return true;
	}

	boolean takePreHand(CommandEncoder encoder, GpuDevice device, GpuBuffer quad, GpuTextureView live,
			int width, int height) {
		if (!ensure(width, height) || !ensurePreHand(width, height)
				|| !fill(encoder, device, quad, live, this.preHand)) {
			return false;
		}

		this.preHandWritten = true;

		return true;
	}

	boolean takeDistantOpaque(CommandEncoder encoder, GpuDevice device, GpuBuffer quad,
			GpuTextureView distant, int width, int height) {
		if (!ensureDistant(width, height) || !fill(encoder, device, quad, distant, this.distantOpaque)) {
			return false;
		}

		this.distantOpaqueWritten = true;
		this.distantHoldsFar = false;

		return true;
	}

	boolean takeDistantScene(CommandEncoder encoder, GpuDevice device, GpuBuffer quad,
			GpuTextureView blended, GpuTextureView carried, GpuTextureView pure, int width,
			int height) {
		if (!ensureDistant(width, height)) {
			return false;
		}

		boolean filled = (blended == null || carried == null)
				? fill(encoder, device, quad, pure, this.distantScene)
				: fillDistant(encoder, device, quad, blended, carried, pure);
		if (!filled) {
			return false;
		}

		this.distantSceneWritten = true;
		this.distantHoldsFar = false;

		return true;
	}

	boolean takeDistantNothing(CommandEncoder encoder, int width, int height) {
		if (!ensureDistant(width, height)) {
			return false;
		}

		if (!this.distantHoldsFar) {
			encoder.clearColorTexture(this.distantOpaque.texture(), FAR_PLANE);
			encoder.clearColorTexture(this.distantScene.texture(), FAR_PLANE);
			this.distantHoldsFar = true;
		}

		this.distantOpaqueWritten = true;
		this.distantSceneWritten = true;

		return true;
	}

	GpuTextureView opaque() {
		return this.opaqueWritten ? this.opaque.view() : null;
	}

	GpuTextureView scene() {
		return this.sceneWritten ? this.scene.view() : null;
	}

	GpuTextureView preHand() {
		return this.preHandWritten || (this.frameBefore && this.preHandKept)
				? this.preHand.view()
				: null;
	}

	boolean distantRefused() {
		return this.distantRefused;
	}

	GpuTextureView distantOpaque() {
		return this.distantOpaqueWritten || (this.frameBefore && this.distantOpaqueKept)
				? this.distantOpaque.view()
				: null;
	}

	GpuTextureView distantScene() {
		return this.distantSceneWritten || (this.frameBefore && this.distantSceneKept)
				? this.distantScene.view()
				: null;
	}

	void forgetDistant() {
		this.distantOpaqueKept = this.distantOpaqueWritten;
		this.distantSceneKept = this.distantSceneWritten;
		this.distantOpaqueWritten = false;
		this.distantSceneWritten = false;
	}

	void frameBefore(boolean before, int width, int height) {
		this.frameBefore = before;

		if (before) {
			forgetOutgrown(width, height);
		}
	}

	void forgetOutgrown(int width, int height) {
		if (outgrown(this.opaque, width, height)) {
			this.opaqueWritten = false;
		}

		if (outgrown(this.preHand, width, height)) {
			this.preHandKept = false;
		}

		if (outgrown(this.distantOpaque, width, height)) {
			this.distantOpaqueKept = false;
		}

		if (outgrown(this.distantScene, width, height)) {
			this.distantSceneKept = false;
		}
	}

	private static boolean outgrown(TargetSurface surface, int width, int height) {
		return surface != null && (surface.width() != width || surface.height() != height);
	}

	void forgetPreHand(boolean held) {
		this.preHandKept = this.preHandWritten;
		this.preHandWritten = false;

		if (!held) {
			this.preHand = close(this.preHand);
			this.preHandKept = false;
			this.preHandBroken = false;
		}
	}

	void release() {
		releaseWorld();
		this.distantOpaque = close(this.distantOpaque);
		this.distantScene = close(this.distantScene);
		this.distantOpaqueWritten = false;
		this.distantSceneWritten = false;
		this.distantOpaqueKept = false;
		this.distantSceneKept = false;
		this.distantHoldsFar = false;
		this.distantBroken = false;
	}

	private void releaseWorld() {
		this.opaque = close(this.opaque);
		this.scene = close(this.scene);
		this.preHand = close(this.preHand);
		this.opaqueWritten = false;
		this.sceneWritten = false;
		this.preHandWritten = false;
		this.preHandKept = false;
		this.preHandBroken = false;
	}

	private boolean ensure(int width, int height) {

		if (width <= 0 || height <= 0) {
			return false;
		}

		if (this.broken && (width != this.brokenWidth || height != this.brokenHeight)) {
			this.broken = false;
		}

		if (this.broken) {
			return false;
		}

		if (this.opaque != null && this.opaque.width() == width && this.opaque.height() == height) {
			return true;
		}

		try {

			releaseWorld();
			this.opaque = new TargetSurface("CoreVK depth before the translucents", FORMAT, false,
					width, height);
			this.scene = new TargetSurface("CoreVK depth with the translucents", FORMAT, false,
					width, height);
		} catch (GpuDeviceLossException e) {
			throw e;
		} catch (RuntimeException e) {
			this.broken = true;
			this.brokenWidth = width;
			this.brokenHeight = height;
			releaseWorld();
			CoreVK.logger().error("CoreVK could not allocate the two depth images the pack reads at "
					+ "{}x{}, so every depthtex lookup of this pack reads the far plane until the "
					+ "screen is another size", width, height, e);

			return false;
		}

		CoreVK.logger().info("The world's depth is converted into the pack's window in two images at "
				+ "{}x{}, {} MiB", width, height,
				(this.opaque.bytes() + this.scene.bytes()) / (1024L * 1024L));

		return true;
	}

	private boolean ensurePreHand(int width, int height) {
		if (this.preHandBroken) {
			return false;
		}

		if (this.preHand != null && this.preHand.width() == width
				&& this.preHand.height() == height) {
			return true;
		}

		try {

			this.preHandKept = false;
			this.preHand = close(this.preHand);
			this.preHand =
					new TargetSurface("CoreVK depth before the hand", FORMAT, false, width, height);
		} catch (GpuDeviceLossException e) {
			throw e;
		} catch (RuntimeException e) {
			this.preHandBroken = true;
			this.preHand = close(this.preHand);
			CoreVK.logger().error("CoreVK could not allocate the depth image the pack reads past the "
					+ "hand with at {}x{}, so until the screen is another size depthtex2 answers "
					+ "exactly as depthtex1 does: the world with the hand in it where that one is "
					+ "served, and the far plane elsewhere, the hand's own pass included; the centre "
					+ "depth is folded from that same image, so a depth of field focuses on the held "
					+ "item again",
					width, height, e);

			return false;
		}

		CoreVK.logger().info("The depth before the hand is converted into the pack's window in one "
				+ "more image at {}x{}, {} MiB", width, height,
				this.preHand.bytes() / (1024L * 1024L));

		return true;
	}

	private boolean ensureDistant(int width, int height) {
		if (width <= 0 || height <= 0) {
			return false;
		}

		if (this.distantBroken
				&& (width != this.distantBrokenWidth || height != this.distantBrokenHeight)) {
			this.distantBroken = false;
		}

		if (this.distantBroken) {
			return false;
		}

		if (this.distantOpaque != null && this.distantOpaque.width() == width
				&& this.distantOpaque.height() == height) {
			return true;
		}

		try {

			this.distantOpaqueWritten = false;
			this.distantSceneWritten = false;
			this.distantOpaqueKept = false;
			this.distantSceneKept = false;
			this.distantHoldsFar = false;
			this.distantOpaque = close(this.distantOpaque);
			this.distantScene = close(this.distantScene);
			this.distantOpaque = new TargetSurface("CoreVK far terrain depth before its water",
					FORMAT, false, width, height);
			this.distantScene = new TargetSurface("CoreVK far terrain depth with its water",
					FORMAT, false, width, height);
		} catch (GpuDeviceLossException e) {
			throw e;
		} catch (RuntimeException e) {
			this.distantBroken = true;
			this.distantBrokenWidth = width;
			this.distantBrokenHeight = height;
			this.distantOpaque = close(this.distantOpaque);
			this.distantScene = close(this.distantScene);
			CoreVK.logger().error("CoreVK could not allocate the two depth images the pack reads "
					+ "the far terrain out of at {}x{}, so every dhDepthTex lookup of this pack reads "
					+ "the far plane until the screen is another size", width, height, e);

			return false;
		}

		CoreVK.logger().info("The far terrain's depth is converted into the pack's window in two "
				+ "more images at {}x{}, {} MiB", width, height,
				(this.distantOpaque.bytes() + this.distantScene.bytes()) / (1024L * 1024L));

		return true;
	}

	private boolean fill(CommandEncoder encoder, GpuDevice device, GpuBuffer quad,
			GpuTextureView live, TargetSurface into) {
		if (quad == null || live == null || into == null) {
			return false;
		}

		RenderPipeline compiled = pipeline(device);
		if (compiled == null) {
			return false;
		}

		try (RenderPass pass = encoder.createRenderPass(() -> LABEL, into.view(), Optional.empty())) {
			GraphicsApi.setPipeline(pass, compiled);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setVertexBuffer(0, quad.slice());

			GraphicsApi.bindTexture(pass, SAMPLER, live,
					RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
			pass.draw(VERTICES, 1, 0, 0);
		}

		return true;
	}

	private boolean fillDistant(CommandEncoder encoder, GpuDevice device, GpuBuffer quad,
			GpuTextureView blended, GpuTextureView carried, GpuTextureView pure) {
		if (quad == null || this.distantScene == null) {
			return false;
		}

		RenderPipeline compiled = distantPipeline(device);
		if (compiled == null) {
			return fill(encoder, device, quad, pure, this.distantScene);
		}

		try (RenderPass pass = encoder.createRenderPass(() -> DISTANT_LABEL,
				this.distantScene.view(), Optional.empty())) {
			GraphicsApi.setPipeline(pass, compiled);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setVertexBuffer(0, quad.slice());
			GraphicsApi.bindTexture(pass, BLENDED, blended,
					RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
			GraphicsApi.bindTexture(pass, CARRIED, carried,
					RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
			GraphicsApi.bindTexture(pass, PURE, pure,
					RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
			pass.draw(VERTICES, 1, 0, 0);
		}

		return true;
	}

	private RenderPipeline distantPipeline(GpuDevice device) {
		if (this.distantRefused) {
			return null;
		}

		if (this.distantPipeline == null) {
			this.distantPipeline = buildDistant();
		}

		if (GraphicsApi.valid(GraphicsApi.compile(device, this.distantPipeline, SOURCE))) {
			return this.distantPipeline;
		}

		this.distantRefused = true;
		this.distantPipeline = null;
		CoreVK.logger().error("The far terrain's depth conversion did not compile, so dhDepthTex0 "
				+ "answers the far terrain without its water rather than a depth carrying the world");

		return null;
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

		this.refused = true;
		this.pipeline = null;
		CoreVK.logger().error("The depth conversion did not compile, so every depthtex lookup of this "
				+ "pack reads the far plane rather than a depth in the wrong direction");

		return null;
	}

	private static RenderPipeline buildDistant() {
		return RenderPipeline.builder()
				.withLocation(
						Identifier.fromNamespaceAndPath(CoreVK.MOD_ID, "pipeline/distant_window"))
				.withVertexShader(VERTEX_ID)
				.withFragmentShader(DISTANT_FRAGMENT_ID)
				.withBindGroupLayout(BindGroupLayouts.GLOBALS)
				.withBindGroupLayout(GraphicsApi.samplers(BLENDED, CARRIED, PURE))
				.withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
				.withColorTargetState(new ColorTargetState(Optional.empty(), FORMAT,
						ColorTargetState.WRITE_ALL))
				.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
				.withCull(false)
				.build();
	}

	private static RenderPipeline build() {
		return RenderPipeline.builder()
				.withLocation(Identifier.fromNamespaceAndPath(CoreVK.MOD_ID, "pipeline/depth_window"))
				.withVertexShader(VERTEX_ID)
				.withFragmentShader(FRAGMENT_ID)
				.withBindGroupLayout(BindGroupLayouts.GLOBALS)
				.withBindGroupLayout(GraphicsApi.samplers(SAMPLER))
				.withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
				.withColorTargetState(new ColorTargetState(Optional.empty(), FORMAT,
						ColorTargetState.WRITE_ALL))
				.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
				.withCull(false)
				.build();
	}

	private static TargetSurface close(TargetSurface surface) {
		if (surface != null) {
			surface.close();
		}

		return null;
	}
}
