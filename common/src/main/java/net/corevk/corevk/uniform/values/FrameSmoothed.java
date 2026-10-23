package net.corevk.corevk.uniform.values;

import net.corevk.corevk.uniform.Smoothed;
import net.corevk.corevk.uniform.WorldState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

public final class FrameSmoothed {

	
	private static final List<FrameSmoothed> ALL = new ArrayList<>();

	
	private static final Set<Smoothed> TRACKED =
			Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

	private final Smoothed smoothed = new Smoothed();

	private int lastFrame = -1;
	private float value;

	FrameSmoothed() {
		ALL.add(this);
	}

	
	public static Smoothed tracked() {
		Smoothed smoothed = new Smoothed();
		TRACKED.add(smoothed);

		return smoothed;
	}

	
	public static void forgetAll() {
		for (FrameSmoothed smoothed : ALL) {
			smoothed.smoothed.reset();
			smoothed.lastFrame = -1;
			smoothed.value = 0.0F;
		}

		synchronized (TRACKED) {
			TRACKED.forEach(Smoothed::reset);
		}
	}

	float get(WorldState world, float raw, float halfLifeUp, float halfLifeDown) {
		if (world.frameCounter() != this.lastFrame) {
			this.lastFrame = world.frameCounter();
			this.value = this.smoothed.updateAndGet(raw, halfLifeUp, halfLifeDown, world.frameTime());
		}

		return this.value;
	}
}
