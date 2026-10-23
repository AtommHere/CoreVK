package net.corevk.corevk.render;

import net.corevk.corevk.pack.target.ChainPlan;
import net.corevk.corevk.pack.model.TargetName;
import net.corevk.corevk.CoreVK;

import com.mojang.blaze3d.GpuDeviceLossException;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.TextureTarget;
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
import org.joml.Vector4fc;

import java.util.Optional;

final class FeatureLayer {

	private static final Identifier VERTEX_ID =
			Identifier.fromNamespaceAndPath(CoreVK.MOD_ID, "pack/features_vertex");
	private static final Identifier FRAGMENT_ID =
			Identifier.fromNamespaceAndPath(CoreVK.MOD_ID, "pack/features_fragment");

	private static final String SAMPLER = "InSampler";

	private static final String LABEL = "CoreVK feature layer";

	private static final Vector4fc TRANSPARENT = new Vector4f(0.0F, 0.0F, 0.0F, 0.0F);

	private static final GpuFormat FORMAT = GpuFormat.RGBA8_UNORM;

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
				ofFragData0 = texture(InSampler, ofTexCoord);
			}
			""";

	private final ChainPlan.Attachment into;

	private final RenderPipeline pipeline;
	private final ShaderSource source;

	private TextureTarget layer;
	private boolean broken;
	private boolean reported;

	private int brokenWidth;
	private int brokenHeight;

	FeatureLayer(ChainPlan.Attachment into, GpuFormat destination) {
		this.into = into;
		this.source = GraphicsApi.source((id, type) -> {
			if (type == ShaderType.FRAGMENT) {
				return FRAGMENT_ID.equals(id) ? FRAGMENT : null;
			}

			return VERTEX_ID.equals(id) ? VERTEX : null;
		});

		this.pipeline = RenderPipeline.builder()
				.withLocation(Identifier.fromNamespaceAndPath(CoreVK.MOD_ID,
						"pipeline/feature_layer"))
				.withVertexShader(VERTEX_ID)
				.withFragmentShader(FRAGMENT_ID)
				.withBindGroupLayout(BindGroupLayouts.GLOBALS)
				.withBindGroupLayout(GraphicsApi.samplers(SAMPLER))
				.withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
				.withColorTargetState(new ColorTargetState(
						Optional.of(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA), destination,
						ColorTargetState.WRITE_ALL))
				.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
				.withCull(false)
				.build();
	}

	GpuTextureView open(GpuDevice device, int width, int height) {

		GeometryHold.flush(() -> "CoreVK feature layer allocation");

		if (width <= 0 || height <= 0) {
			return null;
		}

		if (this.broken && (width != this.brokenWidth || height != this.brokenHeight)) {
			this.broken = false;
		}

		if (this.broken) {
			return null;
		}

		if (this.layer != null
				&& (this.layer.width != width || this.layer.height != height)) {
			this.layer.destroyBuffers();
			this.layer = null;
		}

		if (this.layer == null) {
			try {

				this.layer = GraphicsApi.textureTarget("CoreVK features", width, height, false, FORMAT);
			} catch (GpuDeviceLossException e) {
				throw e;
			} catch (RuntimeException e) {
				this.broken = true;
				this.brokenWidth = width;
				this.brokenHeight = height;
				CoreVK.logger().error("CoreVK could not allocate the feature layer at {}x{}, so the "
						+ "game's translucent features stay on the game's target until the screen is "
						+ "another size", width, height, e);

				return null;
			}
		}

		device.createCommandEncoder().clearColorTexture(this.layer.getColorTexture(), TRANSPARENT);

		return this.layer.getColorTextureView();
	}

	boolean prepare(GpuDevice device) {
		if (GraphicsApi.valid(GraphicsApi.compile(device, this.pipeline, this.source))) {
			return true;
		}

		if (!this.reported) {
			this.reported = true;
			CoreVK.logger().error("The feature layer did not compile, so the game's translucent "
					+ "features are not composed onto {}", TargetName.canonical(this.into.target()));
		}

		return false;
	}

	ChainPlan.Attachment into() {
		return this.into;
	}

	void compose(CommandEncoder encoder, GpuBuffer quad, GpuTextureView into,
			Optional<Vector4fc> clear) {
		if (this.layer == null || quad == null || into == null) {
			return;
		}

		try (RenderPass pass = encoder.createRenderPass(() -> LABEL, into, clear)) {
			GraphicsApi.setPipeline(pass, this.pipeline);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setVertexBuffer(0, quad.slice());
			GraphicsApi.bindTexture(pass, SAMPLER, this.layer.getColorTextureView(),
					RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
			pass.draw(VERTICES, 1, 0, 0);
		}
	}

	void release() {
		if (this.layer != null) {
			this.layer.destroyBuffers();
			this.layer = null;
		}
	}
}
