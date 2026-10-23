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

		put(new Element("End sky", "gbuffers_skytextured", "endsky",
				DefaultVertexFormat.POSITION_TEX_COLOR, PrimitiveTopology.QUADS,
				Optional.of(BlendFunction.TRANSLUCENT), true, false, RenderStage.CUSTOM_SKY, ""));

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

				Map<String, List<ChainPlan.Attachment>> byProgram = new LinkedHashMap<>();
				ELEMENTS.values()
						.forEach(element -> byProgram.computeIfAbsent(element.program(), this::writes));

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

		this.owner.beginFrame();
		if (!this.owner.openTargets(device)) {
			return null;
		}

		if (!shown()) {
			return null;
		}

		RenderPipeline pipeline = program.prepare(device, element.rotated() ? modelView : null, colour);

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
