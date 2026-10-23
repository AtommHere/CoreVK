package net.corevk.corevk.mixin.access;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.world.level.biome.Biome$ClimateSettings")
public interface BiomeClimateAccessor {

	@Accessor("downfall")
	float corevk$downfall();
}
