package net.corevk.corevk.pack.model;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public enum TargetFormat {

	R8_UNORM(1, 1, false),
	R8_SNORM(1, 1, false),
	RG8_UNORM(2, 2, false),
	RG8_SNORM(2, 2, false),
	RGBA8_UNORM(4, 4, false),
	RGBA8_SNORM(4, 4, false),
	R16_UNORM(1, 2, false),
	R16_SNORM(1, 2, false),
	RG16_UNORM(2, 4, false),
	RG16_SNORM(2, 4, false),
	RGBA16_UNORM(4, 8, false),
	RGBA16_SNORM(4, 8, false),
	R8_UINT(1, 1, true),
	R8_SINT(1, 1, true),
	RG8_UINT(2, 2, true),
	RG8_SINT(2, 2, true),
	RGBA8_UINT(4, 4, true),
	RGBA8_SINT(4, 4, true),
	R16_UINT(1, 2, true),
	R16_SINT(1, 2, true),
	RG16_UINT(2, 4, true),
	RG16_SINT(2, 4, true),
	RGBA16_UINT(4, 8, true),
	RGBA16_SINT(4, 8, true),
	R32_UINT(1, 4, true),
	R32_SINT(1, 4, true),
	RG32_UINT(2, 8, true),
	RG32_SINT(2, 8, true),
	RGBA32_UINT(4, 16, true),
	RGBA32_SINT(4, 16, true),
	R16_FLOAT(1, 2, false),
	RG16_FLOAT(2, 4, false),
	RGBA16_FLOAT(4, 8, false),
	R32_FLOAT(1, 4, false),
	RG32_FLOAT(2, 8, false),
	RGBA32_FLOAT(4, 16, false),
	RGB10A2_UNORM(4, 4, false),
	RGB10A2_UINT(4, 4, true),
	RG11B10_FLOAT(3, 4, false);

	private static final TargetFormat FALLBACK = RGBA8_UNORM;

	private static final String DEFAULT_NAME = "RGBA";

	private static final Map<String, Entry> DECLARED = table();

	private final int components;
	private final int bytesPerPixel;
	private final boolean integer;

	TargetFormat(int components, int bytesPerPixel, boolean integer) {
		this.components = components;
		this.bytesPerPixel = bytesPerPixel;
		this.integer = integer;
	}

	public enum Reason { EXACT, PROMOTED, REPLACED, UNKNOWN }

	public record Resolution(String declared, TargetFormat used, Reason reason, boolean alphaAdded) {
	}

	public static Resolution resolve(String declared) {
		Entry entry = DECLARED.get(declared.trim().toUpperCase(Locale.ROOT));
		if (entry == null) {

			return new Resolution(declared, FALLBACK, Reason.UNKNOWN, false);
		}

		return new Resolution(declared, entry.used(), entry.reason(), entry.alphaAdded());
	}

	public static Resolution defaultFormat() {
		return new Resolution(DEFAULT_NAME, FALLBACK, Reason.EXACT, false);
	}

	public int components() {
		return this.components;
	}

	public int bytesPerPixel() {
		return this.bytesPerPixel;
	}

	public boolean integer() {
		return this.integer;
	}

	private static Map<String, Entry> table() {
		Map<String, Entry> declared = new LinkedHashMap<>();

		exact(declared, DEFAULT_NAME, RGBA8_UNORM);

		exact(declared, "R8", R8_UNORM);
		exact(declared, "RG8", RG8_UNORM);
		exact(declared, "RGBA8", RGBA8_UNORM);
		exact(declared, "R8_SNORM", R8_SNORM);
		exact(declared, "RG8_SNORM", RG8_SNORM);
		exact(declared, "RGBA8_SNORM", RGBA8_SNORM);
		exact(declared, "R16", R16_UNORM);
		exact(declared, "RG16", RG16_UNORM);
		exact(declared, "RGBA16", RGBA16_UNORM);
		exact(declared, "R16_SNORM", R16_SNORM);
		exact(declared, "RG16_SNORM", RG16_SNORM);
		exact(declared, "RGBA16_SNORM", RGBA16_SNORM);
		exact(declared, "R16F", R16_FLOAT);
		exact(declared, "RG16F", RG16_FLOAT);
		exact(declared, "RGBA16F", RGBA16_FLOAT);
		exact(declared, "R32F", R32_FLOAT);
		exact(declared, "RG32F", RG32_FLOAT);
		exact(declared, "RGBA32F", RGBA32_FLOAT);
		exact(declared, "R8I", R8_SINT);
		exact(declared, "RG8I", RG8_SINT);
		exact(declared, "RGBA8I", RGBA8_SINT);
		exact(declared, "R8UI", R8_UINT);
		exact(declared, "RG8UI", RG8_UINT);
		exact(declared, "RGBA8UI", RGBA8_UINT);
		exact(declared, "R16I", R16_SINT);
		exact(declared, "RG16I", RG16_SINT);
		exact(declared, "RGBA16I", RGBA16_SINT);
		exact(declared, "R16UI", R16_UINT);
		exact(declared, "RG16UI", RG16_UINT);
		exact(declared, "RGBA16UI", RGBA16_UINT);
		exact(declared, "R32I", R32_SINT);
		exact(declared, "RG32I", RG32_SINT);
		exact(declared, "RGBA32I", RGBA32_SINT);
		exact(declared, "R32UI", R32_UINT);
		exact(declared, "RG32UI", RG32_UINT);
		exact(declared, "RGBA32UI", RGBA32_UINT);
		exact(declared, "RGB10_A2", RGB10A2_UNORM);
		exact(declared, "RGB10_A2UI", RGB10A2_UINT);

		exact(declared, "R11F_G11F_B10F", RG11B10_FLOAT);

		promoted(declared, "RGB8", RGBA8_UNORM);
		promoted(declared, "RGB8_SNORM", RGBA8_SNORM);
		promoted(declared, "RGB16", RGBA16_UNORM);
		promoted(declared, "RGB16_SNORM", RGBA16_SNORM);
		promoted(declared, "RGB16F", RGBA16_FLOAT);
		promoted(declared, "RGB32F", RGBA32_FLOAT);
		promoted(declared, "RGB8I", RGBA8_SINT);
		promoted(declared, "RGB8UI", RGBA8_UINT);
		promoted(declared, "RGB16I", RGBA16_SINT);
		promoted(declared, "RGB16UI", RGBA16_UINT);
		promoted(declared, "RGB32I", RGBA32_SINT);
		promoted(declared, "RGB32UI", RGBA32_UINT);

		replaced(declared, "RGBA2", RGBA8_UNORM, false);
		replaced(declared, "RGBA4", RGBA8_UNORM, false);
		replaced(declared, "R3_G3_B2", RGBA8_UNORM, true);
		replaced(declared, "RGB5_A1", RGBA8_UNORM, false);
		replaced(declared, "RGB565", RGBA8_UNORM, true);

		replaced(declared, "RGB9_E5", RGBA16_FLOAT, true);

		return Map.copyOf(declared);
	}

	private static void exact(Map<String, Entry> declared, String name, TargetFormat used) {
		declared.put(name, new Entry(used, Reason.EXACT, false));
	}

	private static void promoted(Map<String, Entry> declared, String name, TargetFormat used) {
		declared.put(name, new Entry(used, Reason.PROMOTED, true));
	}

	private static void replaced(Map<String, Entry> declared, String name, TargetFormat used,
			boolean alphaAdded) {
		declared.put(name, new Entry(used, Reason.REPLACED, alphaAdded));
	}

	private record Entry(TargetFormat used, Reason reason, boolean alphaAdded) {
	}
}
