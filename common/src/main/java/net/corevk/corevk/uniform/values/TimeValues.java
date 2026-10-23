package net.corevk.corevk.uniform.values;

import net.corevk.corevk.uniform.UniformCatalog;
import net.corevk.corevk.uniform.UniformShape;
import net.corevk.corevk.uniform.WorldState;

import java.time.LocalDateTime;

public final class TimeValues {

	private static final int SECONDS_PER_DAY = 86400;

	private static final WallClock WALL_CLOCK = new WallClock();

	private TimeValues() {
	}

	public static void register(UniformCatalog.Builder builder) {
		builder.add("frameCounter", UniformShape.INT, (world, out) -> out.set(world.frameCounter()));
		builder.add("frameTime", UniformShape.FLOAT, (world, out) -> out.set(world.frameTime()));
		builder.add("frameTimeCounter", UniformShape.FLOAT,
				(world, out) -> out.set(world.frameTimeCounter()));

		builder.add("worldTime", UniformShape.INT, (world, out) -> out.set((int) world.worldTime()));
		builder.add("worldDay", UniformShape.INT, (world, out) -> out.set((int) world.worldDay()));
		builder.add("moonPhase", UniformShape.INT, (world, out) -> out.set(world.moonPhase()));

		// The real world clock, which packs use for a seasonal tint or a date joke. Read once a
		// frame like everything else here: read per source, two passes of one frame either side of
		// a second boundary would be handed two different times.
		builder.add("currentDate", UniformShape.IVEC3, (world, out) -> {
			LocalDateTime now = WALL_CLOCK.at(world);
			out.set(now.getYear(), now.getMonthValue(), now.getDayOfMonth());
		});
		builder.add("currentTime", UniformShape.IVEC3, (world, out) -> {
			LocalDateTime now = WALL_CLOCK.at(world);
			out.set(now.getHour(), now.getMinute(), now.getSecond());
		});
		builder.add("currentYearTime", UniformShape.IVEC2, (world, out) -> {
			LocalDateTime now = WALL_CLOCK.at(world);
			int elapsed = (now.getDayOfYear() - 1) * SECONDS_PER_DAY + now.getHour() * 3600
					+ now.getMinute() * 60 + now.getSecond();
			out.set(elapsed, now.toLocalDate().lengthOfYear() * SECONDS_PER_DAY - elapsed);
		});
	}

	
	// The machine's own zone is the answer here rather than a default nobody chose: a pack asking
	// for the date wants the clock on the wall behind the player.
	@SuppressWarnings("JavaTimeDefaultTimeZone")
	private static final class WallClock {

		private int lastFrame = -1;
		private LocalDateTime sampled = LocalDateTime.now();

		private LocalDateTime at(WorldState world) {
			if (world.frameCounter() != this.lastFrame) {
				this.lastFrame = world.frameCounter();
				this.sampled = LocalDateTime.now();
			}

			return this.sampled;
		}
	}
}
