package net.corevk.corevk.mixin.sodium;

import net.corevk.corevk.render.TerrainDraw;

import net.caffeinemc.mods.sodium.client.gpu.device.batch.MultiDrawBatch;
import net.caffeinemc.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.IdentityHashMap;
import java.util.Map;

@Mixin(value = RenderRegion.class, remap = false)
public abstract class MixinRenderRegion {

	@Unique
	private final Map<TerrainRenderPass, MultiDrawBatch> corevk$shadowBatches =
			new IdentityHashMap<>();

	@Inject(method = "getCachedBatch", at = @At("HEAD"), cancellable = true)
	private void corevk$shadowBatch(TerrainRenderPass pass,
			CallbackInfoReturnable<MultiDrawBatch> cir) {
		if (TerrainDraw.drawingShadow()) {
			cir.setReturnValue(this.corevk$shadowBatches.computeIfAbsent(pass,
					_ -> MultiDrawBatch.newBatch(ModelQuadFacing.COUNT * RenderRegion.REGION_SIZE + 1)));
		}
	}

	@Inject(method = "clearAllCachedBatches", at = @At("HEAD"))
	private void corevk$clearAll(CallbackInfo ci) {
		for (MultiDrawBatch batch : this.corevk$shadowBatches.values()) {
			batch.clear();
		}
	}

	@Inject(method = "clearCachedBatchFor", at = @At("HEAD"))
	private void corevk$clearFor(TerrainRenderPass pass, CallbackInfo ci) {
		MultiDrawBatch batch = this.corevk$shadowBatches.get(pass);
		if (batch != null) {
			batch.clear();
		}
	}

	@Inject(method = "delete", at = @At("HEAD"))
	private void corevk$delete(CallbackInfo ci) {
		for (MultiDrawBatch batch : this.corevk$shadowBatches.values()) {
			batch.delete();
		}

		this.corevk$shadowBatches.clear();
	}
}
