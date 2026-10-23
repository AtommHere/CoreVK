package net.corevk.corevk.glsl;

import net.corevk.corevk.pack.option.EngineDefines;
import net.corevk.corevk.pack.model.AlphaTest;
import net.corevk.corevk.pack.model.ProgramStage;
import net.corevk.corevk.pack.source.IncludeExpander.ExpandedUnit;
import net.corevk.corevk.pack.texture.VolumeAtlas;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterOutputStream;

public final class TranslationCache {

	
	private static final String DIRECTORY_PROPERTY = "corevk.translationDir";

	private static final String FOLDER = "translations";
	private static final String SUFFIX = ".tr";
	private static final String PART_SUFFIX = ".part";

	
	private static final String EDITION_SEPARATOR = "_";

	
	private static final int DIGEST_BYTES = 32;

	
	private static final long MOST_BYTES = 64L * 1024L * 1024L;

	private static final long CEILING_BYTES = 256L * 1024L * 1024L;
	private static final long SWEEP_TARGET = CEILING_BYTES / 4L * 3L;

	private static final long SWEEP_BACKOFF_NANOS = 60_000_000_000L;

	private static final AtomicLong SERVED = new AtomicLong();
	private static final AtomicLong TRANSLATED = new AtomicLong();
	private static final AtomicLong BYTES = new AtomicLong();

	
	private static final AtomicReference<String> REFUSAL = new AtomicReference<>("");

	
	private static final Object LOCK = new Object();

	
	private static volatile Path directory;
	private static volatile String problem = "";

	
	private static volatile long nextSweepNanos;

	
	private static volatile boolean refused;

	static {
		// Inside the try and not beside it: Path.of refuses a name Windows will not have, and an
		// exception out of a static initialiser is an Error, which goes straight past every catch
		// that turns a bad pack into a report and takes the pack load down with it.
		try {
			String outside = System.getProperty(DIRECTORY_PROPERTY, "");
			if (!outside.isEmpty()) {
				open(Path.of(outside).resolve(FOLDER).resolve("outside-the-game"), "");
			}
		} catch (RuntimeException e) {
			problem = e.toString();
		}
	}

	private TranslationCache() {
	}

	
	public static void install(Path parent, String edition, String family) {
		open(parent.resolve(FOLDER).resolve(plain(edition)), plain(family));
	}

	
	private static void open(Path mine, String family) {
		synchronized (LOCK) {
			try {
				Files.createDirectories(mine);
				if (!family.isEmpty()) {
					touch(mine);
					dropOtherEditions(mine.getParent(), mine, family);
				}

				BYTES.set(total(scan(mine, true)));
				directory = mine;
				problem = "";
			} catch (IOException | RuntimeException e) {
				directory = null;
				problem = e.toString();
			}
		}
	}

	
	public static String problem() {
		return problem;
	}

	public static boolean installed() {
		return directory != null;
	}

	
	public static String takeRefusal() {
		return REFUSAL.getAndSet("");
	}

	
	private static void refuse(String what) {
		if (!refused) {
			refused = true;
			REFUSAL.set(what);
		}
	}

	public static long served() {
		return SERVED.get();
	}

	public static long translated() {
		return TRANSLATED.get();
	}

	
	public static void reset() {
		SERVED.set(0L);
		TRANSLATED.set(0L);
	}

	
	static ProgramTranslator.TranslatedProgram lookup(String key, VertexInputs inputs) {
		Path root = directory;
		if (key == null || root == null) {
			return null;
		}

		Path file = root.resolve(key + SUFFIX);
		byte[] raw;
		try {
			// Asked before the read and not after it: the digest sits behind the blob, so nothing
			// can answer for a file that is not already whole in the heap, and a file that grew
			// past what a translation is would be read before anything could refuse it.
			if (Files.size(file) > MOST_BYTES) {
				refuse("a stored translation is larger than any translation is");

				return null;
			}

			raw = Files.readAllBytes(file);
		} catch (IOException e) {
			// Absent is the ordinary case and unreadable the rare one. What follows either way is
			// the translation that would have happened anyway.
			return null;
		} catch (OutOfMemoryError e) {
			// The size was asked for above, so this is a heap that was already at its edge rather
			// than a file that lied about itself. It is still a miss and never a dead load.
			refuse("there was no room to read a stored translation");

			return null;
		}

		int length = raw.length - DIGEST_BYTES;
		if (length <= 0 || !answersForItself(raw, length)) {
			return null;
		}

		ProgramTranslator.TranslatedProgram program;
		try {
			byte[] blob = inflate(raw, length);
			program = TranslatedProgramCodec.read(blob, blob.length, inputs);
		} catch (IOException | RuntimeException e) {
			return null;
		} catch (OutOfMemoryError e) {
			// Not the damaged file: the digest above answers for the bytes on disk, so damage is
			// already a miss by the time the inflate begins. What the digest says nothing about is
			// what those bytes unpack to, which leaves a file written to inflate far past the size
			// it was refused on, or a heap that was already at its edge.
			refuse("a stored translation did not fit the heap once it was unpacked");

			return null;
		}

		touch(file);
		SERVED.incrementAndGet();

		return program;
	}

	
	static void store(String key, ProgramTranslator.TranslatedProgram program) {
		TRANSLATED.incrementAndGet();

		Path root = directory;
		if (key == null || root == null) {
			return;
		}

		byte[] raw;
		try {
			raw = deflate(TranslatedProgramCodec.write(program));
		} catch (IOException | RuntimeException e) {
			return;
		}

		Path file = root.resolve(key + SUFFIX);
		Path part = root.resolve(key + "-"
				+ Long.toHexString(Thread.currentThread().threadId()) + PART_SUFFIX);

		try {
			// The blob, then the digest that answers for it, then the move. A process killed
			// halfway through leaves a neighbour and never half a translation under a whole name.
			Files.write(part, raw);
			Files.write(part, sha256(raw), StandardOpenOption.APPEND);
			move(part, file);
			// Added whether or not this replaced a blob already under the same key, which happens
			// whenever a lookup refused one and the translation wrote over it. The count then runs
			// ahead of the directory until a sweep rescans and puts it right, which is what the
			// sweep does before it deletes anything.
			BYTES.addAndGet(raw.length + (long) DIGEST_BYTES);
		} catch (IOException e) {
			try {
				Files.deleteIfExists(part);
			} catch (IOException ignored) {
				// The next install collects it: that scan deletes the neighbours it comes across.
			}

			return;
		}

		if (BYTES.get() > CEILING_BYTES) {
			sweep(root);
		}
	}

	
	static String keyOf(Map<ProgramStage, ExpandedUnit> units,
			VertexInputs inputs, List<String> boundElements, AlphaTest alphaTest, boolean coverage,
			String program, Map<String, VolumeAtlas> volumes) {
		if (directory == null) {
			return null;
		}

		MessageDigest digest = sha256();
		feed(digest, TranslatedProgramCodec.FORMAT);
		// The switches of the translator itself, which are the one input that is not an argument:
		// the trig substitution and the shadow comparison both change what it emits and neither
		// says so in the text it was handed.
		feed(digest, GlslTranslator.emissionSwitches());
		// And the device's answer on the vendor extensions, on the stages it runs subgroup
		// operations in and on whether its driver is MoltenVK, which decide which branch of a pack
		// compiles and what a float packing call becomes: a card that has one of them translates
		// differently from a card that has not.
		feed(digest, VendorExtensions.key());

		// The whole table, in its own order, which is fixed by the code that builds it. It carries
		// the game version, the operating system, the driver's vendor and renderer, the mipmap
		// level and every other symbol the machine decides, and a define added to it later is in
		// the key from that day without a line here.
		for (Map.Entry<String, String> define
				: EngineDefines.table(EngineDefines.machine()).entrySet()) {
			feed(digest, define.getKey());
			feed(digest, define.getValue());
		}

		feed(digest, inputs.name());
		feed(digest, program);
		feed(digest, alphaTest.function().name());
		digest.update(intBytes(Float.floatToIntBits(alphaTest.reference())));
		digest.update(new byte[] {(byte) (coverage ? 1 : 0)});

		digest.update(intBytes(boundElements.size()));
		for (String element : boundElements) {
			feed(digest, element);
		}

		// IN THE MAP'S OWN ORDER, and not sorted, because the order is not decoration: the
		// translator walks this map to flatten the lookups and emits one helper body per volume in
		// the order it walked, so two maps holding the same entries in a different order are two
		// different texts. Sorting here would have given them one key.
		digest.update(intBytes(volumes.size()));
		for (Map.Entry<String, VolumeAtlas> volume : volumes.entrySet()) {
			feed(digest, volume.getKey());
			digest.update(intBytes(volume.getValue().width()));
			digest.update(intBytes(volume.getValue().height()));
			digest.update(intBytes(volume.getValue().depth()));
			// The helper's text differs between a volume that repeats and one that clamps.
			digest.update(new byte[] {(byte) (volume.getValue().clamp() ? 1 : 0)});
		}

		for (ProgramStage stage : ProgramStage.values()) {
			ExpandedUnit unit = units.get(stage);
			if (unit == null) {
				continue;
			}

			feed(digest, stage.name());
			feed(digest, unit.entry());
			feed(digest, unit.version());
			feedLines(digest, unit.lines());
			// The lines the preprocessor left live, which the translator reads on nearly every walk
			// it makes: two texts that differ only in which of their lines are dead translate
			// differently and would otherwise share a key.
			byte[] live = unit.live().toByteArray();
			digest.update(intBytes(live.length));
			digest.update(live);
		}

		return HexFormat.of().formatHex(digest.digest());
	}

	
	private static byte[] deflate(byte[] blob) throws IOException {
		ByteArrayOutputStream packed = new ByteArrayOutputStream();
		Deflater deflater = new Deflater(Deflater.BEST_SPEED);
		try (DeflaterOutputStream out = new DeflaterOutputStream(packed, deflater)) {
			out.write(blob);
		} finally {
			deflater.end();
		}

		return packed.toByteArray();
	}

	private static byte[] inflate(byte[] raw, int length) throws IOException {
		ByteArrayOutputStream blob = new ByteArrayOutputStream();
		Inflater inflater = new Inflater();
		try (InflaterOutputStream out = new InflaterOutputStream(blob, inflater)) {
			out.write(raw, 0, length);
		} finally {
			inflater.end();
		}

		return blob.toByteArray();
	}

	private static boolean answersForItself(byte[] raw, int length) {
		MessageDigest digest = sha256();
		digest.update(raw, 0, length);

		return Arrays.equals(digest.digest(), 0, DIGEST_BYTES, raw, length, raw.length);
	}

	private static void feed(MessageDigest digest, String text) {
		byte[] raw = text.getBytes(StandardCharsets.UTF_8);
		digest.update(intBytes(raw.length));
		digest.update(raw);
	}

	
	private static void feedLines(MessageDigest digest, List<String> lines) {
		int length = Math.max(0, lines.size() - 1);
		for (String line : lines) {
			length += utf8Length(line);
		}

		digest.update(intBytes(length));
		for (int at = 0; at < lines.size(); at++) {
			if (at > 0) {
				digest.update((byte) '\n');
			}

			digest.update(lines.get(at).getBytes(StandardCharsets.UTF_8));
		}
	}

	
	private static int utf8Length(String text) {
		int length = 0;
		for (int at = 0; at < text.length(); at++) {
			char c = text.charAt(at);
			if (c < 0x80) {
				length += 1;
			} else if (c < 0x800) {
				length += 2;
			} else if (Character.isHighSurrogate(c) && at + 1 < text.length()
					&& Character.isLowSurrogate(text.charAt(at + 1))) {
				length += 4;
				at++;
			} else if (Character.isSurrogate(c)) {
				length += 1;
			} else {
				length += 3;
			}
		}

		return length;
	}

	private static byte[] intBytes(int value) {
		return new byte[] {
				(byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8), (byte) value,
		};
	}

	private static byte[] sha256(byte[] raw) {
		return sha256().digest(raw);
	}

	private static MessageDigest sha256() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is required of every Java runtime", e);
		}
	}

	private static void move(Path part, Path file) throws IOException {
		try {
			Files.move(part, file, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(part, file, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	
	private static void touch(Path file) {
		try {
			Files.setLastModifiedTime(file, FileTime.from(Instant.now()));
		} catch (IOException ignored) {
			// A stamp that cannot be set costs a worse choice at the next sweep and nothing now.
		}
	}

	
	private static void dropOtherEditions(Path root, Path mine, String family) throws IOException {
		List<Path> entries;
		try (Stream<Path> found = Files.list(root)) {
			entries = found.toList();
		}

		String kept = mine.getFileName().toString().equals(family)
				? "" : newestSibling(entries, mine, family);

		for (Path entry : entries) {
			if (!entry.equals(mine) && !entry.getFileName().toString().equals(kept)) {
				dropTree(entry);
			}
		}
	}

	
	private static String newestSibling(List<Path> entries, Path mine, String family)
			throws IOException {
		String newest = "";
		long stamp = Long.MIN_VALUE;

		for (Path entry : entries) {
			String name = entry.getFileName().toString();
			if (entry.equals(mine) || !ofFamily(name, family)) {
				continue;
			}

			long when = Files.getLastModifiedTime(entry).toMillis();
			if (when > stamp) {
				stamp = when;
				newest = name;
			}
		}

		return newest;
	}

	
	private static boolean ofFamily(String name, String family) {
		return name.equals(family) || name.startsWith(family + EDITION_SEPARATOR);
	}

	
	private static String plain(String text) {
		return text.replaceAll("[^A-Za-z0-9._-]", "_");
	}

	private static void dropTree(Path entry) throws IOException {
		try (Stream<Path> tree = Files.walk(entry)) {
			for (Path found : tree.sorted(Comparator.reverseOrder()).toList()) {
				Files.deleteIfExists(found);
			}
		}
	}

	
	private static List<Blob> scan(Path root, boolean prunePartials) throws IOException {
		List<Blob> blobs = new ArrayList<>();

		try (Stream<Path> entries = Files.list(root)) {
			for (Path entry : entries.toList()) {
				if (entry.getFileName().toString().endsWith(PART_SUFFIX)) {
					if (prunePartials) {
						Files.deleteIfExists(entry);
					}
				} else {
					try {
						blobs.add(new Blob(entry, Files.getLastModifiedTime(entry).toMillis(),
								Files.size(entry)));
					} catch (IOException ignored) {
						// Gone, or momentarily unreadable. One blob uncounted, and the next sweep
						// counts it.
					}
				}
			}
		}

		blobs.sort(Comparator.comparingLong(Blob::stamp));

		return blobs;
	}

	private static long total(List<Blob> blobs) {
		long sum = 0L;
		for (Blob blob : blobs) {
			sum += blob.size();
		}

		return sum;
	}

	
	private static void sweep(Path root) {
		synchronized (LOCK) {
			if (System.nanoTime() < nextSweepNanos) {
				return;
			}

			long total = BYTES.get();
			try {
				List<Blob> blobs = scan(root, false);
				total = total(blobs);

				for (Blob blob : blobs) {
					if (total <= SWEEP_TARGET) {
						break;
					}

					Files.deleteIfExists(blob.path());
					total -= blob.size();
				}
			} catch (IOException e) {
				// The ceiling is a courtesy and a refusal here is not worth a load. What it must not
				// do is come straight back: a scan that throws leaves the count where it was, which
				// is over the ceiling, and every later write would then walk the directory again
				// under this lock for the rest of the session.
				nextSweepNanos = System.nanoTime() + SWEEP_BACKOFF_NANOS;
			} finally {
				BYTES.set(total);
			}
		}
	}

	
	private record Blob(Path path, long stamp, long size) {
	}
}
