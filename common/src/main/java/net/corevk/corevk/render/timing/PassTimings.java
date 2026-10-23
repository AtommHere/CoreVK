package net.corevk.corevk.render.timing;

import net.corevk.corevk.render.GeometryHold;
import net.corevk.corevk.CoreVK;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.GpuQueryPool;
import com.mojang.blaze3d.systems.RenderSystem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import java.util.function.Supplier;

public final class PassTimings {

	
	private static final int REPORT_SECONDS = Integer.getInteger("corevk.passTimings", 0);

	private static final boolean ENABLED = REPORT_SECONDS > 0;

	
	private static final int PASSES_PER_FRAME = 256;

	private static final int QUERIES_PER_FRAME = PASSES_PER_FRAME * 2;

	
	private static final int SLOTS = 8;

	
	private static final int ROWS = 24;

	private static final long NANOS_PER_SECOND = 1_000_000_000L;

	private static GpuQueryPool pool;
	private static float nanosPerTick;
	private static long frameNumber;
	private static Frame current;
	private static final ArrayDeque<Frame> pending = new ArrayDeque<>();

	private static final Map<String, Row> rows = new HashMap<>();
	private static long framesSummed;
	private static long framesDropped;
	private static long framesOverflowed;
	private static long passTicks;
	private static long spanTicks;
	private static long frameEnds;
	private static long lastReport;

	
	private static final int CENSUS_PROPERTY = Integer.getInteger("corevk.passCensus", 0);

	private static final boolean KEEP_PROPERTY = Boolean.getBoolean("corevk.keepRedoneWork");

	private static final String KEEP_ARM_FILE = "keep-redone-work";

	
	private static int keepRedone = -1;

	private static final boolean FIRST_DRAW_PROPERTY =
			Boolean.getBoolean("corevk.keepFirstDrawCompiles");

	private static final String FIRST_DRAW_ARM_FILE = "keep-first-draw-compiles";

	
	private static int keepFirstDraw = -1;

	
	private static final int ARMED_BY_FILE_SECONDS = 5;

	private static final String CENSUS_ARM_FILE = "pass-census";

	
	private static int censusSeconds = -1;

	
	private static long lastCensus;

	private static boolean censusPrinted;
	private static boolean censusArmed;
	private static boolean censusComplete;
	private static int censusPasses;
	private static int censusClears;
	private static int censusCopies;

	
	private static int censusSubmits;
	private static Supplier<String> censusOpenLabel;
	private static final Map<String, Integer> censusLabels = new HashMap<>();

	
	private static final Map<String, Map<String, Integer>> censusReopens = new HashMap<>();

	
	private static int censusSlices;

	private static int censusProgramWalks;
	private static int censusFarSections;

	
	private static long censusFrames;

	private static long censusFramesAt;

	
	private static final long[] censusIntervals = new long[4096];

	
	private static int censusIntervalsAt;

	private static int censusIntervalsHeld;

	
	private static long lastFrameAt;

	
	private static long censusFramesMissingMap;

	private static long censusFramesDecidingMap;

	private PassTimings() {
	}

	
	public static void shadowMap(boolean missed) {
		censusFramesDecidingMap++;
		if (missed) {
			censusFramesMissingMap++;
		}
	}

	
	public static void armCensus() {
		if (censusPrinted || censusArmed) {
			return;
		}

		// The repeating census waits its interval out. Without this it would count and print every
		// frame, which is a line a second at best and a log nobody can read at worst.
		if (lastCensus != 0L
				&& System.nanoTime() - lastCensus < censusSeconds() * NANOS_PER_SECOND) {
			return;
		}

		censusArmed = true;
		censusComplete = false;
		censusPasses = 0;
		censusClears = 0;
		censusCopies = 0;
		censusSlices = 0;
		censusProgramWalks = 0;
		censusFarSections = 0;
		censusSubmits = 0;
		censusOpenLabel = null;
		censusLabels.clear();
		censusReopens.clear();
	}

	
	public static boolean censusArmed() {
		return censusArmed;
	}

	
	public static void censusReopen(Supplier<String> label, String cause) {
		if (!censusArmed) {
			return;
		}

		String name = label == null ? "an unnamed pass" : label.get();
		censusReopens.computeIfAbsent(name, key -> new HashMap<>()).merge(cause, 1, Integer::sum);
	}

	
	public static void finishCensus() {
		if (censusPrinted || !censusArmed) {
			return;
		}

		censusComplete = true;
	}

	
	public static void resetCensus() {
		censusPrinted = false;
		censusArmed = false;
		censusComplete = false;
		censusPasses = 0;
		censusClears = 0;
		censusCopies = 0;
		censusSlices = 0;
		censusProgramWalks = 0;
		censusFarSections = 0;
		censusSubmits = 0;
		lastCensus = 0L;
		// The interval that spans a pack load is the load itself, seconds of it, and left in the
		// window it would be the worst frame of the next reading and a late frame in its count.
		lastFrameAt = 0L;
		censusIntervalsAt = 0;
		censusIntervalsHeld = 0;
		censusFramesMissingMap = 0;
		censusFramesDecidingMap = 0;
		// Read again, so an arming file written or changed while the game runs is picked up by the
		// next pack load rather than by the next launch.
		censusSeconds = -1;
		keepRedone = -1;
		keepFirstDraw = -1;
		censusOpenLabel = null;
		censusLabels.clear();
		censusReopens.clear();
	}

	
	private static int censusSeconds() {
		if (censusSeconds >= 0) {
			return censusSeconds;
		}

		censusSeconds = CENSUS_PROPERTY > 0 ? CENSUS_PROPERTY : armedByFile();

		return censusSeconds;
	}

	
	public static boolean keepRedoneWork() {
		if (keepRedone < 0) {
			keepRedone = KEEP_PROPERTY || armFile(KEEP_ARM_FILE) != null ? 1 : 0;
		}

		return keepRedone == 1;
	}

	
	public static boolean keepFirstDrawCompiles() {
		if (keepFirstDraw < 0) {
			keepFirstDraw = FIRST_DRAW_PROPERTY || armFile(FIRST_DRAW_ARM_FILE) != null ? 1 : 0;
		}

		return keepFirstDraw == 1;
	}

	
	private static Path armFile(String name) {
		try {
			Path file = CoreVK.platform().gameDirectory().resolve("corevk").resolve(name);

			return Files.isRegularFile(file) ? file : null;
		} catch (RuntimeException ignored) {
			return null;
		}
	}

	
	private static int armedByFile() {
		Path file = armFile(CENSUS_ARM_FILE);
		if (file == null) {
			return 0;
		}

		try {
			String asked = Files.readString(file).trim();
			if (asked.isEmpty()) {
				return ARMED_BY_FILE_SECONDS;
			}

			int seconds = Integer.parseInt(asked);

			return seconds > 0 ? seconds : ARMED_BY_FILE_SECONDS;
		} catch (IOException | RuntimeException ignored) {
			return ARMED_BY_FILE_SECONDS;
		}
	}

	
	public static void censusSubmit() {
		if (censusArmed) {
			censusSubmits++;
		}
	}

	
	public static void censusClear() {
		if (censusArmed) {
			censusClears++;
		}
	}

	
	public static void censusCopy() {
		if (censusArmed) {
			censusCopies++;
		}
	}

	
	public static void censusSlice() {
		if (censusArmed) {
			censusSlices++;
		}
	}

	
	public static void censusProgramWalk() {
		if (censusArmed) {
			censusProgramWalks++;
		}
	}

	
	public static void censusFarSections(int built) {
		if (censusArmed) {
			censusFarSections += built;
		}
	}

	
	public static void open(CommandEncoder encoder, Supplier<String> label) {
		if (censusArmed) {
			censusOpenLabel = label;
		}

		if (!ENABLED) {
			return;
		}

		Frame frame = current;
		if (frame == null) {
			frame = start();
		}

		if (frame.open) {
			// A pass inside a pass, which the encoder refuses before this is reached.
			return;
		}

		if (frame.count == PASSES_PER_FRAME) {
			frame.overflow = true;
			return;
		}

		encoder.writeTimestamp(pool, frame.slot * QUERIES_PER_FRAME + frame.count * 2);
		frame.openLabel = label;
		frame.open = true;
	}

	
	public static void close(CommandEncoder encoder) {
		if (censusArmed && censusOpenLabel != null) {
			String name = censusOpenLabel.get();
			censusLabels.merge(name, 1, Integer::sum);
			censusPasses++;
			censusOpenLabel = null;
		}

		if (!ENABLED) {
			return;
		}

		Frame frame = current;
		if (frame == null || !frame.open) {
			return;
		}

		encoder.writeTimestamp(pool, frame.slot * QUERIES_PER_FRAME + frame.count * 2 + 1);
		frame.labels[frame.count] = frame.openLabel.get();
		frame.count++;
		frame.open = false;
		frame.openLabel = null;
	}

	
	public static void endFrame() {
		censusFrames++;
		long frameAt = System.nanoTime();
		if (censusFramesAt == 0L) {
			censusFramesAt = frameAt;
		}

		if (lastFrameAt != 0L) {
			censusIntervals[censusIntervalsAt] = frameAt - lastFrameAt;
			censusIntervalsAt = (censusIntervalsAt + 1) % censusIntervals.length;
			if (censusIntervalsHeld < censusIntervals.length) {
				censusIntervalsHeld++;
			}
		}

		lastFrameAt = frameAt;

		if (censusComplete && !censusPrinted) {
			printCensus();
			lastCensus = System.nanoTime();
			// One shot unless a flag asked for more, in which case the next arm is what the
			// interval above gates rather than this.
			censusPrinted = censusSeconds() <= 0;
		}

		censusArmed = false;
		censusComplete = false;

		if (!ENABLED) {
			return;
		}

		long now = System.nanoTime();
		if (lastReport == 0) {
			lastReport = now;
		}

		frameEnds++;
		Frame frame = current;
		if (frame != null) {
			current = null;
			pending.addLast(frame);
		}

		resolve();
		if (now - lastReport >= REPORT_SECONDS * NANOS_PER_SECOND) {
			report(now);
		}
	}

	
	private static void printCensus() {
		CoreVK.logger().info("{} opened {} render passes, cleared {} textures and copied {}, for {} "
						+ "queue submits",
				censusSeconds() > 0 ? "A frame of this pack" : "This pack's first full frame",
				censusPasses, censusClears, censusCopies, censusSubmits);
		CoreVK.logger().info("  and redid {} uniform slices, {} terrain program walks and {} far "
				+ "terrain sections", censusSlices, censusProgramWalks, censusFarSections);
		printRate();

		List<Map.Entry<String, Integer>> sorted = new ArrayList<>(censusLabels.entrySet());
		sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
		int shown = 0;
		int restPasses = 0;
		int restLabels = 0;
		for (Map.Entry<String, Integer> entry : sorted) {
			if (shown < ROWS) {
				CoreVK.logger().info("  x{}  {}", entry.getValue(), entry.getKey());
				printReopens(entry.getKey());
				shown++;
			} else {
				restPasses += entry.getValue();
				restLabels++;
			}
		}

		if (restLabels > 0) {
			CoreVK.logger().info("  x{}  {} other labels", restPasses, restLabels);
		}
	}

	
	private static void printRate() {
		long now = System.nanoTime();
		double seconds = (now - censusFramesAt) / (double) NANOS_PER_SECOND;
		if (censusFrames > 0 && seconds > 0) {
			CoreVK.logger().info("  {} frames a second over the last {} s, {} ms a frame",
					String.format(Locale.ROOT, "%.1f", censusFrames / seconds),
					String.format(Locale.ROOT, "%.1f", seconds),
					String.format(Locale.ROOT, "%.2f", seconds * 1000 / censusFrames));
			// Left to the timed report when that one is armed, or one frame carries both and the
			// same two lines print twice off the same state, which reads as two windows.
			if (!ENABLED) {
				printSpread();
				printMissedMaps();
			}
		}

		censusFrames = 0;
		censusFramesAt = now;
	}

	
	private static void printSpread() {
		int held = censusIntervalsHeld;
		if (held < 200) {
			return;
		}

		long[] sorted = new long[held];
		System.arraycopy(censusIntervals, 0, sorted, 0, held);
		Arrays.sort(sorted);

		long median = sorted[held / 2];
		long late = median * 2;
		int lateFrames = 0;
		for (long interval : sorted) {
			if (interval > late) {
				lateFrames++;
			}
		}

		CoreVK.logger().info("  middle frame {} ms, one in a hundred over {} ms, worst {} ms, "
						+ "{} of the last {} frames late",
				wallMillis(median), wallMillis(sorted[(int) (held * 0.99)]),
				wallMillis(sorted[held - 1]), lateFrames, held);
	}

	
	private static void printMissedMaps() {
		if (censusFramesMissingMap == 0) {
			return;
		}

		CoreVK.logger().info("  {} of {} frames this pack drew read a shadow map the frame that "
				+ "planned it never drew", censusFramesMissingMap, censusFramesDecidingMap);
	}

	
	private static String wallMillis(long nanos) {
		return String.format(Locale.ROOT, "%.2f", nanos / 1_000_000.0);
	}

	
	private static void printReopens(String label) {
		Map<String, Integer> causes = censusReopens.get(label);
		if (causes == null) {
			return;
		}

		List<Map.Entry<String, Integer>> sorted = new ArrayList<>(causes.entrySet());
		sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
		for (Map.Entry<String, Integer> cause : sorted) {
			CoreVK.logger().info("        x{}  because {}", cause.getValue(), cause.getKey());
		}
	}

	private static Frame start() {
		if (pool == null) {
			GpuDevice device = RenderSystem.getDevice();
			pool = device.createTimestampQueryPool(SLOTS * QUERIES_PER_FRAME);
			nanosPerTick = device.getDeviceInfo().timestampPeriod();
		}

		int slot = (int) (frameNumber % SLOTS);
		frameNumber++;
		// The frame that last used this slot is SLOTS frames old. Still unread, its queries are
		// about to be reset under it, so it goes now rather than being read over the new writes.
		Frame oldest = pending.peekFirst();
		if (oldest != null && oldest.slot == slot) {
			pending.pollFirst();
			framesDropped++;
		}

		Frame frame = new Frame(slot);
		current = frame;
		return frame;
	}

	
	private static void resolve() {
		while (!pending.isEmpty()) {
			Frame frame = pending.peekFirst();
			if (frame.count > 0) {
				OptionalLong[] values = pool.getValues(frame.slot * QUERIES_PER_FRAME, frame.count * 2);
				for (OptionalLong value : values) {
					if (value.isEmpty()) {
						return;
					}
				}

				long first = Long.MAX_VALUE;
				long last = Long.MIN_VALUE;
				for (int i = 0; i < frame.count; i++) {
					long begin = values[i * 2].getAsLong();
					long end = values[i * 2 + 1].getAsLong();
					long ticks = end - begin;
					Row row = rows.computeIfAbsent(frame.labels[i], label -> new Row());
					row.ticks += ticks;
					row.count++;
					passTicks += ticks;
					first = Math.min(first, begin);
					last = Math.max(last, end);
				}

				spanTicks += last - first;
			}

			if (frame.overflow) {
				framesOverflowed++;
			}

			framesSummed++;
			pending.pollFirst();
		}
	}

	private static void report(long now) {
		double seconds = (now - lastReport) / (double) NANOS_PER_SECOND;
		if (framesSummed == 0) {
			CoreVK.logger().info("Pass timings over {} s: no frame answered yet",
					String.format(Locale.ROOT, "%.1f", seconds));
		} else {
			double frames = framesSummed;
			double betweenFrames = frameEnds == 0 ? 0 : seconds * 1000 / frameEnds;
			CoreVK.logger().info("Pass timings over {} s, {} frames: {} ms of passes in a {} ms span, "
					+ "{} ms between frames", String.format(Locale.ROOT, "%.1f", seconds), framesSummed,
					millis(passTicks, frames), millis(spanTicks, frames),
					String.format(Locale.ROOT, "%.2f", betweenFrames));

			List<Map.Entry<String, Row>> sorted = new ArrayList<>(rows.entrySet());
			sorted.sort((a, b) -> Long.compare(b.getValue().ticks, a.getValue().ticks));
			double total = Math.max(1, passTicks);
			int shown = 0;
			long restTicks = 0;
			int restLabels = 0;
			for (Map.Entry<String, Row> entry : sorted) {
				Row row = entry.getValue();
				if (shown < ROWS) {
					CoreVK.logger().info("  {} ms {}%  x{}  {}", millis(row.ticks, frames),
							percent(row.ticks, total), perFrame(row.count, frames), entry.getKey());
					shown++;
				} else {
					restTicks += row.ticks;
					restLabels++;
				}
			}

			if (restLabels > 0) {
				CoreVK.logger().info("  {} ms {}%  {} other labels", millis(restTicks, frames),
						percent(restTicks, total), restLabels);
			}

			if (framesDropped > 0 || framesOverflowed > 0) {
				CoreVK.logger().info("  {} frames dropped unanswered, {} overflowed {} passes",
						framesDropped, framesOverflowed, PASSES_PER_FRAME);
			}
		}

		// Outside the branch above, because how evenly the frames arrived is a reading of the wall
		// clock and does not need the card to have answered a single query.
		printSpread();
		printMissedMaps();

		rows.clear();
		framesSummed = 0;
		framesDropped = 0;
		framesOverflowed = 0;
		passTicks = 0;
		spanTicks = 0;
		frameEnds = 0;
		lastReport = now;
	}

	private static String millis(long ticks, double frames) {
		return String.format(Locale.ROOT, "%7.3f", ticks * (double) nanosPerTick / 1_000_000.0 / frames);
	}

	private static String percent(long ticks, double total) {
		return String.format(Locale.ROOT, "%5.1f", 100.0 * ticks / total);
	}

	private static String perFrame(long count, double frames) {
		return String.format(Locale.ROOT, "%-5.1f", count / frames);
	}

	
	private static final class Frame {

		final int slot;
		final String[] labels = new String[PASSES_PER_FRAME];
		int count;
		boolean open;
		boolean overflow;
		Supplier<String> openLabel;

		Frame(int slot) {
			this.slot = slot;
		}
	}

	private static final class Row {

		long ticks;
		long count;
	}
}
