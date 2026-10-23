package net.corevk.corevk.pack.model;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;

public record AlphaTest(Function function, float reference) {

	
	public static final AlphaTest OFF = new AlphaTest(Function.ALWAYS, 0.0F);

	
	public static final AlphaTest CUTOUT = new AlphaTest(Function.GREATER, 0.5F);

	
	public static final AlphaTest ONE_TENTH = new AlphaTest(Function.GREATER, 0.1F);

	
	public static final AlphaTest NON_ZERO = new AlphaTest(Function.GREATER, 0.0001F);

	
	public enum Function {

		NEVER(null),
		LESS("<"),
		EQUAL("=="),
		LEQUAL("<="),
		GREATER(">"),
		NOTEQUAL("!="),
		GEQUAL(">="),
		ALWAYS(null);

		private final String operator;

		Function(String operator) {
			this.operator = operator;
		}
	}

	
	public static Optional<AlphaTest> parse(String raw) {
		String value = raw.trim();
		if (value.equalsIgnoreCase("off") || value.equalsIgnoreCase("false")) {
			return Optional.of(OFF);
		}

		// A third token is ignored rather than refused, which is what CoreVK does with it: a trailing
		// comment on the line would otherwise cost the pack its threshold.
		//
		// One space and not a run of whitespace, because that is the separator CoreVK gives this
		// directive: a value written with a tab or a double space parses into empty words there, and
		// reading it here would accept a line the reference refuses.
		String[] parts = value.split(" ", -1);
		if (parts.length < 2) {
			return Optional.empty();
		}

		// GL_ALWAYS is the spelling shaders.properties documents, and every other name is written
		// bare. Both are taken, as CoreVK takes both.
		String name = parts[0].startsWith("GL_") ? parts[0].substring(3) : parts[0];
		try {
			return Optional.of(new AlphaTest(Function.valueOf(name.toUpperCase(Locale.ROOT)),
					Float.parseFloat(parts[1])));
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}

	
	public boolean tests() {
		return this.function != Function.ALWAYS;
	}

	
	public String discard(String alpha) {
		if (this.function == Function.ALWAYS) {
			return "";
		}

		if (this.function == Function.NEVER) {
			return "discard;";
		}

		return "if (!(" + alpha + " " + this.function.operator + " " + literal() + ")) { discard; }";
	}

	
	private String literal() {
		String plain = new BigDecimal(Float.toString(this.reference)).toPlainString();

		return plain.indexOf('.') < 0 ? plain + ".0" : plain;
	}
}
