package net.corevk.corevk.mixin;

import net.corevk.corevk.render.EntityMesh;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Mixin(RenderPipeline.class)
public abstract class RenderPipelineMixin {

	@Unique
	private @Nullable List<@Nullable VertexFormat> corevk$carried;

	@Inject(method = "getVertexFormatBindings", at = @At("RETURN"), cancellable = true, require = 1)
	private void corevk$bindings(CallbackInfoReturnable<List<@Nullable VertexFormat>> callback) {
		if (!EntityMesh.carrying()) {
			return;
		}

		List<@Nullable VertexFormat> declared = callback.getReturnValue();
		if (declared == null) {
			return;
		}

		List<@Nullable VertexFormat> carried = this.corevk$carried;
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
	private static List<@Nullable VertexFormat> exchange(RenderPipeline pipeline,
			List<@Nullable VertexFormat> declared) {
		List<@Nullable VertexFormat> carried = new ArrayList<>(declared);
		boolean moved = false;
		for (int binding = 0; binding < carried.size(); binding++) {
			VertexFormat one = EntityMesh.binding(pipeline, carried.get(binding));
			moved = moved || one != carried.get(binding);
			carried.set(binding, one);
		}

		return moved ? Collections.unmodifiableList(carried) : declared;
	}
}
