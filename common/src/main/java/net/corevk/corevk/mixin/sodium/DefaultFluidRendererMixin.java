package net.corevk.corevk.mixin.sodium;

import net.corevk.corevk.sodium.TerrainVertex;
import net.corevk.corevk.render.BlockStateIds;
import net.corevk.corevk.render.FaceShading;
import net.corevk.corevk.render.TerrainDraw;
import net.corevk.corevk.CoreVK;

import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.DefaultFluidRenderer;
import net.caffeinemc.mods.sodium.client.model.color.ColorProvider;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.buffers.ChunkModelBuilder;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.Material;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.TranslucentGeometryCollector;
import net.caffeinemc.mods.sodium.client.world.LevelSlice;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.core.BlockPos;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DefaultFluidRenderer.class, remap = false)
public abstract class DefaultFluidRendererMixin {

	
	@Unique
	private static final java.util.concurrent.atomic.AtomicInteger corevk$said =
			new java.util.concurrent.atomic.AtomicInteger(-1);

	@Unique
	private int corevk$id = BlockStateIds.NONE;

	
	@Unique
	private int corevk$origin;

	
	@Inject(method = "render", at = @At("HEAD"), require = 1)
	private void corevk$take(LevelSlice level, BlockState blockState, FluidState fluidState,
			BlockPos pos, BlockPos origin, TranslucentGeometryCollector collector,
			ChunkModelBuilder builder, Material material, ColorProvider<FluidState> colours,
			FluidModel model, CallbackInfo callback) {
		BlockState fluidBlock = fluidState == null ? null : fluidState.createLegacyBlock();
		this.corevk$id = BlockStateIds.packedFluid(fluidBlock);

		// The fluid's own light and not that of the block sharing its position, for the reason the
		// class comment gives about the id: a waterlogged stair is two things to draw, and these
		// quads are the water's.
		this.corevk$origin = pos == null
				? 0
				: TerrainVertex.pack(pos.getX(), pos.getY(), pos.getZ(),
						fluidBlock == null ? 0 : fluidBlock.getLightEmission());

		// Said once for the first fluid meshed under each table, because every link after it is
		// invisible: a number that never leaves this method looks exactly like a number that reached
		// the shader and was ignored, and the two are a mixin apart.
		//
		// And not said at all when the mesh was never extended, which is exactly the case this line
		// exists to tell apart: the id below is computed and then goes nowhere, and printing it
		// beside a startup line saying the mesh carries no block id would be the engine
		// contradicting itself inside one log.
		// A plain read first: the exchange is a locked instruction on one word every builder
		// thread shares, and it only has anything to do once per table.
		int table = BlockStateIds.generation();
		if (TerrainDraw.asked() && corevk$said.get() != table
				&& corevk$said.getAndSet(table) != table) {
			// The table is named, because this line is not the only one of the run: two loads
			// of the same pack print it twice, word for word, and nothing else would say which
			// reading belongs to which table.
			CoreVK.logger().info("The first fluid meshed against block table {} is {} and it "
					+ "carries the packed id {}", table,
					fluidBlock == null ? "nothing" : fluidBlock, this.corevk$id);
		}
	}

	
	@ModifyArg(
			method = "render",
			at = @At(value = "INVOKE",
					target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/pipeline/"
							+ "DefaultFluidRenderer;updateQuad(Lnet/caffeinemc/mods/sodium/client/"
							+ "model/quad/ModelQuadViewMutable;Lnet/caffeinemc/mods/sodium/client/"
							+ "world/LevelSlice;Lnet/minecraft/core/BlockPos;Lnet/caffeinemc/mods/"
							+ "sodium/client/model/light/LightPipeline;Lnet/minecraft/core/Direction;"
							+ "Lnet/caffeinemc/mods/sodium/client/model/quad/properties/"
							+ "ModelQuadFacing;FLnet/caffeinemc/mods/sodium/client/model/color/"
							+ "ColorProvider;Lnet/minecraft/world/level/material/FluidState;)V",
					ordinal = 2),
			require = 1)
	private float corevk$sideFaceAtFullBrightness(float brightness) {
		return FaceShading.dropped() ? 1.0F : brightness;
	}

	
	@ModifyArg(
			method = "writeQuad",
			at = @At(value = "INVOKE",
					target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/vertex/builder/"
							+ "ChunkMeshBufferBuilder;push([Lnet/caffeinemc/mods/sodium/client/render/"
							+ "chunk/vertex/format/ChunkVertexEncoder$Vertex;Lnet/caffeinemc/mods/"
							+ "sodium/client/render/chunk/terrain/material/Material;)V"),
			index = 0, require = 1)
	private ChunkVertexEncoder.Vertex[] corevk$id(ChunkVertexEncoder.Vertex[] vertices) {
		return TerrainVertex.stamp(TerrainVertex.stampOrigin(vertices, this.corevk$origin), this.corevk$id);
	}

	
	@ModifyArg(
			method = "writeQuad",
			at = @At(value = "INVOKE",
					target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/translucent_sorting/"
							+ "TranslucentGeometryCollector;appendQuad([Lnet/caffeinemc/mods/sodium/"
							+ "client/render/chunk/vertex/format/ChunkVertexEncoder$Vertex;"
							+ "Lnet/caffeinemc/mods/sodium/client/model/quad/properties/"
							+ "ModelQuadFacing;I)Z"),
			index = 0, require = 1)
	private ChunkVertexEncoder.Vertex[] corevk$sortedId(ChunkVertexEncoder.Vertex[] vertices) {
		return TerrainVertex.stamp(TerrainVertex.stampOrigin(vertices, this.corevk$origin), this.corevk$id);
	}
}
