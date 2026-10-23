package net.corevk.corevk.render;

import net.corevk.corevk.CoreVK;
import net.corevk.corevk.glsl.DebugNames;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicLong;

public final class PackNames {

	private static final AtomicLong WALKED = new AtomicLong();
	private static final AtomicLong STRIPPED = new AtomicLong();
	private static final AtomicLong NAMES = new AtomicLong();

	private PackNames() {
	}

	
	public static String cacheWord() {
		return DebugNames.VERSION;
	}

	
	public static ByteBuffer patch(String filename, ByteBuffer spirv) {
		// The same set of compiles RawLocals claims, asked of RawLocals rather than restated: two
		// copies of that rule would answer differently the day one of them is widened.
		if (ShaderDebugInfo.asked() || !RawLocals.ours(filename)) {
			return spirv;
		}

		WALKED.incrementAndGet();
		int[] words = new int[spirv.remaining() / 4];
		spirv.duplicate().order(ByteOrder.nativeOrder()).asIntBuffer().get(words);
		DebugNames.Result result = DebugNames.strip(words);
		if (!result.changed()) {
			return spirv;
		}

		ByteBuffer stripped = MemoryUtil.memCalloc(result.words().length * 4);
		stripped.order(ByteOrder.nativeOrder()).asIntBuffer().put(result.words());
		MemoryUtil.memFree(spirv);
		STRIPPED.incrementAndGet();
		NAMES.addAndGet(result.dropped());

		return stripped;
	}

	
	public static void say(long compiled) {
		long walked = WALKED.getAndSet(0L);
		long stripped = STRIPPED.getAndSet(0L);
		long names = NAMES.getAndSet(0L);
		if (ShaderDebugInfo.asked()) {
			CoreVK.logger().warn("Debug names LEFT IN every module, asked for by "
					+ "-Dcorevk.shaderDebugInfo ({} modules built this load, none walked): a pack "
					+ "identifier that collides with Metal's namespace refuses its pipeline on "
					+ "Apple hardware, which is what dropping them prevents", compiled);

			return;
		}

		CoreVK.logger().info("Debug names dropped off what the outside never asks for: {} names in "
						+ "{} of the {} pack modules walked ({} modules built this load in all, the "
						+ "rest served already stripped), which is the default. "
						+ "-Dcorevk.shaderDebugInfo would leave them in, and this line is said "
						+ "either way so that a reading can name the state it was taken under",
				names, stripped, walked, compiled);
	}
}
