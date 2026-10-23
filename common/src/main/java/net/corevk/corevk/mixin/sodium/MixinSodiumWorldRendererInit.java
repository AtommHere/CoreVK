package net.corevk.corevk.mixin.sodium;

import net.corevk.corevk.HostReport;
import net.corevk.corevk.render.EntityMesh;
import net.corevk.corevk.sodium.TerrainMesh;

import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SodiumWorldRenderer.class, remap = false)
public abstract class MixinSodiumWorldRendererInit {

	@Inject(method = "initRenderer", at = @At("HEAD"), require = 1)
	private void corevk$settle(CallbackInfo callback) {

		if (HostReport.otherBackend()) {
			return;
		}

		TerrainMesh.settle();
		EntityMesh.settle();
	}
}
