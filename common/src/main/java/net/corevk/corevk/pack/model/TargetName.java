package net.corevk.corevk.pack.model;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

public final class TargetName {

	public static final int MAX_TARGETS = 32;

	private static final String PREFIX = "colortex";

	private static final String IMAGE_PREFIX = "colorimg";

	private static final List<String> LEGACY = List.of(
			"gcolor", "gdepth", "gnormal", "composite", "gaux1", "gaux2", "gaux3", "gaux4");

	private TargetName() {
	}

	public static OptionalInt index(String name) {
		int legacy = LEGACY.indexOf(name);
		if (legacy >= 0) {
			return OptionalInt.of(legacy);
		}

		if (!name.startsWith(PREFIX)) {
			return OptionalInt.empty();
		}

		String digits = name.substring(PREFIX.length());
		if (digits.isEmpty() || digits.length() > 2 || !digits.chars().allMatch(Character::isDigit)) {
			return OptionalInt.empty();
		}

		int index = Integer.parseInt(digits);

		return index < MAX_TARGETS ? OptionalInt.of(index) : OptionalInt.empty();
	}

	public static OptionalInt imageIndex(String name) {
		if (!name.startsWith(IMAGE_PREFIX)) {
			return OptionalInt.empty();
		}

		String digits = name.substring(IMAGE_PREFIX.length());
		if (digits.isEmpty() || digits.length() > 2 || !digits.chars().allMatch(Character::isDigit)) {
			return OptionalInt.empty();
		}

		int index = Integer.parseInt(digits);

		return index < MAX_TARGETS ? OptionalInt.of(index) : OptionalInt.empty();
	}

	public static String canonical(int index) {
		return PREFIX + index;
	}

	public static Optional<String> legacyAlias(int index) {
		return index >= 0 && index < LEGACY.size() ? Optional.of(LEGACY.get(index)) : Optional.empty();
	}

	public static Optional<Suffixed> split(String directiveName) {
		if (directiveName.startsWith(PREFIX)) {
			int cursor = PREFIX.length();
			while (cursor < directiveName.length() && Character.isDigit(directiveName.charAt(cursor))) {
				cursor++;
			}

			OptionalInt index = index(directiveName.substring(0, cursor));
			if (index.isEmpty() || cursor == directiveName.length()) {
				return Optional.empty();
			}

			return Optional.of(new Suffixed(index.getAsInt(), directiveName.substring(cursor)));
		}

		for (int index = 0; index < LEGACY.size(); index++) {
			String alias = LEGACY.get(index);
			if (directiveName.startsWith(alias) && directiveName.length() > alias.length()) {
				return Optional.of(new Suffixed(index, directiveName.substring(alias.length())));
			}
		}

		return Optional.empty();
	}

	public record Suffixed(int index, String suffix) {
	}

	public static String bareName(String program) {
		int slash = program.lastIndexOf('/');

		return slash < 0 ? program : program.substring(slash + 1);
	}
}
