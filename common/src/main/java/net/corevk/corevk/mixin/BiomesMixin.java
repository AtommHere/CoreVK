package net.corevk.corevk.mixin;

import net.corevk.corevk.render.BiomeClassifier;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Biomes.class)
public abstract class BiomesMixin {

	// require = 1, written out where this config now defaults to it: a silently missed injection
	// here would number every biome nought and write no BIOME_* define at all, which a pack
	// swallows without a word.
	@Inject(method = "register", at = @At("TAIL"), require = 1)
	private static void corevk$record(String name, CallbackInfoReturnable<ResourceKey<Biome>> cir) {
		BiomeClassifier.record(cir.getReturnValue());
	}
}
