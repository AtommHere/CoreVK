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

				return false;
			}

			dest.set(projectionScaleField.getFloat(matrix), offset);

			return true;

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

			usable = false;
		} catch (ReflectiveOperationException | RuntimeException e) {
			usable = false;
			CoreVK.logger().info("Distant Horizons is installed but not in a shape a projection can "
					+ "be read out of, so its far terrain will stay flat: {}", e.toString());
		}
	}

	private static void resolveProjection() throws ReflectiveOperationException {

		Class<?> clientApi = Class.forName(CLIENT_API, false, DhDepth.class.getClassLoader());
		renderParamsField = clientApi.getDeclaredField("RENDER_PARAMS");
		renderParamsField.setAccessible(true);

		dhProjectionField = renderParamsField.getType().getField("dhProjectionMatrix");

		Class<?> matrixType = dhProjectionField.getType();
		projectionScaleField = matrixType.getField("m22");
		projectionOffsetField = matrixType.getField("m23");
	}
}
