package net.corevk.corevk.mixin;

import net.corevk.corevk.render.BlockEntityGeometry;
import net.corevk.corevk.render.BlockEntityOrigin;
import net.corevk.corevk.render.EntityDraw;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(RenderTypeFeatureRenderer.class)
public abstract class RenderTypeFeatureRendererMixin {

	
	@WrapOperation(method = "executeGroup", require = 1,
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/StagedVertexBuffer;"
					+ "getExecuteInfo(Lnet/minecraft/client/renderer/StagedVertexBuffer$Draw;)"
					+ "Lnet/minecraft/client/renderer/StagedVertexBuffer$ExecuteInfo;"))
	private StagedVertexBuffer.ExecuteInfo corevk$origin(StagedVertexBuffer buffer,
			StagedVertexBuffer.Draw draw, Operation<StagedVertexBuffer.ExecuteInfo> original) {
		BlockEntityGeometry.drawing(((BlockEntityOrigin) draw).corevk$fromBlockEntity());

		return original.call(buffer, draw);
	}

	@WrapOperation(method = "executeGroup", require = 1,
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/rendertype/PreparedRenderType;"
							+ "drawFromBuffer("
							+ "Lnet/minecraft/client/renderer/StagedVertexBuffer$ExecuteInfo;"
							+ ")V"))
	private void corevk$draw(PreparedRenderType renderType, StagedVertexBuffer.ExecuteInfo info,
			Operation<Void> original) {
		if (!EntityDraw.draw(renderType, info)) {
			original.call(renderType, info);
		}
	}

	
	@Inject(method = "executeGroup", at = @At("RETURN"), require = 1)
	private void corevk$close(FeatureFrameContext context, int groupIndex, List<?> submits,
			boolean strictlyOrdered, CallbackInfo callback) {
		EntityDraw.endGroup();
	}
}
