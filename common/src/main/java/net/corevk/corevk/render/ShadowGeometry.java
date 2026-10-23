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

		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null || minecraft.level == null || minecraft.player == null
				|| !EntityDraw.wanted() || !casters.anyFeature()) {
			return;
		}

		Camera view = minecraft.gameRenderer.mainCamera();
		if (minecraft.levelRenderer == null || view == null) {
			return;
		}

		Frustum frustum = new Reached(light, TerrainDraw.entityShadowDistance());
		frustum.prepare(camera.x, camera.y, camera.z);

		GameRender.extractCamera(view, STATE.cameraRenderState, minecraft.getDeltaTracker());
		if (!TerrainDraw.drawnShadowPair(STATE.cameraRenderState.viewRotationMatrix,
				STATE.cameraRenderState.projectionMatrix)) {
			return;
		}

		gathered = extractEntities(minecraft, view, frustum, casters);

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

			GameRender.renderAllFeatures(dispatcher, storage, () -> "CoreVK shadow casters");
		} finally {

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

				float partial = delta.getGameTimeDeltaPartialTick(!ticks.isEntityFrozen(entity));
				if (visible(minecraft, entities, entity, frustum, at, partial)) {
					STATE.entityRenderStates.add(extract(entities, delta, ticks, entity));
				}
			}
		} else if (casters.player()) {
			Player player = minecraft.player;

			float partial = delta.getGameTimeDeltaPartialTick(false);
			if (!player.isSpectator() && !player.isInvisible()) {
				STATE.entityRenderStates.add(entities.extractEntity(player, partial));
			}

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
