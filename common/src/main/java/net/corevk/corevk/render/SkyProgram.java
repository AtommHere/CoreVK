package net.corevk.corevk.render;

import net.corevk.corevk.glsl.PackProgram;
import net.corevk.corevk.glsl.SkyVertex;
import net.corevk.corevk.pack.target.ChainPlan;
import net.corevk.corevk.pack.target.TargetPlan;
import net.corevk.corevk.CoreVK;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;

import org.joml.Matrix4fc;
import org.joml.Vector4fc;

import java.util.List;
import java.util.Set;

final class SkyProgram extends FamilyProgram {

	private static final String FAMILY = "sky";

	private static final String NAMESPACE = CoreVK.MOD_ID;

	private SkyProgram(GeometryProgram body) {
		super(body);
	}

	static SkyProgram of(PackProgram.Loaded loaded, SkyDraw.Element element, PackValues values,
			int load, List<ChainPlan.Attachment> writes, TargetPlan chainTargets,
			ColorTargets targets, boolean chainRuns) {

		String servedBy = loaded.path().substring(loaded.path().lastIndexOf('/') + 1);
		PackProgram.Loaded bound =
				loaded.rebind(chainTargets, chainTargets.schedule().step(servedBy));

		return new SkyProgram(new GeometryProgram(new GeometryProgram.Pass(FAMILY, element.element(),
				NAMESPACE, Set.copyOf(SkyVertex.ATTRIBUTES), false, element.blend(),

				element.covers(), true, false, element.topology(),

				true, null, element.stage(),

				null,

				null,

				false),
				bound, values, load, element.format(), writes, targets, chainRuns));
	}

	RenderPipeline prepare(GpuDevice device, Matrix4fc modelView, Vector4fc colour) {
		return this.body.prepare(device, null, modelView, null, colour, null);
	}

	void texture(GpuTextureView view, GpuSampler sampler) {
		this.body.atlas(view);
		this.body.sampler(sampler);
	}

}
