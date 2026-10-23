package net.corevk.corevk.sodium;

import net.corevk.corevk.pack.program.TerrainPass;

import net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;

public final class SodiumPasses {

	private SodiumPasses() {
	}

	@SuppressWarnings("ReferenceEquality")
	public static TerrainPass of(TerrainRenderPass pass) {
		if (pass == DefaultTerrainRenderPasses.SOLID) {
			return TerrainPass.SOLID;
		}

		if (pass == DefaultTerrainRenderPasses.CUTOUT) {
			return TerrainPass.CUTOUT;
		}

		return pass == DefaultTerrainRenderPasses.TRANSLUCENT ? TerrainPass.TRANSLUCENT : null;
	}
}
