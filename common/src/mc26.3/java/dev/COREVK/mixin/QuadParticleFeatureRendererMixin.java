package net.corevk.corevk.mixin;

import net.corevk.corevk.render.GeometryHold;
import net.corevk.corevk.render.LevelPass;
import net.corevk.corevk.render.ParticleDraw;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.QuadParticleFeatureRenderer;
import net.minecraft.client.renderer.oit.OitStage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

@Mixin(QuadParticleFeatureRenderer.class)
public abstract class QuadParticleFeatureRendererMixin {

	@WrapMethod(method = "executeGroup", require = 1)
	private void corevk$group(FeatureFrameContext context, @Nullable OitStage stage,
			RenderPass pass, int groupIndex, List<QuadParticleFeatureRenderer.Submit> submits,
			boolean strictlyOrdered, Operation<Void> original) {
		RenderPass drawn = pass;
		try {
			if (stage == null) {
				drawn = corevk$open(pass, submits);
			}

			original.call(context, stage, drawn, groupIndex, submits, strictlyOrdered);
		} finally {
			try {
				if (drawn != pass) {
					drawn.close();
				}
			} finally {
				ParticleDraw.endGroup();
			}
		}
	}

	@Unique
	private static RenderPass corevk$open(RenderPass pass,
			List<QuadParticleFeatureRenderer.Submit> submits) {
		if (!(pass instanceof LevelPass level) || submits.isEmpty()) {
			return pass;
		}

		boolean translucent = submits.getFirst().translucent();
		RenderPipeline pipeline = ParticleDraw.group(translucent, level.colour(), level.depth());
		if (pipeline == null) {
			return pass;
		}

		RenderPassDescriptor descriptor = ParticleDraw.descriptor();
		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		String half = translucent ? "Translucent" : "Solid";
		RenderPass drawn = descriptor == null
				? encoder.createRenderPass(() -> "Particles - " + half, level.colour(),
						Optional.empty(), level.depth(), OptionalDouble.empty())
				: GeometryHold.open(encoder, descriptor);
		ParticleDraw.opened(drawn);

		return drawn;
	}
}
