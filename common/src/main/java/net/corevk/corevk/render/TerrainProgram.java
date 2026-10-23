package net.corevk.corevk.render;

import net.corevk.corevk.glsl.PackProgram;
import net.corevk.corevk.glsl.SodiumVertex;
import net.corevk.corevk.glsl.VertexInputs;
import net.corevk.corevk.pack.program.TerrainPass;
import net.corevk.corevk.pack.source.OpenedPack;
import net.corevk.corevk.pack.target.ChainPlan;
import net.corevk.corevk.pack.target.TargetPlan;
import net.corevk.corevk.pack.model.TargetSize;
import net.corevk.corevk.CoreVK;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;

import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class TerrainProgram extends FamilyProgram {

	private static final String NAMESPACE = CoreVK.MOD_ID + "_sodium";

	private static final String FAMILY = "chunk";

	private TerrainProgram(TerrainPass pass, PackProgram.Loaded loaded, PackValues values, int load,
			VertexFormat format, List<ChainPlan.Attachment> writes, ColorTargets targets,
			boolean chainRuns) {
		super(new GeometryProgram(new GeometryProgram.Pass(FAMILY,
				pass.name().toLowerCase(Locale.ROOT), NAMESPACE, SodiumVertex.ANSWERED,
				pass.shadow(),

				pass.blended() ? Optional.of(BlendFunction.TRANSLUCENT) : Optional.<BlendFunction>empty(),

				pass.covers(), false, pass.afterDeferred(),

				PrimitiveTopology.QUADS,

				!pass.shadow(), depthState(pass), pass.stage(), null,

				null,

				false),
				loaded, values, load, format, writes, targets, chainRuns));
	}

	@Override
	public boolean warmAhead(AheadCompiler compiler) {
		return false;
	}

	@Override
	public void discardAhead() {
	}

	private static DepthStencilState depthState(TerrainPass pass) {
		return pass.shadow()
				? new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true)
				: DepthStencilState.DEFAULT;
	}

	static PackProgram.Terrain read(OpenedPack pack, String place, PackValues values)
			throws IOException {

		VertexInputs inputs = values.separateAo()
				? VertexInputs.TERRAIN_SEPARATE_AO
				: VertexInputs.TERRAIN;
		PackProgram.Terrain read = PackProgram.loadTerrain(pack, place, inputs);
		if (read.programs().isEmpty()) {
			CoreVK.logger().warn("{} serves no terrain program with both stages in {}, so the "
					+ "world keeps the game's own shader", pack.packPath().getFileName(),
					place.isEmpty() ? "its root" : place);
		}

		return read;
	}

	static Map<TerrainPass, TerrainProgram> build(Map<TerrainPass, PackProgram.Loaded> loaded,
			PackValues values, int load, VertexFormat format, ChainPlan plan,
			TargetPlan chainTargets, boolean chainRuns, ColorTargets targets) {
		try {
			Map<TerrainPass, TerrainProgram> programs = new EnumMap<>(TerrainPass.class);
			loaded.forEach((pass, one) -> {

				String servedBy = one.path().substring(one.path().lastIndexOf('/') + 1);
				PackProgram.Loaded bound = one.rebind(chainTargets, pass.afterDeferred()
						? chainTargets.schedule().stepAfterDeferred(servedBy)
						: chainTargets.schedule().step(servedBy));

				List<ChainPlan.Attachment> writes = plan.geometry(pass)
						.filter(geometry -> {
							if (geometry.size().equals(TargetSize.ofScreen())) {
								return true;
							}

							CoreVK.logger().warn("{} writes targets the pack asked to be scaled, so "
									+ "they cannot share a pass with the game's own target and its "
									+ "other draw buffers are written nowhere", servedBy);

							return false;
						})
						.map(ChainPlan.Pass::attachments)
						.orElse(List.of());
				programs.put(pass, new TerrainProgram(pass, bound, values, load, format, writes,
						targets, chainRuns));
			});

			return programs;
		} catch (RuntimeException e) {
			CoreVK.logger().error("Could not equip the terrain programs, so the world keeps the "
					+ "game's own shader", e);

			return Map.of();
		}
	}

	static boolean carries(VertexFormat format, List<String> carried) {
		List<String> elements = format.getElements().stream()
				.map(VertexFormatElement::name)
				.toList();
		if (elements.equals(carried)) {
			return true;
		}

		CoreVK.logger().error("The chunk mesh carries {} and these programs declare {}, so no terrain "
				+ "program can be drawn and this pack is put away rather than drawn by halves",
				elements, carried);

		return false;
	}

	RenderPipeline prepare(GpuDevice device, GpuTextureView atlas) {
		return this.body.prepare(device, atlas);
	}

	void sampler(GpuSampler sampler) {
		this.body.sampler(sampler);
	}

	boolean servable() {
		return this.body.servable();
	}

	boolean covers() {
		return this.body.covers();
	}

}
