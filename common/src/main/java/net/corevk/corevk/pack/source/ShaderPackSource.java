package net.corevk.corevk.pack.source;

import net.corevk.corevk.pack.option.OptionIndex;
import net.corevk.corevk.pack.option.SettingSet;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public final class ShaderPackSource implements AutoCloseable {

	private static final Set<String> SOURCE_EXTENSIONS =
			Set.of("glsl", "fsh", "vsh", "gsh", "csh", "tcs", "tes", "inc", "settings");

	private static final String SHADERS_DIRECTORY = "shaders";

	private static final int SHADERS_SEARCH_DEPTH = 3;

	private static final long MAX_FILE_BYTES = 8L * 1024 * 1024;

	private static final int BINARY_PROBE = 4096;

	private static final AtomicInteger OPENINGS = new AtomicInteger();

	private final String packName;
	private final Path shadersRoot;
	private final FileSystem ownedFileSystem;

	private final Map<String, Map<String, Path>> listingsByDirectory = new HashMap<>();

	private final Map<String, List<String>> linesByFile = new HashMap<>();

	private final Map<Expansion, IncludeExpander.ExpandedUnit> expandedUnits = new HashMap<>();

	private final Map<Object, Object> derived = new HashMap<>();

	private int caseInsensitiveHits;

	private ShaderPackSource(String packName, Path shadersRoot, FileSystem ownedFileSystem) {
		this.packName = packName;
		this.shadersRoot = shadersRoot;
		this.ownedFileSystem = ownedFileSystem;
	}

	public static String nameOf(Path packPath) {
		Path name = packPath.getFileName();
		if (name == null) {
			return packPath.toString();
		}

		String text = name.toString();
		if (Files.isDirectory(packPath) || !text.toLowerCase(Locale.ROOT).endsWith(".zip")) {
			return text;
		}

		return text.substring(0, text.length() - 4);
	}

	public static int openings() {
		return OPENINGS.get();
	}

	public interface Derivation<T> {
		T read() throws IOException;
	}

	@SuppressWarnings("unchecked")
	public <T> T derived(Object key, Derivation<T> compute) throws IOException {
		Object known = this.derived.get(key);
		if (known == null) {
			known = compute.read();
			this.derived.put(key, known);
		}

		return (T) known;
	}

	public OptionIndex options() throws IOException {
		return derived(OptionIndex.class, () -> {
			OptionIndex.Reader reader = new OptionIndex.Reader();
			for (Path file : sourceFiles()) {
				reader.read(rel(file), readLines(file));
			}

			return reader.index();
		});
	}

	public static ShaderPackSource open(Path packPath) throws IOException {
		OPENINGS.incrementAndGet();
		if (Files.isDirectory(packPath)) {
			return new ShaderPackSource(nameOf(packPath), findShadersRoot(packPath), null);
		}

		String fileName = packPath.getFileName() == null ? "" : packPath.getFileName().toString();
		if (!fileName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
			throw new IOException("A shader pack is either a directory or a .zip, and " + fileName + " is neither");
		}

		FileSystem zip = FileSystems.newFileSystem(packPath);
		try {
			return new ShaderPackSource(nameOf(packPath), findShadersRoot(zip.getPath("/")), zip);
		} catch (IOException | RuntimeException e) {
			zip.close();
			throw e;
		}
	}

	private static Path findShadersRoot(Path root) throws IOException {
		Path direct = root.resolve(SHADERS_DIRECTORY);
		if (Files.isDirectory(direct)) {
			return direct;
		}

		try (Stream<Path> tree = Files.walk(root, SHADERS_SEARCH_DEPTH)) {
			return tree.filter(Files::isDirectory)
					.filter(path -> path.getFileName() != null
							&& path.getFileName().toString().equals(SHADERS_DIRECTORY))
					.min(Comparator.comparing(Path::toString))
					.orElseThrow(() -> new IOException("No shaders directory in this pack"));
		}
	}

	public String packName() {
		return this.packName;
	}

	public boolean isZip() {
		return this.ownedFileSystem != null;
	}

	public int caseInsensitiveHits() {
		return this.caseInsensitiveHits;
	}

	public String rel(Path file) {
		return this.shadersRoot.relativize(file).toString().replace('\\', '/');
	}

	public List<Path> sourceFiles() throws IOException {
		try (Stream<Path> tree = Files.walk(this.shadersRoot)) {
			List<Path> files = new ArrayList<>(tree.filter(Files::isRegularFile)
					.filter(path -> SOURCE_EXTENSIONS.contains(extensionOf(path)))
					.toList());
			files.sort(Comparator.comparing(this::rel));

			return List.copyOf(files);
		}
	}

	public List<Path> otherFiles() throws IOException {
		Set<Path> sources = Set.copyOf(sourceFiles());

		try (Stream<Path> tree = Files.walk(this.shadersRoot)) {
			List<Path> files = new ArrayList<>(tree.filter(Files::isRegularFile)
					.filter(path -> !sources.contains(path))
					.filter(this::withinCeiling)
					.toList());
			files.sort(Comparator.comparing(this::rel));

			return List.copyOf(files);
		}
	}

	public String searchableText(Path file) throws IOException {
		try (InputStream in = Files.newInputStream(file)) {
			byte[] head = in.readNBytes(BINARY_PROBE);
			for (byte b : head) {
				if (b == 0) {
					return "";
				}
			}

			byte[] rest = in.readAllBytes();
			StringBuilder text = new StringBuilder(head.length + rest.length);
			text.append(new String(head, StandardCharsets.ISO_8859_1));
			text.append(new String(rest, StandardCharsets.ISO_8859_1));

			return text.toString();
		}
	}

	private boolean withinCeiling(Path file) {
		try {
			return Files.size(file) <= MAX_FILE_BYTES;
		} catch (IOException e) {

			return false;
		}
	}

	private static String extensionOf(Path path) {
		Path name = path.getFileName();
		if (name == null) {
			return "";
		}

		String text = name.toString();
		int dot = text.lastIndexOf('.');

		return dot < 0 ? "" : text.substring(dot + 1).toLowerCase(Locale.ROOT);
	}

	public List<String> readLines(Path file) throws IOException {
		String relative = rel(file);
		List<String> known = this.linesByFile.get(relative);
		if (known != null) {
			return known;
		}

		long size = Files.size(file);
		if (size > MAX_FILE_BYTES) {
			throw new IOException(relative + " is " + size + " bytes, past the " + MAX_FILE_BYTES
					+ " a shader source is allowed");
		}

		CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
				.onMalformedInput(CodingErrorAction.REPLACE)
				.onUnmappableCharacter(CodingErrorAction.REPLACE);
		CharBuffer decoded = decoder.decode(ByteBuffer.wrap(Files.readAllBytes(file)));

		String text = decoded.toString();
		if (!text.isEmpty() && text.charAt(0) == 0xFEFF) {
			text = text.substring(1);
		}

		List<String> lines = List.of(text.split("\r\n|\n|\r", -1));
		this.linesByFile.put(relative, lines);

		return lines;
	}

	Optional<IncludeExpander.ExpandedUnit> expandedUnit(SettingSet settings, String entry) {
		return Optional.ofNullable(this.expandedUnits.get(new Expansion(settings, entry)));
	}

	void rememberUnit(SettingSet settings, String entry, IncludeExpander.ExpandedUnit unit) {
		this.expandedUnits.put(new Expansion(settings, entry), unit);
	}

	private record Expansion(SettingSet settings, String entry) {
	}

	public Optional<Path> resolveInsideShaders(String spec) {
		return resolveAgainst(this.shadersRoot, withoutLeadingSlash(spec));
	}

	private static String withoutLeadingSlash(String spec) {
		return spec.replaceAll("^/+", "");
	}

	public Optional<Path> resolveRelativeTo(Path fromFile, String spec) {
		Path parent = fromFile.getParent();

		return parent == null ? Optional.empty() : resolveAgainst(parent, spec);
	}

	private Optional<Path> resolveAgainst(Path base, String spec) {
		Optional<Path> target = confine(base, spec);
		if (target.isEmpty()) {
			return Optional.empty();
		}

		if (Files.isRegularFile(target.get())) {
			return target;
		}

		return resolveIgnoringCase(target.get());
	}

	private Optional<Path> confine(Path base, String spec) {
		Path target;
		try {
			target = base.resolve(spec).normalize();
		} catch (RuntimeException e) {
			return Optional.empty();
		}

		return target.startsWith(this.shadersRoot) ? Optional.of(target) : Optional.empty();
	}

	public boolean insidePack(String relative) {
		return confine(this.shadersRoot, withoutLeadingSlash(relative)).isPresent();
	}

	private Optional<Path> resolveIgnoringCase(Path target) {
		Path parent = target.getParent();
		Path name = target.getFileName();
		if (parent == null || name == null || !Files.isDirectory(parent)) {
			return Optional.empty();
		}

		Map<String, Path> listing = this.listingsByDirectory.computeIfAbsent(rel(parent), _ -> listing(parent));
		Path found = listing.get(name.toString().toLowerCase(Locale.ROOT));
		if (found == null) {
			return Optional.empty();
		}

		this.caseInsensitiveHits++;

		return Optional.of(found);
	}

	private static Map<String, Path> listing(Path directory) {
		Map<String, Path> byLowerName = new HashMap<>();
		try (Stream<Path> entries = Files.list(directory)) {
			entries.filter(Files::isRegularFile).forEach(entry -> {
				Path name = entry.getFileName();
				if (name != null) {
					byLowerName.putIfAbsent(name.toString().toLowerCase(Locale.ROOT), entry);
				}
			});
		} catch (IOException e) {
			return Map.of();
		}

		return byLowerName;
	}

	public List<String> topLevelDirectories() throws IOException {
		try (Stream<Path> entries = Files.list(this.shadersRoot)) {
			return entries.filter(Files::isDirectory)
					.map(this::rel)
					.sorted()
					.toList();
		}
	}

	public Optional<Path> file(String relative) {
		return resolveInsideShaders(relative);
	}

	public long size(Path file) throws IOException {
		return Files.size(file);
	}

	public byte[] head(Path file, long wanted) throws IOException {
		if (wanted < 0 || wanted > Integer.MAX_VALUE) {
			throw new IOException(rel(file) + " is asked for " + wanted
					+ " bytes, which is not a length anything here can hold");
		}

		try (InputStream in = Files.newInputStream(file)) {
			byte[] read = in.readNBytes((int) wanted);
			if (read.length < wanted) {
				throw new IOException(rel(file) + " holds " + read.length
						+ " bytes where the declaration asks for " + wanted);
			}

			return read;
		}
	}

	public byte[] bytes(Path file) throws IOException {
		long size = Files.size(file);
		if (size > MAX_FILE_BYTES) {
			throw new IOException(rel(file) + " is " + size + " bytes, past the " + MAX_FILE_BYTES
					+ " a pack file is allowed");
		}

		return Files.readAllBytes(file);
	}

	void forgetDerived() {
		this.derived.clear();
	}

	int filesRead() {
		return this.linesByFile.size();
	}

	int unitsFlattened() {
		return this.expandedUnits.size();
	}

	@Override
	public void close() throws IOException {
		this.linesByFile.clear();
		this.expandedUnits.clear();
		this.derived.clear();
		if (this.ownedFileSystem != null) {
			this.ownedFileSystem.close();
		}
	}
}
