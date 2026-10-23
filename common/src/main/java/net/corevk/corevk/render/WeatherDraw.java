package net.corevk.corevk.render;

import net.corevk.corevk.glsl.PackProgram;
import net.corevk.corevk.glsl.VertexInputs;
import net.corevk.corevk.pack.option.OptionValue;
import net.corevk.corevk.pack.model.AlphaTest;
import net.corevk.corevk.pack.model.RenderStage;
import net.corevk.corevk.pack.source.OpenedPack;
import net.corevk.corevk.pack.target.ChainPlan;
import net.corevk.corevk.pack.target.TargetPlan;
import net.corevk.corevk.pack.model.TargetSize;
import net.corevk.corevk.CoreVK;

import com.mojang.blaze3d.GpuDeviceLossException;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class WeatherDraw extends FamilyDraw {

	private static volatile boolean wanted;

	private static final String PROGRAM = "gbuffers_weather";

	private static final AlphaTest CUTOUT = AlphaTest.ONE_TENTH;

	record Element(RenderPipeline pipeline, String element, RenderStage stage) {

		private PackProgram.GeometryElement asked() {
			return new PackProgram.GeometryElement(this.element, PROGRAM, CUTOUT,
					VertexInputs.PARTICLE, false);
		}
	}

	private static final Map<RenderPipeline, Element> ELEMENTS = new LinkedHashMap<>();

	static {
		put(new Element(GamePipelines.weather(), "weather", RenderStage.RAIN_SNOW));

		RenderPipeline depthWrite = GamePipelines.weatherDepthWrite();
		if (depthWrite != null) {
			put(new Element(depthWrite, "weather_depth", RenderStage.RAIN_SNOW));
		}
	}

	private static void put(Element element) {
		ELEMENTS.put(element.pipeline(), element);
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

	private final Map<String, WeatherProgram> programs = new LinkedHashMap<>();

	private volatile boolean read;

	private final Set<String> refused = new LinkedHashSet<>();

	private WeatherProgram drawing;

	private RenderPassDescriptor descriptor;

	WeatherDraw(PackChain owner, Path packPath, String place, Map<String, OptionValue> chosen,
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

	public static boolean draws() {
		WeatherDraw draw = PackChain.weather();

		return draw == null || !wanted || draw.values.weather().drawn();
	}

	public static boolean splashes() {
		WeatherDraw draw = PackChain.weather();

		return draw == null || !wanted || draw.values.weather().particles();
	}

	public static boolean depth() {
		WeatherDraw draw = PackChain.weather();

		return draw != null && draw.values.rainDepth();
	}

	public static RenderPipeline element(RenderPipeline game, GpuTextureView colour,
			GpuTextureView depth) {
		WeatherDraw draw = PackChain.weather();
		GpuDevice device = RenderSystem.tryGetDevice();
		Element element = ELEMENTS.get(game);
		if (draw == null || !wanted || device == null || element == null) {
			return null;
		}

		try {
			return draw.prepare(device, element, colour, depth);
		} catch (GpuDeviceLossException e) {
			throw e;
		} catch (RuntimeException e) {
			wanted = false;
			CoreVK.logger().error("CoreVK stopped drawing the weather after an error", e);

			return null;
		}
	}

	public static RenderPassDescriptor descriptor() {
		WeatherDraw draw = PackChain.weather();

		return draw == null ? null : draw.descriptor;
	}

	public static void texture(GpuTextureView view, GpuSampler sampler) {
		WeatherDraw draw = PackChain.weather();
		if (draw != null && draw.drawing != null) {
			draw.drawing.texture(view, sampler);
		}
	}

	public static void bind(RenderPass pass, RenderPipeline bound) {
		WeatherDraw draw = PackChain.weather();
		if (draw != null && draw.drawing != null && draw.drawing.owns(bound)) {
			draw.drawing.bind(pass);
		}
	}

	private boolean shown() {
		return !this.chainRuns || this.owner.drawable();
	}

	private RenderPipeline prepare(GpuDevice device, Element element, GpuTextureView colour,
			GpuTextureView depth) {
		if (!this.read) {
			return null;
		}

		this.descriptor = null;
		WeatherProgram program = this.programs.get(element.element());
		this.drawing = program;
		if (program == null) {
			return null;
		}

		if (GameRender.weatherApart()) {
			this.drawing = null;

			return refuse("fabulous", "the game's improved transparency is on, so it draws its "
					+ "weather into a target of its own that it composes afterwards, and the pack's "
					+ "colour targets cannot be attached beside it. This engine turns that option off "
					+ "when the pack is loaded, as CoreVK does when shaders are enabled, so reaching "
					+ "this line means it was turned back on afterwards. Turning it off again gives "
					+ "the pack's rain back");
		}

		this.owner.beginFrame();
		if (!this.owner.openTargets(device)) {
			this.drawing = null;

			return null;
		}

		if (!shown()) {
			this.drawing = null;

			return null;
		}

		RenderPipeline pipeline = program.prepare(device);
		if (pipeline == null) {
			this.drawing = null;

			return refuse("prepare:" + element.element(), "the " + element.element() + " program "
					+ "refused to prepare, which it says on its own line above. That is settled for as "
					+ "long as this pack is loaded, so every frame the game picks that pipeline for "
					+ "draws its own rain, steadily rather than as a flicker");
		}

		this.descriptor = program.descriptor(colour, depth);
		if (this.descriptor == null && !program.plain()) {
			this.drawing = null;

			return refuse("unallocated", "one of the pack's colour targets had no image yet on some "
					+ "frame, so the pass the curtain wanted could not be built then. That comes and "
					+ "goes with the frame rather than lasting");
		}

		return pipeline;
	}

	private RenderPipeline refuse(String reason, String why) {
		if (this.refused.add(reason)) {
			CoreVK.logger().warn("The rain and the snow went back to the game's own shader because "
					+ "{}", why);
		}

		return null;
	}

	private void read(OpenedPack shared) {
		try {
			List<PackProgram.GeometryElement> asked = ELEMENTS.values().stream().map(Element::asked).toList();
			Map<String, PackProgram.Loaded> loaded = shared != null
					? PackProgram.loadGeometry(shared, this.place, asked)
					: PackProgram.loadGeometry(this.packPath, this.place, asked, this.chosen, this.profile);
			if (loaded.isEmpty()) {
				CoreVK.logger().info("{} serves nothing in {} for the weather, so the game keeps its "
						+ "own shader for the rain and the snow", this.packPath.getFileName(),
						this.place.isEmpty() ? "its root" : this.place);

				return;
			}

			List<ChainPlan.Attachment> writes = writes(loaded.values().iterator().next());
			if (writes == null) {
				return;
			}

			ELEMENTS.values().stream()
					.filter(element -> loaded.containsKey(element.element()))
					.forEach(element -> this.programs.put(element.element(), WeatherProgram.of(
							loaded.get(element.element()), element, this.values, this.load, writes,
							this.chainTargets, this.targets, this.chainRuns)));
		} catch (IOException | RuntimeException e) {
			CoreVK.logger().error("Could not prepare the weather program of "
					+ this.packPath.getFileName() + ", so the game keeps its own shader for the rain "
					+ "and the snow", e);
		} finally {
			this.read = true;
		}
	}

	private List<ChainPlan.Attachment> writes(PackProgram.Loaded loaded) {
		String servedBy = loaded.path().substring(loaded.path().lastIndexOf('/') + 1);
		Optional<ChainPlan.Pass> geometry = this.plan.geometryOf(servedBy, true);
		if (geometry.isEmpty()) {
			return List.of();
		}

		ChainPlan.Pass pass = geometry.get();
		if (!pass.size().equals(TargetSize.ofScreen())) {
			CoreVK.logger().warn("{} writes targets the pack asked to be scaled, so they cannot share "
					+ "a pass with the game's own target and the game keeps its own shader for the rain "
					+ "and the snow", servedBy);

			return null;
		}

		return pass.attachments();
	}

	@Override
	Collection<WeatherProgram> programs() {
		return this.programs.values();
	}

	@Override
	void rotate() {
		this.drawing = null;
		this.descriptor = null;
		if (!this.read) {
			return;
		}

		this.programs.values().forEach(WeatherProgram::rotate);
	}

	@Override
	void release() {
		this.programs.values().forEach(WeatherProgram::release);
		this.programs.clear();
		this.drawing = null;
		this.descriptor = null;

		this.refused.clear();
		this.read = false;
	}
}
