package net.corevk.corevk.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.corevk.corevk.render.BufferBlending;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;

@Mixin(RenderPipeline.Builder.class)
public abstract class RenderPipelineBuilderMixin {

	
	@WrapOperation(method = "build()Lcom/mojang/blaze3d/pipeline/RenderPipeline;", require = 1,
			at = @At(value = "INVOKE", target = "Ljava/util/Optional;equals(Ljava/lang/Object;)Z"))
	private boolean corevk$blendApart(Optional<BlendFunction> current, Object last,
			Operation<Boolean> original) {
		return original.call(current, last) || BufferBlending.parting();
	}
}
