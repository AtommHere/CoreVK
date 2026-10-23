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

		String servedBy = loaded.path().substring(loaded.path().lastIndexOf('/') + 1);
		PackProgram.Loaded bound =
				loaded.rebind(chainTargets, chainTargets.schedule().stepAfterDeferred(servedBy));

		return new CloudProgram(new GeometryProgram(new GeometryProgram.Pass(FAMILY,
				fancy ? "fancy" : "flat", NAMESPACE,

				Set.of(), false,

				Optional.of(BlendFunction.TRANSLUCENT),

				false, false, true, PrimitiveTopology.QUADS,

				fancy,

				DepthStencilState.DEFAULT, RenderStage.CLOUDS, BindGroupLayouts.CLOUD_INFO,

				null,

				false),
				bound, values, load,

				null, writes, targets, chainRuns));
	}

	RenderPipeline prepare(GpuDevice device) {
		return this.body.prepare(device, null);
	}

}
