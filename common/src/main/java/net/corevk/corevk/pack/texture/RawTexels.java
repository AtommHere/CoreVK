package net.corevk.corevk.pack.texture;

import net.corevk.corevk.pack.model.PixelFormat;
import net.corevk.corevk.pack.model.PixelType;

import java.util.Set;

public final class RawTexels {

	
	public static final int CHANNELS = 4;

	
	static final int ALPHA = 3;

	
	private static final Set<PixelType> TYPES = Set.of(PixelType.UNSIGNED_BYTE,
			PixelType.UNSIGNED_SHORT, PixelType.HALF_FLOAT, PixelType.FLOAT);

	
	private static final Set<PixelFormat> FORMATS = Set.of(PixelFormat.RED, PixelFormat.RG,
			PixelFormat.RGB, PixelFormat.RGBA);

	
	private static final int MAX_SIDE = 16384;

	private static final long MAX_BYTES = 128L * 1024 * 1024;

	private RawTexels() {
	}

	
	public static boolean holds(PixelType type, PixelFormat format) {
		return TYPES.contains(type) && FORMATS.contains(format);
	}

	
	public static boolean fits(int width, int height, int texelBytes) {
		return width <= MAX_SIDE && height <= MAX_SIDE
				&& (long) width * height * texelBytes <= MAX_BYTES;
	}

	
	static byte[] one(PixelType type) {
		return switch (type) {
			case UNSIGNED_BYTE -> new byte[] {(byte) 0xFF};
			case UNSIGNED_SHORT -> new byte[] {(byte) 0xFF, (byte) 0xFF};
			case HALF_FLOAT -> new byte[] {0x00, 0x3C};
			case FLOAT -> new byte[] {0x00, 0x00, (byte) 0x80, 0x3F};
			default -> throw new IllegalStateException(type + " is not a type these lay out");
		};
	}
}
