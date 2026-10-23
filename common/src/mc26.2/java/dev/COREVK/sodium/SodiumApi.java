package net.corevk.corevk.sodium;

import com.mojang.blaze3d.textures.GpuSampler;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.world.phys.Vec3;

public final class SodiumApi {

	private SodiumApi() {
	}

	
	public static void drawShadowLayer(SodiumWorldRenderer renderer, ChunkSectionLayerGroup group,
			ChunkRenderMatrices matrices, Vec3 camera, GpuSampler sampler) {
		renderer.drawChunkLayer(group, matrices, camera.x, camera.y, camera.z, sampler);
	}
}
