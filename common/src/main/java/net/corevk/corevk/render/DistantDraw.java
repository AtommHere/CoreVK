package net.corevk.corevk.render;

import net.corevk.corevk.dh.DhLods;
import net.corevk.corevk.glsl.DistantVertex;
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
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector2f;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

public final class DistantDraw extends FamilyDraw {

	
	public record Element(String element, String program, boolean water, boolean shadow) {

		
		private PackProgram.GeometryElement asked() {
			// No alpha test: an LOD carries no texture at all, DH's mesh having no texture coordinate
			// this engine reads, so there is no sampled alpha for a threshold to cut. CoreVK hands its
			// DH keys no cutout either. No coverage mask, for the reason DistantProgram gives.
			return new PackProgram.GeometryElement(this.element, this.program, AlphaTest.OFF,
					VertexInputs.DISTANT, false);
		}

		
		boolean afterDeferred() {
			return this.water && !this.shadow;
		}

		
		RenderStage stage() {
			return this.water ? RenderStage.TERRAIN_TRANSLUCENT : RenderStage.TERRAIN_SOLID;
		}

		
		String half() {
			return (this.water ? "translucent" : "opaque") + (this.shadow ? " shadow" : "");
		}
	}

	
	private static final Map<String, Element> ELEMENTS = new LinkedHashMap<>();

	static {
		ELEMENTS.put("distant", new Element("distant", "dh_terrain", false, false));
		ELEMENTS.put("distant_water", new Element("distant_water", "dh_water", true, false));
		ELEMENTS.put("distant_shadow", new Element("distant_shadow", "dh_shadow", false, true));
		ELEMENTS.put("distant_shadow_water",
				new Element("distant_shadow_water", "dh_shadow", true, true));
	}

	private static final String SEED_LABEL = "CoreVK far terrain occlusion";

	
	private static String key(boolean water, boolean shadow) {
		return (shadow ? "distant_shadow" : "distant") + (water ? "_water" : "");
	}

	
	private static final int BLOCK_BYTES = 16;

	
	private static final GpuFormat DEPTH_FORMAT = GpuFormat.D32_FLOAT;

	
	private static final GpuFormat CARRIED_FORMAT = GpuFormat.R32_FLOAT;

	private static final Identifier SEED_VERTEX_ID =
			Identifier.fromNamespaceAndPath(CoreVK.MOD_ID, "distant/occlusion_vertex");
	private static final Identifier SEED_FRAGMENT_ID =
			Identifier.fromNamespaceAndPath(CoreVK.MOD_ID, "distant/occlusion_fragment");

	private static final String SEED_WORLD = "InWorld";
	private static final String SEED_FAR = "InFar";
	private static final String SEED_BLOCK = "OfDistantOcclusion";

	
	private static final int SEED_BLOCK_BYTES = 16;

	
	private static final int SEED_VERTICES = 6;

	private static final String SEED_VERTEX = """
			#version 460 core

			in vec3 Position;
			in vec2 UV0;

			out vec2 ofTexCoord;

			void main() {
				ofTexCoord = UV0;
				gl_Position = vec4(Position.xy * 2.0 - 1.0, 0.0, 1.0);
			}
			""";

	
	private static final String SEED_FRAGMENT = """
			#version 460 core

			layout(std140) uniform OfDistantOcclusion {
				vec2 ofDistantDepth;
			};

			uniform sampler2D InWorld;
			uniform sampler2D InFar;

			in vec2 ofTexCoord;

			layout(location = 0) out vec4 ofFragData0;

			void main() {
				float world = texture(InWorld, ofTexCoord).r;
				float carried = world == 0.0
						? 0.0
						: clamp(ofDistantDepth.x * world + ofDistantDepth.y, 0.0, 1.0);

				ofFragData0 = vec4(carried);
				gl_FragDepth = max(carried, texture(InFar, ofTexCoord).r);
			}
			""";

	private static final ShaderSource SEED_SOURCE = GraphicsApi.source((id, type) -> {
		if (type == ShaderType.FRAGMENT) {
			return SEED_FRAGMENT_ID.equals(id) ? SEED_FRAGMENT : null;
		}

		return SEED_VERTEX_ID.equals(id) ? SEED_VERTEX : null;
	});

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

	
	private final Map<String, DistantProgram> programs = new LinkedHashMap<>();

	
	private List<String> carried = List.of();

	
	private volatile boolean read;

	
	private final Set<String> refused = new LinkedHashSet<>();

	
	private GpuTexture depth;
	private GpuTextureView depthView;
	private int depthWidth;
	private int depthHeight;

	
	private GpuTexture blended;
	private GpuTextureView blendedView;

	
	private TargetSurface worldCarried;

	private int blendedWidth;
	private int blendedHeight;

	
	private boolean seeded;

	
	private boolean seedBroken;

	private RenderPipeline seedPipeline;

	
	private boolean drew;

	
	private boolean broken;

	
	private static final Corners CORNERS = new Corners("CoreVK far terrain sections");

	
	private static final Corners SHADOW_CORNERS = new Corners("CoreVK far terrain shadow sections");

	
	private static final Occlusion OCCLUSION = new Occlusion();

	
	private List<DhLods.Section> opaqueSections = List.of();
	private List<DhLods.Section> waterSections = List.of();

	
	private List<DhLods.Section> shadowOpaque = List.of();
	private List<DhLods.Section> shadowWater = List.of();

	
	private boolean shadowBroken;

	DistantDraw(PackChain owner, Path packPath, String place, Map<String, OptionValue> chosen,
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

	
	public static boolean draw(boolean opaque, List<DhLods.Section> sections) {
		DistantDraw draw = PackChain.distant();
		GpuDevice device = RenderSystem.tryGetDevice();
		Minecraft minecraft = Minecraft.getInstance();
		if (draw == null || draw.broken || device == null || minecraft == null || sections.isEmpty()) {
			return false;
		}

		// Kept before anything can refuse the draw, and kept whether or not the pack ends up drawing
		// it: what the light wants is the geometry DH handed over, and its own stage stands at the
		// far end of the frame from here.
		if (opaque) {
			draw.opaqueSections = sections;
		} else {
			draw.waterSections = sections;
		}

		try {
			return draw.record(device, minecraft, ELEMENTS.get(key(!opaque, false)), sections);
		} catch (GpuDeviceLossException e) {
			throw e;
		} catch (RuntimeException e) {
			// Latched, like every other family the game calls back into: the alternative is one stack
			// trace a frame for a failure that will not mend itself. What is lost by stopping is only
			// this engine's own drawing of the far terrain, DH's being handed back on the next line.
			draw.broken = true;
			CoreVK.logger().error("CoreVK stopped drawing the far terrain after an error, so Distant "
					+ "Horizons draws it with its own shader for the rest of this pack", e);

			return false;
		}
	}

	
	public static void shadow(boolean water, Vec3 camera) {
		DistantDraw draw = PackChain.distant();
		GpuDevice device = RenderSystem.tryGetDevice();
		if (draw == null || draw.shadowBroken || device == null) {
			return;
		}

		try {
			draw.recordShadow(device, ELEMENTS.get(key(water, true)),
					water ? draw.shadowWater : draw.shadowOpaque, camera);
		} catch (GpuDeviceLossException e) {
			throw e;
		} catch (RuntimeException e) {
			// Latched on its own, and the picture keeps going: what stops here is the far terrain's
			// entry into the map, so the LOD is lit by what the pack computes from its own depth,
			// which is the whole of what it has without this half.
			draw.shadowBroken = true;
			CoreVK.logger().error("CoreVK stopped drawing the far terrain into the shadow map after "
					+ "an error, so nothing of it casts into the map for the rest of this pack", e);
		}
	}

	
	GpuTextureView served() {
		return this.drew ? this.depthView : null;
	}

	
	GpuTextureView blendedServed() {
		return this.seeded ? this.blendedView : null;
	}

	
	GpuTextureView worldServed() {
		return this.seeded ? this.worldCarried.view() : null;
	}

	
	private void seedWater(GpuDevice device, RenderTarget main) {
		// And not at all once the take that follows this half has given up on its own three image
		// conversion: seeding without it would leave dhDepthTex0 answering the far terrain with no
		// water for the rest of this load, which is a worse name than an unoccluded water.
		if (this.broken || this.seedBroken || !this.drew
				|| this.targets.depth().distantRefused()) {
			return;
		}

		try {
			// Asked for INSIDE the catch and not handed in: the first frame of a pack allocates it
			// here, and an allocation that gave way outside would land in the caller's own catch,
			// which hands the far terrain back to that mod for the rest of the pack rather than
			// costing the occlusion alone.
			this.seeded = seed(device, main, this.owner.quad(device));
		} catch (GpuDeviceLossException e) {
			throw e;
		} catch (RuntimeException e) {
			this.seedBroken = true;
			CoreVK.logger().error("CoreVK stopped seeding the world's depth under the far terrain's "
					+ "water after an error, so that water paints over whatever the game drew between "
					+ "the two halves of the far terrain for the rest of this pack", e);
		}
	}

	private boolean seed(GpuDevice device, RenderTarget main, GpuBuffer quad) {
		GpuTextureView world = main.getDepthTextureView();
		// The two sizes agree by construction, {@link #record} having just called ensureDepth on
		// this very target, and the comparison stands as the precondition of a pass that samples
		// one image at the size of another: a seed drawn across two sizes would stretch the world's
		// depth over the screen rather than fail. What it does NOT cover is the frame of a resize
		// itself, where ensureDepth has just remade the far terrain's image and emptied it, so the
		// water half of that one frame is held back by the world alone and not by the far terrain's
		// own opaque half. One frame, and the colour of that half is already on screen.
		if (world == null || quad == null || main.width != this.depthWidth
				|| main.height != this.depthHeight) {
			return false;
		}

		Vector2f pair = new Vector2f();
		if (!this.values.world().distantDepthPair(pair)) {
			return false;
		}

		// The pipeline before the images, so that a driver which will not have this shader never
		// allocates the two it would then hold for the rest of the pack. The refusal that comes
		// later, after a resource reload has emptied the device's cache, hands them back itself.
		RenderPipeline compiled = seedPipeline(device);
		if (compiled == null || !ensureBlended(device, this.depthWidth, this.depthHeight)) {
			return false;
		}

		GpuBufferSlice block = OCCLUSION.write(device, pair);

		// Neither attachment is cleared, and neither needs to be: the draw covers both whole, the
		// depth test being ALWAYS and the colour written on every texel.
		try (RenderPass pass = device.createCommandEncoder().createRenderPass(
				() -> SEED_LABEL, this.worldCarried.view(), Optional.empty(), this.blendedView,
				java.util.OptionalDouble.empty())) {
			GraphicsApi.setPipeline(pass, compiled);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setVertexBuffer(0, quad.slice());
			pass.setUniform(SEED_BLOCK, block);
			// NEAREST on both, and it is what makes this a rewrite of the value rather than of the
			// image: one destination texel covers one source texel in each.
			GraphicsApi.bindTexture(pass, SEED_WORLD, world,
					RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
			GraphicsApi.bindTexture(pass, SEED_FAR, this.depthView,
					RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
			pass.draw(SEED_VERTICES, 1, 0, 0);
		}

		return true;
	}

	
	private void recordShadow(GpuDevice device, Element element, List<DhLods.Section> sections,
			Vec3 camera) {
		// Never read from here. The reading opens the pack and expands every include of it, which is
		// not something to do inside the light's own stage; the camera's own halves read at the
		// first frame the far terrain is drawn, and a map is only worth filling for a far terrain
		// something is drawing.
		DistantProgram program = this.programs.get(element.element());
		if (program == null || sections.isEmpty()) {
			return;
		}

		// No volume of its own: this half is drawn in the light's, which the shadow catalogue of the
		// six fixed function names already answers, and the projection handed to a pass is the
		// frame's camera one wherever a pack asks for it by its gbuffers name.
		RenderPipeline pipeline = program.prepare(device, null);
		if (pipeline == null) {
			refuseShadow(element, "prepare", "its program could not be prepared. There are two "
					+ "reasons for that and one of them says so on a line of its own above, the "
					+ "program refusing to compile; the other is a map that is not allocated yet, "
					+ "which passes on its own");

			return;
		}

		// The two arguments are the camera pass's and a shadow program reads neither: it names the
		// map's own attachments and the map's own square.
		RenderPassDescriptor descriptor = program.descriptor(null, null);
		if (descriptor == null) {
			refuseShadow(element, "unallocated", "the shadow map had no image yet on some frame, so "
					+ "the pass this half wanted could not be built then. That comes and goes with "
					+ "the frame rather than lasting");

			return;
		}

		int base = SHADOW_CORNERS.write(device, sections, camera);
		if (base < 0) {
			refuseShadow(element, "sections", "the far terrain grew wider than the block holding its "
					+ "section corners between the two halves of one stage, and the wider block "
					+ "cannot replace the one the half already recorded is drawn from. The next frame "
					+ "has it");

			return;
		}

		try (RenderPass pass = GeometryHold.open(device.createCommandEncoder(), descriptor)) {
			GraphicsApi.setPipeline(pass, pipeline);
			program.bind(pass);

			for (int index = 0; index < sections.size(); index++) {
				pass.setUniform(DistantVertex.SECTION_BLOCK,
						SHADOW_CORNERS.slot(device, base + index));

				for (DhLods.Piece piece : sections.get(index).pieces()) {
					// Asked again here and not only where the section was taken, which is the one
					// thing this half owes to being a frame's width away from its own capture: these
					// are DH's buffers and DH is free to have closed one between its pass and this
					// stage. A section short of a piece is a hole in a shadow; a draw against a
					// closed buffer is the frame.
					if (piece.vertices().isClosed() || piece.indices().isClosed()) {
						continue;
					}

					pass.setIndexBuffer(piece.indices(), IndexType.INT);
					pass.setVertexBuffer(0, piece.vertices().slice());
					pass.drawIndexed(piece.indexCount(), 1, 0, 0, 0);
				}
			}
		}
	}

	
	private void refuseShadow(Element element, String reason, String why) {
		if (this.refused.add("shadow:" + reason + ":" + element.element())) {
			CoreVK.logger().warn("The {} half of the far terrain is not drawn into the shadow map "
					+ "because {}", element.half(), why);
		}
	}

	private boolean record(GpuDevice device, Minecraft minecraft, Element element,
			List<DhLods.Section> sections) {
		if (!this.read) {
			return false;
		}

		DistantProgram program = this.programs.get(element.element());
		if (program == null) {
			return false;
		}

		// The water half only over an opaque half somebody drew in the same frame, and the somebody
		// has to be the same engine on both. Handed back here, DH draws BOTH halves itself and its
		// images agree with each other; said once, because this is the one hand-back of the class
		// whose reason is the frame's shape rather than a failure.
		if (element.afterDeferred() && !this.drew) {
			if (this.refused.add("water-without-opaque")) {
				CoreVK.logger().info("A frame's far terrain had a water half and no opaque half "
						+ "drawn by this engine, so such frames go back to Distant Horizons whole");
			}

			return false;
		}

		RenderTarget main = minecraft.gameRenderer.mainRenderTarget();
		if (main == null || main.getColorTextureView() == null) {
			return false;
		}

		this.owner.beginFrame();
		if (!this.owner.openTargets(device) || (this.chainRuns && !this.owner.drawable())) {
			return false;
		}

		if (!ensureDepth(device, main)) {
			return false;
		}

		RenderPipeline pipeline = program.prepare(device, this.values.world().drawnDistantProjection());
		if (pipeline == null) {
			return refuse(element, "prepare:" + element.element(), "the " + element.element()
					+ " program refused to prepare, which it says on its own line above. That is "
					+ "settled for as long as this pack is loaded, so the far terrain keeps Distant "
					+ "Horizons' own shader steadily rather than as a flicker");
		}

		// The world's depth goes under this half here and not one stage earlier, which is what
		// makes the player's own body stand in front of the water rather than only his cape:
		// seedWater says why. Once a frame, and only for the half that needs it.
		if (element.afterDeferred() && !this.seeded) {
			seedWater(device, main);
		}

		// The water half rasterises against the seeded image where there is one, and that is the
		// whole of the occlusion: the depth test does the rest. Everything else, the opaque half
		// and both halves of the light, keeps the pure image, which is what the two takes read.
		//
		// The flag is the whole of the question and no size is compared beside it, which is only
		// true since the seed moved into this method: it and ensureDepth now run in the same call,
		// so the pair cannot be left at a size the far terrain's own image has moved off.
		GpuTextureView into = element.afterDeferred() && this.seeded
				? this.blendedView
				: this.depthView;

		RenderPassDescriptor descriptor = program.descriptor(main.getColorTextureView(), into);
		if (descriptor == null && !program.plain()) {
			return refuse(element, "unallocated:" + element.element(), "one of the pack's colour "
					+ "targets had no image yet on some frame, so the pass this half wanted could not "
					+ "be built then. That comes and goes with the frame rather than lasting");
		}

		CommandEncoder encoder = device.createCommandEncoder();

		// Emptied at the first half of the frame that gets this far, and asked that way rather than
		// keyed on the opaque half: a half that refused, or a pack that serves only the other one,
		// would otherwise leave the image holding the frame before it, and the takes would hand the
		// pack last frame's far terrain in a world that has moved on.
		//
		// Nought is DH's own clear and the far plane of a reversed Z, so an untouched pixel reads as
		// nothing drawn, which converts to the far plane the pack tests for.
		//
		// Paid as the load-op of the pass that is about to attach the image, and not as a command of
		// its own: an encoder clear is a vkCmdClearDepthStencilImage plus the full pipeline drain the
		// backend appends to every clear it performs, where a load-op costs the pass nothing it was
		// not already paying. That drain is not what ordered the emptying against the draws before it,
		// though: every pass already ends on the same ALL_COMMANDS barrier the clear posted
		// (VulkanCommandEncoder.submitRenderPass), so what the drain added was a second one. Nothing
		// reads the image in between either: it leaves this class through served()
		// alone, which answers null until drew is set, and drew is set below this pass. A half that
		// gives up between here and the pass therefore leaves the image as the last frame left it,
		// which the same guard keeps out of the pack's hands.
		OptionalDouble clear = OptionalDouble.empty();
		if (!this.drew) {
			if (into == this.depthView && coversDepth(descriptor)) {
				clear = OptionalDouble.of(0.0);

				// The hold is ended first because a joined pass applies no load-op, which
				// GeometryHold.open says out loud, and the emptying would go silently missing. This
				// costs nothing that was not already spent: the encoder clear standing here ended
				// the hold too, through the door CommandEncoderMixin holds open for every clear.
				GeometryHold.flush(() -> "the far terrain's depth being emptied");
				if (descriptor != null) {
					descriptor = GraphicsApi.withDepthAttachment(descriptor, into, clear);
				}
			} else {
				encoder.clearDepthTexture(this.depth, 0.0);
			}
		}

		// The camera is the game's own and not DH's copy of it, although DH hands one in: everything
		// else this engine places is placed against the game's, and a far terrain placed against a
		// second reading of the same position would stand a fraction of a block away from the near
		// terrain it meets.
		int base = CORNERS.write(device, sections, minecraft.gameRenderer.mainCamera().position());
		if (base < 0) {
			return refuse(element, "sections", "the far terrain grew wider than the block holding its "
					+ "section corners between the two halves of one frame, and the wider block cannot "
					+ "replace the one the half already recorded is drawn from. The next frame has it");
		}

		try (RenderPass pass = descriptor == null
				? encoder.createRenderPass(() -> "CoreVK " + element.element(),
						main.getColorTextureView(), Optional.empty(), into, clear)
				: GeometryHold.open(encoder, descriptor)) {
			GraphicsApi.setPipeline(pass, pipeline);
			program.bind(pass);

			for (int index = 0; index < sections.size(); index++) {
				pass.setUniform(DistantVertex.SECTION_BLOCK, CORNERS.slot(device, base + index));

				for (DhLods.Piece piece : sections.get(index).pieces()) {
					// The shadow half's guard, asked here for what it says as much as for what
					// it spares. The capture looks at the vertex buffer alone and nothing has
					// ever looked at the index one, so a piece whose index buffer DH closed
					// before it handed the set over is drawn from freed memory two submissions
					// later, which is a device loss with no exception and no line to its name.
					// Which of the two it names is the whole of what this is here to learn.
					if (piece.vertices().isClosed() || piece.indices().isClosed()) {
						String closed = piece.vertices().isClosed() ? "vertex" : "index";
						if (this.refused.add("closed:" + closed + ":" + element.element())) {
							CoreVK.logger().warn("A piece of the {} half of the far terrain reached "
									+ "this frame's draw with its {} buffer closed, and is dropped. "
									+ "Distant Horizons closed it between its own pass and this one",
									element.half(), closed);
						}

						continue;
					}

					pass.setIndexBuffer(piece.indices(), IndexType.INT);
					pass.setVertexBuffer(0, piece.vertices().slice());
					pass.drawIndexed(piece.indexCount(), 1, 0, 0, 0);
				}
			}
		}

		this.drew = true;

		return true;
	}

	
	private boolean coversDepth(RenderPassDescriptor descriptor) {
		if (descriptor == null) {
			return true;
		}

		RenderPass.RenderArea area = GraphicsApi.renderArea(descriptor);

		return area != null && area.x() == 0 && area.y() == 0
				&& area.width() >= this.depthWidth && area.height() >= this.depthHeight;
	}

	
	private boolean ensureDepth(GpuDevice device, RenderTarget main) {
		if (this.depth != null && this.depthWidth == main.width && this.depthHeight == main.height) {
			return true;
		}

		releaseDepth();
		this.depthWidth = main.width;
		this.depthHeight = main.height;
		if (this.depthWidth <= 0 || this.depthHeight <= 0) {
			return false;
		}

		// Three usages and every one of them is asked for by name somewhere: drawn into as an
		// attachment, sampled by the window takes, and emptied by the encoder here at birth and on
		// the frames the pass's own load-op cannot pay for. That last one is not obvious: an encoder
		// refuses to clear a depth image that was not also made a copy destination
		// (CommandEncoder.verifyDepthTexture), a clear being a write it performs itself rather than a
		// load the pass does.
		this.depth = device.createTexture(() -> "CoreVK far terrain depth",
				GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING
						| GpuTexture.USAGE_COPY_DST,
				DEPTH_FORMAT, this.depthWidth, this.depthHeight, 1, 1);
		this.depthView = device.createTextureView(this.depth);

		// Emptied at birth and not only at the head of a frame: remade between the two halves of
		// one frame, on a resize, the per frame clear has already run and the water half would
		// otherwise rasterise against whatever the driver left here.
		device.createCommandEncoder().clearDepthTexture(this.depth, 0.0);

		return true;
	}

	
	private boolean ensureBlended(GpuDevice device, int width, int height) {
		if (this.blended != null && this.blendedWidth == width && this.blendedHeight == height) {
			return true;
		}

		releaseBlended();
		this.blendedWidth = width;
		this.blendedHeight = height;
		if (width <= 0 || height <= 0) {
			return false;
		}

		// Two usages here and three on the pure image beside it: this one is drawn into and read by
		// the take that follows the water half, and never cleared, the seed covering it whole.
		this.blended = device.createTexture(() -> "CoreVK far terrain depth with the world",
				GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING,
				DEPTH_FORMAT, width, height, 1, 1);
		this.blendedView = device.createTextureView(this.blended);
		this.worldCarried = new TargetSurface("CoreVK world depth in the far terrain's volume",
				CARRIED_FORMAT, false, width, height);

		// Said out loud rather than left to be discovered, like every other pair this engine
		// allocates: this is the cost of the far terrain's water standing behind the world, and of
		// nothing else.
		CoreVK.logger().info("The world's depth is carried into the far terrain's volume in two "
				+ "images at {}x{}, {} MiB", width, height,
				(2L * this.worldCarried.bytes()) / (1024L * 1024L));

		return true;
	}

	
	private RenderPipeline seedPipeline(GpuDevice device) {
		if (this.seedPipeline == null) {
			this.seedPipeline = buildSeed();
		}

		if (GraphicsApi.valid(GraphicsApi.compile(device, this.seedPipeline, SEED_SOURCE))) {
			return this.seedPipeline;
		}

		// Handed back and not merely refused: this road is reached after frames have drawn, the
		// compile being asked again at every resource reload, so the two images would otherwise
		// stay resident for the rest of the pack with nothing left to write them.
		this.seedBroken = true;
		this.seedPipeline = null;
		releaseBlended();
		CoreVK.logger().error("The far terrain's occlusion pass did not compile, so its water paints "
				+ "over whatever the game drew between the two halves of the far terrain");

		return null;
	}

	private static RenderPipeline buildSeed() {
		return RenderPipeline.builder()
				.withLocation(
						Identifier.fromNamespaceAndPath(CoreVK.MOD_ID, "pipeline/distant_occlusion"))
				.withVertexShader(SEED_VERTEX_ID)
				.withFragmentShader(SEED_FRAGMENT_ID)
				.withBindGroupLayout(BindGroupLayouts.GLOBALS)
				.withBindGroupLayout(GraphicsApi.blockAndSamplers(SEED_BLOCK, SEED_WORLD, SEED_FAR))
				.withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
				.withColorTargetState(new ColorTargetState(Optional.empty(), CARRIED_FORMAT,
						ColorTargetState.WRITE_ALL))
				// ALWAYS and not the reversed test every other pass of this engine carries: what is
				// written is already the nearer of the two, worked out in the shader, and a test
				// against what the image held would keep the frame before it instead.
				.withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, true))
				.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
				.withCull(false)
				.build();
	}

	
	void prefetch() {
		prefetch(null);
	}

	
	@Override
	void prefetch(OpenedPack shared) {
		if (!this.read) {
			read(shared);
		}
	}

	
	private void read(OpenedPack shared) {
		try {
			List<Element> asked = ELEMENTS.values().stream().filter(this::wanted).toList();
			List<PackProgram.GeometryElement> names = asked.stream().map(Element::asked).toList();
			PackProgram.Distant distant = shared != null
					? PackProgram.loadDistant(shared, this.place, names)
					: PackProgram.loadDistant(this.packPath, this.place, names, this.chosen, this.profile);
			if (distant.programs().isEmpty()) {
				CoreVK.logger().info("{} serves nothing in {} for the far terrain, so Distant "
						+ "Horizons keeps drawing it with its own shader", this.packPath.getFileName(),
						this.place.isEmpty() ? "its root" : this.place);

				return;
			}

			this.carried = distant.carried();
			for (Element element : asked) {
				PackProgram.Loaded one = distant.programs().get(element.element());
				if (one == null) {
					// Said for the picture's halves alone. The light's two resolve one name between
					// them, and that name has no parent in the fallback tree, so a pack without a
					// dh_shadow is the ordinary case rather than a gap: the line further down says it
					// once for the pair.
					if (!element.shadow()) {
						CoreVK.logger().info("{} serves nothing in {} for the {} half of the far "
								+ "terrain", this.packPath.getFileName(),
								this.place.isEmpty() ? "its root" : this.place, element.half());
					}

					continue;
				}

				List<ChainPlan.Attachment> writes = writes(element, one);
				if (writes != null) {
					this.programs.put(element.element(), DistantProgram.of(one, element, this.carried,
							this.values, this.load, writes, this.chainTargets, this.targets,
							this.chainRuns));
				}
			}

			// The two halves of the PICTURE together or not at all. One landscape drawn by two
			// engines does not compose: whichever went back to DH is composited by DH's apply pass
			// out of an image holding that half alone, with nothing left in its depth to occlude it,
			// so far water would show through the hills in front of it. Rarely reached at all: a
			// pack without one of the two files resolves it through its own fallback tree first.
			//
			// And the light's halves go back with them, which is CoreVK's own shape: every road its
			// events take is behind shouldOverride, so a far terrain handed back whole is handed back
			// from the map as well (compat/dh/LodRendererEvents.java:216-227 and :251-259).
			if (served(false) == 1) {
				CoreVK.logger().info("The far terrain goes back to Distant Horizons whole: the pack "
						+ "serves one half of it and the two only compose together");
				this.programs.values().forEach(DistantProgram::release);
				this.programs.clear();
			} else if (served(true) == 0) {
				sayNothingCastsIntoTheMap();
			}
		} catch (IOException | RuntimeException e) {
			CoreVK.logger().error("Could not prepare the far terrain programs of "
					+ this.packPath.getFileName() + ", so Distant Horizons keeps drawing it with its "
					+ "own shader", e);
		} finally {
			this.read = true;
		}
	}

	
	private void sayNothingCastsIntoTheMap() {
		if (!TerrainDraw.shadowsAsked()) {
			return;
		}

		if (!this.values.dhShadow()) {
			CoreVK.logger().info("{} asks with dhShadow.enabled that its far terrain stay out of "
					+ "its shadow map, so nothing of it is drawn there and what shades it is what "
					+ "the pack's own programs work out of its depth", this.packPath.getFileName());

			return;
		}

		CoreVK.logger().info("{} serves no dh_shadow in {}, so the far terrain casts nothing into "
				+ "the pack's shadow map and what shades it is what the pack's own programs work "
				+ "out of its depth", this.packPath.getFileName(),
				this.place.isEmpty() ? "its root" : this.place);
	}

	
	private boolean wanted(Element element) {
		return !element.shadow() || (TerrainDraw.shadowsAsked() && this.values.dhShadow());
	}

	
	private long served(boolean shadow) {
		return ELEMENTS.values().stream()
				.filter(element -> element.shadow() == shadow)
				.filter(element -> this.programs.containsKey(element.element()))
				.count();
	}

	
	private List<ChainPlan.Attachment> writes(Element element, PackProgram.Loaded loaded) {
		// The light's halves write the map's own colour targets, which no chain plan carries a word
		// about: GeometryProgram builds their attachments out of the program's own draw buffers and
		// the map's, and reads nothing of this list.
		if (element.shadow()) {
			return List.of();
		}

		String servedBy = loaded.path().substring(loaded.path().lastIndexOf('/') + 1);
		Optional<ChainPlan.Pass> geometry = this.plan.geometryOf(servedBy, element.afterDeferred());
		if (geometry.isEmpty()) {
			return List.of();
		}

		ChainPlan.Pass pass = geometry.get();
		if (!pass.size().equals(TargetSize.ofScreen())) {
			CoreVK.logger().warn("{} writes targets the pack asked to be scaled, so they cannot share "
					+ "a pass with the game's own target and Distant Horizons keeps drawing the {} half "
					+ "of the far terrain", servedBy, element.half());

			return null;
		}

		return pass.attachments();
	}

	
	private boolean refuse(Element element, String reason, String why) {
		boolean drop = element.afterDeferred() && this.drew;
		if (this.refused.add((drop ? "dropped:" : "") + reason)) {
			CoreVK.logger().warn("The {} half of the far terrain {} because {}", element.half(),
					drop ? "is dropped on such frames, the opaque half being this engine's already"
							: "went back to Distant Horizons' own shader",
					why);
		}

		return drop;
	}

	
	@Override
	Collection<DistantProgram> programs() {
		return this.programs.values();
	}

	
	@Override
	void rotate() {
		this.drew = false;
		// Dropped here and not where the seed is written, for the reason every per frame flag of
		// this class is dropped here: a frame that draws no far terrain would otherwise hand the
		// take an image seeded a frame ago, against a camera that has moved.
		this.seeded = false;
		this.shadowOpaque = this.opaqueSections;
		this.shadowWater = this.waterSections;
		this.opaqueSections = List.of();
		this.waterSections = List.of();
		CORNERS.rotate();
		SHADOW_CORNERS.rotate();
		OCCLUSION.rotate();
		if (this.read) {
			this.programs.values().forEach(DistantProgram::rotate);
		}
	}

	@Override
	void release() {
		this.programs.values().forEach(DistantProgram::release);
		this.programs.clear();
		this.refused.clear();
		this.carried = List.of();
		this.read = false;
		this.drew = false;
		this.broken = false;
		this.shadowBroken = false;
		this.opaqueSections = List.of();
		this.waterSections = List.of();
		this.shadowOpaque = List.of();
		this.shadowWater = List.of();
		// NOT closed, which is the whole of issue 111: a recorded pass holds slices of these
		// two rings, and a load tears the chain down in the middle of a frame that has
		// already bound them. What is dropped here is what this load wrote into them.
		CORNERS.forget();
		SHADOW_CORNERS.forget();

		this.seedBroken = false;
		this.seedPipeline = null;
		releaseBlended();
		releaseDepth();
	}

	
	static void close() {
		CORNERS.close();
		SHADOW_CORNERS.close();
		OCCLUSION.close();
	}

	private void releaseBlended() {
		if (this.blendedView != null) {
			this.blendedView.close();
			this.blendedView = null;
		}

		if (this.blended != null) {
			this.blended.close();
			this.blended = null;
		}

		if (this.worldCarried != null) {
			this.worldCarried.close();
			this.worldCarried = null;
		}

		this.seeded = false;
	}

	private void releaseDepth() {
		if (this.depthView != null) {
			this.depthView.close();
			this.depthView = null;
		}

		if (this.depth != null) {
			this.depth.close();
			this.depth = null;
		}
	}

	
	private static final class Corners {

		private final String label;

		private MappableRingBuffer buffer;
		private int slots;

		
		private int used;

		
		private int peak;

		Corners(String label) {
			this.label = label;
		}

		
		int write(GpuDevice device, List<DhLods.Section> sections, Vec3 camera) {
			int stride = slotBytes(device);
			// Room for BOTH halves and not for the one at hand, which is the whole reason the wanted
			// count is doubled at the head of a frame: the second half of a frame cannot be given a
			// wider buffer, the first half already holding slices of the one that stands. Sized off
			// the half in hand alone, a frame whose two halves each carry sixty sections would fit
			// the first and refuse the second on every frame for as long as the horizon stayed that
			// wide. The peak covers the case the doubling cannot: a second half wider than twice the
			// first is refused once, and the frame after allocates what the refused frame really
			// spent.
			int wanted = Math.max(this.peak,
					this.used == 0 ? 2 * sections.size() : this.used + sections.size());
			if (this.buffer == null || this.slots < wanted) {
				// Not while a half of this frame is already drawn from it: closing the buffer that
				// pass holds slices of would pull the ground out from under a recorded draw. The
				// caller says so, and the refusal itself teaches the width: the next frame allocates
				// what this one wanted rather than a guess that already fell short once.
				if (this.used > 0) {
					this.peak = Math.max(this.peak, wanted);

					return -1;
				}

				if (this.buffer != null) {
					this.buffer.close();
				}

				// In steps rather than to the exact count, so that a player walking into a wider
				// horizon does not reallocate on every frame.
				this.slots = Math.max(64, Mth.smallestEncompassingPowerOfTwo(wanted));
				this.buffer = new MappableRingBuffer(() -> this.label,
						GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, this.slots * stride);
			}

			int base = this.used;
			try (GpuBufferSlice.MappedView view = this.buffer.currentBuffer().map(false, true)) {
				ByteBuffer data = view.data();
				for (int index = 0; index < sections.size(); index++) {
					DhLods.Section section = sections.get(index);
					data.position((base + index) * stride);
					Std140Builder.intoBuffer(data).putVec3(
							(float) (section.x() - camera.x),
							(float) (section.y() - camera.y),
							(float) (section.z() - camera.z));
				}
			}

			this.used = base + sections.size();

			return base;
		}

		
		GpuBufferSlice slot(GpuDevice device, int index) {
			return this.buffer.currentBuffer().slice((long) index * slotBytes(device), BLOCK_BYTES);
		}

		
		private static int slotBytes(GpuDevice device) {
			return Mth.roundToward(BLOCK_BYTES,
					device.getDeviceInfo().limits().minUniformOffsetAlignment());
		}

		void rotate() {
			this.used = 0;
			if (this.buffer != null) {
				this.buffer.rotate();
			}
		}

		
		void forget() {
			this.used = 0;
			this.peak = 0;
		}

		
		void close() {
			if (this.buffer != null) {
				this.buffer.close();
				this.buffer = null;
			}
		}
	}

	
	private static final class Occlusion {

		private MappableRingBuffer buffer;

		
		GpuBufferSlice write(GpuDevice device, Vector2f pair) {
			if (this.buffer == null) {
				this.buffer = new MappableRingBuffer(() -> SEED_LABEL,
						GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, slotBytes(device));
			}

			try (GpuBufferSlice.MappedView view = this.buffer.currentBuffer().map(false, true)) {
				Std140Builder.intoBuffer(view.data()).putVec2(pair.x, pair.y);
			}

			return this.buffer.currentBuffer().slice(0, SEED_BLOCK_BYTES);
		}

		
		private static int slotBytes(GpuDevice device) {
			return Mth.roundToward(SEED_BLOCK_BYTES,
					device.getDeviceInfo().limits().minUniformOffsetAlignment());
		}

		void rotate() {
			if (this.buffer != null) {
				this.buffer.rotate();
			}
		}

		void close() {
			if (this.buffer != null) {
				this.buffer.close();
				this.buffer = null;
			}
		}
	}
}
