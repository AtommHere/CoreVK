package net.corevk.corevk.mixin;

import net.corevk.corevk.render.StartupGuard;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Minecraft.class)
public abstract class MinecraftStartupGuardMixin {

	@WrapOperation(method = "<init>", at = @At(value = "FIELD",
			target = "Lnet/minecraft/client/Options;startedCleanly:Z", opcode = Opcodes.GETFIELD),
			require = 1)
	private boolean corevk$keepTheChosenBackend(Options options, Operation<Boolean> original) {
		return StartupGuard.startedCleanly(options, original.call(options));
	}
}
