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
		// Bound again against the chain's own plan, for the reason every other family is: what the
		// load bound them against is a plan without the user's pass filter.
		//
		// And the step is the ELEMENT's, which is the one place this family needs both answers: it
		// straddles the deferred stage, so the opaque half reads the halves the prepares left and the
		// translucent one reads the halves the deferreds left. Handed one answer for both, the
		// translucent half would write the half the plan gave it and read the half before it, which
		// TargetSchedule.stepAfterDeferred says nothing on either side would report. The terrain
		// branches on the same question and for the same reason.
		String servedBy = loaded.path().substring(loaded.path().lastIndexOf('/') + 1);
		PackProgram.Loaded bound = loaded.rebind(chainTargets, element.afterDeferred()
				? chainTargets.schedule().stepAfterDeferred(servedBy)
				: chainTargets.schedule().step(servedBy));

		RenderPipeline game = element.pipeline();

		return new ParticleProgram(new GeometryProgram(new GeometryProgram.Pass(FAMILY,
				element.element(), NAMESPACE, ANSWERED, false,
				GraphicsApi.colorTarget(game).blendFunction(),
				// The coverage mask on the opaque half and not on the translucent one, which is the
				// entities' rule and is what decides whether the pack owns draw buffer nought.
				//
				// WITHOUT IT THE OPAQUE HALF NEVER WRITES THE PACK'S TARGET AT ALL. Draw buffer
				// nought falls back to the game's own, so a pack whose particle program declares one
				// draw buffer writes nothing of the pack's and its colour reaches the picture only
				// through the seed, already tone mapped and flat. Photon is that pack: its
				// gbuffers_particles writes colortex1, which is its gbuffer, and the deferred stage
				// shades what it finds there. Reached through the seed instead, the smoke arrives as
				// a finished LDR colour the deferred stage then reads as gbuffer data, which is the
				// warm haze a capture pair put beside CoreVK's dark smoke.
				//
				// CoreVK binds every gbuffers program to a framebuffer over the pack's own declared
				// draw buffers, the particles among them (pipeline/CoreVKRenderingPipeline.java:677-678
				// with the two particle keys at pipeline/programs/ShaderKey.java:94,96), so the
				// pack's colour never makes that trip there.
				//
				// The translucent half owes no mask for the reason every after-deferred pass owes
				// none: the seed has run long before it draws, and it owns draw buffer nought on the
				// strength of its side alone.
				//
				// The opaque half is drawn with RenderPipelines.OPAQUE_PARTICLE, which blends
				// nothing, so no sibling has to mark these pixels for it either.
				!element.afterDeferred(), false, element.afterDeferred(),
				game.getPrimitiveTopology(), game.isCull(),
				game.getDepthStencilState(), element.stage(),
				// Nothing of the game's bound beside the mesh, unlike the clouds: a particle carries
				// its whole geometry in the vertex buffer the renderer fills.
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

	
	RenderPipeline reshape(GpuDevice device, VertexFormat layout) {
		return this.body.reshape(device, layout);
	}

}
