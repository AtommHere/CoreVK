package net.corevk.corevk.render;

import net.corevk.corevk.CoreVK;

import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ShadowAmortisation {

	public static final int DEFAULT_FRAMES = 0;

	public static final int MIN_FRAMES = 0;

	public static final int MAX_FRAMES = 2;

	private static final double MOVE_BLOCKS = 4.0;

	private static final float ANGLE_TURNS = 0.0003F;

	private static final String SETTING_FILE = "amortise-shadow";

	private static int frames = -1;

	private static final Vector3d anchorCamera = new Vector3d();

	private static float anchorAngle;

	private static final Vector3d pendingCamera = new Vector3d();

	private static float pendingAngle;

	private static boolean seeded;

	private static int sinceDraw;

	private static boolean drawThisFrame = true;

	private static boolean drewLastFrame = true;

	private static boolean drewSinceBegin;

	private static boolean missedLastPlan;

	private static boolean keepThisFrame;

	private static boolean keptAtLastDraw;

	private static boolean saidRefused;

	private static int countedFrames;
	private static int countedDraws;

	private ShadowAmortisation() {
	}

	public static boolean beginFrame(Vector3dc camera, float shadowAngle, boolean amortisable) {

		missedLastPlan = seeded && drawThisFrame && !drewSinceBegin;

		drewLastFrame = drewSinceBegin;
		drewSinceBegin = false;

		if (!drewLastFrame) {
			sinceDraw++;
		}

		pendingCamera.set(camera);
		pendingAngle = shadowAngle;

		int asked = frames();

		if (asked > 0 && !amortisable && !saidRefused) {
			saidRefused = true;
			CoreVK.logger().info("This pack voxelises into its shadow pass, so the map is drawn "
					+ "every frame whatever the reuse setting says");
		}

		if (asked > 0 && ++countedFrames >= 600) {
			CoreVK.logger().info("Shadow map: the opaque world was drawn into it {} times in the "
					+ "last {} frames", countedDraws, countedFrames);
			countedFrames = 0;
			countedDraws = 0;
		}

		keepThisFrame = asked > 0 && amortisable;

		drawThisFrame = !seeded || asked <= 0 || !amortisable || !keptAtLastDraw
				|| sinceDraw >= asked
				|| anchorCamera.distance(camera) >= MOVE_BLOCKS
				|| Math.abs(shadowAngle - anchorAngle) >= ANGLE_TURNS;

		return drewLastFrame;
	}

	public static boolean missedLastPlan() {
		return missedLastPlan;
	}

	public static boolean drawTerrainThisFrame() {
		return drawThisFrame;
	}

	public static void drawn() {
		countedDraws++;
		anchorCamera.set(pendingCamera);
		anchorAngle = pendingAngle;
		sinceDraw = 0;
		seeded = true;
		drewSinceBegin = true;
		keptAtLastDraw = keepThisFrame;
	}

	public static boolean keepsDrawnMap() {
		return keepThisFrame;
	}

	public static void forget() {
		seeded = false;
		sinceDraw = 0;
		drawThisFrame = true;
		drewLastFrame = true;
		drewSinceBegin = false;
		keptAtLastDraw = false;
		saidRefused = false;
	}

	public static int frames() {
		if (frames < 0) {
			frames = clamp(read());

			if (frames > 0) {
				CoreVK.logger().info("Shadow map kept for {} frame(s) after the one that draws it, "
						+ "so the ground in it is that many frames old, everything that moves being "
						+ "drawn afresh", frames);
			} else {
				CoreVK.logger().info("Shadow map drawn on every frame, none kept");
			}
		}

		return frames;
	}

	public static void setFrames(int asked) {
		frames = clamp(asked);
		try {
			Path file = file();
			Files.createDirectories(file.getParent());
			Files.writeString(file, frames + "\n");
		} catch (IOException | RuntimeException e) {

			CoreVK.logger().warn("Could not store the shadow amortisation, it holds for this "
					+ "session only", e);
		}
	}

	private static int read() {
		try {
			Path file = file();
			if (!Files.isRegularFile(file)) {
				return DEFAULT_FRAMES;
			}

			String asked = Files.readString(file).trim();

			return asked.isEmpty() ? DEFAULT_FRAMES : Integer.parseInt(asked);
		} catch (IOException | RuntimeException ignored) {
			return DEFAULT_FRAMES;
		}
	}

	private static Path file() {
		return CoreVK.platform().gameDirectory().resolve("corevk").resolve(SETTING_FILE);
	}

	private static int clamp(int asked) {
		return Math.min(Math.max(asked, MIN_FRAMES), MAX_FRAMES);
	}
}
