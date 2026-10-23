package net.corevk.corevk.settings;

import net.corevk.corevk.pack.option.OptionValue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class SettingsLayers {

	private static final String DIRECTORY = "corevk";

	private static final String OPTIONS = "options.txt";

	private SettingsLayers() {
	}

	public static Path file(Path gameDirectory) {
		return gameDirectory.resolve(DIRECTORY).resolve(OPTIONS);
	}

	public static Map<String, OptionValue> forced(Path gameDirectory) throws IOException {
		Path file = file(gameDirectory);
		if (!Files.isRegularFile(file)) {
			return Map.of();
		}

		Map<String, OptionValue> chosen = new LinkedHashMap<>();
		for (String line : text(file).lines().toList()) {
			String trimmed = line.trim();
			int equals = trimmed.indexOf('=');
			if (trimmed.isEmpty() || trimmed.startsWith("#") || equals < 1) {
				continue;
			}

			chosen.put(trimmed.substring(0, equals).trim(),
					OptionValue.parse(trimmed.substring(equals + 1).trim()));
		}

		return Collections.unmodifiableMap(chosen);
	}

	private static String text(Path file) throws IOException {
		String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

		return text.startsWith("\uFEFF") ? text.substring(1) : text;
	}

	public static Resolved resolve(SettingsFile.Stored saved, Map<String, OptionValue> forced) {
		Map<String, OptionValue> chosen = new LinkedHashMap<>();
		saved.values().forEach((name, value) -> chosen.put(name, OptionValue.parse(value)));

		String profile = "";
		Set<String> forcedNames = new LinkedHashSet<>(forced.keySet());
		for (Map.Entry<String, OptionValue> entry : forced.entrySet()) {
			if (SettingsFile.PROFILE_KEY.equals(entry.getKey())) {
				profile = entry.getValue().asText();
			} else {
				chosen.put(entry.getKey(), entry.getValue());
			}
		}

		return new Resolved(Collections.unmodifiableMap(chosen), profile,
				Collections.unmodifiableSet(forcedNames));
	}

	public record Resolved(Map<String, OptionValue> chosen, String profile,
			Set<String> forcedNames) {
	}
}
