package net.corevk.corevk.pack.model;

import java.util.Optional;

public enum PixelFormat {

	RED(1),
	RG(2),
	RGB(3),
	BGR(3),
	RGBA(4),
	BGRA(4),
	RED_INTEGER(1),
	RG_INTEGER(2),
	RGB_INTEGER(3),
	BGR_INTEGER(3),
	RGBA_INTEGER(4),
	BGRA_INTEGER(4);

	private final int components;

	PixelFormat(int components) {
		this.components = components;
	}

	
	public static Optional<PixelFormat> parse(String name) {
		for (PixelFormat format : values()) {
			if (format.name().equalsIgnoreCase(name.trim())) {
				return Optional.of(format);
			}
		}

		return Optional.empty();
	}

	public int components() {
		return this.components;
	}
}
