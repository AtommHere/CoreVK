package net.corevk.corevk.mixin;

import net.corevk.corevk.render.BlockEntityGeometry;
import net.corevk.corevk.render.BlockEntityMark;
import net.corevk.corevk.render.BlockEntityOrigin;
import net.corevk.corevk.render.EntityDraw;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(targets = "net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer$Group")
public abstract class RenderTypeFeatureRendererGroupMixin {

	@Shadow
	@Final
	private List<StagedVertexBuffer.Draw> draws;

	@Shadow
	private StagedVertexBuffer.Draw lastDraw;

	@Inject(method = "getVertexBuilder", at = @At("HEAD"), require = 1)
	private void corevk$breakTheRun(RenderType renderType,
			CallbackInfoReturnable<VertexConsumer> callback) {
		if (this.lastDraw != null && EntityDraw.wanted()
				&& ((BlockEntityOrigin) this.lastDraw).corevk$fromBlockEntity()
						!= BlockEntityGeometry.building()) {
			this.lastDraw = null;
		}
	}

	@WrapOperation(method = "getOrAddDraw", require = 1,
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/StagedVertexBuffer;"
					+ "appendDraw(Lcom/mojang/blaze3d/vertex/VertexFormat;"
					+ "Lcom/mojang/blaze3d/PrimitiveTopology;"
					+ "Lcom/mojang/blaze3d/vertex/VertexSorting;)"
					+ "Lnet/minecraft/client/renderer/StagedVertexBuffer$Draw;"))
	private StagedVertexBuffer.Draw corevk$mark(StagedVertexBuffer buffer, VertexFormat format,
			PrimitiveTopology topology, VertexSorting sorting,
			Operation<StagedVertexBuffer.Draw> original) {
		StagedVertexBuffer.Draw draw = original.call(buffer, format, topology, sorting);
		((BlockEntityMark) draw).corevk$fromBlockEntity(BlockEntityGeometry.building());

		return draw;
	}

	@WrapOperation(method = "getOrAddDraw", require = 1,
			at = @At(value = "INVOKE", target = "Ljava/util/List;indexOf(Ljava/lang/Object;)I"))
	private int corevk$keepApart(List<?> types, Object prepared, Operation<Integer> original) {
		int found = original.call(types, prepared);
		if (found == -1 || !EntityDraw.wanted()) {
			return found;
		}

		return ((BlockEntityOrigin) this.draws.get(found)).corevk$fromBlockEntity()
				== BlockEntityGeometry.building() ? found : -1;
	}
}
