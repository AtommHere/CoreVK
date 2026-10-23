package net.corevk.corevk.pack.source;

import net.corevk.corevk.pack.option.EngineDefines;
import net.corevk.corevk.pack.option.OptionValue;
import net.corevk.corevk.pack.option.SettingSet;
import net.corevk.corevk.CoreVK;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

final class KeptPack {

	private static final boolean ENABLED = Boolean.parseBoolean(
			System.getProperty("corevk.keepPackOpen", "true"));

	private static final int MOST_ENTRIES = 20_000;

	private static volatile Held held;

	private KeptPack() {
	}

	static OpenedPack open(Path packPath, Map<String, OptionValue> chosen, String profile)
			throws IOException {
		if (!ENABLED) {
			CoreVK.logger().info("Pack opened afresh: kept openings are off, "
					+ "property=corevk.keepPackOpen");

			return OpenedPack.open(packPath, chosen, profile);
		}

		Key wanted = Key.of(packPath, chosen, profile);
		String print = fingerprint(packPath);
		Held standing = held;
		if (standing != null && print != null && wanted.equals(standing.key())
				&& print.equals(standing.print()) && atTheAskedScale(standing)) {
			ShaderPackSource source = standing.pack().source();
			CoreVK.logger().info("The pack was already open and is read again from that opening: "
					+ "{} files and {} flattened units kept, property=corevk.keepPackOpen",
					source.filesRead(), source.unitsFlattened());

			return standing.pack();
		}

		OpenedPack opened = OpenedPack.open(packPath, chosen, profile);
		CoreVK.logger().info("Pack opened afresh: {}, property=corevk.keepPackOpen",
				why(standing, print, wanted));
		held = print == null ? null : new Held(opened, wanted, print);
		drop(standing);

		return opened;
	}

	static void forget() {
		Held standing = held;
		held = null;
		drop(standing);
	}

	static boolean holds(OpenedPack pack) {
		Held standing = held;
		if (standing == null) {
			return false;
		}

		@SuppressWarnings("ReferenceEquality")
		boolean same = standing.pack() == pack;

		return same;
	}

	private static boolean atTheAskedScale(Held standing) {
		return standing.pack().settings().scale() == SettingSet.askedShadowMapScale();
	}

	private static String why(Held standing, String print, Key wanted) {
		if (standing == null) {
			return "none was held";
		}

		if (print == null) {
			return "this one is not fingerprinted, so it will not be held either";
		}

		if (!wanted.equals(standing.key())) {
			return "the pack, its settings or the machine's defines have moved";
		}

		if (!atTheAskedScale(standing)) {
			return "the shadow map scale has moved";
		}

		return "its files have moved on the disk";
	}

	private static void drop(Held standing) {
		if (standing == null) {
			return;
		}

		try {
			standing.pack().source().close();
		} catch (IOException | RuntimeException e) {

			CoreVK.logger().warn("CoreVK could not close the pack opening it was holding", e);
		}
	}

	private record Held(OpenedPack pack, Key key, String print) {
	}

	private static String fingerprint(Path packPath) {
		List<String> entries = new ArrayList<>();
		try (Stream<Path> walk = Files.walk(packPath)) {
			for (Path path : (Iterable<Path>) walk::iterator) {
				if (entries.size() >= MOST_ENTRIES) {
					return null;
				}

				BasicFileAttributes attributes;
				try {
					attributes = Files.readAttributes(path, BasicFileAttributes.class);
				} catch (IOException e) {
					entries.add(packPath.relativize(path) + " unreadable");

					continue;
				}

				if (attributes.isDirectory()) {
					entries.add(packPath.relativize(path) + " dir");

					continue;
				}

				entries.add(packPath.relativize(path) + " " + attributes.size() + " "
						+ attributes.lastModifiedTime().toMillis());
			}
		} catch (IOException | RuntimeException e) {
			return null;
		}

		Collections.sort(entries);
		MessageDigest digest = sha256();
		for (String entry : entries) {
			digest.update(entry.getBytes(StandardCharsets.UTF_8));
			digest.update((byte) 0);
		}

		return HexFormat.of().formatHex(digest.digest());
	}

	private static MessageDigest sha256() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is required of every Java runtime", e);
		}
	}

	private record Key(Path packPath, String settings, String profile, Map<String, String> defines) {

		static Key of(Path packPath, Map<String, OptionValue> chosen, String profile) {
			List<String> written = new ArrayList<>(chosen.size());
			chosen.forEach((name, value) -> written.add(name + " " + value.isBoolean()
					+ " " + Objects.toString(value.asText(), "")));
			Collections.sort(written);

			return new Key(packPath, String.join("\n", written), profile,
					Map.copyOf(EngineDefines.table(EngineDefines.machine())));
		}
	}
}
