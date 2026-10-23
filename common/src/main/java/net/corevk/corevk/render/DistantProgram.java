package net.corevk.corevk.render;

import net.corevk.corevk.dh.DhLods;
import net.corevk.corevk.glsl.DistantVertex;
import net.corevk.corevk.glsl.PackProgram;
import net.corevk.corevk.pack.target.ChainPlan;
import net.corevk.corevk.pack.target.TargetPlan;
import net.corevk.corevk.CoreVK;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.vertex.VertexFormat;

import org.joml.Matrix4fc;

import java.util.List;
import java.util.Optional;

final class DistantProgram extends FamilyProgram {

	private static final String FAMILY = "far terrain";

	private static final String NAMESPACE = CoreVK.MOD_ID;

	private static final DepthStencilState DEPTH =
			new DepthStencilState(CompareOp.GREATER_THAN, true);

	private static final DepthStencilState SHADOW_DEPTH =
			new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true);

	private DistantProgram(GeometryProgram body) {
		super(body);
	}

	static DistantProgram of(PackProgram.Loaded loaded, DistantDraw.Element element,
			List<String> carried, PackValues values, int load, List<ChainPlan.Attachment> writes,
			TargetPlan chainTargets, ColorTargets targets, boolean chainRuns) {

		String servedBy = loaded.path().substring(loaded.path().lastIndexOf('/') + 1);
		PackProgram.Loaded bound = loaded.rebind(chainTargets, element.afterDeferred()
				? chainTargets.schedule().stepAfterDeferred(servedBy)
				: chainTargets.schedule().step(servedBy));

		VertexFormat format = DistantMesh.format(carried);

		return new DistantProgram(new GeometryProgram(new GeometryProgram.Pass(FAMILY,
				element.element(), NAMESPACE, DistantVertex.ANSWERED, element.shadow(),

				element.afterDeferred() ? Optional.of(BlendFunction.TRANSLUCENT)
						: Optional.<BlendFunction>empty(),

				false, false, element.afterDeferred(), PrimitiveTopology.TRIANGLES,

				!element.afterDeferred() && !element.shadow(),
				element.shadow() ? SHADOW_DEPTH : DEPTH, element.stage(), null,

				DistantVertex.SECTION_BLOCK,

				!element.shadow()),
				bound, values, load, format, writes, targets, chainRuns));
	}

	RenderPipeline prepare(GpuDevice device, Matrix4fc projection) {

		return this.body.prepare(device, null, null, null, null, projection);
	}

	boolean plain() {
		return this.body.plain();
	}

	@Override
	public boolean warmAhead(AheadCompiler compiler) {

		if (!DhLods.usable()) {
			return false;
		}

		return super.warmAhead(compiler);
	}

}
