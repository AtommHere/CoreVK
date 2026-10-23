package net.corevk.corevk.mixin.game;

import net.corevk.corevk.render.SamplerReach;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.backend.api.SpvModule;
import com.mojang.renderpearl.frontend.shaders.SPIRVModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.nio.IntBuffer;
import java.util.List;
import java.util.Set;

@Mixin(SPIRVModule.class)
public abstract class SPIRVModuleMixin implements SamplerReach.Narrowable {

	@Unique
	private Set<String> corevk$unreached = Set.of();

	@Override
	public void corevk$unreached(Set<String> unreached) {
		this.corevk$unreached = unreached;
	}

	@WrapOperation(method = "doReflection", require = 1,
			at = @At(value = "INVOKE",
					target = "Lcom/mojang/renderpearl/frontend/shaders/SPIRVModule;"
							+ "generateDescriptorList(JJLjava/nio/IntBuffer;I)Ljava/util/List;"))
	private List<SpvModule.Reflection.Descriptor> corevk$reached(long compiler, long resources,
			IntBuffer spirv, int type, Operation<List<SpvModule.Reflection.Descriptor>> original) {
		return SamplerReach.keep(original.call(compiler, resources, spirv, type), type,
				this.corevk$unreached);
	}
}
