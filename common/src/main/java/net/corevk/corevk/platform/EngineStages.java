package net.corevk.corevk.platform;

import net.corevk.corevk.cache.ModuleCache;
import net.corevk.corevk.glsl.TranslationCache;
import net.corevk.corevk.HostReport;
import net.corevk.corevk.render.EntityDraw;
import net.corevk.corevk.render.GraphicsApi;
import net.corevk.corevk.render.HandDraw;
import net.corevk.corevk.render.PackChain;
import net.corevk.corevk.render.PackChoice;
import net.corevk.corevk.render.pbr.PbrAtlases;
import net.corevk.corevk.render.pbr.PbrTextures;
import net.corevk.corevk.render.RenderScale;
import net.corevk.corevk.render.ShadowGeometry;
import net.corevk.corevk.render.TerrainDraw;
import net.corevk.corevk.render.TerrainSampler;
import net.corevk.corevk.screen.SettingsKey;
import net.corevk.corevk.sodium.EntityMeshSerializer;
import net.corevk.corevk.sodium.ShadowTerrain;
import net.corevk.corevk.CoreVK;

import org.joml.Matrix4fc;
import net.minecraft.world.phys.Vec3;

public final class EngineStages {

	private EngineStages() {
	}

	
	public static void clientSetup() {
		// The backend rides on the line that was already here rather than taking one of its own. It
		// is the first question any report about a missing picture is answered by, and the game says
		// it far higher up in a log this line is what somebody searches for.
		CoreVK.logger().info("Client setup reached on the {} backend, Sodium is {}",
				HostReport.backend(),
				CoreVK.platform().isModLoaded("sodium") ? "present" : "missing");

		// And what an install decides that this mod cannot, said before the pack is read so that it
		// stands above whatever the pack has to say for itself.
		HostReport.say(CoreVK.platform().gameDirectory());

		// Before the pack and not with it: a serializer is a fact about two vertex formats and knows
		// nothing of a pack, and Sodium keeps it in a cache keyed on the pair. Without it, every mob
		// Sodium draws through its own cuboid writer would reach the mesh with the identifiers left
		// at whatever the arena held. EntityMeshSerializer says the rest.
		EntityMeshSerializer.register();

		// Before the pack, because the first pack read is the one whose translation is worth
		// keeping. The cache knows nothing of a game and takes its directory from here, which is
		// what lets its whole package go on compiling and running without one.
		openTranslationCache();

		// The report of the pack goes with the reading of it, in PackChain, where which pack is
		// being drawn is known.
		PackChoice.load(CoreVK.platform().gameDirectory());
	}

	
	private static void openTranslationCache() {
		TranslationCache.install(
				CoreVK.platform().gameDirectory().resolve(CoreVK.MOD_ID),
				CoreVK.cacheEdition(),
				CoreVK.cacheEditionFamily());

		if (!TranslationCache.installed()) {
			CoreVK.logger().warn("No translation cache this run, so every pack load translates "
					+ "from scratch: {}", TranslationCache.problem());
		}
	}

	
	public static void clientTickStart() {
		SettingsKey.beforeTick();
	}

	
	public static void clientTick() {
		SettingsKey.poll();
		HostReport.sayInWorld();

		// A tick rather than a point of a load, because a load has no single end to hang a line on:
		// the composites compile on the render thread and the leftover families on a pool, over the
		// minute after. The call is silent until the compiler has been quiet for a while, and what
		// it then prints is that load's totals.
		ModuleCache.say();
	}

	
	public static void frameGraphSetup(Matrix4fc modelView, Vec3 cameraPosition) {
		if (HostReport.otherBackend()) {
			return;
		}

		// First, and before the shadow question below can send this line home: the graph is being
		// BUILT here and not executed, so it is the first point of a level frame where no render pass
		// is open. That is what the material maps of a plain texture need, and it is the reason they
		// cannot be read where they are wanted - inside the bind of a draw already being recorded.
		// Nothing to do on a frame that met no new image, which is every frame but the few where a
		// mob first comes on screen.
		PbrTextures.load();

		// The begins ahead of the shadow stage and the prepares behind it, which is the order
		// OptiFine runs them in and the order CoreVK keeps: its begins are the head of the level render
		// (pipeline/CoreVKRenderingPipeline.java:1014, from mixin/MixinLevelRenderer.java:123), its
		// shadow map and the shadowcomp stage that closes it come next
		// (pipeline/CoreVKRenderingPipeline.java:1020-1024 over shadows/ShadowRenderer.java:632-633),
		// and its prepares are the last line of renderShadows (:1025). Drawn as one range on either
		// side of the shadow stage, one of the two families reads the wrong frame of what that stage
		// writes: a prepare is meant to read this frame's shadow work, a begin the frame before's.
		//
		// Both are outside the shadow gate, because a place with no shadow map still has begins and
		// prepares, and they belong before the world whether or not anything was drawn from the
		// light.
		PackChain.drawBegins();

		if (TerrainDraw.shadows()) {
			shadowStage(modelView, cameraPosition);
		}

		PackChain.drawPrepares();
	}

	
	private static void shadowStage(Matrix4fc modelView, Vec3 cameraPosition) {
		ShadowTerrain.capture(modelView, cameraPosition);

		// The pack's shadow compute, HERE at the head of the frame and not beside the shadow map
		// that feeds it, and the placement is the parity contract. Complementary's floodfill
		// ping-pongs on frameCounter % 2, writing one half and having this frame's gbuffers read
		// the other. Dispatched at the end of the frame it runs under the WRITER's parity, and
		// every reader is a frame late forever after: the coloured light smears and snaps as the
		// player walks. Run here it shares the frame, and so the parity, of its readers, which is
		// the moment CoreVK gives it inside its own shadow render. The volumes it propagates are the
		// previous frame's shadow-geometry writes, one frame late like the shadow map itself.
		PackChain.dispatchShadowCompute();
	}

	
	public static void afterOpaqueBlocks() {
		if (HostReport.otherBackend()) {
			return;
		}

		// Opens the one window the entities are served in. It has to be a window, because the
		// screen is drawn by the same feature renderers, with the same pipelines and into the same
		// target, out of a submit storage GameRenderer hands them after the level: nothing about one
		// of those draws says it is not an entity, and only the moment does. The hand is not the
		// other one this keeps out: HandDraw submits it inside the level with a mark of its own, so
		// it is served rather than excluded.
		EntityDraw.opaqueFeatures(true);
	}

	
	public static void afterOpaqueFeatures() {
		if (HostReport.otherBackend()) {
			return;
		}

		// First, and before anything of this engine draws: everything after this point is either the
		// world's translucents or, once the level returns, the screen.
		EntityDraw.opaqueFeatures(false);

		// On a game that draws the whole level through one pass, that pass ends here, before
		// anything below records work a pass may not hold, a compute among them; the level's next
		// draw opens it again. On a game that opens a pass per phase this does nothing.
		GraphicsApi.suspendLevelPass();

		// Then the far terrain's depth, taken into the pack's window before its water half is drawn
		// and before the deferred stage below reads it: this boundary is where CoreVK takes its own
		// copy without the translucent LODs. PackChain.takeDistantDepth says what the frames
		// without a far terrain read instead.
		PackChain.takeDistantDepth();

		// Then the depth the pack reads past the hand with, which has to be taken while the hand is
		// still not in it, and the hand's solid half. The order of these three lines is the whole of
		// where the hand belongs in the frame: after the game's own opaque features, which the window
		// above has just closed, and BEFORE the deferred stage the last line runs. That is exactly
		// where CoreVK puts it, copyPreHandDepth included: beginHand copies, renderSolid draws, and
		// beginTranslucents then runs the deferreds behind both
		// (mixin/MixinLevelRenderer.java:271-274, pipeline/CoreVKRenderingPipeline.java:1043-1065).
		// Drawn after the deferreds instead, the hand would write gbuffers nothing would ever read.
		PackChain.markPreHandDepth();
		HandDraw.drawSolid();
		PackChain.drawBeforeTranslucents();
		PackChain.openFeatures();

		// Last, and after the deferred stage above rather than beside it: what this window opens is
		// the half of the entities that blends onto the target that stage has just composed.
		EntityDraw.translucentFeatures(true);
	}

	
	public static void afterTranslucentFeatures() {
		if (HostReport.otherBackend()) {
			return;
		}

		// First, and before the layer is composed: closing the window closes any pass a group left
		// open, and composing opens one of its own where the encoder allows only one at a time.
		EntityDraw.translucentFeatures(false);
		GraphicsApi.suspendLevelPass();
		PackChain.closeFeatures();
		// Then the copy a translucent program reads where it samples a target it writes, which
		// has to hold the layer just composed and everything before it.
		PackChain.takeReadCopies();
	}

	
	public static void afterLevel() {
		if (HostReport.otherBackend()) {
			return;
		}

		// The hand's blending half, before the chain and never after it: what it draws has to be in
		// the picture the composites read, and this stage is the last moment it can be. CoreVK draws it
		// at the same place, at the head of the call that ends its level render and so ahead of its
		// own finalizeLevelRendering (mixin/MixinLevelRenderer.java:162 ahead of :168).
		HandDraw.drawTranslucent();

		// Nothing is drawn when no pack can be: the game's own image is a better answer than
		// anything this mod could put over it, and the reason is already said, once in the log
		// and again on the settings screen through PackChoice.lastError.
		PackChain.draw();

		// After the chain and not before: the composites above read the map the previous frame
		// drew, and this draws the next frame's over it. The end of the frame is the whole
		// culling design, see ShadowTerrain.
		ShadowTerrain.draw();
	}

	
	public static void leaveWorld() {
		PackChain.leaveWorld();
	}

	
	public static void closeClient() {
		PackChain.close();
		// Its own line and not part of the chain's: what it holds is a second feature renderer and
		// the buffers under it, which belong to the session rather than to a pack, and which have to
		// go back while the device is still alive.
		HandDraw.close();

		// The light's walk holds a third one, for the same reason and with the same lifetime: the
		// map is filled from a submission of its own, so it carries its own dispatcher and its own
		// buffers, and they go back here rather than with any pack.
		ShadowGeometry.close();

		// The render scale's scaled world and its quad belong to the session too: they outlive
		// every pack on purpose, so the end of the session is the one caller that frees them.
		RenderScale.close();

		// The block atlas sampler a pack's terrain binds, one per anisotropy the player asked for.
		// Same lifetime and same reason: it belongs to the device rather than to any pack, and a
		// sampler outliving its device is a handle into freed memory.
		TerrainSampler.release();

		// And the material maps, which belong to the resource pack rather than to any shader pack:
		// nothing in a pack's lifetime touches them, so nothing but the end of the session does. Both
		// doors, the atlases and the plain textures, and neither of them is the other's business.
		PbrAtlases.close();
		PbrTextures.close();
	}
}
