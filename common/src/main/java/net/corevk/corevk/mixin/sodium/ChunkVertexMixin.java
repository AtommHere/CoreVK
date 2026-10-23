package net.corevk.corevk.mixin.sodium;

import net.corevk.corevk.sodium.TerrainVertex;

import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ChunkVertexEncoder.Vertex.class, remap = false)
public abstract class ChunkVertexMixin implements TerrainVertex {

	@Unique
	private int corevk$blockId;

	@Unique
	private int corevk$blockOrigin;

	@Override
	public int corevkBlockId() {
		return this.corevk$blockId;
	}

	@Override
	public void corevkBlockId(int id) {
		this.corevk$blockId = id;
	}

	@Override
	public int corevkBlockOrigin() {
		return this.corevk$blockOrigin;
	}

	@Override
	public void corevkBlockOrigin(int packed) {
		this.corevk$blockOrigin = packed;
	}

	@Inject(method = "copyVertexTo", at = @At("TAIL"), require = 1)
	private static void corevk$copyId(ChunkVertexEncoder.Vertex from, ChunkVertexEncoder.Vertex to,
			CallbackInfo callback) {
		((TerrainVertex) to).corevkBlockId(((TerrainVertex) from).corevkBlockId());
		((TerrainVertex) to).corevkBlockOrigin(((TerrainVertex) from).corevkBlockOrigin());
	}
}
