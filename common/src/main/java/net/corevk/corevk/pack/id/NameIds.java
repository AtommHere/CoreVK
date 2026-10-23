package net.corevk.corevk.pack.id;

import net.corevk.corevk.pack.source.Macros;
import net.corevk.corevk.pack.source.PropertiesFile;
import net.corevk.corevk.pack.source.ShaderPackSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NameIds {

	
	public enum Kind {

		ITEM("item"),
		ENTITY("entity");

		private final String keyword;

		Kind(String keyword) {
			this.keyword = keyword;
		}

		String keyword() {
			return this.keyword;
		}

		String fileName() {
			return this.keyword + ".properties";
		}

		
		Pattern entry() {
			return Pattern.compile("^\\s*" + this.keyword + "\\.(\\d+)\\s*=\\s*(.*)$");
		}
	}

	
	public static final int NONE = -1;

	private static final NameIds EMPTY = new NameIds(false, Map.of(), List.of());

	private final boolean present;
	private final Map<String, Integer> byName;
	private final List<String> problems;

	private NameIds(boolean present, Map<String, Integer> byName, List<String> problems) {
		this.present = present;
		this.byName = Map.copyOf(byName);
		this.problems = List.copyOf(problems);
	}

	public static NameIds empty() {
		return EMPTY;
	}

	public static NameIds read(ShaderPackSource source, Map<String, String> defines, Kind kind)
			throws IOException {
		PropertiesFile file = PropertiesFile.read(source, kind.fileName());
		if (!file.present()) {
			return EMPTY;
		}

		Map<String, Integer> byName = new LinkedHashMap<>();
		List<String> problems = new ArrayList<>();
		Pattern lines = kind.entry();

		file.walkDefining(defines, (line, live) -> {
			Matcher entry = lines.matcher(line);
			if (!entry.matches()) {
				return;
			}

			int id;
			try {
				id = Integer.parseInt(entry.group(1));
			} catch (NumberFormatException e) {
				problems.add(kind.keyword() + "." + entry.group(1) + ": the identifier is not a number");
				return;
			}

			for (String token : Macros.expand(entry.group(2).trim(), live).split("\\s+", -1)) {
				if (token.isEmpty()) {
					continue;
				}

				if (token.indexOf('=') >= 0) {
					problems.add(kind.keyword() + "." + id + ": " + token
							+ " selects on state, which is not read here");
					continue;
				}

				// The last declaration wins, which is what a pack that overrides its own table
				// upgrade path expects, and the earlier one is named rather than lost quietly.
				Integer previous = byName.put(namespaced(token), id);
				if (previous != null && previous != id) {
					problems.add(token + ": claimed by " + kind.keyword() + "." + previous + " and by "
							+ kind.keyword() + "." + id);
				}
			}
		});

		return new NameIds(true, byName, problems);
	}

	private static String namespaced(String token) {
		return token.indexOf(':') < 0 ? "minecraft:" + token : token;
	}

	public boolean present() {
		return this.present;
	}

	
	public int id(String namespacedName) {
		return this.byName.getOrDefault(namespacedName, NONE);
	}

	public Map<String, Integer> byName() {
		return this.byName;
	}

	
	public List<String> problems() {
		return this.problems;
	}
}
