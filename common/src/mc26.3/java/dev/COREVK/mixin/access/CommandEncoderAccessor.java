package net.corevk.corevk.mixin.access;

import com.mojang.renderpearl.backend.api.CommandEncoderBackend;
import com.mojang.renderpearl.frontend.FrontendCommandEncoder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(FrontendCommandEncoder.class)
public interface CommandEncoderAccessor {

	@Invoker("backend")
	CommandEncoderBackend corevk$backend();
}
