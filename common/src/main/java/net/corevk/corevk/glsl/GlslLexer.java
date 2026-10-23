package net.corevk.corevk.glsl;

import java.util.ArrayList;
import java.util.List;

public final class GlslLexer {

	private static final String[] OPERATORS = new String[128];

	static {
		for (char c = 0; c < OPERATORS.length; c++) {
			OPERATORS[c] = String.valueOf(c);
		}
	}

	private GlslLexer() {
	}

	public enum Kind {

		IDENTIFIER,
		NUMBER,
		OPERATOR,
		COMMENT,
		SPACE,
		NEWLINE,

		HASH,

		RAW
	}

	public record Token(Kind kind, String text, String directive, boolean macroName) {

		public static final Token BLANK = new Token(Kind.SPACE, "", null);

		public Token(Kind kind, String text, String directive) {
			this(kind, text, directive, false);
		}

		public boolean trivia() {
			return this.kind == Kind.SPACE || this.kind == Kind.COMMENT;
		}

		public boolean identifier(String name) {
			return this.kind == Kind.IDENTIFIER && this.text.equals(name);
		}

		public boolean operator(String symbol) {
			return this.kind == Kind.OPERATOR && this.text.equals(symbol);
		}

		public Token as(String replacement) {
			return new Token(this.kind, replacement, this.directive, this.macroName);
		}

		public Token naming() {
			return new Token(this.kind, this.text, this.directive, true);
		}
	}

	public static List<Token> lex(String source) {
		List<Token> tokens = new ArrayList<>();
		int index = 0;
		int length = source.length();

		while (index < length) {
			char c = source.charAt(index);

			if (c == '\n' || c == '\r') {
				int end = index + 1;
				if (c == '\r' && end < length && source.charAt(end) == '\n') {
					end++;
				}

				tokens.add(new Token(Kind.NEWLINE, source.substring(index, end), null));
				index = end;
				continue;
			}

			if (c == '\\' && index + 1 < length && (source.charAt(index + 1) == '\n' || source.charAt(index + 1) == '\r')) {
				int end = index + 2;
				if (source.charAt(index + 1) == '\r' && end < length && source.charAt(end) == '\n') {
					end++;
				}

				tokens.add(new Token(Kind.SPACE, source.substring(index, end), null));
				index = end;
				continue;
			}

			if (c == ' ' || c == '\t' || c == '\f' || c == 0x0B) {
				int end = index;
				while (end < length && isBlank(source.charAt(end))) {
					end++;
				}

				tokens.add(new Token(Kind.SPACE, source.substring(index, end), null));
				index = end;
				continue;
			}

			if (c == '/' && index + 1 < length && source.charAt(index + 1) == '/') {
				int end = index;
				while (end < length && source.charAt(end) != '\n' && source.charAt(end) != '\r') {
					end++;
				}

				tokens.add(new Token(Kind.COMMENT, source.substring(index, end), null));
				index = end;
				continue;
			}

			if (c == '/' && index + 1 < length && source.charAt(index + 1) == '*') {
				int close = source.indexOf("*/", index + 2);
				int end = close < 0 ? length : close + 2;
				tokens.add(new Token(Kind.COMMENT, source.substring(index, end), null));
				index = end;
				continue;
			}

			if (isDigit(c) || (c == '.' && index + 1 < length && isDigit(source.charAt(index + 1)))) {
				int end = numberEnd(source, index);
				tokens.add(new Token(Kind.NUMBER, source.substring(index, end), null));
				index = end;
				continue;
			}

			if (isIdentifierStart(c)) {
				int end = index;
				while (end < length && isIdentifierPart(source.charAt(end))) {
					end++;
				}

				tokens.add(new Token(Kind.IDENTIFIER, source.substring(index, end), null));
				index = end;
				continue;
			}

			tokens.add(new Token(Kind.OPERATOR, c < OPERATORS.length ? OPERATORS[c] : String.valueOf(c),
					null));
			index++;
		}

		return markDirectives(tokens);
	}

	public static String join(List<Token> tokens) {
		StringBuilder text = new StringBuilder();
		for (Token token : tokens) {
			text.append(token.text());
		}

		return text.toString();
	}

	private static List<Token> markDirectives(List<Token> tokens) {
		List<Token> marked = new ArrayList<>(tokens.size());
		boolean lineStart = true;
		int index = 0;

		while (index < tokens.size()) {
			Token token = tokens.get(index);

			if (token.kind() == Kind.NEWLINE) {
				marked.add(token);
				lineStart = true;
				index++;
				continue;
			}

			if (token.trivia()) {
				marked.add(token);
				index++;
				continue;
			}

			if (!lineStart || !token.operator("#")) {
				marked.add(token);
				lineStart = false;
				index++;
				continue;
			}

			int end = index;
			while (end < tokens.size() && tokens.get(end).kind() != Kind.NEWLINE) {
				end++;
			}

			String name = "";
			for (int scan = index + 1; scan < end; scan++) {
				Token candidate = tokens.get(scan);
				if (candidate.trivia()) {
					continue;
				}

				name = candidate.kind() == Kind.IDENTIFIER ? candidate.text() : "";
				break;
			}

			marked.add(new Token(Kind.HASH, token.text(), name));
			for (int scan = index + 1; scan < end; scan++) {
				Token inside = tokens.get(scan);
				marked.add(new Token(inside.kind(), inside.text(), name));
			}

			index = end;
			lineStart = false;
		}

		return marked;
	}

	private static int numberEnd(String source, int start) {
		int end = start;

		while (end < source.length()) {
			char c = source.charAt(end);
			if (isIdentifierPart(c) || c == '.') {
				end++;
				continue;
			}

			if ((c == '+' || c == '-') && end > start) {
				char previous = source.charAt(end - 1);
				if (previous == 'e' || previous == 'E') {
					end++;
					continue;
				}
			}

			break;
		}

		return end;
	}

	private static boolean isBlank(char c) {
		return c == ' ' || c == '\t' || c == '\f' || c == 0x0B;
	}

	private static boolean isDigit(char c) {
		return c >= '0' && c <= '9';
	}

	private static boolean isIdentifierStart(char c) {
		return c == '_' || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
	}

	private static boolean isIdentifierPart(char c) {
		return isIdentifierStart(c) || isDigit(c);
	}
}
