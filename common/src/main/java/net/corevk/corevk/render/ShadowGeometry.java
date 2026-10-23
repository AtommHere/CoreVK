package net.corevk.corevk.render;

import net.corevk.corevk.pack.source.ShadowCasters;
import net.corevk.corevk.CoreVK;

import com.mojang.blaze3d.GpuDeviceLossException;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;

public final class ShadowGeometry {

	
	private static RenderBuffers buffers;
	private static SubmitNodeStorage storage;
	private static FeatureRenderDispatcher dispatcher;

	
	private static boolean broken;

	
	private static final LevelRenderState STATE = new LevelRenderState();

	
	private static int counted = -1;

	
	private static int gathered;
	private static int gatheredBlocks;

	
	private static boolean blockEntitiesAsked;
	private static boolean emittersOnly;

	private ShadowGeometry() {
	}

	
	public static void gather(Matrix4f light, Vec3 camera, ShadowCasters casters) {
		STATE.reset();
		gathered = 0;
		gatheredBlocks = 0;
		blockEntitiesAsked = false;
		emittersOnly = false;

		// The entity switch and not one of its own, which is the convention: what enters the map here
		// is the same geometry that door serves, read from the same tables, and a family does not take
		// a second switch without a reason. It is also what keeps the walk honest when the switch is
		// off: the door would refuse every draw, and refusing inside this walk means dropping it, so
		// the walk would cost a full extraction and a submission to draw nothing and say so once per
		// row of the shadow table.
		// The player is in the guard because the extraction below dereferences it without one
		// (Camera.java:124), and what an exception here costs is out of all proportion to the frame
		// that threw it: ShadowTerrain hands it to TerrainDraw.shadowStageFailed, which lowers the
		// stage for the whole session. One frame drawn before the player exists would end the
		// shadows for the rest of the run.
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null || minecraft.level == null || minecraft.player == null
				|| !EntityDraw.wanted() || !casters.anyFeature()) {
			return;
		}

		Camera view = minecraft.gameRenderer.mainCamera();
		if (minecraft.levelRenderer == null || view == null) {
			return;
		}

		// The light's frustum with the pack's reach cut out of it, prepared about the camera because
		// that is what the whole map is built around: the terrain is culled against this very
		// matrix, and an entity measured against another one would be dropped out of a map its own
		// shadow belongs in. The reach is the pack's bound on the casters that move, read once for
		// the walk, and Reached says what it is tested on.
		//
		// It is one bound and not two: the shadow distance the player set is folded into this same
		// number rather than applied beside it, because CoreVK builds the entity frustum out of the
		// PRODUCT of the two multipliers (shadows/ShadowRenderer.java:540) and the world's own is
		// negative whenever the player governs. PackValues.entityShadowDistance carries that
		// arithmetic and its sign.
		Frustum frustum = new Reached(light, TerrainDraw.entityShadowDistance());
		frustum.prepare(camera.x, camera.y, camera.z);

		// The light's own camera, built the way CoreVK builds it and NOT the frame's borrowed: a state
		// extracted fresh from the player camera (shadows/ShadowRenderer.java:390) and then
		// overwritten on the two matrices that make it a camera at all, the view rotation with the
		// light's model view (:418) and the projection with the light's ortho (:431). The rest of
		// the state stays the camera's, position and orientation included, because that is what the
		// submissions are posed relative to.
		//
		// WHAT IT FIXES TODAY IS THE ALIAS AND NOT THE MATRICES. The old line assigned the frame's
		// own object, which then outlived the frame it was borrowed from. The two matrices are
		// nobody's business at this moment: in 26.2 the only readers of viewRotationMatrix and
		// projectionMatrix are LevelRenderer and GameRenderer, and no entity, block entity or feature
		// renderer touches either. They are written to CoreVK's shape so that a reader added later
		// finds the light's answer rather than the camera's.
		//
		// The z sense is the one thing to know before adding such a reader: the game fills that field
		// from Projection.getMatrix, which SWAPS the two planes (Projection.java:70-71) and so hands
		// out a reversed volume, while what goes in here is the pack's shadow pair as it is published,
		// a forward ortho. A consumer that assumes the field's usual convention would read this one
		// inside out.
		//
		// The partial tick is the camera's own accessor, which is what the game passes for this same
		// extraction (GameRenderer.java:391); CoreVK passes its captured tick delta. It reaches only the
		// hurt and death timers of the camera entity's state (Camera.java:143-144). 26.3 takes the
		// tracker and asks the same accessor itself, which GameRender carries.
		GameRender.extractCamera(view, STATE.cameraRenderState, minecraft.getDeltaTracker());
		if (!TerrainDraw.drawnShadowPair(STATE.cameraRenderState.viewRotationMatrix,
				STATE.cameraRenderState.projectionMatrix)) {
			return;
		}

		gathered = extractEntities(minecraft, view, frustum, casters);

		// The block entities are not taken here, and it is not an order this walk chose: the list
		// they are read off does not exist yet at this point of the stage. What the pack asked is
		// left for the half that runs once it does.
		blockEntitiesAsked = casters.anyBlockEntity();
		emittersOnly = casters.emittersOnly();
	}

	
	public static void gatherBlockEntities(BlockEntityWalk walk) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!blockEntitiesAsked || minecraft == null || minecraft.level == null) {
			return;
		}

		blockEntitiesAsked = false;
		walk.into(STATE, minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false));
		if (emittersOnly) {
			keepEmitters(minecraft.level);
		}

		gatheredBlocks = STATE.blockEntityRenderStates.size();
	}

	
	private static void keepEmitters(Level level) {
		STATE.blockEntityRenderStates
				.removeIf(state -> level.getBlockState(state.blockPos).getLightEmission() == 0);
	}

	
	public static void draw(Vec3 camera) {
		try {
			walk(camera);
		} finally {
			// THE FRAME OF THIS FAMILY'S OWN BUFFERS ENDS HERE whether anything was drawn or not,
			// and it is not tidiness: RenderBuffers.endFrame is the ONLY path that recycles what
			// StagedVertexBuffer took, so without it every frame's acquire misses the pool and falls
			// through to a fresh device allocation that nothing ever hands back. The cost is per
			// FRAME and not per caster, so a world holding two entities pays it in full, and it is
			// large enough to be the first thing seen rather than something to look for.
			// HandDraw.drawTranslucent ends its own on both paths for the same reason.
			if (buffers != null) {
				buffers.endFrame();
			}
		}
	}

	
	public static void close() {
		FeatureRenderDispatcher held = dispatcher;
		RenderBuffers owned = buffers;
		dispatcher = null;
		storage = null;
		buffers = null;
		if (held != null) {
			held.close();
		}

		if (owned != null) {
			owned.close();
		}
	}

	private static void walk(Vec3 camera) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null || (gathered == 0 && gatheredBlocks == 0) || !ensure(minecraft)) {
			return;
		}

		submit(minecraft, camera);
		say(gathered, gatheredBlocks);

		EntityDraw.shadowFeatures(true);
		try {
			// Through the seam because 26.3 wants the pass from its caller where 26.2 let every draw
			// open its own; GameRender says which pass that is on each game.
			GameRender.renderAllFeatures(dispatcher, storage, () -> "CoreVK shadow casters");
		} finally {
			// Lowered whatever happened, and this is the one flag of the three that nothing else
			// would lower: the other two are closed by the game's own events, and there is no event
			// after this one. Left standing, every entity of the next frame would be drawn with the
			// shadow program into the picture.
			EntityDraw.shadowFeatures(false);
			STATE.reset();
		}
	}

	
	private static boolean ensure(Minecraft minecraft) {
		if (dispatcher != null) {
			return true;
		}

		if (broken) {
			return false;
		}

		RenderBuffers owned = null;
		try {
			owned = new RenderBuffers(0);
			storage = new SubmitNodeStorage();
			dispatcher = new FeatureRenderDispatcher(owned, minecraft.getModelManager(),
					minecraft.getAtlasManager(), minecraft.font,
					minecraft.gameRenderer.gameRenderState());
			buffers = owned;

			return true;
		} catch (GpuDeviceLossException e) {
			throw e;
		} catch (RuntimeException e) {
			if (owned != null) {
				owned.close();
			}

			buffers = null;
			storage = null;
			dispatcher = null;
			broken = true;
			CoreVK.logger().error("CoreVK could not build the second submission the shadow map is "
					+ "filled from, so nothing that moves casts a shadow this session", e);

			return false;
		}
	}

	
	private static int extractEntities(Minecraft minecraft, Camera view, Frustum frustum,
			ShadowCasters casters) {
		EntityRenderDispatcher entities = minecraft.levelRenderer.entityRenderDispatcher();
		TickRateManager ticks = minecraft.level.tickRateManager();
		DeltaTracker delta = minecraft.getDeltaTracker();
		Vec3 at = view.position();

		if (casters.entities()) {
			for (Entity entity : minecraft.level.entitiesForRendering()) {
				// The entity's own tick, which extract takes too and 26.3's culling box is built at.
				float partial = delta.getGameTimeDeltaPartialTick(!ticks.isEntityFrozen(entity));
				if (visible(minecraft, entities, entity, frustum, at, partial)) {
					STATE.entityRenderStates.add(extract(entities, delta, ticks, entity));
				}
			}
		} else if (casters.player()) {
			Player player = minecraft.player;

			// COREVK'S OWN CONSTANT HERE, and not the entity's own tick. Its player
			// branch takes one getGameTimeDeltaPartialTick(false) for the pair
			// (shadows/ShadowRenderer.java:553) where its entity branch asks per caster (:712). The
			// split is CoreVK's and it is not ours to tidy: made uniform, a frozen world poses the
			// player one way here and another way there, and the packs are written against there.
			float partial = delta.getGameTimeDeltaPartialTick(false);
			if (!player.isSpectator() && !player.isInvisible()) {
				STATE.entityRenderStates.add(entities.extractEntity(player, partial));
			}

			// What carries the player, extracted beside it as CoreVK extracts it (:557-560) and
			// outside the test above, as there too: a rider whose own shadow falls on a horse
			// casting none is worse than neither casting one.
			Entity vehicle = player.getVehicle();
			if (vehicle != null) {
				STATE.entityRenderStates.add(entities.extractEntity(vehicle, partial));
			}
		}

		return STATE.entityRenderStates.size();
	}

	
	private static EntityRenderState extract(EntityRenderDispatcher entities, DeltaTracker delta,
			TickRateManager ticks, Entity entity) {
		if (entity.tickCount == 0) {
			entity.xOld = entity.getX();
			entity.yOld = entity.getY();
			entity.zOld = entity.getZ();
		}

		return entities.extractEntity(entity,
				delta.getGameTimeDeltaPartialTick(!ticks.isEntityFrozen(entity)));
	}

	
	private static boolean visible(Minecraft minecraft, EntityRenderDispatcher entities, Entity entity,
			Frustum frustum, Vec3 at, float partial) {
		if (entity instanceof Player spectator && spectator.isSpectator()) {
			return false;
		}

		Player player = minecraft.player;
		if (!GameRender.shouldRender(entities, entity, frustum, at, partial)
				&& (player == null || !entity.hasIndirectPassenger(player))) {
			return false;
		}

		BlockPos block = entity.blockPosition();

		return minecraft.level.isOutsideBuildHeight(block.getY())
				|| GameRender.sectionShown(minecraft.levelRenderer, block);
	}

	
	private static void submit(Minecraft minecraft, Vec3 camera) {
		PoseStack pose = new PoseStack();

		for (EntityRenderState entity : STATE.entityRenderStates) {
			minecraft.levelRenderer.entityRenderDispatcher().submit(entity, STATE.cameraRenderState,
					entity.x - camera.x, entity.y - camera.y, entity.z - camera.z, pose, storage);
		}

		for (BlockEntityRenderState blockEntity : STATE.blockEntityRenderStates) {
			BlockPos at = blockEntity.blockPos;
			pose.pushPose();
			pose.translate(at.getX() - camera.x, at.getY() - camera.y, at.getZ() - camera.z);
			minecraft.levelRenderer.blockEntityRenderDispatcher().submit(blockEntity, pose, storage,
					STATE.cameraRenderState);
			pose.popPose();
		}
	}

	
	private static void say(int entities, int blockEntities) {
		if (counted != BlockStateIds.generation()) {
			counted = BlockStateIds.generation();
			CoreVK.logger().info("On this frame the light's walk submitted {} entities and {} block "
					+ "entities into the shadow map, on block table {}", entities, blockEntities,
					counted);
		}
	}

	
	private static final class Reached extends Frustum {

		
		private final float reach;

		Reached(Matrix4f light, float reach) {
			super(new Matrix4f(), light);
			this.reach = reach;
		}

		@Override
		public boolean isVisible(AABB box) {
			return !culled(box) && super.isVisible(box);
		}

		
		private boolean culled(AABB box) {
			if (this.reach < 0.0F) {
				return false;
			}

			double x = getCamX();
			double y = getCamY();
			double z = getCamZ();

			return box.maxX < x - this.reach || box.minX > x + this.reach
					|| box.maxY < y - this.reach || box.minY > y + this.reach
					|| box.maxZ < z - this.reach || box.minZ > z + this.reach;
		}
	}

	
	@FunctionalInterface
	public interface BlockEntityWalk {

		
		void into(LevelRenderState state, float partialTick);
	}
}
