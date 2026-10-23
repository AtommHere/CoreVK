package net.corevk.corevk.dh;

import net.corevk.corevk.CoreVK;

import org.joml.Vector2f;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class DhDepth {

	
	private static final String DELAYED = "com.seibel.distanthorizons.api.DhApi$Delayed";

	
	private static final String CLIENT_API =
			"com.seibel.distanthorizons.core.api.internal.ClientApi";

	
	private static Field renderParamsField;
	private static Field dhProjectionField;
	private static Field projectionScaleField;
	private static Field projectionOffsetField;

	private static Field configsField;
	private static Method graphicsMethod;
	private static Method renderDistanceMethod;
	private static Method renderingEnabledMethod;
	private static Method valueMethod;

	
	private static final int CHUNK = 16;

	private static boolean resolved;
	private static boolean usable;

	
	private static boolean distanceUsable = true;

	private DhDepth() {
	}

	
	public static boolean zRow(Vector2f dest) {
		if (!resolved) {
			resolve();
		}

		if (!usable) {
			return false;
		}

		try {
			Object params = renderParamsField.get(null);
			Object matrix = params == null ? null : dhProjectionField.get(params);
			if (matrix == null) {
				return false;
			}

			float offset = projectionOffsetField.getFloat(matrix);
			if (offset == 0.0F) {
				// Still the identity it was made as: DH has not rendered a frame of this world yet.
				return false;
			}

			dest.set(projectionScaleField.getFloat(matrix), offset);

			return true;
			// The error is caught with the rest and on purpose: the first read of that field is what
			// loads DH's client entry point, and a class of DH's that will not load is a far terrain
			// this engine goes without rather than a frame this engine drops.
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			usable = false;
			CoreVK.logger().warn("Distant Horizons' projection cannot be read, so its depth cannot be "
					+ "converted and its far terrain will stay flat for the rest of this session", e);
			return false;
		}
	}

	
	public static boolean planes(float scale, float offset, Vector2f dest) {
		if (scale <= 0.0F) {
			return false;
		}

		dest.set(offset / (1.0F + scale), offset / scale);

		return true;
	}

	
	public static int renderDistanceBlocks() {
		if (!resolved) {
			resolve();
		}

		if (!usable || !distanceUsable || !renderingEnabled()) {
			return -1;
		}

		try {
			Object configs = configsField.get(null);
			if (configs == null) {
				return -1;
			}

			Object value = renderDistanceMethod.invoke(graphicsMethod.invoke(configs));

			return (valueMethod.invoke(value) instanceof Integer chunks) ? chunks * CHUNK : -1;
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			distanceUsable = false;
			CoreVK.logger().warn("Distant Horizons' render distance cannot be read, so for the rest "
					+ "of this session a pack is given the game's own distance, counted in CHUNKS, "
					+ "while it is still told the far terrain is there and still given that mod's "
					+ "clip planes. A pack that measures a length against that distance will fog "
					+ "the far terrain far too close", e);
			return -1;
		}
	}

	
	public static boolean usable() {
		return usable;
	}

	
	public static boolean present() {
		if (!resolved) {
			resolve();
		}

		return usable && renderingEnabled();
	}

	
	private static boolean renderingEnabled() {
		try {
			Object configs = configsField.get(null);
			if (configs == null) {
				return false;
			}

			Object value = renderingEnabledMethod.invoke(graphicsMethod.invoke(configs));

			return valueMethod.invoke(value) instanceof Boolean on && on;
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			usable = false;
			CoreVK.logger().warn("Distant Horizons' rendering switch cannot be read, so its far "
					+ "terrain will stay flat for the rest of this session", e);

			return false;
		}
	}

	private static void resolve() {
		resolved = true;

		try {
			Class<?> delayed = Class.forName(DELAYED);

			resolveProjection();

			configsField = delayed.getField("configs");
			graphicsMethod = configsField.getType().getMethod("graphics");
			renderDistanceMethod = graphicsMethod.getReturnType().getMethod("chunkRenderDistance");
			renderingEnabledMethod = graphicsMethod.getReturnType().getMethod("renderingEnabled");
			valueMethod = renderDistanceMethod.getReturnType().getMethod("getValue");

			usable = true;
			CoreVK.logger().info("Distant Horizons found, its far terrain's volume, planes and "
					+ "switch will be read");
		} catch (ClassNotFoundException e) {
			// The ordinary case: DH is simply not installed. Not worth a line above debug.
			usable = false;
		} catch (ReflectiveOperationException | RuntimeException e) {
			usable = false;
			CoreVK.logger().info("Distant Horizons is installed but not in a shape a projection can "
					+ "be read out of, so its far terrain will stay flat: {}", e.toString());
		}
	}

	
	private static void resolveProjection() throws ReflectiveOperationException {
		// Without initialising it, which matters: the symbols are wanted before the first pack is
		// read and that class builds its render parameter out of DH's own injector as it loads. The
		// first read of the field below is what runs it, and that read is a frame being drawn, by
		// which time DH is up.
		Class<?> clientApi = Class.forName(CLIENT_API, false, DhDepth.class.getClassLoader());
		renderParamsField = clientApi.getDeclaredField("RENDER_PARAMS");
		renderParamsField.setAccessible(true);

		dhProjectionField = renderParamsField.getType().getField("dhProjectionMatrix");

		// Named for what they do and not for what they are called, because the two conventions
		// disagree: this is m22 and m23 of DH's own matrix class, which numbers the row first where
		// JOML numbers the column first and calls the same pair m22 and m32.
		Class<?> matrixType = dhProjectionField.getType();
		projectionScaleField = matrixType.getField("m22");
		projectionOffsetField = matrixType.getField("m23");
	}
}
