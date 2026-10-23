package net.corevk.corevk.pack.texture;

import net.corevk.corevk.pack.model.TargetFormat;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public record CustomImageView(String original, TargetFormat format) {

	private static final String PREFIX = "ofCustomImageView_";
	private static final List<TargetFormat> FORMATS = Arrays.stream(TargetFormat.values())
			.sorted(Comparator.comparingInt((TargetFormat format) -> CustomImages.glslLayout(format).length())
					.reversed()).toList();

	public String name() {
		return PREFIX + CustomImages.glslLayout(this.format) + "_" + this.original;
	}

	public static Optional<CustomImageView> parse(String name) {
		if (!name.startsWith(PREFIX)) {
			return Optional.empty();
		}

		for (TargetFormat format : FORMATS) {
			String head = PREFIX + CustomImages.glslLayout(format) + "_";
			if (name.startsWith(head) && name.length() > head.length()) {
				return Optional.of(new CustomImageView(name.substring(head.length()), format));
			}
		}
		return Optional.empty();
	}

	public static boolean compatible(TargetFormat base, TargetFormat view) {
		return base.bytesPerPixel() == view.bytesPerPixel();
	}

	public static Optional<TargetFormat> requested(TargetFormat base, String type, String layout) {
		if (layout != null && !layout.isEmpty()) {
			return Arrays.stream(TargetFormat.values())
					.filter(format -> CustomImages.glslLayout(format).equals(layout)).findFirst();
		}
		String suffix = type.startsWith("uimage") || type.startsWith("usampler") ? "_UINT"
				: type.startsWith("iimage") || type.startsWith("isampler") ? "_SINT" : "";
		if (suffix.isEmpty() || base.name().endsWith(suffix)) {
			return Optional.empty();
		}

		return Arrays.stream(TargetFormat.values())
				.filter(format -> format.name().endsWith(suffix)
						&& format.components() == base.components() && compatible(base, format))
				.findFirst();
	}
}
