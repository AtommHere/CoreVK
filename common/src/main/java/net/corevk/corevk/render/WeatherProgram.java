package net.corevk.corevk.render;

import net.corevk.corevk.glsl.PackProgram;
import net.corevk.corevk.pack.target.ChainPlan;
import net.corevk.corevk.pack.target.TargetPlan;
import net.corevk.corevk.CoreVK;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;

import java.util.List;
import java.util.Set;

final class WeatherProgram extends FamilyProgram {

	private static final String FAMILY = "weather";

	private static final String NAMESPACE = CoreVK.MOD_ID;

	private static final Set<String> ANSWERED = Set.of();

	private WeatherProgram(GeometryProgram body) {
		super(body);
	}

	static WeatherProgram of(PackProgram.Loaded loaded, WeatherDraw.Element element, PackValues values,
			int load, List<ChainPlan.Attachment> writes, TargetPlan chainTargets,
			ColorTargets targets, boolean chainRuns) {

		String servedBy = loaded.path().substring(loaded.path().lastIndexOf('/') + 1);
		PackProgram.Loaded bound =
				loaded.rebind(chainTargets, chainTargets.schedule().stepAfterDeferred(servedBy));

		RenderPipeline game = element.pipeline();

		return new WeatherProgram(new GeometryProgram(new GeometryProgram.Pass(FAMILY,
				element.element(), NAMESPACE, ANSWERED, false,
				GraphicsApi.colorTarget(game).blendFunction(),

				false, false, true, game.getPrimitiveTopology(), game.isCull(),
				game.getDepthStencilState(), element.stage(),

				null,

				null,

				false),
				bound, values, load, DefaultVertexFormat.PARTICLE, writes, targets, chainRuns));
	}

	RenderPipeline prepare(GpuDevice device) {

		return this.body.prepare(device, null, null, null, null, null);
	}

	void texture(GpuTextureView view, GpuSampler sampler) {
		this.body.atlas(view);
		this.body.sampler(sampler);
	}

	boolean plain() {
		return this.body.plain();
	}

}
