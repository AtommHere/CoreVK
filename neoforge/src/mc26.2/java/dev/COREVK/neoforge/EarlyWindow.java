package net.corevk.corevk.neoforge;

import net.corevk.corevk.CoreVK;

import com.mojang.blaze3d.systems.GpuBackend;
import com.mojang.blaze3d.vulkan.VulkanBackend;
import net.neoforged.fml.loading.EarlyLoadingScreenController;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;

public final class EarlyWindow {

	private static boolean claimed;

	private static long orphan;

	private EarlyWindow() {
	}

	public static EarlyLoadingScreenController hand(GpuBackend backend,
			EarlyLoadingScreenController controller, boolean claim) {
		if (!(backend instanceof VulkanBackend)) {
			return controller;
		}

		if (claim && controller != null) {
			claimed = true;
		}

		return null;
	}

	public static void takeOver() {
		if (!claimed) {
			return;
		}

		claimed = false;
		EarlyLoadingScreenController controller = EarlyLoadingScreenController.current();
		if (controller == null) {

			CoreVK.logger().warn("NeoForge's early loading window was claimed and is gone by the "
					+ "time it should have been taken over, so it stays open behind the game");
			return;
		}

		try {
			orphan = controller.takeOverGlfwWindow();
			GL.createCapabilities();
		} catch (RuntimeException | LinkageError e) {

			CoreVK.logger().warn("Could not take NeoForge's early loading window over", e);
		}
	}

	public static void close() {
		if (orphan == 0L) {
			return;
		}

		try {
			GLFW.glfwMakeContextCurrent(0L);
			GL.setCapabilities(null);
			GLFW.glfwDestroyWindow(orphan);
			CoreVK.logger().info("Closed the early loading window NeoForge was left holding");
		} catch (RuntimeException | LinkageError e) {
			CoreVK.logger().warn("Could not close the early loading window NeoForge was left "
					+ "holding", e);
		} finally {
			orphan = 0L;
		}
	}
}
