package net.corevk.corevk.mixin.sodium;

import net.corevk.corevk.sodium.TerrainMesh;

import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkMeshFormats;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ChunkMeshFormats.class, remap = false)
public abstract class ChunkMeshFormatsMixin {

	@Inject(method = "getCurrent", at = @At("HEAD"), cancellable = true)
	private static void corevk$format(CallbackInfoReturnable<ChunkVertexType> callback) {
		ChunkVertexType ours = TerrainMesh.current();
		if (ours != null) {
			callback.setReturnValue(ours);
		}
	}
}
