package net.corevk.corevk.mixin;

import net.corevk.corevk.screen.CoreVKDebugEntry;

import net.minecraft.client.gui.components.debug.DebugScreenEntryList;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(DebugScreenEntryList.class)
public abstract class DebugScreenEntryListMixin {

	@Shadow
	@Final
	private Map<Identifier, DebugScreenEntryStatus> allStatuses;

	@Inject(method = "rebuildCurrentList", at = @At("HEAD"), require = 0)
	private void corevk$showByDefault(CallbackInfo ci) {
		this.allStatuses.putIfAbsent(CoreVKDebugEntry.ID, DebugScreenEntryStatus.IN_OVERLAY);
	}
}
