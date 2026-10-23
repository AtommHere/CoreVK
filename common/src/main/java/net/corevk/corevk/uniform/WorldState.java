package net.corevk.corevk.uniform;

import org.joml.Vector3dc;
import org.joml.Vector3fc;
import org.joml.Vector4fc;

public interface WorldState extends ViewSource {

	// frame and time

	int frameCounter();

	
	float frameTime();

	
	float frameTimeCounter();

	float partialTick();

	long gameTime();

	
	float glintAlpha();

	// viewport

	float viewWidth();

	float viewHeight();

	// camera positions, in the published convention

	
	Vector3dc cameraPosition();

	
	Vector3dc previousCameraPosition();

	Vector3dc cameraPositionUnshifted();

	Vector3dc previousCameraPositionUnshifted();

	// world

	long worldTime();

	long worldDay();

	int moonPhase();

	float sunAngleDegrees();

	float moonAngleDegrees();

	float sunPathRotation();

	float rainStrength();

	float thunderStrength();

	int skyColorPacked();

	float cloudHeight();

	int bedrockLevel();

	int heightLimit();

	int logicalHeightLimit();

	boolean hasCeiling();

	boolean hasSkylight();

	float ambientLight();

	
	int dimensionOrdinal();

	int seaLevel();

	int biomeId();

	
	int biomeCategory();

	
	int biomePrecipitation();

	float rainfall();

	float temperature();

	// end flash, needed by the celestial values

	boolean hasEndFlash();

	
	boolean endFlashShadows();

	float endFlashXAngleDegrees();

	float endFlashYAngleDegrees();

	float endFlashIntensity();

	float previousEndFlashIntensity();

	// fog, from the game's own fog data

	float fogR();

	float fogG();

	float fogB();

	float fogA();

	float fogStart();

	float fogEnd();

	float fogDensity();

	int fogMode();

	int fogShape();

	boolean heavyFog();

	// player

	int isEyeInWater();

	Vector3dc eyePosition();

	Vector3fc playerLookVector();

	Vector3fc playerBodyVector();

	float blindness();

	float darknessFactor();

	float nightVision();

	float darknessLightFactor();

	int cameraEntityTickCount();

	float screenBrightness();

	float playerMood();

	float constantMood();

	int eyeBrightnessBlock();

	int eyeBrightnessSky();

	boolean sneaking();

	boolean sprinting();

	boolean hurt();

	boolean invisible();

	boolean burning();

	boolean onGround();

	boolean hideGui();

	boolean rightHanded();

	boolean spectator();

	boolean firstPerson();

	boolean elytraFlying();

	boolean riding();

	boolean feetInWater();

	boolean swimming();

	boolean vehicleInWater();

	
	int vehicleId();

	Vector3dc vehicleLookVector();

	Vector3dc relativeVehiclePosition();

	
	float playerHealth();

	float playerMaxHealth();

	float playerHunger();

	float playerMaxHunger();

	float playerArmor();

	float playerMaxArmor();

	float playerAir();

	float playerMaxAir();

	
	Vector3fc selectedBlockPos();

	
	int selectedBlockId();

	Vector4fc lightningBoltPosition();

	
	int heldItemId();

	
	int heldItemId2();

	int heldBlockLight();

	int heldBlockLight2();

	// engine settings and atlas

	int atlasWidth();

	int atlasHeight();

	
	int renderStage();

	float anisotropy();

	int colorSpace();

	int textureFilteringMode();

	float chunkFadeTimeInv();

	// pack directives, read once per pack load

	float wetnessHalfLife();

	
	float drynessHalfLife();

	float eyeBrightnessHalfLife();

	float centerDepthHalfLife();

	float ambientOcclusionLevel();

	float noiseTextureResolution();

	// shadow directives, needed by the shadow matrices

	float shadowDistance();

	float shadowNearPlane();

	float shadowFarPlane();

	float shadowIntervalSize();
}
