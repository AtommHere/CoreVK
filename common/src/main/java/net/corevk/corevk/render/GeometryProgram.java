package net.corevk.corevk.render;

import net.corevk.corevk.glsl.LegacyGlsl;
import net.corevk.corevk.glsl.PackProgram;
import net.corevk.corevk.glsl.TranslatedUnit;
import net.corevk.corevk.pack.model.AlphaTest;
import net.corevk.corevk.pack.model.BlendMode;
import net.corevk.corevk.pack.model.ProgramStage;
import net.corevk.corevk.pack.model.RenderStage;
import net.corevk.corevk.pack.model.TargetName;
import net.corevk.corevk.pack.model.TextureStage;
import net.corevk.corevk.pack.program.TerrainPass;
import net.corevk.corevk.pack.target.ChainPlan;
import net.corevk.corevk.pack.target.DrawBuffers;
import net.corevk.corevk.pack.target.SamplerPlan;
import net.corevk.corevk.render.pbr.PbrAtlases;
import net.corevk.corevk.render.pbr.PbrMap;
import net.corevk.corevk.render.pbr.PbrTextures;
import net.corevk.corevk.render.storage.StorageBuffers;
import net.corevk.corevk.render.storage.StorageImages;
import net.corevk.corevk.render.timing.PassTimings;
import net.corevk.corevk.uniform.ClipSpace;
import net.corevk.corevk.uniform.TextSink;
import net.corevk.corevk.uniform.WorldState;
import net.corevk.corevk.CoreVK;

import com.mojang.blaze3d.GpuDeviceLossException;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import com.mojang.blaze3d.vulkan.VulkanRenderPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.resources.Identifier;

import org.joml.Matrix4fc;
import org.joml.Vector4fc;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

final class GeometryProgram {

	private static final String UNIFORM_BLOCK = "OfGlobals";

	record Pass(String family, String name, String namespace, Set<String> answered, boolean shadow,
			Optional<BlendFunction> blend, boolean covers, boolean claimed, boolean afterDeferred,
			PrimitiveTopology topology, boolean cull, DepthStencilState depth, RenderStage stage,
			BindGroupLayout bindings, String perDraw, boolean distantVolume) {

		boolean blended() {
			return this.blend.isPresent();
		}
	}

	private static final Set<String> ATLAS = Set.of("gtexture", "tex", "texture", "ofTexture");

	private static final String LIGHTMAP = "lightmap";

	private static final String OVERLAY = LegacyGlsl.OVERLAY_SAMPLER;

	private static RenderPass settledIn;

	private static GeometryProgram settled;

	private enum Bound {

		GAME,

		PACK,

		UNUSED,

		COVERAGE
	}

	private record Slot(Bound bound, ChainPlan.Attachment target, GpuFormat format) {
	}

	private static final class Sampled {

		private final String name;

		private final PbrMap material;

		private final boolean albedo;

		private final SamplerPlan.Binding binding;

		private final ColorTargets.PackSource source;

		private boolean interpolates;

		private GpuTextureView view;

		private GpuSampler state;

		private GpuTextureView servedFor;
		private GpuTextureView served;
		private boolean servedKnown;

		Sampled(String name, PbrMap material, boolean albedo, SamplerPlan.Binding binding,
				ColorTargets.PackSource source) {
			this.name = name;
			this.material = material;
			this.albedo = albedo;
			this.binding = binding;
			this.source = source;
		}

		private boolean followsTheImage() {
			return this.material != null || this.albedo;
		}
	}

	private final Pass pass;
	private final String path;

	private final Supplier<String> blockLabel;
	private final Supplier<String> passLabel;
	private final Supplier<String> shadowLabel;

	private final List<ChainPlan.Attachment> extra;

	private final List<Slot> slots;

	private final List<Integer> shadowColours;

	private final boolean ownsFirst;

	private final boolean covers;

	private final boolean demoted;
	private final ColorTargets targets;
	private final ShadowTargets shadow;
	private final PackProgram.Loaded loaded;
	private final PackValues values;
	private final PackUniforms uniforms;
	private final List<String> samplers;
	private final List<String> storage;

	private final Sampled[] bound;

	private final Sampled[] following;
	private final Sampled[] settledOnce;
	private final RenderPipeline pipeline;
	private final ShaderSource source;

	private final Map<VertexFormat, RenderPipeline> reshaped = new HashMap<>();

	private final boolean gameTransforms;

	private MappableRingBuffer block;

	private GpuBufferSlice blockSlice;

	private GpuBuffer blockSliceOf;

	private int blockSliceBytes;

	private ConstantTextures constants;
	private GpuTextureView atlas;

	private Matrix4fc modelView;

	private Matrix4fc bob;

	private Matrix4fc projection;

	private Vector4fc passColour;
	private GpuSampler atlasSampler;
	private boolean announced;
	private boolean drew;
	private boolean broken;
	private boolean compiled;

	private boolean saidUnwritable;

	private List<String> unusableViews;

	private AheadCompiler.Ahead ahead;

	private boolean discarded;

	private final Map<ChainPlan.Attachment, Boolean> readWhileWritten;

	private final List<GpuTextureView> attachedViews = new ArrayList<>();
	private final List<GpuTextureView> shadowViews = new ArrayList<>();

	private RenderPass.RenderArea area;
	private int areaWidth;
	private int areaHeight;
	private RenderPass.RenderArea shadowArea;
	private int shadowAreaSize;

	GeometryProgram(Pass pass, PackProgram.Loaded loaded, PackValues values, int load,
			VertexFormat format, List<ChainPlan.Attachment> writes, ColorTargets targets,
			boolean chainRuns) {
		this.pass = pass;
		this.path = loaded.path();
		this.gameTransforms = loaded.readsGameTransforms();
		this.blockLabel = () -> "CoreVK " + pass.family() + " OfGlobals";
		String passName = "CoreVK " + pass.family();
		String shadowName = "CoreVK shadow " + pass.family();
		this.passLabel = () -> passName;
		this.shadowLabel = () -> shadowName;
		TranslatedUnit.Notes notes = loaded.program().stages().get(ProgramStage.FRAGMENT).notes();
		int outputs = notes.fragmentOutputs();

		boolean owns = chainRuns && !writes.isEmpty();
		this.covers = pass.covers() && covers(notes, writes.size(), chainRuns);

		this.ownsFirst = owns && (this.covers || pass.afterDeferred()
				|| (pass.blended() && pass.claimed()) || pass.distantVolume());

		this.demoted = owns && !this.ownsFirst && pass.blended() && !pass.afterDeferred();
		this.extra = this.ownsFirst
				? List.copyOf(writes)
				: writes.size() < 2 ? List.of() : List.copyOf(writes.subList(1, writes.size()));
		this.slots = pass.shadow() ? List.of() : attachments(targets, outputs);

		this.shadowColours = pass.shadow()
				? DrawBuffers.shadowColours(
						loaded.program().stages().get(ProgramStage.FRAGMENT).drawBuffers(),
						targets.shadow().colourCeiling()).stream()
						.limit(Math.clamp(outputs, 1, ColorTargetState.MAX_COLOR_TARGETS))
						.toList()
				: List.of();

		if (owns && pass.covers() && !this.covers) {

			if (notes.coverage() == 1) {

				CoreVK.logger().warn("{} writes {} draw buffers where the {} pass declares {} "
						+ "fragment outputs, so the coverage mask sits at a rank one of those draw "
						+ "buffers holds and is written over it: draw buffer nought stays on the "
						+ "game's target and the scene seed keeps painting the whole of it", this.path,
						writes.size(), pass.name(), outputs);
			} else {
				CoreVK.logger().warn("{} could not be given a coverage mask by the translation, so "
						+ "draw buffer nought of the {} pass stays on the game's target and the scene "
						+ "seed keeps painting the whole of it", this.path, pass.name());
			}
		}

		this.targets = targets;
		this.shadow = targets.shadow();
		this.loaded = loaded;
		this.values = values;

		this.uniforms = new PackUniforms(loaded.program().uniforms(),
				pass.shadow() ? values.shadowGeometryCatalog() : values.geometryCatalog());
		this.samplers = loaded.program().samplers().stream().map(TranslatedUnit.Uniform::name).toList();
		this.storage = loaded.storageBlocks().stream()
				.distinct()
				.filter(StorageBuffers::named)
				.toList();

		this.bound = this.samplers.stream()
				.map(name -> {
					SamplerPlan.Binding binding = loaded.samplers().binding(name);
					return new Sampled(name, material(name), ATLAS.contains(name), binding,
							binding.kind() == SamplerPlan.Kind.PACK_TEXTURE
									? targets.packSource(TextureStage.GBUFFERS, name)
									: null);
				})
				.toArray(Sampled[]::new);
		this.following = Arrays.stream(this.bound).filter(Sampled::followsTheImage)
				.toArray(Sampled[]::new);
		this.settledOnce = Arrays.stream(this.bound).filter(one -> !one.followsTheImage())
				.toArray(Sampled[]::new);
		this.readWhileWritten = readWhileWritten(targets);

		String vertex = loaded.program().stages().get(ProgramStage.VERTEX).text();

		String stem = "pack/" + load + "/" + pass.name() + "/" + this.path;
		Identifier vertexId = Identifier.fromNamespaceAndPath(pass.namespace(), stem + "/vertex");
		Identifier fragmentId = Identifier.fromNamespaceAndPath(pass.namespace(), stem + "/fragment");

		BindGroupLayout.Builder bindings = BindGroupLayout.builder()
				.withUniform(UNIFORM_BLOCK, UniformType.UNIFORM_BUFFER);

		if (pass.perDraw() != null) {
			bindings.withUniform(pass.perDraw(), UniformType.UNIFORM_BUFFER);
		}

		this.samplers.forEach(name -> GraphicsApi.withSampler(bindings, name));
		this.storage.forEach(name -> bindings.withUniform(name, UniformType.UNIFORM_BUFFER));

		RenderPipeline.Builder builder = RenderPipeline.builder()
				.withLocation(Identifier.fromNamespaceAndPath(pass.namespace(), "pipeline/" + stem))
				.withVertexShader(vertexId)
				.withFragmentShader(fragmentId)
				.withBindGroupLayout(bindings.build())
				.withPrimitiveTopology(pass.topology())
				.withCull(pass.cull());

		if (pass.namespace().contains("sodium")) {
			GraphicsApi.withSodiumPushConstants(builder);
		}

		if (pass.bindings() != null) {
			builder.withBindGroupLayout(pass.bindings());
		}

		if (this.gameTransforms) {
			builder.withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS);
		}

		if (format != null) {
			builder.withVertexBinding(0, format);
		}

		if (pass.depth() != null) {
			builder.withDepthStencilState(pass.depth());
		}

		if (pass.shadow()) {

			for (int slot = 0; slot < this.shadowColours.size(); slot++) {
				builder.withColorTargetState(slot,
						state(slot, targets.shadowFormat(this.shadowColours.get(slot))));
			}
		} else {
			for (int slot = 0; slot < this.slots.size(); slot++) {
				Slot one = this.slots.get(slot);
				switch (one.bound()) {

					case UNUSED -> builder.withUnusedColorTargetState(slot);

					case COVERAGE -> builder.withColorTargetState(slot, new ColorTargetState(
							Optional.empty(), one.format(), ColorTargetState.WRITE_ALL));
					default -> builder.withColorTargetState(slot, state(slot, one.format()));
				}
			}
		}

		this.pipeline = part(builder);

		ShadowCompare.note(this.pipeline, this.path, loaded);

		String fragment = GeometryStage.fragment(this.pipeline, this.path, loaded);
		if (fragment == null) {
			this.broken = true;
		}

		this.source = GraphicsApi.source((id, type) -> {
			if (type == ShaderType.FRAGMENT) {
				return fragmentId.equals(id) ? fragment : null;
			}

			return vertexId.equals(id) ? vertex : null;
		});

		List<String> storage = loaded.storageBlocks().stream()
				.distinct()
				.filter(name -> !StorageBuffers.named(name))
				.toList();
		if (!storage.isEmpty()) {
			this.broken = true;
			CoreVK.logger().error("{} declares the storage block {}, which nothing binds, so the "
					+ "{} pass keeps the game's own shader", this.path, String.join(", ", storage),
					pass.name());
		}
	}

	private List<Slot> attachments(ColorTargets targets, int outputs) {
		List<Slot> built = new ArrayList<>();
		if (!this.ownsFirst) {
			built.add(new Slot(Bound.GAME, null, GpuFormat.RGBA8_UNORM));
		}

		for (ChainPlan.Attachment attachment : this.extra) {
			built.add(new Slot(Bound.PACK, attachment, targets.format(attachment.target())));
		}

		if (this.covers) {
			while (built.size() < outputs) {
				built.add(new Slot(Bound.UNUSED, null, null));
			}

			built.add(new Slot(Bound.COVERAGE, null, targets.coverageFormat()));
		}

		return List.copyOf(built);
	}

	private ColorTargetState state(int rank, GpuFormat format) {
		BlendMode asked = BufferBlending.served()
				? this.targets.blend(this.loaded.path(), rank)
				: this.targets.blend(this.loaded.path());

		return new ColorTargetState(BlendFunctions.of(asked, this.pass.blend()), format,
				ColorTargetState.WRITE_ALL);
	}

	private static RenderPipeline part(RenderPipeline.Builder builder) {
		BufferBlending.building(true);
		try {
			return builder.build();
		} finally {
			BufferBlending.building(false);
		}
	}

	RenderPipeline prepare(GpuDevice device, GpuTextureView atlas) {
		return prepare(device, atlas, null, null, null, null);
	}

	RenderPipeline prepare(GpuDevice device, GpuTextureView atlas, Matrix4fc modelView,
			Matrix4fc bob, Vector4fc passColour, Matrix4fc projection) {
		this.modelView = modelView;
		this.bob = bob;
		this.passColour = passColour;
		this.projection = projection;
		if (this.broken) {
			return null;
		}

		if (this.pass.shadow() && this.shadow.depth() == null) {
			return null;
		}

		if (this.block == null || !ConstantTextures.ready()) {
			GeometryHold.flush(() -> "a program building or clearing its block");
		}

		if (!compile(device)) {
			return null;
		}

		if (this.unusableViews == null) {
			this.unusableViews = StorageImages.unusableViews(this.samplers);
			if (!this.unusableViews.isEmpty()) {
				CoreVK.logger().warn("{} reads {} through a view this device cannot sample and "
						+ "store in that format, so the game's own shader draws this pass", this.path,
						this.unusableViews);
			}
		}

		if (!this.unusableViews.isEmpty()) {
			return null;
		}

		this.atlas = atlas;
		ensureConstants(device);
		if (this.block == null) {
			this.block = new MappableRingBuffer(this.blockLabel,
					GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, blockBytes());
		}

		resolve();
		Sampled unwritable = Arrays.stream(this.bound)
				.filter(one -> one.binding.kind() == SamplerPlan.Kind.COLOUR_IMAGE && one.view == null)
				.findFirst().orElse(null);
		if (unwritable != null) {
			if (!this.saidUnwritable) {
				this.saidUnwritable = true;
				CoreVK.logger().warn("{} stores into {}, which was not created writable: this "
						+ "device makes no storage image of its format, or only a vertex stage "
						+ "names it, so the game's own shader draws this pass", this.path,
						unwritable.name);
			}

			return null;
		}

		announce();
		writeBlock();

		return this.pipeline;
	}

	synchronized boolean compile(GpuDevice device) {
		if (this.broken) {
			return false;
		}

		AheadCompiler.Ahead ready = this.ahead;
		if (ready != null) {
			this.ahead = null;
			if (!ready.adopt(device, this.pipeline)) {
				ready.destroy();
			}
		}

		CompiledRenderPipeline compiled = GraphicsApi.compile(device, this.pipeline, this.source);
		if (!GraphicsApi.valid(compiled)) {

			this.broken = true;

			CoreVK.logger().error("{} did not compile, so the {} pass keeps the game's own shader",
					this.path, this.pass.name());

			return false;
		}

		this.compiled = true;

		return true;
	}

	RenderPipeline reshape(GpuDevice device, VertexFormat layout) {

		synchronized (this) {
			if (this.broken) {
				return null;
			}

			VertexFormat own = this.pipeline.getVertexFormatBinding(0);
			if (layout.equals(own)) {
				return this.pipeline;
			}

			RenderPipeline variant;
			if (this.reshaped.containsKey(layout)) {
				variant = this.reshaped.get(layout);
			} else {
				variant = rebuild(layout, own);
				this.reshaped.put(layout, variant);
			}

			if (variant == null) {
				return null;
			}

			RuntimeException thrown = null;
			boolean valid;
			try {
				valid = GraphicsApi.valid(GraphicsApi.compile(device, variant, this.source));
			} catch (GpuDeviceLossException e) {
				throw e;
			} catch (RuntimeException e) {

				thrown = e;
				valid = false;
			}

			if (!valid) {

				this.reshaped.put(layout, null);
				CoreVK.logger().error("{} did not compile over a mesh layout brought by another "
						+ "mod, so the {} pass draws that mesh through its own layout", this.path,
						this.pass.name(), thrown);

				return null;
			}

			return variant;
		}
	}

	private RenderPipeline rebuild(VertexFormat layout, VertexFormat own) {
		List<VertexFormatElement> lead = own.getElements();
		List<VertexFormatElement> given = layout.getElements();
		boolean leads = given.size() >= lead.size();
		for (int index = 0; leads && index < lead.size(); index++) {
			leads = lead.get(index).name().equals(given.get(index).name());
		}

		if (!leads) {
			CoreVK.logger().warn("A mesh brought by another mod does not lead with the attributes "
					+ "the {} pass reads, so its draws keep the pass pipeline's own layout: attribute "
					+ "locations are numbered over every element of a layout, and a stranger before "
					+ "a known one would make the shader read the wrong attribute without a word",
					this.pass.name());

			return null;
		}

		try {
			return reshapeAs(layout, this.reshaped.size());
		} catch (RuntimeException e) {
			CoreVK.logger().error("The " + this.pass.name() + " pass could not rebuild its "
					+ "pipeline over a mesh layout brought by another mod, so those draws keep the "
					+ "pipeline's own layout", e);

			return null;
		}
	}

	private RenderPipeline reshapeAs(VertexFormat layout, int index) {
		RenderPipeline.Builder builder = RenderPipeline.builder()
				.withLocation(this.pipeline.getLocation().withSuffix("/reshaped/" + index))
				.withVertexShader(GraphicsApi.vertexShader(this.pipeline))
				.withFragmentShader(GraphicsApi.fragmentShader(this.pipeline))
				.withCull(this.pipeline.isCull())
				.withPrimitiveTopology(this.pipeline.getPrimitiveTopology())
				.withVertexBinding(0, layout);
		this.pipeline.getBindGroupLayouts().forEach(builder::withBindGroupLayout);
		GraphicsApi.copyPushConstants(builder, this.pipeline);
		if (this.pipeline.getDepthStencilState() != null) {
			builder.withDepthStencilState(this.pipeline.getDepthStencilState());
		}

		List<@Nullable ColorTargetState> states = GraphicsApi.colorTargets(this.pipeline);
		for (int slot = 0; slot < states.size(); slot++) {
			ColorTargetState state = states.get(slot);
			if (state == null) {
				builder.withUnusedColorTargetState(slot);
			} else {
				builder.withColorTargetState(slot, state);
			}
		}

		RenderPipeline built = part(builder);

		ShadowCompare.noteBeside(built, this.pipeline);
		GeometryStage.noteBeside(built, this.pipeline);

		return built;
	}

	boolean compiled() {
		return this.compiled || this.broken;
	}

	void forgetCompiled() {
		this.compiled = false;
	}

	boolean warmAhead(AheadCompiler compiler) {
		synchronized (this) {
			if (this.compiled || this.broken || this.discarded || this.ahead != null) {
				return false;
			}
		}

		AheadCompiler.Ahead built;
		try {
			built = compiler.build(this.pipeline, this.source);
		} catch (AheadCompiler.Refused e) {

			CoreVK.logger().info("{} stays for its first draw: {}", this.path, e.getMessage());

			return false;
		}

		synchronized (this) {
			if (this.compiled || this.broken || this.discarded) {
				built.destroy();

				return false;
			}

			this.ahead = built;
		}

		return true;
	}

	synchronized void discardAhead() {
		this.discarded = true;
		if (this.ahead != null) {
			this.ahead.destroy();
			this.ahead = null;
		}
	}

	@SuppressWarnings("ReferenceEquality")
	void bind(RenderPass pass) {

		if (!this.drew) {
			this.drew = true;

			CoreVK.logger().info("The {} pass records its first draw with {}{}",
					this.pass.name(), this.path,
					this.samplers.stream().anyMatch(ATLAS::contains)
							? ", reading " + GameImages.name(this.atlas) + " where it asks for the atlas"
							: "");
		}

		pass.setUniform(UNIFORM_BLOCK, blockSlice());
		StorageBuffers.bind(pass, this.storage);

		boolean settle = settledIn != pass || settled != this;
		settledIn = pass;
		settled = this;

		for (Sampled one : this.following) {
			GraphicsApi.bindTexture(pass, one.name, imageView(one), imageSampler(one));
		}

		if (settle) {
			for (Sampled one : this.settledOnce) {
				GraphicsApi.bindTexture(pass, one.name, one.view, one.state);
			}
		}
	}

	private void resolve() {

		settledIn = null;
		settled = null;
		boolean labPbr = PbrAtlases.labPbr();
		for (Sampled one : this.bound) {
			one.interpolates = one.material != null && one.material.interpolates(labPbr);
			one.view = passView(one);
			one.state = passSampler(one);
			one.servedKnown = false;
			one.servedFor = null;
			one.served = null;
		}
	}

	private GpuTextureView imageView(Sampled one) {
		if (one.material != null) {

			if (!one.servedKnown || one.servedFor != this.atlas) {
				GpuTextureView served = PbrAtlases.view(this.atlas, one.material);
				if (served == null) {
					served = PbrTextures.view(this.atlas, one.material);
				}

				one.servedFor = this.atlas;
				one.served = served;
				one.servedKnown = true;
			}

			return one.served == null ? one.view : one.served;
		}

		return this.atlas == null ? one.view : this.atlas;
	}

	private GpuSampler imageSampler(Sampled one) {
		if (this.atlasSampler == null) {
			return one.state;
		}

		return one.material == null || one.interpolates ? this.atlasSampler : one.state;
	}

	private GpuSampler passSampler(Sampled one) {
		String name = one.name;
		if (one.material != null) {

			return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST, true);
		}

		if (LIGHTMAP.equals(name)) {
			return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
		}

		if (OVERLAY.equals(name)) {
			return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
		}

		SamplerPlan.Kind kind = one.binding.kind();
		if (one.source != null && this.targets.packView(one.source.image()) != null) {

			return PackPass.sampler(one.source.repeat(), one.source.filter(), one.source.mipmaps());
		}

		return PackPass.sampler(kind, filter(name), mipmapped(name, kind));
	}

	void sampler(GpuSampler sampler) {
		this.atlasSampler = sampler;
	}

	void atlas(GpuTextureView view) {
		this.atlas = view;
	}

	@SuppressWarnings("ReferenceEquality")
	boolean owns(RenderPipeline bound) {
		return this.pipeline == bound;
	}

	boolean servable() {
		return !this.broken;
	}

	boolean covers() {
		return this.covers;
	}

	static boolean covers(TranslatedUnit.Notes notes, int attachments, boolean chainRuns) {
		return chainRuns && attachments > 0 && notes.coverage() == 1
				&& attachments <= notes.fragmentOutputs()
				&& notes.fragmentOutputs() < ColorTargetState.MAX_COLOR_TARGETS;
	}

	RenderPassDescriptor descriptor(GpuTextureView colour, GpuTextureView depth) {

		if (this.broken) {
			return null;
		}

		if (this.pass.shadow()) {
			return shadowDescriptor();
		}

		if (plain()) {
			if (this.targets.hasPendingClears()) {
				this.targets.flushPending(RenderSystem.getDevice().createCommandEncoder());
			}

			return null;
		}

		this.attachedViews.clear();
		for (Slot slot : this.slots) {
			if (slot.bound() == Bound.UNUSED) {
				this.attachedViews.add(null);
				continue;
			}

			GpuTextureView view = view(slot, colour);
			if (view == null) {

				return null;
			}

			this.attachedViews.add(view);
		}

		PassDescriptor descriptor = PassDescriptor.create(this.passLabel);
		for (GpuTextureView view : this.attachedViews) {
			if (view == null) {
				descriptor.withUnusedColorAttachment();
				continue;
			}

			descriptor.withColorAttachment(view, this.targets.takeClear(view));
		}

		if (this.targets.hasPendingClears()) {
			this.targets.flushPending(RenderSystem.getDevice().createCommandEncoder());
		}

		descriptor.withRenderArea(area(this.targets.screenWidth(), this.targets.screenHeight()));

		return (depth == null ? descriptor : descriptor.withDepthAttachment(depth)).build();
	}

	boolean plain() {
		return this.slots.size() == 1 && this.slots.get(0).bound() == Bound.GAME;
	}

	boolean readsGameTransforms() {
		return this.gameTransforms;
	}

	private GpuTextureView view(Slot slot, GpuTextureView colour) {
		return switch (slot.bound()) {
			case GAME -> colour;
			case PACK -> this.targets.view(slot.target().target(), slot.target().side());
			case COVERAGE -> this.targets.coverage();
			case UNUSED -> null;
		};
	}

	private RenderPassDescriptor shadowDescriptor() {

		GpuTextureView depth = this.shadow.depthAttachment();
		if (depth == null) {
			return null;
		}

		this.shadowViews.clear();
		for (int index : this.shadowColours) {

			GpuTextureView colour = this.shadow.colour(index);
			if (colour == null) {
				return null;
			}

			this.shadowViews.add(colour);
		}

		PassDescriptor descriptor = PassDescriptor.create(this.shadowLabel);
		int at = 0;
		for (int index : this.shadowColours) {
			descriptor.withColorAttachment(this.shadowViews.get(at), this.shadow.takeColourClear(index));
			at++;
		}

		descriptor.withDepthAttachment(depth, this.shadow.takeDepthClear())
				.withRenderArea(shadowArea());
		this.shadow.flushPending(RenderSystem.getDevice().createCommandEncoder());
		return descriptor.build();
	}

	private RenderPass.RenderArea area(int width, int height) {
		if (this.area == null || width != this.areaWidth || height != this.areaHeight) {
			this.areaWidth = width;
			this.areaHeight = height;
			this.area = new RenderPass.RenderArea(0, 0, width, height);
		}

		return this.area;
	}

	private RenderPass.RenderArea shadowArea() {
		int size = this.shadow.resolution();
		if (this.shadowArea == null || size != this.shadowAreaSize) {
			this.shadowAreaSize = size;
			this.shadowArea = new RenderPass.RenderArea(0, 0, size, size);
		}

		return this.shadowArea;
	}

	void rotate() {
		if (this.block != null) {
			this.block.rotate();
		}
	}

	String decoded(WorldState world) {
		TextSink sink = new TextSink();
		this.uniforms.write(sink, world);

		return sink.text();
	}

	String path() {
		return this.path;
	}

	String label() {
		return this.path + " " + this.pass.name();
	}

	void release() {
		if (this.block != null) {
			this.block.close();
			this.block = null;
		}

		this.blockSlice = null;
		this.blockSliceOf = null;

		this.constants = null;

		for (Sampled one : this.bound) {
			one.view = null;
			one.state = null;
			one.servedFor = null;
			one.served = null;
			one.servedKnown = false;
		}

		if (settled == this) {
			settled = null;
			settledIn = null;
		}
	}

	private PbrMap material(String sampler) {
		PbrMap named = PbrMap.named(sampler);
		if (named == null) {
			return null;
		}

		return this.loaded.samplers().binding(sampler).kind() == SamplerPlan.Kind.PACK_TEXTURE
				? null
				: named;
	}

	private int blockBytes() {
		return Math.max(16, this.uniforms.size());
	}

	private GpuBufferSlice blockSlice() {
		GpuBuffer buffer = this.block.currentBuffer();
		if (PassTimings.keepRedoneWork()) {
			PassTimings.censusSlice();

			return buffer.slice(0, blockBytes());
		}

		int bytes = blockBytes();

		if (this.blockSlice == null || this.blockSliceOf != buffer || this.blockSliceBytes != bytes) {
			PassTimings.censusSlice();
			this.blockSlice = buffer.slice(0, bytes);
			this.blockSliceOf = buffer;
			this.blockSliceBytes = bytes;
		}

		return this.blockSlice;
	}

	private void writeBlock() {

		this.values.convention(this.pass.shadow() ? ClipSpace.FORWARD : ClipSpace.REVERSED);
		this.values.modelView(this.modelView, this.bob);
		this.values.projection(this.projection);
		this.values.passColour(this.passColour);
		this.values.passAlphaTest(this.loaded.alphaTest().reference());
		this.values.renderStage(this.pass.stage());

		try (GpuBufferSlice.MappedView view = this.block.currentBuffer().map(false, true)) {
			ByteBuffer data = view.data();
			data.position(0);
			this.uniforms.write(Std140Builder.intoBuffer(data), this.values.world());
		}
	}

	private void ensureConstants(GpuDevice device) {
		this.constants = ConstantTextures.of(device);
	}

	private GpuTextureView passView(Sampled one) {
		String sampler = one.name;
		if (one.material != null) {

			return this.constants.flat(one.material);
		}

		if (one.albedo) {

			return this.constants.white();
		}

		if (OVERLAY.equals(sampler)) {

			Minecraft minecraft = Minecraft.getInstance();
			GpuTextureView overlay = minecraft == null
					? null : minecraft.gameRenderer.overlayTexture().getTextureView();

			return overlay == null ? this.constants.black() : overlay;
		}

		if (LIGHTMAP.equals(sampler)) {

			if (this.loaded.fullbright()) {
				return this.constants.white();
			}

			Minecraft minecraft = Minecraft.getInstance();
			GpuTextureView lightmap = minecraft == null ? null : minecraft.gameRenderer.lightmap();

			return lightmap == null ? this.constants.white() : lightmap;
		}

		SamplerPlan.Binding binding = one.binding;

		return switch (binding.kind()) {
			case COLORTEX -> colortex(binding);
			case DEPTH -> depth(sampler);
			case SHADOW_DEPTH -> shadowDepth(binding.sampler());
			case SHADOW_COLOUR -> shadowColour(binding.index());
			case NOISE -> this.targets.noise();

			case PACK_TEXTURE -> packTexture(one);
			case DISTANT_DEPTH -> distantDepth();

			case COLOUR_IMAGE -> colourImage(binding);
			default -> this.constants.black();
		};
	}

	private GpuTextureView colourImage(SamplerPlan.Binding binding) {
		TargetSurface surface = this.targets.surface(binding.index(), binding.side());

		return surface == null || !surface.storage() ? null : surface.storageView();
	}

	private GpuTextureView packTexture(Sampled one) {
		GpuTextureView supplied = one.source == null ? null : this.targets.packView(one.source.image());

		return supplied == null ? this.constants.black() : supplied;
	}

	private GpuTextureView shadowDepth(String sampler) {
		if (this.pass.shadow()) {
			return this.constants.farPlane();
		}

		GpuTextureView map = this.loaded.samplers().withoutTranslucents(sampler)
				? this.shadow.depthWithoutTranslucents()
				: this.shadow.depth();

		return map == null ? this.constants.farPlane() : map;
	}

	private GpuTextureView shadowColour(int index) {
		if (this.pass.shadow()) {
			return this.constants.white();
		}

		GpuTextureView view = this.shadow.colour(index);

		return view == null ? this.constants.white() : view;
	}

	private GpuTextureView depth(String sampler) {
		if (SamplerPlan.preHandCopy(sampler)) {
			GpuTextureView preHand = this.targets.depth().preHand();
			if (preHand != null) {
				return preHand;
			}
		}

		if (this.pass.afterDeferred() || SamplerPlan.depthCopy(sampler)) {
			GpuTextureView opaque = this.targets.depth().opaque();
			if (opaque != null) {
				return opaque;
			}
		}

		return this.constants.white();
	}

	private GpuTextureView distantDepth() {
		if (this.pass.afterDeferred()) {
			GpuTextureView distant = this.targets.depth().distantOpaque();
			if (distant != null) {
				return distant;
			}
		}

		return this.constants.white();
	}

	private Map<ChainPlan.Attachment, Boolean> readWhileWritten(ColorTargets targets) {
		Set<String> sampled = this.loaded.program().sampled();
		Map<ChainPlan.Attachment, Boolean> collided = new HashMap<>();
		for (ChainPlan.Attachment attachment : this.extra) {
			if (collided.containsKey(attachment)) {
				continue;
			}

			boolean named = false;
			boolean read = false;
			for (Sampled one : this.bound) {
				SamplerPlan.Binding binding = one.binding;
				if (binding.kind() == SamplerPlan.Kind.COLORTEX
						&& binding.index() == attachment.target()
						&& binding.side() == attachment.side()) {

					named = true;
					read |= sampled.contains(one.name);
				}
			}

			if (!named) {
				continue;
			}

			boolean copied = read && this.pass.afterDeferred()
					&& attachment.target() >= TargetCopies.FIRST_SAMPLED;
			collided.put(attachment, copied);
			if (copied) {
				targets.copies().ask(attachment.target(), attachment.side());
			}

			if (!read
					|| !targets.copies().firstMention(this.path, attachment.target(), attachment.side())) {
				continue;
			}

			if (copied) {
				CoreVK.logger().info("{} reads {} on the half it writes, so it is served a copy "
						+ "taken ahead of the pass, which is what the target holds when the "
						+ "reference reads it there", this.path,
						TargetName.canonical(attachment.target()));
			} else if (attachment.target() < TargetCopies.FIRST_SAMPLED) {
				CoreVK.logger().warn("{} reads {} on the half it writes, so it is answered with "
						+ "one pixel: one image cannot be both an attachment and a texture of one "
						+ "pass, and the reference binds none of the first four targets to a "
						+ "geometry program", this.path, TargetName.canonical(attachment.target()));
			} else {
				CoreVK.logger().warn("{} reads {} on the half it writes, so it is answered with "
						+ "one pixel: one image cannot be both an attachment and a texture of one "
						+ "pass, and a pass drawn before the deferred stage has no picture a copy "
						+ "could stand in for", this.path, TargetName.canonical(attachment.target()));
			}
		}

		return Map.copyOf(collided);
	}

	private boolean collides(SamplerPlan.Binding binding) {
		return binding.kind() == SamplerPlan.Kind.COLORTEX && this.readWhileWritten.containsKey(
				new ChainPlan.Attachment(binding.index(), binding.side()));
	}

	private boolean copied(SamplerPlan.Binding binding) {
		return binding.kind() == SamplerPlan.Kind.COLORTEX && this.readWhileWritten.getOrDefault(
				new ChainPlan.Attachment(binding.index(), binding.side()), false);
	}

	private GpuTextureView colortex(SamplerPlan.Binding binding) {
		if (collides(binding)) {
			GpuTextureView copy = copied(binding)
					? this.targets.copies().view(binding.index(), binding.side())
					: null;

			return copy == null ? this.constants.black() : copy;
		}

		GpuTextureView view = this.targets.view(binding.index(), binding.side());

		return view == null ? this.constants.black() : view;
	}

	private String describe(String sampler) {
		SamplerPlan.Binding binding = this.loaded.samplers().binding(sampler);
		if (binding.kind() != SamplerPlan.Kind.COLORTEX) {
			return sampler;
		}

		return sampler + "=" + TargetName.canonical(binding.index()) + " " + binding.side()
				+ (copied(binding) ? " copy" : "");
	}

	private boolean readsATexture(String sampler) {

		if (material(sampler) == null && PbrMap.named(sampler) != null) {
			return this.targets.packTexture(TextureStage.GBUFFERS, sampler) != null;
		}

		PbrMap material = material(sampler);
		if (material != null) {

			return PbrAtlases.supplies(material);
		}

		SamplerPlan.Binding binding = this.loaded.samplers().binding(sampler);
		SamplerPlan.Kind kind = binding.kind();

		if (kind == SamplerPlan.Kind.PACK_TEXTURE) {
			return this.targets.packTexture(TextureStage.GBUFFERS, sampler) != null;
		}

		return ATLAS.contains(sampler) || LIGHTMAP.equals(sampler) || OVERLAY.equals(sampler)

				|| (kind == SamplerPlan.Kind.COLORTEX && (!collides(binding) || copied(binding)))
				|| kind == SamplerPlan.Kind.CUSTOM_IMAGE
				|| kind == SamplerPlan.Kind.NOISE
				|| (kind == SamplerPlan.Kind.DEPTH
						&& (this.pass.afterDeferred() || SamplerPlan.depthCopy(sampler)))

				|| (!this.pass.shadow() && kind == SamplerPlan.Kind.SHADOW_DEPTH
						&& this.shadow.depth() != null)
				|| (!this.pass.shadow() && kind == SamplerPlan.Kind.SHADOW_COLOUR
						&& this.shadow.colour(binding.index()) != null);
	}

	private boolean readsTheDistantDepth(String sampler) {
		return this.loaded.samplers().binding(sampler).kind() == SamplerPlan.Kind.DISTANT_DEPTH;
	}

	private boolean mipmapped(String sampler, SamplerPlan.Kind kind) {
		return kind == SamplerPlan.Kind.SHADOW_DEPTH && this.targets.shadow()
				.depthMipmapped(this.loaded.samplers().withoutTranslucents(sampler));
	}

	private FilterMode filter(String sampler) {
		if (LIGHTMAP.equals(sampler)) {
			return FilterMode.LINEAR;
		}

		SamplerPlan.Binding binding = this.loaded.samplers().binding(sampler);
		if (binding.kind() == SamplerPlan.Kind.COLORTEX) {
			return this.targets.filter(binding.index());
		}

		return switch (binding.kind()) {
			case NOISE, SHADOW_COLOUR -> FilterMode.LINEAR;
			case SHADOW_DEPTH -> this.targets.shadow()
					.depthFilter(this.loaded.samplers().withoutTranslucents(sampler));
			case CUSTOM_IMAGE -> PackPass.customImageFilter(sampler);
			default -> FilterMode.NEAREST;
		};
	}

	private void announce() {
		if (this.announced) {
			return;
		}

		this.announced = true;
		TranslatedUnit fragment = this.loaded.program().stages().get(ProgramStage.FRAGMENT);
		int outputs = fragment.notes().fragmentOutputs();

		CoreVK.logger().info("Drawing the {} {} pass with {} of {} at render stage {}, {} uniforms "
				+ "and {} samplers", this.pass.name(), this.pass.family(), this.path,
				this.loaded.packName(), this.pass.stage(), this.loaded.program().uniforms().size(),
				this.samplers.size());

		AlphaTest alphaTest = this.loaded.alphaTest();
		if (alphaTest.tests() && fragment.notes().alphaEpilogue() == 0) {
			CoreVK.logger().warn("This pass discards at {} {} and the program could not be given the "
					+ "test, so nothing beyond the pack's own discards is thrown away",
					alphaTest.function(), alphaTest.reference());
		}

		List<String> constants = this.loaded.program().synthesized().keySet().stream()
				.filter(name -> !this.pass.answered().contains(name))
				.toList();
		if (!constants.isEmpty()) {
			CoreVK.logger().warn("The {} mesh carries none of these, so they are answered with a "
					+ "constant and what this program computes from them is wrong: {}",
					this.pass.family(), constants);
		}

		List<String> real = this.samplers.stream()
				.filter(this::readsATexture)
				.map(this::describe)
				.toList();

		List<String> distant = this.samplers.stream().filter(this::readsTheDistantDepth).toList();
		List<String> flat = this.samplers.stream()
				.filter(name -> !readsATexture(name) && !readsTheDistantDepth(name))
				.toList();
		CoreVK.logger().info("{} samplers of this program read a real texture: {}", real.size(), real);
		if (!distant.isEmpty()) {
			CoreVK.logger().info("{} read the far terrain's own depth on the frames this pack draws "
					+ "it, and the far plane on the rest: {}", distant.size(), distant);
		}

		if (!flat.isEmpty()) {

			CoreVK.logger().warn("{} read one pixel, because nothing fills them yet: {}",
					flat.size(), flat);
		}

		if (this.pass.shadow()) {
			CoreVK.logger().info("It draws into the shadow map, {}x{}, storing the forward depth "
					+ "window, and into {}", this.shadow.resolution(), this.shadow.resolution(),
					this.shadowColours.stream()
							.map(index -> "shadowcolor" + index)
							.collect(Collectors.joining(", ")));
			if (outputs > this.shadowColours.size()) {
				CoreVK.logger().warn("{} declares {} fragment outputs and its draw buffers name {} "
						+ "of the light's colour targets, so the outputs past the {} are written "
						+ "nowhere", this.path, outputs, this.shadowColours.size(),
						this.shadowColours.size());
			}

			List<Integer> declared = fragment.drawBuffers();
			int ceiling = this.shadow.colourCeiling();
			if (declared.stream().anyMatch(index -> index >= ceiling)) {
				CoreVK.logger().warn("{} asks for {}, and this pack may draw the light into {} "
						+ "colour targets, so the whole list is set aside and the first {} are drawn "
						+ "into instead, as the reference does with it. A pack reaches the eight by "
						+ "declaring HIGHER_SHADOWCOLOR", this.path, declared, ceiling,
						this.shadowColours.size());
			}
		} else if (this.ownsFirst) {

			CoreVK.logger().info("Its draw buffers all reach the pack's own targets, nought "
					+ "included: {}",
					this.extra.stream()
							.map(one -> TargetName.canonical(one.target()) + " " + one.side())
							.toList());
			if (this.covers) {

				CoreVK.logger().info("It also writes the coverage mask, so nothing paints the game's "
						+ "own picture back over what this pass wrote");
			}
		} else if (this.extra.isEmpty()) {

			if (outputs > 1) {
				CoreVK.logger().warn("{} declares {} fragment outputs and writes one draw buffer, so "
						+ "all but the first are written nowhere", this.path, outputs);
			}
		} else {
			CoreVK.logger().info("Its other draw buffers reach the pack's own targets: {}",
					this.extra.stream()
							.map(one -> TargetName.canonical(one.target()) + " " + one.side())
							.toList());
		}

		if (this.demoted) {
			CoreVK.logger().info("It blends and draw buffer nought still goes to the game's own "
					+ "target: this pass is drawn before the scene seed and nothing marks the pixels "
					+ "it blends onto, so the seed would paint the game's picture back over them");
		}

		List<String> hardware = this.loaded.program().stages().values().stream()
				.flatMap(unit -> unit.notes().hardwareCompared().stream())
				.distinct()
				.toList();
		if (!hardware.isEmpty()) {
			CoreVK.logger().info("{} asked the hardware to compare {}, which the binding carries "
					+ "as a comparison sampler", this.path, hardware);
		}

		List<String> compared = this.loaded.program().stages().values().stream()
				.flatMap(unit -> unit.notes().comparedSamplers().stream())
				.distinct()
				.filter(name -> !hardware.contains(name))
				.toList();
		if (!compared.isEmpty()) {
			CoreVK.logger().info("{} asked the hardware to compare {}, which this binding does not "
					+ "carry, so the comparison is made in the shader over the four texels the "
					+ "hardware would have blended", this.path, compared);
		}

		PackValues.Gaps gaps = this.values.classify(this.uniforms.unsupplied());
		if (!gaps.engine().isEmpty()) {
			CoreVK.logger().warn("{} reads {} values written as zeroes: {}", this.path,
					gaps.engine().size(), gaps.engine());
		}

		if (!gaps.nobody().isEmpty()) {
			CoreVK.logger().info("{} reads {} values no engine answers, CoreVK included, so they are "
					+ "zeroes there too: {}", this.path, gaps.nobody().size(), gaps.nobody());
		}

		PackValues.standIns(this.loaded.program().uniforms().stream()
						.map(TranslatedUnit.Uniform::name)
						.toList())
				.forEach((reason, names) -> CoreVK.logger().warn("{} reads {} values answered with "
						+ "a stand-in rather than with the value, {}, which count as supplied "
						+ "everywhere else, because {}", this.path, names.size(), names, reason));
	}
}
