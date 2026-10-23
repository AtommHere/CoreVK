package net.corevk.corevk.sodium;

import net.corevk.corevk.CoreVK;
import net.corevk.corevk.pack.program.TerrainPass;
import net.corevk.corevk.render.GeometryHold;
import net.corevk.corevk.render.TerrainDraw;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.textures.GpuSampler;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.world.phys.Vec3;

public final class SodiumApi {

	private static boolean refusalSaid;

	private SodiumApi() {
	}

	public static void drawShadowLayer(SodiumWorldRenderer renderer, ChunkSectionLayerGroup group,
			ChunkRenderMatrices matrices, Vec3 camera, GpuSampler sampler) {
		RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
		TerrainPass first = group == ChunkSectionLayerGroup.TRANSLUCENT
				? TerrainPass.TRANSLUCENT
				: TerrainPass.SOLID;
		RenderPassDescriptor descriptor = TerrainDraw.descriptor(first, main.getColorTextureView(),
				main.getDepthTextureView());
		if (descriptor == null) {
			if (!refusalSaid) {
				refusalSaid = true;
				CoreVK.logger().warn("CoreVK drew no {} terrain into the shadow map because the pack "
						+ "names no pass for it, and on Minecraft 26.3 the only other pass is the game's "
						+ "own picture", group);
			}

			return;
		}

		renderer.prepareChunkRendering(matrices, camera.x, camera.y, camera.z);
		try (RenderPass pass = GeometryHold.open(RenderSystem.getDevice().createCommandEncoder(),
				descriptor)) {
			renderer.drawChunkLayer(pass, group, matrices, camera.x, camera.y, camera.z, sampler,
					null);
		}
	}
}
