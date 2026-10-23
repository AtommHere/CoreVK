package net.corevk.corevk.mixin;

import net.corevk.corevk.render.EntityMesh;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import org.jspecify.annotations.Nullable;

@Mixin(RenderPipeline.class)
public abstract class RenderPipelineMixin {

	
	@Unique
	private VertexFormat @Nullable [] corevk$carried;

	@Inject(method = "getVertexFormatBindings", at = @At("RETURN"), cancellable = true, require = 1)
	private void corevk$bindings(CallbackInfoReturnable<VertexFormat[]> callback) {
		if (!EntityMesh.carrying()) {
			return;
		}

		VertexFormat[] declared = callback.getReturnValue();
		if (declared == null) {
			return;
		}

		VertexFormat[] carried = this.corevk$carried;
		if (carried == null) {
			carried = exchange((RenderPipeline) (Object) this, declared);
			this.corevk$carried = carried;
		}

		callback.setReturnValue(carried);
	}

	@Inject(method = "getVertexFormatBinding", at = @At("RETURN"), cancellable = true, require = 1)
	private void corevk$binding(int bindingIndex, CallbackInfoReturnable<VertexFormat> callback) {
		callback.setReturnValue(EntityMesh.binding((RenderPipeline) (Object) this,
				callback.getReturnValue()));
	}

	@Unique
	private static VertexFormat[] exchange(RenderPipeline pipeline, VertexFormat[] declared) {
		VertexFormat[] carried = declared.clone();
		boolean moved = false;
		for (int binding = 0; binding < carried.length; binding++) {
			VertexFormat one = EntityMesh.binding(pipeline, carried[binding]);
			moved = moved || one != carried[binding];
			carried[binding] = one;
		}

		return moved ? carried : declared;
	}
}
