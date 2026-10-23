package net.corevk.corevk.render;

import net.corevk.corevk.pack.model.BlendMode;
import net.corevk.corevk.CoreVK;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.platform.BlendFactor;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

final class BlendFunctions {

	
	private static final Set<String> REFUSED = new LinkedHashSet<>();

	private BlendFunctions() {
	}

	
	static Optional<BlendFunction> of(BlendMode mode, Optional<BlendFunction> fallback) {
		if (mode == null) {
			return fallback;
		}

		if (mode.off()) {
			return Optional.empty();
		}

		BlendFactor srcRgb = factor(mode.srcRgb());
		BlendFactor dstRgb = factor(mode.dstRgb());
		BlendFactor srcAlpha = factor(mode.srcAlpha());
		BlendFactor dstAlpha = factor(mode.dstAlpha());
		if (srcRgb == null || dstRgb == null || srcAlpha == null || dstAlpha == null) {
			return fallback;
		}

		return Optional.of(new BlendFunction(srcRgb, dstRgb, srcAlpha, dstAlpha));
	}

	private static BlendFactor factor(String name) {
		try {
			return BlendFactor.valueOf(name);
		} catch (IllegalArgumentException e) {
			if (REFUSED.add(name)) {
				CoreVK.logger().warn("A pack asks to blend with {}, which this backend has no "
						+ "factor for, so the programs that ask for it keep the blending the engine "
						+ "would have used", name);
			}

			return null;
		}
	}
}
