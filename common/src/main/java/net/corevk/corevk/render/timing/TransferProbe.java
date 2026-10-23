package net.corevk.corevk.render.timing;

import net.corevk.corevk.CoreVK;

import net.minecraft.client.Minecraft;

import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class TransferProbe {

	private static final boolean PROPERTY = Boolean.getBoolean("corevk.transferInPass");

	private static final String ARM_FILE = "transfer-in-pass";

	private static final long REPORT_NANOS = 10_000_000_000L;

	private static final int CALLER_FRAMES = 5;

	private static final StackWalker WALKER = StackWalker.getInstance();

	private static final Map<String, int[]> COUNTS = new HashMap<>();

	
	private static Boolean armed;

	private static boolean announced;

	private static long lastReport;

	private TransferProbe() {
	}

	
	public static void seen(String call, Supplier<String> open) {
		if (!armed()) {
			return;
		}

		if (!announced) {
			announced = true;
			lastReport = System.nanoTime();
			CoreVK.logger().warn("Transfer probe armed by corevk/{}: a buffer or texture transfer, a "
					+ "colour or depth clear or a pass opening recorded while a render pass is open is "
					+ "named here", ARM_FILE);
		}

		if (open == null) {
			return;
		}

		String caller = WALKER.walk(frames -> frames
				.filter(frame -> outside(frame.getClassName()))
				.limit(CALLER_FRAMES)
				.map(frame -> simple(frame.getClassName()) + "." + frame.getMethodName())
				.collect(Collectors.joining(" <- ")));
		String key = call + " inside \"" + open.get() + "\" from " + caller;
		int[] count = COUNTS.get(key);
		if (count == null) {
			count = new int[1];
			COUNTS.put(key, count);
			CoreVK.logger().warn("Transfer inside an open pass: {}", key);
		}

		count[0]++;

		long now = System.nanoTime();
		if (now - lastReport >= REPORT_NANOS) {
			lastReport = now;
			COUNTS.forEach((name, n) -> CoreVK.logger().info("Transfer inside an open pass, {} "
					+ "times so far: {}", n[0], name));
		}
	}

	private static boolean armed() {
		if (PROPERTY) {
			return true;
		}

		if (armed == null) {
			Minecraft minecraft = Minecraft.getInstance();
			if (minecraft == null || minecraft.gameDirectory == null) {
				return false;
			}

			armed = Files.isRegularFile(minecraft.gameDirectory.toPath()
					.resolve("corevk").resolve(ARM_FILE));
		}

		return armed;
	}

	private static boolean outside(String className) {
		return !className.startsWith("com.mojang.blaze3d.vulkan.VulkanCommandEncoder")
				&& !className.startsWith("com.mojang.blaze3d.systems.CommandEncoder")
				&& !className.startsWith("net.corevk.corevk.render.timing.TransferProbe")
				&& !className.startsWith("java.");
	}

	private static String simple(String className) {
		int dot = className.lastIndexOf('.');

		return dot < 0 ? className : className.substring(dot + 1);
	}
}
