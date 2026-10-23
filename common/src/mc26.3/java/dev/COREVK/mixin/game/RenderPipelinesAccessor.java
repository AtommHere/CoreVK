package net.corevk.corevk.mixin.game;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderPipelines.class)
public interface RenderPipelinesAccessor {

	@Accessor("WEATHER_SNIPPET")
	static RenderPipeline.Snippet corevk$weatherSnippet() {
		throw new AssertionError("an accessor's body is replaced as the class is loaded");
	}
}
