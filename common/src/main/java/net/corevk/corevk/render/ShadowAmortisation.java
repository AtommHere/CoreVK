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
		// What the previous frame ACTUALLY did, not what it planned: the stage refuses to open on
		// its own account (no chain, no device, an OpenGL boot, a pack whose shadow programs were
		// turned down), and on those frames the map is older than the plan says. The anchor has to
		// follow the map and never the intention, or the pack would be handed matrices for a map
		// that was never drawn.
		// Read before the line below clears drewSinceBegin, which is the binding constraint, and
		// before the plan is overwritten at the foot of this method. Answered rather than reported
		// from here: this class is one of the few of render/ the off-game harness compiles, and the
		// census it would call drags the game in with it.
		//
		// Guarded on seeded, which is what tells a real miss from the two states where the plan
		// says DRAW forever and nothing is wrong: the first frame after a load, and a pack that
		// keeps the opaque world out of its map, whose stage never reaches drawn() at all
		// (ShadowTerrain.java:364).
		missedLastPlan = seeded && drawThisFrame && !drewSinceBegin;

		drewLastFrame = drewSinceBegin;
		drewSinceBegin = false;
		// Counted here rather than by the stage: what the interval counts is frames since the map
		// was last FILLED, and this is the one place that sees every frame whatever the stage did.
		if (!drewLastFrame) {
			sinceDraw++;
		}

		pendingCamera.set(camera);
		pendingAngle = shadowAngle;

		int asked = frames();
		// Said once per pack, because a setting that does nothing and says nothing is worse than a
		// setting that is not there: this pack refuses the reuse on its own account, and the player
		// moving the slider would otherwise watch the frame rate not move.
		if (asked > 0 && !amortisable && !saidRefused) {
			saidRefused = true;
			CoreVK.logger().info("This pack voxelises into its shadow pass, so the map is drawn "
					+ "every frame whatever the reuse setting says");
		}

		// A rate and not a one-shot line: what has to be proved is how OFTEN the world is walked for
		// the light, and a message saying it happened once says nothing about the frame after it.
		if (asked > 0 && ++countedFrames >= 600) {
			CoreVK.logger().info("Shadow map: the opaque world was drawn into it {} times in the "
					+ "last {} frames", countedDraws, countedFrames);
			countedFrames = 0;
			countedDraws = 0;
		}

		keepThisFrame = asked > 0 && amortisable;
		// A map that was not kept when it was drawn is not in the store, and the store may hold an
		// older one from before the reuse was switched off: the first frame after switching it on
		// draws and keeps, so a put back only ever follows a kept draw.
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
			// Said once, either way: a shadow one frame late is the first thing to suspect for a
			// shadow artefact, and a session reading a log has no other way to know whether the
			// map it is looking at is this frame's.
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
			// Kept live anyway: a value that cannot be stored is still the one the player asked for
			// this session, and a selector that springs back to its old place says nothing at all.
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
			// A typo reads as the default, the map drawn every frame, which is the reference's
			// picture and never one a player did not ask for.
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
