package net.corevk.corevk.sodium;

import net.corevk.corevk.mixin.access.MixinSodiumWorldRenderer;
import net.corevk.corevk.mixin.access.RenderSectionManagerAccessor;
import net.corevk.corevk.pack.source.ShadowCasters;
import net.corevk.corevk.render.BlockStateIds;
import net.corevk.corevk.render.DistantDraw;
import net.corevk.corevk.render.PackChain;
import net.corevk.corevk.render.ShadowAmortisation;
import net.corevk.corevk.render.ShadowCullPlan;
import net.corevk.corevk.render.ShadowGeometry;
import net.corevk.corevk.render.TerrainDraw;
import net.corevk.corevk.render.timing.RingTimings;
import net.corevk.corevk.CoreVK;

import com.mojang.blaze3d.GpuDeviceLossException;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import net.caffeinemc.mods.sodium.client.gpu.device.backend.DrawBackend;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.SortedRenderLists;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import net.caffeinemc.mods.sodium.client.util.GameRendererStorage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3d;
import org.joml.Vector3f;

public final class ShadowTerrain {

	
	private static final Matrix4f MODEL_VIEW = new Matrix4f();

	
	private static final Matrix4f LIGHT = new Matrix4f();

	
	private static final Matrix4f CAMERA = new Matrix4f();
	private static final Vector3f LIGHT_VECTOR = new Vector3f();

	private static Vec3 camera;

	
	private static int measured = -1;

	
	private static int walkKept;

	private static int walkDrawn;

	private static int walkTotal;

	private static boolean walkTerrain;

	private static @Nullable String walkCulling;

	private ShadowTerrain() {
	}

	
	public static void capture(Matrix4fc modelView, Vec3 cameraPosition) {
		MODEL_VIEW.set(modelView);
		camera = cameraPosition;
	}

	
	public static void draw() {
		try {
			walk();
		} catch (GpuDeviceLossException e) {
			throw e;
		} catch (RuntimeException e) {
			TerrainDraw.shadowStageFailed(e);
		}
	}

	private static void walk() {
		Vec3 camera = ShadowTerrain.camera;
		ShadowTerrain.camera = null;

		SodiumWorldRenderer renderer = SodiumWorldRenderer.instanceNullable();
		Minecraft minecraft = Minecraft.getInstance();
		if (camera == null || renderer == null || minecraft == null) {
			return;
		}

		// The same refusal the pipeline mixin makes, and it has to be made here too: under OpenGL
		// nothing of ours is ever served, so the stage would walk and draw the whole world a second
		// time with the game's own shader, into the game's own target. This is exactly the state a
		// failed Vulkan boot leaves the machine in.
		if (DrawBackend.BACKEND == DrawBackend.OPENGL) {
			return;
		}

		// The stage itself is never skipped any more. What a kept map buys is the OPAQUE world, and
		// that is decided inside draw below: the walk still runs, because it is what hands Sodium
		// the light's own occlusion tree, and everything that moves is still drawn, because a
		// caster one frame late is the only thing anybody could see.

		// Ordered so that a stage that cannot open leaves the render lists untouched: the walk
		// below hands them to the light, and from that point on the camera has to be given them
		// back whatever else happens.
		if (!TerrainDraw.openShadowStage()) {
			// Complementary Ultra still samples floodfill and WSR from gbuffers. The shadow
			// programs may be refused so the stage never opens, but the clear still has to run
			// or the voxel volume keeps stale writes; the compute itself runs at the head of
			// the frame, from the frame graph setup, whatever this stage does.
			PackChain.clearCustomImages();
			walkCulling = null;
			return;
		}

		RenderSectionManager manager =
				((MixinSodiumWorldRenderer) renderer).corevk$renderSectionManager();
		Matrix4f light = TerrainDraw.shadowFrustum(LIGHT);
		// Read here, in the same breath as the light's own matrix and off the same frame: the shape
		// the terrain is walked against is built from the camera's volume and the light's direction,
		// and the record's own note says what taking one of them a frame later would keep and drop.
		ShadowCullPlan plan = TerrainDraw.shadowCullPlan(LIGHT_VECTOR, CAMERA);
		if (manager == null || light == null || plan == null) {
			return;
		}

		// Counted only on the frame that will print it. It has to be taken HERE, the walk below
		// replacing the camera's render lists with the light's, but every other frame was walking
		// those lists for a line printed once per block table.
		//
		// Read once rather than again at the print, so a table installed between the two is named
		// on the next frame instead of at once. That is the right way round: the count in hand was
		// taken against the table that was standing when it was taken.
		boolean measuring = measured != BlockStateIds.generation();
		int seen = measuring ? sections(manager.getRenderLists()) : 0;

		ShadowCasters casters = TerrainDraw.shadowCasters();

		// The frame counter first, and it is the piece easiest to miss: the per region lists only
		// reset themselves for the first walk of a frame, so a second walk under the same number
		// appends to the camera's lists until it overflows
		// them. The walk itself is the synchronous fallback, which is the one path that neither
		// consults the asynchronous occlusion tree, empty for a viewport it has never seen, nor
		// waits for it.
		//
		// prepareRender would then rotate the indirect command ring. The camera already did that
		// at the top of the frame; a second rotate in the same frame is the stall #115 names.
		// The steps live here rather than as a default on the accessor: Mixin treats an interface
		// mixin with a default method as targeting an interface, and RenderSectionManager is a
		// class, so the config fails to prepare. The check is Mixin's own, seen on the Fabric boot.
		// keepShadowRotate puts the old call back so the two paths can be timed on the same jar.
		if (RingTimings.keepSecondRotate()) {
			manager.prepareRender();
		} else {
			RenderSectionManagerAccessor access = (RenderSectionManagerAccessor) manager;
			access.corevk$setFrame(access.corevk$getFrame() + 1);
			if (access.corevk$cameraChanged()) {
				access.corevk$invalidateRenderLists();
			}
		}
		try {
			// The shape the pack asked for, and a box around the camera cut out of it wherever a
			// shadow distance bounds the walk. Distance, default and Advanced keep that box and
			// no planes. Advanced is the named divergence ShadowCullFrustum.of carries:
			// Complementary Low lands there and the sweep pops leaves. The safe zone still
			// sweeps. Whether a bound applies at all is the pack's business at least as often as
			// the player's, most of the corpus declaring a render multiplier and being held at
			// its own half plane whatever the slider says, and the plan carries the arbitration
			// already made.
			ShadowCullFrustum.Chosen cull = ShadowCullFrustum.of(plan);
			Viewport viewport = new Viewport(cull.frustum(),
					new Vector3d(camera.x, camera.y, camera.z));
			// No fog, whatever the camera's walk was bounded by. With Sodium's fog occlusion on, the
			// walk stops at the fog's cull distance, and the camera's fog is the wrong bound for the
			// light: rain or water shortens it, and a hill that still casts into the view would
			// leave the map with it. CoreVK turns fog occlusion off for both walks whenever a pack is
			// loaded (compat/sodium/mixin/MixinRenderSectionManager.java, corevk$disableFogOcclusion);
			// the camera's walk here still keeps its own fog, a gap of the picture and not of the
			// map, and not this stage's to close.
			manager.finalizeRenderLists(minecraft.gameRenderer.mainCamera(), viewport,
					FogParameters.NONE, true);

			// The entities, now that the tree Sodium answers visibility from is the light's, and the
			// class note says what asking the camera's would drop. The block entities follow: this
			// is the first line of the stage at which the light has render lists of its own, and
			// they are what says which sections to ask. Sodium's door onto them is the one CoreVK
			// reaches through the game's extraction (shadows/ShadowRenderer.java:668, cancelled and
			// served by the same mixin); the game's own visible sections are never filled at all
			// under Sodium, so the walk that read them found a world with no chests in it.
			ShadowGeometry.gather(light, camera, casters);
			ClientLevel level = minecraft.level;
			if (level != null) {
				ShadowGeometry.gatherBlockEntities((state, partial) -> renderer.extractBlockEntities(
						minecraft.gameRenderer.mainCamera(), partial, level.destructionProgress(),
						state));
			}

			// Once per block table, and never on a frame where the camera saw nothing. Two equal
			// numbers mean the cull did not happen, and nothing on screen would say so. The table is
			// named because a second load of the same pack prints this again, word for word: it is
			// what tells the two readings apart, not a property of the cull itself.
			//
			// Two counts of the light's list rather than one, because they answer different
			// questions and only the second one compares with anything outside this engine. The
			// walked count is every section with something to render that the cull kept, which is
			// what says how tight the cull was; the drawn count leaves out those carrying no block
			// geometry, which are the ones a draw costs nothing for.
			count(manager.getRenderLists());
			walkTotal = manager.getTotalSections();
			walkTerrain = casters.terrain();
			walkCulling = cull.culling();

			if (measuring && seen > 0) {
				measured = BlockStateIds.generation();
				CoreVK.logger().info("shadow-cull {} kept={} camera={} drawn={} blocks={}",
						walkCulling, walkKept, seen, walkDrawn, measured);
			}

			// The anchor is NOT taken here. It moves where the opaque world is really drawn, inside
			// the call below, and a copy of that line left standing at this level is what made the
			// whole thing do nothing: it reset the interval on every frame, so the count never
			// elapsed and the map was filled again every time.
			draw(renderer, minecraft, camera);
		} finally {
			// The flag finalizeRenderLists just lowered, back up whatever happened above: the
			// camera's walk at the top of the next frame has to rebuild, or the world would be
			// drawn from the sun.
			((RenderSectionManagerAccessor) manager).corevk$setNeedsRenderListUpdate(true);
		}
	}

	private static void draw(SodiumWorldRenderer renderer, Minecraft minecraft, Vec3 camera) {
		// Sodium's own source for it, so that what reaches u_Globals is what would have reached it
		// anyway: this one carries the walk bob and the camera state's does not.
		Matrix4fc projection =
				((GameRendererStorage) minecraft.gameRenderer).sodium$getProjectionMatrix();
		ChunkRenderMatrices matrices = new ChunkRenderMatrices(projection, MODEL_VIEW);

		// The game's own chunk sampler, mipmapped and clamped, and it is NOT what the pack's shadow
		// programs read the atlas through: the renderer hands this to begin, where the chunk
		// renderer mixin settles the pack's sampler for every pass, this one included, so the shadow
		// half binds the same NEAREST sampler as the gbuffer half, through TerrainSampler and never
		// through this argument.
		//
		// The legacy switch does NOT part the two halves the way it reads: it puts the game's LINEAR
		// back through LegacyTerrainFilter, which is the sampler the renderer hands begin, and the
		// argument on this line is NEAREST rather than the game's. So the gbuffer half gets the
		// game's filter back and the shadow half stays NEAREST either way, and an A/B taken with
		// the switch measures the gbuffer change alone.
		//
		// What this argument reaches is Sodium's own shader, on a pass handed back to it,
		// and there CoreVK hands NEAREST down the same road (shadows/ShadowRenderer.java:389), where
		// the gbuffers hand it the game's LINEAR: NEAREST here too, so that the one pass Sodium
		// draws itself filters as it does under CoreVK. CoreVK's SodiumShader.setupState binds its own
		// instead (pipeline/programs/SodiumShader.java:131), and so does ours.
		GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST, true);

		ShadowCasters casters = TerrainDraw.shadowCasters();

		// Before geometry writes, matching CoreVK clearing custom images at the head of the
		// shadow stage. Complementary's voxel volume is marked clear; the floodfill is not.
		PackChain.clearCustomImages();

		// The opaque world, and the one thing a kept map spares. Restored rather than drawn when the
		// setting says so and a map is in the store, the restore itself having already been encoded
		// where the clear would have been: the pack's own refusal still comes first, since a pack
		// that keeps the opaque world out of its map has nothing to keep.
		boolean drawTerrain = ShadowAmortisation.drawTerrainThisFrame() || !TerrainDraw.shadowMapKept();

		// Refused by the pack rather than skipped for cheapness.
		if (casters.terrain() && drawTerrain) {
			// Distant Horizons' far terrain goes first and INSIDE this word, both of which are
			// CoreVK's. DH hangs its LOD draws off the HEAD of ChunkSectionsToRender.renderGroup
			// (neoforge/mixins/client/MixinChunkSectionsToRender.java:67-74), and the only call to
			// that method in CoreVK's shadow stage is the one inside its own shadowTerrain test
			// (shadows/ShadowRenderer.java:508-511). So a pack that keeps the opaque world out of
			// its map keeps the far terrain out with it, and getting this wrong is not a nuance: a
			// pack that asked for neither would see LODs in its map here and none under CoreVK.
			DistantDraw.shadow(false, camera);
			TerrainDraw.shadowPass(() -> SodiumApi.drawShadowLayer(renderer,
					ChunkSectionLayerGroup.OPAQUE, matrices, camera, sampler));
		}

		// The store is taken HERE and nowhere else: with the opaque world in the map and before the
		// first thing that moves goes into it. A store taken later would carry a mob, and every
		// frame restoring it would paint that mob's old place back under the new one. Taken only where
		// a later frame may put it back: with the reuse off the copy is the whole map and its colours
		// on every frame, read by nobody, and it cost the M4 about eight per cent of its frames.
		if (drawTerrain && casters.terrain()) {
			if (ShadowAmortisation.keepsDrawnMap()) {
				TerrainDraw.keepShadowMap();
			}
			ShadowAmortisation.drawn();
		}

		// Everything that moves, between the opaque world and the copy, which is where CoreVK puts it
		// (shadows/ShadowRenderer.java:584 then :588). It matters that it is before the copy and not
		// after: shadowtex1 is the map WITHOUT the translucent half, and a mob belongs in it. Drawn
		// after the copy, every caster that moves would be missing from the one name half the corpus
		// reads its shadows through.
		ShadowGeometry.draw(camera);

		// Between the translucent group and everything else, and nowhere else: this is the one moment
		// shadowtex0 and shadowtex1 hold different things, and what separates them is exactly the
		// draw that comes next. The renderer closes its own render pass before returning, and the
		// walk above closes its last one, so a copy here is outside one.
		TerrainDraw.copyShadowDepth();

		if (casters.translucent() && !Boolean.getBoolean("corevk.probeNoShadowTranslucent")) {
			// And its water half here, after the copy and inside the word that governs the world's
			// own translucent group, for the two reasons the opaque half is where it is: DH's hook
			// is the head of this very call, and CoreVK makes it inside its own shadowTranslucent test
			// (shadows/ShadowRenderer.java:598-601). shadowtex1 is the map WITHOUT the translucents,
			// and far water belongs on the same side of it as near water.
			DistantDraw.shadow(true, camera);
			TerrainDraw.shadowPass(() -> SodiumApi.drawShadowLayer(renderer,
					ChunkSectionLayerGroup.TRANSLUCENT, matrices, camera, sampler));
		}

		// And the chain last of all, on a map nothing else will write this frame, which is where
		// CoreVK fills it too (shadows/ShadowRenderer.java:613-615). After the copy and not before:
		// shadowtex1 takes its own chain over the base the copy just wrote.
		TerrainDraw.mipShadowMap();
	}

	
	private static int sections(SortedRenderLists lists) {
		int count = 0;
		var iterator = lists.iterator(false);
		while (iterator.hasNext()) {
			ChunkRenderList list = iterator.next();
			count += list.size();
		}

		return count;
	}

	
	private static void count(SortedRenderLists lists) {
		int kept = 0;
		int drawn = 0;
		var iterator = lists.iterator(false);
		while (iterator.hasNext()) {
			ChunkRenderList list = iterator.next();
			kept += list.size();
			drawn += list.getSectionsWithGeometryCount();
		}

		walkKept = kept;
		walkDrawn = drawn;
	}

	
	public record Walk(int kept, int drawn, int total, boolean terrain, String culling) {
	}

	
	public static @Nullable Walk lastWalk() {
		return walkCulling == null ? null
				: new Walk(walkKept, walkDrawn, walkTotal, walkTerrain, walkCulling);
	}
}
