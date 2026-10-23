package net.corevk.corevk.render;

import net.corevk.corevk.glsl.PackProgram;
import net.corevk.corevk.glsl.TranslatedUnit;
import net.corevk.corevk.pack.model.ProgramStage;
import net.corevk.corevk.pack.model.TargetName;
import net.corevk.corevk.pack.model.TextureStage;
import net.corevk.corevk.pack.target.ChainPlan;
import net.corevk.corevk.pack.target.SamplerPlan;
import net.corevk.corevk.pack.target.TargetSchedule;
import net.corevk.corevk.pack.texture.CustomImages;
import net.corevk.corevk.render.storage.StorageBuffers;
import net.corevk.corevk.render.storage.StorageImages;
import net.corevk.corevk.uniform.TextSink;
import net.corevk.corevk.uniform.WorldState;
import net.corevk.corevk.CoreVK;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

final class PackPass {

	
	private static final String UNIFORM_BLOCK = "OfGlobals";

	
	private static final int MAX_ATTACHMENTS = 8;

	
	private static final int PUSH_DESCRIPTORS = 32;

	
	private static final GpuFormat SCREEN_FORMAT = GpuFormat.RGBA8_UNORM;

	
	private static final int VERTICES = 6;

	
	private static final Set<Identifier> ISSUED = new LinkedHashSet<>();
	private static int issuedFor = -1;

	private final String path;

	
	private final TextureStage textureStage;

	private final PackProgram.Loaded loaded;
	private final ChainPlan.Pass pass;
	private final List<ChainPlan.Attachment> attachments;
	private final PackValues values;
	private final PackUniforms uniforms;
	private final List<String> samplers;

	
	private final List<SamplerPlan.Binding> samplerBindings;

	
	private final List<ColorTargets.PackSource> packSources;

	private final List<String> storage;
	private final List<LodRead> lodReads;

	
	private final Set<Integer> lodTargets;

	
	private final TargetSchedule.Bound step;

	private final RenderPipeline pipeline;
	private final ShaderSource source;
	private final Supplier<String> label;
	private final List<String> notes = new ArrayList<>();
	private final List<GpuTextureView> attachedViews = new ArrayList<>();

	
	private boolean unwritableNoted;

	
	private final List<String> unusableViews;

	
	private RenderPass.RenderArea area;
	private int areaWidth;
	private int areaHeight;

	private final int outputs;
	private final int offset;
	private final boolean last;

	
	PackPass(String place, String program, PackProgram.Loaded loaded, ChainPlan.Pass pass,
			ColorTargets targets, PackValues values, int load, int offset) {
		this.path = place.isEmpty() ? program : place + "/" + program;
		this.textureStage = TextureStage.of(program).orElse(null);
		this.loaded = loaded;
		this.pass = pass;
		this.attachments = List.copyOf(pass.attachments());
		this.offset = offset;
		this.last = this.attachments.isEmpty();
		// Finished once: the encoder asks every pass of the game for its label to tell ours
		// apart, so a supplier that concatenated on every ask paid a string per pass per frame.
		String label = "CoreVK " + this.path;
		this.label = () -> label;
		this.values = values;
		this.uniforms = new PackUniforms(loaded.program().uniforms(), values.catalog());
		this.samplers = loaded.program().samplers().stream().map(TranslatedUnit.Uniform::name).toList();
		this.samplerBindings = this.samplers.stream().map(loaded.samplers()::binding).toList();
		this.unusableViews = StorageImages.unusableViews(this.samplers);
		if (!this.unusableViews.isEmpty()) {
			CoreVK.logger().warn("{} is not drawn: it reads {} through a view this device cannot "
					+ "sample and store in that format", this.path, this.unusableViews);
		}
		List<ColorTargets.PackSource> sources = new ArrayList<>();
		for (int at = 0; at < this.samplers.size(); at++) {
			SamplerPlan.Binding binding = this.samplerBindings.get(at);
			// A name that took the default is looked up under colortex0 and not under itself: the
			// pack wrote the directive against the target, and the name only reaches the file
			// because it landed on the same texture unit.
			String named = binding.defaulted()
					? TargetName.canonical(SamplerPlan.DEFAULT_TARGET)
					: this.samplers.get(at);
			sources.add(binding.kind() == SamplerPlan.Kind.PACK_TEXTURE
					? targets.packSource(this.textureStage, named)
					: null);
		}
		this.packSources = Collections.unmodifiableList(sources);
		this.storage = loaded.storageBlocks().stream()
				.distinct()
				.filter(StorageBuffers::named)
				.toList();
		this.lodReads = lodReadsOf(program, loaded, targets);
		this.lodTargets = this.lodReads.stream().map(LodRead::target).collect(Collectors.toSet());
		this.step = targets.schedule().step(program).orElse(null);

		if (this.attachments.size() > MAX_ATTACHMENTS) {
			throw new IllegalStateException(this.path + " writes " + this.attachments.size()
					+ " targets and a pipeline carries " + MAX_ATTACHMENTS);
		}

		int declared = loaded.program().stages().get(ProgramStage.FRAGMENT).notes().fragmentOutputs();
		this.outputs = this.last
				? 1
				: Math.min(Math.max(this.attachments.size(), declared), MAX_ATTACHMENTS);

		String vertex = loaded.program().stages().get(ProgramStage.VERTEX).text();
		String fragment = loaded.program().stages().get(ProgramStage.FRAGMENT).text();
		String stem = "pack/" + load + "/" + (place.isEmpty() ? "root" : place) + "/" + program;
		Identifier vertexId = Identifier.fromNamespaceAndPath(CoreVK.MOD_ID, stem + "/vertex");
		Identifier fragmentId = Identifier.fromNamespaceAndPath(CoreVK.MOD_ID, stem + "/fragment");
		claim(load, this.path, vertexId, fragmentId);

		this.source = GraphicsApi.source((id, type) -> {
			if (type == ShaderType.FRAGMENT) {
				return fragmentId.equals(id) ? fragment : null;
			}

			return vertexId.equals(id) ? vertex : null;
		});

		BindGroupLayout.Builder bindings = BindGroupLayout.builder()
				.withUniform(UNIFORM_BLOCK, UniformType.UNIFORM_BUFFER);
		this.samplers.forEach(name -> GraphicsApi.withSampler(bindings, name));
		this.storage.forEach(name -> bindings.withUniform(name, UniformType.UNIFORM_BUFFER));

		RenderPipeline.Builder builder = RenderPipeline.builder()
				.withLocation(Identifier.fromNamespaceAndPath(CoreVK.MOD_ID, "pipeline/" + stem))
				.withVertexShader(vertexId)
				.withFragmentShader(fragmentId)
				.withBindGroupLayout(BindGroupLayouts.GLOBALS)
				.withBindGroupLayout(bindings.build())
				.withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
				.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
				.withCull(false);

		if (this.last) {
			// The alpha of the game's target is left alone: the interface is drawn over it
			// afterwards and reads it.
			builder.withColorTargetState(0, new ColorTargetState(Optional.empty(), SCREEN_FORMAT,
					ColorTargetState.WRITE_COLOR));
		} else {
			for (int slot = 0; slot < this.attachments.size(); slot++) {
				int index = this.attachments.get(slot).target();
				GpuFormat format = targets.format(index);
				if (format == null) {
					// The plan allocates everything a program of the place writes, so this is the
					// plan disagreeing with itself. Refused here rather than at the first draw,
					// where the message would name a format and not a program.
					throw new IllegalStateException(this.path + " writes "
							+ TargetName.canonical(index) + " and the plan carries no format for it");
				}

				// A full screen program replaces what it writes unless the pack says otherwise, and
				// four packs of the corpus do say otherwise: Mellow and Reverie ask six of their
				// composites to blend ONE ONE, which is an accumulation. Replacing where a pack
				// asked to add is not a subtle difference, and nothing about it shows as an error.
				builder.withColorTargetState(slot, new ColorTargetState(
						BlendFunctions.of(targets.blend(program), Optional.empty()), format,
						ColorTargetState.WRITE_ALL));
			}

			for (int slot = this.attachments.size(); slot < this.outputs; slot++) {
				builder.withUnusedColorTargetState(slot);
			}
		}

		this.pipeline = builder.build();

		// Filed against the pipeline and not the program, because the pipeline is what the
		// descriptor walk can see when it has to answer for a name.
		ShadowCompare.note(this.pipeline, this.path, loaded);
		// A full screen program shipping a geometry stage on a device that cannot run one: the
		// compile that follows refuses the pipeline over the varyings that stage was to hand on,
		// wherever it renames them, and the chain draws the pass without it wherever it does not.
		// Said here rather than passed over in silence; no pack of the corpus writes one.
		if (!GeometryStage.note(this.pipeline, this.path, loaded)) {
			this.notes.add(this.path + " ships a geometry stage and this device cannot run one, so "
					+ "the pass is drawn without whatever that stage worked out per primitive");
		}

		noteGaps(declared);
	}

	
	CompiledRenderPipeline compile(GpuDevice device) {
		return GraphicsApi.compile(device, this.pipeline, this.source);
	}

	
	String path() {
		return this.path;
	}

	
	TargetSchedule.Bound step() {
		return this.step;
	}

	String program() {
		return this.pass.program();
	}

	int uniformOffset() {
		return this.offset;
	}

	
	List<LodRead> lodReads() {
		return this.lodReads;
	}

	
	List<ChainPlan.Attachment> attachments() {
		return this.attachments;
	}

	
	boolean readsCenterDepth() {
		return this.samplers.contains(SamplerPlan.centerDepth());
	}

	
	record LodRead(int target, TargetSchedule.Side side) {
	}

	
	private static List<LodRead> lodReadsOf(String program, PackProgram.Loaded loaded,
			ColorTargets targets) {
		Set<Integer> asked = targets.lodReads(program);
		if (asked.isEmpty()) {
			return List.of();
		}

		Map<Integer, TargetSchedule.Side> reads = new LinkedHashMap<>();
		for (TranslatedUnit.Uniform uniform : loaded.program().samplers()) {
			SamplerPlan.Binding binding = loaded.samplers().binding(uniform.name());
			if (binding.kind() == SamplerPlan.Kind.COLORTEX && asked.contains(binding.index())) {
				reads.putIfAbsent(binding.index(), binding.side());
			}
		}

		return reads.entrySet().stream()
				.map(entry -> new LodRead(entry.getKey(), entry.getValue()))
				.toList();
	}

	
	int uniformSize() {
		return Math.max(16, this.uniforms.size());
	}

	
	static int uniformSizeOf(PackProgram.Loaded loaded) {
		return Math.max(16, new PackUniforms(loaded.program().uniforms()).size());
	}

	void write(Std140Builder into, WorldState world) {
		this.uniforms.write(into, world);
	}

	
	String decoded(WorldState world) {
		TextSink sink = new TextSink();
		this.uniforms.write(sink, world);

		return sink.text();
	}

	
	int descriptors() {
		return this.samplers.size() + 2;
	}

	
	String describe() {
		StringBuilder line = new StringBuilder(this.path).append(" writes ").append(writes());
		if (!this.last && !this.pass.size().full()) {
			line.append(", sized ").append(this.pass.size().relative()
					? String.format(Locale.ROOT, "%.3f by %.3f of the screen",
							this.pass.size().width(), this.pass.size().height())
					: (int) this.pass.size().width() + " by " + (int) this.pass.size().height()
							+ " pixels");
		}

		line.append(", ")
				.append(this.loaded.program().uniforms().size()).append(" uniforms and ")
				.append(this.samplers.size()).append(" samplers, ")
				.append(descriptors()).append(" descriptors");

		// Said here and nowhere else, because this is the one binding the pack's own text cannot be
		// read for: the pack never wrote colortex0 beside these names, and whether they read the
		// scene or read nothing is the whole picture on a pack that calls the screen tex.
		List<String> defaulted = this.loaded.samplers().defaulted();
		if (!defaulted.isEmpty()) {
			line.append(", ").append(TargetName.canonical(SamplerPlan.DEFAULT_TARGET))
					.append(" by default for ").append(String.join(", ", defaulted));
		}

		return line.toString();
	}

	private String writes() {
		if (this.last) {
			return "the game's own target";
		}

		return this.attachments.stream()
				.map(attachment -> TargetName.canonical(attachment.target()) + " "
						+ attachment.side().name().toLowerCase(Locale.ROOT))
				.collect(Collectors.joining(", "));
	}

	
	List<String> notes() {
		return List.copyOf(this.notes);
	}

	
	static Set<Integer> colourImages(PackProgram.Loaded loaded) {
		Set<Integer> indices = new LinkedHashSet<>();
		for (TranslatedUnit.Uniform sampler : loaded.program().samplers()) {
			TargetName.imageIndex(sampler.name()).ifPresent(indices::add);
		}

		return indices;
	}

	
	static String describe(List<TranslatedUnit.Uniform> samplers) {
		return samplers.stream()
				.map(sampler -> sampler.name() + " as " + sampler.type())
				.collect(Collectors.joining(", "));
	}

	
	void draw(CommandEncoder encoder, ColorTargets targets, GpuTextureView depthView,
			GpuTextureView distantView, GpuBuffer quad, GpuBufferSlice uniforms, int screenWidth,
			int screenHeight) {
		// The two draws are not interchangeable and neither mistake shows as an error: a final sent
		// here has one null attachment and no target at all, and a composite sent the other way
		// paints the screen instead of the buffer the rest of the chain reads.
		if (this.last) {
			throw new IllegalStateException(this.path + " writes the game's target and has to be "
					+ "drawn through drawFinal");
		}

		this.attachedViews.clear();
		for (ChainPlan.Attachment attachment : this.attachments) {
			this.attachedViews.add(view(targets, attachment));
		}

		PassDescriptor descriptor = PassDescriptor.create(this.label);
		for (GpuTextureView view : this.attachedViews) {
			descriptor.withColorAttachment(view, targets.takeClear(view));
		}

		// Always at the tail. The encoder asserts that attachment zero is there, and a pipeline
		// whose state count differs from the attachment count is refused by setPipeline.
		for (int slot = this.attachments.size(); slot < this.outputs; slot++) {
			descriptor.withUnusedColorAttachment();
		}

		descriptor.withRenderArea(area(screenWidth, screenHeight));

		if (targets.hasPendingClears()) {
			targets.flushPending(encoder);
		}

		try (RenderPass pass = encoder.createRenderPass(descriptor.build())) {
			record(pass, targets, depthView, distantView, quad, uniforms);
		}
	}

	
	private RenderPass.RenderArea area(int screenWidth, int screenHeight) {
		if (this.area == null || screenWidth != this.areaWidth || screenHeight != this.areaHeight) {
			this.areaWidth = screenWidth;
			this.areaHeight = screenHeight;
			this.area = new RenderPass.RenderArea(0, 0, this.pass.size().width(screenWidth),
					this.pass.size().height(screenHeight));
		}

		return this.area;
	}

	
	void drawFinal(CommandEncoder encoder, GpuTextureView into, ColorTargets targets,
			GpuTextureView depthView, GpuTextureView distantView, GpuBuffer quad,
			GpuBufferSlice uniforms) {
		if (!this.last) {
			throw new IllegalStateException(this.path + " writes " + this.attachments.size()
					+ " colour targets of the pack and cannot be drawn onto the game's own");
		}

		try (RenderPass pass = encoder.createRenderPass(this.label, into, Optional.empty())) {
			record(pass, targets, depthView, distantView, quad, uniforms);
		}
	}

	private void record(RenderPass pass, ColorTargets targets, GpuTextureView depthView,
			GpuTextureView distantView, GpuBuffer quad, GpuBufferSlice uniforms) {
		GraphicsApi.setPipeline(pass, this.pipeline);
		RenderSystem.bindDefaultUniforms(pass);
		pass.setUniform(UNIFORM_BLOCK, uniforms);
		StorageBuffers.bind(pass, this.storage);
		pass.setVertexBuffer(0, quad.slice());
		if (this.unusableViews.isEmpty() && bindSamplers(pass, targets, depthView, distantView)) {
			pass.draw(VERTICES, 1, 0, 0);
		}
	}

	
	private boolean bindSamplers(RenderPass pass, ColorTargets targets, GpuTextureView depthView,
			GpuTextureView distantView) {
		for (int at = 0; at < this.samplers.size(); at++) {
			String sampler = this.samplers.get(at);
			SamplerPlan.Binding binding = this.samplerBindings.get(at);
			// An imageStore from a vertex or fragment stage writes the same half that a compute
			// hanging off this pass would write. A final has no step, so it uses the frame's
			// ending side. The descriptor mixins turn this binding into a storage image.
			var image = TargetName.imageIndex(sampler);
			if (image.isPresent()) {
				int index = image.getAsInt();
				TargetSchedule.Side side = this.step == null
						? (targets.schedule().flippedAtEnd().contains(index)
								? TargetSchedule.Side.ALT : TargetSchedule.Side.MAIN)
						: this.step.read(index);
				TargetSurface target = targets.surface(index, side);
				// Left unbound the draw would throw and stop the whole pack, so this program alone
				// is not drawn, as a compute storing into such a target is not dispatched.
				if (target == null || !target.storage()) {
					if (!this.unwritableNoted) {
						this.unwritableNoted = true;
						CoreVK.logger().warn("{} stores into {}, which {}, so it is not drawn",
								this.path, sampler, target == null
										? "this place never allocated"
										: "was not created writable: this device makes no storage "
												+ "image of its format");
					}
					return false;
				}
				GraphicsApi.bindTexture(pass, sampler, target.storageView(), sampler(false, FilterMode.NEAREST, false));
				continue;
			}

			// A texture the pack ships answers all three questions at once, and they are one
			// answer: which image, how it is filtered, and how it is addressed outside zero to one
			// are all the pack's to say, in the same directive and the same .mcmeta beside it.
			ColorTargets.PackSource source = this.packSources.get(at);
			GpuTextureView supplied = source == null ? null : targets.packView(source.image());

			// Resolved once for the view and the chain below, which used to be two walks of the
			// same map for one name.
			TargetSurface surface = binding.kind() == SamplerPlan.Kind.COLORTEX
					? targets.surface(binding.index(), binding.side())
					: null;

			GpuTextureView bound = supplied != null ? supplied : switch (binding.kind()) {
				case COLORTEX -> surface == null ? null : surface.view();
				// White where no image is there, and white is the far plane rather than a
				// placeholder: what a depth lookup reads is now an image already in the pack's own
				// window, where one is the far plane, and the whole world would otherwise be drawn
				// against the camera. It follows the image rather than the convention of the
				// target, which is what makes it the same answer as the shadow map's.
				case DEPTH -> depth(binding.sampler(), targets, depthView);
				// The far plane where the map is not there, rather than a placeholder: a shadowtex
				// lookup is the one depth read the translation never wraps, so the map stores the
				// forward window and a lookup that finds nothing has to say "nothing between here
				// and the light". Black would put the world in its own shadow. In a depth format
				// first, because a comparison sampler is only defined against one; the RGBA white
				// stays as the answer of last resort for the frames before the constants exist.
				case SHADOW_DEPTH -> or(this.loaded.samplers().withoutTranslucents(binding.sampler())
						? targets.shadow().depthWithoutTranslucents()
						: targets.shadow().depth(),
						or(ConstantTextures.farPlaneIfReady(), targets.white()));
				case SHADOW_COLOUR -> or(targets.shadow().colour(binding.index()), targets.white());
				case NOISE -> targets.noise();
				// White until the pass behind it has drawn once, which is the far plane in the pack's
				// own window and so a focus point at the horizon. Black would be a focus point at the
				// camera, which is the defect this whole path exists to close.
				case CENTER_DEPTH -> centerDepth(targets);
				// The far terrain's own depth, kept beside the world's as CoreVK keeps it, and white
				// for the far plane on the frames the pack drew no far terrain: the pack's Distant
				// Horizons branches then stay shut, exactly as without the mod. dhDepthTex1 is the
				// image without the water whichever half asks, which is CoreVK's copy before the
				// translucent LODs; the other two names follow the half, like depthtex0.
				case DISTANT_DEPTH -> distant(binding.sampler(), targets, distantView);
				// A name this backend cannot bind should have taken its program out of the chain
				// before a frame was drawn. It is still answered rather than left out, because the
				// layout carries it either way and the draw throws on the first name it misses.
				//
				// A pack texture reaches this line only when the pack took the name over and
				// nothing could be put behind it, which covers neither a file the pack does not
				// ship nor a picture refused at its header: both hand the name back and the colour
				// target keeps its ordinary binding, as it does under CoreVK. What is left here is a
				// declaration this engine could not serve, a raw blob it does not lay out, a path
				// outside the pack, or a picture whose pixels do not decode or are more than this
				// engine allocates, and black is the honest answer for it: the colour target of
				// the same name would have the pass read the scene as whatever the pack meant to
				// sample and look convincing.
				// A colour image never reaches this switch, bound above as a storage image.
				case UNSERVED, UNBINDABLE, PACK_TEXTURE, CUSTOM_IMAGE, COLOUR_IMAGE -> targets.black();
			};

			// The noise image is LINEAR for the same reason the terrain reads it LINEAR: it is a
			// continuous field the pack interpolates surfaces out of, and CoreVK binds it that way.
			//
			// A shadow COLOUR is LINEAR as well, whatever the pack declares, and that is the
			// reference rather than a taste: OptiFine filters shadowcolor linearly unless the pack
			// writes shadowcolorNNearest, while CoreVK parses that name and then binds the buffer
			// through GlSampler.LINEAR regardless (CoreVKSamplers.java:156-176), a sampler object
			// overruling the texture's own parameters. Honouring it here would diverge from the
			// engine packs are tuned against, on the buffer that carries the light which came
			// through stained glass and water: a pack blurs that across a penumbra, and read
			// NEAREST it steps in blocks the size of a shadow texel. Pegasus declares
			// shadowcolor0Nearest and sees it ignored under CoreVK.
			//
			// The shadow DEPTH is LINEAR for a reason of its own, and the reason a reader reaches
			// for to keep it NEAREST does not hold. An averaged depth would indeed be a comparison
			// against a surface standing nowhere, but that is true of an ordinary sampler and not
			// of a comparison one, where the hardware compares first and averages the RESULTS; and
			// the comparison this engine makes for itself takes its four texels with textureGather,
			// which no bound filter reaches either way. What the filter really decides is the other
			// read, the one every pack of the corpus makes: a PCF loop that samples shadowtex as a
			// plain sampler2D and expects each of its taps to be smoothed over a texel. CoreVK
			// filters both depth images LINEAR unless the pack asks otherwise, since
			// SamplingSettings.nearest starts false (ShadowRenderer.configureDepthSampler), so read
			// NEAREST every edge of such a loop walks in texels of the map however many taps it
			// pays for.
			//
			// The names that ask for NEAREST back on the DEPTH pair are read: shadowtexNearest,
			// shadowtexNNearest and shadowNMinMagNearest. The depth paragraph above is what a pack
			// that writes none of them gets, and that is not the whole corpus: Pegasus declares
			// shadowtex0Nearest and shadowtex1Nearest true and reads its map through a manual PCF
			// loop, so what it was shown was a map smoothed against its own declaration. The map
			// itself is asked rather than this line deciding, which is what keeps the three roads
			// that bind it - here, a geometry program and a compute - reading it one way.
			//
			// A custom image is the image's own answer and not this pass's, which is why it is
			// asked of one place rather than decided here: see customImageFilter.
			FilterMode filter = supplied != null ? source.filter() : switch (binding.kind()) {
				case COLORTEX -> targets.filter(binding.index());
				case NOISE, SHADOW_COLOUR -> FilterMode.LINEAR;
				case SHADOW_DEPTH -> targets.shadow()
						.depthFilter(this.loaded.samplers().withoutTranslucents(binding.sampler()));
				case CUSTOM_IMAGE -> customImageFilter(sampler);
				default -> FilterMode.NEAREST;
			};

			// A lod read needs a sampler that may climb past level nought, and that is not the
			// default: the cache's ordinary samplers stop there, so a lod read would come back with
			// the base image and a filled chain would look like it had never been written.
			//
			// Narrowed to the targets THIS program reads at a lod, which are exactly the chains the
			// caller filled before this draw, and not to every target that carries one. The two are
			// not the same set, and the difference is not a detail: a chain is only filled before
			// its reader, so letting an unrelated read climb one would hand it levels that belong
			// to an earlier moment of the frame.
			//
			// And asked of the surface as well, because the reduction is allowed to fail: its
			// pipeline may refuse to compile for a format, and then the levels hold whatever the
			// driver left there. Deciding this at the binding rather than when the pass was built
			// is what makes the fall back real instead of announced: a chain nothing has written
			// is read at level nought, which is the image the pack had before there were chains.
			//
			// The shadow map's own chain is the other half of this, and it is asked of the map for
			// the reason the filter is: a pack reading one image through two samplers is a
			// difference nothing would ever explain. The narrowing above has no equivalent there,
			// the map's chain being filled once at the tail of the stage that drew it and standing
			// for the whole frame that follows, so there is no moment of a frame it could belong
			// to instead.
			boolean mipmaps = binding.kind() == SamplerPlan.Kind.SHADOW_DEPTH
					? targets.shadow().depthMipmapped(
							this.loaded.samplers().withoutTranslucents(binding.sampler()))
					: surface != null && surface.chainWritten()
							&& this.lodTargets.contains(binding.index());

			// The noise image repeats and everything else clamps, which is CoreVK's choice and not a
			// taste: a pack indexes noisetex with coordinates of its own, in texels and well past
			// one, and clamped it reads the same edge row for the whole screen. That does not fail,
			// it produces a field of stripes that reads as an effect nobody asked for, and it takes
			// whatever the pack built out of it down with it. A texture of the pack's own says for
			// itself, in the .mcmeta beside the file.
			GraphicsApi.bindTexture(pass, sampler, bound == null ? targets.black() : bound,
					supplied != null
							? sampler(source.repeat(), filter, source.mipmaps())
							: sampler(binding.kind(), filter, mipmaps));
		}

		return true;
	}

	
	static GpuTextureView depth(String sampler, ColorTargets targets, GpuTextureView depthView) {
		if (SamplerPlan.depthCopy(sampler)) {
			if (SamplerPlan.preHandCopy(sampler)) {
				GpuTextureView preHand = targets.depth().preHand();
				if (preHand != null) {
					return preHand;
				}
			}

			GpuTextureView opaque = targets.depth().opaque();
			if (opaque != null) {
				return opaque;
			}
		}

		return depthView == null ? targets.white() : depthView;
	}

	
	static GpuTextureView distant(String sampler, ColorTargets targets, GpuTextureView distantView) {
		return SamplerPlan.distantWithoutWater(sampler)
				? or(targets.depth().distantOpaque(), targets.white())
				: or(distantView, targets.white());
	}

	
	static GpuTextureView centerDepth(ColorTargets targets) {
		return or(targets.centerDepth().view(), targets.white());
	}

	
	private static GpuTextureView or(GpuTextureView view, GpuTextureView fallback) {
		return view == null ? fallback : view;
	}

	
	static GpuSampler sampler(SamplerPlan.Kind kind, FilterMode filter, boolean mipmaps) {
		return sampler(kind == SamplerPlan.Kind.NOISE, filter, mipmaps);
	}

	
	static FilterMode customImageFilter(String sampler) {
		return CustomImages.viewFormat(sampler)
				.map(GpuFormats::filterFor)
				.orElse(FilterMode.NEAREST);
	}

	
	static GpuSampler sampler(boolean repeat, FilterMode filter, boolean mipmaps) {
		return repeat
				? RenderSystem.getSamplerCache().getRepeat(filter, mipmaps)
				: RenderSystem.getSamplerCache().getClampToEdge(filter, mipmaps);
	}

	private GpuTextureView view(ColorTargets targets, ChainPlan.Attachment attachment) {
		GpuTextureView view = targets.view(attachment.target(), attachment.side());
		if (view == null) {
			// Named rather than let through: a null attachment reaches the encoder as an assertion
			// or a null pointer, neither of which says which program wrote which target.
			throw new IllegalStateException(this.path + " writes "
					+ TargetName.canonical(attachment.target()) + " on its " + attachment.side()
					+ " half and nothing was allocated for it");
		}

		return view;
	}

	private void noteGaps(int declared) {
		if (declared > this.attachments.size() && !this.last) {
			this.notes.add(this.path + " declares " + declared + " fragment outputs for "
					+ this.attachments.size() + " draw buffers, so the ones past the last draw "
					+ "buffer are written nowhere");
		}

		if (this.last && declared > 1) {
			this.notes.add(this.path + " declares " + declared + " fragment outputs and a final "
					+ "writes the game's one target, so all but the first are written nowhere");
		}

		if (descriptors() > PUSH_DESCRIPTORS) {
			this.notes.add(this.path + " binds " + descriptors() + " descriptors in one set, past "
					+ "the " + PUSH_DESCRIPTORS + " a device commonly allows pushed at once");
		}

		// What the block could not be given, said as the three different things it is rather than
		// as one number. A name the pack declares for itself is ours to resolve and the reason is a
		// line above this one; a name no engine answers is nobody's; what is left is a value this
		// engine owes.
		PackValues.Gaps gaps = this.values.classify(this.uniforms.unsupplied());
		if (!gaps.engine().isEmpty()) {
			this.notes.add(this.path + " reads " + gaps.engine().size() + " values written as "
					+ "zeroes because this engine does not supply them yet: " + gaps.engine());
		}

		if (!gaps.pack().isEmpty()) {
			this.notes.add(this.path + " reads " + gaps.pack().size() + " values "
					+ this.loaded.packName() + " declares for itself, and none of those "
					+ "declarations survived: " + gaps.pack());
		}

		// And beside those, the names that are nobody's debt: the pack reads them under CoreVK as
		// well and gets the same nought, so saying this engine owes them would send a reader
		// looking through CoreVK for a source that was never written.
		if (!gaps.nobody().isEmpty()) {
			this.notes.add(this.path + " reads " + gaps.nobody().size() + " values no engine "
					+ "answers, CoreVK included, so they are zeroes there too: " + gaps.nobody());
		}

		// Underneath the three, the members that count as supplied and are not: a zero that
		// arrived through a registered source is the one failure a screenshot can never show.
		PackValues.standIns(this.loaded.program().uniforms().stream()
						.map(TranslatedUnit.Uniform::name)
						.toList())
				.forEach((reason, names) -> this.notes.add(this.path + " reads " + names
						+ " answered with a stand-in rather than with a value, which count as "
						+ "supplied everywhere else, because " + reason));

		List<String> unserved = this.loaded.samplers().unserved();
		if (!unserved.isEmpty()) {
			this.notes.add(this.path + " declares " + unserved.size() + " samplers that read one "
					+ "black pixel because nothing serves them: " + unserved);
		}

		// This one should be unreachable: a program declaring such a sampler is taken out of the
		// chain before anything is built. It stays because the failure it would otherwise produce
		// is the raw one this whole path exists to replace, and it names the type as well.
		List<TranslatedUnit.Uniform> unbindable = this.loaded.unbindable();
		if (!unbindable.isEmpty()) {
			this.notes.add(this.path + " is being drawn although it declares "
					+ describe(unbindable) + ", which this backend cannot bind, so its pipeline "
					+ "will not build");
		}
	}

	private static synchronized void claim(int load, String path, Identifier vertex,
			Identifier fragment) {
		if (issuedFor != load) {
			issuedFor = load;
			ISSUED.clear();
		}

		if (!ISSUED.add(vertex) || !ISSUED.add(fragment)) {
			throw new IllegalStateException("Two programs of one load would be compiled under the "
					+ "same names, " + vertex + " and " + fragment + ", the second being " + path);
		}
	}
}
