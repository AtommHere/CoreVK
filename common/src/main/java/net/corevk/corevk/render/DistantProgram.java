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
		// Bound again against the chain's own plan, for the reason every other family is: what the
		// load bound them against is a plan without the user's pass filter. The step is the half's,
		// the two standing on opposite sides of the deferred stage.
		String servedBy = loaded.path().substring(loaded.path().lastIndexOf('/') + 1);
		PackProgram.Loaded bound = loaded.rebind(chainTargets, element.afterDeferred()
				? chainTargets.schedule().stepAfterDeferred(servedBy)
				: chainTargets.schedule().step(servedBy));

		VertexFormat format = DistantMesh.format(carried);

		return new DistantProgram(new GeometryProgram(new GeometryProgram.Pass(FAMILY,
				element.element(), NAMESPACE, DistantVertex.ANSWERED, element.shadow(),
				// The half DH defers blends over what stands behind it, exactly as the world's own
				// water does; the opaque half writes outright. DH's own two pipelines are built the
				// same way, one withoutBlend and one with TRANSLUCENT
				// (common/render/blaze/BlazeDhTerrainRenderer.java:140 and :148). Neither of the
				// light's halves blends unless the pack says so: what a map wants from a surface is
				// the depth it stands at and the colour it tints the light with, both written
				// outright, and that is what the world's own shadow halves do here.
				//
				// It is the DEFAULT and not a rule, which is the whole of what this argument is:
				// a blend directive the pack wrote for this program still wins, GeometryProgram
				// asking the pack first. CoreVK has no default of its own to copy here - its
				// dh_shadow key carries no blend override, alone among the shadow keys, which all
				// carry BlendModeOverride.OFF (shaderpack/loading/ProgramId.java:13-19 against
				// :57), so what its DH shadow program blends with is the pack's own directive or
				// whatever state stands.
				element.afterDeferred() ? Optional.of(BlendFunction.TRANSLUCENT)
						: Optional.<BlendFunction>empty(),
				// No coverage mask, and the class comment says what writes those pixels instead.
				// claimed: nothing of this family marks them either, for the same reason. The mask
				// is about the scene seed, which the light's halves are no part of at all.
				false, false, element.afterDeferred(), PrimitiveTopology.TRIANGLES,
				// The opaque half is culled, which is DH's own answer, one withFaceCulling(true) on
				// the shared builder (common/render/blaze/BlazeDhTerrainRenderer.java:101); the
				// water half is not, which is what a pack gets under CoreVK: a bare
				// glDisable(GL_CULL_FACE) ahead of the transparent LOD pass
				// (compat/dh/LodRendererEvents.java:346), never re-enabled by CoreVK itself and put
				// back by the next state that asks for it. So far water keeps a surface when seen
				// from underneath or from inside it.
				//
				// Nothing at all is culled in the map, which is the world's own answer there and
				// CoreVK's, one _disableCull for the whole of its shadow stage
				// (shadows/ShadowRenderer.java:501): what matters is which surface is nearest the
				// light and not which way it faces, and a hill drawn on one side only leaks light
				// through its back.
				!element.afterDeferred() && !element.shadow(),
				element.shadow() ? SHADOW_DEPTH : DEPTH, element.stage(), null,
				// The one family with a value that belongs to the section rather than to the pass.
				DistantVertex.SECTION_BLOCK,
				// And the one family drawn in DH's own volume - the camera's halves are, and the
				// light's are not. A dh_shadow writes gl_ProjectionMatrix * gl_ModelViewMatrix like
				// every other shadow program, and those two answer the shadow pair here because the
				// pass is a shadow pass; CoreVK fills the same two names of its own DH shadow program
				// with ShadowRenderer.PROJECTION and MODELVIEW and not with DH's volume
				// (compat/dh/LodRendererEvents.java:304-307). The three dhProjection names answer
				// DH's volume for every pass alike, as CoreVK serves them.
				!element.shadow()),
				bound, values, load, format, writes, targets, chainRuns));
	}

	
	RenderPipeline prepare(GpuDevice device, Matrix4fc projection) {
		// No model view and no colour of its own: an LOD stands in the world like a chunk, so the
		// frame's camera places it, and nothing modulates what DH built.
		return this.body.prepare(device, null, null, null, null, projection);
	}

	
	boolean plain() {
		return this.body.plain();
	}

	@Override
	public boolean warmAhead(AheadCompiler compiler) {
		// Without DH standing, nothing ever draws these. And measured on a bench without that
		// mod, the two dh programs also refused shaderc outright, so compiling ahead here bought
		// nothing but refusal lines for programs no frame would ever ask for.
		if (!DhLods.usable()) {
			return false;
		}

		return super.warmAhead(compiler);
	}

}
