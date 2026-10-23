package net.corevk.corevk.render;

import net.corevk.corevk.dh.DhDepth;
import net.corevk.corevk.pack.id.NameIds;
import net.corevk.corevk.pack.model.RenderStage;
import net.corevk.corevk.pack.target.PackDirectives;
import net.corevk.corevk.render.timing.PassTimings;
import net.corevk.corevk.uniform.ClipSpace;
import net.corevk.corevk.uniform.values.FrameSmoothed;
import net.corevk.corevk.uniform.WorldState;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.TextureFilteringMethod;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.EndFlashState;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;
import org.joml.Vector2f;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.joml.Vector4fc;

public final class FrameState implements WorldState {

	
	private static final float COUNTER_WRAP = 3600.0F;

	
	private static final int FRAME_WRAP = 720720;

	
	private static final Vector3f NOTHING_SELECTED = new Vector3f(-256.0F);

	
	private static final float MAX_HUNGER = 20.0F;
	private static final float MAX_ARMOR = 50.0F;

	
	private static final int FOG_OFF = 0;
	private static final int FOG_SHAPE_OFF = -1;

	private final ViewMatrices view = new ViewMatrices();

	
	private final Vector2f distantPlanes = new Vector2f();
	private final CameraShift shift = new CameraShift();

	private PackDirectives directives = PackDirectives.defaults();

	
	private RenderStage stage = RenderStage.NONE;

	
	private boolean endFlashShadows;

	
	private boolean oldHandLight = true;

	
	private Object lastLevel;

	// frame and time
	private long lastFrameNanos;
	private boolean timed;
	private int frameCounter;
	private float frameTime;
	private float frameTimeCounter;
	private float partialTick;
	private long gameTime;
	private float glintAlpha = 1.0F;
	private float viewWidth;
	private float viewHeight;

	// world
	private long worldTime;
	private long worldDay;
	private int moonPhase;
	private float sunAngleDegrees;
	private float moonAngleDegrees;
	private float rainStrength;
	private float thunderStrength;
	private int skyColorPacked;
	private float cloudHeight = 192.0F;
	private int bedrockLevel;
	private int heightLimit = 256;
	private int logicalHeightLimit = 256;
	private boolean hasCeiling;
	private boolean hasSkylight = true;
	private float ambientLight;
	private int dimensionOrdinal;
	private int seaLevel;
	
	private Holder<Biome> biomeHolder;
	private int biomeId;
	private int biomeCategory;
	private int biomePrecipitation;
	private float rainfall;
	private float temperature;

	// end flash
	private boolean hasEndFlash;
	private float endFlashXAngle;
	private float endFlashYAngle;
	private float endFlashIntensity;
	private float previousEndFlashIntensity;

	// fog
	private final Vector4f fogColor = new Vector4f();
	private float fogStart;
	private float fogEnd;
	private boolean heavyFog;

	// player
	private int isEyeInWater;
	private final Vector3d eyePosition = new Vector3d();
	private final Vector3f playerLookVector = new Vector3f();
	private final Vector3f playerBodyVector = new Vector3f();
	private float blindness;
	private float darknessFactor;
	private float nightVision;
	private float darknessLightFactor;
	private int cameraEntityTickCount;
	private float screenBrightness;
	private float playerMood;
	private int eyeBrightnessBlock;
	private int eyeBrightnessSky;
	private boolean sneaking;
	private boolean sprinting;
	private boolean hurt;
	private boolean invisible;
	private boolean burning;
	private boolean onGround;
	private boolean hideGui;
	private boolean rightHanded = true;
	private boolean spectator;
	private boolean firstPerson = true;
	private boolean elytraFlying;
	private boolean riding;
	private boolean feetInWater;
	private boolean swimming;
	private boolean vehicleInWater;
	private int vehicleId;
	private final Vector3d vehicleLookVector = new Vector3d();
	private final Vector3d relativeVehiclePosition = new Vector3d();
	private float playerHealth = -1.0F;
	private float playerMaxHealth = -1.0F;
	private float playerHunger = -1.0F;
	private float playerArmor = -1.0F;
	private float playerAir = -1.0F;
	private float playerMaxAir = -1.0F;
	private final Vector3f selectedBlockPos = new Vector3f(NOTHING_SELECTED);
	private int selectedBlockId;
	private final Vector4f lightningBoltPosition = new Vector4f();
	
	private long lightningTick = Long.MIN_VALUE;
	private int heldItemId = NameIds.NONE;
	private int heldItemId2 = NameIds.NONE;
	private int heldBlockLight;
	private int heldBlockLight2;

	// engine
	private int atlasWidth;
	private int atlasHeight;
	private float anisotropy;
	private int textureFilteringMode;
	private float chunkFadeTimeInv;

	
	public FrameState() {
		FrameSmoothed.forgetAll();
	}

	
	public void directives(PackDirectives directives) {
		this.directives = directives;
	}

	
	public PackDirectives directives() {
		return this.directives;
	}

	
	public void endFlashShadows(boolean endFlashShadows) {
		this.endFlashShadows = endFlashShadows;
	}

	
	public void oldHandLight(boolean oldHandLight) {
		this.oldHandLight = oldHandLight;
	}

	
	public ViewMatrices view() {
		return this.view;
	}

	
	public void convention(Vector4fc convention) {
		this.view.convention(convention);
	}

	
	public void passModelView(Matrix4fc matrix, Matrix4fc bob) {
		this.view.passModelView(matrix, bob);
	}

	
	public void passProjection(Matrix4fc matrix) {
		this.view.passProjection(matrix);
	}

	
	public void passColour(Vector4fc colour) {
		this.view.passColour(colour);
	}

	
	public void passAlphaTest(float reference) {
		this.view.passAlphaTest(reference);
	}

	
	public void renderStage(RenderStage stage) {
		this.stage = stage;
	}

	
	@SuppressWarnings("ReferenceEquality")
	public void advance() {
		advanceClock();

		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft == null ? null : minecraft.level;
		if (level == null) {
			CapturedProjection.clear();
			CameraBob.clear();

			return;
		}

		if (this.lastLevel != level) {
			// A different world, so nothing carried over from the previous frame means anything:
			// the camera has jumped a dimension's worth of coordinates and the shift, the history
			// and every motion vector built from them would be one frame of nonsense. The clock is
			// part of that and the half easiest to leave out, which only shows on a dimension the
			// pack serves out of the directory it was already loaded from: nothing is read again
			// there, so the first frame on the far side would publish a frameTime measuring the
			// whole change, seconds of it, into every value a pack integrates over one.
			this.lastLevel = level;
			reset();
		}

		GameRenderer renderer = minecraft.gameRenderer;
		Camera camera = renderer.mainCamera();
		CameraRenderState cameraState = renderer.gameRenderState().levelRenderState.cameraRenderState;
		this.partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		this.gameTime = renderer.gameRenderState().levelRenderState.gameTime;
		// The frame's own snapshot of the setting and not a second reading of the option: this is the
		// very field the game hands its globals block for its own glint shader
		// (renderer/GameRenderer.java:419 out of :623), so a pack's glint fades with the slider on the
		// same frame the game's would have.
		this.glintAlpha = (float) renderer.gameRenderState().optionsRenderState.glintStrength;

		RenderTarget main = renderer.mainRenderTarget();
		if (main != null) {
			// The main target and not the window. A pack divides by these to step across a
			// texture, and what it steps across is the target that was drawn into: the game only
			// brings the target to the window's size where it builds a frame, and the panorama
			// capture goes round that path, resizing the target to 4096 square itself in
			// Minecraft.grabPanoramixScreenshot.
			this.viewWidth = main.width;
			this.viewHeight = main.height;
		}

		// The shift moves first. Two of the values below are differences against the camera, the
		// lightning bolt and the vehicle, and reading them against last frame's position would
		// give both of them a lag that only shows while the player moves.
		this.shift.advance(cameraState.pos);

		readWorld(level, camera);
		readFog(cameraState);
		readPlayer(minecraft, level, camera);
		readBiome(minecraft, level);
		readEngine(minecraft);
		advanceView(minecraft, cameraState);

		// Cleared last, so that a frame in which nothing captured falls back rather than
		// publishing the frame before it. The bob goes with it and for the same reason: a frame
		// that took none must not be handed the last one's, or the world would keep swinging after
		// the player had stopped.
		CapturedProjection.clear();
		CameraBob.clear();
	}

	
	public void reset() {
		this.view.reset();
		this.shift.reset();
		FrameSmoothed.forgetAll();
		this.timed = false;
		this.frameCounter = 0;
		this.frameTime = 0.0F;
		this.frameTimeCounter = 0.0F;
		// The bolt with them, and it is the tick beside it that makes this necessary rather than
		// tidy: a client that leaves a save and rejoins it comes back at the tick it left on, so a
		// counter kept across that would match, the search would be skipped, and the position of
		// the session before would be served as this one's.
		this.lightningTick = Long.MIN_VALUE;
		this.lightningBoltPosition.zero();
		this.biomeHolder = null;
	}

	
	public void leaveWorld() {
		reset();
		this.lastLevel = null;
	}

	
	@SuppressWarnings("NarrowCalculation")
	private void advanceClock() {
		long now = System.nanoTime();
		long elapsed = this.timed ? now - this.lastFrameNanos : 0L;
		this.lastFrameNanos = now;
		this.timed = true;

		this.frameTime = (elapsed / 1000L) / 1000L / 1000.0F;
		this.frameTimeCounter += this.frameTime;
		if (this.frameTimeCounter >= COUNTER_WRAP) {
			this.frameTimeCounter = 0.0F;
		}

		this.frameCounter = (this.frameCounter + 1) % FRAME_WRAP;
	}

	private void advanceView(Minecraft minecraft, CameraRenderState cameraState) {
		int renderDistance = minecraft.options.getEffectiveRenderDistance();
		Matrix4fc rendered = CapturedProjection.rendered(cameraState.projectionMatrix);

		// The bob goes to the model view and the projection is published clean, which is where a
		// pack expects both. Only once the two have been multiplied back together and found to be
		// the matrix the level was really drawn with: a term this engine failed to intercept would
		// otherwise be missing from one and not made up for by the other.
		boolean split = CameraBob.agrees(cameraState.projectionMatrix, rendered);

		// The render distance in blocks, which is a quarter of the plane the game actually clips
		// at: Camera sets depthFar to max(renderDistance * 4, cloudRange * 16). That quarter is
		// what CoreVK hands out, so every linearisation in every pack of the corpus is written
		// against it, and correcting it here would make all of them wrong at once.
		this.view.advance(cameraState.viewRotationMatrix, CameraBob.taken(),
				split ? cameraState.projectionMatrix : rendered, renderDistance * 16.0F,
				renderDistance);

		// And what Distant Horizons drew with, which is nothing at all on the sessions that have no
		// far terrain. The distance answers on its own and the two planes answer on theirs, which is
		// how CoreVK asks them, and advanceDistant carries what the pair being short of the distance
		// costs.
		//
		// What the planes carry is the projection DH drew the PREVIOUS frame with, and that is worth
		// saying rather than hiding. DH fills its render parameter from the opaque chunk pass, while
		// the pack's frame opens ahead of it at the first sky draw. Reading them later would not
		// mend it: the block is written once, here, so a value taken after it would reach a pack one
		// frame later still. What it costs is nothing measurable, both planes moving with DH's own
		// settings and with the player's height above the world rather than with the camera.
		//
		// And the volume beside them, which is the row rather than the pair of planes the planes
		// were worked out from: a row put back together out of two planes would be the same
		// arithmetic done twice and rounded twice. Handed a zero offset where there is no row to be
		// had, and then the volume published is the frame's own, which is what a pack reads where
		// this engine draws no far terrain at all.
		//
		// ALL THREE ARE TAKEN BEFORE ANY OF THEM IS PUBLISHED, and that is what stands in for the
		// coupling rather than the coupling itself. Each of the three reads can latch this engine's
		// whole view of that mod off on a reflective failure, and the frame that latch lands in is
		// the only frame that could hold answers from both sides of it: far terrain still on screen,
		// and the numbers that place it disagreeing. Ordering the reads mends nothing, it only
		// chooses which of them is the stale one, so the frame asks after all three instead and
		// takes the fallback whole. On a latch inside the distance or inside the planes that is what
		// this method already did while the three moved together. On a latch inside the row it is
		// new: that read used to come after the pair was published, so the frame went out with a
		// real distance, real planes and a volume that had given up.
		//
		// It does NOT cover the three partings that OUTLIVE the coupling: the ordinary one this
		// batch exists for, one that is CoreVK's own answer, and one that is a hole older than any of
		// this. They are named one by one in ViewMatrices.advanceDistant.
		int distance = DhDepth.renderDistanceBlocks();

		// One reflective read of the row, and the planes worked out of it, rather than the row
		// read twice through four field gets a frame.
		boolean row = DhDepth.zRow(this.distantPlanes);
		float scale = row ? this.distantPlanes.x : 0.0F;
		float offset = row ? this.distantPlanes.y : 0.0F;

		boolean planes = row && DhDepth.planes(scale, offset, this.distantPlanes);
		float near = planes ? this.distantPlanes.x : ViewMatrices.FALLBACK_PLANE;
		float far = planes ? this.distantPlanes.y : ViewMatrices.FALLBACK_PLANE;

		if (!DhDepth.usable()) {
			distance = -1;
			near = ViewMatrices.FALLBACK_PLANE;
			far = ViewMatrices.FALLBACK_PLANE;
			scale = 0.0F;
			offset = 0.0F;
		}

		this.view.advanceDistant(near, far, distance);
		this.view.advanceDistantVolume(scale, offset);

		// Settled here and nowhere else, because the answer decides which pair of shadow matrices
		// every sampling pass of this frame is handed, and that is built two lines down. The stage
		// at the end of the frame reads the same decision back rather than making its own.
		float shadowAngle = sunAngle(isDay()) / 360.0F;
		boolean drewLastFrame = ShadowAmortisation.beginFrame(this.shift.unshifted(), shadowAngle,
				TerrainDraw.shadowAmortisable());
		// Counted where the decision is read rather than where it is made, so one frame is counted
		// once and only a frame that reached the decision counts at all.
		PassTimings.shadowMap(ShadowAmortisation.missedLastPlan());

		this.view.advanceShadow(shadowAngle, this.directives.sunPathRotation(),
				this.directives.shadowIntervalSize(), this.shift.unshifted(),
				this.directives.shadowDistance(), this.directives.shadowNearPlane(),
				this.directives.shadowFarPlane(), inEndFlash(), this.endFlashXAngle,
				this.endFlashYAngle, drewLastFrame);
	}

	
	private float sunAngle(boolean sun) {
		float angle = (sun ? this.sunAngleDegrees : this.moonAngleDegrees) + 90.0F;
		if (angle < 0.0F) {
			angle += 360.0F;
		} else if (angle > 360.0F) {
			angle -= 360.0F;
		}

		return angle;
	}

	private boolean isDay() {
		return sunAngle(true) < 180.0F;
	}

	
	private boolean inEndFlash() {
		return this.endFlashShadows && this.hasEndFlash && this.dimensionOrdinal == 1;
	}

	
	private void readWorld(ClientLevel level, Camera camera) {
		float pt = this.partialTick;
		this.sunAngleDegrees = camera.attributeProbe().getValue(EnvironmentAttributes.SUN_ANGLE, pt);
		this.moonAngleDegrees = camera.attributeProbe().getValue(EnvironmentAttributes.MOON_ANGLE, pt);
		this.moonPhase = camera.attributeProbe().getValue(EnvironmentAttributes.MOON_PHASE, pt).index();
		// Packed on 26.2 and three floats on 26.3; GameRender hands both back packed.
		this.skyColorPacked = GameRender.skyColor(camera, pt);
		this.cloudHeight = camera.attributeProbe().getValue(EnvironmentAttributes.CLOUD_HEIGHT, pt);

		// Clamped because some servers send values outside the range, and a pack that trusts them
		// draws weather harder than weather.
		this.rainStrength = Mth.clamp(level.getRainLevel(pt), 0.0F, 1.0F);
		this.thunderStrength = Mth.clamp(level.getThunderLevel(pt), 0.0F, 1.0F);

		this.dimensionOrdinal = dimensionOrdinal(level);

		long clock = level.getDefaultClockTime();
		this.worldDay = clock / 24000L;
		// The Nether and the End keep the raw modulus rather than honouring hasFixedTime. That is
		// an oversight of CoreVK's that packs now depend on: Complementary's ender beams stop moving
		// without it.
		boolean nowhere = this.dimensionOrdinal == -1 || this.dimensionOrdinal == 1;
		this.worldTime = !nowhere && level.dimensionType().hasFixedTime() ? 0L : clock % 24000L;

		DimensionType dimension = level.dimensionType();
		this.bedrockLevel = dimension.minY();
		this.heightLimit = dimension.height();
		this.logicalHeightLimit = dimension.logicalHeight();
		this.hasCeiling = dimension.hasCeiling();
		this.hasSkylight = dimension.hasSkyLight();
		this.ambientLight = dimension.ambientLight();
		this.seaLevel = level.getSeaLevel();

		EndFlashState flash = level.endFlashState();
		this.hasEndFlash = flash != null;
		this.previousEndFlashIntensity = this.endFlashIntensity;
		this.endFlashXAngle = flash == null ? 0.0F : flash.getXAngle();
		this.endFlashYAngle = flash == null ? 0.0F : flash.getYAngle();
		this.endFlashIntensity = flash == null ? 0.0F : flash.getIntensity(pt);
	}

	// A dimension key is interned by the game, so the three names below are the instances a level
	// carries and not copies of them.
	@SuppressWarnings("ReferenceEquality")
	private static int dimensionOrdinal(ClientLevel level) {
		if (level.dimension() == Level.OVERWORLD) {
			return 0;
		} else if (level.dimension() == Level.NETHER) {
			return -1;
		} else if (level.dimension() == Level.END) {
			return 1;
		}

		return 2;
	}

	
	private void readFog(CameraRenderState cameraState) {
		this.fogColor.set(cameraState.fogData.color);
		this.fogStart = cameraState.fogData.environmentalStart;
		this.fogEnd = cameraState.fogData.environmentalEnd;
	}

	private void readPlayer(Minecraft minecraft, ClientLevel level, Camera camera) {
		float pt = this.partialTick;
		LocalPlayer player = minecraft.player;
		Entity cameraEntity = minecraft.getCameraEntity();

		this.isEyeInWater = eyeInWater(camera.getFluidInCamera(),
				player != null && player.isSpectator());
		this.heavyFog = minecraft.gui.hud.getBossOverlay().shouldCreateWorldFog();
		this.hideGui = minecraft.gui.hud.isHidden();
		this.rightHanded = minecraft.options.mainHand().get() == HumanoidArm.RIGHT;
		this.screenBrightness = minecraft.options.gamma().get().floatValue();
		this.firstPerson = minecraft.options.getCameraType().isFirstPerson();
		this.spectator = minecraft.gameMode != null
				&& minecraft.gameMode.getPlayerMode() == GameType.SPECTATOR;

		if (cameraEntity != null) {
			this.cameraEntityTickCount = cameraEntity.tickCount;

			Vec3 eye = cameraEntity.getEyePosition(pt);
			this.eyePosition.set(eye.x, eye.y, eye.z);
			Vec3 forward = cameraEntity.getForward();
			this.playerBodyVector.set((float) forward.x, (float) forward.y, (float) forward.z);

			// At the block the eyes are in, which is not the block the feet are in and is the
			// whole reason a pack can tell that the player's head is above the water.
			BlockPos eyeBlock = BlockPos.containing(eye.x, cameraEntity.getEyeY(), eye.z);
			this.eyeBrightnessBlock = level.getBrightness(LightLayer.BLOCK, eyeBlock) * 16;
			this.eyeBrightnessSky = level.getBrightness(LightLayer.SKY, eyeBlock) * 16;
		}

		if (cameraEntity instanceof LivingEntity living) {
			Vec3 look = living.getViewVector(pt);
			this.playerLookVector.set((float) look.x, (float) look.y, (float) look.z);
			this.blindness = blindness(living);
			this.darknessFactor = living.getEffectBlendFactor(MobEffects.DARKNESS, pt);
		} else {
			this.blindness = 0.0F;
			this.darknessFactor = 0.0F;
		}

		this.nightVision = nightVision(player, cameraEntity, pt);
		this.darknessLightFactor = darknessLightFactor(minecraft, player, pt);
		this.playerMood = player == null ? 0.0F : Mth.clamp(player.getCurrentMood(), 0.0F, 1.0F);

		readFlags(player);
		readStats(minecraft, player);
		readSelection(minecraft, level, camera);
		readLightning(level, pt);
		readHeld(player);
	}

	
	private static int eyeInWater(FogType submersion, boolean spectator) {
		if (submersion == FogType.WATER) {
			return 1;
		} else if (!spectator && submersion == FogType.LAVA) {
			return 2;
		} else if (submersion == FogType.POWDER_SNOW) {
			return 3;
		}

		return 0;
	}

	
	private static float blindness(LivingEntity living) {
		MobEffectInstance blindness = living.getEffect(MobEffects.BLINDNESS);
		if (blindness == null) {
			return 0.0F;
		}

		return blindness.isInfiniteDuration() ? 1.0F
				: Mth.clamp(blindness.getDuration() / 20.0F, 0.0F, 1.0F);
	}

	
	private static float nightVision(LocalPlayer player, Entity cameraEntity, float pt) {
		if (cameraEntity instanceof LivingEntity living
				&& living.hasEffect(MobEffects.NIGHT_VISION)) {
			return Mth.clamp(GameRenderer.nightVisionScale(living, pt), 0.0F, 1.0F);
		}

		if (player != null && player.getWaterVision() > 0.0F
				&& player.hasEffect(MobEffects.CONDUIT_POWER)) {
			return Mth.clamp(player.getWaterVision(), 0.0F, 1.0F);
		}

		return 0.0F;
	}

	
	private static float darknessLightFactor(Minecraft minecraft, LocalPlayer player, float pt) {
		if (player == null) {
			return 0.0F;
		}

		float scale = minecraft.options.darknessEffectScale().get().floatValue();
		float gamma = player.getEffectBlendFactor(MobEffects.DARKNESS, pt) * scale;
		float darkness = Math.max(0.0F,
				Mth.cos((player.tickCount - pt) * (float) Math.PI * 0.025F) * 0.45F * gamma);

		return darkness * scale;
	}

	private void readFlags(LocalPlayer player) {
		if (player == null) {
			// The mount goes back to nothing rather than standing: each of the four names it answers
			// asks after the player before it asks after the vehicle and gives up on the first
			// question (uniforms/CoreVKExclusiveUniforms.java:106, :119, :126 and :137). A number
			// carried over is the last world's mount read against this world's table, which names a
			// different animal.
			noVehicle();

			noFlags();

			return;
		}

		this.sneaking = player.isCrouching();
		this.sprinting = player.isSprinting();
		// hurtTime and not isHurt. The second one answers a different question, and CoreVK's comment
		// on the line is emphatic about it.
		this.hurt = player.hurtTime > 0;
		this.invisible = player.isInvisible();
		this.burning = player.isOnFire();
		this.onGround = player.onGround();
		this.elytraFlying = player.isFallFlying();
		this.riding = player.isPassenger();
		this.feetInWater = player.isInShallowWater();
		this.swimming = player.isSwimming();

		Entity vehicle = player.getVehicle();
		if (vehicle == null) {
			noVehicle();
		} else {
			this.vehicleInWater = vehicle.isInShallowWater();
			this.vehicleId = PackNameIds.entity(vehicle.getType());
			Vec3 forward = vehicle.getForward();
			this.vehicleLookVector.set(forward.x, forward.y, forward.z);
			Vec3 position = vehicle.getPosition(this.partialTick);
			this.relativeVehiclePosition.set(this.shift.unshifted())
					.sub(position.x, position.y, position.z);
		}
	}

	
	private void noVehicle() {
		this.vehicleInWater = false;
		this.vehicleId = 0;
		this.vehicleLookVector.zero();
		this.relativeVehiclePosition.zero();
	}

	
	private void noFlags() {
		this.sneaking = false;
		this.sprinting = false;
		this.hurt = false;
		this.invisible = false;
		this.burning = false;
		this.onGround = false;
		this.elytraFlying = false;
		this.riding = false;
		this.feetInWater = false;
		this.swimming = false;
	}

	
	private void readStats(Minecraft minecraft, LocalPlayer player) {
		boolean survival = player != null && minecraft.gameMode != null
				&& minecraft.gameMode.getPlayerMode().isSurvival();
		if (!survival) {
			this.playerHealth = -1.0F;
			this.playerMaxHealth = -1.0F;
			this.playerHunger = -1.0F;
			this.playerArmor = -1.0F;
			this.playerAir = -1.0F;
			this.playerMaxAir = -1.0F;

			return;
		}

		this.playerHealth = player.getHealth() / player.getMaxHealth();
		this.playerMaxHealth = player.getMaxHealth();
		this.playerHunger = player.getFoodData().getFoodLevel() / MAX_HUNGER;
		this.playerArmor = player.getArmorValue() / MAX_ARMOR;
		this.playerAir = (float) player.getAirSupply() / (float) player.getMaxAirSupply();
		this.playerMaxAir = player.getMaxAirSupply();
	}

	
	private void readSelection(Minecraft minecraft, ClientLevel level, Camera camera) {
		HitResult hit = minecraft.hitResult;
		if (hit == null || hit.getType() != HitResult.Type.BLOCK
				|| !(minecraft.getCameraEntity() instanceof Player player)
				|| minecraft.gui.hud.isHidden()) {
			nothingSelected();

			return;
		}

		BlockPos block = ((BlockHitResult) hit).getBlockPos();
		if (!player.getAbilities().mayBuild && !outlined(minecraft, level, player, block)) {
			nothingSelected();

			return;
		}

		Vec3 position = camera.position();
		this.selectedBlockPos.set((float) (block.getX() + 0.5 - position.x),
				(float) (block.getY() + 0.5 - position.y),
				(float) (block.getZ() + 0.5 - position.z));

		BlockState state = level.getBlockState(block);
		this.selectedBlockId = state.isAir() || !level.getWorldBorder().isWithinBounds(block)
				? 0
				: BlockStateIds.id(state);
	}

	
	private void nothingSelected() {
		this.selectedBlockPos.set(NOTHING_SELECTED);
		this.selectedBlockId = 0;
	}

	
	private static boolean outlined(Minecraft minecraft, ClientLevel level, Player player,
			BlockPos block) {
		if (minecraft.gameMode != null
				&& minecraft.gameMode.getPlayerMode() == GameType.SPECTATOR) {
			return level.getBlockState(block).getMenuProvider(level, block) != null;
		}

		ItemStack held = player.getMainHandItem();
		BlockInWorld inWorld = new BlockInWorld(level, block, false);

		return !held.isEmpty() && (held.canBreakBlockInAdventureMode(inWorld)
				|| held.canPlaceOnBlockInAdventureMode(inWorld));
	}

	
	private void readLightning(ClientLevel level, float pt) {
		long tick = level.getGameTime();
		if (tick == this.lightningTick) {
			return;
		}

		this.lightningTick = tick;
		this.lightningBoltPosition.zero();
		for (Entity entity : level.entitiesForRendering()) {
			if (entity instanceof LightningBolt) {
				Vec3 position = entity.getPosition(pt);
				Vector3dc camera = this.shift.unshifted();
				this.lightningBoltPosition.set((float) (position.x - camera.x()),
						(float) (position.y - camera.y()),
						(float) (position.z - camera.z()), 1.0F);

				return;
			}
		}
	}

	
	private void readHeld(LocalPlayer player) {
		if (player == null) {
			this.heldItemId = NameIds.NONE;
			this.heldItemId2 = NameIds.NONE;
			this.heldBlockLight = 0;
			this.heldBlockLight2 = 0;

			return;
		}

		ItemStack mainHand = player.getItemInHand(InteractionHand.MAIN_HAND);
		ItemStack offHand = player.getItemInHand(InteractionHand.OFF_HAND);
		this.heldItemId = itemId(mainHand);
		this.heldItemId2 = itemId(offHand);

		int main = emission(mainHand);
		int off = emission(offHand);
		this.heldBlockLight = this.oldHandLight ? Math.max(main, off) : main;
		this.heldBlockLight2 = off;
	}

	
	private static int itemId(ItemStack stack) {
		if (stack == null) {
			return NameIds.NONE;
		}

		Identifier name = stack.get(DataComponents.ITEM_MODEL);

		return PackNameIds.item(name == null ? BuiltInRegistries.ITEM.getKey(stack.getItem()) : name);
	}

	
	private static int emission(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return 0;
		}

		Block block = Block.byItem(stack.getItem());

		return block == null ? 0 : block.defaultBlockState().getLightEmission();
	}

	private void readBiome(Minecraft minecraft, ClientLevel level) {
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return;
		}

		// At the player's block and not the camera's. They are different in third person, and very
		// different when the camera has backed through a wall.
		BlockPos position = player.blockPosition();
		Holder<Biome> holder = level.getBiome(position);
		// The number and the category are properties of the biome, and the registry hands the
		// same holder back for it frame after frame: the sixteen tag tests of the category and
		// the key lookup of the number run when the player crosses into another biome, which is
		// how CoreVK reads them too, off a value it keeps on the biome itself. What neither
		// follows is a data pack reload rebinding the tags of a biome the player is standing in:
		// the category catches up when they leave it, where CoreVK keeps its first answer for the
		// session.
		if (holder != this.biomeHolder) {
			this.biomeHolder = holder;
			this.biomeId = BiomeClassifier.identify(holder);
			this.biomeCategory = BiomeClassifier.categoryOf(holder);
		}
		this.biomePrecipitation =
				switch (holder.value().getPrecipitationAt(position, level.getSeaLevel())) {
					case NONE -> 0;
					case RAIN -> 1;
					case SNOW -> 2;
				};
		this.rainfall = ((BiomeHumidity) (Object) holder.value()).corevk$downfall();
		this.temperature = holder.value().getBaseTemperature();
	}

	private void readEngine(Minecraft minecraft) {
		TextureFilteringMethod filtering = minecraft.options.textureFiltering().get();
		this.anisotropy = filtering == TextureFilteringMethod.ANISOTROPIC
				? minecraft.options.maxAnisotropyValue()
				: 0.0F;
		this.textureFilteringMode = switch (filtering) {
			case NONE -> 0;
			case RGSS -> 1;
			case ANISOTROPIC -> 2;
		};

		// Divided without a guard, because Chunk Fade set to none is a normal setting and not the
		// accident a guard would suggest, and because infinity is what CoreVK hands there
		// (uniforms/CoreVKExclusiveUniforms.java:46). A section weighted by
		// min(1.0, age * chunkFadeTimeInv) then reads as already faded in for every age but nought,
		// where a zero would hold every section at the start of a fade that never advances. At an
		// age of nought the product is a NaN and the min is what the driver makes of it, which is
		// the same hole CoreVK has: the value is handed as CoreVK hands it rather than corrected here,
		// so a pack tuned against one engine reads the same from the other.
		double fadeMillis = minecraft.options.chunkSectionFadeInTime().get() * 1000.0;
		this.chunkFadeTimeInv = (float) (1.0 / fadeMillis);

		readAtlas(minecraft);
	}

	
	private void readAtlas(Minecraft minecraft) {
		try {
			AbstractTexture atlas = minecraft.getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS);
			if (atlas.getTexture() != null) {
				this.atlasWidth = atlas.getTexture().getWidth(0);
				this.atlasHeight = atlas.getTexture().getHeight(0);
			}
		} catch (RuntimeException e) {
			// Asked for before the atlases are stitched, which is a state that fixes itself. Not
			// logged, because this runs every frame and the answer arrives on its own.
		}
	}

	
	private static final class CameraShift {

		private static final double WALK_RANGE = 30000.0;
		private static final double TP_RANGE = 1000.0;

		private final Vector3d shift = new Vector3d();
		private final Vector3d current = new Vector3d();
		private final Vector3d previous = new Vector3d();
		private final Vector3d currentUnshifted = new Vector3d();
		private final Vector3d previousUnshifted = new Vector3d();

		private boolean seeded;

		void advance(Vec3 position) {
			this.previous.set(this.current);
			this.previousUnshifted.set(this.currentUnshifted);
			this.currentUnshifted.set(position.x, position.y, position.z);
			this.current.set(this.currentUnshifted).add(this.shift);

			if (!this.seeded) {
				// The same guard the matrices give themselves, and for the same reason: the first
				// frame after a world change has no previous position, and the origin standing in
				// for it is a motion vector the width of the world. It also keeps the teleport test
				// below from reading the spawn itself as a teleport.
				this.previous.set(this.current);
				this.previousUnshifted.set(this.currentUnshifted);
				this.seeded = true;
			}

			double dX = shiftOf(this.current.x, this.previous.x);
			double dZ = shiftOf(this.current.z, this.previous.z);
			if (dX == 0.0 && dZ == 0.0) {
				return;
			}

			// This frame and the previous one move by the same amount, so that the difference
			// between them, which is all a motion vector is, survives the shift.
			this.shift.x += dX;
			this.current.x += dX;
			this.previous.x += dX;
			this.shift.z += dZ;
			this.current.z += dZ;
			this.previous.z += dZ;
		}

		void reset() {
			this.shift.zero();
			this.current.zero();
			this.previous.zero();
			this.currentUnshifted.zero();
			this.previousUnshifted.zero();
			this.seeded = false;
		}

		private static double shiftOf(double value, double previous) {
			if (Math.abs(value) > WALK_RANGE || Math.abs(value - previous) > TP_RANGE) {
				return -(value - (value % WALK_RANGE));
			}

			return 0.0;
		}

		Vector3dc shifted() {
			return this.current;
		}

		Vector3dc previousShifted() {
			return this.previous;
		}

		Vector3dc unshifted() {
			return this.currentUnshifted;
		}

		Vector3dc previousUnshifted() {
			return this.previousUnshifted;
		}
	}

	// frame and time

	@Override
	public int frameCounter() {
		return this.frameCounter;
	}

	@Override
	public float frameTime() {
		return this.frameTime;
	}

	@Override
	public float frameTimeCounter() {
		return this.frameTimeCounter;
	}

	@Override
	public float partialTick() {
		return this.partialTick;
	}

	@Override
	public long gameTime() {
		return this.gameTime;
	}

	@Override
	public float glintAlpha() {
		return this.glintAlpha;
	}

	// viewport

	@Override
	public float viewWidth() {
		return this.viewWidth;
	}

	@Override
	public float viewHeight() {
		return this.viewHeight;
	}

	// camera positions

	@Override
	public Vector3dc cameraPosition() {
		return this.shift.shifted();
	}

	@Override
	public Vector3dc previousCameraPosition() {
		return this.shift.previousShifted();
	}

	@Override
	public Vector3dc cameraPositionUnshifted() {
		return this.shift.unshifted();
	}

	@Override
	public Vector3dc previousCameraPositionUnshifted() {
		return this.shift.previousUnshifted();
	}

	// world

	@Override
	public long worldTime() {
		return this.worldTime;
	}

	@Override
	public long worldDay() {
		return this.worldDay;
	}

	@Override
	public int moonPhase() {
		return this.moonPhase;
	}

	@Override
	public float sunAngleDegrees() {
		return this.sunAngleDegrees;
	}

	@Override
	public float moonAngleDegrees() {
		return this.moonAngleDegrees;
	}

	@Override
	public float sunPathRotation() {
		return this.directives.sunPathRotation();
	}

	@Override
	public float rainStrength() {
		return this.rainStrength;
	}

	@Override
	public float thunderStrength() {
		return this.thunderStrength;
	}

	@Override
	public int skyColorPacked() {
		return this.skyColorPacked;
	}

	@Override
	public float cloudHeight() {
		return this.cloudHeight;
	}

	@Override
	public int bedrockLevel() {
		return this.bedrockLevel;
	}

	@Override
	public int heightLimit() {
		return this.heightLimit;
	}

	@Override
	public int logicalHeightLimit() {
		return this.logicalHeightLimit;
	}

	@Override
	public boolean hasCeiling() {
		return this.hasCeiling;
	}

	@Override
	public boolean hasSkylight() {
		return this.hasSkylight;
	}

	@Override
	public float ambientLight() {
		return this.ambientLight;
	}

	@Override
	public int dimensionOrdinal() {
		return this.dimensionOrdinal;
	}

	@Override
	public int seaLevel() {
		return this.seaLevel;
	}

	@Override
	public int biomeId() {
		return this.biomeId;
	}

	@Override
	public int biomeCategory() {
		return this.biomeCategory;
	}

	@Override
	public int biomePrecipitation() {
		return this.biomePrecipitation;
	}

	
	@Override
	public float rainfall() {
		return this.rainfall;
	}

	@Override
	public float temperature() {
		return this.temperature;
	}

	// end flash

	@Override
	public boolean hasEndFlash() {
		return this.hasEndFlash;
	}

	@Override
	public boolean endFlashShadows() {
		return this.endFlashShadows;
	}

	@Override
	public float endFlashXAngleDegrees() {
		return this.endFlashXAngle;
	}

	@Override
	public float endFlashYAngleDegrees() {
		return this.endFlashYAngle;
	}

	@Override
	public float endFlashIntensity() {
		return this.endFlashIntensity;
	}

	@Override
	public float previousEndFlashIntensity() {
		return this.previousEndFlashIntensity;
	}

	// fog

	@Override
	public float fogR() {
		return this.fogColor.x;
	}

	@Override
	public float fogG() {
		return this.fogColor.y;
	}

	@Override
	public float fogB() {
		return this.fogColor.z;
	}

	@Override
	public float fogA() {
		return this.fogColor.w;
	}

	@Override
	public float fogStart() {
		return this.fogStart;
	}

	@Override
	public float fogEnd() {
		return this.fogEnd;
	}

	
	@Override
	public float fogDensity() {
		return 0.0F;
	}

	
	@Override
	public int fogMode() {
		return FOG_OFF;
	}

	
	@Override
	public int fogShape() {
		return FOG_SHAPE_OFF;
	}

	@Override
	public boolean heavyFog() {
		return this.heavyFog;
	}

	// player

	@Override
	public int isEyeInWater() {
		return this.isEyeInWater;
	}

	@Override
	public Vector3dc eyePosition() {
		return this.eyePosition;
	}

	@Override
	public Vector3fc playerLookVector() {
		return this.playerLookVector;
	}

	@Override
	public Vector3fc playerBodyVector() {
		return this.playerBodyVector;
	}

	@Override
	public float blindness() {
		return this.blindness;
	}

	@Override
	public float darknessFactor() {
		return this.darknessFactor;
	}

	@Override
	public float nightVision() {
		return this.nightVision;
	}

	@Override
	public float darknessLightFactor() {
		return this.darknessLightFactor;
	}

	@Override
	public int cameraEntityTickCount() {
		return this.cameraEntityTickCount;
	}

	@Override
	public float screenBrightness() {
		return this.screenBrightness;
	}

	@Override
	public float playerMood() {
		return this.playerMood;
	}

	
	@Override
	public float constantMood() {
		return 0.0F;
	}

	@Override
	public int eyeBrightnessBlock() {
		return this.eyeBrightnessBlock;
	}

	@Override
	public int eyeBrightnessSky() {
		return this.eyeBrightnessSky;
	}

	@Override
	public boolean sneaking() {
		return this.sneaking;
	}

	@Override
	public boolean sprinting() {
		return this.sprinting;
	}

	@Override
	public boolean hurt() {
		return this.hurt;
	}

	@Override
	public boolean invisible() {
		return this.invisible;
	}

	@Override
	public boolean burning() {
		return this.burning;
	}

	@Override
	public boolean onGround() {
		return this.onGround;
	}

	@Override
	public boolean hideGui() {
		return this.hideGui;
	}

	@Override
	public boolean rightHanded() {
		return this.rightHanded;
	}

	@Override
	public boolean spectator() {
		return this.spectator;
	}

	@Override
	public boolean firstPerson() {
		return this.firstPerson;
	}

	@Override
	public boolean elytraFlying() {
		return this.elytraFlying;
	}

	@Override
	public boolean riding() {
		return this.riding;
	}

	@Override
	public boolean feetInWater() {
		return this.feetInWater;
	}

	@Override
	public boolean swimming() {
		return this.swimming;
	}

	@Override
	public boolean vehicleInWater() {
		return this.vehicleInWater;
	}

	@Override
	public int vehicleId() {
		return this.vehicleId;
	}

	@Override
	public Vector3dc vehicleLookVector() {
		return this.vehicleLookVector;
	}

	@Override
	public Vector3dc relativeVehiclePosition() {
		return this.relativeVehiclePosition;
	}

	@Override
	public float playerHealth() {
		return this.playerHealth;
	}

	@Override
	public float playerMaxHealth() {
		return this.playerMaxHealth;
	}

	@Override
	public float playerHunger() {
		return this.playerHunger;
	}

	@Override
	public float playerMaxHunger() {
		return MAX_HUNGER;
	}

	@Override
	public float playerArmor() {
		return this.playerArmor;
	}

	@Override
	public float playerMaxArmor() {
		return MAX_ARMOR;
	}

	@Override
	public float playerAir() {
		return this.playerAir;
	}

	@Override
	public float playerMaxAir() {
		return this.playerMaxAir;
	}

	@Override
	public Vector3fc selectedBlockPos() {
		return this.selectedBlockPos;
	}

	@Override
	public int selectedBlockId() {
		return this.selectedBlockId;
	}

	@Override
	public Vector4fc lightningBoltPosition() {
		return this.lightningBoltPosition;
	}

	@Override
	public int heldItemId() {
		return this.heldItemId;
	}

	@Override
	public int heldItemId2() {
		return this.heldItemId2;
	}

	@Override
	public int heldBlockLight() {
		return this.heldBlockLight;
	}

	@Override
	public int heldBlockLight2() {
		return this.heldBlockLight2;
	}

	// engine settings and atlas

	@Override
	public int atlasWidth() {
		return this.atlasWidth;
	}

	@Override
	public int atlasHeight() {
		return this.atlasHeight;
	}

	// The same number EngineDefines writes for the stage, and it is the ordinal on both sides.
	@Override
	@SuppressWarnings("EnumOrdinal")
	public int renderStage() {
		return this.stage.ordinal();
	}

	@Override
	public float anisotropy() {
		return this.anisotropy;
	}

	
	@Override
	public int colorSpace() {
		return 0;
	}

	@Override
	public int textureFilteringMode() {
		return this.textureFilteringMode;
	}

	@Override
	public float chunkFadeTimeInv() {
		return this.chunkFadeTimeInv;
	}

	// pack directives

	@Override
	public float wetnessHalfLife() {
		return this.directives.wetnessHalflife();
	}

	
	@Override
	public float drynessHalfLife() {
		return this.directives.drynessHalflife();
	}

	@Override
	public float eyeBrightnessHalfLife() {
		return this.directives.eyeBrightnessHalflife();
	}

	@Override
	public float centerDepthHalfLife() {
		return this.directives.centerDepthHalflife();
	}

	@Override
	public float ambientOcclusionLevel() {
		return this.directives.ambientOcclusionLevel();
	}

	@Override
	public float noiseTextureResolution() {
		return this.directives.noiseTextureResolution();
	}

	@Override
	public float shadowDistance() {
		return this.directives.shadowDistance();
	}

	@Override
	public float shadowNearPlane() {
		return this.directives.shadowNearPlane();
	}

	@Override
	public float shadowFarPlane() {
		return this.directives.shadowFarPlane();
	}

	@Override
	public float shadowIntervalSize() {
		return this.directives.shadowIntervalSize();
	}

	// view geometry, delegated

	@Override
	public Matrix4fc gbufferModelView() {
		return this.view.gbufferModelView();
	}

	@Override
	public Matrix4fc gbufferModelViewInverse() {
		return this.view.gbufferModelViewInverse();
	}

	@Override
	public Matrix4fc passModelView() {
		return this.view.passModelView();
	}

	@Override
	public Matrix4fc cameraBob() {
		return this.view.cameraBob();
	}

	@Override
	public Matrix4fc passModelViewInverse() {
		return this.view.passModelViewInverse();
	}

	@Override
	public Matrix4fc passProjection() {
		return this.view.passProjection();
	}

	@Override
	public Matrix4fc passProjectionInverse() {
		return this.view.passProjectionInverse();
	}

	@Override
	public Vector4fc passColour() {
		return this.view.passColour();
	}

	@Override
	public float passAlphaTest() {
		return this.view.passAlphaTest();
	}

	@Override
	public Matrix4fc gbufferProjection() {
		return this.view.gbufferProjection();
	}

	@Override
	public Matrix4fc gbufferProjectionInverse() {
		return this.view.gbufferProjectionInverse();
	}

	@Override
	public Matrix4fc gbufferPreviousModelView() {
		return this.view.gbufferPreviousModelView();
	}

	@Override
	public Matrix4fc gbufferPreviousProjection() {
		return this.view.gbufferPreviousProjection();
	}

	@Override
	public Matrix4fc shadowModelView() {
		return this.view.shadowModelView();
	}

	@Override
	public Matrix4fc shadowModelViewInverse() {
		return this.view.shadowModelViewInverse();
	}

	@Override
	public Matrix4fc shadowProjection() {
		return this.view.shadowProjection();
	}

	@Override
	public Matrix4fc shadowProjectionInverse() {
		return this.view.shadowProjectionInverse();
	}

	@Override
	public Matrix4fc drawnShadowModelView() {
		return this.view.drawnShadowModelView();
	}

	@Override
	public Matrix4fc drawnShadowModelViewInverse() {
		return this.view.drawnShadowModelViewInverse();
	}

	@Override
	public Matrix4fc drawnShadowProjection() {
		return this.view.drawnShadowProjection();
	}

	@Override
	public Matrix4fc drawnShadowProjectionInverse() {
		return this.view.drawnShadowProjectionInverse();
	}

	@Override
	public Matrix4fc drawnDistantProjection() {
		return this.view.drawnDistantProjection();
	}

	@Override
	public boolean distantDepthPair(Vector2f dest) {
		return this.view.distantDepthPair(dest);
	}

	@Override
	public Matrix4fc dhProjection() {
		return this.view.dhProjection();
	}

	@Override
	public Matrix4fc dhProjectionInverse() {
		return this.view.dhProjectionInverse();
	}

	@Override
	public Matrix4fc dhPreviousProjection() {
		return this.view.dhPreviousProjection();
	}

	@Override
	public float dhNearPlane() {
		return this.view.dhNearPlane();
	}

	@Override
	public float dhFarPlane() {
		return this.view.dhFarPlane();
	}

	@Override
	public int dhRenderDistance() {
		return this.view.dhRenderDistance();
	}

	@Override
	public float near() {
		return this.view.near();
	}

	@Override
	public float far() {
		return this.view.far();
	}

	@Override
	public Vector4fc depthConvention() {
		return this.view.depthConvention();
	}
}
