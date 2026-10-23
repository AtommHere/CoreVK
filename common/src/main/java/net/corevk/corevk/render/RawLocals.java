package net.corevk.corevk.render;

import net.corevk.corevk.CoreVK;
import net.corevk.corevk.glsl.LocalZeroes;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

public final class RawLocals {

	private static final boolean PROPERTY = Boolean.getBoolean("corevk.rawLocals");

	private static final String ARM_FILE = "raw-locals";

	private static volatile boolean armed;

	
	private static final ThreadLocal<Boolean> SNAPSHOT = new ThreadLocal<>();

	
	private static final AtomicLong WALKED = new AtomicLong();
	private static final AtomicLong ZEROED = new AtomicLong();
	private static final AtomicLong VALUES = new AtomicLong();

	private RawLocals() {
	}

	
	public static void read(Path gameDirectory) {
		armed = PROPERTY
				|| Files.isRegularFile(gameDirectory.resolve(CoreVK.MOD_ID).resolve(ARM_FILE));
	}

	
	public static void begin() {
		SNAPSHOT.set(armed);
	}

	public static void end() {
		SNAPSHOT.remove();
	}

	private static boolean raw() {
		Boolean snapshot = SNAPSHOT.get();

		return snapshot != null ? snapshot : armed;
	}

	
	public static String cacheWord() {
		return raw() ? "raw-locals" : "zeroed-locals";
	}

	
	public static ByteBuffer patch(String filename, ByteBuffer spirv) {
		if (raw() || !ours(filename)) {
			return spirv;
		}

		WALKED.incrementAndGet();
		int[] words = new int[spirv.remaining() / 4];
		spirv.duplicate().order(ByteOrder.nativeOrder()).asIntBuffer().get(words);
		LocalZeroes.Result result = LocalZeroes.apply(words);
		if (!result.changed()) {
			return spirv;
		}

		// The same allocator and the same zeroing as the game's own copy, since the module closes
		// this buffer exactly as it would have closed that one.
		ByteBuffer zeroed = MemoryUtil.memCalloc(result.words().length * 4);
		zeroed.order(ByteOrder.nativeOrder()).asIntBuffer().put(result.words());
		MemoryUtil.memFree(spirv);
		ZEROED.incrementAndGet();
		VALUES.addAndGet(result.variables() + result.undefs());

		return zeroed;
	}

	
	public static boolean ours(String filename) {
		return filename.startsWith(CoreVK.MOD_ID + "_") || filename.startsWith("pack/");
	}

	
	public static void say(long compiled) {
		long walked = WALKED.getAndSet(0L);
		long zeroed = ZEROED.getAndSet(0L);
		long values = VALUES.getAndSet(0L);
		if (armed) {
			CoreVK.logger().warn("Uninitialised variables left RAW, asked for by corevk/{} or by "
					+ "-Dcorevk.rawLocals ({} modules built this load, none walked): a pack reading "
					+ "one before writing it gets whatever the register held here and zero under "
					+ "CoreVK; remove the file to go back", ARM_FILE, compiled);

			return;
		}

		CoreVK.logger().info("Uninitialised variables given their zero: {} values in {} of the {} "
				+ "pack modules walked ({} modules built this load in all, the rest served already "
				+ "zeroed), which is the default. corevk/{} would leave them raw, and this line is "
				+ "said either way so that a reading can name the state it was taken under",
				values, zeroed, walked, compiled, ARM_FILE);
	}
}
