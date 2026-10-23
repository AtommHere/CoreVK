package net.corevk.corevk.pack.source;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public final class PreprocessorExpression {

	private static final int MAX_RESOLUTION_DEPTH = 8;

	private static final int MAX_NESTING_DEPTH = 64;

	private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/");
	private static final Pattern LINE_COMMENT = Pattern.compile("//.*");

	private final List<String> tokens;
	private final Map<String, String> defines;
	private final int depth;

	private int position;
	private int nesting;

	private boolean failed;

	private boolean malformed;

	private boolean fractional;

	private record Scalar(double number, long whole, boolean integral) {

		static Scalar of(long value) {
			return new Scalar(value, value, true);
		}

		static Scalar of(boolean truth) {
			return of(truth ? 1 : 0);
		}

		static Scalar fraction(double value) {
			return new Scalar(value, (long) value, false);
		}

		boolean truth() {
			return this.integral ? this.whole != 0 : this.number != 0;
		}
	}

	private PreprocessorExpression(List<String> tokens, Map<String, String> defines, int depth) {
		this.tokens = tokens;
		this.defines = defines;
		this.depth = depth;
	}

	public static Optional<Boolean> evaluate(String expression, Map<String, String> defines) {
		return decide(expression, defines).taken();
	}

	public record Verdict(Optional<Boolean> taken, boolean fractional) {
	}

	public static Verdict decide(String expression, Map<String, String> defines) {
		Reading read = read(expression, defines, 0);

		return new Verdict(read.value().map(Scalar::truth), read.fractional());
	}

	private record Reading(Optional<Scalar> value, boolean fractional) {
	}

	private static Reading read(String expression, Map<String, String> defines, int depth) {
		if (depth > MAX_RESOLUTION_DEPTH) {
			return new Reading(Optional.empty(), false);
		}

		List<String> tokens = tokenise(stripComments(expression));
		if (tokens.isEmpty()) {
			return new Reading(Optional.empty(), false);
		}

		PreprocessorExpression parser = new PreprocessorExpression(tokens, defines, depth);
		Scalar result = parser.logicalOr();
		if (parser.failed || parser.malformed || parser.position < tokens.size()) {
			return new Reading(Optional.empty(), parser.fractional);
		}

		return new Reading(Optional.of(result), parser.fractional);
	}

	private static String stripComments(String expression) {
		return LINE_COMMENT.matcher(BLOCK_COMMENT.matcher(expression).replaceAll(" ")).replaceAll("");
	}

	private static List<String> tokenise(String text) {
		List<String> tokens = new ArrayList<>();
		int i = 0;

		while (i < text.length()) {
			char c = text.charAt(i);
			if (Character.isWhitespace(c)) {
				i++;
			} else if (Character.isLetter(c) || c == '_') {
				int start = i;
				while (i < text.length() && (Character.isLetterOrDigit(text.charAt(i)) || text.charAt(i) == '_')) {
					i++;
				}

				tokens.add(text.substring(start, i));
			} else if (Character.isDigit(c)) {
				int start = i;
				while (i < text.length() && isNumberPart(text.charAt(i))) {
					i++;
				}

				tokens.add(text.substring(start, i));
			} else {

				if (i + 1 < text.length() && isTwoCharacterOperator(text.substring(i, i + 2))) {
					tokens.add(text.substring(i, i + 2));
					i += 2;
				} else {
					tokens.add(String.valueOf(c));
					i++;
				}
			}
		}

		return tokens;
	}

	private static boolean isNumberPart(char c) {
		return Character.isLetterOrDigit(c) || c == '.';
	}

	private static boolean isTwoCharacterOperator(String pair) {
		return switch (pair) {
			case "&&", "||", "==", "!=", "<=", ">=", "<<", ">>" -> true;
			default -> false;
		};
	}

	private String peek() {
		return this.position < this.tokens.size() ? this.tokens.get(this.position) : null;
	}

	private boolean accept(String token) {
		if (token.equals(peek())) {
			this.position++;
			return true;
		}

		return false;
	}

	private Scalar logicalOr() {
		Scalar left = logicalAnd();
		while (accept("||")) {
			boolean decided = this.failed;
			Scalar right = logicalAnd();
			if (left.truth()) {
				this.failed = decided;
			}

			left = Scalar.of(left.truth() || right.truth());
		}

		return left;
	}

	private Scalar logicalAnd() {
		Scalar left = bitwiseOr();
		while (accept("&&")) {
			boolean decided = this.failed;
			Scalar right = bitwiseOr();
			if (!left.truth()) {
				this.failed = decided;
			}

			left = Scalar.of(left.truth() && right.truth());
		}

		return left;
	}

	private Scalar bitwiseOr() {
		Scalar left = bitwiseXor();
		while ("|".equals(peek())) {
			this.position++;
			left = bits(left, bitwiseXor(), "|");
		}

		return left;
	}

	private Scalar bitwiseXor() {
		Scalar left = bitwiseAnd();
		while (accept("^")) {
			left = bits(left, bitwiseAnd(), "^");
		}

		return left;
	}

	private Scalar bitwiseAnd() {
		Scalar left = equality();
		while ("&".equals(peek())) {
			this.position++;
			left = bits(left, equality(), "&");
		}

		return left;
	}

	private Scalar bits(Scalar left, Scalar right, String operator) {
		if (!left.integral() || !right.integral()) {
			this.failed = true;

			return Scalar.of(0);
		}

		long a = left.whole();
		long b = right.whole();

		return Scalar.of(switch (operator) {
			case "|" -> a | b;
			case "^" -> a ^ b;
			default -> a & b;
		});
	}

	private Scalar equality() {
		Scalar left = relational();
		while (true) {
			if (accept("==")) {
				left = Scalar.of(compare(left, relational()) == 0);
			} else if (accept("!=")) {
				left = Scalar.of(compare(left, relational()) != 0);
			} else {
				return left;
			}
		}
	}

	private Scalar relational() {
		Scalar left = shift();
		while (true) {
			if (accept("<=")) {
				left = Scalar.of(compare(left, shift()) <= 0);
			} else if (accept(">=")) {
				left = Scalar.of(compare(left, shift()) >= 0);
			} else if (accept("<")) {
				left = Scalar.of(compare(left, shift()) < 0);
			} else if (accept(">")) {
				left = Scalar.of(compare(left, shift()) > 0);
			} else {
				return left;
			}
		}
	}

	private static int compare(Scalar left, Scalar right) {
		return left.integral() && right.integral()
				? Long.compare(left.whole(), right.whole())
				: Double.compare(left.number(), right.number());
	}

	private Scalar shift() {
		Scalar left = additive();
		while (true) {
			if (accept("<<")) {
				left = shiftBy(left, additive(), true);
			} else if (accept(">>")) {
				left = shiftBy(left, additive(), false);
			} else {
				return left;
			}
		}
	}

	private Scalar shiftBy(Scalar left, Scalar places, boolean up) {
		if (!left.integral() || !places.integral()
				|| places.whole() < 0 || places.whole() > 63) {
			this.failed = true;

			return Scalar.of(0);
		}

		return Scalar.of(up ? left.whole() << places.whole() : left.whole() >> places.whole());
	}

	private Scalar additive() {
		Scalar left = multiplicative();
		while (true) {
			if (accept("+")) {
				Scalar right = multiplicative();
				left = left.integral() && right.integral()
						? Scalar.of(left.whole() + right.whole())
						: Scalar.fraction(left.number() + right.number());
			} else if (accept("-")) {
				Scalar right = multiplicative();
				left = left.integral() && right.integral()
						? Scalar.of(left.whole() - right.whole())
						: Scalar.fraction(left.number() - right.number());
			} else {
				return left;
			}
		}
	}

	private Scalar multiplicative() {
		Scalar left = unary();
		while (true) {
			if (accept("*")) {
				Scalar right = unary();
				left = left.integral() && right.integral()
						? Scalar.of(left.whole() * right.whole())
						: Scalar.fraction(left.number() * right.number());
			} else if (accept("/")) {
				left = divide(left, unary(), false);
			} else if (accept("%")) {
				left = divide(left, unary(), true);
			} else {
				return left;
			}
		}
	}

	private Scalar divide(Scalar left, Scalar right, boolean remainder) {
		if (!right.truth()) {
			this.failed = true;

			return Scalar.of(0);
		}

		if (!left.integral() || !right.integral()) {
			if (remainder) {
				this.failed = true;

				return Scalar.of(0);
			}

			return Scalar.fraction(left.number() / right.number());
		}

		long a = left.whole();
		long b = right.whole();
		if (a == Long.MIN_VALUE && b == -1) {
			this.failed = true;

			return Scalar.of(0);
		}

		return Scalar.of(remainder ? a % b : a / b);
	}

	private Scalar unary() {
		String token = peek();
		if (token == null || !isPrefix(token)) {
			return primary();
		}

		this.position++;
		if (++this.nesting > MAX_NESTING_DEPTH) {
			this.malformed = true;
			return Scalar.of(0);
		}

		Scalar value = unary();
		this.nesting--;

		switch (token) {
			case "!":
				return Scalar.of(!value.truth());
			case "-":
				return value.integral() ? Scalar.of(-value.whole())
						: Scalar.fraction(-value.number());
			case "~":
				if (!value.integral()) {
					this.failed = true;

					return Scalar.of(0);
				}

				return Scalar.of(~value.whole());
			default:
				return value;
		}
	}

	private static boolean isPrefix(String token) {
		return switch (token) {
			case "!", "-", "+", "~" -> true;
			default -> false;
		};
	}

	private Scalar primary() {
		String token = peek();
		if (token == null) {
			this.malformed = true;
			return Scalar.of(0);
		}

		if (accept("(")) {
			if (++this.nesting > MAX_NESTING_DEPTH) {
				this.malformed = true;
				return Scalar.of(0);
			}

			Scalar value = logicalOr();
			this.nesting--;
			if (!accept(")")) {
				this.malformed = true;
			}

			return value;
		}

		this.position++;

		if (token.equals("defined")) {
			return definedOperator();
		}

		if (isIdentifier(token)) {
			Reading read = resolve(token, this.defines, this.depth + 1);
			this.fractional |= read.fractional();

			return read.value().orElse(Scalar.of(0));
		}

		Optional<Scalar> number = parseNumber(token);
		if (number.isEmpty()) {
			this.malformed = true;
			return Scalar.of(0);
		}

		this.fractional |= !number.get().integral();

		return number.get();
	}

	private Scalar definedOperator() {
		boolean parenthesised = accept("(");
		String name = peek();
		if (name == null || !isIdentifier(name)) {
			this.malformed = true;
			return Scalar.of(0);
		}

		this.position++;
		if (parenthesised && !accept(")")) {
			this.malformed = true;
			return Scalar.of(0);
		}

		return Scalar.of(this.defines.containsKey(name));
	}

	private static Reading resolve(String name, Map<String, String> defines, int depth) {
		if (depth > MAX_RESOLUTION_DEPTH) {
			return new Reading(Optional.of(Scalar.of(0)), false);
		}

		String value = defines.get(name);
		if (value == null || value.isBlank()) {

			return new Reading(Optional.of(Scalar.of(defines.containsKey(name))), false);
		}

		String trimmed = value.trim();
		Optional<Scalar> number = parseNumber(trimmed);
		if (number.isPresent()) {
			return new Reading(number, !number.get().integral());
		}

		if (isIdentifier(trimmed)) {
			return resolve(trimmed, defines, depth + 1);
		}

		Reading read = read(trimmed, defines, depth + 1);

		return new Reading(Optional.of(read.value().orElse(Scalar.of(1))), read.fractional());
	}

	private static boolean isIdentifier(String token) {
		if (token.isEmpty() || !(Character.isLetter(token.charAt(0)) || token.charAt(0) == '_')) {
			return false;
		}

		return token.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == '_');
	}

	private static Optional<Scalar> parseNumber(String token) {
		boolean hexadecimal = token.length() > 2 && (token.startsWith("0x") || token.startsWith("0X"));

		String suffixes = hexadecimal ? "uUlL" : "uUlLfF";
		String text = token;
		while (!text.isEmpty() && suffixes.indexOf(text.charAt(text.length() - 1)) >= 0) {
			text = text.substring(0, text.length() - 1);
		}

		if (text.isEmpty()) {
			return Optional.empty();
		}

		try {
			if (hexadecimal) {
				return Optional.of(Scalar.of(Long.parseLong(text.substring(2), 16)));
			}

			if (text.indexOf('.') >= 0 || text.indexOf('e') >= 0 || text.indexOf('E') >= 0) {
				return Optional.of(Scalar.fraction(Double.parseDouble(text)));
			}

			return Optional.of(Scalar.of(Long.parseLong(text)));
		} catch (NumberFormatException e) {
			return Optional.empty();
		}
	}
}
