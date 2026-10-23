package net.corevk.corevk.mixin.game;

import com.mojang.blaze3d.vulkan.glsl.IntermediaryShaderModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(IntermediaryShaderModule.class)
@SuppressWarnings("rawtypes")
public interface IntermediaryShaderModuleAccessor {

	@Accessor("samplers")
	@SuppressWarnings("rawtypes")
	List corevk$samplers();

	@Accessor("uniformBuffers")
	@SuppressWarnings("rawtypes")
	List corevk$uniformBuffers();

	@Accessor("outputs")
	@SuppressWarnings("rawtypes")
	List corevk$outputs();

	@Accessor("inputs")
	@SuppressWarnings("rawtypes")
	List corevk$inputs();
}
