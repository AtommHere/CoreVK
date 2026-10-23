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

			builder.withColorTargetState(0, new ColorTargetState(Optional.empty(), SCREEN_FORMAT,
					ColorTargetState.WRITE_COLOR));
		} else {
			for (int slot = 0; slot < this.attachments.size(); slot++) {
				int index = this.attachments.get(slot).target();
				GpuFormat format = targets.format(index);
				if (format == null) {

					throw new IllegalStateException(this.path + " writes "
							+ TargetName.canonical(index) + " and the plan carries no format for it");
				}

				builder.withColorTargetState(slot, new ColorTargetState(
						BlendFunctions.of(targets.blend(program), Optional.empty()), format,
						ColorTargetState.WRITE_ALL));
			}

			for (int slot = this.attachments.size(); slot < this.outputs; slot++) {
				builder.withUnusedColorTargetState(slot);
			}
		}

		this.pipeline = builder.build();

		ShadowCompare.note(this.pipeline, this.path, loaded);

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

			var image = TargetName.imageIndex(sampler);
			if (image.isPresent()) {
				int index = image.getAsInt();
				TargetSchedule.Side side = this.step == null
						? (targets.schedule().flippedAtEnd().contains(index)
								? TargetSchedule.Side.ALT : TargetSchedule.Side.MAIN)
						: this.step.read(index);
				TargetSurface target = targets.surface(index, side);

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

			ColorTargets.PackSource source = this.packSources.get(at);
			GpuTextureView supplied = source == null ? null : targets.packView(source.image());

			TargetSurface surface = binding.kind() == SamplerPlan.Kind.COLORTEX
					? targets.surface(binding.index(), binding.side())
					: null;

			GpuTextureView bound = supplied != null ? supplied : switch (binding.kind()) {
				case COLORTEX -> surface == null ? null : surface.view();

				case DEPTH -> depth(binding.sampler(), targets, depthView);

				case SHADOW_DEPTH -> or(this.loaded.samplers().withoutTranslucents(binding.sampler())
						? targets.shadow().depthWithoutTranslucents()
						: targets.shadow().depth(),
						or(ConstantTextures.farPlaneIfReady(), targets.white()));
				case SHADOW_COLOUR -> or(targets.shadow().colour(binding.index()), targets.white());
				case NOISE -> targets.noise();

				case CENTER_DEPTH -> centerDepth(targets);

				case DISTANT_DEPTH -> distant(binding.sampler(), targets, distantView);

				case UNSERVED, UNBINDABLE, PACK_TEXTURE, CUSTOM_IMAGE, COLOUR_IMAGE -> targets.black();
			};

			FilterMode filter = supplied != null ? source.filter() : switch (binding.kind()) {
				case COLORTEX -> targets.filter(binding.index());
				case NOISE, SHADOW_COLOUR -> FilterMode.LINEAR;
				case SHADOW_DEPTH -> targets.shadow()
						.depthFilter(this.loaded.samplers().withoutTranslucents(binding.sampler()));
				case CUSTOM_IMAGE -> customImageFilter(sampler);
				default -> FilterMode.NEAREST;
			};

			boolean mipmaps = binding.kind() == SamplerPlan.Kind.SHADOW_DEPTH
					? targets.shadow().depthMipmapped(
							this.loaded.samplers().withoutTranslucents(binding.sampler()))
					: surface != null && surface.chainWritten()
							&& this.lodTargets.contains(binding.index());

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

		if (!gaps.nobody().isEmpty()) {
			this.notes.add(this.path + " reads " + gaps.nobody().size() + " values no engine "
					+ "answers, CoreVK included, so they are zeroes there too: " + gaps.nobody());
		}

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
