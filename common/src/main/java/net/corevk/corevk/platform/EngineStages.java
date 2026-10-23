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

		CoreVK.logger().info("Client setup reached on the {} backend, Sodium is {}",
				HostReport.backend(),
				CoreVK.platform().isModLoaded("sodium") ? "present" : "missing");

		HostReport.say(CoreVK.platform().gameDirectory());

		EntityMeshSerializer.register();

		openTranslationCache();

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

		ModuleCache.say();
	}

	public static void frameGraphSetup(Matrix4fc modelView, Vec3 cameraPosition) {
		if (HostReport.otherBackend()) {
			return;
		}

		PbrTextures.load();

		PackChain.drawBegins();

		if (TerrainDraw.shadows()) {
			shadowStage(modelView, cameraPosition);
		}

		PackChain.drawPrepares();
	}

	private static void shadowStage(Matrix4fc modelView, Vec3 cameraPosition) {
		ShadowTerrain.capture(modelView, cameraPosition);

		PackChain.dispatchShadowCompute();
	}

	public static void afterOpaqueBlocks() {
		if (HostReport.otherBackend()) {
			return;
		}

		EntityDraw.opaqueFeatures(true);
	}

	public static void afterOpaqueFeatures() {
		if (HostReport.otherBackend()) {
			return;
		}

		EntityDraw.opaqueFeatures(false);

		GraphicsApi.suspendLevelPass();

		PackChain.takeDistantDepth();

		PackChain.markPreHandDepth();
		HandDraw.drawSolid();
		PackChain.drawBeforeTranslucents();
		PackChain.openFeatures();

		EntityDraw.translucentFeatures(true);
	}

	public static void afterTranslucentFeatures() {
		if (HostReport.otherBackend()) {
			return;
		}

		EntityDraw.translucentFeatures(false);
		GraphicsApi.suspendLevelPass();
		PackChain.closeFeatures();

		PackChain.takeReadCopies();
	}

	public static void afterLevel() {
		if (HostReport.otherBackend()) {
			return;
		}

		HandDraw.drawTranslucent();

		PackChain.draw();

		ShadowTerrain.draw();
	}

	public static void leaveWorld() {
		PackChain.leaveWorld();
	}

	public static void closeClient() {
		PackChain.close();

		HandDraw.close();

		ShadowGeometry.close();

		RenderScale.close();

		TerrainSampler.release();

		PbrAtlases.close();
		PbrTextures.close();
	}
}
