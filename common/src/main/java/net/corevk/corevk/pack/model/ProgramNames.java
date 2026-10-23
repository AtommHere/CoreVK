package net.corevk.corevk.pack.model;

import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ProgramNames {

	private static Set<String> named() {
		return ProgramFallbacks.names();
	}

	private static final Set<String> NUMBERED = Set.of(
			"composite", "deferred", "prepare", "shadowcomp", "setup", "begin");

	private static final Set<String> SIMPLE = Set.of("shadow", "final");

	private static final Set<String> CHAINED = Set.of("begin", "prepare", "deferred", "composite",
			"final");

	private static final int MAX_SLOT = 99;

	private static final Pattern COMPUTE_SUFFIX = Pattern.compile("^(.*?)_([a-z])$");

	public static final int GEOMETRY_RANK = 3;

	private ProgramNames() {
	}

	public static int frameRank(String family) {
		if (geometry(family)) {
			return GEOMETRY_RANK;
		}

		return switch (family) {
			case "begin" -> 0;
			case "shadowcomp" -> 1;
			case "prepare" -> 2;
			case "deferred" -> 4;
			case "composite" -> 5;
			default -> 6;
		};
	}

	public static Comparator<String> frameOrder() {
		return Comparator
				.comparingInt((String program) -> frameRank(familyOf(program)))
				.thenComparingInt(program -> parse(program)
						.map(ProgramName::slot)
						.orElse(Integer.MAX_VALUE));
	}

	public static boolean before(String program, String other) {
		return frameOrder().compare(program, other) < 0;
	}

	public static int directiveRank(String family) {
		return switch (family) {
			case "shadowcomp" -> 0;
			case "begin" -> 1;
			case "prepare" -> 2;
			case "deferred" -> 4;
			case "composite" -> 5;
			default -> 3;
		};
	}

	public static boolean geometry(String family) {
		return family.startsWith("gbuffers") || family.startsWith("dh_")
				|| shadowGeometry(family);
	}

	public static boolean shadowComposite(String family) {
		return family.equals("shadowcomp");
	}

	public static boolean shadowGeometry(String family) {
		return family.equals("shadow") || family.startsWith("shadow_") || family.equals("dh_shadow");
	}

	public static String familyOf(String baseName) {
		return parse(baseName).map(ProgramName::family).orElse(baseName);
	}

	public static Optional<String> computeBase(String file) {
		return parse(file)
				.filter(name -> CHAINED.contains(name.family()))
				.map(ProgramName::baseName);
	}

	public static String computeLetter(String file) {
		return parse(file).map(ProgramName::computeSuffix).orElse("");
	}

	public static Optional<ProgramName> parse(String baseName) {
		if (named().contains(baseName) || SIMPLE.contains(baseName)) {
			return Optional.of(new ProgramName(baseName, -1, null));
		}

		Optional<ProgramName> numbered = parseNumbered(baseName);
		if (numbered.isPresent()) {
			return numbered;
		}

		Matcher compute = COMPUTE_SUFFIX.matcher(baseName);
		if (compute.matches()) {
			String stem = compute.group(1);
			if (SIMPLE.contains(stem)) {
				return Optional.of(new ProgramName(stem, -1, compute.group(2)));
			}

			Optional<ProgramName> stemNumbered = parseNumbered(stem);
			if (stemNumbered.isPresent()) {
				ProgramName base = stemNumbered.get();
				return Optional.of(new ProgramName(base.family(), base.slot(), compute.group(2)));
			}
		}

		return Optional.empty();
	}

	private static Optional<ProgramName> parseNumbered(String baseName) {
		for (String family : NUMBERED) {
			if (!baseName.startsWith(family)) {
				continue;
			}

			String tail = baseName.substring(family.length());
			if (tail.isEmpty()) {
				return Optional.of(new ProgramName(family, 0, null));
			}

			if (tail.length() > 2 || !tail.chars().allMatch(Character::isDigit)) {
				continue;
			}

			int slot = Integer.parseInt(tail);
			if (slot <= MAX_SLOT) {
				return Optional.of(new ProgramName(family, slot, null));
			}
		}

		return Optional.empty();
	}

	public record ProgramName(String family, int slot, String computeSuffix) {

		public boolean isCompute() {
			return this.computeSuffix != null;
		}

		public String baseName() {
			return this.slot < 0 ? this.family : this.family + (this.slot == 0 ? "" : Integer.toString(this.slot));
		}
	}
}
