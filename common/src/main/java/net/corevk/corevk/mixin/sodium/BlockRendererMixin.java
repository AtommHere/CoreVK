package net.corevk.corevk.mixin.sodium;

import net.corevk.corevk.sodium.TerrainVertex;
import net.corevk.corevk.render.BlockStateIds;

import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import net.caffeinemc.mods.sodium.client.render.model.AbstractBlockRenderContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = BlockRenderer.class, remap = false)
public abstract class BlockRendererMixin extends AbstractBlockRenderContext {

	@ModifyArg(
			method = "bufferQuad",
			at = @At(value = "INVOKE",
					target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/vertex/builder/"
							+ "ChunkMeshBufferBuilder;push([Lnet/caffeinemc/mods/sodium/client/render/"
							+ "chunk/vertex/format/ChunkVertexEncoder$Vertex;I)V"),
			index = 0, require = 1)
	private ChunkVertexEncoder.Vertex[] corevk$id(ChunkVertexEncoder.Vertex[] vertices) {
		return corevk$stamp(vertices);
	}

	@ModifyArg(
			method = "bufferQuad",
			at = @At(value = "INVOKE",
					target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/translucent_sorting/"
							+ "TranslucentGeometryCollector;appendQuad([Lnet/caffeinemc/mods/sodium/"
							+ "client/render/chunk/vertex/format/ChunkVertexEncoder$Vertex;"
							+ "Lnet/caffeinemc/mods/sodium/client/model/quad/properties/"
							+ "ModelQuadFacing;I)Z"),
			index = 0, require = 1)
	private ChunkVertexEncoder.Vertex[] corevk$sortedId(ChunkVertexEncoder.Vertex[] vertices) {
		return corevk$stamp(vertices);
	}

	@Unique
	private BlockState corevk$stampedState;

	@Unique
	private long corevk$stampedAt = Long.MIN_VALUE;

	@Unique
	private int corevk$stampedId = BlockStateIds.NONE;

	@Unique
	private int corevk$stampedOrigin;

	@Unique
	private ChunkVertexEncoder.Vertex[] corevk$stamp(ChunkVertexEncoder.Vertex[] vertices) {

		BlockState state = this.state;
		long at = this.pos == null ? Long.MIN_VALUE : this.pos.asLong();
		if (state != this.corevk$stampedState || at != this.corevk$stampedAt) {
			this.corevk$stampedState = state;
			this.corevk$stampedAt = at;
			this.corevk$stampedId = state == null ? BlockStateIds.NONE : BlockStateIds.packed(state);
			this.corevk$stampedOrigin = this.pos == null
					? 0
					: TerrainVertex.pack(this.pos.getX(), this.pos.getY(), this.pos.getZ(),
							state == null ? 0 : state.getLightEmission());
		}

		TerrainVertex.stampOrigin(vertices, this.corevk$stampedOrigin);

		return TerrainVertex.stamp(vertices, this.corevk$stampedId);
	}
}
