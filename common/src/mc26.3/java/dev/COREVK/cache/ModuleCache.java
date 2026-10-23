package net.corevk.corevk.cache;

import net.corevk.corevk.glsl.LocalZeroes;
import net.corevk.corevk.render.PackChain;
import net.corevk.corevk.render.PackNames;
import net.corevk.corevk.render.RawLocals;
import net.corevk.corevk.render.SamplerReach;
import net.corevk.corevk.render.ShaderDebugInfo;
import net.corevk.corevk.CoreVK;

import org.jspecify.annotations.Nullable;
import org.lwjgl.Version;
import org.lwjgl.system.MemoryUtil;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

public final class ModuleCache {

	
	private static final boolean ENABLED = Boolean.parseBoolean(
			System.getProperty("corevk.moduleCache", "true"));

	
	private static final int MAGIC = 0x07230203;

	
	private static final int SHORTEST = 20;

	
	private static final int DIGEST_BYTES = 32;

	
	private static final long MOST_BYTES = 64L * 1024L * 1024L;

	
	public static final int MIN_CEILING_MIB = 128;
	public static final int MAX_CEILING_MIB = 2048;
	public static final int DEFAULT_CEILING_MIB = 512;
	public static final int CEILING_STEP_MIB = 128;

	private static final String CEILING_FILE = "module-cache-ceiling.txt";

	private static volatile int ceilingMib = DEFAULT_CEILING_MIB;
	private static volatile boolean ceilingLoaded;

	
	private static final long SWEEP_BACKOFF_NANOS = 60_000_000_000L;

	
	private static final String FORMAT = "corevk-module-26.3-1";

	private static final String FOLDER = "modules";

	
	private static final String EDITION_SEPARATOR = "+";

	
	private static final int NAMED_MISSES = 12;

	
	private static final List<String> BUILT_NAMES = new ArrayList<>();
	private static final String SUFFIX = ".mod";
	private static final String PART_SUFFIX = ".part";

	
	private static final long QUIET_NANOS = 2_000_000_000L;

	private static final AtomicLong SERVED = new AtomicLong();
	private static final AtomicLong COMPILED = new AtomicLong();
	private static final AtomicLong SERVED_SINCE_LAUNCH = new AtomicLong();
	private static final AtomicLong COMPILED_SINCE_LAUNCH = new AtomicLong();
	private static final AtomicLong BYTES = new AtomicLong();

	
	private static final Object LOCK = new Object();

	private static volatile @Nullable Path directory;
	private static volatile boolean unavailable;
	private static volatile long lastUnitNanos;
	private static volatile long nextSweepNanos;
	private static volatile boolean saidAboutReading;
	private static volatile boolean saidAboutStoring;
	private static volatile boolean saidAboutWriting;

	private ModuleCache() {
	}

	
	public static int ceilingMib() {
		if (!ceilingLoaded) {
			loadCeiling();
		}

		return ceilingMib;
	}

	
	public static void setCeilingMib(int mib) {
		int asked = snapCeiling(mib);
		writeCeiling(asked);
		ceilingMib = asked;
		ceilingLoaded = true;
		nextSweepNanos = 0L;
		Path root = directory;
		if (root != null && BYTES.get() > bytesOf(asked)) {
			sweep(root);
		}
	}

	private static void loadCeiling() {
		synchronized (LOCK) {
			if (ceilingLoaded) {
				return;
			}

			ceilingMib = readCeilingFile();
			ceilingLoaded = true;
		}
	}

	private static int readCeilingFile() {
		Path file;
		try {
			file = CoreVK.platform().gameDirectory().resolve(CoreVK.MOD_ID).resolve(CEILING_FILE);
		} catch (RuntimeException e) {
			return DEFAULT_CEILING_MIB;
		}

		try {
			if (!Files.isRegularFile(file)) {
				return DEFAULT_CEILING_MIB;
			}

			String text = Files.readString(file, StandardCharsets.UTF_8).trim();

			return snapCeiling(Integer.parseInt(text));
		} catch (NumberFormatException e) {
			CoreVK.logger().warn("corevk/{} is not a size in mebibytes, so the default {} is used",
					CEILING_FILE, DEFAULT_CEILING_MIB);

			return DEFAULT_CEILING_MIB;
		} catch (IOException | RuntimeException e) {
			return DEFAULT_CEILING_MIB;
		}
	}

	private static void writeCeiling(int mib) {
		try {
			Path file = CoreVK.platform().gameDirectory().resolve(CoreVK.MOD_ID)
					.resolve(CEILING_FILE);
			Files.createDirectories(file.getParent());
			Files.writeString(file, mib + "\n", StandardCharsets.UTF_8);
		} catch (IOException | RuntimeException e) {
			CoreVK.logger().error("CoreVK could not write the module cache ceiling to corevk/{}",
					CEILING_FILE, e);
		}
	}

	private static int snapCeiling(int asked) {
		int clamped = Math.clamp(asked, MIN_CEILING_MIB, MAX_CEILING_MIB);
		int steps = (clamped - MIN_CEILING_MIB + CEILING_STEP_MIB / 2) / CEILING_STEP_MIB;

		return Math.clamp(MIN_CEILING_MIB + steps * CEILING_STEP_MIB, MIN_CEILING_MIB,
				MAX_CEILING_MIB);
	}

	private static long bytesOf(int mib) {
		return (long) mib * 1024L * 1024L;
	}

	private static long ceilingBytes() {
		return bytesOf(ceilingMib());
	}

	private static long sweepTarget() {
		return ceilingBytes() / 4L * 3L;
	}

	
	public static @Nullable String keyOf(String source, String stage, String defines) {
		if (directory() == null || source.contains("#include")) {
			return null;
		}

		MessageDigest digest = sha256();

		feed(digest, FORMAT);
		feed(digest, CoreVK.cacheVersion());

		// The commit, on a development build and only there. The folder already keeps two such
		// builds apart, and this is the same claim made where the class makes every other one: the
		// key IS the input, and on a development build the version alone does not name the
		// translator that produced the text. A release feeds nothing extra, which is what leaves
		// every key a player already holds exactly where it was.
		String build = CoreVK.buildIdentity();
		if (!build.isEmpty()) {
			feed(digest, build);
		}

		feed(digest, CoreVK.platform().minecraftVersion());
		feed(digest, CoreVK.platform().loaderName());
		feed(digest, CoreVK.platform().loaderVersion());
		feed(digest, Version.getVersion());
		// The two switches that change the bytes a compile produces without changing its text: each
		// state keeps its own set of blobs, and a reading taken under one never draws another's.
		feed(digest, RawLocals.cacheWord());
		feed(digest, LocalZeroes.VERSION);
		feed(digest, ShaderDebugInfo.cacheWord());
		feed(digest, PackNames.cacheWord());
		feed(digest, stage);
		feed(digest, defines);
		feed(digest, source);

		return HexFormat.of().formatHex(digest.digest());
	}

	
	public static @Nullable ByteBuffer lookup(@Nullable String key) {
		Path root = directory();
		if (key == null || root == null) {
			return null;
		}

		Path file = root.resolve(key + SUFFIX);
		byte[] raw;
		try {
			if (Files.size(file) > MOST_BYTES) {
				sayAboutReading("a stored module is larger than any module is");

				return null;
			}

			raw = Files.readAllBytes(file);
		} catch (IOException e) {
			// Absent is the ordinary case and unreadable the rare one, and neither is worth a word:
			// what follows either way is the compile that would have happened anyway.
			return null;
		} catch (OutOfMemoryError e) {
			// The size was asked for above, so this is a heap that was already at its edge rather
			// than a file that lied about itself. It is still a miss and never a dead load.
			sayAboutReading("there was no room to read a stored module");

			return null;
		}

		int length = raw.length - DIGEST_BYTES;
		if (length <= 0 || !answersForItself(raw, length)) {
			sayAboutReading("a stored module did not answer for its own bytes");

			return null;
		}

		ByteBuffer spirv = rebuild(raw, length);
		if (spirv == null) {
			return null;
		}

		touch(file);
		SERVED.incrementAndGet();
		SERVED_SINCE_LAUNCH.incrementAndGet();
		lastUnitNanos = System.nanoTime();

		return spirv;
	}

	
	private static boolean answersForItself(byte[] raw, int length) {
		MessageDigest digest = sha256();
		digest.update(raw, 0, length);

		return Arrays.equals(digest.digest(), 0, DIGEST_BYTES, raw, length, raw.length);
	}

	
	private static @Nullable ByteBuffer rebuild(byte[] raw, int length) {
		ByteBuffer spirv = null;
		try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(raw, 0, length))) {
			int size = in.readInt();
			if (size < SHORTEST || size % 4 != 0 || size > length) {
				throw new IOException("a stored module claims " + size + " bytes of SPIR-V");
			}

			byte[] words = new byte[size];
			in.readFully(words);
			if (ByteBuffer.wrap(words).order(ByteOrder.nativeOrder()).getInt(0) != MAGIC) {
				throw new IOException("a stored module does not open on the SPIR-V magic word");
			}

			spirv = MemoryUtil.memAlloc(size);
			spirv.put(words);
			spirv.flip();

			return spirv;
		} catch (IOException | RuntimeException | OutOfMemoryError e) {
			// The Error is in the list on purpose. Everything else in here is a miss, and a length
			// this could not allocate for would otherwise be the one shape of damaged file that
			// takes the pack load down instead.
			if (spirv != null) {
				MemoryUtil.memFree(spirv);
			}

			sayAboutReading("a stored module could not be read back (" + e + ")");

			return null;
		}
	}

	
	public static void building(String filename) {
		COMPILED.incrementAndGet();
		COMPILED_SINCE_LAUNCH.incrementAndGet();
		lastUnitNanos = System.nanoTime();
		synchronized (BUILT_NAMES) {
			if (BUILT_NAMES.size() < NAMED_MISSES) {
				BUILT_NAMES.add(filename);
			}
		}
	}

	
	public static void store(@Nullable String key, ByteBuffer spirv) {
		Path root = directory();
		if (key == null || root == null) {
			return;
		}

		byte[] raw;
		try {
			raw = describe(spirv);
		} catch (IOException | RuntimeException e) {
			sayAboutStoring("a module could not be written down (" + e + ")");

			return;
		}

		Path file = root.resolve(key + SUFFIX);
		Path part = root.resolve(key + "-"
				+ Long.toHexString(Thread.currentThread().threadId()) + PART_SUFFIX);

		try {
			// The module, then the digest that answers for it. Behind rather than in front, so
			// nothing inside has to move to make room for it.
			Files.write(part, raw);
			Files.write(part, sha256(raw), StandardOpenOption.APPEND);
			move(part, file);
			BYTES.addAndGet(raw.length + (long) DIGEST_BYTES);
		} catch (IOException e) {
			sayAboutWriting("a module could not be stored", e);

			try {
				Files.deleteIfExists(part);
			} catch (IOException ignored) {
				// The next sweep collects it: every scan deletes the neighbours it comes across.
			}

			return;
		}

		if (BYTES.get() > ceilingBytes()) {
			sweep(root);
		}
	}

	
	private static byte[] describe(ByteBuffer spirv) throws IOException {
		// A view of its own, so the caller's position and limit are left where they were.
		ByteBuffer view = spirv.duplicate();
		byte[] words = new byte[view.remaining()];
		view.get(words);
		ByteBuffer raw = ByteBuffer.allocate(Integer.BYTES + words.length);
		raw.putInt(words.length).put(words);

		return raw.array();
	}

	
	public static void say() {
		if (SERVED.get() == 0L && COMPILED.get() == 0L) {
			return;
		}

		if (System.nanoTime() - lastUnitNanos < QUIET_NANOS) {
			return;
		}

		long hits = SERVED.getAndSet(0L);
		long misses = COMPILED.getAndSet(0L);
		List<String> named;
		synchronized (BUILT_NAMES) {
			named = List.copyOf(BUILT_NAMES);
			BUILT_NAMES.clear();
		}

		if (!ENABLED) {
			CoreVK.logger().info("Module cache off, so all {} units of this load were compiled ({} "
					+ "since this launch)", misses, COMPILED_SINCE_LAUNCH.get());
			RawLocals.say(misses);
			PackNames.say(misses);
			SamplerReach.say(misses);

			return;
		}

		Path root = directory;
		CoreVK.logger().info("Module cache: {} units served, {} built by the compiler, {} and {} "
						+ "since this launch, {} MB in {}",
				hits, misses, SERVED_SINCE_LAUNCH.get(), COMPILED_SINCE_LAUNCH.get(),
				megabytes(BYTES.get()), root == null ? "nowhere" : root);
		// Said only where a load rebuilt something a store already held units of: a cold first
		// load builds everything for a reason nobody needs told, a warm one rebuilding a handful
		// has a reason worth finding, and the names are where the search starts.
		if (misses > 0L && hits > 0L) {
			CoreVK.logger().info("The {} built this load {}: {}", misses,
					misses > named.size() ? "begin with" : "are", String.join(", ", named));
		}

		// At the same quiet moment and about the same compiles: what the passes did to the modules
		// this line counts as built.
		RawLocals.say(misses);
		PackNames.say(misses);
		SamplerReach.say(misses);
	}

	
	private static @Nullable Path directory() {
		if (!ENABLED || unavailable) {
			return null;
		}

		Path known = directory;
		if (known != null) {
			return known;
		}

		synchronized (LOCK) {
			if (directory == null && !unavailable) {
				open();
			}

			return directory;
		}
	}

	
	private static void open() {
		try {
			Path root = CoreVK.platform().gameDirectory().resolve(CoreVK.MOD_ID).resolve(FOLDER);
			Path mine = root.resolve(CoreVK.cacheEdition());
			Files.createDirectories(mine);
			touch(mine);
			dropOtherEditions(root, mine, CoreVK.cacheEditionFamily());
			BYTES.set(total(scan(mine, true)));
			directory = mine;
		} catch (IOException | RuntimeException e) {
			unavailable = true;
			CoreVK.logger().warn("No module cache this run, so every shader is compiled: {}",
					e.toString());
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

	private static void dropTree(Path entry) throws IOException {
		try (Stream<Path> tree = Files.walk(entry)) {
			for (Path found : tree.sorted(Comparator.reverseOrder()).toList()) {
				Files.deleteIfExists(found);
			}
		}
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

	
	private static void feed(MessageDigest digest, String text) {
		byte[] raw = text.getBytes(StandardCharsets.UTF_8);
		digest.update(new byte[] {
				(byte) (raw.length >>> 24), (byte) (raw.length >>> 16),
				(byte) (raw.length >>> 8), (byte) raw.length,
		});
		digest.update(raw);
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
			// Deliberately silent, and deliberately not a miss: the blob itself is fine either way.
		}
	}

	
	private static List<Unit> scan(Path root, boolean prunePartials) throws IOException {
		List<Unit> units = new ArrayList<>();

		try (Stream<Path> entries = Files.list(root)) {
			for (Path entry : entries.toList()) {
				if (entry.getFileName().toString().endsWith(PART_SUFFIX)) {
					if (prunePartials) {
						Files.deleteIfExists(entry);
					}
				} else {
					try {
						units.add(new Unit(entry, Files.getLastModifiedTime(entry).toMillis(),
								Files.size(entry)));
					} catch (IOException ignored) {
						// Gone, or momentarily unreadable. One unit uncounted, and the next sweep
						// counts it.
					}
				}
			}
		}

		units.sort(Comparator.comparingLong(Unit::stamp));

		return units;
	}

	private static long total(List<Unit> units) {
		long sum = 0L;
		for (Unit unit : units) {
			sum += unit.size();
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
				List<Unit> units = scan(root, false);
				total = total(units);
				long before = total;

				for (Unit unit : units) {
					if (total <= sweepTarget()) {
						break;
					}

					Files.deleteIfExists(unit.path());
					total -= unit.size();
				}

				if (total < before) {
					CoreVK.logger().info("The module cache went over its ceiling, so the units "
							+ "nothing has asked for lately were dropped, {} MB left",
							megabytes(total));
				}
			} catch (IOException e) {
				sayAboutWriting("the cache could not be swept", e);
			} finally {
				BYTES.set(total);

				if (total > ceilingBytes()) {
					nextSweepNanos = System.nanoTime() + SWEEP_BACKOFF_NANOS;
				}
			}
		}
	}

	private static void sayAboutReading(String what) {
		if (!saidAboutReading) {
			saidAboutReading = true;
			CoreVK.logger().warn("In the module cache, {}, so it was compiled instead. Said once a "
					+ "run: nothing about it stops a pack loading", what);
		}
	}

	
	private static void sayAboutStoring(String what) {
		if (!saidAboutStoring) {
			saidAboutStoring = true;
			CoreVK.logger().warn("In the module cache, {}, so it was used and not kept. Said once "
					+ "a run: the next load compiles it again and nothing else changes", what);
		}
	}

	private static void sayAboutWriting(String what, IOException cause) {
		if (!saidAboutWriting) {
			saidAboutWriting = true;
			CoreVK.logger().warn("In the module cache, {}: {}. Said once a run: the next load pays "
					+ "for the compile again and nothing else changes", what, cause.toString());
		}
	}

	private static String megabytes(long amount) {
		return String.format(Locale.ROOT, "%.1f", amount / 1048576.0D);
	}

	
	private record Unit(Path path, long stamp, long size) {
	}
}
