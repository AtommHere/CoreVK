package net.corevk.corevk.render;

import net.corevk.corevk.dh.DhLods;
import net.corevk.corevk.glsl.PackProgram;
import net.corevk.corevk.pack.model.ProgramNames;
import net.corevk.corevk.pack.model.RenderStage;
import net.corevk.corevk.pack.model.TargetName;
import net.corevk.corevk.pack.option.OptionValue;
import net.corevk.corevk.pack.program.TerrainPass;
import net.corevk.corevk.pack.source.OpenedPack;
import net.corevk.corevk.pack.target.ChainPlan;
import net.corevk.corevk.pack.target.SamplerPlan;
import net.corevk.corevk.pack.target.TargetDirectives;
import net.corevk.corevk.pack.target.TargetPlan;
import net.corevk.corevk.pack.target.TargetSchedule;
import net.corevk.corevk.pack.texture.CustomImages;
import net.corevk.corevk.render.storage.StorageImages;
import net.corevk.corevk.render.timing.PassTimings;
import net.corevk.corevk.HostReport;
import net.corevk.corevk.ScreenText;
import net.corevk.corevk.uniform.ClipSpace;
import net.corevk.corevk.uniform.WorldState;
import net.corevk.corevk.CoreVK;

import com.mojang.blaze3d.GpuDeviceLossException;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.joml.Vector3dc;
import org.joml.Vector3i;
import org.joml.Vector4f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.TreeSet;

public final class PackChain {

	private static final AtomicInteger LOADS = new AtomicInteger();

	private static final float[] QUAD = {
			0.0F, 0.0F, 0.0F, 0.0F, 0.0F,
			1.0F, 0.0F, 0.0F, 1.0F, 0.0F,
			1.0F, 1.0F, 0.0F, 1.0F, 1.0F,
			0.0F, 0.0F, 0.0F, 0.0F, 0.0F,
			1.0F, 1.0F, 0.0F, 1.0F, 1.0F,
			0.0F, 1.0F, 0.0F, 0.0F, 1.0F };

	private static final String BLOCK_LABEL = "CoreVK OfGlobals";
	private static final String QUAD_LABEL = "CoreVK quad";

	private static volatile PackChain active;

	static GpuTextureView motionVectors() {
		PackChain chain = active;

		return chain == null || chain.targets == null
				? null
				: chain.targets.motionVectors().view();
	}

	private static volatile boolean disabled;

	private static volatile boolean chainWanted = true;

	static void activate(PackChain chain) {
		active = chain;
	}

	static PackChain takeDown() {
		PackChain previous = active;
		active = null;
		disabled = false;

		return previous;
	}

	static void stop() {
		disabled = true;

		FaceShading.none();
	}

	static boolean stopped() {
		return disabled;
	}

	static void chainWanted(boolean wanted) {
		chainWanted = wanted;
	}

	private boolean advanced;

	private boolean opened;

	private final Vector3i voxelAnchor = new Vector3i();

	private boolean voxelAnchored;

	private boolean everAdvanced;

	private boolean begun;

	private boolean prepared;

	private boolean early;

	private boolean filled;

	private boolean seeded;

	private boolean sceneDepth;

	private boolean split;

	private boolean saidBeforeClear;
	private boolean saidAfterTheWorld;

	private final PackProgram.Chain chain;
	private final PackValues values;

	private final Path packPath;
	private final Map<String, OptionValue> chosen;
	private final String profile;
	private final String world;
	private final ColorTargets targets;

	private final Set<TargetSurface> currentChains = new HashSet<>();

	private final Vector4f fogClear = new Vector4f(0.0F, 0.0F, 0.0F, 1.0F);

	private final SceneSeed seed;

	private final ChainPresent present;
	private final boolean seedEnabled;

	private final FeatureLayer features;
	private final int load;
	final TerrainDraw terrain;
	private final SkyDraw sky;
	private final EntityDraw entities;
	private final CloudDraw clouds;
	private final WeatherDraw weather;
	private final ParticleDraw particles;

	private final DistantDraw distant;

	private final List<FamilyDraw> families;

	private final PackCompute compute;

	private List<PackPass> programs;
	private PackPass last;

	private List<Standalone> standalone = List.of();

	private boolean centerDepthRead;

	private MappableRingBuffer block;
	private GpuBuffer quad;
	private CompiledRenderPipeline head;
	private int blockBytes;
	private int warmed;

	private final Object familyMaps = new Object();

	private final FamilyWarmup warmup;

	private boolean geometryReady;
	private boolean announced;

	private boolean redirected;

	PackChain(PackProgram.Chain chain, PackValues values, String world, boolean seedEnabled,
			OpenedPack opened, Path packPath, Map<String, OptionValue> chosen, String profile) {

		ShadowAmortisation.forget();
		this.chain = chain;
		this.values = values;
		this.packPath = packPath;
		this.chosen = Map.copyOf(chosen);
		this.profile = profile;
		this.world = world;
		this.seedEnabled = seedEnabled;
		this.load = LOADS.incrementAndGet();

		this.targets = new ColorTargets(chain.targets(), values.noiseResolution(),
				values.noiseImage(), values.packImages(), values.storageImages(),
				values.shadowResolution(), values.shadowColours(), values.shadowDepths());

		this.seed = chain.chain().seed()
				.filter(where -> this.targets.has(where.target()))
				.filter(where -> !holdsIntegers(where.target()))
				.map(where -> new SceneSeed(where, this.targets.format(where.target()),
						seedExtras(chain, where)))
				.orElse(null);

		this.present = chain.chain().present()
				.filter(where -> this.targets.has(where.target()))
				.map(ChainPresent::new)
				.orElse(null);

		this.features = featuresInto()
				.filter(into -> !holdsIntegers(into.target()))
				.map(into -> new FeatureLayer(into, this.targets.format(into.target())))
				.orElse(null);

		this.terrain = new TerrainDraw(this, packPath, chain.place(), values,
				this.load, chain.chain(), chain.targets(), chainWanted, this.targets);

		this.sky = new SkyDraw(this, packPath, chain.place(), chosen, profile, values, this.load,
				chain.chain(), chain.targets(), chainWanted, this.targets);

		this.entities = new EntityDraw(this, packPath, chain.place(), chosen, profile, values,
				this.load, chain.chain(), chain.targets(), chainWanted,
				seedEnabled && this.seed != null, this.targets);

		this.clouds = new CloudDraw(this, packPath, chain.place(), chosen, profile, values,
				this.load, chain.chain(), chain.targets(), chainWanted, this.targets);

		this.weather = new WeatherDraw(this, packPath, chain.place(), chosen, profile, values,
				this.load, chain.chain(), chain.targets(), chainWanted, this.targets);

		this.particles = new ParticleDraw(this, packPath, chain.place(), chosen, profile, values,
				this.load, chain.chain(), chain.targets(), chainWanted, this.targets);

		this.distant = new DistantDraw(this, packPath, chain.place(), chosen, profile, values,
				this.load, chain.chain(), chain.targets(), chainWanted, this.targets);
		this.families = List.of(this.sky, this.entities, this.clouds, this.weather,
				this.particles, this.distant);
		this.warmup = new FamilyWarmup(this.families, this.packPath, this.chosen, this.profile,
				this.familyMaps);

		this.compute = PackCompute.load(opened, chain.place(), chain.targets().computes(), this.load,
				values.shadowGeometryCatalog(), values.catalog(),
				ordered(chain.chain()).stream().map(ChainPlan.Pass::program)
						.collect(Collectors.toSet()),
				Set.copyOf(chain.targets().passing()));

		Set<Integer> stored = new LinkedHashSet<>(this.compute.storageTargets());
		stored.addAll(chain.targets().stored());
		for (ChainPlan.Pass pass : ordered(chain.chain())) {
			PackProgram.Loaded loaded = chain.programs().get(pass.program());
			if (loaded != null) {
				stored.addAll(PackPass.colourImages(loaded));
			}
		}
		this.targets.storageTargets(stored);
	}

	private Optional<ChainPlan.Attachment> featuresInto() {
		return this.chain.chain().geometry(TerrainPass.TRANSLUCENT)
				.map(ChainPlan.Pass::attachments)
				.filter(attachments -> !attachments.isEmpty())
				.map(attachments -> attachments.get(0))
				.filter(into -> this.targets.has(into.target()));
	}

	private boolean holdsIntegers(int target) {
		return this.chain.targets().directives().format(target).used().integer();
	}

	private static String stillTheGame() {
		List<String> carried = new ArrayList<>();
		if (!EntityDraw.wanted()) {
			carried.add("the entities");
		}

		if (!HandDraw.wanted()) {

			carried.add("the hand");
		}

		if (!SkyDraw.wanted()) {
			carried.add("the sky");
		}

		if (!CloudDraw.wanted()) {
			carried.add("the clouds");
		}

		if (!WeatherDraw.wanted()) {
			carried.add("the weather");
		}

		if (!ParticleDraw.wanted()) {
			carried.add("the particles");
		}

		if (EntityDraw.wanted()) {

			carried.add("the beacon beam and the lightning");
		}

		if (carried.size() < 2) {
			return String.join("", carried);
		}

		return String.join(", ", carried.subList(0, carried.size() - 1)) + " and "
				+ carried.get(carried.size() - 1);
	}

	private List<SceneSeed.Extra> seedExtras(PackProgram.Chain chain, ChainPlan.Seed where) {
		List<ChainPlan.Attachment> attachments = chain.chain()
				.geometryOf(where.from(), false)
				.map(ChainPlan.Pass::attachments)
				.orElse(List.of());
		if (attachments.size() < 2 || attachments.get(0).target() != where.target()
				|| attachments.get(0).side() != where.side()) {
			return List.of();
		}

		TargetDirectives directives = chain.targets().directives();
		List<SceneSeed.Extra> extras = new ArrayList<>();
		for (ChainPlan.Attachment attachment : attachments.subList(1, attachments.size())) {
			int index = attachment.target();
			if (!this.targets.has(index) || !directives.clears(index)
					|| directives.format(index).used().integer()
					|| (index == 0 && !directives.declaresClearColour(index))) {
				continue;
			}

			TargetDirectives.Colour empty = directives.clearColour(index);
			extras.add(new SceneSeed.Extra(index, attachment.side(), this.targets.format(index),
					new Vector4f(empty.r(), empty.g(), empty.b(), empty.a())));
		}

		return extras;
	}

	public static boolean drawingPack() {
		return active != null && !disabled;
	}

	void putAway(String why) {
		if (disabled) {
			return;
		}

		disabled = true;

		FaceShading.none();
		PackChoice.error(this.chain.packName() + " is not drawn at all: " + why);
		CoreVK.logger().error("{} is put away rather than drawn by halves, because {}",
				this.chain.packName(), why);
	}

	public static boolean beforeLevel() {

		if (HostReport.otherBackend()) {
			return false;
		}

		sayBeforeLevelRoad();

		return RELOAD_BEFORE_LEVEL
				&& PackChoice.reloadIfTheWorldMoved(CoreVK.platform().gameDirectory())
				&& drawingPack();
	}

	public static boolean draw() {
		PackChoice.reloadIfTheWorldMoved(CoreVK.platform().gameDirectory());

		PackChain chain = active;
		if (disabled || chain == null) {

			if (chain != null) {
				chain.closeFrame();
			}

			return false;
		}

		try {
			if (chainWanted) {
				chain.run();
			} else {

				chain.beginFrame();
			}
		} catch (GpuDeviceLossException e) {
			throw e;
		} catch (RuntimeException e) {
			stop();
			CoreVK.logger().error("CoreVK stopped drawing this pack after an error", e);
			chain.release();
		}

		chain.closeFrame();
		if (chainWanted && chain.drawable()) {
			PassTimings.finishCensus();
		}

		return chainWanted;
	}

	public static boolean warming() {
		PackChain chain = active;
		if (disabled || !chainWanted || chain == null) {
			return false;
		}

		if (chain.programs != null && chain.programs.isEmpty() && chain.standalone.isEmpty()) {
			return false;
		}

		return !chain.drawable();
	}

	public static boolean compiling() {
		PackChain chain = active;
		if (disabled || !chainWanted || chain == null) {
			return false;
		}

		return !chain.warmup.warmed() && (warming() || chain.drawable());
	}

	public static Optional<Component> compilingWords() {
		PackChain chain = active;
		if (!compiling() || chain == null) {
			return Optional.empty();
		}

		return Optional.of(compilingLabel(chain));
	}

	private static Component compilingLabel(PackChain chain) {
		Component words = Component.translatable(ScreenText.COMPILING);
		int total = chain.warmup.total();
		if (total > 0) {
			words = words.copy()
					.append(" " + Math.min(chain.warmup.walked(), total) + "/" + total);
		}

		return words;
	}

	public static long warmedForMillis() {
		PackChain chain = active;
		if (disabled || !chainWanted || chain == null || !chain.warmup.warmed()
				|| !chain.drawable()) {
			return -1L;
		}

		return Util.getMillis() - chain.warmup.warmedAt();
	}

	public static int loadNumber() {
		PackChain chain = active;

		return chain == null ? 0 : chain.load;
	}

	public static int liveLoad() {
		PackChain chain = active;

		return disabled || chain == null ? 0 : chain.load;
	}

	public static void pumpWarmup() {
		PackChain chain = active;
		GpuDevice device = RenderSystem.tryGetDevice();
		Minecraft minecraft = Minecraft.getInstance();
		if (disabled || !chainWanted || chain == null || device == null || minecraft == null) {
			return;
		}

		if (chain.drawable()) {
			return;
		}

		RenderTarget main = minecraft.gameRenderer.mainRenderTarget();
		if (main == null || main.getColorTexture() == null) {
			return;
		}

		long deadline = System.nanoTime() + WARM_BUDGET_NANOS;
		try {
			do {
				if (disabled) {
					return;
				}

				if (chain.programs == null) {
					chain.build(device);
				}

				if (!chain.prepare(device, main) || !chain.warm(device)) {
					continue;
				}

				return;
			} while (System.nanoTime() < deadline);
		} catch (GpuDeviceLossException e) {

			throw e;
		} catch (RuntimeException e) {

			stop();
			CoreVK.logger().error("CoreVK stopped drawing this pack after an error", e);
			chain.release();
		}
	}

	static TerrainDraw terrain() {
		PackChain chain = active;

		return disabled || chain == null ? null : chain.terrain;
	}

	public static void clearCustomImages() {
		PackChain chain = active;
		if (disabled || chain == null) {
			return;
		}

		GpuDevice device = RenderSystem.tryGetDevice();
		if (device == null) {
			return;
		}

		chain.targets.clearStorage(device.createCommandEncoder());

		if (chain.everAdvanced) {
			Vector3dc camera = chain.values.world().cameraPositionUnshifted();
			chain.voxelAnchor.set(block(camera.x()), block(camera.y()), block(camera.z()));
			chain.voxelAnchored = true;
		}
	}

	public static void dispatchShadowCompute() {
		PackChain chain = active;
		if (disabled || chain == null) {
			return;
		}

		chain.beginFrame();
		chain.reanchorCustomImages();
		chain.compute.dispatch(chain.values, chain.targets);

		chain.values.convention(ClipSpace.REVERSED);
	}

	private void reanchorCustomImages() {
		if (!this.voxelAnchored) {
			return;
		}

		GpuDevice device = RenderSystem.tryGetDevice();
		if (device == null) {
			return;
		}

		Vector3dc camera = this.values.world().cameraPositionUnshifted();
		int x = block(camera.x());
		int y = block(camera.y());
		int z = block(camera.z());
		this.targets.reanchorStorage(device.createCommandEncoder(), x - this.voxelAnchor.x,
				y - this.voxelAnchor.y, z - this.voxelAnchor.z);

		this.voxelAnchor.set(x, y, z);
	}

	private static int block(double coordinate) {
		return (int) Math.floor(coordinate);
	}

	static SkyDraw sky() {
		PackChain chain = active;

		return disabled || chain == null ? null : chain.sky;
	}

	static EntityDraw entities() {
		PackChain chain = active;

		return disabled || chain == null ? null : chain.entities;
	}

	static CloudDraw clouds() {
		PackChain chain = active;

		return disabled || chain == null ? null : chain.clouds;
	}

	static WeatherDraw weather() {
		PackChain chain = active;

		return disabled || chain == null ? null : chain.weather;
	}

	static DistantDraw distant() {
		PackChain chain = active;

		return disabled || chain == null ? null : chain.distant;
	}

	static ParticleDraw particles() {
		PackChain chain = active;

		return disabled || chain == null ? null : chain.particles;
	}

	void beginFrame() {
		if (!this.advanced) {
			this.advanced = true;
			this.everAdvanced = true;
			PassTimings.armCensus();
			this.values.advance();
			PackDump.take(this.chain.place(), this.load,
					this.programs == null ? List.of() : this.programs, this.values.world(),
					this.terrain.programs(), this.families);
		}
	}

	boolean openTargets(GpuDevice device) {
		if (this.opened) {
			return true;
		}

		Minecraft minecraft = Minecraft.getInstance();
		RenderTarget main = minecraft == null ? null : minecraft.gameRenderer.mainRenderTarget();
		if (main == null || !ensureTargets(main)) {
			return false;
		}

		this.targets.depth().forgetOutgrown(main.width, main.height);

		WorldState world = this.values.world();
		this.fogClear.set(world.fogR(), world.fogG(), world.fogB(), 1.0F);
		this.targets.clear(device.createCommandEncoder(), this.fogClear);

		this.opened = true;

		return true;
	}

	private void closeFrame() {
		GeometryHold.flush(() -> "the frame before it closing");
		this.advanced = false;
		this.opened = false;
		this.begun = false;
		this.prepared = false;
		this.early = false;

		this.targets.copies().forget();
		this.filled = false;
		this.seeded = false;
		this.sceneDepth = false;

		this.targets.depth().forgetPreHand(HandDraw.diverted());

		this.targets.depth().forgetDistant();

		if (this.block != null) {
			this.block.rotate();
		}

		this.terrain.rotate();

		int ready = Math.min(this.warmup.familiesReady(), this.families.size());
		for (int family = 0; family < ready; family++) {
			this.families.get(family).rotate();
		}
	}

	public static void close() {

		PackChain chain = takeDown();
		if (chain != null) {
			chain.release();
		}

		FamilyWarmup.awaitAll();

		DistantDraw.close();
		ConstantTextures.close();
		ShadowCompare.close();
		GeometryStage.close();
	}

	public static void leaveWorld() {
		PackChain chain = active;
		if (chain == null) {
			return;
		}

		try {

			long megabytes = chain.targets.bytes() / (1024L * 1024L);
			if (megabytes > 0L) {
				CoreVK.logger().info("Left the world, so the {} MiB of colour targets, the shadow map "
						+ "and the buffers of {} go back until one is joined again", megabytes,
						chain.chain.packName());
			} else {
				CoreVK.logger().info("Left the world, and {} had nothing left to hand back",
						chain.chain.packName());
			}

			chain.release();
			chain.values.leaveWorld();

			chain.voxelAnchored = false;
		} catch (GpuDeviceLossException e) {
			throw e;
		} catch (RuntimeException e) {
			stop();
			CoreVK.logger().error("CoreVK stopped drawing this pack after an error", e);
		}
	}

	private Ready ready(GpuDevice device) {
		Minecraft minecraft = Minecraft.getInstance();
		RenderTarget main = minecraft == null ? null : minecraft.gameRenderer.mainRenderTarget();
		if (main == null || main.getColorTexture() == null) {
			return null;
		}

		if (this.programs == null) {
			build(device);
		}

		if (!prepare(device, main) || !warm(device)) {
			return null;
		}

		GpuTextureView mainView = main.getColorTextureView();
		if (mainView == null) {
			return null;
		}

		boolean seeding = this.seed != null && this.seedEnabled && this.seed.prepare(device);

		if (!this.filled) {
			this.filled = true;
			announce(main, seeding);
			writeBlocks();
		}

		openTargets(device);

		return new Ready(main, mainView, GraphicsApi.hasDepth(main) ? main.getDepthTextureView() : null,
				seeding);
	}

	private void drawRange(GpuDevice device, Ready ready, int from, int to, GpuTextureView depth,
			GpuTextureView distant, boolean seeding, Cut cut) {

		int seedAt = seeding && ready.seeding()
				? Math.min(this.chain.chain().seed().map(ChainPlan.Seed::at).orElse(-1),
						this.programs.size())
				: -1;

		CommandEncoder encoder = device.createCommandEncoder();
		GpuBuffer buffer = this.block.currentBuffer();

		this.currentChains.clear();
		for (int at = from; at < to; at++) {
			if (!this.seeded && at == seedAt) {
				paintSeed(encoder, ready);
				this.currentChains.clear();
			}

			PackPass pass = this.programs.get(at);

			boolean emptying = this.targets.hasPendingClears();

			dispatchStandalone(cut, at, Reach.AT_INDEX, encoder, device, ready, depth, distant,
					emptying);
			if (this.compute.hangsOff(pass.program())) {

				if (emptying) {
					this.targets.flushPending(encoder);
				}

				this.compute.dispatchBefore(pass.program(), encoder, device, this.values,
						this.targets, pass.step(), depth, distant, ready.main().width,
						ready.main().height);
				this.currentChains.clear();
			}

			for (PackPass.LodRead read : pass.lodReads()) {
				TargetSurface surface = this.targets.surface(read.target(), read.side());
				if (surface != null && !this.currentChains.contains(surface)
						&& MipmapReduction.generate(encoder, surface)) {
					this.currentChains.add(surface);
				}
			}

			GpuBufferSlice uniforms = buffer.slice(pass.uniformOffset(), pass.uniformSize());
			if (pass == this.last) {
				pass.drawFinal(encoder, ready.mainView(), this.targets, depth, distant, this.quad,
						uniforms);
			} else {
				pass.draw(encoder, this.targets, depth, distant, this.quad, uniforms,
						ready.main().width, ready.main().height);
			}

			if (emptying) {
				this.currentChains.clear();
			} else {
				for (ChainPlan.Attachment attachment : pass.attachments()) {
					this.currentChains.remove(
							this.targets.surface(attachment.target(), attachment.side()));
				}
			}
		}

		if (!this.seeded && seedAt >= from && seedAt <= to) {
			paintSeed(encoder, ready);
			this.currentChains.clear();
		}

		dispatchStandalone(cut, to, Reach.PAST_END, encoder, device, ready, depth, distant,
				this.targets.hasPendingClears());
	}

	private void dispatchStandalone(Cut cut, int at, Reach reach, CommandEncoder encoder,
			GpuDevice device, Ready ready, GpuTextureView depth, GpuTextureView distant,
			boolean emptying) {
		boolean ran = false;
		for (Standalone waiting : this.standalone) {
			if (waiting.cut() != cut || !reach.takes(waiting, at)) {
				continue;
			}

			if (emptying && !ran) {
				this.targets.flushPending(encoder);
			}

			ran = true;
			this.compute.dispatchAlone(waiting.program(), encoder, device, this.values, this.targets,
					waiting.step(), depth, distant, ready.main().width, ready.main().height);
		}

		if (ran) {
			this.currentChains.clear();
		}
	}

	private enum Reach {

		AT_INDEX,

		PAST_END;

		boolean takes(Standalone waiting, int at) {
			return this == PAST_END ? waiting.at() >= at : waiting.at() == at;
		}
	}

	private record Ready(RenderTarget main, GpuTextureView mainView, GpuTextureView depthView,
			boolean seeding) {
	}

	public static void drawBegins() {
		PackChain chain = active;
		GpuDevice device = RenderSystem.tryGetDevice();
		if (disabled || chain == null || device == null || !chainWanted) {
			return;
		}

		try {
			chain.drawBegins(device);
		} catch (GpuDeviceLossException e) {
			throw e;
		} catch (RuntimeException e) {
			stop();
			CoreVK.logger().error("CoreVK stopped drawing this pack after an error", e);
			chain.release();
		}
	}

	public static void drawPrepares() {
		PackChain chain = active;
		GpuDevice device = RenderSystem.tryGetDevice();
		if (disabled || chain == null || device == null || !chainWanted) {
			return;
		}

		try {
			chain.drawPrepares(device);
		} catch (GpuDeviceLossException e) {
			throw e;
		} catch (RuntimeException e) {
			stop();
			CoreVK.logger().error("CoreVK stopped drawing this pack after an error", e);
			chain.release();
		}
	}

	public static void drawBeforeTranslucents() {
		PackChain chain = active;
		GpuDevice device = RenderSystem.tryGetDevice();
		if (disabled || chain == null || device == null || !chainWanted) {
			return;
		}

		try {
			chain.drawEarly(device);
		} catch (GpuDeviceLossException e) {
			throw e;
		} catch (RuntimeException e) {
			stop();
			CoreVK.logger().error("CoreVK stopped drawing this pack after an error", e);
			chain.release();
		}
	}

	public static void takeDistantDepth() {
		PackChain chain = active;
		GpuDevice device = RenderSystem.tryGetDevice();
		Minecraft minecraft = Minecraft.getInstance();
		if (disabled || chain == null || device == null || minecraft == null || !chainWanted) {
			return;
		}

		DhLods.install();

		DhLods.clouds(chain.values.avoidsDhClouds());

		GpuTextureView served = chain.distant.served();
		if (served == null && !chain.readsDistantDepth()) {
			return;
		}

		try {
			if (served == null) {

				RenderTarget main = minecraft.gameRenderer.mainRenderTarget();
				if (main != null) {
					chain.targets.depth().takeDistantNothing(device.createCommandEncoder(), main.width,
							main.height);
				}

				return;
			}

			chain.targets.depth().takeDistantOpaque(device.createCommandEncoder(), device,
					chain.quad(device), served, served.getWidth(0), served.getHeight(0));
		} catch (GpuDeviceLossException e) {
			throw e;
		} catch (RuntimeException e) {
			stop();
			CoreVK.logger().error("CoreVK stopped drawing this pack after an error", e);
			chain.release();
		}
	}

	boolean mayReadShadowWithoutTranslucents() {
		return this.chain.mentions().maybe("shadowtex1")
				|| this.chain.mentions().maybe("watershadow");
	}

	private boolean mayReadPreHandDepth() {
		return this.chain.mentions().maybe("depthtex2");
	}

	private boolean readsDistantDepth() {
		return PackDefines.distantHorizons() && this.chain.mentions().maybe("dhDepthTex");
	}

	private boolean wantsPreHandDepth() {
		return mayReadPreHandDepth() || this.centerDepthRead;
	}

	public static void markPreHandDepth() {
		PackChain chain = active;
		GpuDevice device = RenderSystem.tryGetDevice();
		Minecraft minecraft = Minecraft.getInstance();
		if (disabled || chain == null || device == null || minecraft == null || !chainWanted
				|| !HandDraw.draws() || !chain.wantsPreHandDepth()) {
			return;
		}

		RenderTarget main = minecraft.gameRenderer.mainRenderTarget();
		if (main == null) {
			return;
		}

		try {
			chain.targets.depth().takePreHand(device.createCommandEncoder(), device, chain.quad(device),
					main.getDepthTextureView(), main.width, main.height);
		} catch (GpuDeviceLossException e) {
			throw e;
		} catch (RuntimeException e) {
			stop();
			CoreVK.logger().error("CoreVK stopped drawing this pack after an error", e);
			chain.release();
		}
	}

	public static void markSceneDepth() {
		PackChain chain = active;
		GpuDevice device = RenderSystem.tryGetDevice();
		Minecraft minecraft = Minecraft.getInstance();
		if (disabled || chain == null || device == null || minecraft == null || !chainWanted) {
			return;
		}

		RenderTarget main = minecraft.gameRenderer.mainRenderTarget();
		if (main == null) {
			return;
		}

		try {
			if (chain.keepScene(device, main.getDepthTextureView(), main.width, main.height)
					&& !chain.saidBeforeClear) {
				chain.saidBeforeClear = true;
				CoreVK.logger().info("The scene's depth is kept before the game clears it for its "
						+ "always-on-top features, so what this pack reads as depthtex0 is the world "
						+ "rather than the far plane");
			}
		} catch (GpuDeviceLossException e) {
			throw e;
		} catch (RuntimeException e) {
			stop();
			CoreVK.logger().error("CoreVK stopped drawing this pack after an error", e);
			chain.release();
		}
	}

	private boolean keepScene(GpuDevice device, GpuTextureView depth, int width, int height) {
		if (this.sceneDepth) {
			return false;
		}

		this.sceneDepth = this.targets.depth().takeScene(device.createCommandEncoder(), device,
				quad(device), depth, width, height);

		return this.sceneDepth;
	}

	public static void openFeatures() {
		PackChain chain = active;
		GpuDevice device = RenderSystem.tryGetDevice();
		Minecraft minecraft = Minecraft.getInstance();

		if (disabled || chain == null || device == null || minecraft == null || !chainWanted
				|| chain.features == null || !GameRender.redirectsFeatures()) {
			return;
		}

		RenderTarget main = minecraft.gameRenderer.mainRenderTarget();

		try {

			if (main == null || !chain.features.prepare(device) || !chain.drawable()) {
				return;
			}

			GpuTextureView layer = chain.features.open(device, main.width, main.height);
			if (layer == null) {
				return;
			}

			GameRender.redirectFeatures(layer, main.getDepthTextureView());
			chain.redirected = true;
		} catch (GpuDeviceLossException e) {
			throw e;
		} catch (RuntimeException e) {

			GameRender.endFeatureRedirect();
			chain.redirected = false;
			stop();
			CoreVK.logger().error("CoreVK stopped drawing this pack after an error", e);
			chain.release();
		}
	}

	public static void takeReadCopies() {
		PackChain chain = active;
		GpuDevice device = RenderSystem.tryGetDevice();

		if (disabled || chain == null || device == null || !chainWanted || !chain.early) {
			return;
		}

		try {
			chain.takeReadCopies(device);
		} catch (GpuDeviceLossException e) {
			throw e;
		} catch (RuntimeException e) {
			stop();
			CoreVK.logger().error("CoreVK stopped drawing this pack after an error", e);
			chain.release();
		}
	}

	private void takeReadCopies(GpuDevice device) {
		if (!this.targets.copies().any()) {
			return;
		}

		CommandEncoder encoder = device.createCommandEncoder();
		this.targets.flushPending(encoder);
		this.targets.copies().take(encoder);
	}

	public static void closeFeatures() {

		GameRender.endFeatureRedirect();

		PackChain chain = active;
		if (chain == null || !chain.redirected) {
			return;
		}

		chain.redirected = false;

		GpuDevice device = RenderSystem.tryGetDevice();
		if (disabled || device == null || chain.features == null) {
			return;
		}

		try {
			ChainPlan.Attachment into = chain.features.into();
			GpuTextureView view = chain.targets.view(into.target(), into.side());

			chain.features.compose(device.createCommandEncoder(), chain.quad, view,
					chain.targets.takeClear(view));
		} catch (GpuDeviceLossException e) {
			throw e;
		} catch (RuntimeException e) {
			stop();
			CoreVK.logger().error("CoreVK stopped drawing this pack after an error", e);
			chain.release();
		}
	}

	private void drawBegins(GpuDevice device) {
		if (this.begun) {
			return;
		}

		if (this.chain.chain().beginEnd() == 0 && !standing(Cut.AHEAD_OF_SHADOWS)) {
			this.begun = true;

			return;
		}

		Ready ready = ready(device);
		if (ready == null) {
			return;
		}

		this.begun = true;
		drawBeforeWorld(device, ready, 0, beginEnd(), Cut.AHEAD_OF_SHADOWS);
	}

	private void drawPrepares(GpuDevice device) {
		if (this.prepared) {
			return;
		}

		if (this.chain.chain().beginEnd() == this.chain.chain().prepareEnd()
				&& !standing(Cut.BEFORE_WORLD)) {
			this.prepared = true;

			return;
		}

		Ready ready = ready(device);
		if (ready == null) {
			return;
		}

		this.prepared = true;
		drawBeforeWorld(device, ready, beginEnd(), prepareEnd(), Cut.BEFORE_WORLD);
	}

	private boolean standing(Cut cut) {
		for (String program : this.compute.standingAlone()) {
			if (Cut.of(program) == cut) {
				return true;
			}
		}

		return false;
	}

	private void drawBeforeWorld(GpuDevice device, Ready ready, int from, int to, Cut cut) {
		PackDepth depth = this.targets.depth();
		depth.frameBefore(true, ready.main().width, ready.main().height);
		try {

			drawRange(device, ready, from, to, null, depth.distantScene(), false, cut);
		} finally {
			depth.frameBefore(false, ready.main().width, ready.main().height);
		}
	}

	private void drawEarly(GpuDevice device) {
		if (this.early) {
			return;
		}

		drawBegins(device);
		drawPrepares(device);

		Ready ready = ready(device);
		if (ready == null) {
			return;
		}

		this.early = true;

		this.targets.depth().takeOpaque(device.createCommandEncoder(), device, this.quad,
				ready.depthView(), ready.main().width, ready.main().height);

		sampleCenterDepth(device);

		MotionVectors vectors = this.targets.motionVectors();
		if (RenderScale.upscaling() && TemporalAccumulation.wanted()) {
			vectors.draw(device.createCommandEncoder(), device, this.quad,
					this.targets.depth().opaque(), ready.main().width, ready.main().height,
					this.values.view(), this.values.world());
		} else {
			vectors.standDown();
		}

		int world = prepareEnd();
		int end = deferredEnd();
		if (!this.split) {
			this.split = true;

			if (this.programs.isEmpty()) {
				CoreVK.logger().info("This chain draws no full screen pass and runs its computes on "
						+ "their own: {}", this.standalone.stream()
								.map(Standalone::program)
								.toList());
			} else {
				CoreVK.logger().info("{} of this chain run before the world {}, {} more before its "
						+ "translucents {}, {} after",
						world, this.programs.stream().limit(world).map(PackPass::path).toList(),
						end - world,
						this.programs.stream().skip(world).limit(end - world).map(PackPass::path)
								.toList(),
						this.programs.size() - end);
			}
		}

		drawRange(device, ready, world, end, this.targets.depth().opaque(),
				this.targets.depth().distantOpaque(), true, Cut.BEFORE_TRANSLUCENTS);

		takeReadCopies(device);
	}

	private int deferredEnd() {
		return Math.min(this.chain.chain().deferredEnd(), this.programs.size());
	}

	private int prepareEnd() {
		return Math.min(this.chain.chain().prepareEnd(), this.programs.size());
	}

	private int beginEnd() {
		return Math.min(this.chain.chain().beginEnd(), this.programs.size());
	}

	private void run() {
		GpuDevice device = RenderSystem.tryGetDevice();
		if (device == null) {
			return;
		}

		drawEarly(device);

		Ready ready = ready(device);
		if (ready == null) {
			return;
		}

		if (keepScene(device, ready.depthView(), ready.main().width, ready.main().height)
				&& !this.saidAfterTheWorld) {
			this.saidAfterTheWorld = true;
			CoreVK.logger().info("The scene's depth is kept after the world, nothing having kept it "
					+ "earlier in this frame");
		}

		GpuTextureView distantServed = this.distant.served();
		if (distantServed != null) {
			this.targets.depth().takeDistantScene(device.createCommandEncoder(), device, this.quad,
					this.distant.blendedServed(), this.distant.worldServed(), distantServed,
					distantServed.getWidth(0), distantServed.getHeight(0));
		}

		drawRange(device, ready, deferredEnd(), this.programs.size(), this.targets.depth().scene(),
				this.targets.depth().distantScene(), true, Cut.AFTER_TRANSLUCENTS);

		if (this.present != null && this.present.prepare(device)) {
			this.present.draw(device.createCommandEncoder(), this.quad, ready.mainView(),
					this.targets);
		}

		this.targets.copyBack(device.createCommandEncoder(), this.chain.chain().swapBack());
	}

	private void sampleCenterDepth(GpuDevice device) {
		if (!this.centerDepthRead) {
			return;
		}

		GpuTextureView preHand = this.targets.depth().preHand();
		WorldState world = this.values.world();
		this.targets.centerDepth().sample(device.createCommandEncoder(), device, this.quad,
				preHand != null ? preHand : this.targets.depth().opaque(),
				world.centerDepthHalfLife(), world.frameTime());
	}

	private void paintSeed(CommandEncoder encoder, Ready ready) {
		this.seeded = true;
		drawSeed(encoder, ready.mainView(), ready.depthView());
	}

	private void drawSeed(CommandEncoder encoder, GpuTextureView mainView, GpuTextureView depthView) {

		GpuTextureView covered = this.targets.coverage();

		this.targets.flushPending(encoder);

		GpuTextureView distantServed = this.distant.served();
		this.seed.draw(encoder, this.quad, mainView,
				covered == null ? this.targets.unwritten() : covered, depthView,
				distantServed == null ? this.targets.unwritten() : distantServed, this.targets);
	}

	private void build(GpuDevice device) {
		int alignment = Math.max(16, device.getDeviceInfo().limits().minUniformOffsetAlignment());
		ChainPlan plan = this.chain.chain();
		List<PackPass> built = new ArrayList<>();
		int offset = 0;

		for (ChainPlan.Pass pass : ordered(plan)) {
			PackProgram.Loaded loaded = this.chain.programs().get(pass.program());
			if (loaded == null) {

				throw new IllegalStateException(pass.program() + " is in the chain of "
						+ this.chain.packName() + " and was never translated");
			}

			built.add(new PackPass(this.chain.place(), pass.program(), loaded, pass, this.targets,
					this.values, this.load, offset));
			offset += Mth.roundToward(PackPass.uniformSizeOf(loaded), alignment);
		}

		this.programs = List.copyOf(built);
		this.standalone = standaloneOf(built);

		this.last = plan.last().isEmpty() || built.isEmpty() ? null : built.get(built.size() - 1);

		this.centerDepthRead = built.stream().anyMatch(PackPass::readsCenterDepth)
				|| this.compute.readsCenterDepth();
		this.blockBytes = Math.max(alignment, offset);
	}

	private static List<ChainPlan.Pass> ordered(ChainPlan plan) {
		List<ChainPlan.Pass> all = new ArrayList<>(plan.passes());
		plan.last().ifPresent(all::add);

		return all;
	}

	private static final int BEGIN_RANK = ProgramNames.frameRank("begin");

	private static final int PREPARE_RANK = ProgramNames.frameRank("prepare");

	private static final int DEFERRED_RANK = ProgramNames.frameRank("deferred");

	private enum Cut {

		AHEAD_OF_SHADOWS,

		BEFORE_WORLD,

		BEFORE_TRANSLUCENTS,

		AFTER_TRANSLUCENTS;

		static Cut of(String program) {
			int rank = rankOf(program);
			if (rank <= BEGIN_RANK) {
				return AHEAD_OF_SHADOWS;
			}

			if (rank <= PREPARE_RANK) {
				return BEFORE_WORLD;
			}

			return rank <= DEFERRED_RANK ? BEFORE_TRANSLUCENTS : AFTER_TRANSLUCENTS;
		}
	}

	private static int rankOf(String program) {
		return ProgramNames.frameRank(ProgramNames.familyOf(TargetName.bareName(program)));
	}

	private record Standalone(Cut cut, int at, String program, TargetSchedule.Bound step) {
	}

	private List<Standalone> standaloneOf(List<PackPass> built) {
		if (this.compute.standingAlone().isEmpty()) {
			return List.of();
		}

		Comparator<String> order = ProgramNames.frameOrder();
		List<Standalone> waiting = new ArrayList<>();
		for (String program : this.compute.standingAlone()) {
			TargetSchedule.Bound step = this.targets.schedule().passing(program).orElse(null);

			if (step == null) {
				CoreVK.logger().warn("compute of {} is not dispatched: the schedule gives it no "
						+ "halves to read", program);
				continue;
			}

			int at = 0;
			while (at < built.size() && ProgramNames.before(built.get(at).program(), program)) {
				at++;
			}

			waiting.add(new Standalone(Cut.of(program), at, program, step));
		}

		waiting.sort(Comparator.comparing(Standalone::program, order));

		return List.copyOf(waiting);
	}

	private boolean warm(GpuDevice device) {
		if (this.programs.isEmpty() && this.standalone.isEmpty()) {
			return false;
		}

		boolean standing = this.programs.isEmpty() ? warmPresent(device) : warmPasses(device);
		if (!standing) {
			return false;
		}

		startFamilyPrefetch();

		if (compileNext(device, this.terrain.programs())) {
			return false;
		}

		if (!this.geometryReady) {
			CoreVK.logger().info("The pack's composites and terrain are compiled, the chain can draw");
		}

		this.geometryReady = true;

		return true;
	}

	@SuppressWarnings("ReferenceEquality")
	private boolean warmPasses(GpuDevice device) {
		CompiledRenderPipeline first = this.programs.get(0).compile(device);
		if (!valid(first, this.programs.get(0))) {
			return false;
		}

		if (first != this.head) {
			this.head = first;
			this.warmed = 1;
			forgetGeometry();

			return false;
		}

		if (this.warmed < this.programs.size()) {
			PackPass pass = this.programs.get(this.warmed);
			if (!valid(pass.compile(device), pass)) {
				return false;
			}

			this.warmed++;

			return false;
		}

		return true;
	}

	@SuppressWarnings("ReferenceEquality")
	private boolean warmPresent(GpuDevice device) {
		if (this.present == null) {
			return true;
		}

		CompiledRenderPipeline first = this.present.compiled(device);
		if (first != this.head) {
			this.head = first;
			forgetGeometry();

			return false;
		}

		return true;
	}

	private static final long WARM_BUDGET_NANOS = 80_000_000L;

	private static final boolean RELOAD_BEFORE_LEVEL = Boolean.parseBoolean(
			System.getProperty("corevk.reloadBeforeLevel", "true"));

	private static boolean beforeLevelRoadSaid;

	private static void sayBeforeLevelRoad() {
		if (beforeLevelRoadSaid) {
			return;
		}

		beforeLevelRoadSaid = true;
		CoreVK.logger().info("The world is asked about {} the level is drawn, "
						+ "property=corevk.reloadBeforeLevel",
				RELOAD_BEFORE_LEVEL ? "before" : "after");
	}

	void startFamilyPrefetch() {
		this.warmup.start();
	}

	private static boolean compileNext(GpuDevice device,
			Collection<? extends DumpedProgram> programs) {
		for (DumpedProgram program : programs) {
			if (!program.compiled()) {
				program.compile(device);

				return true;
			}
		}

		return false;
	}

	private void forgetGeometry() {
		this.geometryReady = false;
		forgetCompiled(this.terrain.programs());
		int ready = Math.min(this.warmup.familiesReady(), this.families.size());
		for (int family = 0; family < ready; family++) {
			forgetCompiled(this.warmup.familyPrograms(family));
		}
	}

	private static void forgetCompiled(Collection<? extends DumpedProgram> programs) {
		programs.forEach(DumpedProgram::forgetCompiled);
	}

	boolean drawable() {
		return this.programs != null && !(this.programs.isEmpty() && this.standalone.isEmpty())
				&& this.warmed == this.programs.size()
				&& this.geometryReady;
	}

	private boolean valid(CompiledRenderPipeline compiled, PackPass pass) {
		if (GraphicsApi.valid(compiled)) {
			return true;
		}

		stop();
		CoreVK.logger().error("{} did not compile, nothing of this pack will be drawn", pass.path());
		release();

		return false;
	}

	GpuBuffer quad(GpuDevice device) {
		if (this.quad == null) {
			ByteBuffer vertices = ByteBuffer.allocateDirect(QUAD.length * Float.BYTES)
					.order(ByteOrder.nativeOrder());
			vertices.asFloatBuffer().put(QUAD);
			this.quad = device.createBuffer(() -> QUAD_LABEL,
					GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, vertices);
		}

		return this.quad;
	}

	private boolean prepare(GpuDevice device, RenderTarget main) {
		quad(device);

		if (this.block == null) {

			this.block = new MappableRingBuffer(() -> BLOCK_LABEL,
					GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, this.blockBytes);
		}

		return ensureTargets(main);
	}

	private boolean ensureTargets(RenderTarget main) {
		if (this.targets.ensure(main.width, main.height)) {
			return true;
		}

		if (this.targets.refusedForGood()) {
			putAway("the device would not allocate an image it declares, see the log");
		}

		return false;
	}

	private void writeBlocks() {

		beginFrame();

		this.values.convention(ClipSpace.REVERSED);

		this.values.renderStage(RenderStage.NONE);

		this.values.modelView(null, null);
		this.values.passColour(null);
		this.values.projection(null);

		try (GpuBufferSlice.MappedView view = this.block.currentBuffer().map(false, true)) {
			ByteBuffer data = view.data();
			for (PackPass pass : this.programs) {
				data.position(pass.uniformOffset());
				pass.write(Std140Builder.intoBuffer(data), this.values.world());
			}
		}
	}

	private void announce(RenderTarget main, boolean seeding) {
		if (this.announced) {
			return;
		}

		this.announced = true;
		TargetPlan plan = this.chain.targets();
		ChainPlan unfolded = this.chain.chain();

		CoreVK.logger().info("Drawing {} from {} for {}, at {}x{}, {} full screen passes before the "
				+ "final", this.chain.packName(), place(), this.world, main.width, main.height,
				unfolded.passes().size());

		for (PackPass pass : this.programs) {
			CoreVK.logger().info("{}", pass.describe());
		}

		plan.notes().forEach(note -> CoreVK.logger().info("{}", note));
		this.targets.notes().forEach(note -> CoreVK.logger().warn("{}", note));

		announceSeed(seeding);
		announceFeatures();
		announceResting(seeding);

		List<Integer> back = unfolded.swapBack();
		if (!back.isEmpty()) {
			CoreVK.logger().info("{} targets are copied back from their far half at the end of every "
					+ "frame, because the pack keeps them and the chain left them there: {}",
					back.size(), back);
		}

		unfolded.history().forEach(note -> CoreVK.logger().info("{}", note));

		this.values.notes().forEach(note -> CoreVK.logger().info("{}", note));

		unfolded.notes().forEach(note -> CoreVK.logger().warn("{}", note));
		this.programs.forEach(pass -> pass.notes().forEach(note -> CoreVK.logger().warn("{}", note)));

		announceSamplers();
	}

	private void announceSeed(boolean seeding) {
		Optional<ChainPlan.Seed> where = this.chain.chain().seed();
		if (seeding && where.isPresent()) {

			CoreVK.logger().info("{} carries the game's opaque frame, drawn in for {}: {} still come "
					+ "from the game, already tone mapped, and the translucent chunk pass blends onto "
					+ "this seed afterwards",
					TargetName.canonical(where.get().target()), where.get().from(), stillTheGame());

			CoreVK.logger().info("It is painted where the world would be drawn, after {} passes of "
					+ "the chain", where.get().at());

			CoreVK.logger().info("It paints where the world's depth stands in front of the one the "
					+ "pack's own geometry left, which is what the coverage mask carries: everywhere "
					+ "the pack wrote nothing, and everywhere the game has drawn over what it wrote");

			List<Integer> emptied = this.seed.emptied();
			if (!emptied.isEmpty()) {
				CoreVK.logger().info("Where it paints over that terrain, which is where the game drew "
						+ "in front of it, the rest of that program's draw buffers are emptied: {}. "
						+ "They would otherwise keep the gbuffer of the block behind, and the deferred "
						+ "stage would light the game's colour with it",
						emptied.stream().map(TargetName::canonical).toList());
			}
		} else if (where.isEmpty()) {
			CoreVK.logger().info("Nothing carries the game's frame in {}, so every program of the "
					+ "chain starts from a clear colour", place());
		} else if (!this.seedEnabled) {
			CoreVK.logger().info("The scene seed is off, {} holds its clear colour as well",
					TargetName.canonical(where.get().target()));
		} else if (this.seed == null && holdsIntegers(where.get().target())) {
			CoreVK.logger().warn("{} writes {} first and it holds integers, so the scene seed is not "
					+ "drawn: the game's frame is a colour and no integer stands for one. Whatever the "
					+ "game still draws in the pack's place is missing from the picture",
					where.get().from(), TargetName.canonical(where.get().target()));
		}
	}

	private void announceFeatures() {
		if (this.features != null) {
			return;
		}

		featuresInto().filter(into -> holdsIntegers(into.target())).ifPresent(into ->
				CoreVK.logger().warn("The world's translucents are drawn into {} first and it holds "
						+ "integers, so the game's translucent features are not composed onto it: the "
						+ "beacon beam, the lightning and anything else the pack does not draw stay on "
						+ "the game's own target, which the pack's final draws over",
						TargetName.canonical(into.target())));
	}

	private void announceResting(boolean seeding) {
		Set<Integer> filled = new TreeSet<>();
		if (seeding) {
			this.chain.chain().seed().ifPresent(where -> filled.add(where.target()));
		}

		this.chain.chain().passes().forEach(pass -> filled.addAll(pass.targets()));

		List<String> resting = this.chain.targets().ordered().stream()
				.filter(index -> !filled.contains(index))
				.map(TargetName::canonical)
				.toList();
		if (!resting.isEmpty()) {
			CoreVK.logger().info(
					"No pass of this chain writes these, so they hold nothing but their clear colour: {}",
					resting);
		}
	}

	private void announceSamplers() {
		Map<SamplerPlan.Kind, Set<String>> byKind = new EnumMap<>(SamplerPlan.Kind.class);
		for (PackProgram.Loaded loaded : this.chain.programs().values()) {
			loaded.samplers().byKind().forEach((kind, names) ->
					byKind.computeIfAbsent(kind, _ -> new LinkedHashSet<>()).addAll(names));
		}

		named(byKind, SamplerPlan.Kind.COLORTEX, "read a real colour target");
		named(byKind, SamplerPlan.Kind.DEPTH, "read the world's depth");
		String map = this.targets.shadow().depth() == null
				? "read white, no shadow map is allocated"
				: "read the shadow map";
		named(byKind, SamplerPlan.Kind.SHADOW_DEPTH, map);
		named(byKind, SamplerPlan.Kind.SHADOW_COLOUR, map);
		named(byKind, SamplerPlan.Kind.CENTER_DEPTH,
				"read the smoothed depth at the centre of the screen, which is what a depth of field "
						+ "focuses on");
		named(byKind, SamplerPlan.Kind.DISTANT_DEPTH,
				"read the far terrain's own depth, kept beside the world's as CoreVK keeps it, on the "
						+ "frames this pack draws the far terrain, and the far plane on the rest");
		named(byKind, SamplerPlan.Kind.CUSTOM_IMAGE,
				"read a storage image the pack declared with image.NAME");
		named(byKind, SamplerPlan.Kind.UNBINDABLE,
				"are declared under a type this backend cannot bind, and should have gone with "
						+ "their pass");

		Set<String> depths = byKind.getOrDefault(SamplerPlan.Kind.DEPTH, Set.of());
		List<String> copies = depths.stream()
				.filter(name -> SamplerPlan.depthCopy(name) && !SamplerPlan.preHandCopy(name))
				.toList();
		if (!copies.isEmpty()) {
			CoreVK.logger().info("{} read the depth of the world as it stood before its "
					+ "translucents", copies);
		}

		List<String> pastTheHand = depths.stream().filter(SamplerPlan::preHandCopy).toList();
		if (!pastTheHand.isEmpty()) {
			CoreVK.logger().info("{} read the depth of the world from before the hand was drawn, "
					+ "which is an image of its own only while this engine draws the hand", pastTheHand);
		}

		if (!mayReadShadowWithoutTranslucents()) {
			CoreVK.logger().info("No source of this pack names shadowtex1 or watershadow, so the "
					+ "copy of the shadow map taken before its translucents is not taken at all");
		}

		if (!wantsPreHandDepth()) {
			CoreVK.logger().info("No source of this pack names depthtex2 and nothing in this chain "
					+ "folds the centre depth, so the depth of the world from before the hand is "
					+ "neither converted nor kept");
		}
	}

	private static void named(Map<SamplerPlan.Kind, Set<String>> byKind, SamplerPlan.Kind kind,
			String what) {
		Set<String> names = byKind.getOrDefault(kind, Set.of());
		if (!names.isEmpty()) {
			CoreVK.logger().info("{} samplers this chain {}: {}", names.size(), what, names);
		}
	}

	private String place() {
		return this.chain.place().isEmpty() ? "the root" : this.chain.place();
	}

	void release() {

		this.warmup.release();
		int ready = Math.min(this.warmup.familiesReady(), this.families.size());
		for (int family = 0; family < ready; family++) {
			this.warmup.familyPrograms(family).forEach(DumpedProgram::discardAhead);
		}

		CustomImages.clear();

		TerrainSampler.breaksAnisotropy(false);
		this.compute.close();
		this.targets.release();
		if (this.features != null) {
			this.features.release();
		}

		DhLods.handBack();

		this.terrain.release();
		synchronized (this.familyMaps) {
			this.families.forEach(FamilyDraw::release);
		}

		if (this.block != null) {
			this.block.close();
			this.block = null;
		}

		if (this.quad != null) {
			this.quad.close();
			this.quad = null;
		}

		if (this.programs != null && !this.programs.isEmpty()) {
			boolean stillLive = active == this && !disabled;
			CoreVK.logger().info("{} pipelines and {} shader modules of load {} stay in the device "
					+ "cache, and the next purge {}", this.programs.size(),
					2 * this.programs.size(), this.load,
					stillLive ? "carries them, this chain being the one that draws again"
							: "frees them");
		}
	}
}
