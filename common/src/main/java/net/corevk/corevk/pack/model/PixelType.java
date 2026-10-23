package net.corevk.corevk.pack.model;

import java.util.Optional;

public enum PixelType {

	BYTE(1, false),
	SHORT(2, false),
	INT(4, false),
	HALF_FLOAT(2, false),
	FLOAT(4, false),
	UNSIGNED_BYTE(1, false),
	UNSIGNED_SHORT(2, false),
	UNSIGNED_INT(4, false),
	UNSIGNED_BYTE_3_3_2(1, true),
	UNSIGNED_BYTE_2_3_3_REV(1, true),
	UNSIGNED_SHORT_5_6_5(2, true),
	UNSIGNED_SHORT_5_6_5_REV(2, true),
	UNSIGNED_SHORT_4_4_4_4(2, true),
	UNSIGNED_SHORT_4_4_4_4_REV(2, true),
	UNSIGNED_SHORT_5_5_5_1(2, true),
	UNSIGNED_SHORT_1_5_5_5_REV(2, true),
	UNSIGNED_INT_8_8_8_8(4, true),
	UNSIGNED_INT_8_8_8_8_REV(4, true),
	UNSIGNED_INT_10_10_10_2(4, true),
	UNSIGNED_INT_2_10_10_10_REV(4, true),
	UNSIGNED_INT_10F_11F_11F_REV(4, true),
	UNSIGNED_INT_5_9_9_9_REV(4, true);

	private final int bytes;
	private final boolean packed;

	PixelType(int bytes, boolean packed) {
		this.bytes = bytes;
		this.packed = packed;
	}

	
	public static Optional<PixelType> parse(String name) {
		for (PixelType type : values()) {
			if (type.name().equalsIgnoreCase(name.trim())) {
				return Optional.of(type);
			}
		}

		return Optional.empty();
	}

	
	public long bytesPerTexel(PixelFormat format) {
		return this.packed ? this.bytes : (long) this.bytes * format.components();
	}

	
	public int channelBytes() {
		return this.bytes;
	}
}
