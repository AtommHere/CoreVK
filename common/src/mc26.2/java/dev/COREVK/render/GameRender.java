package net.corevk.corevk.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

public final class GameRender {

	private GameRender() {
	}

	public static boolean redirectsFeatures() {
		return true;
	}

	public static void redirectFeatures(GpuTextureView colour, GpuTextureView depth) {
		RenderSystem.outputColorTextureOverride = colour;
		RenderSystem.outputDepthTextureOverride = depth;
	}

	public static void endFeatureRedirect() {
		RenderSystem.outputColorTextureOverride = null;
		RenderSystem.outputDepthTextureOverride = null;
	}

	@SuppressWarnings("ReferenceEquality")
	public static boolean drawsOnMainTarget(PreparedRenderType prepared) {
		Minecraft minecraft = Minecraft.getInstance();
		RenderTarget main = minecraft == null ? null : minecraft.gameRenderer.mainRenderTarget();

		return main != null && prepared.outputTarget().getRenderTarget() == main;
	}

	public static String drawTargetName(PreparedRenderType prepared) {
		return String.valueOf(prepared.outputTarget());
	}

	public static GpuTextureView drawColour(PreparedRenderType prepared) {
		RenderTarget target = prepared.outputTarget().getRenderTarget();

		return RenderSystem.outputColorTextureOverride != null
				? RenderSystem.outputColorTextureOverride
				: target.getColorTextureView();
	}

	public static @Nullable GpuTextureView drawDepth(PreparedRenderType prepared) {
		RenderTarget target = prepared.outputTarget().getRenderTarget();

		return !target.useDepth ? null
				: RenderSystem.outputDepthTextureOverride != null
						? RenderSystem.outputDepthTextureOverride
						: target.getDepthTextureView();
	}

	public static void renderAllFeatures(FeatureRenderDispatcher dispatcher,
			SubmitNodeStorage submits, Supplier<String> label) {
		dispatcher.renderAllFeatures(submits);
	}

	public static void submitHands(GameRenderer gameRenderer, float partialTicks, PoseStack pose,
			SubmitNodeCollector into, LocalPlayer player, int light) {
		gameRenderer.itemInHandRenderer.submitHandsWithItems(partialTicks, pose, into, player, light);
	}

	public static void extractCamera(Camera camera, CameraRenderState into, DeltaTracker delta) {
		camera.extractRenderState(into, camera.getCameraEntityPartialTicks(delta));
	}

	public static boolean shouldRender(EntityRenderDispatcher entities, Entity entity,
			Frustum frustum, Vec3 at, float partialTicks) {
		return entities.shouldRender(entity, frustum, at.x, at.y, at.z);
	}

	public static boolean sectionShown(LevelRenderer level, BlockPos block) {
		return level.isSectionCompiledAndVisible(block);
	}

	public static int skyColor(Camera camera, float partialTick) {
		return camera.attributeProbe().getValue(EnvironmentAttributes.SKY_COLOR, partialTick);
	}

	public static boolean particlesApart() {
		return Minecraft.getInstance().levelRenderer.particlesTarget() != null;
	}

	public static boolean weatherApart() {
		return Minecraft.getInstance().levelRenderer.weatherTarget() != null;
	}
}
