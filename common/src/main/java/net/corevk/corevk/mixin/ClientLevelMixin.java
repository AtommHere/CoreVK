package net.corevk.corevk.mixin;

import net.corevk.corevk.render.WeatherDraw;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.server.level.ParticleStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {

	
	@ModifyVariable(method = "tickWeatherEffects", at = @At("STORE"), require = 1)
	private ParticleStatus corevk$splashes(ParticleStatus status) {
		return WeatherDraw.splashes() ? status : ParticleStatus.MINIMAL;
	}
}
