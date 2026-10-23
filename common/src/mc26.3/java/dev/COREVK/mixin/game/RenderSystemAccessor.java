package net.corevk.corevk.mixin.game;

import com.mojang.blaze3d.pipeline.PipelineCache;
import com.mojang.blaze3d.systems.RenderSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import org.jspecify.annotations.Nullable;

@Mixin(RenderSystem.class)
public interface RenderSystemAccessor {

	@Accessor("currentPipelineCache")
	static @Nullable PipelineCache corevk$current() {
		throw new AssertionError();
	}

	@Accessor("fallbackPipelineCache")
	static @Nullable PipelineCache corevk$fallback() {
		throw new AssertionError();
	}
}
