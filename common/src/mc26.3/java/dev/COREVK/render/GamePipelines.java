package net.corevk.corevk.render;

import net.corevk.corevk.CoreVK;
import net.corevk.corevk.mixin.game.RenderPipelinesAccessor;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import org.jspecify.annotations.Nullable;

public final class GamePipelines {

	private static @Nullable RenderPipeline weatherDepthWrite;

	private GamePipelines() {
	}

	public static RenderPipeline armorTranslucent() {
		return RenderPipelines.WOLF_ARMOR_CRACKS;
	}

	public static boolean solidPhase(RenderPipeline pipeline) {
		return pipeline == RenderPipelines.BANNER_PATTERN;
	}

	public static @Nullable RenderPipeline textBackground() {
		return null;
	}

	public static @Nullable RenderPipeline textBackgroundSeeThrough() {
		return null;
	}

	public static RenderPipeline weather() {
		return RenderPipelines.WEATHER;
	}

	public static RenderPipeline weatherDepthWrite() {
		RenderPipeline built = weatherDepthWrite;
		if (built == null) {
			built = RenderPipeline.builder(RenderPipelinesAccessor.corevk$weatherSnippet())
					.withLocation(Identifier.fromNamespaceAndPath(CoreVK.MOD_ID,
							"pipeline/weather_depth_write"))
					.build();
			weatherDepthWrite = built;
		}

		return built;
	}
}
