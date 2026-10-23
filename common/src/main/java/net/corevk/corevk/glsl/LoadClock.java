package net.corevk.corevk.glsl;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class LoadClock {

	private static final AtomicLong EXPANSION_NANOS = new AtomicLong();

	private static final AtomicInteger EXPANDED = new AtomicInteger();

	private static final AtomicInteger EXPANSIONS_SERVED = new AtomicInteger();

	private static final AtomicLong TRANSLATION_NANOS = new AtomicLong();

	private static final AtomicInteger TRANSLATED = new AtomicInteger();

	private static final AtomicLong MODULE_NANOS = new AtomicLong();

	private static final AtomicInteger MODULES = new AtomicInteger();

	private LoadClock() {
	}

	
	public static void reset() {
		EXPANSION_NANOS.set(0);
		EXPANDED.set(0);
		EXPANSIONS_SERVED.set(0);
		TRANSLATION_NANOS.set(0);
		TRANSLATED.set(0);
		MODULE_NANOS.set(0);
		MODULES.set(0);
	}

	
	public static void expansion(long nanos) {
		EXPANSION_NANOS.addAndGet(nanos);
		EXPANDED.incrementAndGet();
	}

	
	public static void expansionServed() {
		EXPANSIONS_SERVED.incrementAndGet();
	}

	
	public static void translation(long nanos) {
		TRANSLATION_NANOS.addAndGet(nanos);
		TRANSLATED.incrementAndGet();
	}

	
	public static void module(long nanos) {
		MODULE_NANOS.addAndGet(nanos);
		MODULES.incrementAndGet();
	}

	public static long expansionMillis() {
		return EXPANSION_NANOS.get() / 1_000_000L;
	}

	public static int expanded() {
		return EXPANDED.get();
	}

	public static int expansionsServed() {
		return EXPANSIONS_SERVED.get();
	}

	public static long translationMillis() {
		return TRANSLATION_NANOS.get() / 1_000_000L;
	}

	public static int translated() {
		return TRANSLATED.get();
	}

	public static long moduleMillis() {
		return MODULE_NANOS.get() / 1_000_000L;
	}

	public static int modules() {
		return MODULES.get();
	}
}
