package net.corevk.corevk.render;

import net.corevk.corevk.CoreVK;

import com.mojang.blaze3d.GpuDeviceLossException;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Matrix4fc;

public final class HandDraw {

	
	private static final float DEPTH = 0.125F;

	
	private static final float NEAR = 0.05F;

	
	private static volatile boolean wanted;

	
	private static Half half;

	
	private static HandDraw instance;

	
	private static boolean failed;

	
	private enum Half {
		SOLID,
		TRANSLUCENT
	}

	
	private final RenderBuffers buffers;
	private final SubmitNodeStorage submits;
	private final FeatureRenderDispatcher dispatcher;

	
	private final ProjectionMatrixBuffer bound;

	
	private final Projection perspective = new Projection();

	
	private final Matrix4f volume = new Matrix4f();

	
	private final Matrix4f drawn = new Matrix4f();

	
	private final Matrix4f head = new Matrix4f();

	private HandDraw(Minecraft minecraft) {
		this.buffers = new RenderBuffers(0);
		this.submits = new SubmitNodeStorage();
		this.dispatcher = new FeatureRenderDispatcher(this.buffers, minecraft.getModelManager(),
				minecraft.getAtlasManager(), minecraft.font, minecraft.gameRenderer.gameRenderState());
		this.bound = new ProjectionMatrixBuffer("CoreVK hand");
	}

	
	static void wanted(boolean asked) {
		wanted = asked;
		EntityMesh.ask();
	}

	
	static void stopped() {
		wanted = false;
	}

	
	static boolean wanted() {
		return wanted;
	}

	
	public static boolean diverted() {
		return wanted && !failed && PackChain.entities() != null;
	}

	
	public static boolean draws() {
		Minecraft minecraft = Minecraft.getInstance();
		GameRenderState state = minecraft.gameRenderer.gameRenderState();

		return diverted() && minecraft.player != null
				&& shows(minecraft, state, state.levelRenderState.cameraRenderState);
	}

	
	static boolean drawing() {
		return half != null;
	}

	
	static boolean drawingSolid() {
		return half == Half.SOLID;
	}

	
	static Matrix4fc volume() {
		return half == null || instance == null ? null : instance.volume;
	}

	
	static Matrix4fc bob() {
		return half == null || instance == null ? null : CameraBob.pose();
	}

	
	public static boolean skip(ItemStack held) {
		Half which = half;

		return which != null && translucent(held) != (which == Half.TRANSLUCENT);
	}

	
	public static void drawSolid() {
		draw(Half.SOLID);
	}

	
	public static void drawTranslucent() {
		draw(Half.TRANSLUCENT);
		if (instance != null) {
			instance.buffers.endFrame();
		}
	}

	
	public static void close() {
		HandDraw hand = instance;
		instance = null;
		if (hand != null) {
			hand.dispatcher.close();
			hand.bound.close();
			hand.buffers.close();
		}
	}

	private static void draw(Half which) {
		if (!draws()) {
			return;
		}

		HandDraw hand = instance();
		if (hand != null) {
			hand.submit(which);
		}
	}

	private static HandDraw instance() {
		if (instance == null && !failed) {
			try {
				instance = new HandDraw(Minecraft.getInstance());
			} catch (GpuDeviceLossException e) {
				throw e;
			} catch (RuntimeException e) {
				failed = true;
				CoreVK.logger().error("CoreVK could not build the renderer it draws the hand with, "
						+ "so the game keeps its own hand, drawn after the pack's image rather than "
						+ "into it", e);
			}
		}

		return instance;
	}

	
	private static boolean shows(Minecraft minecraft, GameRenderState state,
			CameraRenderState camera) {
		return minecraft.gameMode != null
				&& !camera.isPanoramicMode
				&& state.optionsRenderState.cameraType.isFirstPerson()
				&& !camera.entityRenderState.isSleeping
				&& !state.guiRenderState.isHudHidden
				&& minecraft.gameMode.getPlayerMode() != GameType.SPECTATOR;
	}

	
	private static boolean translucent(ItemStack held) {
		if (!(held.getItem() instanceof BlockItem block)) {
			return false;
		}

		return Minecraft.getInstance().getModelManager().getBlockStateModelSet()
				.get(block.getBlock().defaultBlockState())
				.hasMaterialFlag(BakedQuad.FLAG_TRANSLUCENT);
	}

	private void submit(Half which) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		GameRenderer gameRenderer = minecraft.gameRenderer;
		GameRenderState state = gameRenderer.gameRenderState();
		CameraRenderState camera = state.levelRenderState.cameraRenderState;
		// The state was settled by draws() before this was reached; what is left here is the type,
		// the submission below taking a player and not a player or nothing.
		if (player == null) {
			return;
		}

		// The same partial tick the game hands its own call, taken from the same place: the hand
		// animation is measured on the camera entity and not on the world clock, and the two part
		// company at every tick boundary.
		float partial = gameRenderer.mainCamera()
				.getCameraEntityPartialTicks(minecraft.getDeltaTracker());
		int light = minecraft.getEntityRenderDispatcher().getPackedLightCoords(player, partial);

		// Rebuilt every frame rather than cached: the field of view moves with the sprint and the
		// bow, and Projection itself compares before it dirties, so an unchanged frame costs a
		// handful of float comparisons.
		//
		// The far plane is the CAMERA's and not the hundred the game writes into its own head-up
		// projection, which is CoreVK's choice (pathways/HandRenderer.java:62) and the one that makes
		// MC_HAND_DEPTH mean what a pack uses it for: a pack divides the squeeze back out and then
		// linearises with the level's own near and far, and that only lands if the hand was drawn
		// through the level's own volume. The game can afford its hundred because nothing of its own
		// reads the hand's depth afterwards.
		this.perspective.setupPerspective(NEAR, camera.depthFar, camera.hudFov,
				state.windowRenderState.width, state.windowRenderState.height);
		// The squeeze, in the volume this backend really rasterises in rather than in the one CoreVK
		// writes it for. The class comment carries the conversion and why a bare scaling puts the
		// hand BEHIND the world here; what it comes to is that the term in w moves the squeezed band
		// back to the middle of the range, where a reversed z keeps everything the camera can see.
		this.volume.translation(0.0F, 0.0F, (1.0F - DEPTH) / 2.0F).scale(1.0F, 1.0F, DEPTH)
				.mul(this.perspective.getMatrix(this.head));
		this.drawn.set(this.volume).mul(CameraBob.pose());

		RenderSystem.backupProjectionMatrix();
		Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
		modelViewStack.pushMatrix();
		half = which;

		try {
			RenderSystem.setProjectionMatrix(this.bound.getBuffer(this.drawn),
					ProjectionType.PERSPECTIVE);
			// The identity on both, which is CoreVK's shape and not an omission. The game reaches the
			// same place the long way round, pushing the level's model view and handing the
			// submission its inverse; with the bob in the matrix above there is nothing left for
			// either of them to carry, and a draw this engine hands back to the game lands in the
			// same pixels as one it serves.
			modelViewStack.identity();
			GameRender.submitHands(gameRenderer, partial, new PoseStack(), this.submits, player,
					light);
			GameRender.renderAllFeatures(this.dispatcher, this.submits, () -> "CoreVK hand");
		} finally {
			half = null;
			modelViewStack.popMatrix();
			RenderSystem.restoreProjectionMatrix();
		}
	}
}
