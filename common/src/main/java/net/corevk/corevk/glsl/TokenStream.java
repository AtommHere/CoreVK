package net.corevk.corevk.glsl;

import net.corevk.corevk.glsl.GlslLexer.Kind;
import net.corevk.corevk.glsl.GlslLexer.Token;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

final class TokenStream implements Iterable<Token> {

	private static final int MAX_STATEMENT_TOKENS = 4096;

	private final List<Token> tokens;

	private int functionEndFor = -1;
	private int functionEndAt;

	private int[] lineTable;

	TokenStream(List<Token> tokens) {
		this.tokens = new ArrayList<>(tokens);
	}

	int size() {
		return this.tokens.size();
	}

	Token get(int index) {
		return this.tokens.get(index);
	}

	@Override
	public Iterator<Token> iterator() {
		return Collections.unmodifiableList(this.tokens).iterator();
	}

	String join() {
		return GlslLexer.join(this.tokens);
	}

	void naming(int index) {
		set(index, this.tokens.get(index).naming());
	}

	record Closing(int at, String text, String directive) {
	}

	record Insertion(int at, Token token) {
	}

	void insertTokens(List<Insertion> insertions) {
		if (insertions.isEmpty()) {
			return;
		}

		this.functionEndFor = -1;
		this.lineTable = null;
		List<Insertion> ordered = new ArrayList<>(insertions);
		ordered.sort(Comparator.comparingInt(Insertion::at));
		List<Token> rebuilt = new ArrayList<>(this.tokens.size() + ordered.size());
		int next = 0;
		for (int at = 0; at <= this.tokens.size(); at++) {
			while (next < ordered.size() && ordered.get(next).at() == at) {
				rebuilt.add(ordered.get(next).token());
				next++;
			}

			if (at < this.tokens.size()) {
				rebuilt.add(this.tokens.get(at));
			}
		}

		this.tokens.clear();
		this.tokens.addAll(rebuilt);
	}

	void insertClosings(List<Closing> closings) {
		if (closings.isEmpty()) {
			return;
		}

		this.functionEndFor = -1;
		this.lineTable = null;

		closings.sort(Comparator.comparingInt(Closing::at));
		List<Token> rebuilt = new ArrayList<>(this.tokens.size() + closings.size());
		int next = 0;
		for (int at = 0; at <= this.tokens.size(); at++) {
			int first = next;
			while (next < closings.size() && closings.get(next).at() == at) {
				next++;
			}

			for (int closing = next - 1; closing >= first; closing--) {
				Closing one = closings.get(closing);
				rebuilt.add(new Token(Kind.RAW, one.text(), one.directive()));
			}

			if (at < this.tokens.size()) {
				rebuilt.add(this.tokens.get(at));
			}
		}

		this.tokens.clear();
		this.tokens.addAll(rebuilt);
	}

	void replace(int index, String text) {
		set(index, this.tokens.get(index).as(text));
	}

	void inject(int index, String text) {
		set(index, new Token(Kind.RAW, text, this.tokens.get(index).directive()));
	}

	void blank(int index) {
		if (index >= 0) {
			set(index, Token.BLANK);
		}
	}

	void blankRange(int start, int end) {
		for (int index = start; index <= end; index++) {
			if (this.tokens.get(index).kind() != Kind.NEWLINE) {
				set(index, blanked(this.tokens.get(index)));
			}
		}
	}

	void blankDirective(int hash) {
		for (int index = hash; index < this.tokens.size(); index++) {
			if (this.tokens.get(index).kind() == Kind.NEWLINE) {
				return;
			}

			set(index, blanked(this.tokens.get(index)));
		}
	}

	private static Token blanked(Token token) {
		int breaks = breaks(token.text());

		return breaks == 0
				? Token.BLANK
				: new Token(Kind.SPACE, "\n".repeat(breaks), token.directive());
	}

	private void set(int index, Token token) {
		this.functionEndFor = -1;
		if (breaks(this.tokens.get(index).text()) != breaks(token.text())) {
			this.lineTable = null;
		}

		this.tokens.set(index, token);
	}

	private static int breaks(String text) {
		int breaks = 0;
		for (int at = 0; at < text.length(); at++) {
			if (text.charAt(at) == '\n') {
				breaks++;
			}
		}

		return breaks;
	}

	int significantAfter(int index) {
		if (index < 0) {
			return -1;
		}

		boolean directive = this.tokens.get(index).directive() != null;
		for (int scan = index + 1; scan < this.tokens.size(); scan++) {
			Token token = this.tokens.get(scan);
			if (token.trivia()) {
				continue;
			}

			if (token.kind() == Kind.NEWLINE) {
				if (directive) {
					return -1;
				}

				continue;
			}

			return scan;
		}

		return -1;
	}

	int macroNameAfter(int hash) {
		boolean keywordSeen = false;

		for (int scan = hash + 1; scan < this.tokens.size(); scan++) {
			Token token = this.tokens.get(scan);
			if (token.kind() == Kind.NEWLINE) {
				return -1;
			}

			if (token.trivia()) {
				continue;
			}

			if (!keywordSeen) {
				keywordSeen = true;
				continue;
			}

			return token.kind() == Kind.IDENTIFIER ? scan : -1;
		}

		return -1;
	}

	int significantBefore(int index) {
		for (int scan = index - 1; scan >= 0; scan--) {
			Token token = this.tokens.get(scan);
			if (token.trivia() || token.kind() == Kind.NEWLINE) {
				continue;
			}

			return token.directive() == null ? scan : -1;
		}

		return -1;
	}

	int[] lineNumbers() {
		if (this.lineTable != null) {
			return this.lineTable;
		}

		int[] lines = new int[this.tokens.size()];
		int line = 0;

		for (int index = 0; index < this.tokens.size(); index++) {
			lines[index] = line;
			line += breaks(this.tokens.get(index).text());
		}

		this.lineTable = lines;

		return lines;
	}

	int callOpener(int index) {
		int next = significantAfter(index);

		return next >= 0 && this.tokens.get(next).operator("(") ? next : -1;
	}

	int matchingBracket(int open) {
		if (open < 0) {
			return -1;
		}

		String opening = this.tokens.get(open).text();
		String closing = switch (opening) {
			case "(" -> ")";
			case "{" -> "}";
			default -> "]";
		};
		int depth = 0;

		for (int scan = open; scan < this.tokens.size(); scan++) {
			Token token = this.tokens.get(scan);
			if (token.kind() != Kind.OPERATOR) {
				continue;
			}

			if (token.operator(opening)) {
				depth++;
			} else if (token.operator(closing)) {
				depth--;
				if (depth == 0) {
					return scan;
				}
			}
		}

		return -1;
	}

	int functionEnd(int parameters) {

		if (parameters == this.functionEndFor) {
			return this.functionEndAt;
		}

		int end = walkFunctionEnd(parameters);
		this.functionEndFor = parameters;
		this.functionEndAt = end;

		return end;
	}

	private int walkFunctionEnd(int parameters) {
		int close = matchingBracket(parameters);
		int brace = close < 0 ? -1 : significantAfter(close);
		if (brace < 0 || !this.tokens.get(brace).operator("{")) {
			return close < 0 ? this.tokens.size() - 1 : close;
		}

		int end = matchingBracket(brace);

		return end < 0 ? this.tokens.size() - 1 : end;
	}

	int statementStart(int index) {
		int limit = Math.max(0, index - MAX_STATEMENT_TOKENS);
		int start = -1;

		for (int scan = index - 1; scan >= limit; scan--) {
			Token token = this.tokens.get(scan);
			boolean boundary = token.kind() == Kind.HASH || token.directive() != null
					|| token.operator(";") || token.operator("{") || token.operator("}");
			if (boundary) {
				start = scan + 1;
				break;
			}
		}

		if (start < 0) {

			if (limit > 0) {
				return -1;
			}

			start = 0;
		}

		while (start < index) {
			Token token = this.tokens.get(start);
			if (!token.trivia() && token.kind() != Kind.NEWLINE) {
				break;
			}

			start++;
		}

		return start;
	}

	int statementEnd(int index) {
		int depth = 0;
		int last = Math.min(this.tokens.size(), index + MAX_STATEMENT_TOKENS);

		for (int scan = index; scan < last; scan++) {
			Token token = this.tokens.get(scan);
			if (token.kind() != Kind.OPERATOR || token.directive() != null) {
				continue;
			}

			String text = token.text();
			if (text.equals("(") || text.equals("[")) {
				depth++;
			} else if (text.equals(")") || text.equals("]")) {
				depth--;
			} else if (depth == 0 && text.equals("{")) {
				return -1;
			} else if (depth == 0 && text.equals(";")) {
				return scan;
			}
		}

		return -1;
	}

	List<Integer> significantRange(int start, int end) {
		List<Integer> found = new ArrayList<>();
		for (int scan = start; scan <= end; scan++) {
			Token token = this.tokens.get(scan);
			if (!token.trivia() && token.kind() != Kind.NEWLINE) {
				found.add(scan);
			}
		}

		return found;
	}
}
