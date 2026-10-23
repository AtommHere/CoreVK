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
import com.mojang.blaze3d.vertex.VertexFormat;

import java.util.List;
import java.util.Set;

final class ParticleProgram extends FamilyProgram {

	private static final String FAMILY = "particles";

	private static final String NAMESPACE = CoreVK.MOD_ID;

	private static final Set<String> ANSWERED = Set.of();

	private ParticleProgram(GeometryProgram body) {
		super(body);
	}

	static ParticleProgram of(PackProgram.Loaded loaded, ParticleDraw.Element element,
			PackValues values, int load, List<ChainPlan.Attachment> writes, TargetPlan chainTargets,
			ColorTargets targets, boolean chainRuns) {

		String servedBy = loaded.path().substring(loaded.path().lastIndexOf('/') + 1);
		PackProgram.Loaded bound = loaded.rebind(chainTargets, element.afterDeferred()
				? chainTargets.schedule().stepAfterDeferred(servedBy)
				: chainTargets.schedule().step(servedBy));

		RenderPipeline game = element.pipeline();

		return new ParticleProgram(new GeometryProgram(new GeometryProgram.Pass(FAMILY,
				element.element(), NAMESPACE, ANSWERED, false,
				GraphicsApi.colorTarget(game).blendFunction(),

				!element.afterDeferred(), false, element.afterDeferred(),
				game.getPrimitiveTopology(), game.isCull(),
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

	RenderPipeline reshape(GpuDevice device, VertexFormat layout) {
		return this.body.reshape(device, layout);
	}

}
