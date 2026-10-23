package net.corevk.corevk.mixin;

import net.corevk.corevk.render.CloudDraw;
import net.corevk.corevk.render.GeometryHold;
import net.corevk.corevk.render.GraphicsApi;
import net.corevk.corevk.render.LevelPass;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.renderer.CloudRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import org.jspecify.annotations.Nullable;

@Mixin(CloudRenderer.class)
public abstract class CloudRendererMixin {

	
	@Unique
	private @Nullable RenderPipeline corevk$pipeline;

	
	@Unique
	private @Nullable RenderPass corevk$opened;

	
	@ModifyVariable(method = "render(Lnet/minecraft/client/CloudStatus;"
			+ "Lcom/mojang/renderpearl/api/commands/RenderPass;)V",
			require = 1, argsOnly = true, at = @At(value = "LOAD", ordinal = 0))
	private RenderPass corevk$open(RenderPass pass, CloudStatus cloudStatus) {
		this.corevk$pipeline = null;
		this.corevk$opened = null;
		if (!(pass instanceof LevelPass level)) {
			return pass;
		}

		this.corevk$pipeline = CloudDraw.pipeline(cloudStatus == CloudStatus.FANCY);
		RenderPassDescriptor descriptor = this.corevk$pipeline == null
				? null
				: CloudDraw.descriptor(level.colour(), level.depth());
		if (descriptor == null) {
			return pass;
		}

		this.corevk$opened = GeometryHold.open(RenderSystem.getDevice().createCommandEncoder(),
				descriptor);

		return this.corevk$opened;
	}

	
	@Inject(method = "render(Lnet/minecraft/client/CloudStatus;"
			+ "Lcom/mojang/renderpearl/api/commands/RenderPass;)V",
			require = 1, at = @At("RETURN"))
	private void corevk$close(CallbackInfo callback) {
		RenderPass opened = this.corevk$opened;
		this.corevk$opened = null;
		this.corevk$pipeline = null;
		if (opened != null) {
			opened.close();
		}
	}

	@WrapOperation(method = "render(Lcom/mojang/renderpearl/api/commands/RenderPass;"
			+ "Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;)V",
			require = 1, at = @At(value = "INVOKE",
					target = "Lcom/mojang/renderpearl/api/commands/RenderPass;setPipeline("
							+ "Lcom/mojang/renderpearl/api/pipeline/CompiledRenderPipeline;)V"))
	private void corevk$pipeline(RenderPass pass, CompiledRenderPipeline compiled,
			Operation<Void> original) {
		RenderPipeline ours = this.corevk$pipeline;
		CompiledRenderPipeline chosen = ours == null ? null : GraphicsApi.compiledFor(ours);
		original.call(pass, chosen == null ? compiled : chosen);
	}

	
	@WrapOperation(method = "render(Lcom/mojang/renderpearl/api/commands/RenderPass;"
			+ "Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;)V",
			require = 1, at = @At(value = "INVOKE",
					target = "Lcom/mojang/renderpearl/api/commands/RenderPass;drawIndexed(IIIII)V"))
	private void corevk$draw(RenderPass pass, int indexCount, int instanceCount, int firstIndex,
			int vertexOffset, int firstInstance, Operation<Void> original) {
		RenderPipeline ours = this.corevk$pipeline;
		if (ours != null) {
			CloudDraw.bind(pass, ours);
		}

		original.call(pass, indexCount, instanceCount, firstIndex, vertexOffset, firstInstance);
	}
}
