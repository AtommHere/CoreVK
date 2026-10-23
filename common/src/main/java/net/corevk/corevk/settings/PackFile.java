package net.corevk.corevk.settings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

public record PackFile(String name, boolean enabled, int shadowDistance, int renderScale,
		int shadowMapScale) {

	
	public static final String NONE = "none";

	private static final String NAME_KEY = "pack";
	private static final String ENABLED_KEY = "enabled";
	private static final String SHADOW_DISTANCE_KEY = "shadowdistance";
	private static final String RENDER_SCALE_KEY = "renderscale";
	private static final String SHADOW_MAP_SCALE_KEY = "shadowmapscale";

	
	public static final int MIN_SHADOW_DISTANCE = 0;
	public static final int MAX_SHADOW_DISTANCE = 32;
	public static final int DEFAULT_SHADOW_DISTANCE = MAX_SHADOW_DISTANCE;

	
	public static final int MIN_RENDER_SCALE = 25;
	public static final int MAX_RENDER_SCALE = 100;
	public static final int DEFAULT_RENDER_SCALE = MAX_RENDER_SCALE;

	
	public static final int MIN_SHADOW_MAP_SCALE = 25;
	public static final int MAX_SHADOW_MAP_SCALE = 100;
	public static final int DEFAULT_SHADOW_MAP_SCALE = MAX_SHADOW_MAP_SCALE;

	
	public static final PackFile EMPTY = new PackFile("", true, DEFAULT_SHADOW_DISTANCE,
			DEFAULT_RENDER_SCALE, DEFAULT_SHADOW_MAP_SCALE);

	public PackFile {
		name = name.trim();
		// Clamped here rather than where the file is read, so that a number typed by hand and a
		// number arriving from a screen are held to the same range: the value is served to the
		// culling as a distance and a negative one there means something else entirely. Both scales
		// under the same rule, their floors being where the picture stops being one.
		shadowDistance = Math.clamp(shadowDistance, MIN_SHADOW_DISTANCE, MAX_SHADOW_DISTANCE);
		renderScale = Math.clamp(renderScale, MIN_RENDER_SCALE, MAX_RENDER_SCALE);
		shadowMapScale = Math.clamp(shadowMapScale, MIN_SHADOW_MAP_SCALE, MAX_SHADOW_MAP_SCALE);
	}

	
	public static PackFile read(Path file) throws IOException {
		if (!Files.isRegularFile(file)) {
			return EMPTY;
		}

		String content = text(file);
		// The one line format, which is what every file written before this class looks like. Told
		// apart by having no key at all rather than by counting lines, so that a stray blank line or a
		// trailing newline does not change which format a file is read as.
		if (!content.contains("=")) {
			String bare = content.trim();

			return NONE.equalsIgnoreCase(bare)
					? new PackFile("", false, DEFAULT_SHADOW_DISTANCE, DEFAULT_RENDER_SCALE,
							DEFAULT_SHADOW_MAP_SCALE)
					: new PackFile(bare, true, DEFAULT_SHADOW_DISTANCE, DEFAULT_RENDER_SCALE,
							DEFAULT_SHADOW_MAP_SCALE);
		}

		String name = "";
		boolean enabled = true;
		int shadowDistance = DEFAULT_SHADOW_DISTANCE;
		int renderScale = DEFAULT_RENDER_SCALE;
		int shadowMapScale = DEFAULT_SHADOW_MAP_SCALE;
		for (String line : content.lines().toList()) {
			String trimmed = line.trim();
			if (trimmed.isEmpty() || trimmed.startsWith("#")) {
				continue;
			}

			int split = trimmed.indexOf('=');
			if (split < 0) {
				continue;
			}

			String key = trimmed.substring(0, split).trim().toLowerCase(Locale.ROOT);
			String value = trimmed.substring(split + 1).trim();
			switch (key) {
				case NAME_KEY -> name = value;
				// Anything that is not the word for true is false, which is how the pack format's own
				// booleans read and what keeps a typo from turning shaders on by accident.
				case ENABLED_KEY -> enabled = Boolean.parseBoolean(value);
				// A number that does not parse keeps the default rather than refusing the file, which
				// is how every other line here is read: this file is edited by hand, and one bad
				// character must not cost the player the pack they had chosen.
				case SHADOW_DISTANCE_KEY -> shadowDistance = number(value, shadowDistance);
				case RENDER_SCALE_KEY -> renderScale = number(value, renderScale);
				case SHADOW_MAP_SCALE_KEY -> shadowMapScale = number(value, shadowMapScale);
				default -> {
				}
			}
		}

		return new PackFile(name, enabled, shadowDistance, renderScale, shadowMapScale);
	}

	
	private static String text(Path file) throws IOException {
		String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

		return text.startsWith("\uFEFF") ? text.substring(1) : text;
	}

	private static int number(String value, int fallback) {
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	
	public static void write(Path file, PackFile chosen) throws IOException {
		Files.createDirectories(file.getParent());

		Path temporary = file.resolveSibling(file.getFileName() + ".part");
		Files.writeString(temporary,
				NAME_KEY + "=" + chosen.name() + "\n"
						+ ENABLED_KEY + "=" + chosen.enabled() + "\n"
						+ SHADOW_DISTANCE_KEY + "=" + chosen.shadowDistance() + "\n"
						+ RENDER_SCALE_KEY + "=" + chosen.renderScale() + "\n"
						+ SHADOW_MAP_SCALE_KEY + "=" + chosen.shadowMapScale() + "\n",
				StandardCharsets.UTF_8);
		try {
			try {
				Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING,
						StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			// A move that failed for any other reason would leave the .part beside the file for
			// ever, and nothing else ever looks at it.
			try {
				Files.deleteIfExists(temporary);
			} catch (IOException swallowed) {
				e.addSuppressed(swallowed);
			}
			throw e;
		}
	}

	
	public boolean wantsPack() {
		return this.enabled && !this.name.isEmpty();
	}

	
	public boolean namesNone() {
		return NONE.equalsIgnoreCase(this.name);
	}

	public PackFile withName(String name) {
		return new PackFile(name, this.enabled, this.shadowDistance, this.renderScale,
				this.shadowMapScale);
	}

	public PackFile withEnabled(boolean enabled) {
		return new PackFile(this.name, enabled, this.shadowDistance, this.renderScale,
				this.shadowMapScale);
	}

	
	public PackFile withChoice(String name, boolean enabled) {
		return new PackFile(name, enabled, this.shadowDistance, this.renderScale,
				this.shadowMapScale);
	}

	public PackFile withShadowDistance(int chunks) {
		return new PackFile(this.name, this.enabled, chunks, this.renderScale, this.shadowMapScale);
	}

	public PackFile withRenderScale(int percent) {
		return new PackFile(this.name, this.enabled, this.shadowDistance, percent,
				this.shadowMapScale);
	}

	public PackFile withShadowMapScale(int percent) {
		return new PackFile(this.name, this.enabled, this.shadowDistance, this.renderScale, percent);
	}
}
