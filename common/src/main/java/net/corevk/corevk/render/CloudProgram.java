package net.corevk.corevk.render;

import net.corevk.corevk.glsl.PackProgram;
import net.corevk.corevk.pack.model.RenderStage;
import net.corevk.corevk.pack.target.ChainPlan;
import net.corevk.corevk.pack.target.TargetPlan;
import net.corevk.corevk.CoreVK;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.GpuDevice;
import net.minecraft.client.renderer.BindGroupLayouts;

import java.util.List;
import java.util.Optional;
import java.util.Set;

final class CloudProgram extends FamilyProgram {

	
	private static final String FAMILY = "cloud";

	
	private static final String NAMESPACE = CoreVK.MOD_ID;

	private CloudProgram(GeometryProgram body) {
		super(body);
	}

	
	static CloudProgram of(PackProgram.Loaded loaded, boolean fancy, PackValues values, int load,
			List<ChainPlan.Attachment> writes, TargetPlan chainTargets, ColorTargets targets,
			boolean chainRuns) {
		// Bound again against the chain's own plan, for the reason the terrain and the sky are: what
		// the load bound them against is a plan without the user's pass filter.
		//
		// On the step AFTER the deferreds, which is where the clouds part company with the sky and
		// join the translucent chunk pass. The game draws them once the main pass is over, so the
		// halves they read are the ones the deferred stage turned over. Read on the near side, a
		// cloud program sampling a target its own pack's deferred writes gets the frame before's.
		String servedBy = loaded.path().substring(loaded.path().lastIndexOf('/') + 1);
		PackProgram.Loaded bound =
				loaded.rebind(chainTargets, chainTargets.schedule().stepAfterDeferred(servedBy));

		return new CloudProgram(new GeometryProgram(new GeometryProgram.Pass(FAMILY,
				fancy ? "fancy" : "flat", NAMESPACE,
				// The cloud buffer carries none of the names a pack declares for itself, so every one
				// of them really is a constant here and the log is right to say so.
				Set.of(), false,
				// What the game's own two cloud pipelines blend with, both of them.
				Optional.of(BlendFunction.TRANSLUCENT),
				// No coverage mask. The clouds are drawn after the main pass and therefore after the
				// scene seed has run, so there is nothing left for a mask to keep off them, and
				// nothing to claim their pixels for them either.
				false, false, true, PrimitiveTopology.QUADS,
				// The game's answer and not a taste: RenderPipelines.CLOUDS takes the builder's
				// default, which is to cull, and FLAT_CLOUDS names withCull(false). Culling the flat
				// cloud would leave the sky empty from underneath, which is where it is looked at.
				fancy,
				// The game's own state, which tests and writes under the reversed Z the scene is
				// drawn in. Unlike the sky, which declares none: a cloud stands in the world and has
				// to be hidden by whatever is in front of it.
				DepthStencilState.DEFAULT, RenderStage.CLOUDS, BindGroupLayouts.CLOUD_INFO,
				// One block, written once: every cloud face of a pass shares every value it reads.
				null,
				// Drawn in the game's own volume, so the dh matrices answer the game's.
				false),
				bound, values, load,
				// No format, which this family is alone in and GeometryProgram takes as "bind no
				// vertex buffer". Handing DefaultVertexFormat.POSITION here instead would declare an
				// input the pass never sets and read whatever the last draw left bound.
				null, writes, targets, chainRuns));
	}

	
	RenderPipeline prepare(GpuDevice device) {
		return this.body.prepare(device, null);
	}

}
