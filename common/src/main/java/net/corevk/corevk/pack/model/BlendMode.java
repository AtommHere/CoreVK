package net.corevk.corevk.pack.model;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public record BlendMode(boolean off, String srcRgb, String dstRgb, String srcAlpha, String dstAlpha) {

	public static final BlendMode OFF = new BlendMode(true, "", "", "", "");

	public static Optional<BlendMode> parse(String value) {
		if (value == null) {
			return Optional.empty();
		}

		String trimmed = value.trim();
		if (trimmed.equalsIgnoreCase("off")) {
			return Optional.of(OFF);
		}

		List<String> factors = List.of(trimmed.toUpperCase(Locale.ROOT).split(" ", -1));
		if (factors.size() == 4) {
			return Optional.of(new BlendMode(false, factors.get(0), factors.get(1), factors.get(2),
					factors.get(3)));
		}

		if (factors.size() == 2) {
			return Optional.of(new BlendMode(false, factors.get(0), factors.get(1), factors.get(0),
					factors.get(1)));
		}

		return Optional.empty();
	}

	@Override
	public String toString() {
		return this.off ? "off" : this.srcRgb + " " + this.dstRgb + " " + this.srcAlpha + " "
				+ this.dstAlpha;
	}
}
