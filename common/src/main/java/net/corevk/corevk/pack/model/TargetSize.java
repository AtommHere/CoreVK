package net.corevk.corevk.pack.model;

import java.util.Map;
import java.util.Optional;

public record TargetSize(boolean relative, float width, float height) {

	
	private static final int MAX_SUBSTITUTIONS = 8;

	
	public static final int MAX_DIMENSION = 16384;

	
	private static final TargetSize SCREEN = new TargetSize(true, 1.0F, 1.0F);

	public static TargetSize ofScreen() {
		return SCREEN;
	}

	
	public static Optional<TargetSize> parse(String value, Map<String, String> defines) {
		// One space, as CoreVK splits size.buffer, and not a run of whitespace: two words separated by
		// more than one space make three there, and the directive is refused instead of resized.
		String[] parts = value.trim().split(" ", -1);
		if (parts.length != 2) {
			return Optional.empty();
		}

		String horizontal = substitute(parts[0], defines);
		String vertical = substitute(parts[1], defines);
		boolean relative = horizontal.contains(".");
		if (relative != vertical.contains(".")) {
			return Optional.empty();
		}

		try {
			return Optional.of(new TargetSize(relative, Float.parseFloat(horizontal),
					Float.parseFloat(vertical)));
		} catch (NumberFormatException e) {
			return Optional.empty();
		}
	}

	public int width(int screenWidth) {
		return scale(screenWidth, this.width);
	}

	public int height(int screenHeight) {
		return scale(screenHeight, this.height);
	}

	public boolean full() {
		return this.relative && this.width == 1.0F && this.height == 1.0F;
	}

	private int scale(int screen, float value) {
		// Never zero: a target of no pixels is refused by the allocator rather than ignored, and
		// a pack asking for a twentieth of a small window is one rounding away from it. Never
		// larger than MAX_DIMENSION either, and in long, because the float this multiplies has no
		// bound of its own and an allocation that fails takes every other target down with it.
		return Math.clamp(this.relative ? (long) (screen * value) : (long) value, 1, MAX_DIMENSION);
	}

	
	public boolean overCap() {
		return !this.relative && (this.width > MAX_DIMENSION || this.height > MAX_DIMENSION);
	}

	private static String substitute(String token, Map<String, String> defines) {
		String current = token.trim();
		for (int step = 0; step < MAX_SUBSTITUTIONS; step++) {
			String next = defines.get(current);
			if (next == null) {
				return current;
			}

			current = next.trim();
		}

		return current;
	}
}
