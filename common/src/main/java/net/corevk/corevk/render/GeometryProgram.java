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

		// Draw buffer nought goes to the pack, on all three halves of the world, and the whole cost
		// of that is that somebody else has to stop painting over it.
		//
		// The translucent half has needed it from the start. It is drawn AFTER the seed has run, so
		// the pack's own colour target already holds the opaque world, and that is exactly what a
		// gbuffers_water expects to blend onto. Sent to the game's target instead, the water is
		// drawn and then thrown away: the final overwrites that target with the image the chain
		// composed out of a colortex the water never reached.
		//
		// Keeping every opaque half on the game's target and reaching the pack's colortex through
		// the seed is one conversion too many. What a gbuffers program puts in draw
		// buffer nought is not a colour but whatever the pack packed there, and the game's target is
		// eight bits a channel: Bliss packs two values into each channel of a sixteen bit colortex1,
		// and the trip through the game's target quantised its albedo away entirely, leaving the
		// encoded normal to be read back as the albedo. So the opaque halves write their target
		// outright, and the coverage mask below is what keeps the seed off the pixels they wrote.
		// The entities came last of them, and only once the mask carried a DEPTH: while it was a
		// flag, the cut compared the world's depth with one taken before a single feature was drawn,
		// and a mob standing in front of a block moved that depth by construction, so no mask it
		// wrote could have kept the seed off it.
		//
		// Everything that lands back on the game's target, and it is a list of reasons rather than a
		// count. When the chain is not running there is no final to bring a colortex to the screen,
		// so anything sent there would simply vanish; when the plan had no answer there is nowhere
		// else to send it; when a half that ASKED for a mask could not be given one, the seed would
		// repaint the whole target and take the geometry with it; when a half never asked for one,
		// which is the weather alone, the seed carries it in by design; and the
		// last is what the statement after next is about, a blending pass drawn before the seed with
		// nothing marking the pixels it blends onto. Either way the pass draws where Sodium would
		// have, which is also what keeps the pipeline's one state the pass's.
		//
		// Whether the mask was really written is the translation's answer and not a second reading
		// of the same rule: the stage that could not be given one says so, and an engine that
		// decided for itself would be attaching an image nothing fills.
		boolean owns = chainRuns && !writes.isEmpty();
		this.covers = pass.covers() && covers(notes, writes.size(), chainRuns);
		// A blending pass may take draw buffer nought outright only where the seed will not repaint
		// the pixels it blended onto, and blending is not that question: it was read as though it
		// were, and every family answered the same either way until the hand arrived.
		//
		// Three ways the seed is kept off. The pass is drawn AFTER the seed, which is the world's
		// water, the weather, the clouds, the translucent particles, the blending half of the
		// entities and the hand's own water pass. It writes the mask itself, which is what covers
		// above answers, and which the hand's solid pass now does with the rest of the door. Or the
		// family draws opaque pieces of its own over the same pixels, which is the sky alone and
		// which claimed carries, with the two places it does not hold named where that field is
		// declared.
		//
		// The hand came last of the three roads and by the second, and what had made it impossible
		// there was the flag. The cut then asked whether the depth had moved closer since a copy
		// taken before the game's features, and the hand is drawn with its clip depth squeezed into
		// the band 0.4375 to 0.5625 (render/HandDraw.java:93,382), which is not the depth of anything
		// it stands in front of: every hand pixel answered that yes, whatever mask was written there.
		// The mask carries the depth now, so a hand row writes that same squeezed value and the cut
		// compares it with itself.
		//
		// AND IT IS WHERE COREVK HAS IT. CoreVK binds every gbuffers program to a framebuffer over the
		// pack's own declared draw buffers, the hand included
		// (pipeline/CoreVKRenderingPipeline.java:686-687; the four keys its hand passes ask for are at
		// pipeline/CoreVKPipelines.java:192,204,216), so its hand colour is written to the pack's
		// target and never leaves it. Three things were paid for the trip through the game's target
		// while this pass was still making it, and they are what the mask buys back. The trip through
		// eight bits a channel, which is the quantisation the Bliss paragraph above measured on the
		// terrain. The blend, which happened against the game's target: with the chain running the
		// world is in the PACK's target, so a hand pixel of alpha under one blended against the clear
		// rather than against what stands behind it. And a row that writes no depth with nothing of
		// its own pass writing depth under it - a held banner's pattern is the reachable one
		// (BANNER_PATTERN, RenderPipelines.java:318) - which the cut discarded outright, the mask and
		// the world's depth both holding what the geometry behind the hand left.
		// The far terrain owns its first draw buffer whichever half it falls on, and it is the one
		// family that owns it without a mask and without being drawn after the seed. CoreVK binds its
		// dh programs to a framebuffer over the pack's own declared draw buffers, exactly as it
		// binds every gbuffers program (compat/dh/DHCompatInternal.java:92 building
		// createDHFramebuffer over the terrain source), so the pack's colour never makes the trip
		// through the game's target there. And the seed cannot be this family's road at all: the
		// pack's sky claims the whole horizon with a depth of its own - the disc's, and the cone's
		// below it - which stands nearer than any far terrain, so the cut refuses the transport and
		// what the seed target keeps where the far terrain stands is the sky. What keeps the seed
		// OFF the pixels this family writes is that same claim, the world's depth holding the far
		// plane there: a game feature really drawn in front still comes in over it, its depth being
		// nearer than the sky's claim.
		this.ownsFirst = owns && (this.covers || pass.afterDeferred()
				|| (pass.blended() && pass.claimed()) || pass.distantVolume());
		// The demotion just above and none of the ones before it, which is why owns and the side are
		// both in it: without owns this would answer yes for every blending pass in a place where
		// the chain does not run or the plan had no attachments to give, and then say of the water,
		// the clouds and the weather that they are drawn before a seed they are drawn after - or
		// before a seed that is never painted at all. Nothing tests the shadow map here because
		// nothing needs to: no pass drawn from the light carries a blend of its own, whatever a
		// pack's own directive later puts on its attachments.
		this.demoted = owns && !this.ownsFirst && pass.blended() && !pass.afterDeferred();
		this.extra = this.ownsFirst
				? List.copyOf(writes)
				: writes.size() < 2 ? List.of() : List.copyOf(writes.subList(1, writes.size()));
		this.slots = pass.shadow() ? List.of() : attachments(targets, outputs);
		// Never fewer than one, whatever the fragment stage declares. Mellow's shadow program writes
		// no output at all - its whole body is one discard test, so the count is nought - and a pass
		// with no colour attachment is one the encoder accepts on the strength of its depth while
		// the pipeline substitutes a state of its own, which is the mismatch this file refuses by
		// name elsewhere. It draws into the map and into nought, exactly as it would without this rule.
		//
		// And never more than a pipeline holds states for. The builder writes them into an array of
		// that length, so one rank past it is an index out of bounds where the program is built
		// rather than a refusal that names it. A directive can reach it: the indices run together
		// and DRAWBUFFERS:000000000 parses.
		this.shadowColours = pass.shadow()
				? DrawBuffers.shadowColours(
						loaded.program().stages().get(ProgramStage.FRAGMENT).drawBuffers(),
						targets.shadow().colourCeiling()).stream()
						.limit(Math.clamp(outputs, 1, ColorTargetState.MAX_COLOR_TARGETS))
						.toList()
				: List.of();

		// Said here rather than in announce(), because it is a property of the text and not of a
		// frame, and because what it costs is invisible: the pass then draws exactly as it did
		// before the mask existed, and it is Bliss's albedo that pays for it.
		if (owns && pass.covers() && !this.covers) {
			// Split by cause, and the split is not where the predicate above suggests. The
			// translation refuses to place a mask at eight outputs, which is the same eight a
			// pipeline carries, so a stage that is full and a stage that could not be given one are
			// the same case and both belong below. What is left on this side is a pack writing more
			// draw buffers than its fragment stage declares outputs, and the numbers describe that
			// and nothing else.
			if (notes.coverage() == 1) {
				// Present tense, and it is worse than a lost mask: the translation was asked for one
				// by the family and placed it one rank above the outputs, so the fragment writes it
				// at a rank this attachment list fills with a colour target of the pack. Nothing
				// downgrades on that side, and what the pack meant to write there is written over.
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
		// A shadow pass is drawn from the light, so the six fixed function names answer the shadow
		// pair. Everything else in the table is the frame's and is shared with the world.
		this.uniforms = new PackUniforms(loaded.program().uniforms(),
				pass.shadow() ? values.shadowGeometryCatalog() : values.geometryCatalog());
		this.samplers = loaded.program().samplers().stream().map(TranslatedUnit.Uniform::name).toList();
		this.storage = loaded.storageBlocks().stream()
				.distinct()
				.filter(StorageBuffers::named)
				.toList();
		// Which map a name asks for, and whether it is the albedo, are questions about the NAME and
		// about the plan the load built, so they are asked here once for the life of the program.
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
		// The pass is in the name and not only the path, because two passes are usually served by
		// the same file and their text still differs: the cutout half carries a discard the solid
		// half does not. The device caches a shader module under its identifier, so one name for two
		// texts would hand the second whatever the first compiled to, and the picture would be a
		// picture with the discard silently gone.
		String stem = "pack/" + load + "/" + pass.name() + "/" + this.path;
		Identifier vertexId = Identifier.fromNamespaceAndPath(pass.namespace(), stem + "/vertex");
		Identifier fragmentId = Identifier.fromNamespaceAndPath(pass.namespace(), stem + "/fragment");

		BindGroupLayout.Builder bindings = BindGroupLayout.builder()
				.withUniform(UNIFORM_BLOCK, UniformType.UNIFORM_BUFFER);
		// The second block, for the one family whose geometry arrives in pieces that do not share a
		// value. In the same group as ours rather than in a group of its own: what the game builds a
		// second group for is a layout of ITS own, bound by the names it spells, and this one is this
		// engine's from end to end.
		if (pass.perDraw() != null) {
			bindings.withUniform(pass.perDraw(), UniformType.UNIFORM_BUFFER);
		}

		this.samplers.forEach(name -> GraphicsApi.withSampler(bindings, name));
		this.storage.forEach(name -> bindings.withUniform(name, UniformType.UNIFORM_BUFFER));

		// Everything but the shaders, the bind group, the attachments and the two lines below is
		// Sodium's own, taken from ShaderChunkRenderer.createShader: the pass this is bound into was
		// opened for that pipeline and a difference of topology would be a difference nobody declared.
		RenderPipeline.Builder builder = RenderPipeline.builder()
				.withLocation(Identifier.fromNamespaceAndPath(pass.namespace(), "pipeline/" + stem))
				.withVertexShader(vertexId)
				.withFragmentShader(fragmentId)
				.withBindGroupLayout(bindings.build())
				.withPrimitiveTopology(pass.topology())
				.withCull(pass.cull());

		// The region offset Sodium pushes at every draw of a chunk layer. One game hands it to the
		// pipelines whose name says sodium, the other has each pipeline declare the room it takes;
		// GraphicsApi answers for both, and the family whose geometry is not Sodium's takes none.
		if (pass.namespace().contains("sodium")) {
			GraphicsApi.withSodiumPushConstants(builder);
		}

		// A second group, and it is the game's own rather than one built here: the pass binds its
		// contents by name, so the names have to be the ones it binds. Only the clouds have one.
		if (pass.bindings() != null) {
			builder.withBindGroupLayout(pass.bindings());
		}

		// And the game's per draw transforms, for a program that reads a texture matrix out of them.
		// The game's own object again, and for the same reason: what is bound into it is bound by the
		// name the layout carries, so a copy built here would have to spell DynamicTransforms anyway.
		if (this.gameTransforms) {
			builder.withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS);
		}

		// No binding at all where the family binds no mesh, which is the clouds and only them: their
		// geometry is three bytes a face in a texel buffer and a vertex identifier, so a binding of
		// nought elements would name a buffer the pass never sets.
		if (format != null) {
			builder.withVertexBinding(0, format);
		}

		// Left unset where the family answers null, which is not the same as setting the default one:
		// the builder hands a null state through to the pipeline, and that is a pass which neither
		// tests nor writes a depth. It is what the game's own sky is built with.
		if (pass.depth() != null) {
			builder.withDepthStencilState(pass.depth());
		}

		// One state per attachment, and dynamic rendering wants the two counts equal.
		// By slot and never by append: the builder holds the states in an array and the argumentless
		// form writes slot nought every time, so three calls would leave one state and a pipeline
		// the pass refuses to bind, by name and in the middle of the world.
		if (pass.shadow()) {
			// One state per shadowcolor the program's own draw buffers name, in their order, which
			// is also the rank a per buffer blend directive on this program lands on. The format is
			// each buffer's own and not a constant: Mellow asks for R8 on nought, and a state naming
			// four channels against a one channel attachment is the pipeline refusing to bind.
			for (int slot = 0; slot < this.shadowColours.size(); slot++) {
				builder.withColorTargetState(slot,
						state(slot, targets.shadowFormat(this.shadowColours.get(slot))));
			}
		} else {
			for (int slot = 0; slot < this.slots.size(); slot++) {
				Slot one = this.slots.get(slot);
				switch (one.bound()) {
					// Held as a null state, which the backend turns into a colour write mask of
					// nought while every neighbour carries WRITE_ALL (VulkanRenderPipeline:182-189).
					// So a pass that covers its outputs hands the device an attachment array whose
					// elements already differ, blend functions or no blend functions, and has since
					// long before any of them were read by rank.
					case UNUSED -> builder.withUnusedColorTargetState(slot);
					// The mask is written outright and never blended, whatever the pack asked for its
					// own targets: what it carries is the depth the fragment left, and a depth mixed
					// with the one behind it is the depth of nothing at all.
					case COVERAGE -> builder.withColorTargetState(slot, new ColorTargetState(
							Optional.empty(), one.format(), ColorTargetState.WRITE_ALL));
					default -> builder.withColorTargetState(slot, state(slot, one.format()));
				}
			}
		}

		this.pipeline = part(builder);

		// Filed against the pipeline and not the program, because the pipeline is what the
		// descriptor walk can see when it has to answer for a name.
		ShadowCompare.note(this.pipeline, this.path, loaded);

		// Same filing, same reason: the compile road has the pipeline in hand and nothing else. And
		// the same refusal the storage block below takes, on the one road that answers no: a pack
		// ships a geometry stage the device cannot run and that cannot be folded away. The fragment
		// stage is asked for here rather than read beside the vertex one because a fold rewrites it,
		// which is also why the source is only built once the pipeline exists.
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

		// A storage block this engine has no bufferObject for is the one refusal that does not
		// announce itself. An unbindable sampler stops the pipeline from being built and this class
		// already falls back on that; a storage block compiles, never enters a bind group, and
		// leaves the descriptor on the binding the pack wrote, which is a draw against nothing.
		// The chain refuses one by name and so does this, so that the world keeps the game's own
		// shader instead. Complementary's blockDataBuffer is served, and stays off this list.
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

		// Refused rather than drawn somewhere else. A shadow program handed back with no map to
		// draw into would be bound into the pass the renderer opens for itself, which is the game's
		// own target, and the pack's shadow output would land on the screen.
		if (this.pass.shadow() && this.shadow.depth() == null) {
			return null;
		}

		// Creating a texture or clearing one records into the command buffer a pass would be
		// recording into. The hold has to end first; after that this program's block exists and
		// the next prepare of it can run while the next geometry program keeps that pass open.
		if (this.block == null || !ConstantTextures.ready()) {
			GeometryHold.flush(() -> "a program building or clearing its block");
		}

		if (!compile(device)) {
			return null;
		}

		// Asked once, here where the program is prepared, so that a view the device cannot give is
		// a program refused rather than a descriptor refused while the pass records.
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

		// After the constants and never before them: what a name with no image is answered with is a
		// view of one of those textures, and this is the call that settles those answers again
		// after a release.
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

		// NOT short-circuited on the flag below, which only says shaderc has been paid once.
		// Every resource reload empties the device cache under a running session, and what
		// lowers that flag is the chain noticing its own head recompiled, which happens later in
		// the frame than the sky, the first thing the world draws. A program trusting the flag
		// hands back a pipeline the cache no longer holds, and setPipeline compiles it from the
		// game's shader sources, which carry no line of this pack: an invalid pipeline, and the
		// frame throws by name in the middle of the sky. The call below is a computeIfAbsent, so
		// it costs a lookup for as long as the cache holds the key.

		// What the worker finished is handed to the cache here, on the render thread, and the
		// precompile below finds it as a lookup. A cache that already holds the key kept a copy
		// somebody compiled meanwhile, so ours dies instead, never having been bound anywhere.
		AheadCompiler.Ahead ready = this.ahead;
		if (ready != null) {
			this.ahead = null;
			if (!ready.adopt(device, this.pipeline)) {
				ready.destroy();
			}
		}

		CompiledRenderPipeline compiled = GraphicsApi.compile(device, this.pipeline, this.source);
		if (!GraphicsApi.valid(compiled)) {
			// Handing back an invalid pipeline throws inside setPipeline, in the middle of Sodium's
			// own pass, which reads as a Sodium failure. Refused here instead, once.
			this.broken = true;
			// The PASS and not "the terrain", which it said from the day only the terrain came through
			// here, and not the family either: the family is one word for all twenty entity pieces,
			// so it would not say which failed. The same words as the storage block refusal above,
			// because the two latch the same flag and a reader meeting either has the same question.
			CoreVK.logger().error("{} did not compile, so the {} pass keeps the game's own shader",
					this.path, this.pass.name());

			return false;
		}

		this.compiled = true;

		return true;
	}

	
	RenderPipeline reshape(GpuDevice device, VertexFormat layout) {
		// The lock compile() takes, for the flag they share: a worker warming this program ahead
		// writes broken under it, and this runs on the render thread while it does.
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
				// A stage the driver refuses throws out of precompilePipeline rather than coming back
				// invalid, which is what MoltenVK does with one Metal will not build, and the caller
				// is a pipeline being set on a pass, outside every catch of the frame. It is the same
				// refusal, so it takes the same latch.
				thrown = e;
				valid = false;
			}

			if (!valid) {
				// Latched like compile()'s refusal, and for its reason: retrying would pay shaderc
				// every draw for the same answer, and the caller has a fallback to hand the draw to.
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

		// Null is how the builder holds an unused slot, so null is copied as unused; the count the
		// pipeline carries beside the array is rebuilt by walking every slot in order.
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

		// The comparison note is keyed on the pipeline object, not read off its states, so it is
		// the one answer a rebuild does not carry by itself. Left unfiled, the descriptor walk
		// answers no for every compared name of this program and the variant's depth-reference
		// lookups run on an ordinary sampler, undefined and silent.
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
			// The first draw retries on the device's own path, which latches broken and prints
			// the pack's defect properly. Said here as well because a refusal the device path
			// does NOT reproduce would otherwise never be seen at all.
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
		// Once, and it is the one thing that tells a pass that draws from a pass that only compiled:
		// announce() says a program was prepared, which happens whether or not the renderer goes on
		// to record a single command against it.
		if (!this.drew) {
			this.drew = true;

			// The image goes on this line and not on the one announce() prints, which is the only
			// place it can go: the atlas belongs to the DRAW for two of the three families, so at
			// the moment the program is announced this field holds nothing yet. Said for whoever
			// reads the atlas and for nobody else, a pass with no such name having no image to name.
			CoreVK.logger().info("The {} pass records its first draw with {}{}",
					this.pass.name(), this.path,
					this.samplers.stream().anyMatch(ATLAS::contains)
							? ", reading " + GameImages.name(this.atlas) + " where it asks for the atlas"
							: "");
		}

		pass.setUniform(UNIFORM_BLOCK, blockSlice());
		StorageBuffers.bind(pass, this.storage);

		// The rest are already standing in this pass, put there by this program's own last bind into
		// it, and writing them again would allocate a pair and put back what the map already holds.
		// {@link #settledIn} says what can undo that and why nothing else can.
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
		// Whatever this program left standing in a pass is no longer what it would write now, so the
		// next bind writes the lot again. release() clears the same two, and not because a released
		// program is unreachable: TerrainDraw is the one family that keeps its program map across a
		// release, so its static bind can reach a program that has never come back through here.
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
			// The map that follows THIS pass's own image, which is how the block atlas, the particle
			// atlas and every entity texture answer for themselves without this step knowing which of
			// them it is drawing. CoreVK resolves it from the bound albedo for the same reason
			// (pipeline/CoreVKRenderingPipeline.java:849-871).
			//
			// Two doors and not one, because the two shapes of image are read differently: a stitched
			// atlas has its maps stitched to match it, and a texture of its own has them beside it
			// under its own name. CoreVK keeps the same pair and picks between them by the class of the
			// bound texture (pbr/loader/PBRTextureLoaderRegistry.java:15-16); here the atlas door
			// answers only for an image it was built against, so asking it first picks the same one.
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

		// One pixel where the pass has no atlas of its own, which is every cloud and the sky's own
		// disc: a name bound to nothing at all throws at the bind. The sky's textured elements do
		// have one, handed to them per draw by SkyProgram.texture.
		//
		// WHITE and not black, which is CoreVK's answer for the same case and is the one that leaves a
		// picture. A pack multiplies the atlas into its albedo, so white is the neutral of what it is
		// about to do and black is the value that erases the geometry: three packs of the corpus
		// sample this name from their cloud stage, and with a black pixel behind it their clouds come
		// out invisible. Nothing of the sky reads it - Body Camera declares the name in its skybasic
		// and never samples it - so the sky sees no difference either way.
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
			// The albedo's own sampler is what imageSampler hands back over this, because a
			// map is read at the albedo's own texture coordinate whichever door served it, so anything
			// read differently would be read at a different place than the albedo beside it. What that
			// means is not the same on the two doors: an atlas's maps ARE the atlas, the same size and
			// the same chain with every sprite in the same slot, where a plain texture's cover the same
			// whole range at a resolution of their own and carry no chain at all. A mipmapped sampler
			// on the second is harmless rather than overlooked: the view carries one level, so the read
			// is bounded by the image.
			//
			// Except the specular map under labPBR, where a filter that blends two texels of it
			// produces a material that is in neither of them. CoreVK asks the same question here
			// (pipeline/CoreVKRenderingPipeline.java:860-867) and answers it with
			// GlSampler.MIPPED_NEAREST_NEAREST, which is NEAREST inside a level AND between levels.
			// That map lands here rather than on the albedo's sampler, and so does every map on a pass
			// the game handed no sampler for.
			//
			// THIS ONLY GETS HALF OF IT, and the half it misses is an obstacle of the API rather
			// than a preference. What comes back here is nearest inside a level and LINEAR between
			// them: the backend sets the mip mode from the sampler's maximum lod alone,
			// VulkanGpuSampler:47 taking LINEAR for anything above a quarter, and the cache offers
			// no way to ask for the other one - a sampler built with no mipmaps caps the lod at that
			// quarter and gives up the chain entirely, which is worse. Making one by hand does not
			// help either, GpuDevice.createSampler reaching the same constructor.
			// What it costs: on a distant surface, where two levels are blended, the thresholds are
			// crossed after all. Close up, where a level is read whole, they are not.
			return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST, true);
		}

		if (LIGHTMAP.equals(name)) {
			return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
		}

		// Nothing about this one reaches the read, which is a texelFetch and takes no sampler state
		// at all. It is bound because a name declared in the module has to be, and NEAREST is what
		// says so: a filter here would be a flash halfway to the tint if anything ever sampled it.
		if (OVERLAY.equals(name)) {
			return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
		}

		// The noise image tiles and everything else clamps, the same rule the chain follows.
		//
		// A COLOUR TARGET is never read past level nought here, even where it carries a chain, and
		// this is not a gap left open. Nothing fills such a chain for a geometry program: it is
		// filled right before the full screen pass that reads it, and a terrain pass draws inside
		// the one Sodium opened, where no transfer may be recorded. A sampler that let these reads
		// climb would hand them levels nothing has written, which is undefined memory rather than a
		// coarser image.
		//
		// Nothing asks for it either, and that was measured rather than assumed: across the eight
		// packs there are fifty reads at a lod other than nought and not one of them is in a
		// geometry file. Four of them do declare colortexNMipmapEnabled on a gbuffers program,
		// Bliss on gaux1 the loudest, and none of those programs ever reads the target at a lod. So
		// the directive is theirs to declare and dead on their side, and the cost of honouring it
		// here would be a risk taken for nobody.
		//
		// The SHADOW MAP is the one exception, and it is one because of when its chain is filled:
		// at the tail of the stage that drew the map, outside every pass, so it stands for the
		// whole frame that reads it and a geometry program is inside that frame like anything else.
		// The map itself answers, as it answers for the filter, so that the three roads that bind
		// it read it one way.
		SamplerPlan.Kind kind = one.binding.kind();
		if (one.source != null && this.targets.packView(one.source.image()) != null) {
			// A file of the pack's own is filtered and addressed as the pack asked, in the .mcmeta
			// beside it, and the name it took over has nothing to say about either.
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
		// The same refusal prepare makes, or the two part company: with the pipeline refused,
		// Sodium keeps its own shader, which declares one target state, and steering its pass onto
		// a many target descriptor is refused at setPipeline in the middle of Sodium's own draw.
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
				// The targets are not there yet, or not there any more. Sodium's own pass draws this
				// frame rather than nothing at all, and the next frame tries again.
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

		// Never left out: the encoder refuses a descriptor without one outright, and it refuses it
		// at the first draw rather than at load time. The size is the screen's, and stays the
		// screen's now that attachment nought may be a target of the pack's: a scaled colour target
		// is dropped before it gets here, and the game's depth is attached whatever else is.
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
		// The one-level view and not the sampled one: a pack that asked for a chain gets a map of
		// several levels, and Vulkan attaches exactly one.
		GpuTextureView depth = this.shadow.depthAttachment();
		if (depth == null) {
			return null;
		}

		this.shadowViews.clear();
		for (int index : this.shadowColours) {
			// One attachment per state the pipeline carries, and in the same order. Every index in
			// the list is one the plan read for this same program through this same rule, so an
			// image for it was allocated with the depth and this is null only where the depth above
			// is. The test is kept all the same because the two failures are not equal: a pass short
			// of an attachment the pipeline names is setPipeline refusing in the middle of the
			// world, where a null descriptor out of this method is the shadow stage not opening at
			// all.
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

		// With the ring and not after it: a slice outliving the buffer it names is memory that reads
		// as valid.
		this.blockSlice = null;
		this.blockSliceOf = null;

		// The reference is dropped, the textures are not: they are the device's, not this
		// program's, and re-clearing them once per program was about ninety of the standalone
		// clears a reload frame paid.
		this.constants = null;

		// Dropped with the textures they name rather than left standing. Nothing binds a released
		// program before it is prepared again, and prepare settles these afresh, so this only shortens
		// the life of a reference; a view of a destroyed texture is not a thing to keep a field on.
		for (Sampled one : this.bound) {
			one.view = null;
			one.state = null;
			one.servedFor = null;
			one.served = null;
			one.servedKnown = false;
		}

		// Let go of the pass and the program the same way, and for the same reason as the views
		// above: these two are the one place a closed RenderPass and a released program stay
		// reachable, and they would otherwise stand until some program's next bind. Only when it
		// is this program standing there, another one's settling being none of our business.
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
		// The length as well as the buffer, though only the buffer can move today: the block's size
		// is settled at translation and a translation goes through release, which drops the ring.
		// Comparing it costs one integer and takes a silent wrong answer off the table if that ever
		// stops being true.
		if (this.blockSlice == null || this.blockSliceOf != buffer || this.blockSliceBytes != bytes) {
			PassTimings.censusSlice();
			this.blockSlice = buffer.slice(0, bytes);
			this.blockSliceOf = buffer;
			this.blockSliceBytes = bytes;
		}

		return this.blockSlice;
	}

	private void writeBlock() {
		// Before the block and never once for the run: the two conventions alternate inside one
		// frame now that the shadow map is ours and the game's targets are not, and what a vertex
		// stage does with its clip depth on the way out comes from this pair.
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
			// One texel of what the absence of this map means, for a sprite the resource pack ships
			// nothing for and for every family drawn with no image at all. What is drawn over it when
			// the draw does bring one is in imageView.
			return this.constants.flat(one.material);
		}

		if (one.albedo) {
			// One pixel where the pass has no atlas of its own, which is every cloud and the sky's
			// own disc: a name bound to nothing at all throws at the bind. imageView says why it is
			// white and not black.
			return this.constants.white();
		}

		if (OVERLAY.equals(sampler)) {
			// The image the hit flash and the damage tint are read out of, sixteen by sixteen and
			// built once at start up. It is read by a texelFetch at the coordinate the mesh carries,
			// so unlike every other name here the fallback is not a colour that means something: a
			// single texel is out of bounds at that coordinate and what comes back is undefined. The
			// fallback is there because a name declared in the module has to be bound at all, and the
			// branch is unreachable while there is a client to draw from - the game holds this in a
			// field of its own, built with the renderer and never replaced.
			Minecraft minecraft = Minecraft.getInstance();
			GpuTextureView overlay = minecraft == null
					? null : minecraft.gameRenderer.overlayTexture().getTextureView();

			return overlay == null ? this.constants.black() : overlay;
		}

		if (LIGHTMAP.equals(sampler)) {
			// One white texel for a piece the game draws at full light, which is the other half of what
			// the vertex head did with the light map names and is read off the same field so that the
			// two cannot part company (PackProgram.Loaded.fullbright). CoreVK binds its own white pixel
			// here (samplers/CoreVKSamplers.java:202-206). White is what leaves the piece alone: a pack
			// multiplies its albedo by what it reads out of this image, and every texel of the game's
			// own is darker than one except at the brightest level.
			if (this.loaded.fullbright()) {
				return this.constants.white();
			}

			Minecraft minecraft = Minecraft.getInstance();
			GpuTextureView lightmap = minecraft == null ? null : minecraft.gameRenderer.lightmap();

			return lightmap == null ? this.constants.white() : lightmap;
		}

		SamplerPlan.Binding binding = one.binding;

		// White and not black for a depth that stays a constant, and the reason is the image rather
		// than a taste: what a depth lookup reads is already in the pack's own window, where one is
		// the far plane. Black would put the whole world against the camera. PackPass answers the
		// same way, and the two have to move together.
		return switch (binding.kind()) {
			case COLORTEX -> colortex(binding);
			case DEPTH -> depth(sampler);
			case SHADOW_DEPTH -> shadowDepth(binding.sampler());
			case SHADOW_COLOUR -> shadowColour(binding.index());
			case NOISE -> this.targets.noise();
			// Every geometry program is drawn in the gbuffers stage, the shadow passes included,
			// which is the one thing a texture.STAGE.NAME override needs to know. Mellow moves
			// noisetex there and nowhere else, so this is not a case the chain also covers.
			case PACK_TEXTURE -> packTexture(one);
			case DISTANT_DEPTH -> distantDepth();
			// Null where the target cannot be stored into: prepare refuses the program then,
			// the layout typing this name as a storage image that no placeholder can stand for.
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

		// shadowtex1 is the map without the translucents and shadowtex0 the map with them. Serving
		// one image to both is what makes a pack's coloured shadow branch dead code: it asks whether
		// a point is occluded in nought and clear in one, and one image can never answer yes. Which
		// of the two the bare "shadow" names depends on the whole list this program declared, so the
		// plan answers rather than the name.
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

		// The two copies read the opaque image wherever it exists, the frame before's ahead of the
		// take and this frame's behind it; the live name only once the pass stands behind the
		// deferred stage, where the two are one depth.
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
					// Two names can answer one target, gaux1 and colortex4, and a read of either counts.
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

			// A name only declared draws the same whatever answers it, so it is not worth a line.
			// Once per program path: the entity family builds one of these per element it serves.
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
		// Asked of the pack first, on the same order the bind above follows: a name the pack supplied
		// a file for is that file, and whether it is a real image is a question about the file.
		if (material(sampler) == null && PbrMap.named(sampler) != null) {
			return this.targets.packTexture(TextureStage.GBUFFERS, sampler) != null;
		}

		PbrMap material = material(sampler);
		if (material != null) {
			// The session's answer and deliberately not this pass's, which is the one thing here that
			// cannot be asked once. This program may be drawn with several images in one frame, and
			// the atlas field holds none of them yet at the moment this line is printed: the shadow
			// stage prepares its three chunk programs with no image in hand, and the sky, the
			// particles, the weather and the entities are all handed theirs per draw, after the
			// prepare. Asked of the pass, the line printed the answer for whichever image happened to
			// be in the field, once, for the session.
			//
			// So it says what a latched line can say without lying: whether anything fills the name
			// at all. Which atlases really answer is said exactly, one line each, as they are built.
			return PbrAtlases.supplies(material);
		}

		SamplerPlan.Binding binding = this.loaded.samplers().binding(sampler);
		SamplerPlan.Kind kind = binding.kind();

		// A file of the pack's own counts when the file is there, on the same call the bind makes:
		// what packTexture answers is what the draw reads. Left out of this list, Reverie's eight
		// lookup tables and Complementary's cloud-water were all reported as one pixel on every
		// geometry program while the draw was reading them, and the line sent a whole session
		// after a black picture whose cause was elsewhere.
		if (kind == SamplerPlan.Kind.PACK_TEXTURE) {
			return this.targets.packTexture(TextureStage.GBUFFERS, sampler) != null;
		}

		return ATLAS.contains(sampler) || LIGHTMAP.equals(sampler) || OVERLAY.equals(sampler)
				// A target this pass writes counts only where the copy answers it; the pixel stands
				// in everywhere else, and the load has already said why.
				|| (kind == SamplerPlan.Kind.COLORTEX && (!collides(binding) || copied(binding)))
				|| kind == SamplerPlan.Kind.CUSTOM_IMAGE
				|| kind == SamplerPlan.Kind.NOISE
				|| (kind == SamplerPlan.Kind.DEPTH
						&& (this.pass.afterDeferred() || SamplerPlan.depthCopy(sampler)))
				// The map exists from the first frame, but a pass that draws it reads its own
				// attachment and is answered with a constant like everything else that collides.
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

		// A colour target is filtered as the chain filters it, LINEAR wherever the format allows it,
		// so that a name reads the same here and one pass later.
		SamplerPlan.Binding binding = this.loaded.samplers().binding(sampler);
		if (binding.kind() == SamplerPlan.Kind.COLORTEX) {
			return this.targets.filter(binding.index());
		}

		// The noise image is a continuous field, not a lookup table: a pack derives water normals
		// and cloud shapes from it and counts on the interpolation. CoreVK binds it LINEAR_REPEAT,
		// and reading it NEAREST shatters every one of those surfaces into facets. A shadow colour
		// is continuous in the same way, carrying the light that came through glass and water
		// across a penumbra, and it stays LINEAR whatever the pack declares: OptiFine filters it
		// linearly unless the pack writes shadowcolorNNearest, while CoreVK parses that name and
		// binds the buffer through GlSampler.LINEAR regardless (CoreVKSamplers.java:156-176). Reading
		// it as the pack asked would diverge from the engine packs are tuned against.
		//
		// The shadow DEPTH is LINEAR too, and the reason a reader reaches for to keep it NEAREST
		// does not hold: where the compare mode is on, a sampler averages the RESULTS of the
		// four tests and never the depths, so nothing is ever compared against a surface that exists
		// nowhere. CoreVK filters this LINEAR unless the pack asks otherwise, its SamplingSettings
		// starting at nearest=false and shadowtexNearest with its per-index spellings turning that
		// round (shadows/ShadowRenderer.java:267-280), and adds GL_COMPARE_REF_TO_TEXTURE on top
		// only where the pack writes shadowHardwareFiltering. Those names are read, and LINEAR is
		// what a pack that writes none of them gets, which is not all of them: Pegasus declares
		// the depth pair NEAREST. The manual PCF loops packs write are the
		// whole point either way, since every tap of such a loop rides on this filter. The map
		// itself is asked rather than this line deciding, which is what makes the answer PackPass's
		// as well, it binding the same name for the full screen passes: the two halves of one frame
		// reading one map through two filters is a difference nothing would ever explain.
		// A custom image follows its format, and it is asked of one place for the same reason the
		// shadow map is: a volume read one way here and another from a composite is a difference
		// nothing would ever explain. PackPass.customImageFilter is that place.
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
		// The render stage is in the line rather than left silent, because nothing else can say it:
		// what a pack does with it is a branch inside its own code, so a pass told the wrong one
		// draws something that looks like a picture, and this module has no harness to catch that.
		CoreVK.logger().info("Drawing the {} {} pass with {} of {} at render stage {}, {} uniforms "
				+ "and {} samplers", this.pass.name(), this.pass.family(), this.path,
				this.loaded.packName(), this.pass.stage(), this.loaded.program().uniforms().size(),
				this.samplers.size());

		// A cutout stage without its discard draws a leaf as a cube, which reads as the pack being
		// wrong rather than as a translation that could not place a statement.
		AlphaTest alphaTest = this.loaded.alphaTest();
		if (alphaTest.tests() && fragment.notes().alphaEpilogue() == 0) {
			CoreVK.logger().warn("This pass discards at {} {} and the program could not be given the "
					+ "test, so nothing beyond the pack's own discards is thrown away",
					alphaTest.function(), alphaTest.reference());
		}

		// Split by what the mesh really answers. Only names the mesh has no element for are a gap;
		// the chunk mesh now answers four of them and the entity mesh two, while the glint's answers
		// none, which is why this comes off the PASS rather than off the family.
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
		// Kept out of the line below rather than counted in it, because that line is about a gap and
		// this is not one: these names are filled exactly on the frames the pack draws the far
		// terrain, and PackChain says once for the whole chain what they carry.
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
			// What is left is what nothing fills for this pass: the shadow map, the live depth on
			// the passes that draw ahead of the deferred stage, and the two material maps
			// wherever the RESOURCE pack ships no file beside the sprites of this pass's atlas. The
			// last of the three is the one a reader can act on, and it is not a shader pack's doing.
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

			// Said apart, because it is a different thing and the count above cannot show it: a
			// directive naming a target past the ceiling is thrown away WHOLE, so the program draws
			// into the pair instead of into what it asked for.
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
			// Nought included, and the log says the sides because they are the whole fix: a write on
			// the half the composites do not read is geometry that vanishes without a word from
			// anyone.
			CoreVK.logger().info("Its draw buffers all reach the pack's own targets, nought "
					+ "included: {}",
					this.extra.stream()
							.map(one -> TargetName.canonical(one.target()) + " " + one.side())
							.toList());
			if (this.covers) {
				// The pair to read this against is the seed's own line: this one says the mask is
				// written, that one says it is honoured.
				CoreVK.logger().info("It also writes the coverage mask, so nothing paints the game's "
						+ "own picture back over what this pass wrote");
			}
		} else if (this.extra.isEmpty()) {
			// Draw buffer nought is not named here on purpose: it goes to the game's own target,
			// which is where it has always gone and where the seed reads it back from.
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

		// Said outside the chain above, and it is a second line about one cause rather than a line
		// about a second cause: what lands here asked for a mask and was not given one, which the
		// constructor has already named. What this adds is the half the warning does not carry, and
		// it is the half a blending pass pays twice: the blend is made against the game's target
		// instead of against the world, so a pixel of alpha under one is tinted by a clear rather
		// than by what stands behind it, and a row that writes no depth is discarded outright by the
		// seed's cut. Neither shows up as an error and neither is visible in a silent log.
		if (this.demoted) {
			CoreVK.logger().info("It blends and draw buffer nought still goes to the game's own "
					+ "target: this pass is drawn before the scene seed and nothing marks the pixels "
					+ "it blends onto, so the seed would paint the game's picture back over them");
		}

		// Said because nothing on screen would. A pack declaring sampler2DShadow asks the hardware
		// to compare and hand back a filtered fraction; blaze3d's GpuSampler carries no comparison
		// at all, so the map's own names are bound under the comparison sampler this engine makes
		// in Vulkan's terms, and anything else compared is made in the shader instead: four texels
		// gathered, four steps, and the same bilinear blend the hardware would have applied.
		//
		// Asked of the notes and not of the samplers: on the arithmetic road the type has been
		// rewritten to the ordinary one and there is nothing left to recognise.
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

		// Said apart from those, and at info: the pack reads these under CoreVK as well and gets the
		// same nought there, so they are nobody's debt and a warning would read as one.
		if (!gaps.nobody().isEmpty()) {
			CoreVK.logger().info("{} reads {} values no engine answers, CoreVK included, so they are "
					+ "zeroes there too: {}", this.path, gaps.nobody().size(), gaps.nobody());
		}

		// Underneath that, and it is not the same list: these are members a source really answered,
		// with something that is not the value. They count as supplied wherever a count is taken,
		// which is the whole reason for naming them, and a full screen pass has named them since the
		// block existed while a geometry pass named none.
		//
		// One list for every pass, and that is a change this lot earned: the names that were a
		// placeholder on the entity mesh alone are made on the mesh now, each out of an element of
		// its own, so no name is a stand-in in one pass and a value in another.
		PackValues.standIns(this.loaded.program().uniforms().stream()
						.map(TranslatedUnit.Uniform::name)
						.toList())
				.forEach((reason, names) -> CoreVK.logger().warn("{} reads {} values answered with "
						+ "a stand-in rather than with the value, {}, which count as supplied "
						+ "everywhere else, because {}", this.path, names.size(), names, reason));
	}
}
