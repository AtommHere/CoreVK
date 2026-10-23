package net.corevk.corevk.pack.target;

import net.corevk.corevk.pack.source.IncludeExpander;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ConstDirectives {

	private static final String[] TYPES = { "int", "float", "vec2", "ivec3", "vec4", "bool" };

	private ConstDirectives() {
	}

	public record Directive(String type, String name, String value, int line) {
	}

	public static List<Directive> read(IncludeExpander.ExpandedUnit unit) {
		List<Directive> found = new ArrayList<>();
		List<String> lines = unit.lines();

		for (int index = 0; index < lines.size(); index++) {
			if (unit.isLive(index)) {
				parse(lines.get(index), index).ifPresent(found::add);
			}
		}

		return List.copyOf(found);
	}

	public static Optional<Directive> readLine(String line) {
		return parse(line, -1);
	}

	private static Optional<Directive> parse(String line, int number) {

		if (!line.contains("const") || !line.contains("=") || !line.contains(";")) {
			return Optional.empty();
		}

		String rest = line.trim();
		if (!rest.startsWith("const")) {
			return Optional.empty();
		}

		rest = rest.substring("const".length());
		if (!startsWithSpace(rest)) {
			return Optional.empty();
		}

		rest = rest.trim();
		String type = null;
		for (String candidate : TYPES) {
			if (rest.startsWith(candidate)) {
				type = candidate;
				rest = rest.substring(candidate.length());
				break;
			}
		}

		if (type == null || !startsWithSpace(rest)) {
			return Optional.empty();
		}

		int equals = rest.indexOf('=');
		if (equals < 0) {
			return Optional.empty();
		}

		String name = rest.substring(0, equals).trim();
		if (!isWord(name)) {
			return Optional.empty();
		}

		String tail = rest.substring(equals + 1);
		int semicolon = tail.indexOf(';');
		if (semicolon < 0) {
			return Optional.empty();
		}

		return Optional.of(new Directive(type, name, tail.substring(0, semicolon).trim(), number));
	}

	private static boolean startsWithSpace(String text) {
		return !text.isEmpty() && Character.isWhitespace(text.charAt(0));
	}

	private static boolean isWord(String text) {
		if (text.isEmpty()) {
			return false;
		}

		for (int index = 0; index < text.length(); index++) {
			char character = text.charAt(index);
			if (!Character.isDigit(character) && !Character.isAlphabetic(character) && character != '_') {
				return false;
			}
		}

		return true;
	}
}
