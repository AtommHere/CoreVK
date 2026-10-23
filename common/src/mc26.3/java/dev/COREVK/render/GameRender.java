package net.corevk.corevk.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.GpuTextureView;
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
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;

public final class GameRender {

	private static @Nullable GpuTextureView featuresColour;

	private static @Nullable GpuTextureView featuresDepth;

	private GameRender() {
	}

	public static boolean redirectsFeatures() {
		return true;
	}

	public static void redirectFeatures(GpuTextureView colour, GpuTextureView depth) {
		featuresColour = colour;
		featuresDepth = depth;
	}

	public static void endFeatureRedirect() {
		featuresColour = null;
		featuresDepth = null;
	}

	public static @Nullable RenderPass featuresPass() {
		GpuTextureView colour = featuresColour;
		GpuTextureView depth = featuresDepth;
		if (colour == null) {
			return null;
		}

		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		Supplier<String> label = () -> "CoreVK translucent features";
		RenderPass pass = LevelPass.open(encoder.createRenderPass(label, colour, Optional.empty(),
				depth, OptionalDouble.empty()), colour, depth,
				() -> encoder.createRenderPass(label, colour, Optional.empty(), depth,
						OptionalDouble.empty()));
		RenderSystem.bindDefaultUniforms(pass);

		return pass;
	}

	public static boolean drawsOnMainTarget(PreparedRenderType prepared) {
		Minecraft minecraft = Minecraft.getInstance();

		return minecraft != null && (!minecraft.gameRenderer.useImprovedTransparency()
				|| prepared.oitPipelineSet() == null);
	}

	public static String drawTargetName(PreparedRenderType prepared) {
		return "the order independent transparency targets";
	}

	public static GpuTextureView drawColour(PreparedRenderType prepared) {
		GpuTextureView redirected = featuresColour;

		return redirected != null ? redirected
				: Minecraft.getInstance().gameRenderer.mainRenderTarget().getColorTextureView();
	}

	public static @Nullable GpuTextureView drawDepth(PreparedRenderType prepared) {
		GpuTextureView redirected = featuresDepth;
		if (redirected != null) {
			return redirected;
		}

		RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();

		return GraphicsApi.hasDepth(main) ? main.getDepthTextureView() : null;
	}

	public static void renderAllFeatures(FeatureRenderDispatcher dispatcher,
			SubmitNodeStorage submits, Supplier<String> label) {
		RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
		GpuTextureView colour = main.getColorTextureView();
		GpuTextureView depth = main.getDepthTextureView();
		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();

		try (FeatureRenderDispatcher.PreparedFrame frame = dispatcher.prepareFrame(submits);
				RenderPass pass = LevelPass.open(encoder.createRenderPass(label, colour,
						Optional.empty(), depth, OptionalDouble.empty()), colour, depth,
						() -> encoder.createRenderPass(label, colour, Optional.empty(), depth,
								OptionalDouble.empty()))) {
			RenderSystem.bindDefaultUniforms(pass);
			FeatureRenderDispatcher.renderAllFeatures(pass, frame);
		}
	}

	public static void submitHands(GameRenderer gameRenderer, float partialTicks, PoseStack pose,
			SubmitNodeCollector into, LocalPlayer player, int light) {
		PlayerRenderState state = gameRenderer.gameRenderState().levelRenderState.playerRenderState;
		if (state.hasPlayer) {
			gameRenderer.firstPersonHandsAndItemsRenderer.submitHandsWithItems(partialTicks, pose,
					into, state, state.firstPersonHandsAndItems);
		}
	}

	public static void extractCamera(Camera camera, CameraRenderState into, DeltaTracker delta) {
		camera.extractRenderState(into, delta);
	}

	public static boolean shouldRender(EntityRenderDispatcher entities, Entity entity,
			Frustum frustum, Vec3 at, float partialTicks) {
		return entities.shouldRender(entity, frustum, at.x, at.y, at.z, partialTicks);
	}

	public static boolean sectionShown(LevelRenderer level, BlockPos block) {
		long fade = Util.toMillis(Minecraft.getInstance().options.chunkSectionFadeInTime().get());

		return level.isSectionCompiledAndVisible(block, fade);
	}

	public static int skyColor(Camera camera, float partialTick) {
		Vector3fc colour = camera.attributeProbe().getValue(EnvironmentAttributes.SKY_COLOR,
				partialTick);

		return ARGB.color(255, channel(colour.x()), channel(colour.y()), channel(colour.z()));
	}

	private static int channel(float value) {
		return Mth.clamp(Math.round(value * 255.0F), 0, 255);
	}

	public static boolean particlesApart() {
		return Minecraft.getInstance().gameRenderer.useImprovedTransparency();
	}

	public static boolean weatherApart() {
		return Minecraft.getInstance().gameRenderer.useImprovedTransparency();
	}
}
