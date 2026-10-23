package net.corevk.corevk.pack.target;

import java.util.Set;

public final class SamplerTypes {

	private static final String SAMPLER = "sampler";

	private static final Set<String> BINDABLE = Set.of(
			"2D", "2DShadow", "2DArray", "2DArrayShadow", "2DMS", "2DMSArray",
			"Cube", "CubeShadow", "CubeArray", "CubeArrayShadow");

	private SamplerTypes() {
	}

	public static boolean refused(String type) {
		String shape = shapeOf(type);

		return shape != null && !BINDABLE.contains(shape);
	}

	public static String shapeOf(String type) {
		String name = type;
		if (name.startsWith("i") || name.startsWith("u")) {
			name = name.substring(1);
		}

		return name.startsWith(SAMPLER) ? name.substring(SAMPLER.length()) : null;
	}
}
