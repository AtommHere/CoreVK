package net.corevk.corevk.mixin;

import net.corevk.corevk.screen.CoreVKDebugEntry;

import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DebugScreenEntries.class)
public abstract class DebugScreenEntriesMixin {

	@Shadow
	private static Identifier register(Identifier identifier, DebugScreenEntry entry) {
		throw new AssertionError();
	}

	@Inject(method = "<clinit>", at = @At("RETURN"), require = 0)
	private static void corevk$register(CallbackInfo ci) {
		register(CoreVKDebugEntry.ID, new CoreVKDebugEntry());
	}
}
