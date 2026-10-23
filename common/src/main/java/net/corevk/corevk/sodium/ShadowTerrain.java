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

		if (DrawBackend.BACKEND == DrawBackend.OPENGL) {
			return;
		}

		if (!TerrainDraw.openShadowStage()) {

			PackChain.clearCustomImages();
			walkCulling = null;
			return;
		}

		RenderSectionManager manager =
				((MixinSodiumWorldRenderer) renderer).corevk$renderSectionManager();
		Matrix4f light = TerrainDraw.shadowFrustum(LIGHT);

		ShadowCullPlan plan = TerrainDraw.shadowCullPlan(LIGHT_VECTOR, CAMERA);
		if (manager == null || light == null || plan == null) {
			return;
		}

		boolean measuring = measured != BlockStateIds.generation();
		int seen = measuring ? sections(manager.getRenderLists()) : 0;

		ShadowCasters casters = TerrainDraw.shadowCasters();

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

			ShadowCullFrustum.Chosen cull = ShadowCullFrustum.of(plan);
			Viewport viewport = new Viewport(cull.frustum(),
					new Vector3d(camera.x, camera.y, camera.z));

			manager.finalizeRenderLists(minecraft.gameRenderer.mainCamera(), viewport,
					FogParameters.NONE, true);

			ShadowGeometry.gather(light, camera, casters);
			ClientLevel level = minecraft.level;
			if (level != null) {
				ShadowGeometry.gatherBlockEntities((state, partial) -> renderer.extractBlockEntities(
						minecraft.gameRenderer.mainCamera(), partial, level.destructionProgress(),
						state));
			}

			count(manager.getRenderLists());
			walkTotal = manager.getTotalSections();
			walkTerrain = casters.terrain();
			walkCulling = cull.culling();

			if (measuring && seen > 0) {
				measured = BlockStateIds.generation();
				CoreVK.logger().info("shadow-cull {} kept={} camera={} drawn={} blocks={}",
						walkCulling, walkKept, seen, walkDrawn, measured);
			}

			draw(renderer, minecraft, camera);
		} finally {

			((RenderSectionManagerAccessor) manager).corevk$setNeedsRenderListUpdate(true);
		}
	}

	private static void draw(SodiumWorldRenderer renderer, Minecraft minecraft, Vec3 camera) {

		Matrix4fc projection =
				((GameRendererStorage) minecraft.gameRenderer).sodium$getProjectionMatrix();
		ChunkRenderMatrices matrices = new ChunkRenderMatrices(projection, MODEL_VIEW);

		GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST, true);

		ShadowCasters casters = TerrainDraw.shadowCasters();

		PackChain.clearCustomImages();

		boolean drawTerrain = ShadowAmortisation.drawTerrainThisFrame() || !TerrainDraw.shadowMapKept();

		if (casters.terrain() && drawTerrain) {

			DistantDraw.shadow(false, camera);
			TerrainDraw.shadowPass(() -> SodiumApi.drawShadowLayer(renderer,
					ChunkSectionLayerGroup.OPAQUE, matrices, camera, sampler));
		}

		if (drawTerrain && casters.terrain()) {
			if (ShadowAmortisation.keepsDrawnMap()) {
				TerrainDraw.keepShadowMap();
			}
			ShadowAmortisation.drawn();
		}

		ShadowGeometry.draw(camera);

		TerrainDraw.copyShadowDepth();

		if (casters.translucent() && !Boolean.getBoolean("corevk.probeNoShadowTranslucent")) {

			DistantDraw.shadow(true, camera);
			TerrainDraw.shadowPass(() -> SodiumApi.drawShadowLayer(renderer,
					ChunkSectionLayerGroup.TRANSLUCENT, matrices, camera, sampler));
		}

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
