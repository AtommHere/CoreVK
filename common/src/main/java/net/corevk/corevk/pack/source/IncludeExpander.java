package net.corevk.corevk.pack.source;

import net.corevk.corevk.glsl.CompilerMacros;
import net.corevk.corevk.glsl.LoadClock;
import net.corevk.corevk.pack.model.ProgramStage;
import net.corevk.corevk.pack.option.OptionRewriter;
import net.corevk.corevk.pack.option.SettingSet;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class IncludeExpander {

	private static final int MAX_DEPTH = 32;

	private static final int MAX_FILES = 20_000;

	private static final int MAX_LINES = 400_000;

	private static final int MAX_CHARACTERS = 4_000_000;

	private static final Pattern INCLUDE = Pattern.compile("^\\s*#\\s*include\\s+[<\"](.+?)[>\"].*$");
	private static final Pattern IF_DEFINED = Pattern.compile("^(\\s*)#\\s*(ifdef|ifndef)\\b(.*)$");

	static final Pattern DEFINED_NAME = Pattern.compile("^\\s+([A-Za-z_]\\w*)(.*)$");

	private static final Pattern IF = Pattern.compile("^(\\s*)#\\s*if\\b(.*)$");
	private static final Pattern ELIF = Pattern.compile("^(\\s*)#\\s*elif\\b(.*)$");
	private static final Pattern ELSE = Pattern.compile("^\\s*#\\s*else\\b.*$");
	private static final Pattern ENDIF = Pattern.compile("^\\s*#\\s*endif\\b.*$");
	private static final Pattern DEFINE = Pattern.compile("^\\s*#\\s*define\\s+([A-Za-z_]\\w*)\\s*(.*)$");
	private static final Pattern UNDEF = Pattern.compile("^\\s*#\\s*undef\\s+([A-Za-z_]\\w*).*$");
	private static final Pattern VERSION = Pattern.compile("^\\s*#\\s*version\\s+(.*)$");
	private static final Pattern ERROR = Pattern.compile("^\\s*#\\s*error\\b.*$");

	private static final Pattern LINE_COMMENT = Pattern.compile("//.*");
	private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/");

	private final ShaderPackSource source;
	private final SettingSet settings;
	private final boolean partOfALoad;

	private final Set<String> loose = new LinkedHashSet<>();

	public IncludeExpander(ShaderPackSource source, SettingSet settings) {
		this(source, settings, true);
	}

	private IncludeExpander(ShaderPackSource source, SettingSet settings, boolean partOfALoad) {
		this.source = source;
		this.settings = settings;
		this.partOfALoad = partOfALoad;
	}

	public static IncludeExpander forTheReport(ShaderPackSource source, SettingSet settings) {
		return new IncludeExpander(source, settings, false);
	}

	public ExpandedUnit expand(Path entry) throws IOException {
		String relative = this.source.rel(entry);
		if (this.partOfALoad) {
			Optional<ExpandedUnit> known = this.source.expandedUnit(this.settings, relative);
			if (known.isPresent()) {
				LoadClock.expansionServed();

				return known.get();
			}
		}

		long began = System.nanoTime();
		State state = new State(startingDefines(relative), this.loose);

		expandFile(entry, 0, state);

		ExpandedUnit unit = new ExpandedUnit(relative, List.copyOf(state.output), state.version,
				state.toStats(), state.live, state.defines);
		if (this.partOfALoad) {
			LoadClock.expansion(System.nanoTime() - began);
			this.source.rememberUnit(this.settings, relative, unit);
		}

		return unit;
	}

	private Map<String, String> startingDefines(String relative) {
		Map<String, String> defines = new LinkedHashMap<>();
		ProgramStage.ofFile(relative)
				.ifPresent(stage -> defines.putAll(CompilerMacros.definedIn(stage)));
		defines.putAll(this.settings.unitDefines());

		return defines;
	}

	public List<String> looseDirectives() {
		return List.copyOf(this.loose);
	}

	private void expandFile(Path file, int depth, State state) throws IOException {
		String relative = this.source.rel(file);
		state.maxDepth = Math.max(state.maxDepth, depth);

		if (depth > MAX_DEPTH) {
			state.tooDeep++;
			state.emit("#error include nesting too deep at " + relative, true);
			return;
		}

		if (++state.filesExpanded > MAX_FILES || state.overBudget()) {
			state.giveUp(relative);
			return;
		}

		if (!state.onPath.add(relative)) {
			state.cycles++;
			state.emit("#error include cycle at " + relative, true);
			return;
		}

		if (!state.everSeen.add(relative)) {
			state.duplicates++;
		}

		ConditionStack conditions = new ConditionStack();

		for (Logical logical : logicalLines(file)) {

			if (state.overBudget()) {
				state.giveUp(relative);
				break;
			}

			String line = logical.text();
			if (state.inBlockComment && !INCLUDE.matcher(line).matches()) {
				String settled = conditions.active()
						? OptionRewriter.apply(line, this.settings.chosen(), this.settings.scale())
						: line;
				if (settled.equals(line)) {
					state.emit(logical.physical(), conditions.active());
				} else {
					state.emit(settled, true);
				}

				continue;
			}

			String directive = handleCondition(logical, relative, conditions, state);
			if (directive != null) {
				if (directive.equals(line)) {
					state.emit(logical.physical(), true);
				} else {
					state.emit(directive, true);
				}

				continue;
			}

			if (ERROR.matcher(line).matches()) {
				report(conditions, state, relative, logical,
						"the reference only logs it, so it is not written out");

				state.emit(BlockComments.openAfter(line, false)
						? "/* error not written out */ /*"
						: "// error not written out: " + line.trim(), conditions.active());
				continue;
			}

			Matcher include = INCLUDE.matcher(line);
			if (include.matches()) {
				state.seen++;
				if (conditions.active()) {
					follow(file, include.group(1), depth, state);
				} else {
					state.skipped++;
					state.emit("// include not taken: " + include.group(1), false);
				}

				continue;
			}

			if (!conditions.active()) {

				state.emit(logical.physical(), false);
				continue;
			}

			Matcher version = VERSION.matcher(line);
			if (version.matches()) {

				if (state.version == null) {
					state.version = LINE_COMMENT.matcher(version.group(1)).replaceAll("").trim();
					state.emit(logical.physical(), true);
				}

				continue;
			}

			String tracked = track(line, state);
			if (tracked.equals(line)) {
				state.emit(logical.physical(), true);
			} else {
				state.emit(tracked, true);
			}
		}

		for (int open = conditions.unclosedRewritten(); open > 0; open--) {
			state.emit("#endif", true);
			state.openInOutput--;
		}

		state.onPath.remove(relative);
	}

	private record Logical(String text, int number, List<String> physical) {
	}

	private record LogicalLinesOf(Path file) {
	}

	private List<Logical> logicalLines(Path file) throws IOException {
		if (!this.partOfALoad) {
			return logicalLines(this.source.readLines(file));
		}

		return this.source.derived(new LogicalLinesOf(file),
				() -> logicalLines(this.source.readLines(file)));
	}

	private static List<Logical> logicalLines(List<String> lines) {
		List<Logical> logical = new ArrayList<>(lines.size());
		StringBuilder joined = new StringBuilder();
		List<String> physical = new ArrayList<>();
		int number = 1;
		for (String line : lines) {
			physical.add(line);
			if (line.endsWith("\\")) {
				joined.append(line, 0, line.length() - 1);
				continue;
			}

			joined.append(line);
			logical.add(new Logical(joined.toString(), number, List.copyOf(physical)));
			number += physical.size();
			joined.setLength(0);
			physical.clear();
		}

		if (!physical.isEmpty()) {
			logical.add(new Logical(joined.toString(), number, List.copyOf(physical)));
		}

		return logical;
	}

	private String handleCondition(Logical logical, String relative, ConditionStack conditions,
			State state) {
		String line = logical.text();

		Matcher ifDefined = IF_DEFINED.matcher(line);
		if (ifDefined.matches()) {
			state.conditionals++;
			state.openInOutput++;

			return defined(logical, relative, ifDefined, conditions, state);
		}

		Matcher iff = IF.matcher(line);
		if (iff.matches()) {
			state.conditionals++;
			state.openInOutput++;
			if (!beyondComments(iff.group(2)).isEmpty()) {
				String written = rewritten(iff.group(2), state);
				if (written != null) {
					conditions.rewrittenIfDirective(written.equals("1"));

					return iff.group(1) + "#if " + written;
				}

				conditions.ifDirective(() -> decide(iff.group(2), state));
				return line;
			}

			report(conditions, state, relative, logical,
					"no expression follows it, so the group it opens is not taken");
			conditions.rewrittenIfDirective(false);

			return iff.group(1) + "#if 0";
		}

		Matcher elif = ELIF.matcher(line);
		if (elif.matches()) {
			state.conditionals++;
			if (state.openInOutput == 0) {
				return unopened(conditions, state, relative, logical);
			}

			if (!beyondComments(elif.group(2)).isEmpty()) {
				String written = rewritten(elif.group(2), state);
				if (written != null) {
					conditions.rewrittenElifDirective(written.equals("1"));

					return elif.group(1) + "#elif " + written;
				}

				conditions.elifDirective(() -> decide(elif.group(2), state));
				return line;
			}

			report(conditions, state, relative, logical,
					"no expression follows it, so the branch it opens is not taken");
			conditions.elifDirective(() -> false);

			return elif.group(1) + "#elif 0";
		}

		if (ELSE.matcher(line).matches()) {
			if (state.openInOutput == 0) {
				return unopened(conditions, state, relative, logical);
			}

			conditions.elseDirective();

			return line;
		}

		if (ENDIF.matcher(line).matches()) {
			if (state.openInOutput == 0) {
				return unopened(conditions, state, relative, logical);
			}

			state.openInOutput--;
			conditions.endifDirective();

			return line;
		}

		return null;
	}

	private static String unopened(ConditionStack conditions, State state, String relative,
			Logical logical) {
		report(conditions, state, relative, logical,
				"nothing is open for it to close, so it is not written out");

		return "// no conditional is open here: " + logical.text().trim();
	}

	private static void report(ConditionStack conditions, State state, String relative,
			Logical logical, String decided) {
		if (conditions.active()) {
			state.loose(relative, logical, decided);
		}
	}

	private String defined(Logical logical, String relative, Matcher ifDefined,
			ConditionStack conditions, State state) {
		String indent = ifDefined.group(1);
		String keyword = ifDefined.group(2);
		Matcher name = DEFINED_NAME.matcher(ifDefined.group(3));

		if (!name.matches()) {
			report(conditions, state, relative, logical,
					"nothing that can name a setting follows it, so the group it opens is taken");
			conditions.rewrittenIfDirective(true);

			return indent + "#if 1";
		}

		boolean defined = state.defines.containsKey(name.group(1));
		conditions.ifDirective(keyword.equals("ifdef") == defined);

		if (beyondComments(name.group(2)).isEmpty()) {
			return logical.text();
		}

		report(conditions, state, relative, logical, "only " + name.group(1) + " is read");

		return indent + "#" + keyword + " " + name.group(1);
	}

	private static String beyondComments(String tail) {
		String code = BLOCK_COMMENT.matcher(tail).replaceAll(" ");
		int line = code.indexOf("//");
		int unterminated = code.indexOf("/*");
		if (line >= 0) {
			code = code.substring(0, line);
		}

		if (unterminated >= 0 && (line < 0 || unterminated < line)) {
			code = code.substring(0, unterminated);
		}

		return code.trim();
	}

	private boolean decide(String expression, State state) {
		Optional<Boolean> value = PreprocessorExpression.evaluate(expression, state.defines);
		if (value.isEmpty()) {
			state.undecidable++;
			return true;
		}

		return value.get();
	}

	private String rewritten(String expression, State state) {
		PreprocessorExpression.Verdict verdict =
				PreprocessorExpression.decide(expression, state.defines);
		if (!verdict.fractional()) {
			return null;
		}

		return verdict.taken().orElse(true) ? "1" : "0";
	}

	private void follow(Path from, String spec, int depth, State state) throws IOException {
		Optional<Path> target = spec.startsWith("/")
				? this.source.resolveInsideShaders(spec)
				: this.source.resolveRelativeTo(from, spec);

		if (target.isEmpty()) {
			state.missing++;

			state.emit("#error include not found: " + spec, true);
			return;
		}

		state.followed++;
		expandFile(target.get(), depth + 1, state);
	}

	private String track(String line, State state) {
		Matcher undef = UNDEF.matcher(line);
		if (undef.matches()) {
			state.defines.remove(undef.group(1));
			return line;
		}

		Matcher original = DEFINE.matcher(line);
		String rewritten = OptionRewriter.apply(line, this.settings.chosen(), this.settings.scale());
		Matcher applied = DEFINE.matcher(rewritten);

		if (applied.matches()) {
			state.defines.put(applied.group(1),
					LINE_COMMENT.matcher(applied.group(2)).replaceAll("").trim());
		} else if (original.matches()) {

			state.defines.remove(original.group(1));
		}

		return rewritten;
	}

	public record ExpandedUnit(String entry, List<String> lines, String version,
			ExpansionStats stats, BitSet live, Map<String, String> defines) {

		public ExpandedUnit {
			live = (BitSet) live.clone();
			defines = Map.copyOf(defines);
		}

		@Override
		public BitSet live() {
			return (BitSet) this.live.clone();
		}

		public String text() {
			return String.join("\n", this.lines);
		}

		public boolean isLive(int line) {
			return this.live.get(line);
		}
	}

	private static final class State {

		private final Map<String, String> defines;
		private final Set<String> loose;
		private final List<String> output = new ArrayList<>();
		private final BitSet live = new BitSet();
		private final Set<String> onPath = new HashSet<>();
		private final Set<String> everSeen = new HashSet<>();

		private String version;
		private long characters;
		private int filesExpanded;
		private int exhausted;
		private int seen;
		private int followed;
		private int skipped;
		private int missing;
		private int duplicates;
		private int cycles;
		private int maxDepth;
		private int tooDeep;
		private int conditionals;
		private int undecidable;

		private int openInOutput;

		private boolean inBlockComment;

		private State(Map<String, String> defines, Set<String> loose) {
			this.defines = new LinkedHashMap<>(defines);
			this.loose = loose;
		}

		private void loose(String relative, Logical logical, String decided) {
			this.loose.add(relative + ":" + logical.number() + " writes " + logical.text().trim()
					+ ", and " + decided);
		}

		private void emit(String line, boolean taken) {
			add(line, taken);
			this.inBlockComment = BlockComments.openAfter(line, this.inBlockComment);
		}

		private void emit(List<String> lines, boolean taken) {
			StringBuilder joined = new StringBuilder();
			for (String line : lines) {
				add(line, taken);
				joined.append(line.endsWith("\\") ? line.substring(0, line.length() - 1) : line);
			}

			this.inBlockComment = BlockComments.openAfter(joined.toString(), this.inBlockComment);
		}

		private void add(String line, boolean taken) {
			this.live.set(this.output.size(), taken);
			this.output.add(line);
			this.characters += line.length() + 1;
		}

		private boolean overBudget() {
			return this.output.size() > MAX_LINES || this.characters > MAX_CHARACTERS;
		}

		private void giveUp(String relative) {
			if (this.exhausted++ == 0) {
				emit("#error include budget exhausted at " + relative, true);
			}
		}

		private ExpansionStats toStats() {
			return new ExpansionStats(this.seen, this.followed, this.skipped, this.missing,
					this.duplicates, this.cycles, this.maxDepth, this.tooDeep, this.conditionals,
					this.undecidable, this.exhausted);
		}
	}
}
