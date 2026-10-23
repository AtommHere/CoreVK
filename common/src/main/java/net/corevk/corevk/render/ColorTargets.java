package net.corevk.corevk.render;

import net.corevk.corevk.pack.model.BlendMode;
import net.corevk.corevk.pack.model.ImageInformation;
import net.corevk.corevk.pack.model.TargetFormat;
import net.corevk.corevk.pack.model.TargetName;
import net.corevk.corevk.pack.model.TargetSize;
import net.corevk.corevk.pack.model.TextureStage;
import net.corevk.corevk.pack.target.PackDirectives;
import net.corevk.corevk.pack.target.SamplerPlan;
import net.corevk.corevk.pack.target.TargetDirectives;
import net.corevk.corevk.pack.target.TargetPlan;
import net.corevk.corevk.pack.target.TargetSchedule;
import net.corevk.corevk.pack.texture.CustomStorage;
import net.corevk.corevk.render.storage.StorageBuffers;
import net.corevk.corevk.render.storage.StorageImages;
import net.corevk.corevk.uniform.ClipSpace;
import net.corevk.corevk.uniform.NoiseTexture;
import net.corevk.corevk.CoreVK;

import com.mojang.blaze3d.GpuDeviceLossException;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import org.joml.Vector4f;
import org.joml.Vector4fc;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

final class ColorTargets {

	private static final GpuFormat CONSTANT_FORMAT = GpuFormat.RGBA8_UNORM;

	private static final GpuFormat COVERAGE_FORMAT = GpuFormat.R32_FLOAT;

	static final float COVERAGE_EMPTY = ClipSpace.REVERSED.z < 0.0F ? -1.0F : 2.0F;

	private static final int FOG_TARGET = 0;

	private static final Vector4fc OPAQUE_BLACK = new Vector4f(0.0F, 0.0F, 0.0F, 1.0F);
	private static final Vector4fc OPAQUE_WHITE = new Vector4f(1.0F, 1.0F, 1.0F, 1.0F);
	private static final Vector4fc MID_GREY = new Vector4f(0.5F, 0.5F, 0.5F, 1.0F);

	private static final Vector4fc UNWRITTEN =
			new Vector4f(COVERAGE_EMPTY, COVERAGE_EMPTY, COVERAGE_EMPTY, COVERAGE_EMPTY);

	private static final long LOUD_BYTES = 512L * 1024L * 1024L;

	private static final int ASSUMED_COLOR_ATTACHMENTS = 8;

	private static final String CLEAR_LABEL = "CoreVK pending colour clears";

	record PackBinding(GpuTextureView view, FilterMode filter, boolean repeat, boolean mipmaps) {
	}

	TargetSchedule schedule() {
		return this.plan.schedule();
	}

	record PackSource(PackImages.Image image, FilterMode filter, boolean repeat, boolean mipmaps) {
	}

	private final TargetPlan plan;
	private final Set<Integer> doubled;
	private final int noiseResolution;

	private final NoiseTexture.Image noiseImage;

	private final PackImages packImages;

	private final StorageImages storageImages;
	private final StorageBuffers storageBuffers;

	private final Map<PackImages.Image, TargetSurface> packSurfaces = new LinkedHashMap<>();

	private final Map<GpuFormat, Boolean> filterable = new ConcurrentHashMap<>();

	private final ShadowTargets shadowMap;

	private final PackDepth depth = new PackDepth();

	private final TargetCopies copies = new TargetCopies();

	private final CenterDepth centerDepth = new CenterDepth();

	private final MotionVectors motionVectors = new MotionVectors();

	private final Map<Integer, GpuFormat> formats = new LinkedHashMap<>();

	private final Map<Integer, FilterMode> filters = new LinkedHashMap<>();
	private final Map<Integer, Vector4fc> clearColours = new LinkedHashMap<>();

	private final boolean fogCleared;
	private final Map<Integer, TargetSurface> mainSide = new LinkedHashMap<>();
	private final Map<Integer, TargetSurface> altSide = new LinkedHashMap<>();
	private final Set<Integer> fellBack = new TreeSet<>();
	private final List<String> notes = new ArrayList<>();

	private final Set<Integer> mipmapped;

	private final Map<String, Set<Integer>> lodReads;

	private TargetSurface black;
	private TargetSurface white;
	private TargetSurface grey;
	private TargetSurface noise;
	private TargetSurface unwritten;

	private TargetSurface coverage;

	private int screenWidth;
	private int screenHeight;
	private boolean clearOwed;

	private boolean fullDeferOwed;

	private Set<Integer> storageTargets = Set.of();

	private final Set<Integer> storageRefused = new HashSet<>();

	private boolean broken;

	private final Map<GpuTexture, Vector4fc> pendingClears = new IdentityHashMap<>();

	private long announcedMegabytes = -1L;

	private int brokenWidth;
	private int brokenHeight;

	private boolean brokenForGood;

	ColorTargets(TargetPlan plan, int noiseResolution, NoiseTexture.Image noiseImage,
			PackImages packImages, ImageInformation.Reading storageImages, int shadowResolution,
			List<PackDirectives.ShadowColour> shadowColours,
			List<PackDirectives.ShadowDepth> shadowDepths) {
		this.plan = plan;
		this.packImages = packImages;
		this.storageImages = new StorageImages(storageImages);
		this.storageBuffers = new StorageBuffers(CustomStorage.reading());

		this.noiseResolution = noiseResolution;

		this.noiseImage = noiseImage;

		this.shadowMap = new ShadowTargets(shadowResolution, shadowColours, shadowDepths,
				plan.shadowAllocated(), plan.shadowCeiling());
		this.doubled = Set.copyOf(plan.schedule().doubled());

		TargetDirectives directives = plan.directives();
		for (int index : plan.ordered()) {
			TargetDirectives.Colour colour = directives.clearColour(index);
			TargetFormat format = directives.format(index).used();
			this.formats.put(index, GpuFormats.of(format));
			this.filters.put(index, GpuFormats.filterFor(format));
			this.clearColours.put(index, new Vector4f(colour.r(), colour.g(), colour.b(), colour.a()));
		}

		this.fogCleared = !directives.declaresClearColour(FOG_TARGET);

		this.mipmapped = directives.mipmapped().stream()
				.filter(this.formats::containsKey)
				.collect(Collectors.toCollection(TreeSet::new));
		Map<String, Set<Integer>> lodReads = new LinkedHashMap<>();
		directives.mipmapRequests().forEach((program, asked) -> lodReads.put(program,
				asked.stream().filter(this.mipmapped::contains)
						.collect(Collectors.toCollection(TreeSet::new))));
		this.lodReads = Map.copyOf(lodReads);

		List<Integer> nowhere = this.doubled.stream()
				.filter(index -> !this.formats.containsKey(index))
				.toList();
		if (!nowhere.isEmpty()) {
			note("targets the schedule turns over and that no program of this place writes or "
					+ "samples, so no texture exists for either half: " + nowhere);
		}
	}

	boolean ensure(int screenWidth, int screenHeight) {

		if (screenWidth <= 0 || screenHeight <= 0) {
			return false;
		}

		if (this.broken && !this.brokenForGood
				&& (screenWidth != this.brokenWidth || screenHeight != this.brokenHeight)) {
			this.broken = false;
		}

		if (this.broken) {
			return false;
		}

		this.screenWidth = screenWidth;
		this.screenHeight = screenHeight;

		boolean changed = false;
		try {
			changed = ensureConstants();
			changed |= ensureCoverage(screenWidth, screenHeight);

			this.shadowMap.ensure();
			this.storageImages.ensure(screenWidth, screenHeight);
			this.storageBuffers.ensure(screenWidth, screenHeight);
			for (int index : this.plan.ordered()) {

				TargetSize size = this.plan.directives().size(index);
				int width = size.width(screenWidth);
				int height = size.height(screenHeight);

				changed |= ensureSide(this.mainSide, index, width, height, "");
				if (this.doubled.contains(index)) {
					changed |= ensureSide(this.altSide, index, width, height, " alt");
				}
			}

			this.copies.ensure(this::target);
		} catch (GpuDeviceLossException e) {
			throw e;
		} catch (RuntimeException e) {
			this.broken = true;
			this.brokenWidth = screenWidth;
			this.brokenHeight = screenHeight;
			this.brokenForGood = this.storageImages.refusedForGood();

			note(this.plan.packName() + " could not allocate its colour targets: " + e.getMessage());
			CoreVK.logger().error("CoreVK could not allocate the colour targets of {} at {}x{}, so "
					+ "nothing is drawn {}", this.plan.packName(), screenWidth, screenHeight,
					this.brokenForGood ? "at any size" : "until the screen is another size", e);

			return false;
		}

		if (changed) {

			this.clearOwed = true;
			announceSize();
		}

		return true;
	}

	boolean usable() {
		return !this.broken && this.screenWidth > 0 && this.screenHeight > 0;
	}

	boolean refusedForGood() {
		return this.brokenForGood;
	}

	void clear(CommandEncoder encoder, Vector4fc fog) {

		this.fullDeferOwed &= !this.pendingClears.isEmpty();

		boolean full = this.clearOwed || this.fullDeferOwed;

		if (this.clearOwed) {
			clear(encoder, this.black, OPAQUE_BLACK);
			clear(encoder, this.white, OPAQUE_WHITE);
			clear(encoder, this.grey, MID_GREY);
			clear(encoder, this.unwritten, UNWRITTEN);
			uploadNoise(encoder);
			this.packSurfaces.forEach((image, surface) -> upload(encoder, surface, image.pixels()));

			for (int index : this.plan.ordered()) {
				Vector4fc colour = this.fogCleared && index == FOG_TARGET
						? fog
						: this.clearColours.get(index);
				clear(encoder, this.mainSide.get(index), colour);
				clear(encoder, this.altSide.get(index), colour);
			}
		}

		this.pendingClears.clear();

		defer(this.coverage, UNWRITTEN);

		for (int index : this.plan.ordered()) {

			if (!full && !this.plan.directives().clears(index)) {
				continue;
			}

			Vector4fc colour = this.fogCleared && index == FOG_TARGET
					? fog
					: this.clearColours.get(index);
			defer(this.mainSide.get(index), colour);
			defer(this.altSide.get(index), colour);
		}

		this.fullDeferOwed = full;
		this.clearOwed = false;
	}

	Optional<Vector4fc> takeClear(GpuTextureView view) {
		if (view == null) {
			return Optional.empty();
		}

		Vector4fc colour = this.pendingClears.remove(view.texture());
		return colour == null ? Optional.empty() : Optional.of(colour);
	}

	boolean hasPendingClears() {
		return !this.pendingClears.isEmpty();
	}

	private record Pending(GpuTextureView view, Vector4fc colour) {
	}

	void flushPending(CommandEncoder encoder) {
		if (this.pendingClears.isEmpty()) {
			return;
		}

		List<Pending> pending = new ArrayList<>();
		collectPending(this.coverage, pending);
		for (TargetSurface surface : this.mainSide.values()) {
			collectPending(surface, pending);
		}

		for (TargetSurface surface : this.altSide.values()) {
			collectPending(surface, pending);
		}

		if (pending.isEmpty()) {
			return;
		}

		LinkedHashMap<Long, List<Pending>> bySize = new LinkedHashMap<>();
		for (Pending one : pending) {
			GpuTexture texture = one.view().texture();
			long key = ((long) texture.getWidth(0) << 32) | (texture.getHeight(0) & 0xFFFFFFFFL);
			bySize.computeIfAbsent(key, ignored -> new ArrayList<>()).add(one);
		}

		int perPass = maxColorAttachments();
		for (List<Pending> group : bySize.values()) {
			int width = group.get(0).view().texture().getWidth(0);
			int height = group.get(0).view().texture().getHeight(0);
			for (int from = 0; from < group.size(); from += perPass) {
				int to = Math.min(from + perPass, group.size());
				PassDescriptor descriptor = PassDescriptor.create(() -> CLEAR_LABEL);
				for (int index = from; index < to; index++) {
					Pending one = group.get(index);
					descriptor.withColorAttachment(one.view(), Optional.of(one.colour()));
				}

				descriptor.withRenderArea(new RenderPass.RenderArea(0, 0, width, height));
				encoder.createRenderPass(descriptor.build()).close();
			}
		}
	}

	private void collectPending(TargetSurface surface, List<Pending> into) {
		GpuTexture texture = surface == null ? null : surface.texture();
		if (texture == null) {
			return;
		}

		Vector4fc colour = this.pendingClears.remove(texture);
		if (colour != null) {
			into.add(new Pending(surface.view(), colour));
		}
	}

	private static int maxColorAttachments() {
		GpuDevice device = RenderSystem.tryGetDevice();

		return device == null
				? ASSUMED_COLOR_ATTACHMENTS
				: Math.max(1, device.getDeviceInfo().limits().maxColorAttachments());
	}

	void storageTargets(Set<Integer> targets) {
		this.storageTargets = Set.copyOf(targets);
	}

	void copyBack(CommandEncoder encoder, List<Integer> targets) {
		for (int index : targets) {
			TargetSurface alt = this.altSide.get(index);
			TargetSurface main = this.mainSide.get(index);
			if (alt == null || main == null) {
				note("nothing to copy back for " + TargetName.canonical(index) + ": the plan asks "
						+ "for it and only one half of it exists");
				continue;
			}

			GpuTexture from = alt.texture();
			GpuTexture to = main.texture();
			if (from != null && to != null) {

				encoder.copyTextureToTexture(from, to, 0, 0, 0, 0, 0, main.width(), main.height());
			}
		}
	}

	PackDepth depth() {
		return this.depth;
	}

	TargetCopies copies() {
		return this.copies;
	}

	CenterDepth centerDepth() {
		return this.centerDepth;
	}

	MotionVectors motionVectors() {
		return this.motionVectors;
	}

	GpuTextureView view(int index, TargetSchedule.Side side) {
		TargetSurface surface = target(index, side);

		return surface == null ? null : surface.view();
	}

	GpuTexture texture(int index, TargetSchedule.Side side) {
		TargetSurface surface = target(index, side);

		return surface == null ? null : surface.texture();
	}

	TargetSurface surface(int index, TargetSchedule.Side side) {
		return target(index, side);
	}

	BlendMode blend(String program) {
		return this.plan.blend(program).orElse(null);
	}

	BlendMode blend(String program, int rank) {
		return this.plan.blend(program, rank).orElse(null);
	}

	Set<Integer> lodReads(String program) {
		return this.lodReads.getOrDefault(program, Set.of());
	}

	GpuFormat format(int index) {
		return this.formats.get(index);
	}

	GpuFormat shadowFormat(int index) {
		return this.shadowMap.format(index);
	}

	int screenWidth() {
		return this.screenWidth;
	}

	int screenHeight() {
		return this.screenHeight;
	}

	FilterMode filter(int index) {
		return this.filters.getOrDefault(index, FilterMode.NEAREST);
	}

	boolean has(int index) {
		return this.formats.containsKey(index);
	}

	GpuTextureView black() {
		return this.black == null ? null : this.black.view();
	}

	GpuTextureView unwritten() {
		return this.unwritten == null ? null : this.unwritten.view();
	}

	GpuTextureView white() {
		return this.white == null ? null : this.white.view();
	}

	GpuTextureView grey() {
		return this.grey == null ? null : this.grey.view();
	}

	GpuTextureView noise() {
		return this.noise == null ? grey() : this.noise.view();
	}

	GpuTextureView coverage() {
		return this.coverage == null ? null : this.coverage.view();
	}

	GpuFormat coverageFormat() {
		return COVERAGE_FORMAT;
	}

	ShadowTargets shadow() {
		return this.shadowMap;
	}

	long bytes() {
		return sum(TargetSurface::bytes);
	}

	private long baseBytes() {
		return sum(TargetSurface::baseBytes);
	}

	private long sum(ToLongFunction<TargetSurface> cost) {
		long total = 0L;
		for (TargetSurface surface : this.mainSide.values()) {
			total += cost.applyAsLong(surface);
		}

		for (TargetSurface surface : this.altSide.values()) {
			total += cost.applyAsLong(surface);
		}

		return total;
	}

	List<String> notes() {
		return List.copyOf(this.notes);
	}

	void clearStorage(CommandEncoder encoder) {
		this.storageImages.clearMarked(encoder);
	}

	void reanchorStorage(CommandEncoder encoder, int dx, int dy, int dz) {
		this.storageImages.reanchor(encoder, dx, dy, dz);
	}

	void release() {
		this.mainSide.values().forEach(TargetSurface::close);
		this.altSide.values().forEach(TargetSurface::close);
		this.mainSide.clear();
		this.altSide.clear();

		this.packSurfaces.values().forEach(TargetSurface::close);
		this.packSurfaces.clear();
		this.filterable.clear();
		this.storageImages.close();
		this.storageBuffers.close();

		this.black = release(this.black);
		this.white = release(this.white);
		this.grey = release(this.grey);
		this.noise = release(this.noise);
		this.unwritten = release(this.unwritten);
		this.coverage = release(this.coverage);
		this.shadowMap.release();
		this.depth.release();
		this.copies.release();
		this.centerDepth.release();
		this.motionVectors.release();
		this.pendingClears.clear();

		this.announcedMegabytes = -1L;
	}

	private boolean ensureConstants() {
		if (this.black != null) {
			return false;
		}

		ConstantTextures.of(RenderSystem.getDevice());

		this.black = new TargetSurface("CoreVK black", CONSTANT_FORMAT, false, 1, 1);
		this.white = new TargetSurface("CoreVK white", CONSTANT_FORMAT, false, 1, 1);
		this.grey = new TargetSurface("CoreVK grey", CONSTANT_FORMAT, false, 1, 1);

		this.unwritten = new TargetSurface("CoreVK unwritten mask", COVERAGE_FORMAT, false, 1, 1);

		if (this.noiseImage != null) {
			this.noise = new TargetSurface("CoreVK noise", CONSTANT_FORMAT, false,
					this.noiseImage.width(), this.noiseImage.height());
			CoreVK.logger().info("noisetex is the pack's own image, {}x{}",
					this.noiseImage.width(), this.noiseImage.height());
		} else {
			int resolution = this.noiseResolution;
			this.noise = new TargetSurface("CoreVK noise", CONSTANT_FORMAT, false, resolution,
					resolution);
			CoreVK.logger().info("noisetex is the generated field at {}x{}", resolution, resolution);
		}

		ensurePackTextures();

		return true;
	}

	private boolean ensureCoverage(int width, int height) {
		if (this.coverage == null) {
			this.coverage = new TargetSurface("CoreVK terrain coverage", COVERAGE_FORMAT, false,
					width, height);

			return true;
		}

		return this.coverage.resize(width, height);
	}

	private void ensurePackTextures() {
		List<PackImages.Image> images = this.packImages.images();
		if (images.isEmpty()) {
			return;
		}

		for (PackImages.Image image : images) {
			CoreVK.logger().info("The pack supplies {}", PackImages.describe(image));

			if (image.live()) {
				continue;
			}

			if (image.texture().blur() && !filterable(image.format())) {
				note(image.texture().sampler() + " is read at the nearest texel, this device not "
						+ "filtering " + image.format() + " linearly");
			}

			try {
				this.packSurfaces.put(image, new TargetSurface(
						"CoreVK " + image.texture().sampler(), image.format(), false, image.width(),
						image.height()));
			} catch (GpuDeviceLossException e) {
				throw e;
			} catch (RuntimeException e) {
				note(image.texture().sampler() + " could not be allocated at " + image.width() + "x"
						+ image.height() + ", so it reads one black pixel: " + e.getMessage());
				CoreVK.logger().warn("CoreVK could not allocate {} at {}x{}, so it reads one black pixel",
						image.texture().sampler(), image.width(), image.height(), e);
			}
		}

		long bytes = this.packImages.bytes();
		CoreVK.logger().info("{} textures of the pack's own, {} MiB once decoded", images.size(),
				megabytes(bytes));

		if (this.packImages.loud()) {

			CoreVK.logger().warn("The pack's own textures take {} MiB of memory, far more than the "
					+ "files they were read from", megabytes(bytes));
		}
	}

	PackBinding packTexture(TextureStage stage, String sampler) {
		PackSource source = packSource(stage, sampler);
		GpuTextureView view = source == null ? null : packView(source.image());

		return view == null ? null
				: new PackBinding(view, source.filter(), source.repeat(), source.mipmaps());
	}

	PackSource packSource(TextureStage stage, String sampler) {
		PackImages.Image image = this.packImages.find(stage, sampler);
		if (image == null) {
			return null;
		}

		boolean flat = !sampler.equals(SamplerPlan.behind(sampler));

		if (image.live()) {
			return new PackSource(image, FilterMode.NEAREST, !flat, true);
		}

		return new PackSource(image, filterFor(image), !flat && !image.texture().clamp(), false);
	}

	private FilterMode filterFor(PackImages.Image image) {
		return image.texture().blur() && filterable(image.format())
				? FilterMode.LINEAR
				: FilterMode.NEAREST;
	}

	private boolean filterable(GpuFormat format) {
		return this.filterable.computeIfAbsent(format, one -> {
			boolean answer = GpuFormats.filtersLinearly(one);
			if (!answer) {
				CoreVK.logger().warn("This device does not filter {} linearly, so what the pack "
						+ "supplies in it is read at the nearest texel where its directive asked for "
						+ "a blend", one);
			}

			return answer;
		});
	}

	GpuTextureView packView(PackImages.Image image) {
		if (image.live()) {
			return GameImages.held(PackImages.location(image.texture().path()));
		}

		TargetSurface surface = this.packSurfaces.get(image);

		return surface == null ? null : surface.view();
	}

	private void uploadNoise(CommandEncoder encoder) {
		byte[] pixels = this.noiseImage != null
				? this.noiseImage.rgba()
				: NoiseTexture.rgba(this.noise.width());
		upload(encoder, this.noise, pixels);
	}

	private static void upload(CommandEncoder encoder, TargetSurface surface, byte[] pixels) {
		ByteBuffer data = ByteBuffer.allocateDirect(pixels.length).order(ByteOrder.nativeOrder());
		data.put(pixels).flip();

		encoder.writeToTexture(surface.texture(), data, 0, 0, 0, 0, surface.width(),
				surface.height());
	}

	private boolean ensureSide(Map<Integer, TargetSurface> side, int index, int width, int height, String suffix) {
		TargetSurface surface = side.get(index);
		if (surface != null && surface.width() == width && surface.height() == height) {
			return false;
		}

		String name = TargetName.canonical(index) + suffix;
		boolean mipped = this.mipmapped.contains(index);
		if (surface == null) {
			GpuFormat format = this.formats.get(index);
			TargetDirectives directives = this.plan.directives();
			boolean storage = this.storageTargets.contains(index) && GpuFormats.storageCapable(format);
			if (this.storageTargets.contains(index) && !storage && this.storageRefused.add(index)) {
				CoreVK.logger().warn("{} is stored into as an image by a program of the pack, and "
						+ "this device makes no storage image of {}, so what stores into it is not run",
						name, format);
			}

			CoreVK.logger().info("Allocating {} as {} at {}x{}, {} level(s), declared {} at {}{}", name,
					format, width, height, TargetSurface.levelsFor(mipped, width, height),
					directives.format(index).declared(), directives.formatSource(index),
					storage ? ", writable as a storage image" : "");
			side.put(index, new TargetSurface("CoreVK " + name, format, mipped, storage, width,
					height));
		} else {
			surface.resize(width, height);
		}

		return true;
	}

	private void announceSize() {
		long bytes = bytes();
		long base = baseBytes();
		long single = this.plan.bytesAt(this.screenWidth, this.screenHeight, Set.of());

		long total = megabytes(bytes);
		if (total == this.announcedMegabytes) {
			return;
		}

		this.announcedMegabytes = total;
		CoreVK.logger().info("Colour targets of {} sized for {}x{}: {} targets, {} MiB",
				this.plan.packName(), this.screenWidth, this.screenHeight, this.mainSide.size(),
				megabytes(bytes));

		if (!this.doubled.isEmpty()) {
			CoreVK.logger().info("{} targets doubled: {}, {} MiB instead of {}",
					this.doubled.size(), this.doubled, megabytes(base), megabytes(single));
		}

		if (!this.mipmapped.isEmpty()) {
			CoreVK.logger().info("{} targets carry a mip chain because a program reads them at a "
					+ "lod: {}, {} MiB more", this.mipmapped.size(), this.mipmapped,
					megabytes(bytes - base));
		}

		if (bytes > LOUD_BYTES) {

			CoreVK.logger().warn("{} takes {} MiB of colour targets at {}x{}, {} of them for the "
					+ "second halves of {} and {} for the mip chains", this.plan.packName(),
					megabytes(bytes), this.screenWidth, this.screenHeight, megabytes(base - single),
					this.doubled, megabytes(bytes - base));
		}
	}

	private static long megabytes(long bytes) {
		return bytes / (1024L * 1024L);
	}

	private TargetSurface target(int index, TargetSchedule.Side side) {

		if (side == TargetSchedule.Side.ALT) {
			TargetSurface alt = this.altSide.get(index);
			if (alt != null) {
				return alt;
			}

			if (this.formats.containsKey(index) && this.fellBack.add(index)) {
				CoreVK.logger().warn("{} was read from its alternate half and only one half of it "
						+ "exists, so both sides of the ping pong are the same texture",
						TargetName.canonical(index));
			}
		}

		return this.mainSide.get(index);
	}

	private void note(String text) {
		if (!this.notes.contains(text)) {
			this.notes.add(text);
		}
	}

	private void defer(TargetSurface surface, Vector4fc colour) {
		GpuTexture texture = surface == null ? null : surface.texture();
		if (texture != null) {
			this.pendingClears.put(texture, colour);
		}
	}

	private static void clear(CommandEncoder encoder, TargetSurface surface, Vector4fc colour) {
		GpuTexture texture = surface == null ? null : surface.texture();
		if (texture != null) {
			encoder.clearColorTexture(texture, colour);
		}
	}

	private static TargetSurface release(TargetSurface surface) {
		if (surface != null) {
			surface.close();
		}

		return null;
	}
}
