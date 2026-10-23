package net.corevk.corevk.render;

import net.corevk.corevk.CoreVK;

import java.util.concurrent.atomic.AtomicBoolean;

public final class ShaderDebugInfo {

	private static final boolean ASKED = Boolean.getBoolean("corevk.shaderDebugInfo");

	private static final AtomicBoolean ANNOUNCED = new AtomicBoolean();

	private ShaderDebugInfo() {
	}

	public static boolean asked() {
		return ASKED;
	}

	public static String cacheWord() {
		return ASKED ? "debug-info" : "no-debug-info";
	}

	public static void announce() {
		if (!ANNOUNCED.compareAndSet(false, true)) {
			return;
		}

		if (ASKED) {
			CoreVK.logger().warn("Modules compiled WITH debug info, asked for by "
					+ "corevk.shaderDebugInfo: two to three times the bytes, and a place a "
					+ "validation layer can name inside a shader");

			return;
		}

		CoreVK.logger().info("Modules compiled without debug info, property="
				+ "corevk.shaderDebugInfo");
	}
}
