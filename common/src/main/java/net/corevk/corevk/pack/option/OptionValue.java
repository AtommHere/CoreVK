package net.corevk.corevk.pack.option;

import java.util.Locale;

public final class OptionValue {

	private static final OptionValue ON = new OptionValue(true, true, null);
	private static final OptionValue OFF = new OptionValue(true, false, null);

	private final boolean bool;
	private final boolean enabled;
	private final String text;

	private OptionValue(boolean bool, boolean enabled, String text) {
		this.bool = bool;
		this.enabled = enabled;
		this.text = text;
	}

	public static OptionValue on() {
		return ON;
	}

	public static OptionValue off() {
		return OFF;
	}

	public static OptionValue of(String text) {
		return new OptionValue(false, false, text);
	}

	public static OptionValue parse(String text) {
		String value = text.trim();

		return switch (value.toLowerCase(Locale.ROOT)) {
			case "on", "true" -> ON;
			case "off", "false" -> OFF;
			default -> of(value);
		};
	}

	public boolean isBoolean() {
		return this.bool;
	}

	public boolean asBoolean() {
		return this.enabled;
	}

	public String text() {
		return this.text;
	}

	public String asText() {
		if (!this.bool) {
			return this.text;
		}

		return this.enabled ? "on" : "off";
	}

	@Override
	public String toString() {
		return this.bool ? Boolean.toString(this.enabled) : this.text;
	}
}
