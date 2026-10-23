package net.corevk.corevk.pack.option;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OptionIndex {

	private static final Pattern DEFINE =
			Pattern.compile("^\\s*(//\\s*)?#\\s*define\\s+([A-Za-z_]\\w*)\\s*(.*)$");
	private static final Pattern CONSTANT =
			Pattern.compile("^\\s*const\\s+(int|float|bool|uint)\\s+([A-Za-z_]\\w*)\\s*=\\s*([^;]+);(.*)$");

	private static final Pattern VALUE_LIST = Pattern.compile("//[^\\[]*\\[(.*?)]");
	private static final Pattern TRAILING_COMMENT = Pattern.compile("//.*");

	private static final Pattern CONDITIONAL =
			Pattern.compile("^\\s*#(?:ifdef|ifndef)\\s+([A-Za-z_]\\w*)\\s*$");

	private final Map<String, PackOption> byName;
	private final Set<String> conditionalReferences;

	private OptionIndex(Map<String, PackOption> byName, Set<String> conditionalReferences) {
		this.byName = Map.copyOf(byName);
		this.conditionalReferences = Set.copyOf(conditionalReferences);
	}

	public static final class Reader {

		private final Map<String, PackOption> found = new LinkedHashMap<>();
		private final Set<String> referenced = new HashSet<>();

		public void read(String where, List<String> lines) {
			for (int i = 0; i < lines.size(); i++) {
				PackOption option = parse(lines.get(i), where, i + 1);
				if (option != null) {
					this.found.putIfAbsent(option.name(), option);
				}

				Matcher conditional = CONDITIONAL.matcher(lines.get(i));
				if (conditional.matches()) {
					this.referenced.add(conditional.group(1));
				}
			}
		}

		public OptionIndex index() {
			return new OptionIndex(this.found, this.referenced);
		}
	}

	private static PackOption parse(String line, String where, int lineNumber) {
		Matcher define = DEFINE.matcher(line);
		if (define.matches()) {
			String rest = define.group(3);
			String defaultText = TRAILING_COMMENT.matcher(rest).replaceAll("").trim();

			return new PackOption(define.group(2),
					defaultText.isEmpty() ? PackOption.Kind.TOGGLE : PackOption.Kind.VALUE,
					defaultText, valueList(rest), define.group(1) != null, null, where, lineNumber);
		}

		Matcher constant = CONSTANT.matcher(line);
		if (constant.matches()) {
			return new PackOption(constant.group(2), PackOption.Kind.CONST, constant.group(3).trim(),
					valueList(constant.group(4)), false, constant.group(1), where, lineNumber);
		}

		return null;
	}

	private static List<String> valueList(String text) {
		Matcher list = VALUE_LIST.matcher(text);
		if (!list.find()) {
			return List.of();
		}

		return List.of(list.group(1).trim().split("\\s+", -1)).stream().filter(token -> !token.isEmpty()).toList();
	}

	public Optional<PackOption> get(String name) {
		return Optional.ofNullable(this.byName.get(name));
	}

	public boolean referenced(String name) {
		return this.conditionalReferences.contains(name);
	}

	public boolean offers(PackOption option) {
		return switch (option.kind()) {
			case TOGGLE -> referenced(option.name());
			case VALUE -> option.hasValueList();
			case CONST -> ConstOptions.isOption(option.name())
					&& !"uint".equals(option.constType())
					&& ("bool".equals(option.constType()) ? referenced(option.name())
							: option.hasValueList());
		};
	}

	public Collection<PackOption> all() {
		return this.byName.values();
	}

	public Set<String> names() {
		return this.byName.keySet();
	}

	public int count() {
		return this.byName.size();
	}

	public int countByKind(PackOption.Kind kind) {
		return (int) this.byName.values().stream().filter(option -> option.kind() == kind).count();
	}

	public int disabledCount() {
		return (int) this.byName.values().stream().filter(PackOption::defaultOff).count();
	}

	public int withValueListCount() {
		return (int) this.byName.values().stream().filter(PackOption::hasValueList).count();
	}

	public Collection<String> caseCollisions() {
		Map<String, String> seen = new LinkedHashMap<>();
		Collection<String> collisions = new TreeSet<>();

		for (String name : this.byName.keySet()) {
			String previous = seen.putIfAbsent(name.toLowerCase(Locale.ROOT), name);
			if (previous != null) {
				collisions.add(previous);
				collisions.add(name);
			}
		}

		return collisions;
	}
}
