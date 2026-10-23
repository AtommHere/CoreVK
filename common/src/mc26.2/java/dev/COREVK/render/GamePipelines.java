package net.corevk.corevk.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;

import org.jspecify.annotations.Nullable;

public final class GamePipelines {

	private GamePipelines() {
	}

	
	public static RenderPipeline armorTranslucent() {
		return RenderPipelines.ARMOR_TRANSLUCENT;
	}

	
	public static boolean solidPhase(RenderPipeline pipeline) {
		return false;
	}

	
	public static @Nullable RenderPipeline textBackground() {
		return RenderPipelines.TEXT_BACKGROUND;
	}

	
	public static @Nullable RenderPipeline textBackgroundSeeThrough() {
		return RenderPipelines.TEXT_BACKGROUND_SEE_THROUGH;
	}

	
	public static RenderPipeline weather() {
		return RenderPipelines.WEATHER_NO_DEPTH_WRITE;
	}

	
	public static @Nullable RenderPipeline weatherDepthWrite() {
		return RenderPipelines.WEATHER_DEPTH_WRITE;
	}
}
