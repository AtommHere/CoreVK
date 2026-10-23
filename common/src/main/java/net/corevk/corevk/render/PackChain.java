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
		// The mesh is not taken down here and the pass goes back to the game's own shader, which
		// expects the game's per face brightness to be in the vertex colour. Said here rather than
		// at each road into this method, so a new one cannot forget it: the refusals of the load and
		// the errors the frame catches. putAway, the frame's stop for a reason it names, says the
		// same thing for the same reason.
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
		// One pack's shadow map is not another's, and the arming file is read again here so a
		// session can turn it on and off without leaving the game. Here rather than in beginFrame,
		// which despite its name runs once per FRAME: forgetting there dropped the map on every
		// frame, which is a draw on every frame, which is the amortisation doing nothing at all
		// while announcing itself in the log sixty times a second. Measured 6 September 2026.
		ShadowAmortisation.forget();
		this.chain = chain;
		this.values = values;
		this.packPath = packPath;
		this.chosen = Map.copyOf(chosen);
		this.profile = profile;
		this.world = world;
		this.seedEnabled = seedEnabled;
		this.load = LOADS.incrementAndGet();

		// None of this touches the device: the textures are allocated by the first frame and this
		// runs while the client is still starting up, off the render thread.
		this.targets = new ColorTargets(chain.targets(), values.noiseResolution(),
				values.noiseImage(), values.packImages(), values.storageImages(),
				values.shadowResolution(), values.shadowColours(), values.shadowDepths());
		// No seed at all into a target of integers, and asked here rather than of the compile, which
		// on Metal throws instead of handing back a pipeline that is invalid. announceSeed says so.
		this.seed = chain.chain().seed()
				.filter(where -> this.targets.has(where.target()))
				.filter(where -> !holdsIntegers(where.target()))
				.map(where -> new SceneSeed(where, this.targets.format(where.target()),
						seedExtras(chain, where)))
				.orElse(null);
		// Null on every pack that ships a final, which is every pack but two of this corpus. The
		// target has to exist for the same reason the seed's does: a plan naming one the targets do
		// not hold is a frame drawn from a texture nobody allocated.
		this.present = chain.chain().present()
				.filter(where -> this.targets.has(where.target()))
				.map(ChainPresent::new)
				.orElse(null);
		// Composed where the world's own translucents are about to blend, so it needs that pass to
		// exist: a pack serving no translucent geometry gets no layer, and the game's features stay
		// where the game drew them. A target of integers gets none either, for the seed's reason.
		this.features = featuresInto()
				.filter(into -> !holdsIntegers(into.target()))
				.map(into -> new FeatureLayer(into, this.targets.format(into.target())))
				.orElse(null);
		// Held rather than read: the terrain program is compiled against the chunk mesh format, and
		// nothing knows that format until the renderer asks for its shader.
		//
		// It is handed THIS chain's plan and schedule and never ones of its own, which would be
		// built without the user's pass filter. The half a program writes comes from the schedule,
		// and a schedule walked over a different set of passes hands out a different parity: the
		// terrain would then write one half while the chain read the other, and neither side would
		// say a word. Whether the chain runs travels with them, because a translucent pass that
		// takes draw buffer nought for the pack is drawing for a final: without one, the water
		// would leave the screen and reach nothing.
		this.terrain = new TerrainDraw(this, packPath, chain.place(), values,
				this.load, chain.chain(), chain.targets(), chainWanted, this.targets);
		// The same plan and the same schedule again, and for the same reason. The sky is read on
		// demand too, since the game builds its meshes once at startup and a place that never draws
		// a sky should not pay for one.
		this.sky = new SkyDraw(this, packPath, chain.place(), chosen, profile, values, this.load,
				chain.chain(), chain.targets(), chainWanted, this.targets);
		// And again, for the same reason, and read on demand for a third one: a place the player
		// crosses without an entity in it should not pay for ten programs it never draws.
		// Handed the seed's own switch as well, which neither of the other two needs: it is the one
		// family whose first output has no road of its own into the pack's picture.
		this.entities = new EntityDraw(this, packPath, chain.place(), chosen, profile, values,
				this.load, chain.chain(), chain.targets(), chainWanted,
				seedEnabled && this.seed != null, this.targets);
		// And a fourth time, read on demand like the last two: a place with the clouds switched off,
		// which is every Nether and every player who turned them off, should not pay for a program
		// nothing draws.
		this.clouds = new CloudDraw(this, packPath, chain.place(), chosen, profile, values,
				this.load, chain.chain(), chain.targets(), chainWanted, this.targets);
		// And once more, read on demand for a fifth reason: a pack may be loaded for an hour before
		// it rains. It needs no switch of the seed's, being the one family here drawn WHOLLY after
		// the deferred stage: it blends onto what the chain has already put in the pack's target,
		// which is the position the world's own translucents are in. The particles below straddle
		// that stage instead, and that is why they need the switch and this does not.
		this.weather = new WeatherDraw(this, packPath, chain.place(), chosen, profile, values,
				this.load, chain.chain(), chain.targets(), chainWanted, this.targets);
		// And the sixth, which straddles the deferred stage: its opaque half is drawn among the
		// game's solid features and writes the coverage mask as the entities do, its translucent
		// half after the world's water. Neither half asks whether the seed is painted, both owning
		// the draw buffers the pack asked for.
		this.particles = new ParticleDraw(this, packPath, chain.place(), chosen, profile, values,
				this.load, chain.chain(), chain.targets(), chainWanted, this.targets);
		// And the seventh, read on demand like the five before it and for the sharpest reason of
		// them: most sessions have no Distant Horizons at all, and the ones that do only reach this
		// on the frames DH really draws a far terrain.
		this.distant = new DistantDraw(this, packPath, chain.place(), chosen, profile, values,
				this.load, chain.chain(), chain.targets(), chainWanted, this.targets);
		this.families = List.of(this.sky, this.entities, this.clouds, this.weather,
				this.particles, this.distant);
		this.warmup = new FamilyWarmup(this.families, this.packPath, this.chosen, this.profile,
				this.familyMaps);
		// The passes the chain will draw, read off the plan: the pass objects themselves are only
		// built on the render thread, after this constructor.
		this.compute = PackCompute.load(opened, chain.place(), chain.targets().computes(), this.load,
				values.shadowGeometryCatalog(), values.catalog(),
				ordered(chain.chain()).stream().map(ChainPlan.Pass::program)
						.collect(Collectors.toSet()),
				Set.copyOf(chain.targets().passing()));
		// Before the first frame allocates a target: the storage usage is baked into the image at
		// creation, and nothing can add it afterwards. Whatever stores into a target asks for it: a
		// compute, a full screen program, read off their translated text, and a world program,
		// which is only translated when first drawn and so is read off the pack's text instead.
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
			// The one entry the seed does not in fact carry, and it is named all the same because the
			// sentence is about what the picture still owes the game. Off, the hand is drawn after the
			// chain rather than into it, so it is painted straight onto the finished image and no seed
			// is involved; EngineOptions.announceHandOff is where that difference is spelt out.
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
			// Named apart, because a reader who has just turned the entities on and still sees a
			// beam or a bolt of the game's would otherwise have nothing to go on: both halves of the
			// entities go through the pack, and these two pieces are what FeatureLayer still carries.
			carried.add("the beacon beam and the lightning");
		}

		// The conjunction the hand-written sentence carried, kept: this is a line of prose in the log
		// and a bare comma list reads as a truncated one. The branch for a shorter list is not dead
		// and is in fact the ordinary answer with every switch on: the entities put one of their two
		// strings in either way, and every other family here names itself only when it is off.
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

		// The clear colours are read off the plan and not off ColorTargets, which reads them off the
		// plan itself: the decision is the plan's, and asking it is asking the one place that holds
		// it rather than adding a second.
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
		// The frame's own road out for a reason it names. The mesh stands and the game's shader
		// takes the chunk passes back, so the per face brightness it reads out of the vertex colour
		// has to be there.
		// Fired from inside a frame on purpose: allChanged raises a flag the next extract consumes
		// rather than tearing sections down under the frame that asked.
		FaceShading.none();
		PackChoice.error(this.chain.packName() + " is not drawn at all: " + why);
		CoreVK.logger().error("{} is put away rather than drawn by halves, because {}",
				this.chain.packName(), why);
	}

	
	public static boolean beforeLevel() {
		// Off Vulkan nothing is loaded for the world to move under, and the road is not named either:
		// beside CoreVK that line would be about a pack CoreVK is the one drawing.
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
			// The frame is closed whatever happened: a terrain program may have opened it during the
			// world even when nothing of the chain is drawn afterwards, and a flag left standing
			// would stop the values ever moving again, or the targets ever being cleared again if
			// this pack is turned back on.
			if (chain != null) {
				chain.closeFrame();
			}

			return false;
		}

		try {
			if (chainWanted) {
				chain.run();
			} else {
				// All a frame owes when the chain itself is not drawn. The ring buffers are not part
				// of it: closeFrame turns them below, drawn or not.
				chain.beginFrame();
			}
		} catch (GpuDeviceLossException e) {
			throw e;
		} catch (RuntimeException e) {
			stop();
			CoreVK.logger().error("CoreVK stopped drawing this pack after an error", e);
			chain.release();
		}

		// Outside the try: a frame that failed halfway still owes its flags and its ring buffers.
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

		// An empty composite list will never become drawable, unless computes stand alone in it and
		// give the frame something to run. Skipping the world for one that will not would leave the
		// player in a black pause with nothing left to compile.
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
			// Not about this pack: the device is gone, and PackChoice rethrows it out of a release
			// for the same reason.
			throw e;
		} catch (RuntimeException e) {
			// A pipeline the driver will not build throws out of precompilePipeline rather than coming
			// back invalid, which is what MoltenVK does with a stage Metal refuses, and nothing between
			// here and the game loop caught it. The reference stops drawing the pack and draws the
			// game's own picture on an exception while it builds its pipeline, and so does this.
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

		// Taken with the clear rather than after the geometry, because the clear is the line that
		// says the volume from here on holds THIS frame's writes and nothing older. A pack anchors
		// what it stores on the block the camera stands in, so that block is what says which cell
		// each identity lands in, and the frame reading it is not this one.
		//
		// Not taken at all from a chain that has never opened a frame: see everAdvanced. The value
		// store would answer the origin of the world, and the next frame would read a move of the
		// player's whole coordinates, which reanchor answers by emptying the volume.
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

		// The frame opens HERE, not at the first draw, and the compute's correctness hangs on it.
		// The values only move at beginFrame, so without this the dispatch reads the PREVIOUS
		// frame's numbers: the floodfill then runs under the old frameCounter parity and writes
		// the half this frame's gbuffers do not read, and every voxel light flickers as the
		// player moves. Idempotent for the rest of the frame, which sees the same numbers it
		// always did, only settled a moment earlier.
		chain.beginFrame();
		chain.reanchorCustomImages();
		chain.compute.dispatch(chain.values, chain.targets);

		// And the table goes back to the window every block of the chain is written under, because
		// the dispatch above leaves the light's standing behind it. This stage stands between the
		// two ranges the frame opens with, and a compute of the prepares writes its block off the
		// live table at the moment it is dispatched, where a pass reads a block written once at the
		// head of the frame: left flipped, a place with a begin ahead of this stage and a shadow
		// compute in it hands every compute of its prepares the light's window instead of the
		// screen's. Put back where it is flipped rather than at the head of each range, so that a
		// range drawn here later cannot forget what it never has to know.
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

		// Where the volume stands now, and it stays there until the shadow stage refills it.
		// Written rather than a flag lowered, and the difference is a frame that draws no shadow
		// map at all: the identities in the volume are still the last ones written, they still have
		// to follow the camera, and a second call of the same frame moves them by nothing.
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

		// The depth images the screen moved away from go before any geometry pass samples one: the
		// opaque image is refilled later in the frame at the new size, and until then a geometry
		// pass would read the frame before's at the old one, stretched over the screen.
		this.targets.depth().forgetOutgrown(main.width, main.height);

		// Taken here and not held from the load: it is the fog of THIS frame, and the whole value of
		// clearing colortex0 to it is that the sky the pack has not drawn over reads as distance
		// rather than as a hole. Read after beginFrame, which every caller of this does first, so the
		// state has already been advanced onto this frame.
		WorldState world = this.values.world();
		this.fogClear.set(world.fogR(), world.fogG(), world.fogB(), 1.0F);
		this.targets.clear(device.createCommandEncoder(), this.fogClear);
		// After the clear and not before. The clear is what pays the debt a fresh allocation owes,
		// and it is the one call here that can throw; raised first, the second call of the same frame
		// found the frame opened and skipped the clear it never got, and the debt died with the
		// exception rather than with the work. Of the same frame and not of the next one: closeFrame
		// lowers the flag on every pass through draw, so what this order protects is the pair of
		// callers above, and a frame whose throw escaped before draw was reached at all.
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
		// A per frame picture like the depths: a frame that takes none reads the pixel, never the
		// frame before's world.
		this.targets.copies().forget();
		this.filled = false;
		this.seeded = false;
		this.sceneDepth = false;

		// The depth taken before the hand is a per frame fact too, and the only one of the three
		// images that is not refilled at a fixed point of every frame: it is taken while the engine
		// draws the hand and nowhere else, so left standing it would serve the last frame that drew
		// one to every frame that did not. The image itself is handed back with the family rather
		// than with the frame, which is what the argument is.
		this.targets.depth().forgetPreHand(HandDraw.diverted());

		// The far terrain's pair is on the same per frame rule, and PackDepth says why.
		this.targets.depth().forgetDistant();

		if (this.block != null) {
			this.block.rotate();
		}

		this.terrain.rotate();
		// Only families the worker has finished translating. drawable() is true once the
		// composites and the terrain are compiled, which is earlier than leftover families
		// have been read, and walking those maps while the worker still fills them is the
		// crash at world join.
		int ready = Math.min(this.warmup.familiesReady(), this.families.size());
		for (int family = 0; family < ready; family++) {
			this.families.get(family).rotate();
		}
	}

	
	public static void close() {
		// Taken down before it is released, and not merely released: after this nothing draws again,
		// so a chain still answering as the live one would have the release announce that the next
		// device purge carries its pipelines, when the next purge is the one inside the device's own
		// close and that one carries nothing at all.
		PackChain chain = takeDown();
		if (chain != null) {
			chain.release();
		}

		FamilyWarmup.awaitAll();

		// The far terrain's two corner rings, the one-texel constants and the comparison sampler
		// survive every release on purpose, so the shutdown is the one caller that really frees
		// them.
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
			// The line is about what goes back, so it has to be able to say that nothing does. A
			// chain that threw was released where it threw and a world left before a frame was drawn
			// never allocated anything, and either way announcing nought mebibytes returning reads
			// as a leak rather than as the release that already happened.
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

			// The volumes went back with the targets and the camera went back to nought, so what
			// the anchor names no longer exists. Lowered rather than left standing: this chain is
			// NOT replaced on the way out, so nothing else would ever reset it, and a world joined
			// again would be measured against where the player stood in the one they left.
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

		// Pipelines compile before anything of the chain is drawn. The world is not drawn at all for
		// those frames and the HUD is what the player sees, the corner's mark and its words saying
		// the pack is still compiling; drawable() keeps the terrain aside or the world would be
		// drawn into a colour target this has no final ready to bring back.
		//
		// The targets and the buffers first and the compilation second, which is not the order this
		// had. What the warm up holds back is the drawing and not the frame, so the block ring and
		// the targets are standing by the time the last program compiles rather than being allocated
		// in that one frame on top of it. The quad is made in prepare with them, and quad() says why
		// it is asked for from elsewhere as well.
		//
		// Outside any render pass, both of them: creating a texture or a buffer records a barrier
		// into the very command buffer a pass would be recording into, and the clears refuse
		// outright while one is open.
		if (!prepare(device, main) || !warm(device)) {
			return null;
		}

		GpuTextureView mainView = main.getColorTextureView();
		if (mainView == null) {
			return null;
		}

		// Every frame here as well, and for the same reason as the pipelines of the chain.
		boolean seeding = this.seed != null && this.seedEnabled && this.seed.prepare(device);

		// Once a frame, whichever half asks first. The blocks above all: the passes of the early half
		// have already been recorded against this very buffer by the time the late half runs, and
		// mapping it again under them is a write to memory a command is holding, even when the bytes
		// that go back are the same.
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
		// Clamped to the list, so that the rank of a chain whose every pass runs before the world is
		// one the walk below can reach rather than one nothing ever equals.
		int seedAt = seeding && ready.seeding()
				? Math.min(this.chain.chain().seed().map(ChainPlan.Seed::at).orElse(-1),
						this.programs.size())
				: -1;

		// Each pass opens and closes its own render pass. Closing one is what makes the next able
		// to read it: the Vulkan backend ends a pass with a full memory barrier, so the cost of
		// the chain is one whole serialisation of the GPU per program and there is no way around
		// it short of knowing which passes do not overlap.
		CommandEncoder encoder = device.createCommandEncoder();
		GpuBuffer buffer = this.block.currentBuffer();
		// The chains this walk has filled and nothing has written over since, by target and side.
		// Emptied at the head because the range starts after geometry the plan does not see, and
		// again wherever a write this loop cannot place lands: the seed paints targets of its own
		// list, and the deferred emptying inside a draw clears every target still owed one.
		this.currentChains.clear();
		for (int at = from; at < to; at++) {
			if (!this.seeded && at == seedAt) {
				paintSeed(encoder, ready);
				this.currentChains.clear();
			}

			PackPass pass = this.programs.get(at);

			boolean emptying = this.targets.hasPendingClears();
			// Where a pass of this index would be drawn, the seed above included: what a cut can still
			// hold here is of a family the frame runs after the world, the begins and the prepares
			// having a cut apiece that runs before it and paints no seed at all.
			dispatchStandalone(cut, at, Reach.AT_INDEX, encoder, device, ready, depth, distant,
					emptying);
			if (this.compute.hangsOff(pass.program())) {
				// The frame's clears are paid before the computes and not inside the draw after
				// them, where the first pass of the frame pays them: a compute storing into a
				// target still owed its clear would have its stores emptied by the very pass it
				// hangs off.
				if (emptying) {
					this.targets.flushPending(encoder);
				}

				// The computes hanging off this pass run right before it, on the halves it reads,
				// as CoreVK does, and before the chains below are filled, as CoreVK fills them after
				// its dispatch (CompositeRenderer.java:289-300): a compute storing into a target
				// the pass reads at a lod is what the chain has to be built from. Outside any
				// render pass: a dispatch is a command of its own, and the barriers around it are
				// what let the pass sample what the compute stored.
				this.compute.dispatchBefore(pass.program(), encoder, device, this.values,
						this.targets, pass.step(), depth, distant, ready.main().width,
						ready.main().height);
				this.currentChains.clear();
			}

			// Right before the first program that reads them after a write: a chain is only true
			// of the level nought it was built from, and every pass between the two may have
			// written it. The blits record outside any pass, which is why this is here rather
			// than inside the draw: a transfer cannot be recorded while a pass is. A chain filled
			// for an earlier reader and written over by nothing since is still true, so it is not
			// refilled: two readers in a row used to pay two whole chains for one image.
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

		// A rank that falls exactly on the end of this half is painted here, at its tail, and never
		// at the head of the next one. The world is drawn before the deferred stage and not after
		// it: walked as a half open range alone, a seed whose rank equals deferredEnd() - which is
		// every place that ships no deferred at all, Body Camera's overworld among them - missed the
		// first half and led the second, and was painted at AfterLevel over the water, the terrain
		// and the particles the pack had just written.
		//
		// The same line still covers the place whose whole chain runs before the world: its rank is
		// the length of the list, so it equals the end of the last half.
		if (!this.seeded && seedAt >= from && seedAt <= to) {
			paintSeed(encoder, ready);
			this.currentChains.clear();
		}

		// The end of this cut, where the ones no pass of it follows are dispatched: the loop stops
		// before that index, so without this call the compute of a cut that draws no pass after it
		// would never run at all. It is where the frame really leaves this part of itself, and a
		// compute of a family the cut carries has nowhere later to go. Behind the seed for the reason
		// the walk's own dispatch is behind it.
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

		// Where the far terrain is taken over, and it is here rather than where the take needs it:
		// this is the one point of the frame reached exactly when DH's far terrain matters to a
		// pack. What it costs is that a takeover lands on the NEXT frame, DH having drawn its LODs
		// long before this line; nothing of the picture turns on that, the frame before it being
		// what this engine draws with the door shut.
		DhLods.install();

		// And DH's own clouds with it, on the pack's word and on nothing else: they are drawn whether
		// or not the far terrain is taken over, and handed back with the pack in release.
		DhLods.clouds(chain.values.avoidsDhClouds());

		GpuTextureView served = chain.distant.served();
		if (served == null && !chain.readsDistantDepth()) {
			return;
		}

		// Caught like every other point the game calls this engine back at: an exception here reaches
		// the game through an event handler and comes back on the very next frame.
		try {
			if (served == null) {
				// Nothing of the far terrain in view, which is not the far terrain being absent: the
				// pack was told it is there and reads its depth by texel. PackDepth.takeDistantNothing
				// says why the one texel white will not do for that.
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

		// Caught like every other point the game calls this engine back at: an exception here reaches
		// the game through an event handler and comes back on the very next frame.
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

		// Caught like every other point the game calls this engine back at: an exception here reaches
		// the game inside its own frame graph and comes back on the very next frame.
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
		// The game's answer is asked last, after everything that is this engine's own: a game that
		// cannot redirect says so in the log once, and only where there was something to redirect.
		if (disabled || chain == null || device == null || minecraft == null || !chainWanted
				|| chain.features == null || !GameRender.redirectsFeatures()) {
			return;
		}

		RenderTarget main = minecraft.gameRenderer.mainRenderTarget();

		// Caught like every other entry point this bus calls. These two were the only ones without
		// it, and they are the worst place to be missing one: an exception here reaches the game
		// through an event handler and comes back on the very next frame, so what the player sees
		// is not a pack that stopped drawing but a game that will not run. The layer's compile is
		// inside it too, a pipeline the driver refuses throwing rather than coming back invalid.
		try {
			// The layer's own pipeline is compiled on the refused frames too, and deliberately
			// before the question below rather than after it: it is one more pipeline the frame
			// that finally draws would otherwise compile on top of the pack's last one.
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
			// The overrides are cleared on the way out rather than left half set: one standing past
			// this point swallows every later feature draw of the frame.
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
		// The early half is the gate and not drawable(): a frame that never reached it has no
		// deferred stage behind the target, and a copy of that is the picture this refuses.
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
		// Always put back, whatever else happens below: overrides left standing past this point
		// would swallow every later feature draw of the frame.
		GameRender.endFeatureRedirect();

		PackChain chain = active;
		if (chain == null || !chain.redirected) {
			return;
		}

		// Taken down before anything can throw, so the pair is balanced by the frame that opened it
		// and never by the next one. Whether the chain may draw cannot have moved between the two
		// halves of this bracket, the warm up turning on AfterOpaqueFeatures which is the event
		// openFeatures itself is called from, but the flag makes that a fact rather than a timing.
		chain.redirected = false;

		GpuDevice device = RenderSystem.tryGetDevice();
		if (disabled || device == null || chain.features == null) {
			return;
		}

		try {
			ChainPlan.Attachment into = chain.features.into();
			GpuTextureView view = chain.targets.view(into.target(), into.side());
			// This pass may be the frame's first to attach that target, so it takes the emptying
			// still owed, the way every attaching pass does: composed onto a debt left standing,
			// the layer would sit on stale texels and be erased with them at the deferred flush.
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

		// A place with nothing at all in this range is left alone entirely, and the early return is
		// the whole of what keeps it so: everything below opens the frame, writes the blocks and
		// clears the targets, and a place with neither family goes on paying all three at the moment
		// it always paid them at rather than at the head of the frame. A compute standing on its own
		// is something in the range: it is then all the range runs, and refusing it for want of a pass
		// would drop the one thing the pack asked for there.
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

		// The same early return as the begins', on this range's own emptiness. Together the two keep
		// a place with neither family out of the head of the frame entirely.
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
			// Null for the far plane rather than an image, which is what the game has left in its own
			// depth at this point of the frame. The far terrain's is read inside the window, so what
			// goes in is the image the frame before drew and not the nothing this frame holds, and it
			// is the one WITH the water: CoreVK hands dhDepthTex0 the live texture of Distant Horizons,
			// which after a frame it drew holds the far terrain's water as well.
			drawRange(device, ready, from, to, null, depth.distantScene(), false, cut);
		} finally {
			depth.frameBefore(false, ready.main().width, ready.main().height);
		}
	}

	private void drawEarly(GpuDevice device) {
		if (this.early) {
			return;
		}

		// First, because a frame that never reached the graph's setup with a chain to draw still owes
		// its begins and its prepares, and owes them before anything below reads what they wrote. On
		// every ordinary frame both have already run and the two calls cost a field read each.
		//
		// WHICH FRAMES REACH THEM HERE, since the depth they read on this road is not the one they
		// read on the other. The setup runs at the head of every level frame, LevelRenderer.render on
		// Fabric and the frame graph event on NeoForge, so the only frames left to this line are the
		// ones where the chain could not draw yet when the graph was built and can by now: the frame
		// a load or a reload finishes warming on, ready() compiling one pipeline per call and being
		// called twice a frame, and a frame the pack was turned on in the middle of.
		//
		// THE DIVERGENCE THAT LEAVES. CoreVK always draws them ahead of its own copies: its begins at
		// CoreVKRenderingPipeline.java:1014 and its prepares at :1025, both before beginHand rewrites
		// noHand at :1043-1048, so what they read for depthtex2 and for the far terrain is the frame
		// before's whatever the frame did. Here EngineStages.java:227 and :237 have already taken
		// both by the time this line is reached, so on these frames the window serves this frame's
		// images instead of the previous frame's. It cannot be matched without keeping a second copy
		// of each all frame long, which is a full screen image apiece to be right on the frames a
		// pack finishes compiling. What it costs is that a prepare reading depthtex2 or dhDepthTex
		// there sees the world of the frame it is opening rather than the one behind it, on the one
		// frame per load where nothing else of the pack is on screen yet either.
		drawBegins(device);
		drawPrepares(device);

		Ready ready = ready(device);
		if (ready == null) {
			return;
		}

		this.early = true;

		// The depth of the opaque world, taken before anything translucent is drawn, which is what
		// the OptiFine model calls depthtex1. CoreVK takes it at the same moment, beginTranslucents,
		// before running its deferreds; the deferreds read it too, and nothing between here and the
		// world's translucents writes the game's depth, so one image serves the whole rest of the
		// frame. Outside any render pass, exactly as the copy this replaces had to be.
		this.targets.depth().takeOpaque(device.createCommandEncoder(), device, this.quad,
				ready.depthView(), ready.main().width, ready.main().height);

		// Here and not after the deferreds, because here is CoreVK's beginHand: it is called at the
		// head of corevk$beginTranslucents, MixinLevelRenderer.java:271, which is before the solid
		// hand, before the world's translucents and before beginTranslucents runs the deferred
		// stage. So the deferreds below read the value of THIS frame rather than of the one before.
		// What is NOT the same is that the hand has already been drawn by the time this line is
		// reached, which is why the fold reads the copy kept before it rather than the image the
		// line above has just taken. Outside any render pass, since it opens one.
		//
		// The begins and the prepares are not in the range below. Both run ahead of beginHand under
		// CoreVK: the begins at CoreVKRenderingPipeline.java:1014, the prepares at :1025 from inside
		// renderShadows and so before the opaque world exists at all, beginHand itself at :1043. So
		// what they read of this texel is the frame before's. drawBegins and drawPrepares run them at
		// that same point of the frame here, and say which of the depth names is the frame before's.
		sampleCenterDepth(device);
		// Here because the opaque depth taken above is this frame's, and because the matrices have
		// been advanced once for this frame already, so what the pass calls the previous pair really
		// is the frame before rather than this one. Both halves of that are the same boundary
		// FrameState.advance draws, and moving this line either side of it would reproject a frame
		// against itself and write a screen of noughts that looks exactly like a still camera.
		// The gate is asked BEFORE anything is built for the call: an encoder made in the argument
		// list is two allocations a frame for a player who never turns any of this on.
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
			// Said once, because the alternative is a chain that silently runs entirely after the
			// world again: nothing on screen tells the two apart, and the pack that needs the split
			// is the one whose water disappears. The first count is said apart from the second for
			// the same kind of reason: a prepare run after the world writes over the gbuffers it was
			// meant to be written over by, and that too looks like a pack of its own rather than
			// like a misplaced stage.
			//
			// A place with no full screen pass says what it does instead. The line below would read
			// as three noughts and two empty lists, which is a chain that runs nothing at all, and
			// what this one really runs is its computes: they are named, since a place that draws no
			// pass has nothing else to be recognised by.
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

		// The first of the frame's two takes, for the translucent passes drawn inside the game's
		// features window that opens right after this: the translucent half of the entities reads
		// the opaque world as the deferreds and the seed left it, which is what stands here.
		// takeReadCopies says where the second one is and why there are two.
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

		// The depth of the whole scene, which by now carries the world's translucents and the
		// features that were redirected into the pack's image. Kept apart from the opaque world's
		// rather than once for both, because the two halves are not asking the same question: a
		// composite that read the opaque world would blur and fog straight through water, and nothing
		// about that fails.
		//
		// Reached when nothing kept it earlier in the frame, which is normally a frame with no
		// always-on-top pass to keep it at; see sceneDepth. Normally and not always, which is why the
		// line says what was seen and not why: a take refused at that pass, or a reload that put a
		// new chain under the frame the old one took it in, both land here on a depth already
		// cleared, and a cause said once a load would be wrong for the whole session.
		if (keepScene(device, ready.depthView(), ready.main().width, ready.main().height)
				&& !this.saidAfterTheWorld) {
			this.saidAfterTheWorld = true;
			CoreVK.logger().info("The scene's depth is kept after the world, nothing having kept it "
					+ "earlier in this frame");
		}

		// The far terrain's depth with its water in, which the composites read as dhDepthTex0. On a
		// frame the pack served only the opaque half this is that half over again, which is also
		// what CoreVK's live image holds then.
		GpuTextureView distantServed = this.distant.served();
		if (distantServed != null) {
			this.targets.depth().takeDistantScene(device.createCommandEncoder(), device, this.quad,
					this.distant.blendedServed(), this.distant.worldServed(), distantServed,
					distantServed.getWidth(0), distantServed.getHeight(0));
		}

		drawRange(device, ready, deferredEnd(), this.programs.size(), this.targets.depth().scene(),
				this.targets.depth().distantScene(), true, Cut.AFTER_TRANSLUCENTS);

		// After the whole chain and before the kept targets are copied back, which is where a final
		// would have drawn. Only on a pack that ships none; ChainPresent says what it stands in for.
		if (this.present != null && this.present.prepare(device)) {
			this.present.draw(device.createCommandEncoder(), this.quad, ready.mainView(),
					this.targets);
		}

		// Outside any pass, and after the last one. Only the targets the pack keeps between frames
		// and that the chain left on the far half are copied: the next frame walks from an empty
		// flipped set and would otherwise be handed what was written two frames ago.
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
		// One pixel of the sentinel where no mask has been allocated yet, which reads as a screen the
		// pack wrote nowhere on and hides nothing. The mask itself is emptied at the head of every
		// frame, so a frame no program of the pack drew in is served an empty one rather than the
		// last one that was written.
		GpuTextureView covered = this.targets.coverage();
		// Whatever is still owed an emptying is owed it HERE at the latest, the mask above first
		// among them: it rides the load-op of the first geometry pass that attaches it, which is
		// free, but a frame in which the pack's geometry opens no pass at all never reaches that
		// load-op. The mask would then still hold the LAST frame's depths, compared against this
		// frame's with the camera moved in between, and the seed would repaint the pack's geometry
		// over most of the screen.
		this.targets.flushPending(encoder);

		// The far terrain's own depth rides the same fallback: a frame it drew nothing in reads as
		// nothing of the pack's standing there, through the sentinel sitting outside the range on
		// the clear's side.
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
				// The plan and the programs come out of one reading of the pack, so this is the
				// pack disagreeing with itself rather than anything a pack can cause.
				throw new IllegalStateException(pass.program() + " is in the chain of "
						+ this.chain.packName() + " and was never translated");
			}

			built.add(new PackPass(this.chain.place(), pass.program(), loaded, pass, this.targets,
					this.values, this.load, offset));
			offset += Mth.roundToward(PackPass.uniformSizeOf(loaded), alignment);
		}

		this.programs = List.copyOf(built);
		this.standalone = standaloneOf(built);
		// Asked of the plan and not of the list: ordered() puts the final at the end when there is
		// one, and where there is none the last of the list is an ordinary composite that writes
		// its own targets. Drawing that one onto the game's target would be the chain's middle
		// painted over the screen.
		this.last = plan.last().isEmpty() || built.isEmpty() ? null : built.get(built.size() - 1);
		// Or a compute hanging off a pass: it is handed the same texel, so it arms the same fold.
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
			// The plan named it and the schedule did not, which is the plan disagreeing with itself
			// rather than anything a pack can cause. Left undispatched: a compute pushed with no
			// halves would be handed no colour target at all and throw once per frame.
			if (step == null) {
				CoreVK.logger().warn("compute of {} is not dispatched: the schedule gives it no "
						+ "halves to read", program);
				continue;
			}

			// The same test the plan stops its walk of the overrides on, so that the moment this
			// dispatch takes and the halves the plan says it reads are the one answer.
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

		// Terrain is the world frame. Complementary Unbound's leftover families are the minute
		// between packs; the worker compiles them while the world is already being played, and a
		// first draw that outruns it falls back here. Terrain is a handful of pipelines, and the
		// first world frame hitches without them.
		//
		// THE LEFTOVERS ARE NOT COMPILED ON THIS THREAD, and that is a decision rather than an
		// omission. Doing it a program a frame, here, cost two minutes at two frames a second on
		// entering a world with Complementary Unbound: one shaderc compile is about half a second
		// and a frame is worth sixteen milliseconds, so there is no per-frame budget it fits in
		// and spreading it only spaces the stalls out. What removed the stalls rather than moving
		// them is the worker: precompilePipeline is not safe off the render thread, its caches
		// and its compiler being shared and unguarded, but the create calls under it are once
		// those are bypassed, and FamilyWarmup says how the two halves are split.
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
			// Three buffers and a fence per turn, so a frame never writes over what the previous
			// one is still being read for.
			this.block = new MappableRingBuffer(() -> BLOCK_LABEL,
					GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, this.blockBytes);
		}

		// Compared against the window every frame rather than driven by an event: the resize event
		// fires too early, fires again when only the interface scale moved, and the panorama
		// capture takes the main target to 4096 without going through the game's resize at all.
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
		// The clocks, the counters and the previous frame's matrices move at the frame boundary and
		// never here: two passes of one frame have to be handed the same numbers, or the second one
		// silently reprojects against itself and a smooth() of the pack fades at twice the speed.
		// Idempotent, and whoever gets here first pays it, WHICH IS OFTEN NOT THIS LINE. A pack with
		// a begin opens the frame here, at the head of the level frame and one step ahead of the
		// shadow stage. A pack with no begin and a shadow compute opens it at dispatchShadowCompute
		// instead, PackChain.java:964, which stands between the two ranges and carries a reason of
		// its own; a pack with a prepare and neither of those opens it here too, one step behind
		// that stage. On a place with none of the three, the terrain opens it during the world and
		// this is free.
		beginFrame();

		// Said rather than inherited, and what makes it necessary is that the table is shared rather
		// than the moment this line runs at. Every value below is left standing by whoever wrote it
		// last and is put back by nothing in between: from the head of the frame, where a place with
		// a begin or a prepare reaches this, that is the previous frame's shadow map, drawn at the
		// tail of it under the forward window; from the deferred stage, where a place with none of
		// the three still reaches it, that is this frame's sky, terrain and entities. The one flip
		// this frame makes between the two ranges puts itself back, dispatchShadowCompute saying
		// why. Every pass of the chain draws into a target the game rasterised under a reversed Z,
		// whichever it was.
		this.values.convention(ClipSpace.REVERSED);

		// The same, and for a sharper reason: renderStage is in the table a full screen pass shares
		// with a geometry one, so a stage left standing is read by the whole chain. On the frames
		// this runs after the sky it would tell every composite it was drawing the moon.
		this.values.renderStage(RenderStage.NONE);

		// And the same again for three of the four values a pass writes beside it, the alpha
		// reference being the one kept, ViewSource.passAlphaTest saying why. Two geometry families
		// set a matrix of their own: the sky pushes the rotation of the day, and the hand is drawn
		// under an identity model view and sets a whole projection besides, the head-up volume with
		// its clip depth squeezed to an eighth. Whichever set one last would otherwise stand in for
		// the camera in every composite of the frame, and in the decoded dump beside it; the hand's
		// case is the worst of the three, the squeezed volume reprojecting the whole screen.
		//
		// Nothing is owed in the other direction. GeometryProgram.writeBlock sets all of these again
		// before every geometry block it writes, the convention and the render stage included, so
		// what this line leaves neutral never reaches a pass of the world.
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

		// The world is printed beside the place because the fallback is silent otherwise: a pack
		// that serves no Nether draws it from the root, which is a line that reads as ordinary
		// until it is read next to the world it was chosen for.
		CoreVK.logger().info("Drawing {} from {} for {}, at {}x{}, {} full screen passes before the "
				+ "final", this.chain.packName(), place(), this.world, main.width, main.height,
				unfolded.passes().size());

		for (PackPass pass : this.programs) {
			CoreVK.logger().info("{}", pass.describe());
		}

		// Already whole sentences, and already the pack's own words where it has any: promotions,
		// sizes the pack asked for, the passes the pack keeps off, mipmaps nothing can generate,
		// and what this engine will not do.
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

		// Beside the copy above, which is the other half of the same subject: what this pack carries
		// from one frame to the next. Said at info and not with the warnings below, though they are
		// the same walk's answers, because a pack reading a target it keeps is a mechanism of the
		// pack rather than a hole in this engine, and CoreVK hands it exactly the same one. Read among
		// the warnings, BSL's colortex2 line was taken for a defect of the temporal antialiasing and
		// stayed a suspect for a fortnight.
		unfolded.history().forEach(note -> CoreVK.logger().info("{}", note));

		// The values the pack declares for itself, once for the whole chain: every program was
		// built against the one catalogue, so a pass saying it would say it once per program.
		this.values.notes().forEach(note -> CoreVK.logger().info("{}", note));

		// What the picture will be wrong about, in the pack's own terms and naming the pass that
		// reads it. This is the list that has to be read before the image is, or the image gets
		// read as though the chain were complete.
		unfolded.notes().forEach(note -> CoreVK.logger().warn("{}", note));
		this.programs.forEach(pass -> pass.notes().forEach(note -> CoreVK.logger().warn("{}", note)));

		announceSamplers();
	}

	private void announceSeed(boolean seeding) {
		Optional<ChainPlan.Seed> where = this.chain.chain().seed();
		if (seeding && where.isPresent()) {
			// What the seed still carries is not a constant, and this line is where docs/ sends a
			// reader for it. Composed from the switches rather than written out, or it goes stale
			// the day the next family lands, in the one place that must not.
			//
			// The switch and NOT what the pack turned out to serve, which is a real limit and not a
			// shortcut: this runs when the chain is built and the entity programs are read at the
			// first entity drawn, so on a pack that serves none, or whose targets the family refuses,
			// this line has already spoken. Those refusals each say so on a line of their own, which
			// is where a reader finds out; making this one wait for them would mean not printing the
			// scope at load at all.
			CoreVK.logger().info("{} carries the game's opaque frame, drawn in for {}: {} still come "
					+ "from the game, already tone mapped, and the translucent chunk pass blends onto "
					+ "this seed afterwards",
					TargetName.canonical(where.get().target()), where.get().from(), stillTheGame());
			// The number is worth printing on its own: it is the whole difference between a begin
			// that reads the world of this frame and one that reads what the clear left.
			CoreVK.logger().info("It is painted where the world would be drawn, after {} passes of "
					+ "the chain", where.get().at());
			// The pair to the terrain's own line. A frame where no program of the pack ran leaves
			// the mask on its sentinel and this covers the target whole, which is what it always did.
			CoreVK.logger().info("It paints where the world's depth stands in front of the one the "
					+ "pack's own geometry left, which is what the coverage mask carries: everywhere "
					+ "the pack wrote nothing, and everywhere the game has drawn over what it wrote");
			// Named because it is the difference between a gbuffer that agrees with itself and one
			// that carries the game's colour over the pack's normals, and neither shows as itself.
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

		// Named apart, because the two are one image whenever the hand is left to the game and two
		// whenever it is not, and which of the two a pack is reading is the whole of what it asked
		// for by writing depthtex2.
		List<String> pastTheHand = depths.stream().filter(SamplerPlan::preHandCopy).toList();
		if (!pastTheHand.isEmpty()) {
			CoreVK.logger().info("{} read the depth of the world from before the hand was drawn, "
					+ "which is an image of its own only while this engine draws the hand", pastTheHand);
		}

		// Said whichever way it went, because what is worth reading here is the work NOT done and
		// a silence would look the same as a guard that never fired. Off the whole pack's text
		// rather than off this chain's programs: that is the reading the guards go by, and a line
		// quoting a narrower one would send whoever chases a missing shadow to the wrong place. The
		// depth from before the hand has the centre depth fold beside that text, since the fold
		// reaches the image by no name a pack could be searched for, so its line names both.
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
		// The worker's flag first, so a compile still running for this chain stores nothing more
		// into programs nothing will ever draw again; what it already stored is destroyed with the
		// walk below, which is safe for objects nothing ever bound. Only families the worker
		// finished translating are walked, for the reason rotate() gives.
		this.warmup.release();
		int ready = Math.min(this.warmup.familiesReady(), this.families.size());
		for (int family = 0; family < ready; family++) {
			this.warmup.familyPrograms(family).forEach(DumpedProgram::discardAhead);
		}

		CustomImages.clear();
		// Beside it, and said here as well as at the head of a load: leaving a world releases the
		// chain without replacing it, so this is the moment the pack's declaration stops holding,
		// whether or not another load ever follows.
		TerrainSampler.breaksAnisotropy(false);
		this.compute.close();
		this.targets.release();
		if (this.features != null) {
			this.features.release();
		}

		// DH takes its far terrain back with the pack, its own frame order included: DhLods.handBack
		// says why nothing else would ever return it.
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

		// The one thing that cannot be handed back. A compiled pipeline lives in a cache keyed by
		// the pipeline object, and the only way to remove one is to empty the whole cache, which
		// waits for the queue to go idle and destroys the game's own pipelines with ours. Doing
		// that from here would do it in the middle of a frame whose commands are already recorded
		// against them. The reload's cost is therefore named and left: it is a few hundred
		// kilobytes of SPIR-V a reload, against the hundred megabytes of targets freed above.
		//
		// What frees them afterwards is not one answer any more, and the line says which it is. A
		// release on the way out of a world leaves this chain live, because it draws again the
		// moment one is joined, and the next device purge carries its pipelines over itself
		// (VulkanDeviceMixin). A release because the chain is being replaced or has been stopped
		// leaves it neither, and there the purge frees them as it always did.
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
