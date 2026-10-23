package net.corevk.corevk.mixin.game;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.corevk.corevk.render.LevelPass;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMainPassMixin {

	@WrapOperation(method = "lambda$addMainPass$0", require = 1,
			at = @At(value = "INVOKE",
					target = "Lcom/mojang/renderpearl/api/commands/CommandEncoder;createRenderPass("
							+ "Ljava/util/function/Supplier;"
							+ "Lcom/mojang/renderpearl/api/textures/GpuTextureView;Ljava/util/Optional;"
							+ "Lcom/mojang/renderpearl/api/textures/GpuTextureView;Ljava/util/OptionalDouble;)"
							+ "Lcom/mojang/renderpearl/api/commands/RenderPass;"))
	private RenderPass corevk$levelPass(CommandEncoder encoder, Supplier<String> label,
			GpuTextureView colour, Optional<?> clearColour, GpuTextureView depth,
			OptionalDouble clearDepth, Operation<RenderPass> original) {
		RenderPass first = original.call(encoder, label, colour, clearColour, depth, clearDepth);
		return LevelPass.open(first, colour, depth, () -> original.call(encoder, label, colour,
				Optional.empty(), depth, OptionalDouble.empty()));
	}
}
