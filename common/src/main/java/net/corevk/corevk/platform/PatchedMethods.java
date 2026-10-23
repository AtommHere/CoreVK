package net.corevk.corevk.platform;

public final class PatchedMethods {

	
	public static final String SKY_PASS =
			"addSkyPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;"
					+ "Lnet/minecraft/client/renderer/state/level/CameraRenderState;"
					+ "Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V";

	
	public static final String SKY_PASS_WIDENED =
			"addSkyPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;"
					+ "Lnet/minecraft/client/renderer/state/level/CameraRenderState;"
					+ "Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"
					+ "Lorg/joml/Matrix4fc;)V";

	
	public static final String WEATHER_RENDER =
			"render(Lnet/minecraft/world/phys/Vec3;"
					+ "Lnet/minecraft/client/renderer/state/level/WeatherRenderState;)V";

	
	public static final String WEATHER_RENDER_WIDENED =
			"render(Lnet/minecraft/world/phys/Vec3;"
					+ "Lnet/minecraft/client/renderer/state/level/WeatherRenderState;"
					+ "Lnet/minecraft/client/renderer/state/level/LevelRenderState;)V";

	private PatchedMethods() {
	}
}
