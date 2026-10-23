package net.corevk.corevk.pack.source;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Macros {

	private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_]\\w*");

	private static final int MAX_ROUNDS = 4;

	private Macros() {
	}

	public static String expand(String text, Map<String, String> defines) {
		String current = text;

		for (int round = 0; round < MAX_ROUNDS; round++) {
			String next = expandOnce(current, defines);
			if (next.equals(current)) {
				return current;
			}

			current = next;
		}

		return current;
	}

	private static String expandOnce(String text, Map<String, String> defines) {
		Matcher identifier = IDENTIFIER.matcher(text);
		StringBuilder out = new StringBuilder();

		while (identifier.find()) {
			String name = identifier.group();
			String value = defines.get(name);
			boolean substitute = value != null && !value.isBlank() && !value.equals(name)
					&& !component(text, identifier.start());

			identifier.appendReplacement(out, Matcher.quoteReplacement(substitute ? value : name));
		}

		identifier.appendTail(out);

		return out.toString();
	}

	private static boolean component(String text, int start) {
		for (int i = start - 1; i >= 0; i--) {
			char c = text.charAt(i);
			if (c == '.') {
				return true;
			}
			if (!Character.isWhitespace(c)) {
				return false;
			}
		}

		return false;
	}
}
