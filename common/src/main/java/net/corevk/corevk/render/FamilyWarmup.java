package net.corevk.corevk.render;

import net.corevk.corevk.glsl.LoadClock;
import net.corevk.corevk.glsl.TranslationCache;
import net.corevk.corevk.pack.option.OptionValue;
import net.corevk.corevk.pack.source.OpenedPack;
import net.corevk.corevk.render.timing.PassTimings;
import net.corevk.corevk.CoreVK;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.util.Util;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class FamilyWarmup {

	
	private static final Set<CompletableFuture<Void>> WARMUPS = ConcurrentHashMap.newKeySet();

	
	private static final ThreadPoolExecutor COMPILE_POOL = compilePool();

	
	private final List<FamilyDraw> families;

	
	private final Path packPath;
	private final Map<String, OptionValue> chosen;
	private final String profile;

	
	private final Object familyMaps;

	private boolean started;
	private volatile int familiesReady;

	
	private volatile boolean released;

	
	private volatile boolean familiesWarmed;

	
	private long warmedAt;

	
	private final AtomicInteger warmWalked = new AtomicInteger();
	private final AtomicInteger warmServed = new AtomicInteger();
	private final AtomicInteger warmTotal = new AtomicInteger();

	FamilyWarmup(List<FamilyDraw> families, Path packPath, Map<String, OptionValue> chosen,
			String profile, Object familyMaps) {
		this.families = families;
		this.packPath = packPath;
		this.chosen = chosen;
		this.profile = profile;
		this.familyMaps = familyMaps;
	}

	
	int familiesReady() {
		return this.familiesReady;
	}

	
	boolean warmed() {
		return this.familiesWarmed;
	}

	
	long warmedAt() {
		return this.warmedAt;
	}

	int walked() {
		return this.warmWalked.get();
	}

	int total() {
		return this.warmTotal.get();
	}

	
	void start() {
		synchronized (this) {
			if (this.started) {
				return;
			}

			this.started = true;
		}

		// Read here, before any worker exists, and carried in as a value: the arming file read
		// lazily from a worker would race the reset a pack swap does on the render thread.
		boolean keepOld = PassTimings.keepFirstDrawCompiles();
		AtomicBoolean fanned = new AtomicBoolean();
		AtomicLong start = new AtomicLong();
		CompletableFuture<Void> whole;
		try {
			whole = CompletableFuture.supplyAsync(() -> {
				start.set(System.nanoTime());
				CoreVK.logger().info("The pack-load worker starts on the six families");
				GpuDevice device = compileDevice(keepOld);
				fanned.set(device != null);

				List<CompletableFuture<Void>> compiles = new ArrayList<>(this.families.size());
				// One opening for the six, so the plan of the place, the program tree and every
				// header they share are worked out once on this worker rather than once per family:
				// the families used to be five of every six walks a warm load made of the archive.
				// A family that reads later on its own, at a first draw, still opens for itself.
				try (OpenedPack shared = OpenedPack.open(this.packPath, this.chosen, this.profile)) {
					for (int family = 0; family < this.families.size(); family++) {
						FamilyDraw read = this.families.get(family);
						prefetchFamily(() -> read.prefetch(shared));
						spawnFamilyCompiles(compiles, family, device);
					}
				} catch (Throwable e) {
					// prefetchFamily catches the RuntimeException of one translation; anything
					// harder would otherwise take this stage down EXCEPTIONALLY, and the whole
					// would then complete while the tasks already spawned still run, out of the
					// reach of the shutdown wait. Caught here, the spawned tasks stay tracked
					// and the families never reached keep their first-draw path.
					CoreVK.logger().error("The pack-load worker died", e);
				}

				return compiles;
			}, Util.backgroundExecutor()).thenCompose(compiles ->
					CompletableFuture.allOf(compiles.toArray(new CompletableFuture<?>[0])));
		} catch (RejectedExecutionException e) {
			// The executor only refuses while the client shuts down. The families keep their
			// first-draw path, and the flag closes the mark rather than leaving one that can
			// never go out.
			this.warmedAt = Util.getMillis();
			this.familiesWarmed = true;

			return;
		}

		CompletableFuture<Void> tracked = whole.handle((unused, e) -> {
			if (e != null) {
				// handle() and not a catch: the futures swallow what their runnables throw, so
				// anything that dies unlogged reads as the workers having finished. The families
				// they did not reach fall back to the first-draw path either way, and one
				// family's failure no longer stops the five others.
				CoreVK.logger().error("The pack-load worker died", e);
			}

			this.warmedAt = Util.getMillis();
			this.familiesWarmed = true;
			if (fanned.get()) {
				CoreVK.logger().info("{} of {} leftover pipelines compiled ahead of their "
						+ "first draw, {} ms of background work, translations included",
						this.warmServed.get(), this.warmWalked.get(),
						(System.nanoTime() - start.get()) / 1_000_000L);
				// Beside the total it explains. The spans are summed per program across workers,
				// so together they can pass the wall clock of the load; they compare with each
				// other, not with it.
				CoreVK.logger().info("With the families in, flattening the chain's units cost {} "
						+ "ms over {} of them with {} more handed back, translating cost {} ms over "
						+ "{} translator calls with {} programs served from the translation cache "
						+ "and {} translated, and making modules cost {} ms over {} modules, "
						+ "shaderc and SPIRV-Cross together", LoadClock.expansionMillis(),
						LoadClock.expanded(), LoadClock.expansionsServed(),
						LoadClock.translationMillis(), LoadClock.translated(),
						TranslationCache.served(), TranslationCache.translated(),
						LoadClock.moduleMillis(), LoadClock.modules());
			}

			return (Void) null;
		});
		WARMUPS.add(tracked);
		tracked.whenComplete((unused, e) -> WARMUPS.remove(tracked));
	}

	private static ThreadPoolExecutor compilePool() {
		AtomicInteger names = new AtomicInteger();
		ThreadPoolExecutor pool = new ThreadPoolExecutor(3, 3, 30L, TimeUnit.SECONDS,
				new LinkedBlockingQueue<>(), runnable -> {
					Thread thread = new Thread(runnable,
							"CoreVK compile worker " + names.incrementAndGet());
					thread.setDaemon(true);
					// The game outranks the warm-up by design: a window measured during play ran
					// six times longer than the same one behind a loading screen, which is these
					// threads yielding, and the priority writes that bargain down.
					thread.setPriority(Thread.MIN_PRIORITY);

					return thread;
				});
		pool.allowCoreThreadTimeOut(true);

		return pool;
	}

	
	private static GpuDevice compileDevice(boolean keepOld) {
		GpuDevice front = RenderSystem.tryGetDevice();
		if (front != null && !keepOld && AheadCompiler.available(front)) {
			return front;
		}

		CoreVK.logger().info("The workers leave the leftover families to their first draw: {}",
				front == null ? "no device"
						: keepOld ? "keep-first-draw-compiles"
								: "the backend is not the Vulkan one");

		return null;
	}

	
	private void spawnFamilyCompiles(List<CompletableFuture<Void>> compiles, int family,
			GpuDevice device) {
		if (device == null) {
			return;
		}

		// Copied here, on the thread that has just filled this family, and never walked live:
		// the maps behind it are emptied on the render thread when the pack goes, and an
		// iterator standing in one then throws under the worker even though the released flag
		// it reads every step is already up. Under the lock, because the copy itself is a read
		// of the live map and the emptying is what it races.
		List<DumpedProgram> programs;
		synchronized (this.familyMaps) {
			programs = List.copyOf(familyPrograms(family));
		}

		// The plate grows before the task that will empty it exists, so the corner's count can
		// only ever run behind the truth, never past it.
		this.warmTotal.addAndGet(programs.size());
		compiles.add(CompletableFuture.runAsync(
				() -> warmFamily(programs, device), COMPILE_POOL));
	}

	
	private void warmFamily(List<DumpedProgram> programs, GpuDevice device) {
		try (AheadCompiler compiler = AheadCompiler.open(device)) {
			for (DumpedProgram program : programs) {
				if (this.released || PackChain.stopped()) {
					return;
				}

				this.warmWalked.incrementAndGet();
				if (program.warmAhead(compiler)) {
					this.warmServed.incrementAndGet();
				}
			}
		}
	}

	
	private void prefetchFamily(Runnable prefetch) {
		if (this.released || PackChain.stopped()) {
			return;
		}

		try {
			prefetch.run();
		} catch (RuntimeException e) {
			CoreVK.logger().error("Translating a pack family failed", e);
		}

		this.familiesReady++;
	}

	Collection<? extends DumpedProgram> familyPrograms(int index) {
		return index >= 0 && index < this.families.size()
				? this.families.get(index).programs()
				: List.of();
	}

	
	void release() {
		this.released = true;
	}

	
	static void awaitAll() {
		CompletableFuture<?>[] running = WARMUPS.toArray(new CompletableFuture<?>[0]);
		if (running.length == 0) {
			return;
		}

		try {
			CompletableFuture.allOf(running).get(2, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (ExecutionException | TimeoutException ignored) {
			// The runnable logs its own failures, and a timeout leaves nothing to do but quit.
		}
	}
}
