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
		// Bound again against the chain's own plan, for the reason the terrain and the sky are: what
		// the load bound them against is a plan without the user's pass filter.
		//
		// And taken through stepAfterDeferred, which is what this family answers differently from the
		// sky and the entities: the game draws its weather in a frame graph pass of its own, after the
		// whole main pass, so the halves it READS have to be the ones the deferred stage left, exactly
		// as the translucent chunk pass takes them. Asked through step, this program would write the
		// half ChainPlan gave it, which is the after-deferred one, and read the half before it, and
		// TargetSchedule.stepAfterDeferred says in its own words that nothing on either side would
		// report that. Its "for the translucent chunk pass alone" is about CoreVK's Sodium wiring and
		// not about the format: CoreVK's own core shader path hands every gbuffers key a snapshot that
		// switches when the translucents begin, and its weather key is on the late side of that.
		String servedBy = loaded.path().substring(loaded.path().lastIndexOf('/') + 1);
		PackProgram.Loaded bound =
				loaded.rebind(chainTargets, chainTargets.schedule().stepAfterDeferred(servedBy));

		RenderPipeline game = element.pipeline();

		return new WeatherProgram(new GeometryProgram(new GeometryProgram.Pass(FAMILY,
				element.element(), NAMESPACE, ANSWERED, false,
				GraphicsApi.colorTarget(game).blendFunction(),
				// No coverage mask, and the sky's rule is the one that decides it: the mask is written
				// whatever the blend, so a curtain of rain that is a hundred parts transparent to one
				// part water would claim every pixel it spans. It is also drawn long after the seed,
				// which is what the mask exists to cut. Nothing claims these pixels for it either, and
				// nothing needs to: a pass drawn after the seed answers that question by itself.
				false, false, true, game.getPrimitiveTopology(), game.isCull(),
				game.getDepthStencilState(), element.stage(),
				// Nothing of the game's bound beside the mesh, unlike the clouds: the curtain is a
				// vertex buffer the renderer fills, and this program declares no name of its own.
				null,
				// One block, written once: every draw of one pass reads the same values.
				null,
				// Drawn in the game's own volume, so the dh matrices answer the game's.
				false),
				bound, values, load, DefaultVertexFormat.PARTICLE, writes, targets, chainRuns));
	}

	
	RenderPipeline prepare(GpuDevice device) {
		// No model view of its own and no colour: the renderer writes its transform from
		// RenderSystem.getModelViewMatrixCopy(), which is the frame's camera, and through the one
		// argument overload, whose modulator is white.
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
