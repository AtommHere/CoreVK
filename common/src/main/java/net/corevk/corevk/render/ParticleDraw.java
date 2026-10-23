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
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class ParticleDraw extends FamilyDraw {

	private static volatile boolean wanted;

	private static final AlphaTest CUTOUT = AlphaTest.ONE_TENTH;

	record Element(RenderPipeline pipeline, String element, String program, boolean afterDeferred) {

		private PackProgram.GeometryElement asked() {
			return new PackProgram.GeometryElement(this.element, this.program, CUTOUT,
					VertexInputs.PARTICLE, !this.afterDeferred);
		}

		RenderStage stage() {
			return RenderStage.PARTICLES;
		}
	}

	private static final Map<Boolean, Element> ELEMENTS = new LinkedHashMap<>();

	static {
		ELEMENTS.put(false, new Element(RenderPipelines.OPAQUE_PARTICLE, "particles",
				"gbuffers_particles", false));
		ELEMENTS.put(true, new Element(RenderPipelines.TRANSLUCENT_PARTICLE, "particles_translucent",
				"gbuffers_particles_translucent", true));
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

	private final Map<String, ParticleProgram> programs = new LinkedHashMap<>();

	private volatile boolean read;

	private final Set<String> refused = new LinkedHashSet<>();

	private final Set<RenderPipeline> foreign = new LinkedHashSet<>();

	private static final String ATLAS_SAMPLER = "Sampler0";

	private ParticleProgram drawing;
	private RenderPipeline standsIn;
	private RenderPipeline bound;

	private RenderPass pass;

	private boolean binding;

	private boolean handedOurs;

	private RenderPassDescriptor descriptor;

	ParticleDraw(PackChain owner, Path packPath, String place, Map<String, OptionValue> chosen,
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

	public static RenderPipeline group(boolean translucent, GpuTextureView colour,
			GpuTextureView depth) {
		ParticleDraw draw = PackChain.particles();
		GpuDevice device = RenderSystem.tryGetDevice();
		if (draw == null || !wanted || device == null) {
			return null;
		}

		try {
			return draw.prepare(device, ELEMENTS.get(translucent), colour, depth);
		} catch (GpuDeviceLossException e) {
			throw e;
		} catch (RuntimeException e) {
			wanted = false;
			CoreVK.logger().error("CoreVK stopped drawing the particles after an error", e);

			return null;
		}
	}

	public static RenderPassDescriptor descriptor() {
		ParticleDraw draw = PackChain.particles();

		return draw == null ? null : draw.descriptor;
	}

	public static void opened(RenderPass pass) {
		ParticleDraw draw = PackChain.particles();
		if (draw != null && draw.drawing != null) {
			draw.pass = pass;
		}
	}

	@SuppressWarnings("ReferenceEquality")
	public static RenderPipeline pipeline(RenderPass pass, RenderPipeline game) {
		ParticleDraw draw = PackChain.particles();
		if (draw == null || draw.drawing == null || draw.bound == null || pass != draw.pass) {
			return game;
		}

		draw.handedOurs = true;

		if (game == draw.bound) {
			return game;
		}

		if (draw.standsIn == game) {
			return draw.bound;
		}

		if (draw.descriptor == null) {
			draw.handedOurs = false;

			return game;
		}

		GpuDevice device = RenderSystem.tryGetDevice();
		VertexFormat layout = game.getVertexFormatBinding(0);
		RenderPipeline reshaped = device == null || layout == null
				? null
				: draw.drawing.reshape(device, layout);
		if (draw.foreign.add(game)) {
			if (reshaped != null) {
				CoreVK.logger().warn("A particle draw asked for {}, which is neither of the two "
						+ "pipelines the game draws its own quad particles with, inside a group "
						+ "whose pass this engine had already opened over the pack's own colour "
						+ "targets, where a pipeline carrying one colour state is refused by name. "
						+ "It takes the pack's particle program over the caller's own vertex "
						+ "layout, which is where CoreVK lands for the callers that register such "
						+ "pipelines against its particle programs", game.getLocation());
			} else {
				CoreVK.logger().warn("A particle draw asked for {}, which is neither of the two "
						+ "pipelines the game draws its own quad particles with, inside a group "
						+ "whose pass this engine had already opened over the pack's own colour "
						+ "targets, where a pipeline carrying one colour state is refused by name. "
						+ "The pack's particle program could not be rebuilt over the caller's own "
						+ "vertex layout, so it is bound as built and reads that mesh through its "
						+ "own", game.getLocation());
			}
		}

		return reshaped == null ? draw.bound : reshaped;
	}

	public static void texture(RenderPass pass, String name, GpuTextureView view,
			GpuSampler sampler) {
		ParticleDraw draw = PackChain.particles();

		if (draw == null || draw.drawing == null || draw.bound == null || !draw.handedOurs
				|| pass != draw.pass || draw.binding || !ATLAS_SAMPLER.equals(name)) {
			return;
		}

		draw.binding = true;
		try {
			draw.drawing.texture(view, sampler);
			draw.drawing.bind(pass);
		} finally {
			draw.binding = false;
		}
	}

	public static void endGroup() {
		ParticleDraw draw = PackChain.particles();
		if (draw != null) {
			draw.forget();
		}
	}

	private void forget() {
		this.drawing = null;
		this.standsIn = null;
		this.bound = null;
		this.pass = null;
		this.descriptor = null;
		this.handedOurs = false;
	}

	private boolean shown() {
		return !this.chainRuns || this.owner.drawable();
	}

	private RenderPipeline prepare(GpuDevice device, Element element, GpuTextureView colour,
			GpuTextureView depth) {
		if (!this.read) {
			return null;
		}

		forget();
		ParticleProgram program = this.programs.get(element.element());
		if (program == null) {
			return null;
		}

		if (element.afterDeferred() && GameRender.particlesApart()) {
			return refuse("fabulous", "the game's improved transparency is on, so it draws its "
					+ "translucent particles into a target of its own that it composes afterwards, "
					+ "and the pack's colour targets cannot be attached beside it. This engine turns "
					+ "that option off when the pack is loaded, as CoreVK does when shaders are enabled, "
					+ "so reaching this line means it was turned back on afterwards. Turning it off "
					+ "again gives that half back to the pack");
		}

		this.owner.beginFrame();
		if (!this.owner.openTargets(device) || !shown()) {
			return null;
		}

		RenderPipeline pipeline = program.prepare(device);
		if (pipeline == null) {

			return refuse("prepare:" + element.element(), "the " + element.element() + " program "
					+ "refused to prepare, which it says on its own line above. That is settled for "
					+ "as long as this pack is loaded, so it paints steadily rather than as a "
					+ "flicker");
		}

		RenderPassDescriptor pass = program.descriptor(colour, depth);
		if (pass == null && !program.plain()) {
			return refuse("unallocated:" + element.element(), "one of the pack's colour targets had "
					+ "no image yet on some frame, so the pass this half wanted could not be built "
					+ "then. That comes and goes with the frame rather than lasting");
		}

		this.drawing = program;
		this.standsIn = element.pipeline();
		this.bound = pipeline;
		this.descriptor = pass;

		return pipeline;
	}

	private RenderPipeline refuse(String reason, String why) {
		if (this.refused.add(reason)) {
			CoreVK.logger().warn("A group of particles went back to the game's own shader because {}",
					why);
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
				CoreVK.logger().info("{} serves nothing in {} for the particles, so the game keeps its "
						+ "own shader for them", this.packPath.getFileName(),
						this.place.isEmpty() ? "its root" : this.place);

				return;
			}

			announceOrdering();

			for (Element element : ELEMENTS.values()) {
				PackProgram.Loaded one = loaded.get(element.element());
				if (one == null) {
					CoreVK.logger().info("{} serves nothing in {} for the {} particles, so the game "
							+ "keeps its own shader for that half", this.packPath.getFileName(),
							this.place.isEmpty() ? "its root" : this.place,
							element.afterDeferred() ? "translucent" : "opaque");

					continue;
				}

				List<ChainPlan.Attachment> writes = writes(element, one);
				if (writes != null) {
					this.programs.put(element.element(), ParticleProgram.of(one, element, this.values,
							this.load, writes, this.chainTargets, this.targets, this.chainRuns));
				}
			}
		} catch (IOException | RuntimeException e) {
			CoreVK.logger().error("Could not prepare the particle programs of "
					+ this.packPath.getFileName() + ", so the game keeps its own shader for them", e);
		} finally {
			this.read = true;
		}
	}

	private void announceOrdering() {
		this.values.particleOrdering()
				.filter(ordering -> !ordering.equalsIgnoreCase("mixed"))
				.ifPresent(ordering -> CoreVK.logger().info("{} asks for particles.ordering={}, and "
						+ "this engine draws them where the game does: the opaque ones before the "
						+ "deferred stage and the translucent ones after the world's water, which is "
						+ "what mixed names. CoreVK reads the word and moves nothing either",
						this.packPath.getFileName(), ordering));
	}

	private List<ChainPlan.Attachment> writes(Element element, PackProgram.Loaded loaded) {
		String servedBy = loaded.path().substring(loaded.path().lastIndexOf('/') + 1);
		Optional<ChainPlan.Pass> geometry = this.plan.geometryOf(servedBy, element.afterDeferred());
		if (geometry.isEmpty()) {
			return List.of();
		}

		ChainPlan.Pass pass = geometry.get();
		if (!pass.size().equals(TargetSize.ofScreen())) {
			CoreVK.logger().warn("{} writes targets the pack asked to be scaled, so they cannot share "
					+ "a pass with the game's own target and the game keeps its own shader for the {} "
					+ "particles", servedBy, element.afterDeferred() ? "translucent" : "opaque");

			return null;
		}

		return pass.attachments();
	}

	@Override
	Collection<ParticleProgram> programs() {
		return this.programs.values();
	}

	@Override
	void rotate() {
		forget();
		if (!this.read) {
			return;
		}

		this.programs.values().forEach(ParticleProgram::rotate);
	}

	@Override
	void release() {
		forget();
		this.programs.values().forEach(ParticleProgram::release);
		this.programs.clear();
		this.refused.clear();
		this.foreign.clear();
		this.read = false;
	}
}
