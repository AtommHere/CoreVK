package net.corevk.corevk.pack.target;

import net.corevk.corevk.pack.model.TargetFormat;
import net.corevk.corevk.pack.model.TargetName;
import net.corevk.corevk.pack.model.TargetSize;
import net.corevk.corevk.pack.source.IncludeExpander;
import net.corevk.corevk.pack.source.ShaderProperties;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public final class TargetDirectives {

	private static final String FORMAT = "Format";
	private static final String CLEAR = "Clear";
	private static final String CLEAR_COLOR = "ClearColor";
	private static final String MIPMAP = "MipmapEnabled";

	private static final Colour OPAQUE_BLACK = new Colour(0.0F, 0.0F, 0.0F, 1.0F);
	private static final Colour TRANSPARENT_BLACK = new Colour(0.0F, 0.0F, 0.0F, 0.0F);
	private static final Colour OPAQUE_WHITE = new Colour(1.0F, 1.0F, 1.0F, 1.0F);

	private final Map<Integer, Setting> settings;
	private final Map<String, Set<Integer>> mipmapRequests;
	private final List<String> conflicts;
	private final List<String> notes;
	private final int formatsSeen;
	private final int formatsApplied;

	private TargetDirectives(Builder builder) {
		Map<Integer, Setting> copied = new TreeMap<>();
		builder.settings.forEach((index, setting) -> copied.put(index, setting.copy()));

		this.settings = Collections.unmodifiableMap(copied);

		Map<String, Set<Integer>> mipmaps = new TreeMap<>();
		builder.mipmapRequests.forEach((program, indices) -> {
			if (!indices.isEmpty()) {
				mipmaps.put(program, Collections.unmodifiableSet(new TreeSet<>(indices)));
			}
		});

		this.mipmapRequests = Collections.unmodifiableMap(mipmaps);
		this.conflicts = List.copyOf(builder.conflicts);
		this.notes = List.copyOf(builder.notes);
		this.formatsSeen = builder.formatsSeen.size();
		this.formatsApplied = builder.formatsApplied.size();
	}

	public record Colour(float r, float g, float b, float a) {
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private final Map<Integer, Setting> settings = new TreeMap<>();
		private final Map<String, Set<Integer>> mipmapRequests = new LinkedHashMap<>();
		private final Map<String, String> lastValue = new LinkedHashMap<>();
		private final Map<String, String> lastSource = new LinkedHashMap<>();
		private final Set<String> formatsSeen = new LinkedHashSet<>();
		private final Set<String> formatsApplied = new LinkedHashSet<>();
		private final List<String> conflicts = new ArrayList<>();
		private final List<String> notes = new ArrayList<>();

		private Builder() {
		}

		public Builder accept(String program, IncludeExpander.ExpandedUnit fragment) {
			List<String> lines = fragment.lines();

			for (int line = 0; line < lines.size(); line++) {
				Optional<ConstDirectives.Directive> read = ConstDirectives.readLine(lines.get(line));
				if (read.isEmpty()) {
					continue;
				}

				ConstDirectives.Directive directive = read.get();
				Optional<TargetName.Suffixed> split = TargetName.split(directive.name());
				if (split.isEmpty()) {
					continue;
				}

				apply(program, line, directive, split.get(), fragment.isLive(line));
			}

			return this;
		}

		public Builder accept(ShaderProperties properties, Map<String, String> defines) {
			Map<Integer, String> wanted = new TreeMap<>();

			properties.sizeBuffers(defines).forEach((buffer, value) -> {
				Optional<Integer> index = boxed(buffer);
				if (index.isEmpty()) {
					this.notes.add("size.buffer." + buffer + " names no target we know of, ignored");
					return;
				}

				boolean legacy = !buffer.equals(TargetName.canonical(index.get()));
				if (legacy || !wanted.containsKey(index.get())) {
					wanted.put(index.get(), value);
				}
			});

			wanted.forEach((index, value) -> {
				Optional<TargetSize> size = TargetSize.parse(value, defines);
				if (size.isEmpty()) {
					this.notes.add("size.buffer." + TargetName.canonical(index) + " reads '" + value
							+ "', which is not two numbers once the settings are applied; full size instead");
					return;
				}

				setting(index).size = size.get();
			});

			return this;
		}

		public TargetDirectives build() {
			return new TargetDirectives(this);
		}

		private void apply(String program, int line, ConstDirectives.Directive directive,
				TargetName.Suffixed split, boolean live) {
			String type = directive.type();
			String suffix = split.suffix();

			boolean known = switch (suffix) {
				case FORMAT -> type.equals("int");
				case CLEAR, MIPMAP -> type.equals("bool");
				case CLEAR_COLOR -> type.equals("vec4");
				default -> false;
			};

			if (!known) {
				return;
			}

			String where = program + ":" + (line + 1);

			if (suffix.equals(FORMAT)) {

				this.formatsSeen.add(directive.name() + "=" + directive.value());
				if (live) {
					this.formatsApplied.add(directive.name() + "=" + directive.value());
				}
			}

			if (!live) {
				return;
			}

			if (suffix.equals(MIPMAP)) {
				Set<Integer> asked = this.mipmapRequests.computeIfAbsent(bareName(program),
						_ -> new TreeSet<>());
				if (directive.value().equals("true")) {
					asked.add(split.index());
				} else if (directive.value().equals("false")) {
					asked.remove(split.index());
				}

				return;
			}

			noteConflict(directive, where);

			Setting setting = setting(split.index());
			switch (suffix) {
				case FORMAT -> {
					setting.format = TargetFormat.resolve(directive.value());
					setting.formatSource = where;
				}
				case CLEAR -> {
					if (directive.value().equals("true") || directive.value().equals("false")) {
						setting.clear = directive.value().equals("true");
					}
				}
				case CLEAR_COLOR -> parseColour(directive.value()).ifPresentOrElse(
						colour -> setting.clearColour = colour,
						() -> this.notes.add(directive.name() + " at " + where + " reads '"
								+ directive.value() + "', which is not a vec4 constructor, ignored"));
				default -> {
				}
			}
		}

		private void noteConflict(ConstDirectives.Directive directive, String where) {
			String previous = this.lastValue.put(directive.name(), directive.value());
			String source = this.lastSource.put(directive.name(), where);
			if (previous != null && !previous.equals(directive.value())) {
				this.conflicts.add(directive.name() + " is " + previous + " at " + source + " and "
						+ directive.value() + " at " + where);
			}
		}

		private Setting setting(int index) {
			return this.settings.computeIfAbsent(index, _ -> new Setting());
		}

		private static Optional<Integer> boxed(String name) {
			return TargetName.index(name).stream().boxed().findFirst();
		}
	}

	public Set<Integer> declared() {
		return this.settings.keySet();
	}

	public TargetFormat.Resolution format(int index) {
		Setting setting = this.settings.get(index);

		return setting == null || setting.format == null ? TargetFormat.defaultFormat() : setting.format;
	}

	public String formatSource(int index) {
		Setting setting = this.settings.get(index);

		return setting == null || setting.formatSource == null ? "default" : setting.formatSource;
	}

	public boolean clears(int index) {
		Setting setting = this.settings.get(index);

		return setting == null || setting.clear;
	}

	public Colour clearColour(int index) {
		Setting setting = this.settings.get(index);
		if (setting != null && setting.clearColour != null) {
			return setting.clearColour;
		}

		if (index == 0) {
			return OPAQUE_BLACK;
		}

		if (index == 1) {
			return OPAQUE_WHITE;
		}

		return format(index).alphaAdded() ? OPAQUE_BLACK : TRANSPARENT_BLACK;
	}

	public boolean declaresClearColour(int index) {
		Setting setting = this.settings.get(index);

		return setting != null && setting.clearColour != null;
	}

	public TargetSize size(int index) {
		Setting setting = this.settings.get(index);

		return setting == null || setting.size == null ? TargetSize.ofScreen() : setting.size;
	}

	public Map<String, Set<Integer>> mipmapRequests() {
		return this.mipmapRequests;
	}

	public Set<Integer> mipmapped() {
		Set<Integer> targets = new TreeSet<>();
		this.mipmapRequests.values().forEach(targets::addAll);

		return Collections.unmodifiableSet(targets);
	}

	public List<String> conflicts() {
		return this.conflicts;
	}

	public int formatsSeen() {
		return this.formatsSeen;
	}

	public int formatsApplied() {
		return this.formatsApplied;
	}

	public List<String> notes() {
		return this.notes;
	}

	static Optional<Colour> parseColour(String value) {
		String text = value.trim();
		if (!text.startsWith("vec4")) {
			return Optional.empty();
		}

		String arguments = text.substring("vec4".length()).trim();
		if (!arguments.startsWith("(") || !arguments.endsWith(")")) {
			return Optional.empty();
		}

		String[] parts = arguments.substring(1, arguments.length() - 1).split(",", 0);
		if (parts.length != 4) {
			return Optional.empty();
		}

		try {
			return Optional.of(new Colour(Float.parseFloat(parts[0].trim()),
					Float.parseFloat(parts[1].trim()), Float.parseFloat(parts[2].trim()),
					Float.parseFloat(parts[3].trim())));
		} catch (NumberFormatException e) {
			return Optional.empty();
		}
	}

	private static String bareName(String program) {
		String name = TargetName.bareName(program);
		int dot = name.lastIndexOf('.');

		return dot < 0 ? name : name.substring(0, dot);
	}

	private static final class Setting {

		private TargetFormat.Resolution format;
		private String formatSource;
		private boolean clear = true;
		private Colour clearColour;
		private TargetSize size;

		private Setting copy() {
			Setting copied = new Setting();
			copied.format = this.format;
			copied.formatSource = this.formatSource;
			copied.clear = this.clear;
			copied.clearColour = this.clearColour;
			copied.size = this.size;

			return copied;
		}
	}
}
