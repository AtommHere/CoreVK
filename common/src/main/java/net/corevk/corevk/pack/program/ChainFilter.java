package net.corevk.corevk.pack.program;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

public record ChainFilter(List<String> only, List<String> without, int limit) {

	
	public static final ChainFilter ALL = new ChainFilter(List.of(), -1);

	
	private static final Pattern NAME = Pattern.compile("[A-Za-z_]\\w*");

	public ChainFilter {
		only = List.copyOf(only);
		without = List.copyOf(without);
	}

	public ChainFilter(List<String> only, int limit) {
		this(only, List.of(), limit);
	}

	
	public static ChainFilter parse(String text) {
		if (text == null) {
			return ALL;
		}

		String trimmed = text.trim();
		if (trimmed.isEmpty()) {
			return ALL;
		}

		if (trimmed.chars().allMatch(Character::isDigit)) {
			try {
				return new ChainFilter(List.of(), Integer.parseInt(trimmed));
			} catch (NumberFormatException e) {
				return ALL;
			}
		}

		List<String> names = new ArrayList<>();
		// Limit 0, the one-argument reading: a trailing empty field goes and a leading one stays,
		// so "composite4," filters and ",composite4" falls back to the whole chain. Lopsided, and
		// kept lopsided on purpose - the two answers are a filter and no filter, and moving a
		// debugging knob's fallback is not something a build gate should be allowed to decide.
		for (String token : trimmed.split(",", 0)) {
			String name = token.trim();
			if (!NAME.matcher(name).matches()) {
				return ALL;
			}

			names.add(name);
		}

		return names.isEmpty() ? ALL : new ChainFilter(names, -1);
	}

	
	public ChainFilter without(Collection<String> programs) {
		if (programs.isEmpty()) {
			return this;
		}

		List<String> refused = new ArrayList<>(this.without);
		programs.stream().filter(name -> !refused.contains(name)).forEach(refused::add);

		return new ChainFilter(this.only, refused, this.limit);
	}

	
	public boolean refuses(String bareName) {
		return this.without.contains(bareName);
	}

	
	public boolean accepts(String bareName, int rank) {
		if (refuses(bareName)) {
			return false;
		}

		if (!this.only.isEmpty()) {
			return this.only.contains(bareName);
		}

		return this.limit < 0 || rank < this.limit;
	}
}
