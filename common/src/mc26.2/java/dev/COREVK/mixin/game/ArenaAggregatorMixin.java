package net.corevk.corevk.mixin.game;

import net.caffeinemc.mods.sodium.client.gpu.arena.ArenaAggregator;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkMeshFormats;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(value = ArenaAggregator.class, remap = false)
public abstract class ArenaAggregatorMixin {

	@Redirect(
			method = "<init>",
			at = @At(
					value = "FIELD",
					opcode = Opcodes.GETSTATIC,
					target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/vertex/format/ChunkMeshFormats;COMPACT:Lnet/caffeinemc/mods/sodium/client/render/chunk/vertex/format/ChunkVertexType;"),
			require = 1)
	private static ChunkVertexType corevk$geometryFormat() {
		return ChunkMeshFormats.getCurrent();
	}
}