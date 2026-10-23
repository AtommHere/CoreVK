package net.corevk.corevk.pack.id;

import net.corevk.corevk.pack.source.PropertiesFile;
import net.corevk.corevk.pack.source.ShaderPackSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BlockIds {

	
	private static final Pattern RUN_ON = Pattern.compile("(?<=\\S)[ \\t]*(?=block\\.\\d)");

	private static final Pattern KEY = Pattern.compile("^\\s*block\\.(\\d+)\\s*$");

	private final List<Entry> entries;
	private final List<String> problems;

	private BlockIds(List<Entry> entries, List<String> problems) {
		this.entries = List.copyOf(entries);
		this.problems = List.copyOf(problems);
	}

	
	public record Entry(boolean tag, String namespace, String path, Map<String, String> predicates,
			int id) {

		public Entry {
			predicates = Map.copyOf(predicates);
		}

		
		public String name() {
			return this.namespace + ":" + this.path;
		}
	}

	
	public static BlockIds read(ShaderPackSource source, Map<String, String> defines)
			throws IOException {
		PropertiesFile file = PropertiesFile.read(source, "block.properties");
		List<Entry> entries = new ArrayList<>();
		List<String> problems = new ArrayList<>();

		file.walk(defines, line -> {
			for (String statement : RUN_ON.split(line, -1)) {
				readStatement(statement, entries, problems);
			}
		});

		return new BlockIds(entries, problems);
	}

	private static void readStatement(String statement, List<Entry> entries, List<String> problems) {
		int equals = statement.indexOf('=');
		if (equals < 0) {
			return;
		}

		Matcher key = KEY.matcher(statement.substring(0, equals));
		if (!key.matches()) {
			// layer.translucent and the like. Another feature of the same file, not ours to read.
			return;
		}

		int id;
		try {
			id = Integer.parseInt(key.group(1));
		} catch (NumberFormatException e) {
			problems.add("block." + key.group(1) + " is not a number this engine can hold");
			return;
		}

		for (String entry : statement.substring(equals + 1).trim().split("\\s+", -1)) {
			if (!entry.isEmpty()) {
				parse(entry, id, entries, problems);
			}
		}
	}

	
	private static void parse(String entry, int id, List<Entry> entries, List<String> problems) {
		boolean tag = entry.startsWith("%");

		// Limit 0, which is the reading the one-argument form has: the empty terms at the END of the
		// entry go. That is what this depends on. 'stone:' has to arrive as the single term 'stone',
		// so that the default namespace goes in front of it and the entry binds minecraft:stone;
		// under a limit that kept the empty, 'stone' would be taken for the namespace instead and
		// the declaration would light nothing. CoreVK splits at the default limit too, in
		// BlockEntry.parse, and then parts company: it builds its identifier from the entry as
		// written rather than from the split, so the colon travels into the path and the entry
		// fails validation there. Same reading of the separator, different answer after it.
		//
		// The length is tested before the index below because this form answers ':' with an EMPTY
		// array, which is the other half of the surprise and the half that throws.
		String[] parts = (tag ? entry.substring(1) : entry).split(":", 0);
		if (parts.length == 0 || parts[0].isEmpty()) {
			problems.add("block." + id + " lists '" + entry + "', which names nothing");
			return;
		}

		String namespace = "minecraft";
		String path = parts[0];
		int from = 1;
		if (parts.length > 1 && !parts[1].contains("=")) {
			namespace = parts[0];
			path = parts[1];
			from = 2;
		}

		Map<String, String> predicates = new LinkedHashMap<>();
		for (int at = from; at < parts.length; at++) {
			int split = parts[at].indexOf('=');
			if (split < 0) {
				// Named rather than dropped: the entry still binds, on more states than the pack
				// meant, and that is a block lit as something else rather than a block left out.
				problems.add("block." + id + " lists '" + entry + "', where '" + parts[at]
						+ "' is not a property filter of the form key=value");
				continue;
			}

			predicates.put(parts[at].substring(0, split), parts[at].substring(split + 1));
		}

		entries.add(new Entry(tag, namespace, path, predicates, id));
	}

	
	public List<Entry> entries() {
		return this.entries;
	}

	
	public List<String> problems() {
		return this.problems;
	}

	public boolean isEmpty() {
		return this.entries.isEmpty();
	}

	
	public int highest() {
		return this.entries.stream().mapToInt(Entry::id).max().orElse(0);
	}
}
