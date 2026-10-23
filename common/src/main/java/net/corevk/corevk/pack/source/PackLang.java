package net.corevk.corevk.pack.source;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

public final class PackLang {

	private static final String FALLBACK_CODE = "en_us";

	private static final PackLang EMPTY = new PackLang("", Map.of());

	private final String file;
	private final Map<String, String> entries;

	private PackLang(String file, Map<String, String> entries) {
		this.file = file;
		this.entries = entries;
	}

	public static PackLang read(ShaderPackSource source, String languageCode) throws IOException {
		Optional<Path> found = find(source, languageCode);
		if (found.isEmpty()) {
			return EMPTY;
		}

		Path file = found.get();
		Properties properties = new Properties();
		try {

			properties.load(new StringReader(String.join("\n", source.readLines(file))));
		} catch (IllegalArgumentException e) {

			throw new IOException(source.rel(file) + " is not readable as a language file", e);
		}

		Map<String, String> entries = new LinkedHashMap<>();
		properties.forEach((key, value) -> entries.put(key.toString(), value.toString()));

		Path name = file.getFileName();

		return new PackLang(name == null ? "" : name.toString(), Map.copyOf(entries));
	}

	private static Optional<Path> find(ShaderPackSource source, String languageCode) {
		String code = languageCode.toLowerCase(Locale.ROOT);
		Optional<Path> file = source.resolveInsideShaders("lang/" + code + ".lang");
		if (file.isPresent() || code.equals(FALLBACK_CODE)) {
			return file;
		}

		return source.resolveInsideShaders("lang/" + FALLBACK_CODE + ".lang");
	}

	public static PackLang empty() {
		return EMPTY;
	}

	public String file() {
		return this.file;
	}

	public int size() {
		return this.entries.size();
	}

	public String option(String name) {
		return this.entries.getOrDefault("option." + name, name);
	}

	public Optional<String> optionComment(String name) {
		return entry("option." + name + ".comment");
	}

	public String value(String name, String value) {
		String prefix = this.entries.getOrDefault("prefix." + name, "");
		String named = this.entries.get("value." + name + "." + value);
		if (named != null) {
			return prefix + named;
		}

		return prefix + value + this.entries.getOrDefault("suffix." + name, "");
	}

	public String page(String id) {
		return this.entries.getOrDefault("screen." + id, id);
	}

	public Optional<String> pageComment(String id) {
		return entry("screen." + id + ".comment");
	}

	public String profile(String name) {
		return this.entries.getOrDefault("profile." + name, name);
	}

	public Optional<String> profileComment() {
		return entry("profile.comment");
	}

	private Optional<String> entry(String key) {
		return Optional.ofNullable(this.entries.get(key));
	}
}
