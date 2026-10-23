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
		// Bound again against the chain's own plan, for the reason the terrain is: what the load
		// bound them against is a plan without the user's pass filter. The step is the one before
		// the deferreds, the sky standing at the third rank of the frame.
		String servedBy = loaded.path().substring(loaded.path().lastIndexOf('/') + 1);
		PackProgram.Loaded bound =
				loaded.rebind(chainTargets, chainTargets.schedule().step(servedBy));

		// A piece that claims every pixel it spans is one the scene seed must not paint over, and the
		// element answers that itself rather than this deriving it from the blend. The mask is
		// written whatever the blend, so a star quad, a hundred parts transparent to one part star,
		// would claim every pixel it spans and cut the game's picture out of all of them; but the
		// converse does not hold, and the End's cube of sky is where it fails, the game blending a
		// mesh that is opaque at every vertex. The opaque and cutout chunk passes answer the same
		// question the same way, and the translucent one answers it no.
		return new SkyProgram(new GeometryProgram(new GeometryProgram.Pass(FAMILY, element.element(),
				NAMESPACE, Set.copyOf(SkyVertex.ATTRIBUTES), false, element.blend(),
				// claimed, and the sky is the one family that answers it yes: it draws pieces of its
				// own that claim every pixel they span, the disc and the dark in the overworld and the
				// cube of sky in the End, over the pixels the five that claim nothing span, which are
				// the stars, the sunrise, the sun, the moon and the End's flash. Not the five that
				// blend, which are six: the cube of sky blends and claims the frame all the same.
				// Those five blend onto a target the seed leaves alone although none of them marks a
				// pixel of its own. Answered no, they would go to the game's target and the seed would
				// discard at exactly those pixels, on the mask their own siblings wrote: no stars, no
				// sun, no moon and no flash.
				//
				// The band the game's own discs leave open is marked by ours, HorizonCone, which
				// shares the disc's pass and its mask. What that still does not cover is written
				// where the field is declared, GeometryProgram.Pass#claimed.
				element.covers(), true, false, element.topology(),
				// Five pipelines under the eight passes, the disc sharing one with the dark plane and
				// the End's flash sharing another with the sun and the moon, and not one of the five
				// names a culling of its own: all eight take the builder's default, which is to cull.
				true, null, element.stage(),
				// Nothing of the game's bound beside the mesh, unlike the clouds: every one of the
				// eight sky passes carries its whole geometry in the vertex buffer it binds.
				null,
				// One block, written once: every draw of one pass reads the same values.
				null,
				// Drawn in the game's own volume, so the dh matrices answer the game's.
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
