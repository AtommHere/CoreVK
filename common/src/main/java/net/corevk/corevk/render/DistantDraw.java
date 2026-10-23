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

		if (this.broken || this.seedBroken || !this.drew
				|| this.targets.depth().distantRefused()) {
			return;
		}

		try {

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

		if (world == null || quad == null || main.width != this.depthWidth
				|| main.height != this.depthHeight) {
			return false;
		}

		Vector2f pair = new Vector2f();
		if (!this.values.world().distantDepthPair(pair)) {
			return false;
		}

		RenderPipeline compiled = seedPipeline(device);
		if (compiled == null || !ensureBlended(device, this.depthWidth, this.depthHeight)) {
			return false;
		}

		GpuBufferSlice block = OCCLUSION.write(device, pair);

		try (RenderPass pass = device.createCommandEncoder().createRenderPass(
				() -> SEED_LABEL, this.worldCarried.view(), Optional.empty(), this.blendedView,
				java.util.OptionalDouble.empty())) {
			GraphicsApi.setPipeline(pass, compiled);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setVertexBuffer(0, quad.slice());
			pass.setUniform(SEED_BLOCK, block);

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

		DistantProgram program = this.programs.get(element.element());
		if (program == null || sections.isEmpty()) {
			return;
		}

		RenderPipeline pipeline = program.prepare(device, null);
		if (pipeline == null) {
			refuseShadow(element, "prepare", "its program could not be prepared. There are two "
					+ "reasons for that and one of them says so on a line of its own above, the "
					+ "program refusing to compile; the other is a map that is not allocated yet, "
					+ "which passes on its own");

			return;
		}

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

		if (element.afterDeferred() && !this.seeded) {
			seedWater(device, main);
		}

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

		OptionalDouble clear = OptionalDouble.empty();
		if (!this.drew) {
			if (into == this.depthView && coversDepth(descriptor)) {
				clear = OptionalDouble.of(0.0);

				GeometryHold.flush(() -> "the far terrain's depth being emptied");
				if (descriptor != null) {
					descriptor = GraphicsApi.withDepthAttachment(descriptor, into, clear);
				}
			} else {
				encoder.clearDepthTexture(this.depth, 0.0);
			}
		}

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

		this.depth = device.createTexture(() -> "CoreVK far terrain depth",
				GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING
						| GpuTexture.USAGE_COPY_DST,
				DEPTH_FORMAT, this.depthWidth, this.depthHeight, 1, 1);
		this.depthView = device.createTextureView(this.depth);

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

		this.blended = device.createTexture(() -> "CoreVK far terrain depth with the world",
				GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING,
				DEPTH_FORMAT, width, height, 1, 1);
		this.blendedView = device.createTextureView(this.blended);
		this.worldCarried = new TargetSurface("CoreVK world depth in the far terrain's volume",
				CARRIED_FORMAT, false, width, height);

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

			int wanted = Math.max(this.peak,
					this.used == 0 ? 2 * sections.size() : this.used + sections.size());
			if (this.buffer == null || this.slots < wanted) {

				if (this.used > 0) {
					this.peak = Math.max(this.peak, wanted);

					return -1;
				}

				if (this.buffer != null) {
					this.buffer.close();
				}

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
