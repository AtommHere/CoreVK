package net.corevk.corevk.render;

import net.corevk.corevk.glsl.EntityVertex;
import net.corevk.corevk.glsl.LinesVertex;
import net.corevk.corevk.glsl.MovingBlockVertex;
import net.corevk.corevk.glsl.PackProgram;
import net.corevk.corevk.pack.model.ProgramFallbacks;
import net.corevk.corevk.pack.target.ChainPlan;
import net.corevk.corevk.pack.target.TargetPlan;
import net.corevk.corevk.CoreVK;

import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;

import org.joml.Matrix4fc;

import java.util.List;
import java.util.Optional;
import java.util.Set;

final class EntityProgram extends FamilyProgram {

	
	private static final String FAMILY = "entity";

	
	private static final String NAMESPACE = CoreVK.MOD_ID;

	
	private static Set<String> answered(EntityDraw.Element element) {
		if (element.glint() || element.text()) {
			return Set.of();
		}

		if (element.movingBlock()) {
			return MovingBlockVertex.ANSWERED;
		}

		return element.lines() ? LinesVertex.ANSWERED : EntityVertex.ANSWERED;
	}

	private EntityProgram(GeometryProgram body) {
		super(body);
	}

	
	static EntityProgram of(PackProgram.Loaded loaded, EntityDraw.Element element, PackValues values,
			int load, List<ChainPlan.Attachment> writes, TargetPlan chainTargets,
			ColorTargets targets, boolean chainRuns) {
		// Bound again against the chain's own plan, for the reason the terrain and the sky are: what
		// the load bound them against is a plan without the user's pass filter. The step is the
		// PIECE's, which is where the two halves part company: the writing half is drawn in the
		// game's opaque feature phase, which stands between the opaque chunks and the deferred stage,
		// and the blending half after that stage has run. A half bound against the other one's step
		// reads the side of every target the chain is about to write rather than the one it wrote,
		// which is a frame of lag nothing anywhere would report.
		String servedBy = loaded.path().substring(loaded.path().lastIndexOf('/') + 1);
		// A shadow row is bound at the step of the half its geometry is drawn in, which is the early
		// one: the map is filled before the deferred stage of the frame it is read by, and it writes
		// shadowcolor rather than any colortex, so the side it reads is the only thing the step
		// decides for it.
		boolean shadow = element.shadow();
		// Asked of the row and never of the blend, which is Element.afterStage: a hand pass is drawn
		// wholly on one side whatever its rows blend, and the arm blends. Read off the blend here,
		// every hand row whose blend disagreed with its pass was bound against the wrong step, and
		// the two tables were wrong in opposite directions: a blending row of the solid pass against
		// a stage its pass runs before, a row of the water pass that does not blend against one its
		// pass runs after.
		boolean afterStage = element.afterStage();
		PackProgram.Loaded bound = loaded.rebind(chainTargets, afterStage
				? chainTargets.schedule().stepAfterDeferred(servedBy)
				: chainTargets.schedule().step(servedBy));

		RenderPipeline game = element.pipeline();

		return new EntityProgram(new GeometryProgram(new GeometryProgram.Pass(FAMILY,
				element.element(), NAMESPACE, answered(element), shadow,
				// Nothing blends into the shadow map, whatever the pipeline the row was made from
				// says. Every shadow program of CoreVK is declared with BlendModeOverride.OFF
				// (shaderpack/loading/ProgramId.java:13-19), and the reason is what a map is for:
				// what it wants of a translucent surface is the depth that surface stands at, not
				// that depth mixed with the one behind it.
				//
				// Off the map, the game's own blending for this pipeline, unless the name asked for
				// is one CoreVK gives a default of its own; the pack's own blend directive still
				// displaces either, GeometryProgram looking that one up under the file that really
				// served the piece. Only the eyes have one, and for them the game's translucent
				// blend is the wrong answer rather than a duller one: an eye is drawn additively or
				// it is a decal. The two questions are asked in this order and not folded together:
				// the map's refusal is about the target, the lookup is about the program name.
				shadow ? Optional.<BlendFunction>empty()
						: BlendFunctions.of(ProgramFallbacks.blendOverride(element.program()),
								GraphicsApi.colorTarget(game).blendFunction()),
				// covers: the mask on every piece drawn before the seed and on no other, which is
				// Element.covers and is answered there, beside the question of which side of the
				// stage a piece is drawn on. It is what takes draw buffer nought of those pieces off
				// the game's target: the mask carries the depth the fragment left, so the seed reads
				// a mob's own depth back at every pixel of it and leaves the pixel alone.
				//
				// The two are still not one decision with draw buffer nought: GeometryProgram asks
				// the side of the stage first, so a pass can keep nought on the game's target for a
				// reason that has nothing to do with a mask.
				//
				// claimed: no opaque piece of this family stands over these pixels, which is true of
				// every piece here including the arm. The sky's blending pieces have the disc and the
				// horizon cone under them and are the one family that answers otherwise; what earns
				// the hand's solid pass its own draw buffer nought is the mask it writes, not a
				// sibling.
				element.covers(), false,
				// afterDeferred: which side of the stage this piece is drawn on, which decides the
				// half of every target it reads and whether a depth sampler may be answered with the
				// opaque world's image. Asked of the row, Element.afterStage saying why that is not
				// the same question as what the pipeline blends. A shadow row is on neither side of a
				// stage that has not run when it draws, and answers the early one.
				afterStage,
				// The piece's own on both sides of the map, which is what CoreVK ends up drawing with
				// too, and NOT the chunk passes' answer: those really do drop the cull, and that is
				// about a wall meshed one side only, where a mob is closed geometry with no such back
				// to leak through.
				//
				// CoreVK looks like it says otherwise and does not. It brackets its shadow stage with
				// _disableCull() and _enableCull() (shadows/ShadowRenderer.java:501 and :615), and
				// that bracket is overridden on the first pipeline change either way. Inside the
				// branch for the passes it opens itself it re-applies the pipeline's own cull in so
				// many words (mixin/MixinGlCommandEncoder.java:137-140, under the pipeline memo at
				// :117); and it never reaches that branch for a geometry draw, its hook being an
				// @Inject at the HEAD of trySetup that only cancels there (:92, :101), so the vanilla
				// setup runs to the end and applies the same thing. The second leg is read on 26.2's
				// GL encoder rather than on the one CoreVK runs against, which is the weaker half of
				// this; the first is read in CoreVK itself.
				game.getPrimitiveTopology(), game.isCull(),
				// The piece's own, TURNED ROUND rather than replaced. See intoMap.
				shadow ? intoMap(game.getDepthStencilState()) : game.getDepthStencilState(),
				// The piece's own, and the halves answer differently: NONE for a mob, which is
				// CoreVK's answer rather than a reading of what the pass is, and BLOCK_ENTITIES for a
				// block entity, which CoreVK really does pose. The class comment has the file:line.
				element.stage(),
				// Nothing of the game's bound beside the mesh: an entity pipeline carries samplers
				// and transforms, and this program declares none of their names.
				null,
				// One block, written once: every draw of one pass reads the same values.
				null,
				// Drawn in the game's own volume, so the dh matrices answer the game's.
				false),
				bound, values, load, element.format(), writes, targets, chainRuns));
	}

	
	private static DepthStencilState intoMap(DepthStencilState state) {
		if (state == null) {
			return null;
		}

		return new DepthStencilState(mirrored(state.depthTest()), state.writeDepth(),
				-state.depthBiasScaleFactor(), -state.depthBiasConstant());
	}

	
	private static CompareOp mirrored(CompareOp op) {
		return switch (op) {
			case LESS_THAN -> CompareOp.GREATER_THAN;
			case LESS_THAN_OR_EQUAL -> CompareOp.GREATER_THAN_OR_EQUAL;
			case GREATER_THAN -> CompareOp.LESS_THAN;
			case GREATER_THAN_OR_EQUAL -> CompareOp.LESS_THAN_OR_EQUAL;
			default -> op;
		};
	}

	
	RenderPipeline prepare(GpuDevice device, Matrix4fc modelView, Matrix4fc bob,
			Matrix4fc projection) {
		return this.body.prepare(device, null, modelView, bob, null, projection);
	}

	
	void texture(GpuTextureView view, GpuSampler sampler) {
		this.body.atlas(view);
		this.body.sampler(sampler);
	}

	
	boolean plain() {
		return this.body.plain();
	}

	
	boolean readsGameTransforms() {
		return this.body.readsGameTransforms();
	}

}
