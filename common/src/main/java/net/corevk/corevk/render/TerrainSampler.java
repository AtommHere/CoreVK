package net.corevk.corevk.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;

import net.minecraft.client.Minecraft;
import net.minecraft.client.TextureFilteringMethod;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;

public final class TerrainSampler {

	private static final Map<Integer, GpuSampler> BY_ANISOTROPY = new HashMap<>();

	private static volatile boolean breaksAnisotropy;

	private TerrainSampler() {
	}

	public static void breaksAnisotropy(boolean breaks) {
		breaksAnisotropy = breaks;
	}

	public static GpuSampler get() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null || RenderSystem.tryGetDevice() == null) {
			return null;
		}

		int anisotropy = !breaksAnisotropy
				&& minecraft.options.textureFiltering().get() == TextureFilteringMethod.ANISOTROPIC
				? minecraft.options.maxAnisotropyValue()
				: 1;

		return BY_ANISOTROPY.computeIfAbsent(anisotropy, asked ->
				RenderSystem.getDevice().createSampler(AddressMode.CLAMP_TO_EDGE,
						AddressMode.CLAMP_TO_EDGE, FilterMode.NEAREST, FilterMode.NEAREST, asked,
						OptionalDouble.empty()));
	}

	public static void release() {
		BY_ANISOTROPY.values().forEach(GpuSampler::close);
		BY_ANISOTROPY.clear();
	}
}
