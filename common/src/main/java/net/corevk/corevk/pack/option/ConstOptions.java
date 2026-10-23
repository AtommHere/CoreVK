package net.corevk.corevk.pack.option;

import java.util.HashSet;
import java.util.Set;

public final class ConstOptions {

	private static final Set<String> NAMES = names();

	private ConstOptions() {
	}

	
	public static boolean isOption(String name) {
		return NAMES.contains(name);
	}

	private static Set<String> names() {
		Set<String> names = new HashSet<>(Set.of(
				"shadowMapResolution",
				"shadowDistance",
				"voxelDistance",
				"shadowDistanceRenderMul",
				"entityShadowDistanceMul",
				"shadowIntervalSize",
				"generateShadowMipmap",
				"generateShadowColorMipmap",
				"shadowHardwareFiltering",
				"shadowtex0Mipmap",
				"shadowtexMipmap",
				"shadowtex1Mipmap",
				"shadowtex0Nearest",
				"shadowtexNearest",
				"shadow0MinMagNearest",
				"shadowtex1Nearest",
				"shadow1MinMagNearest",
				"wetnessHalflife",
				"drynessHalflife",
				"eyeBrightnessHalflife",
				"centerDepthHalflife",
				"sunPathRotation",
				"ambientOcclusionLevel",
				"superSamplingLevel",
				"noiseTextureResolution"));

		for (int i = 0; i < 8; i++) {
			names.add("shadowcolor" + i + "Mipmap");
			names.add("shadowColor" + i + "Mipmap");
			names.add("shadowcolor" + i + "Nearest");
			names.add("shadowColor" + i + "Nearest");
			names.add("shadowcolor" + i + "MinMagNearest");
			names.add("shadowColor" + i + "MinMagNearest");
			names.add("shadowHardwareFiltering" + i);
		}

		return Set.copyOf(names);
	}
}
