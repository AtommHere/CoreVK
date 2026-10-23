package net.corevk.corevk.render;

import net.corevk.corevk.render.pbr.PbrMap;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.textures.GpuTextureView;

import org.joml.Vector4f;

import java.util.EnumMap;
import java.util.Map;

final class ConstantTextures {

	private static final GpuFormat FORMAT = GpuFormat.RGBA8_UNORM;

	private static final Vector4f OPAQUE_BLACK = new Vector4f(0.0F, 0.0F, 0.0F, 1.0F);
	private static final Vector4f OPAQUE_WHITE = new Vector4f(1.0F, 1.0F, 1.0F, 1.0F);

	private static ConstantTextures instance;

	private final TextureTarget black;
	private final TextureTarget white;
	private final TextureTarget farPlane;
	private final Map<PbrMap, TextureTarget> flatMaps = new EnumMap<>(PbrMap.class);

	private ConstantTextures(GpuDevice device) {
		this.black = GraphicsApi.textureTarget("CoreVK terrain black", 1, 1, false, FORMAT);
		this.white = GraphicsApi.textureTarget("CoreVK terrain white", 1, 1, false, FORMAT);
		this.farPlane = GraphicsApi.textureTarget("CoreVK far plane", 1, 1, true, FORMAT);
		for (PbrMap map : PbrMap.values()) {
			this.flatMaps.put(map, GraphicsApi.textureTarget("CoreVK terrain " + map.sampler(), 1, 1,
					false, FORMAT));
		}

		CommandEncoder encoder = device.createCommandEncoder();
		encoder.clearColorTexture(this.black.getColorTexture(), OPAQUE_BLACK);
		encoder.clearColorTexture(this.white.getColorTexture(), OPAQUE_WHITE);
		encoder.clearDepthTexture(this.farPlane.getDepthTexture(), 1.0);
		this.flatMaps.forEach((map, target) ->
				encoder.clearColorTexture(target.getColorTexture(), map.missing()));
	}

	static ConstantTextures of(GpuDevice device) {
		if (instance == null) {
			instance = new ConstantTextures(device);
		}

		return instance;
	}

	static boolean ready() {
		return instance != null;
	}

	static void close() {
		if (instance == null) {
			return;
		}

		instance.black.destroyBuffers();
		instance.white.destroyBuffers();
		instance.farPlane.destroyBuffers();
		instance.flatMaps.values().forEach(TextureTarget::destroyBuffers);
		instance = null;
	}

	GpuTextureView black() {
		return this.black.getColorTextureView();
	}

	GpuTextureView white() {
		return this.white.getColorTextureView();
	}

	GpuTextureView farPlane() {
		return this.farPlane.getDepthTextureView();
	}

	static GpuTextureView farPlaneIfReady() {
		return instance == null ? null : instance.farPlane();
	}

	GpuTextureView flat(PbrMap map) {
		return this.flatMaps.get(map).getColorTextureView();
	}
}
