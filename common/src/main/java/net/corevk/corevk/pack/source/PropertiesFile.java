package net.corevk.corevk.pack.source;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PropertiesFile {

	private static final Pattern DIRECTIVE = Pattern.compile("^\\s*#\\s*(if|ifdef|ifndef|else|elif|endif)\\b.*$");
	private static final Pattern DEFINE = Pattern.compile("^\\s*#define\\s+([A-Za-z_]\\w*)(.*)$");
	private static final Pattern UNDEF = Pattern.compile("^\\s*#undef\\s+([A-Za-z_]\\w*).*$");

	private final String name;
	private final List<String> lines;

	private PropertiesFile(String name, List<String> lines) {
		this.name = name;
		this.lines = lines;
	}

	public static PropertiesFile read(ShaderPackSource source, String name) throws IOException {
		Optional<Path> file = source.file(name);
		if (file.isEmpty()) {
			return new PropertiesFile(name, List.of());
		}

		return new PropertiesFile(name, List.copyOf(source.readLines(file.get())));
	}

	public String name() {
		return this.name;
	}

	public boolean present() {
		return !this.lines.isEmpty();
	}

	
	public void walk(Map<String, String> defines, Consumer<String> line) {
		walk(this.lines, defines, line);
	}

	
	public void walkDefining(Map<String, String> defines, BiConsumer<String, Map<String, String>> line) {
		walkDefining(this.lines, defines, line);
	}

	
	static void walk(List<String> lines, Map<String, String> defines, Consumer<String> line) {
		walkDefining(lines, defines, (text, _) -> line.accept(text));
	}

	
	static void walkDefining(List<String> lines, Map<String, String> defines,
			BiConsumer<String, Map<String, String>> line) {
		Map<String, String> live = new LinkedHashMap<>(defines);
		ConditionStack conditions = new ConditionStack();
		StringBuilder joined = null;

		for (String text : lines) {
			Matcher directive = DIRECTIVE.matcher(text);
			if (directive.matches()) {
				apply(directive.group(1), text, conditions, live);
				continue;
			}

			if (!conditions.active()) {
				continue;
			}

			Matcher defined = DEFINE.matcher(text);
			if (defined.matches()) {
				live.put(defined.group(1), defined.group(2).replaceFirst("//.*$", "").trim());
				continue;
			}

			Matcher undefined = UNDEF.matcher(text);
			if (undefined.matches()) {
				live.remove(undefined.group(1));
				continue;
			}

			boolean continues = text.endsWith("\\");
			String body = continues ? text.substring(0, text.length() - 1) : text;
			if (joined == null) {
				joined = new StringBuilder(body);
			} else {
				joined.append(' ').append(body.stripLeading());
			}

			if (!continues) {
				line.accept(joined.toString(), live);
				joined = null;
			}
		}

		// A file whose last line asks to be continued. The pack is wrong and the value is still
		// worth handing over, since everything before the backslash is a complete declaration.
		if (joined != null) {
			line.accept(joined.toString(), live);
		}
	}

	
	private static void apply(String keyword, String line, ConditionStack conditions,
			Map<String, String> defines) {
		switch (keyword) {
			case "ifdef", "ifndef" -> {
				Matcher name = IncludeExpander.DEFINED_NAME
						.matcher(line.replaceFirst("^\\s*#\\s*\\w+", ""));
				// Nothing that can name a setting after the keyword leaves the group taken, and
				// anything written after the name is dropped. These files carry the conditionals
				// a pack's shaders carry, so they get the same reading: one stack, one decision.
				conditions.ifDirective(!name.matches()
						|| keyword.equals("ifdef") == defines.containsKey(name.group(1)));
			}
			case "if" -> conditions.ifDirective(decide(after(line), defines));
			case "elif" -> conditions.elifDirective(() -> decide(after(line), defines));
			case "else" -> conditions.elseDirective();
			default -> conditions.endifDirective();
		}
	}

	
	private static boolean decide(String expression, Map<String, String> defines) {
		return !expression.isBlank()
				&& PreprocessorExpression.evaluate(expression, defines).orElse(true);
	}

	private static String after(String line) {
		return line.replaceAll("^\\s*#\\s*\\w+\\s*", "");
	}
}
