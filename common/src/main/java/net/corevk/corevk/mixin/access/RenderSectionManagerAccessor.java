package net.corevk.corevk.mixin.access;

import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = RenderSectionManager.class, remap = false)
public interface RenderSectionManagerAccessor {

	@Accessor("needsRenderListUpdate")
	void corevk$setNeedsRenderListUpdate(boolean value);

	@Accessor("frame")
	int corevk$getFrame();

	@Accessor("frame")
	void corevk$setFrame(int frame);

	@Accessor("cameraChanged")
	boolean corevk$cameraChanged();

	@Invoker("invalidateRenderLists")
	void corevk$invalidateRenderLists();
}
