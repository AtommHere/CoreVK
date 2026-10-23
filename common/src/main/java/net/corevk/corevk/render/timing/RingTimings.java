package net.corevk.corevk.render.timing;

import net.corevk.corevk.CoreVK;

import net.caffeinemc.mods.sodium.client.gpu.device.backend.DrawBackend;
import net.minecraft.client.Minecraft;

import java.nio.file.Files;
import java.util.Locale;

public final class RingTimings {

	private static final int REPORT_SECONDS = Integer.getInteger("corevk.ringTimings", 0);

	private static final boolean ENABLED = REPORT_SECONDS > 0;

	private static final boolean KEEP = Boolean.getBoolean("corevk.keepShadowRotate");

	private static final long NANOS_PER_SECOND = 1_000_000_000L;

	private static int rotatesThisFrame;

	private static long begin;

	private static long frames;

	private static long firstNanos;

	private static long firstCount;

	private static long firstMax;

	private static long secondNanos;

	private static long secondCount;

	private static long secondMax;

	private static long lastReport;

	private static boolean loggedBackend;

	
	private static Boolean keepFromFile;

	private RingTimings() {
	}

	
	public static boolean keepSecondRotate() {
		if (KEEP) {
			return true;
		}

		if (keepFromFile == null) {
			Minecraft minecraft = Minecraft.getInstance();
			if (minecraft == null || minecraft.gameDirectory == null) {
				return false;
			}

			keepFromFile = Files.isRegularFile(minecraft.gameDirectory.toPath()
					.resolve("corevk").resolve("keep-shadow-rotate"));
		}

		return keepFromFile;
	}

	public static void beginRotate() {
		if (!ENABLED) {
			return;
		}

		begin = System.nanoTime();
	}

	public static void endRotate() {
		if (!ENABLED) {
			return;
		}

		long dt = System.nanoTime() - begin;
		rotatesThisFrame++;
		if (rotatesThisFrame == 1) {
			firstNanos += dt;
			firstCount++;
			if (dt > firstMax) {
				firstMax = dt;
			}
		} else if (rotatesThisFrame == 2) {
			secondNanos += dt;
			secondCount++;
			if (dt > secondMax) {
				secondMax = dt;
			}
		}

		if (!loggedBackend) {
			loggedBackend = true;
			CoreVK.logger().info("Ring timings: backend {}, keepShadowRotate {}",
					DrawBackend.BACKEND, keepSecondRotate());
		}
	}

	public static void endFrame() {
		if (!ENABLED) {
			return;
		}

		long now = System.nanoTime();
		if (lastReport == 0) {
			lastReport = now;
		}

		frames++;
		rotatesThisFrame = 0;
		if (now - lastReport >= REPORT_SECONDS * NANOS_PER_SECOND) {
			report(now);
		}
	}

	private static void report(long now) {
		double seconds = (now - lastReport) / (double) NANOS_PER_SECOND;
		CoreVK.logger().info(
				"Ring timings over {} s, {} frames, backend {}, keepShadowRotate {}",
				String.format(Locale.ROOT, "%.1f", seconds), frames, DrawBackend.BACKEND,
				keepSecondRotate());
		CoreVK.logger().info("  first rotate:  {} ms avg, {} ms max, {} times",
				millis(firstNanos, firstCount), millis(firstMax, 1), firstCount);
		CoreVK.logger().info("  second rotate: {} ms avg, {} ms max, {} times",
				millis(secondNanos, secondCount), millis(secondMax, 1), secondCount);

		frames = 0;
		firstNanos = 0;
		firstCount = 0;
		firstMax = 0;
		secondNanos = 0;
		secondCount = 0;
		secondMax = 0;
		lastReport = now;
	}

	private static String millis(long nanos, long count) {
		if (count == 0) {
			return "  0.000";
		}

		return String.format(Locale.ROOT, "%7.3f", nanos / 1_000_000.0 / count);
	}
}
