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

		String servedBy = loaded.path().substring(loaded.path().lastIndexOf('/') + 1);

		boolean shadow = element.shadow();

		boolean afterStage = element.afterStage();
		PackProgram.Loaded bound = loaded.rebind(chainTargets, afterStage
				? chainTargets.schedule().stepAfterDeferred(servedBy)
				: chainTargets.schedule().step(servedBy));

		RenderPipeline game = element.pipeline();

		return new EntityProgram(new GeometryProgram(new GeometryProgram.Pass(FAMILY,
				element.element(), NAMESPACE, answered(element), shadow,

				shadow ? Optional.<BlendFunction>empty()
						: BlendFunctions.of(ProgramFallbacks.blendOverride(element.program()),
								GraphicsApi.colorTarget(game).blendFunction()),

				element.covers(), false,

				afterStage,

				game.getPrimitiveTopology(), game.isCull(),

				shadow ? intoMap(game.getDepthStencilState()) : game.getDepthStencilState(),

				element.stage(),

				null,

				null,

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
