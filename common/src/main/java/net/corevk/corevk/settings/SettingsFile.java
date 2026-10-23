package net.corevk.corevk.settings;

import net.corevk.corevk.pack.menu.MenuOption;
import net.corevk.corevk.pack.menu.MenuValues;
import net.corevk.corevk.pack.menu.PackMenu;
import net.corevk.corevk.pack.option.OptionValue;
import net.corevk.corevk.pack.load.PackLoader;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SettingsFile {

	private static final String SUFFIX = ".txt";

	private static final String LEGACY_DIRECTORY = "corevk";
	private static final String LEGACY_SETTINGS = "settings";

	private static final String MIGRATED = ".migrated";

	public static final String PROFILE_KEY = "profile";

	private static final List<String> HEADER = List.of(
			"# Written by CoreVK's settings screen and by CoreVK, one NAME=value per line.",
			"# Only what differs from the pack's own defaults.",
			"# corevk/options.txt is a different file, it is never written here, and it wins.");

	private static final byte[] BYTE_ORDER_MARK = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

	private SettingsFile() {
	}

	public static Path of(Path gameDirectory, String packFileName) {
		return PackLoader.directory(gameDirectory).resolve(packFileName + SUFFIX);
	}

	public static Path legacy(Path gameDirectory, String packFileName) {
		return gameDirectory.resolve(LEGACY_DIRECTORY).resolve(LEGACY_SETTINGS)
				.resolve(packFileName + SUFFIX);
	}

	public static Carried migrate(Path gameDirectory, String packFileName, PackMenu menu)
			throws IOException {
		Path shared = of(gameDirectory, packFileName);
		Path legacy = legacy(gameDirectory, packFileName);
		if (Files.isRegularFile(shared) || !Files.isRegularFile(legacy)) {
			return new Carried(Carry.NOTHING, "", null);
		}

		Map<String, String> values = new LinkedHashMap<>();
		String profile = "";
		for (Map.Entry<String, String> entry : lines(legacy, StandardCharsets.UTF_8).entrySet()) {
			if (entry.getKey().equals(PROFILE_KEY)) {
				profile = entry.getValue();
			} else {
				values.put(entry.getKey(), entry.getValue());
			}
		}

		if (!profile.isEmpty() && !menu.profileNames().contains(profile)) {
			return new Carried(Carry.UNKNOWN_PROFILE, profile, legacy);
		}

		Map<String, String> merged = new LinkedHashMap<>(menu.profile(profile));
		merged.putAll(values);

		Map<String, String> written = new LinkedHashMap<>();
		merged.forEach((name, value) -> {

			String fallback = menu.option(name).map(MenuOption::defaultValue).orElse(null);
			if (fallback == null
					|| !OptionValue.parse(fallback).asText().equals(OptionValue.parse(value).asText())) {
				written.put(name, MenuValues.written(menu, name, value));
			}
		});

		try {
			write(shared, new Stored(written));
		} catch (IOException e) {
			return new Carried(Carry.FAILED, profile, shared);
		}

		try {
			Files.move(legacy, legacy.resolveSibling(legacy.getFileName() + MIGRATED),
					StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			Files.deleteIfExists(shared);

			return new Carried(Carry.FAILED, profile, legacy);
		}

		return new Carried(Carry.MOVED, profile, null);
	}

	public record Carried(Carry carry, String profile, Path file) {
	}

	public enum Carry {

		NOTHING,

		MOVED,

		UNKNOWN_PROFILE,

		FAILED
	}

	public static Stored read(Path file) throws IOException {
		Map<String, String> values = new LinkedHashMap<>(lines(file, StandardCharsets.ISO_8859_1));
		values.remove(PROFILE_KEY);

		return new Stored(values);
	}

	private static Map<String, String> lines(Path file, Charset charset) throws IOException {
		if (!Files.isRegularFile(file)) {
			return Map.of();
		}

		byte[] bytes = Files.readAllBytes(file);
		int start = bytes.length >= BYTE_ORDER_MARK.length
				&& Arrays.equals(bytes, 0, BYTE_ORDER_MARK.length, BYTE_ORDER_MARK, 0,
						BYTE_ORDER_MARK.length)
				? BYTE_ORDER_MARK.length
				: 0;
		String text = new String(bytes, start, bytes.length - start, charset);

		Map<String, String> values = new LinkedHashMap<>();
		for (String line : text.lines().toList()) {
			String trimmed = line.trim();
			int equals = trimmed.indexOf('=');
			if (trimmed.isEmpty() || trimmed.startsWith("#") || equals < 1) {
				continue;
			}

			values.put(trimmed.substring(0, equals).trim(), trimmed.substring(equals + 1).trim());
		}

		return values;
	}

	public static void write(Path file, Stored stored) throws IOException {
		Path directory = file.getParent();
		if (directory != null) {
			Files.createDirectories(directory);
		}

		List<String> lines = new ArrayList<>(HEADER);
		stored.values().forEach((name, value) -> lines.add(name + "=" + value));

		Path temporary = file.resolveSibling(file.getFileName() + ".part");
		Files.write(temporary,
				String.join("\n", lines).concat("\n").getBytes(StandardCharsets.ISO_8859_1));
		try {
			Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING,
					StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	public record Stored(Map<String, String> values) {

		public Stored {

			values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
		}

		public static Stored empty() {
			return new Stored(Map.of());
		}
	}
}
