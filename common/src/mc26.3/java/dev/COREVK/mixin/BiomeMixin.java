package net.corevk.corevk.mixin;

import net.corevk.corevk.mixin.access.BiomeClimateAccessor;
import net.corevk.corevk.render.BiomeHumidity;

import net.minecraft.world.attribute.EnvironmentAttributeMap;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Biome.class)
public abstract class BiomeMixin implements BiomeHumidity {

	@Unique
	private float corevk$downfall;

	@Inject(method = "<init>", at = @At("RETURN"), require = 1)
	private void corevk$readDownfall(@Coerce Object climate, EnvironmentAttributeMap attributes,
			BiomeSpecialEffects effects, BiomeGenerationSettings generation,
			CallbackInfo callback) {
		this.corevk$downfall = ((BiomeClimateAccessor) climate).corevk$downfall();
	}

	@Override
	public float corevk$downfall() {
		return this.corevk$downfall;
	}
}
