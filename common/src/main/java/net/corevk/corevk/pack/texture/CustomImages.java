package net.corevk.corevk.pack.texture;

import net.corevk.corevk.pack.model.ImageInformation;
import net.corevk.corevk.pack.model.TargetFormat;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class CustomImages {

	private static final Set<String> LAYOUT_FORMATS = Arrays.stream(TargetFormat.values())
			.map(CustomImages::glslLayout)
			.collect(Collectors.toUnmodifiableSet());

	private static volatile Map<String, ImageInformation> byName = Map.of();
	private static volatile Set<String> names = Set.of();
	private static volatile String key = "";

	private CustomImages() {
	}

	public static boolean isLayoutFormat(String word) {
		return LAYOUT_FORMATS.contains(word);
	}

	public static void install(ImageInformation.Reading reading) {
		Map<String, ImageInformation> images = new LinkedHashMap<>();
		for (ImageInformation image : reading.images()) {
			images.put(image.name(), image);
			image.sampler().ifPresent(sampler -> images.putIfAbsent(sampler, image));
		}

		byName = Map.copyOf(images);
		names = namesOf(reading);
		key = images.entrySet().stream().sorted(Map.Entry.comparingByKey())
				.map(entry -> entry.getKey() + ":" + entry.getValue().internalFormat().used().name())
				.collect(Collectors.joining(";"));
	}

	public static void clear() {
		byName = Map.of();
		names = Set.of();
		key = "";
	}

	public static String key() {
		return key;
	}

	public static Set<String> namesOf(ImageInformation.Reading reading) {
		Set<String> named = new LinkedHashSet<>();
		for (ImageInformation image : reading.images()) {
			named.add(image.name());
			image.sampler().ifPresent(named::add);
		}

		return Set.copyOf(named);
	}

	public static boolean named(String name) {
		return names.contains(originalName(name));
	}

	public static String originalName(String name) {
		return CustomImageView.parse(name).map(CustomImageView::original).orElse(name);
	}

	public static Set<String> names() {
		return names;
	}

	public static boolean storage(String name) {
		String original = originalName(name);
		ImageInformation image = byName.get(original);
		return image != null && image.name().equals(original);
	}

	public static Optional<String> layoutFormat(String name) {
		if (!storage(name)) {
			return Optional.empty();
		}
		return viewFormat(name).map(CustomImages::glslLayout);
	}

	public static Optional<ImageInformation> image(String name) {
		return Optional.ofNullable(byName.get(originalName(name)));
	}

	public static Optional<TargetFormat> viewFormat(String name) {
		return CustomImageView.parse(name).map(CustomImageView::format)
				.or(() -> image(name).map(image -> image.internalFormat().used()));
	}

	static String glslLayout(TargetFormat format) {
		return switch (format) {
			case R8_UNORM -> "r8";
			case R8_SNORM -> "r8_snorm";
			case RG8_UNORM -> "rg8";
			case RG8_SNORM -> "rg8_snorm";
			case RGBA8_UNORM -> "rgba8";
			case RGBA8_SNORM -> "rgba8_snorm";
			case R16_UNORM -> "r16";
			case R16_SNORM -> "r16_snorm";
			case RG16_UNORM -> "rg16";
			case RG16_SNORM -> "rg16_snorm";
			case RGBA16_UNORM -> "rgba16";
			case RGBA16_SNORM -> "rgba16_snorm";
			case R8_UINT -> "r8ui";
			case R8_SINT -> "r8i";
			case RG8_UINT -> "rg8ui";
			case RG8_SINT -> "rg8i";
			case RGBA8_UINT -> "rgba8ui";
			case RGBA8_SINT -> "rgba8i";
			case R16_UINT -> "r16ui";
			case R16_SINT -> "r16i";
			case RG16_UINT -> "rg16ui";
			case RG16_SINT -> "rg16i";
			case RGBA16_UINT -> "rgba16ui";
			case RGBA16_SINT -> "rgba16i";
			case R32_UINT -> "r32ui";
			case R32_SINT -> "r32i";
			case RG32_UINT -> "rg32ui";
			case RG32_SINT -> "rg32i";
			case RGBA32_UINT -> "rgba32ui";
			case RGBA32_SINT -> "rgba32i";
			case R16_FLOAT -> "r16f";
			case RG16_FLOAT -> "rg16f";
			case RGBA16_FLOAT -> "rgba16f";
			case R32_FLOAT -> "r32f";
			case RG32_FLOAT -> "rg32f";
			case RGBA32_FLOAT -> "rgba32f";
			case RGB10A2_UNORM -> "rgb10_a2";
			case RGB10A2_UINT -> "rgb10_a2ui";
			case RG11B10_FLOAT -> "r11f_g11f_b10f";
		};
	}
}
