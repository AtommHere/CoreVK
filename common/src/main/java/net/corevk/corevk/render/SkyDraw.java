package net.corevk.corevk.render;

import net.corevk.corevk.glsl.PackProgram;
import net.corevk.corevk.pack.option.OptionValue;
import net.corevk.corevk.pack.model.RenderStage;
import net.corevk.corevk.pack.source.OpenedPack;
import net.corevk.corevk.pack.target.ChainPlan;
import net.corevk.corevk.pack.target.TargetPlan;
import net.corevk.corevk.pack.model.TargetSize;
import net.corevk.corevk.CoreVK;

import com.mojang.blaze3d.GpuDeviceLossException;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;

import org.joml.Matrix4fc;
import org.joml.Vector4fc;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class SkyDraw extends FamilyDraw {

	
	private static volatile boolean wanted;

	
	record Element(String label, String program, String element, VertexFormat format,
			PrimitiveTopology topology, Optional<BlendFunction> blend, boolean covers, boolean rotated,
			RenderStage stage, String directive) {

		
		private PackProgram.SkyElement asked() {
			return new PackProgram.SkyElement(this.element, this.program,
					this.format.getElements().stream().map(VertexFormatElement::name).toList(),
					this.covers);
		}
	}

	
	private static final Map<String, Element> ELEMENTS = new LinkedHashMap<>();

	
	private static final String DISC = "Sky disc";

	static {
		put(new Element(DISC, "gbuffers_skybasic", "disc", DefaultVertexFormat.POSITION,
				PrimitiveTopology.TRIANGLE_FAN, Optional.empty(), true, false, RenderStage.SKY, ""));
		put(new Element("Sky dark", "gbuffers_skybasic", "dark", DefaultVertexFormat.POSITION,
				PrimitiveTopology.TRIANGLE_FAN, Optional.empty(), true, true, RenderStage.VOID, ""));
		put(new Element("Stars", "gbuffers_skybasic", "stars", DefaultVertexFormat.POSITION,
				PrimitiveTopology.QUADS, Optional.of(BlendFunction.OVERLAY), false, true,
				RenderStage.STARS, ""));
		put(new Element("Sunrise sunset", "gbuffers_skybasic", "sunrise",
				DefaultVertexFormat.POSITION_COLOR, PrimitiveTopology.TRIANGLE_FAN,
				Optional.of(BlendFunction.TRANSLUCENT), false, true, RenderStage.SUNSET, ""));
		put(new Element("Sky sun", "gbuffers_skytextured", "sun", DefaultVertexFormat.POSITION_TEX,
				PrimitiveTopology.QUADS, Optional.of(BlendFunction.OVERLAY), false, true,
				RenderStage.SUN, "sun"));
		put(new Element("Sky moon", "gbuffers_skytextured", "moon", DefaultVertexFormat.POSITION_TEX,
				PrimitiveTopology.QUADS, Optional.of(BlendFunction.OVERLAY), false, true,
				RenderStage.MOON, "moon"));
		// The End's cube of sky is the one element whose mesh carries a colour of its own AND a
		// texture coordinate. It is drawn under the frame's own matrix as the disc is, renderEndSky
		// writing its transform from getModelViewMatrixCopy without pushing anything of its own, so
		// it is handed no matrix here and reads the frame's.
		//
		// And it MARKS, though the game's own pipeline blends it, because it is the only piece the
		// End draws that could: buildEndSky writes 0xFF282828 at every vertex and end_sky.png has no
		// transparent texel, so the cube covers the frame the way the disc does. Left unmarked, the
		// End's whole sky was drawn into the pack's target and then painted over by the scene seed,
		// with the game's own target holding nothing but the frame's clear since the draw was taken
		// from it: a flat sky the colour of the fog, in the one place the pack had answered for.
		put(new Element("End sky", "gbuffers_skytextured", "endsky",
				DefaultVertexFormat.POSITION_TEX_COLOR, PrimitiveTopology.QUADS,
				Optional.of(BlendFunction.TRANSLUCENT), true, false, RenderStage.CUSTOM_SKY, ""));
		// The End's flash, which the game draws with the celestial pipeline and the celestial
		// atlas: same format, same topology and same blend as the sun and the moon, and rotated like
		// them, renderEndFlash pushing a model view of its own before it writes its transform. It
		// blends over the cube and claims nothing, as the sun blends over the disc.
		put(new Element("End flash", "gbuffers_skytextured", "endflash",
				DefaultVertexFormat.POSITION_TEX, PrimitiveTopology.QUADS,
				Optional.of(BlendFunction.OVERLAY), false, true, RenderStage.CUSTOM_SKY, ""));
	}

	private static void put(Element element) {
		ELEMENTS.put(element.label(), element);
	}

	private final PackChain owner;
	private final Path packPath;
	private final String place;
	private final Map<String, OptionValue> chosen;
	private final String profile;
	private final PackValues values;
	private final int load;
	private final ChainPlan plan;
	private final TargetPlan chainTargets;
	private final boolean chainRuns;
	private final ColorTargets targets;

	
	private final Map<String, SkyProgram> programs = new LinkedHashMap<>();

	
	private final HorizonCone horizon = new HorizonCone();

	
	private volatile boolean read;

	
	private SkyProgram drawing;

	SkyDraw(PackChain owner, Path packPath, String place, Map<String, OptionValue> chosen,
			String profile, PackValues values, int load, ChainPlan plan, TargetPlan chainTargets,
			boolean chainRuns, ColorTargets targets) {
		this.owner = owner;
		this.packPath = packPath;
		this.place = place;
		this.chosen = Map.copyOf(chosen);
		this.profile = profile;
		this.values = values;
		this.load = load;
		this.plan = plan;
		this.chainTargets = chainTargets;
		this.chainRuns = chainRuns;
		this.targets = targets;
	}

	
	static void wanted(boolean asked) {
		wanted = asked;
	}

	
	static boolean wanted() {
		return wanted;
	}

	
	void prefetch() {
		prefetch(null);
	}

	
	@Override
	void prefetch(OpenedPack shared) {
		if (!this.read && wanted()) {
			read(shared);
		}
	}

	
	public static boolean serves() {
		return PackChain.sky() != null && wanted;
	}

	
	public static float sunPathRotation() {
		SkyDraw draw = PackChain.sky();

		return draw == null ? 0.0F : draw.values.sunPathRotation();
	}

	
	public static RenderPipeline element(String label, Matrix4fc modelView, Vector4fc colour) {
		SkyDraw draw = PackChain.sky();
		GpuDevice device = RenderSystem.tryGetDevice();
		Element element = ELEMENTS.get(label);
		if (draw == null || !wanted || device == null || element == null) {
			return null;
		}

		try {
			return draw.prepare(device, element, modelView, colour);
		} catch (GpuDeviceLossException e) {
			throw e;
		} catch (RuntimeException e) {
			wanted = false;
			CoreVK.logger().error("CoreVK stopped drawing the sky after an error", e);

			return null;
		}
	}

	
	public static boolean draws(String label) {
		SkyDraw draw = PackChain.sky();
		Element element = ELEMENTS.get(label);
		if (draw == null || !wanted || element == null) {
			return true;
		}

		return draw.values.skyElements().allows(element.directive());
	}

	
	public static RenderPassDescriptor descriptor(GpuTextureView colour, GpuTextureView depth) {
		SkyDraw draw = PackChain.sky();
		if (draw == null || draw.drawing == null) {
			return null;
		}

		return draw.drawing.descriptor(colour, depth);
	}

	
	private boolean shown() {
		return !this.chainRuns || this.owner.drawable();
	}

	
	public static void texture(GpuTextureView view, GpuSampler sampler) {
		SkyDraw draw = PackChain.sky();
		if (draw != null && draw.drawing != null) {
			draw.drawing.texture(view, sampler);
		}
	}

	
	public static void horizon(RenderPass pass, RenderPipeline bound) {
		SkyDraw draw = PackChain.sky();
		if (draw != null && draw.drawing != null && draw.drawing.owns(bound) && draw.cone()) {
			draw.horizon.draw(pass, draw.drawing.path(), TerrainDraw.opaqueMask());
		}
	}

	
	private boolean cone() {
		return this.values.skyElements().sky();
	}

	
	public static void bind(RenderPass pass, RenderPipeline bound) {
		SkyDraw draw = PackChain.sky();
		if (draw != null && draw.drawing != null && draw.drawing.owns(bound)) {
			draw.drawing.bind(pass);
		}
	}

	
	private void read(OpenedPack shared) {
		try {
			List<String> off = ELEMENTS.values().stream()
					.map(Element::directive)
					.filter(directive -> !directive.isEmpty()
							&& !this.values.skyElements().allows(directive))
					.toList();
			if (!off.isEmpty()) {
				CoreVK.logger().info("{} draws its own {}, so where the game draws one this engine "
						+ "draws neither it nor a shader of the pack's in its place",
						this.packPath.getFileName(), String.join(" and ", off));
			}

			try {
				List<PackProgram.SkyElement> asked =
						ELEMENTS.values().stream().map(Element::asked).toList();
				Map<String, PackProgram.Loaded> loaded = shared != null
						? PackProgram.loadSky(shared, this.place, asked)
						: PackProgram.loadSky(this.packPath, this.place, asked, this.chosen, this.profile);

				// Asked once per PROGRAM and not once per piece: four of the eight are drawn with
				// gbuffers_skybasic and the other four with gbuffers_skytextured, and the plan would
				// answer for each of the two four times over.
				Map<String, List<ChainPlan.Attachment>> byProgram = new LinkedHashMap<>();
				ELEMENTS.values()
						.forEach(element -> byProgram.computeIfAbsent(element.program(), this::writes));

				// All of them or none of them, for the reason the class comment gives.
				//
				// Body Camera is the pack this was decided for and it NO LONGER REACHES IT, which is
				// worth knowing before reading a log that never says its name again: it declares
				// DRAWBUFFERS:012 on gbuffers_skybasic and nothing on gbuffers_skytextured, and since a
				// geometry program declaring nothing is read as writing colortex0, its sun and its moon
				// have a target of their own. Its whole sky went from the game's target to the pack's
				// with that reading, which is where CoreVK puts it. What still reaches this branch is a
				// pack whose sky programs are not all served, or whose targets this place cannot carry.
				List<String> behind = behind(loaded, byProgram);
				if (!behind.isEmpty()) {
					CoreVK.logger().info("{} has nowhere of its own for the {} of its sky, so the whole "
							+ "of the sky keeps the game's target and the scene seed brings it across",
							this.packPath.getFileName(), String.join(", ", behind));
					byProgram.clear();
				}

				ELEMENTS.values().stream()
						.filter(element -> loaded.containsKey(element.element()))
						.forEach(element -> this.programs.put(element.label(), SkyProgram.of(
								loaded.get(element.element()), element, this.values, this.load,
								byProgram.getOrDefault(element.program(), List.of()), this.chainTargets,
								this.targets, this.chainRuns)));

				// Both branches again, and worded for it: a place takes only the half of this list its
				// own branch reaches, so in the End the four pieces of gbuffers_skybasic can be named
				// here without the game ever having drawn one of them.
				List<String> missing = ELEMENTS.values().stream()
						.filter(element -> !loaded.containsKey(element.element()))
						.map(Element::element)
						.toList();
				if (!missing.isEmpty()) {
					CoreVK.logger().info("{} serves nothing in {} for the {} of its sky, so where the "
							+ "game draws one it keeps its own shader", this.packPath.getFileName(),
							this.place.isEmpty() ? "its root" : this.place, String.join(", ", missing));
				}
			} catch (IOException | RuntimeException e) {
				CoreVK.logger().error("Could not prepare the sky programs of "
						+ this.packPath.getFileName() + ", so the game keeps its own sky", e);
			}
		} finally {
			this.read = true;
		}
	}

	
	private List<ChainPlan.Attachment> writes(String program) {
		return this.plan.sky(program)
				.filter(sky -> {
					if (sky.size().equals(TargetSize.ofScreen())) {
						return true;
					}

					CoreVK.logger().warn("{} writes targets the pack asked to be scaled, so every "
							+ "piece of the sky it draws keeps the game's own target", sky.program());

					return false;
				})
				.map(ChainPlan.Pass::attachments)
				.orElse(List.of());
	}

	
	private List<String> behind(Map<String, PackProgram.Loaded> loaded,
			Map<String, List<ChainPlan.Attachment>> byProgram) {
		return ELEMENTS.values().stream()
				.filter(element -> this.values.skyElements().allows(element.directive()))
				.filter(element -> !loaded.containsKey(element.element())
						|| byProgram.get(element.program()).isEmpty())
				.map(Element::element)
				.toList();
	}

	private RenderPipeline prepare(GpuDevice device, Element element, Matrix4fc modelView,
			Vector4fc colour) {
		if (!this.read) {
			return null;
		}

		SkyProgram program = this.programs.get(element.label());
		this.drawing = program;
		if (program == null) {
			return null;
		}

		// The same two calls the terrain makes, and for the same reasons. The sky is drawn first in
		// the frame, so more often than not it is this that opens it; the second call is what makes
		// sure the colour targets a sky program samples exist before it reads them.
		this.owner.beginFrame();
		if (!this.owner.openTargets(device)) {
			return null;
		}

		// After the two calls above and never at the door, for the reason TerrainDraw gives at the
		// same point: the frame boundary hangs off whichever of the sky, the terrain and the chain
		// comes first, and the sky is usually first. Refused before opening the frame, the whole warm
		// up would leave the value store standing.
		if (!shown()) {
			return null;
		}

		// The colour goes to every element and the matrix only to those the game rotated: the two
		// are not the same question. Every sky draw carries a modulator, and two elements have a
		// matrix that is the camera's already, the disc and the End's cube of sky, one per branch.
		RenderPipeline pipeline = program.prepare(device, element.rotated() ? modelView : null, colour);

		// Here and not at the draw, because filling a vertex buffer is a copy the encoder refuses
		// while a pass is open, and this is the last moment before the disc's pass exists. Only once
		// the disc really has a pipeline: a place this engine does not draw the sky in pays nothing.
		if (pipeline != null && DISC.equals(element.label()) && cone()) {
			this.horizon.update(device);
		}

		return pipeline;
	}

	
	@Override
	Collection<SkyProgram> programs() {
		return this.programs.values();
	}

	
	@Override
	void rotate() {
		this.drawing = null;
		if (!this.read) {
			return;
		}

		this.programs.values().forEach(SkyProgram::rotate);
	}

	@Override
	void release() {
		this.programs.values().forEach(SkyProgram::release);
		this.programs.clear();
		this.horizon.release();
		this.drawing = null;
		this.read = false;
	}
}
