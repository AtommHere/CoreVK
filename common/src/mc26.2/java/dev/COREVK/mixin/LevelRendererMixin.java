package net.corevk.corevk.mixin;

import net.corevk.corevk.render.PackChain;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.framegraph.FramePass;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

	
	@WrapOperation(method = "addAlwaysOnTopPass",
			at = @At(value = "INVOKE",
					target = "Lcom/mojang/blaze3d/framegraph/FramePass;executes(Ljava/lang/Runnable;)V"),
			require = 1)
	private void corevk$scene(FramePass pass, Runnable body, Operation<Void> original) {
		Runnable kept = () -> {
			PackChain.markSceneDepth();
			body.run();
		};

		original.call(pass, kept);
	}
}
