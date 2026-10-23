package net.corevk.corevk.render;

import net.corevk.corevk.mixin.access.LevelRendererOutlineAccessor;
import net.corevk.corevk.mixin.access.RenderTargetAccessor;
import net.corevk.corevk.settings.PackFile;
import net.corevk.corevk.CoreVK;

import com.mojang.blaze3d.GpuDeviceLossException;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;

public final class RenderScale {

	private static final int SMALLEST = PackFile.MIN_RENDER_SCALE;
	private static final int WHOLE = PackFile.MAX_RENDER_SCALE;

	private static final String UPSCALE_LABEL = "CoreVK upscale";
	private static final String SHARPEN_LABEL = "CoreVK sharpen";

	private static final String SAMPLER = "InSampler";

	private static final int VERTICES = 6;
	private static final float[] QUAD = {
			0.0F, 0.0F, 0.0F, 0.0F, 0.0F,
			1.0F, 0.0F, 0.0F, 1.0F, 0.0F,
			1.0F, 1.0F, 0.0F, 1.0F, 1.0F,
			0.0F, 0.0F, 0.0F, 0.0F, 0.0F,
			1.0F, 1.0F, 0.0F, 1.0F, 1.0F,
			0.0F, 1.0F, 0.0F, 0.0F, 1.0F };

	private static final GpuFormat FORMAT = GpuFormat.RGBA8_UNORM;

	private static final Identifier VERTEX_ID =
			Identifier.fromNamespaceAndPath(CoreVK.MOD_ID, "scale/upscale_vertex");
	private static final Identifier UPSCALE_ID =
			Identifier.fromNamespaceAndPath(CoreVK.MOD_ID, "scale/upscale_fragment");
	private static final Identifier SHARPEN_ID =
			Identifier.fromNamespaceAndPath(CoreVK.MOD_ID, "scale/sharpen_fragment");
	private static final Identifier BLIT_ID =
			Identifier.fromNamespaceAndPath(CoreVK.MOD_ID, "scale/blit_fragment");

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

	private static final String UPSCALE = """
			#version 460 core

			uniform sampler2D InSampler;

			in vec2 ofTexCoord;

			layout(location = 0) out vec4 ofFragData0;

			float aRcp(float x) {
				return uintBitsToFloat(0x7ef07ebbu - floatBitsToUint(x));
			}

			float aRsq(float x) {
				return uintBitsToFloat(0x5f347d74u - (floatBitsToUint(x) >> 1));
			}

			void easuTap(inout vec3 colour, inout float weight, vec2 off, vec2 dir, vec2 len,
					float lob, float clp, vec3 c) {
				vec2 v = vec2(off.x * dir.x + off.y * dir.y, off.x * -dir.y + off.y * dir.x) * len;
				float d2 = min(dot(v, v), clp);
				float wB = (2.0 / 5.0) * d2 - 1.0;
				float wA = lob * d2 - 1.0;
				wB *= wB;
				wA *= wA;
				wB = (25.0 / 16.0) * wB - (25.0 / 16.0 - 1.0);
				float w = wB * wA;
				colour += c * w;
				weight += w;
			}

			void easuSet(inout vec2 dir, inout float len, float w,
					float lA, float lB, float lC, float lD, float lE) {
				float lenX = max(abs(lD - lC), abs(lC - lB));
				float dirX = lD - lB;
				dir.x += dirX * w;
				lenX = clamp(abs(dirX) * aRcp(lenX), 0.0, 1.0);
				len += lenX * lenX * w;
				float lenY = max(abs(lE - lC), abs(lC - lA));
				float dirY = lE - lA;
				dir.y += dirY * w;
				lenY = clamp(abs(dirY) * aRcp(lenY), 0.0, 1.0);
				len += lenY * lenY * w;
			}

			void main() {
				vec2 inputSize = vec2(textureSize(InSampler, 0));
				vec2 texel = 1.0 / inputSize;
				vec2 pp = ofTexCoord * inputSize - 0.5;
				vec2 fp = floor(pp);
				pp -= fp;
				// The 12-tap footprint, gathered four texels at a time:
				//    b c
				//  e f g h
				//  i j k l
				//    n o
				vec2 p0 = (fp + vec2(1.0, -1.0)) * texel;
				vec2 p1 = p0 + vec2(-1.0, 2.0) * texel;
				vec2 p2 = p0 + vec2(1.0, 2.0) * texel;
				vec2 p3 = p0 + vec2(0.0, 4.0) * texel;
				vec4 bczzR = textureGather(InSampler, p0, 0);
				vec4 bczzG = textureGather(InSampler, p0, 1);
				vec4 bczzB = textureGather(InSampler, p0, 2);
				vec4 ijfeR = textureGather(InSampler, p1, 0);
				vec4 ijfeG = textureGather(InSampler, p1, 1);
				vec4 ijfeB = textureGather(InSampler, p1, 2);
				vec4 klhgR = textureGather(InSampler, p2, 0);
				vec4 klhgG = textureGather(InSampler, p2, 1);
				vec4 klhgB = textureGather(InSampler, p2, 2);
				vec4 zzonR = textureGather(InSampler, p3, 0);
				vec4 zzonG = textureGather(InSampler, p3, 1);
				vec4 zzonB = textureGather(InSampler, p3, 2);
				vec4 bczzL = bczzB * 0.5 + (bczzR * 0.5 + bczzG);
				vec4 ijfeL = ijfeB * 0.5 + (ijfeR * 0.5 + ijfeG);
				vec4 klhgL = klhgB * 0.5 + (klhgR * 0.5 + klhgG);
				vec4 zzonL = zzonB * 0.5 + (zzonR * 0.5 + zzonG);
				float bL = bczzL.x;
				float cL = bczzL.y;
				float iL = ijfeL.x;
				float jL = ijfeL.y;
				float fL = ijfeL.z;
				float eL = ijfeL.w;
				float kL = klhgL.x;
				float lL = klhgL.y;
				float hL = klhgL.z;
				float gL = klhgL.w;
				float oL = zzonL.z;
				float nL = zzonL.w;
				vec2 dir = vec2(0.0);
				float len = 0.0;
				easuSet(dir, len, (1.0 - pp.x) * (1.0 - pp.y), bL, eL, fL, gL, jL);
				easuSet(dir, len, pp.x * (1.0 - pp.y), cL, fL, gL, hL, kL);
				easuSet(dir, len, (1.0 - pp.x) * pp.y, fL, iL, jL, kL, nL);
				easuSet(dir, len, pp.x * pp.y, gL, jL, kL, lL, oL);
				float dirR = dot(dir, dir);
				bool zro = dirR < (1.0 / 32768.0);
				dirR = zro ? 1.0 : aRsq(dirR);
				dir.x = zro ? 1.0 : dir.x;
				dir *= dirR;
				len = len * 0.5;
				len *= len;
				float stretch = dot(dir, dir) * aRcp(max(abs(dir.x), abs(dir.y)));
				vec2 len2 = vec2(1.0 + (stretch - 1.0) * len, 1.0 - 0.5 * len);
				float lob = 0.5 + ((1.0 / 4.0 - 0.04) - 0.5) * len;
				float clp = aRcp(lob);
				vec3 min4 = min(min(vec3(ijfeR.z, ijfeG.z, ijfeB.z), vec3(klhgR.w, klhgG.w, klhgB.w)),
						min(vec3(ijfeR.y, ijfeG.y, ijfeB.y), vec3(klhgR.x, klhgG.x, klhgB.x)));
				vec3 max4 = max(max(vec3(ijfeR.z, ijfeG.z, ijfeB.z), vec3(klhgR.w, klhgG.w, klhgB.w)),
						max(vec3(ijfeR.y, ijfeG.y, ijfeB.y), vec3(klhgR.x, klhgG.x, klhgB.x)));
				vec3 colour = vec3(0.0);
				float weight = 0.0;
				easuTap(colour, weight, vec2(0.0, -1.0) - pp, dir, len2, lob, clp, vec3(bczzR.x, bczzG.x, bczzB.x));
				easuTap(colour, weight, vec2(1.0, -1.0) - pp, dir, len2, lob, clp, vec3(bczzR.y, bczzG.y, bczzB.y));
				easuTap(colour, weight, vec2(-1.0, 1.0) - pp, dir, len2, lob, clp, vec3(ijfeR.x, ijfeG.x, ijfeB.x));
				easuTap(colour, weight, vec2(0.0, 1.0) - pp, dir, len2, lob, clp, vec3(ijfeR.y, ijfeG.y, ijfeB.y));
				easuTap(colour, weight, vec2(0.0, 0.0) - pp, dir, len2, lob, clp, vec3(ijfeR.z, ijfeG.z, ijfeB.z));
				easuTap(colour, weight, vec2(-1.0, 0.0) - pp, dir, len2, lob, clp, vec3(ijfeR.w, ijfeG.w, ijfeB.w));
				easuTap(colour, weight, vec2(1.0, 1.0) - pp, dir, len2, lob, clp, vec3(klhgR.x, klhgG.x, klhgB.x));
				easuTap(colour, weight, vec2(2.0, 1.0) - pp, dir, len2, lob, clp, vec3(klhgR.y, klhgG.y, klhgB.y));
				easuTap(colour, weight, vec2(2.0, 0.0) - pp, dir, len2, lob, clp, vec3(klhgR.z, klhgG.z, klhgB.z));
				easuTap(colour, weight, vec2(1.0, 0.0) - pp, dir, len2, lob, clp, vec3(klhgR.w, klhgG.w, klhgB.w));
				easuTap(colour, weight, vec2(1.0, 2.0) - pp, dir, len2, lob, clp, vec3(zzonR.z, zzonG.z, zzonB.z));
				easuTap(colour, weight, vec2(0.0, 2.0) - pp, dir, len2, lob, clp, vec3(zzonR.w, zzonG.w, zzonB.w));
				ofFragData0 = vec4(min(max4, max(min4, colour * (1.0 / weight))), 1.0);
			}
			""";

	private static final String SHARPEN = """
			#version 460 core

			uniform sampler2D InSampler;

			in vec2 ofTexCoord;

			layout(location = 0) out vec4 ofFragData0;

			void main() {
				ivec2 last = textureSize(InSampler, 0) - 1;
				ivec2 sp = ivec2(gl_FragCoord.xy);
				// The cross of taps:
				//    b
				//  d e f
				//    h
				vec3 b = texelFetch(InSampler, clamp(sp + ivec2(0, -1), ivec2(0), last), 0).rgb;
				vec3 d = texelFetch(InSampler, clamp(sp + ivec2(-1, 0), ivec2(0), last), 0).rgb;
				vec3 e = texelFetch(InSampler, clamp(sp, ivec2(0), last), 0).rgb;
				vec3 f = texelFetch(InSampler, clamp(sp + ivec2(1, 0), ivec2(0), last), 0).rgb;
				vec3 h = texelFetch(InSampler, clamp(sp + ivec2(0, 1), ivec2(0), last), 0).rgb;
				vec3 mn4 = min(min(b, d), min(f, h));
				vec3 mx4 = max(max(b, d), max(f, h));
				vec3 hitMin = min(mn4, e) / (4.0 * mx4);
				vec3 hitMax = (1.0 - max(mx4, e)) / (4.0 * mn4 - 4.0);
				vec3 lobes = max(-hitMin, hitMax);
				float lobe = max(-(0.25 - 1.0 / 16.0),
						min(max(lobes.r, max(lobes.g, lobes.b)), 0.0)) * exp2(-0.2);
				float weight = 1.0 / (4.0 * lobe + 1.0);
				ofFragData0 = vec4((lobe * (b + d + f + h) + e) * weight, 1.0);
			}
			""";

	private static final String BLIT = """
			#version 460 core

			uniform sampler2D InSampler;

			in vec2 ofTexCoord;

			layout(location = 0) out vec4 ofFragData0;

			void main() {
				ofFragData0 = vec4(texture(InSampler, ofTexCoord).rgb, 1.0);
			}
			""";

	private static final ShaderSource SOURCE = GraphicsApi.source((id, type) -> {
		if (type == ShaderType.FRAGMENT) {
			if (UPSCALE_ID.equals(id)) {
				return UPSCALE;
			}

			if (SHARPEN_ID.equals(id)) {
				return SHARPEN;
			}

			return BLIT_ID.equals(id) ? BLIT : null;
		}

		return VERTEX_ID.equals(id) ? VERTEX : null;
	});

	private static final class Pass {

		private final Identifier fragment;
		private RenderPipeline pipeline;
		private boolean refused;

		private Pass(Identifier fragment) {
			this.fragment = fragment;
		}

		private RenderPipeline get(GpuDevice device) {
			if (this.refused) {
				return null;
			}

			if (this.pipeline == null) {
				this.pipeline = build(this.fragment);
			}

			try {
				if (GraphicsApi.valid(GraphicsApi.compile(device, this.pipeline, SOURCE))) {
					return this.pipeline;
				}

				CoreVK.logger().error("The {} pass of the render scale did not compile",
						this.fragment.getPath());
			} catch (GpuDeviceLossException e) {
				throw e;
			} catch (RuntimeException e) {

				CoreVK.logger().error("The {} pass of the render scale did not compile",
						this.fragment.getPath(), e);
			}

			this.refused = true;
			this.pipeline = null;

			return null;
		}
	}

	private static final Pass EASU = new Pass(UPSCALE_ID);
	private static final Pass RCAS = new Pass(SHARPEN_ID);
	private static final Pass BILINEAR = new Pass(BLIT_ID);

	private static volatile int percent = WHOLE;

	private static boolean worldComing;

	private static boolean swapped;

	static boolean upscaling() {
		return swapped;
	}

	private static GpuTexture fullColor;
	private static GpuTextureView fullColorView;
	private static GpuTexture fullDepth;
	private static GpuTextureView fullDepthView;
	private static int fullWidth;
	private static int fullHeight;

	private static TextureTarget scaled;

	private static TargetSurface upscaled;

	private static GpuBuffer quad;

	private static final TemporalAccumulation TEMPORAL = new TemporalAccumulation();

	private static boolean outlineScaled;

	private static volatile boolean refusedAtSize;
	private static int refusedWidth;
	private static int refusedHeight;

	private RenderScale() {
	}

	public static void wanted(int asked) {
		int clamped = Math.clamp(asked, SMALLEST, WHOLE);

		if (clamped != percent) {
			refusedAtSize = false;
		}

		percent = clamped;
	}

	public static void recover(RenderTarget main) {
		if (swapped && main != null) {
			restore(main);
		}
	}

	public static void frameIntent(boolean rendersWorld) {
		worldComing = rendersWorld;
	}

	public static boolean beginWorld(RenderTarget main) {
		if (main == null) {
			return false;
		}

		int asked = percent;

		if (asked >= WHOLE || !worldComing || !PackChain.drawingPack() || BILINEAR.refused) {
			standDown(main);

			return false;
		}

		int width = Math.max(1, main.width * asked / WHOLE);
		int height = Math.max(1, main.height * asked / WHOLE);
		if (width >= main.width || height >= main.height) {
			standDown(main);

			return false;
		}

		if (!ensure(width, height, main.width, main.height)) {
			standDown(main);

			return false;
		}

		resizeOutline(width, height, true);

		RenderTargetAccessor accessor = (RenderTargetAccessor) main;
		fullColor = accessor.corevk$colorTexture();
		fullColorView = accessor.corevk$colorTextureView();
		fullDepth = accessor.corevk$depthTexture();
		fullDepthView = accessor.corevk$depthTextureView();
		fullWidth = main.width;
		fullHeight = main.height;

		RenderTargetAccessor ours = (RenderTargetAccessor) scaled;
		accessor.corevk$colorTexture(ours.corevk$colorTexture());
		accessor.corevk$colorTextureView(ours.corevk$colorTextureView());
		accessor.corevk$depthTexture(ours.corevk$depthTexture());
		accessor.corevk$depthTextureView(ours.corevk$depthTextureView());
		main.width = width;
		main.height = height;
		swapped = true;

		return true;
	}

	public static void endWorld(RenderTarget main, CommandEncoder encoder) {
		if (!swapped || main == null) {
			return;
		}

		restore(main);

		GpuDevice device = RenderSystem.getDevice();
		GpuTextureView world = ((RenderTargetAccessor) scaled).corevk$colorTextureView();
		RenderPipeline easu = EASU.get(device);
		RenderPipeline rcas = easu == null ? null : RCAS.get(device);
		if (easu != null && rcas != null && upscaled != null) {
			draw(encoder, device, easu, world, upscaled.view(), FilterMode.LINEAR, UPSCALE_LABEL);

			draw(encoder, device, rcas, sharpenFrom(encoder, device, main), fullColorView(main),
					FilterMode.NEAREST, SHARPEN_LABEL);

			return;
		}

		RenderPipeline fallback = BILINEAR.get(device);
		if (fallback != null) {
			draw(encoder, device, fallback, world, fullColorView(main), FilterMode.LINEAR,
					UPSCALE_LABEL);

			return;
		}

		CoreVK.logger().error("The {}% render scale is abandoned for this session: no upscale "
				+ "pipeline compiles, so a world rendered small could never reach the window again",
				percent);
		release();
	}

	public static void close() {
		if (swapped) {
			Minecraft minecraft = Minecraft.getInstance();
			RenderTarget main = minecraft == null || minecraft.gameRenderer == null
					? null
					: minecraft.gameRenderer.mainRenderTarget();
			if (main == null) {
				return;
			}

			restore(main);
		}

		release();
		if (quad != null) {
			quad.close();
			quad = null;
		}
	}

	private static void standDown(RenderTarget main) {
		if (outlineScaled) {
			resizeOutline(main.width, main.height, false);
		}

		release();
	}

	private static void release() {
		if (scaled != null) {
			scaled.destroyBuffers();
			scaled = null;
		}

		if (upscaled != null) {
			upscaled.close();
			upscaled = null;
		}

		TEMPORAL.release();
	}

	private static void restore(RenderTarget main) {
		RenderTargetAccessor accessor = (RenderTargetAccessor) main;
		accessor.corevk$colorTexture(fullColor);
		accessor.corevk$colorTextureView(fullColorView);
		accessor.corevk$depthTexture(fullDepth);
		accessor.corevk$depthTextureView(fullDepthView);
		main.width = fullWidth;
		main.height = fullHeight;
		fullColor = null;
		fullColorView = null;
		fullDepth = null;
		fullDepthView = null;
		swapped = false;
	}

	private static GpuTextureView fullColorView(RenderTarget main) {
		return ((RenderTargetAccessor) main).corevk$colorTextureView();
	}

	private static boolean ensure(int width, int height, int outWidth, int outHeight) {
		if (refusedAtSize && (width != refusedWidth || height != refusedHeight)) {
			refusedAtSize = false;
		}

		if (refusedAtSize) {
			return false;
		}

		try {
			if (scaled == null) {
				scaled = GraphicsApi.textureTarget("CoreVK scaled world", width, height, true, FORMAT);
				announce(width, height, outWidth, outHeight);
			} else if (scaled.width != width || scaled.height != height) {
				scaled.resize(width, height);
				announce(width, height, outWidth, outHeight);
			}

			if (upscaled == null) {
				upscaled = new TargetSurface(UPSCALE_LABEL, FORMAT, false, outWidth, outHeight);
			} else if (upscaled.width() != outWidth || upscaled.height() != outHeight) {
				upscaled.resize(outWidth, outHeight);
			}
		} catch (GpuDeviceLossException e) {
			throw e;
		} catch (RuntimeException e) {
			refusedAtSize = true;
			refusedWidth = width;
			refusedHeight = height;
			release();
			CoreVK.logger().error("CoreVK could not allocate the scaled world at {}x{}, so the "
					+ "{}% render scale waits for the window to be another size", width, height,
					percent, e);

			return false;
		}

		return true;
	}

	private static void announce(int width, int height, int outWidth, int outHeight) {
		CoreVK.logger().info("The world renders at {}x{} for a {}x{} window, render scale {}%",
				width, height, outWidth, outHeight, percent);
	}

	private static void resizeOutline(int width, int height, boolean scaledNow) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null || minecraft.levelRenderer == null) {
			return;
		}

		RenderTarget outline = ((LevelRendererOutlineAccessor) minecraft.levelRenderer)
				.corevk$entityOutlineTarget();
		if (outline != null && (outline.width != width || outline.height != height)) {
			outline.resize(width, height);
		}

		outlineScaled = scaledNow;
	}

	private static GpuTextureView sharpenFrom(CommandEncoder encoder, GpuDevice device,
			RenderTarget main) {
		if (!TemporalAccumulation.wanted() || scaled == null) {

			TEMPORAL.release();

			return upscaled.view();
		}

		GpuTextureView folded = TEMPORAL.fold(encoder, device, quad(device), upscaled.view(),
				PackChain.motionVectors(), main.width, main.height, scaled.width, scaled.height);

		return folded == null ? upscaled.view() : folded;
	}

	private static void draw(CommandEncoder encoder, GpuDevice device, RenderPipeline pipeline,
			GpuTextureView from, GpuTextureView into, FilterMode filter, String label) {
		try (RenderPass pass = encoder.createRenderPass(() -> label, into, Optional.empty())) {
			GraphicsApi.setPipeline(pass, pipeline);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setVertexBuffer(0, quad(device).slice());
			GraphicsApi.bindTexture(pass, SAMPLER, from,
					RenderSystem.getSamplerCache().getClampToEdge(filter));
			pass.draw(VERTICES, 1, 0, 0);
		}
	}

	private static RenderPipeline build(Identifier fragment) {
		return RenderPipeline.builder()
				.withLocation(Identifier.fromNamespaceAndPath(CoreVK.MOD_ID,
						"pipeline/" + fragment.getPath().replace('/', '_')))
				.withVertexShader(VERTEX_ID)
				.withFragmentShader(fragment)
				.withBindGroupLayout(BindGroupLayouts.GLOBALS)
				.withBindGroupLayout(GraphicsApi.samplers(SAMPLER))
				.withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
				.withColorTargetState(new ColorTargetState(Optional.empty(), FORMAT,
						ColorTargetState.WRITE_ALL))
				.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
				.withCull(false)
				.build();
	}

	private static GpuBuffer quad(GpuDevice device) {
		if (quad == null) {
			ByteBuffer vertices = ByteBuffer.allocateDirect(QUAD.length * Float.BYTES)
					.order(ByteOrder.nativeOrder());
			vertices.asFloatBuffer().put(QUAD);
			quad = device.createBuffer(() -> "CoreVK upscale quad",
					GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, vertices);
		}

		return quad;
	}
}
