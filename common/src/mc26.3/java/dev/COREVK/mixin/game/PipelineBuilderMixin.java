package net.corevk.corevk.mixin.game;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import com.mojang.renderpearl.backend.api.SpvModule;
import com.mojang.renderpearl.frontend.shaders.PipelineBuilder;
import net.corevk.corevk.CoreVK;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import org.lwjgl.util.spvc.Spvc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mixin(PipelineBuilder.class)
public abstract class PipelineBuilderMixin {

	@WrapOperation(method = "generateBackendCreateInfo", require = 2,
			at = @At(value = "INVOKE",
					target = "Lcom/mojang/renderpearl/backend/api/SpvModule$Reflection$Descriptor;"
							+ "resourceType()I"))
	private int corevk$declaredKind(SpvModule.Reflection.Descriptor descriptor,
			Operation<Integer> original) {
		int kind = original.call(descriptor);
		if (kind == Spvc.SPVC_RESOURCE_TYPE_STORAGE_BUFFER) {
			return Spvc.SPVC_RESOURCE_TYPE_UNIFORM_BUFFER;
		}

		if (kind == Spvc.SPVC_RESOURCE_TYPE_STORAGE_IMAGE) {
			return Spvc.SPVC_RESOURCE_TYPE_SAMPLED_IMAGE;
		}

		return kind;
	}

	@WrapOperation(method = "generateBackendCreateInfo", require = 2,
			at = @At(value = "INVOKE",
					target = "Lcom/mojang/renderpearl/backend/api/SpvModule$Reflection$Type;"
							+ "dimensions()I"))
	private int corevk$allowLineAndVolume(SpvModule.Reflection.Type type, Operation<Integer> original) {
		int dimensions = original.call(type);
		return dimensions == 0 || dimensions == 2 ? 1 : dimensions;
	}

	@Unique
	private static final ThreadLocal<Map<String, Integer>> COREVK$WRITTEN = new ThreadLocal<>();

	@WrapOperation(method = "generateBackendCreateInfo", require = 2,
			at = @At(value = "INVOKE",
					target = "Lcom/mojang/renderpearl/frontend/shaders/PipelineBuilder;generateSlotMap("
							+ "Ljava/util/List;Ljava/lang/String;"
							+ "Lcom/mojang/renderpearl/api/pipeline/ShaderType;)"
							+ "Lit/unimi/dsi/fastutil/ints/Int2ObjectMap;"))
	private Int2ObjectMap<?> corevk$linkByName(List<SpvModule.Reflection.InterfaceVariable> variables,
			String stage, ShaderType type, Operation<Int2ObjectMap<?>> original) {
		if (!stage.startsWith(CoreVK.MOD_ID)) {
			return original.call(variables, stage, type);
		}

		if (type == ShaderType.VERTEX) {
			Map<String, Integer> written = new HashMap<>();
			for (SpvModule.Reflection.InterfaceVariable output : variables) {
				written.put(output.name(), output.location());
			}

			COREVK$WRITTEN.set(written);
		} else {
			Map<String, Integer> written = COREVK$WRITTEN.get();
			COREVK$WRITTEN.remove();
			if (written != null) {
				for (SpvModule.Reflection.InterfaceVariable input : variables) {
					Integer location = written.get(input.name());
					if (location != null) {
						input.location(location);
					}
				}
			}
		}

		return original.call(variables, stage, type);
	}
}
