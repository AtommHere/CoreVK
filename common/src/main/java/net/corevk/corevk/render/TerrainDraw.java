package net.corevk.corevk.render;

import net.corevk.corevk.glsl.PackProgram;
import net.corevk.corevk.pack.program.TerrainPass;
import net.corevk.corevk.pack.source.OpenedPack;
import net.corevk.corevk.pack.source.ShadowCasters;
import net.corevk.corevk.pack.target.ChainPlan;
import net.corevk.corevk.pack.target.TargetPlan;
import net.corevk.corevk.render.timing.PassTimings;
import net.corevk.corevk.CoreVK;

import com.mojang.blaze3d.GpuDeviceLossException;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

public final class TerrainDraw {

	
	public enum Mask {

		
		UNREAD,

		
		WRITTEN,

		
		ABSENT
	}

	
	private static final List<TerrainPass> OPAQUE = List.of(TerrainPass.SOLID, TerrainPass.CUTOUT);

	
	private static volatile boolean wanted;

	
	private static volatile boolean shadowWanted;

	
	private static boolean shadowing;

	
	private static volatile List<String> carried = List.of();

	private final PackChain owner;
	private final Path packPath;
	private final String place;
	private final PackValues values;
	private final int load;
	private final ChainPlan plan;
	private final TargetPlan chainTargets;
	private final boolean chainRuns;
	private final ColorTargets targets;

	
	private Map<TerrainPass, PackProgram.Loaded> loaded = Map.of();
	private boolean shadowVoxelises;

	
	private List<String> declares = List.of();

	private Map<TerrainPass, TerrainProgram> programs = Map.of();

	
	private RenderPipeline lastBound;

	private TerrainProgram lastOwner;
	private boolean read;

	TerrainDraw(PackChain owner, Path packPath, String place, PackValues values, int load,
			ChainPlan plan, TargetPlan chainTargets, boolean chainRuns, ColorTargets targets) {
		this.owner = owner;
		this.packPath = packPath;
		this.place = place;
		this.values = values;
		this.load = load;
		this.plan = plan;
		this.chainTargets = chainTargets;
		this.chainRuns = chainRuns;
		this.targets = targets;
	}

	
	static void wanted(boolean asked) {
		if (!asked) {
			// Whether or not the flag itself moved. A pack refused after this was raised leaves the
			// flag standing with no chain behind it, and the elements are the half of the answer the
			// mesh really reads.
			carries(List.of());
			// And the game's own per face brightness with them, for the same reason and on the same
			// terms: the chunk passes are going back to the game's shader, which reads that factor
			// out of the vertex colour. Said here rather than at each caller that lowers this.
			FaceShading.none();
		}

		if (wanted == asked) {
			return;
		}

		wanted = asked;
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null || minecraft.level == null) {
			return;
		}

		CoreVK.logger().info("The pack's own terrain program is {} now, and the chunk mesh carries "
				+ "what it reads only while it does, so the sections are all built again",
				asked ? "wanted" : "no longer wanted");
		minecraft.levelExtractor.allChanged();
	}

	
	void read(OpenedPack pack) {
		if (!wanted) {
			return;
		}

		try {
			PackProgram.Terrain read = TerrainProgram.read(pack, this.place, this.values);
			this.loaded = read.programs();
			this.declares = read.carried();
			this.shadowVoxelises = this.loaded.entrySet().stream()
					.filter(entry -> entry.getKey().shadow())
					.map(Map.Entry::getValue)
					.anyMatch(PackProgram.Loaded::voxelises);
		} catch (IOException | RuntimeException e) {
			wanted = false;
			// And with it the game's own per face brightness, which the shader about to draw this
			// mesh expects to find in the vertex colour.
			FaceShading.none();
			CoreVK.logger().error("Could not read the terrain programs of "
					+ this.packPath.getFileName() + ", so the world keeps the game's own shader", e);
		}

		carries(this.declares);
	}

	
	private static void carries(List<String> asked) {
		if (carried.equals(asked)) {
			return;
		}

		carried = List.copyOf(asked);
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null || minecraft.level == null) {
			return;
		}

		CoreVK.logger().info("The chunk mesh carries {} now, so the sections are all built again",
				carried);
		minecraft.levelExtractor.allChanged();
	}

	
	public static List<String> carried() {
		return carried;
	}

	
	static void shadowWanted(boolean asked) {
		shadowWanted = asked;
	}

	
	public static void shadowStageFailed(RuntimeException e) {
		shadowWanted = false;
		CoreVK.logger().error("CoreVK stopped drawing the shadow map after an error in the stage, "
				+ "so every shadowtex lookup of the pack reads the far plane", e);

		TerrainDraw self = PackChain.terrain();
		GpuDevice device = RenderSystem.tryGetDevice();
		if (self == null || device == null) {
			return;
		}

		try {
			self.targets.shadow().clear(device.createCommandEncoder());
		} catch (GpuDeviceLossException lost) {
			throw lost;
		} catch (RuntimeException second) {
			// Not rethrown: this is the handler the bus called, and the error above is the one worth
			// reading. A pass left open by whatever threw there is enough to refuse a clear, so the
			// second one is said rather than swallowed - it is what makes the line above stop being
			// true.
			CoreVK.logger().error("The shadow map could not be emptied either, so what the pack reads "
					+ "as a shadow is whatever the frame that failed left in it", second);
		}
	}

	
	public static ShadowCasters shadowCasters() {
		TerrainDraw self = PackChain.terrain();

		return self == null ? DEFAULT_CASTERS : self.values.shadowCasters();
	}

	
	public static float entityShadowDistance() {
		TerrainDraw self = PackChain.terrain();

		return self == null ? -1.0F
				: self.values.entityShadowDistance(PackChoice.shadowDistance(), renderDistanceChunks());
	}

	
	public static float shadowRenderDistance() {
		TerrainDraw self = PackChain.terrain();

		return self == null ? -1.0F
				: self.values.shadowRenderDistance(PackChoice.shadowDistance(), renderDistanceChunks());
	}

	
	public static int shadowDistanceChunks() {
		return forcedShadowDistanceChunks().orElseGet(PackChoice::shadowDistance);
	}

	
	public static OptionalInt forcedShadowDistanceChunks() {
		TerrainDraw self = PackChain.terrain();

		return self == null ? OptionalInt.empty() : self.values.forcedShadowRenderDistanceChunks();
	}

	
	private static int renderDistanceChunks() {
		Minecraft minecraft = Minecraft.getInstance();

		return minecraft == null ? 0 : minecraft.options.getEffectiveRenderDistance();
	}

	
	private static final ShadowCasters DEFAULT_CASTERS = ShadowCasters.DEFAULT;

	
	static boolean shadowsAsked() {
		return wanted && shadowWanted;
	}

	
	public static boolean shadows() {
		return wanted && shadowWanted && PackChain.terrain() != null;
	}

	
	public static boolean shadowAmortisable() {
		TerrainDraw self = PackChain.terrain();

		return self != null && !self.shadowVoxelises;
	}

	
	public static boolean shadowMapServed() {
		TerrainDraw self = PackChain.terrain();

		return self != null && shadows() && self.shadowsServed();
	}

	
	public static void keepShadowMap() {
		TerrainDraw self = PackChain.terrain();
		GpuDevice device = RenderSystem.tryGetDevice();
		if (self != null && device != null) {
			self.targets.shadow().keep(device.createCommandEncoder());
		}
	}

	
	public static boolean shadowMapKept() {
		TerrainDraw self = PackChain.terrain();

		return self != null && self.targets.shadow().hasKept();
	}

	
	public static void copyShadowDepth() {
		TerrainDraw self = PackChain.terrain();
		GpuDevice device = RenderSystem.tryGetDevice();
		if (self != null && device != null && self.owner.mayReadShadowWithoutTranslucents()) {
			self.targets.shadow().copyWithoutTranslucents(device.createCommandEncoder());
		}
	}

	
	public static void mipShadowMap() {
		TerrainDraw self = PackChain.terrain();
		GpuDevice device = RenderSystem.tryGetDevice();
		if (self != null && device != null) {
			self.targets.shadow().generateMipmaps(device.createCommandEncoder());
		}
	}

	
	public static boolean openShadowStage() {
		TerrainDraw self = PackChain.terrain();
		GpuDevice device = RenderSystem.tryGetDevice();
		if (self == null || device == null) {
			return false;
		}

		ShadowTargets shadow = self.targets.shadow();
		if (!shadows() || !self.shadowsServed() || shadowDistanceChunks() == 0) {
			// A shadow distance of nought is not a very short walk, it is no stage at all, and the
			// test is on the CHUNKS rather than on the blocks the walk is bounded by. That is CoreVK's
			// own: renderShadows returns at its first line when the distance the pack or the player
			// settled on is zero (shadows/ShadowRenderer.java:384-387), before a frustum is built or
			// a target is touched. Bounding the walk at zero blocks instead would cost a full walk
			// and a full clear to draw nothing.
			//
			// What the pack reads then is the DEPTH emptied to the far plane, both names of it,
			// because the clear below empties the depth whichever way the pack's own keep directive
			// reads (ShadowTargets.stash raises it for every frame the map exists). A shadowcolor
			// the pack asked to KEEP is a different matter and is NOT emptied (the same method,
			// through ShadowTargets.wanted, which is what reads the directive): it holds the last
			// frame the stage drew, for as long as the setting stays at nought. CoreVK returns before
			// touching any of its targets, so there both halves keep the last frame.
			//
			// A pack that serves no shadow program gets no shadow pass, which is CoreVK's rule, and
			// at the end of a frame it is also the only safe answer: with nothing of ours to hand
			// the renderer, the pass it opens for itself is the game's own target, and the stage
			// would paint the world over the finished image. The map is emptied rather than left
			// standing, so a program broken mid-session reads as no shadow and not as the last
			// map it ever drew, frozen.
			//
			// The stage switched off by the engine option does not come through here, whatever the
			// shadows() test above may suggest: the frame graph stops capturing the camera at the
			// head of the frame, so the stage returns long before it reaches this. Its map is the
			// one ShadowTargets.ensure empties where it allocates, which is the same answer at no
			// cost per frame.
			shadow.clear(device.createCommandEncoder());

			return false;
		}

		if (!shadow.ensure()) {
			return false;
		}

		// A frame that puts the kept map back must not empty it first, and the emptying is deferred
		// rather than encoded, so refusing it here is the whole of the refusal: the load-op the
		// first pass would carry is what would wipe the restore, and it is armed by this line.
		if (ShadowAmortisation.drawTerrainThisFrame() || !shadow.hasKept()) {
			shadow.defer();
		} else {
			shadow.restore(device.createCommandEncoder());
		}

		// After the defer, so that a refusal below still empties the map, and before the stage is
		// declared open, which is the whole point of the step.
		if (!self.shadowsPrepared(device)) {
			shadow.flushPending(device.createCommandEncoder());
			return false;
		}

		return shadow.depth() != null;
	}

	
	private boolean shadowsPrepared(GpuDevice device) {
		for (TerrainPass pass : TerrainPass.values()) {
			if (!pass.shadow()) {
				continue;
			}

			TerrainProgram program = this.programs.get(pass);
			try {
				if (program.prepare(device, null) == null) {
					// The line this step exists for. Nothing on screen can say it: the artefact lasts
					// one frame, and a stage that never opens leaves no trace at all.
					CoreVK.logger().warn("{} refused to prepare, so the shadow stage does not open: "
							+ "with nothing of ours to hand the renderer, the pass it opens for itself "
							+ "is the game's own target and the stage would paint the world over the "
							+ "finished image", program.path());

					return false;
				}
			} catch (GpuDeviceLossException e) {
				throw e;
			} catch (RuntimeException e) {
				shadowWanted = false;
				CoreVK.logger().error("CoreVK stopped drawing the shadow map after an error in "
						+ program.path(), e);

				return false;
			}
		}

		return true;
	}

	
	private boolean shadowsServed() {
		for (TerrainPass pass : TerrainPass.values()) {
			if (pass.shadow()) {
				TerrainProgram program = this.programs.get(pass);
				if (program == null || !program.servable()) {
					return false;
				}
			}
		}

		return true;
	}

	
	public static void shadowPass(Runnable draw) {
		TerrainDraw self = PackChain.terrain();
		if (self == null || !shadows() || self.targets.shadow().depth() == null) {
			return;
		}

		shadowing = true;
		try {
			draw.run();
		} finally {
			shadowing = false;
		}
	}

	
	public static Matrix4f shadowFrustum(Matrix4f dest) {
		TerrainDraw self = PackChain.terrain();

		return self == null ? null : self.values.shadowFrustum(dest);
	}

	
	public static ShadowCullPlan shadowCullPlan(Vector3f light, Matrix4f camera) {
		TerrainDraw self = PackChain.terrain();

		return self == null ? null : self.values.shadowCullPlan(PackChoice.shadowDistance(),
				renderDistanceChunks(), light, camera, self.voxelisesShadow());
	}

	
	private boolean voxelisesShadow() {
		return this.shadowVoxelises;
	}

	
	public static boolean drawnShadowPair(Matrix4f modelView, Matrix4f projection) {
		TerrainDraw self = PackChain.terrain();
		if (self == null) {
			return false;
		}

		self.values.drawnShadowPair(modelView, projection);

		return true;
	}

	
	public static boolean drawingShadow() {
		return shadowing;
	}

	
	private static TerrainPass drawn(TerrainPass pass) {
		return shadowing ? pass.inShadow() : pass;
	}

	
	public static boolean asked() {
		return wanted;
	}

	
	public static Mask opaqueMask() {
		TerrainDraw draw = PackChain.terrain();
		if (draw == null || !wanted) {
			return Mask.ABSENT;
		}

		if (!draw.read) {
			return Mask.UNREAD;
		}

		for (TerrainPass pass : OPAQUE) {
			TerrainProgram program = draw.programs.get(pass);
			if (program == null || !program.covers()) {
				return Mask.ABSENT;
			}
		}

		return Mask.WRITTEN;
	}

	
	public static RenderPipeline pipeline(TerrainPass pass, VertexFormat format,
			GpuTextureView atlas) {
		TerrainDraw draw = PackChain.terrain();
		TerrainPass drawn = drawn(pass);
		if (draw == null || !wanted || drawn == null) {
			return null;
		}

		try {
			return draw.prepare(drawn, format, atlas);
		} catch (GpuDeviceLossException e) {
			throw e;
		} catch (RuntimeException e) {
			wanted = false;
			// The same as at the read above: the mesh is about to be drawn by the game's shader.
			FaceShading.none();
			CoreVK.logger().error("CoreVK stopped drawing the terrain after an error", e);

			return null;
		}
	}

	
	public static void sampler(GpuSampler game) {
		TerrainDraw draw = PackChain.terrain();
		if (draw == null) {
			return;
		}

		GpuSampler ours = LegacyTerrainFilter.armed() ? null : TerrainSampler.get();
		GpuSampler bound = ours == null ? game : ours;
		draw.programs.values().forEach(program -> program.sampler(bound));
	}

	
	public static void bind(RenderPass pass, RenderPipeline bound) {
		TerrainDraw draw = PackChain.terrain();
		if (draw == null) {
			return;
		}

		TerrainProgram program = draw.owner(bound);
		if (program != null) {
			program.bind(pass);
		}
	}

	
	private TerrainProgram owner(RenderPipeline bound) {
		if (this.lastBound == bound && !PassTimings.keepRedoneWork()) {
			return this.lastOwner;
		}

		PassTimings.censusProgramWalk();
		this.lastBound = bound;
		this.lastOwner = null;
		for (TerrainProgram program : this.programs.values()) {
			if (program.owns(bound)) {
				this.lastOwner = program;
				break;
			}
		}

		return this.lastOwner;
	}

	
	public static RenderPassDescriptor descriptor(TerrainPass pass, GpuTextureView colour,
			GpuTextureView depth) {
		TerrainDraw draw = PackChain.terrain();
		TerrainPass drawn = drawn(pass);
		if (draw == null || !wanted || drawn == null) {
			return null;
		}

		// The same answer the pipeline gives, and it has to be the same one: the pipeline carries
		// a colour state per attachment this names, and one of the two refusing alone is setPipeline
		// throwing by name in the middle of Sodium's own draw. The two are asked at two points of one
		// pass, begin and render, and nothing between them moves the answer: it turns where the chain
		// warms up, which is either side of the whole pass and never inside it.
		if (!shadowing && !draw.shown()) {
			return null;
		}

		TerrainProgram program = draw.programs.get(drawn);

		return program == null ? null : program.descriptor(colour, depth);
	}

	
	private boolean shown() {
		return !this.chainRuns || this.owner.drawable();
	}

	private RenderPipeline prepare(TerrainPass pass, VertexFormat format, GpuTextureView atlas) {
		if (!this.read) {
			this.read = true;
			// Asked of the programs and not of the mesh, because a pack that serves no chunk program
			// at all declares nothing and asked for nothing: the mesh was never extended for it, and
			// comparing an empty list against Sodium's own four would put the whole pack away over a
			// chunk pass it never wanted. It keeps the game's own shader for the six passes, which is
			// a normal thing for a pack to do, and its sky and its chain go on being drawn.
			if (!this.loaded.isEmpty() && !TerrainProgram.carries(format, this.declares)) {
				this.owner.putAway("the chunk mesh does not carry what a terrain program reads");

				return null;
			}

			this.programs = TerrainProgram.build(this.loaded, this.values, this.load, format,
					this.plan, this.chainTargets, this.chainRuns, this.targets);
			// With the map it answers out of. A pipeline that outlived a rebuild would otherwise be
			// answered with the program of the map before it.
			this.lastBound = null;
			this.lastOwner = null;
		}

		TerrainProgram program = this.programs.get(pass);
		if (program == null) {
			return null;
		}

		GpuDevice device = RenderSystem.tryGetDevice();
		if (device == null) {
			return null;
		}

		if (!program.compile(device)) {
			return null;
		}

		// Not during the shadow stage, and that is not an optimisation. The stage runs once the
		// chain has closed the frame, so the per frame guards are down again: opening here would
		// advance the value store a second time, which turns every gbufferPrevious* of the next
		// frame into the current one, and clear the colour targets over what the chain just wrote.
		// Everything these two calls provide, the stage already has: the values were advanced when
		// this frame opened, and the map is ensured and emptied by openShadowStage.
		if (!shadowing) {
			this.owner.beginFrame();

			// Before the pipeline and before the pass, which is the whole point: the clears belong
			// ahead of the world now that something writes the pack's targets during it. And a frame
			// where the targets cannot be opened keeps the game's own shader outright: the pipeline
			// carries one colour state per attachment the descriptor would have named, and Sodium's
			// own pass, the only one left to bind it into, carries exactly one.
			if (!this.owner.openTargets(device)) {
				return null;
			}

			// After the two calls above and not with the guards at the top of the two doors, which is
			// the whole reason it sits here. The frame boundary hangs off whichever of the terrain and
			// the chain comes first, and while the chain is warming up the chain never gets far enough
			// to open one: refused at the door, these frames would leave the value store standing, and
			// the frame that finally drew would call the camera of several frames ago its previous one.
			// The clears are owed for the same reason, or the first frame drawn would compose targets
			// holding whatever the warm up left in them.
			if (!shown()) {
				return null;
			}
		}

		return program.prepare(device, atlas);
	}

	
	Collection<TerrainProgram> programs() {
		return this.programs.values();
	}

	
	void rotate() {
		this.programs.values().forEach(TerrainProgram::rotate);
	}

	void release() {
		this.programs.values().forEach(TerrainProgram::release);
		// The same forgetting a rebuild does: nothing reaches a released holder today, but a memo
		// that outlives the map it answers out of is exactly the shape of trap the rebuild guards.
		this.lastBound = null;
		this.lastOwner = null;
	}
}
