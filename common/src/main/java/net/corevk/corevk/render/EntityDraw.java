package net.corevk.corevk.render;

import net.corevk.corevk.glsl.LegacyGlsl;
import net.corevk.corevk.glsl.LinesVertex;
import net.corevk.corevk.glsl.PackProgram;
import net.corevk.corevk.glsl.TranslatedUnit;
import net.corevk.corevk.glsl.VertexInputs;
import net.corevk.corevk.pack.option.OptionValue;
import net.corevk.corevk.pack.model.AlphaTest;
import net.corevk.corevk.pack.model.ProgramStage;
import net.corevk.corevk.pack.model.RenderStage;
import net.corevk.corevk.pack.source.OpenedPack;
import net.corevk.corevk.pack.target.ChainPlan;
import net.corevk.corevk.pack.model.TargetName;
import net.corevk.corevk.pack.target.TargetPlan;
import net.corevk.corevk.pack.model.TargetSize;
import net.corevk.corevk.CoreVK;

import com.mojang.blaze3d.GpuDeviceLossException;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.ScissorState;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;

import org.joml.Matrix4fc;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class EntityDraw extends FamilyDraw {

	private static volatile boolean wanted;

	private static volatile boolean opaqueFeatures;

	private static volatile boolean translucentFeatures;

	private static volatile boolean shadowFeatures;

	private static final String LABEL = "CoreVK entity";

	private static final String TEXTURE = "Sampler0";

	record Element(RenderPipeline pipeline, String element, String program, AlphaTest alphaTest,
			RenderStage stage, boolean afterDeferred, boolean fullbright) {

		Element(RenderPipeline pipeline, String element, String program, AlphaTest alphaTest) {
			this(pipeline, element, program, alphaTest, RenderStage.NONE, false, false);
		}

		Element(RenderPipeline pipeline, String element, String program, AlphaTest alphaTest,
				RenderStage stage, boolean afterDeferred) {
			this(pipeline, element, program, alphaTest, stage, afterDeferred, false);
		}

		boolean hand() {
			return this.stage == RenderStage.HAND_SOLID || this.stage == RenderStage.HAND_TRANSLUCENT;
		}

		boolean shadow() {
			return SHADOW_ENTITIES.equals(this.program) || SHADOW_CUTOUT.equals(this.program)
					|| SHADOW_WATER.equals(this.program);
		}

		@SuppressWarnings("ReferenceEquality")
		boolean glint() {
			return this.pipeline == RenderPipelines.GLINT;
		}

		@SuppressWarnings("ReferenceEquality")
		boolean crumbling() {
			return this.pipeline == RenderPipelines.CRUMBLING;
		}

		boolean movingBlock() {
			return EntityMesh.movingBlock(this.pipeline);
		}

		@SuppressWarnings("ReferenceEquality")
		boolean lines() {
			return this.pipeline == RenderPipelines.LINES
					|| this.pipeline == RenderPipelines.LINES_TRANSLUCENT
					|| this.pipeline == RenderPipelines.SECONDARY_BLOCK_OUTLINE;
		}

		boolean text() {
			return TEXT_FORMATS.containsKey(this.pipeline);
		}

		boolean intensity() {
			return TEXT_INTENSITY.contains(this.pipeline);
		}

		VertexFormat format() {
			if (glint()) {
				return DefaultVertexFormat.POSITION_TEX;
			}

			if (lines()) {
				return DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH;
			}

			VertexFormat glyph = TEXT_FORMATS.get(this.pipeline);
			if (glyph != null) {
				return glyph;
			}

			if (movingBlock()) {
				return EntityMesh.movingBlockFormat();
			}

			return crumbling() ? DefaultVertexFormat.BLOCK : EntityMesh.format();
		}

		List<String> bound() {
			return text()
					? format().getElements().stream().map(VertexFormatElement::name).toList()
					: inputs().elements();
		}

		VertexInputs inputs() {
			if (glint()) {
				return VertexInputs.GLINT;
			}

			if (crumbling()) {
				return VertexInputs.CRUMBLING;
			}

			if (movingBlock()) {
				return VertexInputs.MOVING_BLOCK;
			}

			if (lines()) {
				return VertexInputs.LINES;
			}

			if (text()) {
				return VertexInputs.GLYPH;
			}

			return this.fullbright ? VertexInputs.ENTITY_FULLBRIGHT : VertexInputs.ENTITY;
		}

		String family() {
			if (shadow()) {
				return "entities in the shadow map";
			}

			if (glint()) {
				return "glint";
			}

			if (crumbling()) {
				return "block breaking";
			}

			if (movingBlock()) {
				return shadow() ? "moving blocks in the shadow map" : "moving blocks";
			}

			if (lines()) {
				return "block outline";
			}

			if (text()) {
				return "text";
			}

			return hand() ? "hand" : "entities";
		}

		boolean afterStage() {
			if (shadow()) {
				return false;
			}

			if (hand() || glint() || crumbling()) {
				return this.afterDeferred;
			}

			return blended() && !GamePipelines.solidPhase(this.pipeline);
		}

		boolean covers() {
			return !shadow() && !afterStage();
		}

		private PackProgram.GeometryElement asked() {
			return new PackProgram.GeometryElement(this.element, this.program, this.alphaTest,
					inputs(), bound(), covers());
		}

		boolean blended() {
			return GraphicsApi.colorTarget(this.pipeline).blendFunction().isPresent();
		}

		private Matrix4fc modelView() {
			return hand() ? RenderSystem.getModelViewMatrixCopy() : null;
		}
	}

	private static final String ENTITIES = "gbuffers_entities";

	private static final String BLOCK = "gbuffers_block";

	private static final String TERRAIN_SOLID = "gbuffers_terrain_solid";

	private static final String TERRAIN_CUTOUT = "gbuffers_terrain_cutout";

	private static final String ENTITIES_TRANSLUCENT = "gbuffers_entities_translucent";

	private static final String BLOCK_TRANSLUCENT = "gbuffers_block_translucent";

	private static final String DAMAGED_BLOCK = "gbuffers_damagedblock";

	private static final String LINE = "gbuffers_line";

	private static final String SPIDER_EYES = "gbuffers_spidereyes";

	private static final AlphaTest CUTOUT = AlphaTest.ONE_TENTH;

	private static final Map<RenderPipeline, VertexFormat> TEXT_FORMATS = textFormats();

	private static Map<RenderPipeline, VertexFormat> textFormats() {
		Map<RenderPipeline, VertexFormat> formats = new HashMap<>();
		formats.put(RenderPipelines.TEXT, DefaultVertexFormat.POSITION_TEX_LIGHTMAP_COLOR);
		formats.put(RenderPipelines.TEXT_GRAYSCALE, DefaultVertexFormat.POSITION_TEX_LIGHTMAP_COLOR);
		formats.put(RenderPipelines.TEXT_POLYGON_OFFSET,
				DefaultVertexFormat.POSITION_TEX_LIGHTMAP_COLOR);
		formats.put(RenderPipelines.TEXT_GRAYSCALE_POLYGON_OFFSET,
				DefaultVertexFormat.POSITION_TEX_LIGHTMAP_COLOR);
		formats.put(RenderPipelines.TEXT_SEE_THROUGH, DefaultVertexFormat.POSITION_TEX_COLOR);
		formats.put(RenderPipelines.TEXT_GRAYSCALE_SEE_THROUGH, DefaultVertexFormat.POSITION_TEX_COLOR);

		RenderPipeline background = GamePipelines.textBackground();
		if (background != null) {
			formats.put(background, DefaultVertexFormat.POSITION_COLOR_LIGHTMAP);
		}

		RenderPipeline seeThrough = GamePipelines.textBackgroundSeeThrough();
		if (seeThrough != null) {
			formats.put(seeThrough, DefaultVertexFormat.POSITION_COLOR);
		}

		return Map.copyOf(formats);
	}

	private static final Set<RenderPipeline> TEXT_INTENSITY = Set.of(
			RenderPipelines.TEXT_GRAYSCALE,
			RenderPipelines.TEXT_GRAYSCALE_POLYGON_OFFSET,
			RenderPipelines.TEXT_GRAYSCALE_SEE_THROUGH);

	private static final Set<RenderPipeline> PINNED = Stream.of(
			RenderPipelines.END_CRYSTAL_BEAM,
			RenderPipelines.ENTITY_CUTOUT_Z_OFFSET,
			RenderPipelines.ENERGY_SWIRL,
			GamePipelines.textBackground(),
			GamePipelines.textBackgroundSeeThrough())
			.filter(Objects::nonNull)
			.collect(Collectors.toUnmodifiableSet());

	private static final Map<RenderPipeline, Element> ELEMENTS = new LinkedHashMap<>();

	static {
		put(new Element(RenderPipelines.ENTITY_SOLID, "solid", ENTITIES, AlphaTest.OFF));
		put(new Element(RenderPipelines.ENTITY_SOLID_Z_OFFSET_FORWARD, "solid_offset", ENTITIES,
				AlphaTest.OFF));
		put(new Element(RenderPipelines.ENTITY_CUTOUT, "cutout", ENTITIES, CUTOUT));
		put(new Element(RenderPipelines.ENTITY_CUTOUT_CULL, "cutout_cull", ENTITIES, CUTOUT));
		put(new Element(RenderPipelines.ENTITY_CUTOUT_Z_OFFSET, "cutout_offset", ENTITIES, CUTOUT));
		put(new Element(RenderPipelines.ENTITY_CUTOUT_DISSOLVE, "cutout_dissolve", ENTITIES, CUTOUT));
		put(new Element(RenderPipelines.ARMOR_CUTOUT_NO_CULL, "armor", ENTITIES, CUTOUT));
		put(new Element(RenderPipelines.ARMOR_DECAL_CUTOUT_NO_CULL, "armor_decal", ENTITIES, CUTOUT));
		put(new Element(RenderPipelines.END_CRYSTAL_BEAM, "crystal_beam", ENTITIES, CUTOUT));
		put(new Element(RenderPipelines.ITEM_CUTOUT, "item", ENTITIES, CUTOUT));

		put(new Element(RenderPipelines.ENTITY_TRANSLUCENT, "translucent", ENTITIES_TRANSLUCENT,
				CUTOUT));
		put(new Element(RenderPipelines.ENTITY_TRANSLUCENT_CULL, "translucent_cull",
				ENTITIES_TRANSLUCENT, CUTOUT));

		put(new Element(GamePipelines.armorTranslucent(), "armor_translucent", ENTITIES_TRANSLUCENT,
				CUTOUT));
		put(new Element(RenderPipelines.ITEM_TRANSLUCENT, "item_translucent", ENTITIES_TRANSLUCENT,
				CUTOUT));
		put(new Element(RenderPipelines.BANNER_PATTERN, "banner", ENTITIES_TRANSLUCENT, CUTOUT));

		put(new Element(RenderPipelines.ENTITY_SHADOW, "ground_shadow", ENTITIES_TRANSLUCENT, CUTOUT));

		put(new Element(RenderPipelines.BREEZE_WIND, "breeze_wind", ENTITIES_TRANSLUCENT, CUTOUT));

		put(new Element(RenderPipelines.ENERGY_SWIRL, "energy_swirl", ENTITIES, CUTOUT));

		put(new Element(RenderPipelines.TEXT, "text", ENTITIES_TRANSLUCENT, CUTOUT));
		put(new Element(RenderPipelines.TEXT_GRAYSCALE, "text_grayscale", ENTITIES_TRANSLUCENT,
				CUTOUT));
		put(new Element(RenderPipelines.TEXT_POLYGON_OFFSET, "text_offset", ENTITIES_TRANSLUCENT,
				CUTOUT));
		put(new Element(RenderPipelines.TEXT_GRAYSCALE_POLYGON_OFFSET, "text_grayscale_offset",
				ENTITIES_TRANSLUCENT, CUTOUT));
		put(new Element(RenderPipelines.TEXT_SEE_THROUGH, "text_see_through", ENTITIES_TRANSLUCENT,
				CUTOUT));
		put(new Element(RenderPipelines.TEXT_GRAYSCALE_SEE_THROUGH, "text_grayscale_see_through",
				ENTITIES_TRANSLUCENT, CUTOUT));

		RenderPipeline background = GamePipelines.textBackground();
		if (background != null) {
			put(new Element(background, "text_background", ENTITIES_TRANSLUCENT, CUTOUT));
		}

		RenderPipeline seeThrough = GamePipelines.textBackgroundSeeThrough();
		if (seeThrough != null) {
			put(new Element(seeThrough, "text_background_see_through", ENTITIES_TRANSLUCENT,
					CUTOUT));
		}
	}

	private static final Map<RenderPipeline, Element> BLOCK_ELEMENTS = new LinkedHashMap<>();

	static {

		ELEMENTS.values().stream()
				.map(EntityDraw::blockTwin)
				.forEach(element -> BLOCK_ELEMENTS.put(element.pipeline(), element));
	}

	private static Element blockTwin(Element mob) {
		if (PINNED.contains(mob.pipeline())) {
			return new Element(mob.pipeline(), "block_" + mob.element(), mob.program(),
					mob.alphaTest(), RenderStage.BLOCK_ENTITIES, false);
		}

		return new Element(mob.pipeline(), "block_" + mob.element(),
				mob.blended() ? BLOCK_TRANSLUCENT : BLOCK, CUTOUT,
				RenderStage.BLOCK_ENTITIES, false);
	}

	private static final String HAND = "gbuffers_hand";
	private static final String HAND_WATER = "gbuffers_hand_water";

	private static final Map<RenderPipeline, Element> HAND_ELEMENTS = new LinkedHashMap<>();
	private static final Map<RenderPipeline, Element> HAND_WATER_ELEMENTS = new LinkedHashMap<>();

	static {

		twins(HAND_ELEMENTS, "hand_", HAND, RenderStage.HAND_SOLID, false);
		twins(HAND_WATER_ELEMENTS, "hand_water_", HAND_WATER, RenderStage.HAND_TRANSLUCENT, true);
	}

	private static void twins(Map<RenderPipeline, Element> into, String prefix, String program,
			RenderStage stage, boolean afterDeferred) {
		ELEMENTS.values().stream()
				.map(mob -> new Element(mob.pipeline(), prefix + mob.element(), program,
						mob.text() ? AlphaTest.NON_ZERO : CUTOUT, stage, afterDeferred))
				.forEach(element -> into.put(element.pipeline(), element));
	}

	private static final String SHADOW_ENTITIES = "shadow_entities";

	private static final String SHADOW_CUTOUT = "shadow_cutout";

	private static final String SHADOW_WATER = "shadow_water";

	private static final Map<RenderPipeline, Element> SHADOW_ELEMENTS = new LinkedHashMap<>();

	static {
		shadowTwins(ELEMENTS);
	}

	private static void shadowTwins(Map<RenderPipeline, Element> from) {
		from.values().stream()

				.filter(mob -> mob.pipeline() != RenderPipelines.ENTITY_SHADOW && !mob.lines())
				.map(mob -> {
					if (!mob.movingBlock()) {
						return new Element(mob.pipeline(), "shadow_" + mob.element(), SHADOW_ENTITIES,
							mob.text() ? AlphaTest.NON_ZERO : CUTOUT, RenderStage.ENTITIES, false);
					}

					String program = RenderPipelines.TRANSLUCENT_BLOCK.equals(mob.pipeline())
							? SHADOW_WATER : SHADOW_CUTOUT;
					return new Element(mob.pipeline(), "shadow_" + mob.element(), program, CUTOUT,
							RenderStage.ENTITIES, false);
				})
				.forEach(element -> SHADOW_ELEMENTS.put(element.pipeline(), element));
	}

	private static void put(Element element) {
		ELEMENTS.put(element.pipeline(), element);
	}

	private static final String ARMOR_GLINT = "gbuffers_armor_glint";

	private static final Element GLINT_EARLY = glint("glint", RenderStage.NONE, false);

	private static final Element GLINT_LATE = glint("glint_late", RenderStage.NONE, true);

	private static final Element GLINT_HAND = glint("hand_glint", RenderStage.HAND_SOLID, false);

	private static final Element GLINT_HAND_WATER =
			glint("hand_water_glint", RenderStage.HAND_TRANSLUCENT, true);

	private static Element glint(String element, RenderStage stage, boolean afterDeferred) {
		return new Element(RenderPipelines.GLINT, element, ARMOR_GLINT, AlphaTest.NON_ZERO,
				stage, afterDeferred);
	}

	private static final Element CRUMBLING_EARLY = crumbling("crumbling", false);

	private static final Element CRUMBLING_LATE = crumbling("crumbling_late", true);

	private static Element crumbling(String element, boolean afterDeferred) {
		return new Element(RenderPipelines.CRUMBLING, element, DAMAGED_BLOCK, CUTOUT,
				RenderStage.NONE, afterDeferred, true);
	}

	private static final Map<RenderPipeline, Element> FIXED = new LinkedHashMap<>();

	static {
		FIXED.put(RenderPipelines.EYES, new Element(RenderPipelines.EYES, "eyes", SPIDER_EYES,
				AlphaTest.NON_ZERO, RenderStage.NONE, false, true));
		FIXED.put(RenderPipelines.ENTITY_TRANSLUCENT_EMISSIVE,
				new Element(RenderPipelines.ENTITY_TRANSLUCENT_EMISSIVE, "eyes_emissive", SPIDER_EYES,
						CUTOUT, RenderStage.NONE, false, true));

		FIXED.put(RenderPipelines.SOLID_BLOCK, new Element(RenderPipelines.SOLID_BLOCK,
				"moving_solid", TERRAIN_SOLID, AlphaTest.OFF));
		FIXED.put(RenderPipelines.CUTOUT_BLOCK, new Element(RenderPipelines.CUTOUT_BLOCK,
				"moving_cutout", TERRAIN_CUTOUT, AlphaTest.CUTOUT));
		FIXED.put(RenderPipelines.TRANSLUCENT_BLOCK, new Element(RenderPipelines.TRANSLUCENT_BLOCK,
				"moving_translucent", BLOCK, CUTOUT));

		FIXED.put(RenderPipelines.LINES, new Element(RenderPipelines.LINES, "lines", LINE,
				AlphaTest.OFF, RenderStage.OUTLINE, false, false));
		FIXED.put(RenderPipelines.LINES_TRANSLUCENT, new Element(RenderPipelines.LINES_TRANSLUCENT,
				"lines_translucent", LINE, AlphaTest.OFF, RenderStage.OUTLINE, false, false));
		FIXED.put(RenderPipelines.SECONDARY_BLOCK_OUTLINE,
				new Element(RenderPipelines.SECONDARY_BLOCK_OUTLINE, "outline_secondary", LINE,
						AlphaTest.OFF, RenderStage.OUTLINE, false, false));
	}

	static {

		shadowTwins(FIXED);
	}

	@SuppressWarnings("ReferenceEquality")
	private static Element element(RenderPipeline pipeline) {

		if (shadowFeatures) {
			return SHADOW_ELEMENTS.get(pipeline);
		}

		Element fixed = FIXED.get(pipeline);
		if (fixed != null) {
			return fixed;
		}

		if (pipeline == RenderPipelines.CRUMBLING) {
			return translucentFeatures ? CRUMBLING_LATE : CRUMBLING_EARLY;
		}

		if (HandDraw.drawing()) {
			if (pipeline == RenderPipelines.GLINT) {
				return HandDraw.drawingSolid() ? GLINT_HAND : GLINT_HAND_WATER;
			}

			return (HandDraw.drawingSolid() ? HAND_ELEMENTS : HAND_WATER_ELEMENTS).get(pipeline);
		}

		if (pipeline == RenderPipelines.GLINT) {
			return translucentFeatures ? GLINT_LATE : GLINT_EARLY;
		}

		return BlockEntityGeometry.drawing()
				? BLOCK_ELEMENTS.get(pipeline)
				: ELEMENTS.get(pipeline);
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

	private final boolean seeded;
	private final ColorTargets targets;

	private final Map<String, EntityProgram> programs = new LinkedHashMap<>();

	private Element recordedElement;
	private EntityProgram recordedProgram;

	private volatile boolean read;

	private final Set<String> refused = new LinkedHashSet<>();

	private final Set<RenderPipeline> droppedPipelines = new LinkedHashSet<>();

	private RenderPass open;

	private EntityProgram drawing;
	private RenderPipeline bound;

	EntityDraw(PackChain owner, Path packPath, String place, Map<String, OptionValue> chosen,
			String profile, PackValues values, int load, ChainPlan plan, TargetPlan chainTargets,
			boolean chainRuns, boolean seeded, ColorTargets targets) {
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
		this.seeded = seeded;
		this.targets = targets;
	}

	static void wanted(boolean asked) {
		wanted = asked;
		EntityMesh.ask();
	}

	public static boolean wanted() {
		return wanted;
	}

	void prefetch() {
		prefetch(null);
	}

	@Override
	void prefetch(OpenedPack shared) {
		if (!this.read && (wanted() || HandDraw.wanted())) {
			read(shared);
		}
	}

	public static void opaqueFeatures(boolean drawing) {
		opaqueFeatures = drawing;
		if (!drawing) {
			closeWindow();
		}
	}

	public static void translucentFeatures(boolean drawing) {
		translucentFeatures = drawing;
		if (!drawing) {
			closeWindow();
		}
	}

	public static void shadowFeatures(boolean drawing) {
		shadowFeatures = drawing;
		if (!drawing) {
			closeWindow();
		}
	}

	private static boolean inWindow(Element element) {
		if (element.shadow()) {
			return shadowFeatures;
		}

		return element.afterStage() ? translucentFeatures : opaqueFeatures;
	}

	private static boolean onMainTarget(PreparedRenderType prepared) {
		return GameRender.drawsOnMainTarget(prepared);
	}

	private static void closeWindow() {

		BlockEntityGeometry.clear();

		EntityIdentifiers.clear();

		endGroup();
	}

	public static boolean draw(PreparedRenderType prepared, StagedVertexBuffer.ExecuteInfo info) {
		if (served(prepared, info)) {
			return true;
		}

		if (shadowFeatures) {
			EntityDraw draw = PackChain.entities();
			if (draw != null) {
				draw.dropped(prepared.pipeline());
			}

			return true;
		}

		return false;
	}

	@SuppressWarnings("ReferenceEquality")
	private static boolean served(PreparedRenderType prepared, StagedVertexBuffer.ExecuteInfo info) {
		EntityDraw draw = PackChain.entities();
		if (draw == null) {
			return false;
		}

		GpuDevice device = RenderSystem.tryGetDevice();
		Element element = element(prepared.pipeline());

		boolean inMoment = (element != null && element.hand()) ? HandDraw.wanted()
				: wanted && element != null && inWindow(element);

		if (!inMoment || !EntityMesh.carrying() || device == null || element == null) {
			draw.end();

			return false;
		}

		if (!element.shadow() && !onMainTarget(prepared)) {
			draw.end();

			String target = GameRender.drawTargetName(prepared);

			return draw.refuse(element, "elsewhere:" + (element.glint() ? "glint" : "entity") + ":"
					+ target, true, "the game sends it to "
					+ target + ", which it composes itself afterwards, and the pack's "
					+ "colour targets cannot be attached beside a picture this engine has not got. It "
					+ "is the game's improved transparency that allocates those targets, and this "
					+ "engine turns that option off when the pack is loaded, as CoreVK does when shaders "
					+ "are enabled (mixin/fabulous/MixinDisableFabulousGraphics.java:37-40). Reaching "
					+ "this line means it was turned back on afterwards, and turning it off again "
					+ "gives the draw back");
		}

		try {
			return draw.record(device, element, prepared, info);
		} catch (GpuDeviceLossException e) {
			throw e;
		} catch (RuntimeException e) {

			wanted = false;
			HandDraw.stopped();
			CoreVK.logger().error("CoreVK stopped drawing the entities, the hand and the glint after "
							+ "an error",
					e);
			draw.end();

			return false;
		}
	}

	public static void endGroup() {
		EntityDraw draw = PackChain.entities();
		if (draw != null) {
			draw.end();
		}
	}

	private boolean shown() {
		return !this.chainRuns || this.owner.drawable();
	}

	private boolean record(GpuDevice device, Element element, PreparedRenderType prepared,
			StagedVertexBuffer.ExecuteInfo info) {
		if (!this.read) {
			return false;
		}

		EntityProgram program;
		if (element == this.recordedElement) {
			program = this.recordedProgram;
		} else {
			program = this.programs.get(element.element());
			this.recordedElement = element;
			this.recordedProgram = program;
		}

		if (program == null) {
			end();

			return refuse(element, "missing:" + element.element(), true,
					"the load left no program for the " + element.element() + " piece");
		}

		if (this.drawing != program && !begin(device, element, program, prepared)) {
			return false;
		}

		PreparedRenderType.Texture texture = image(prepared);
		program.texture(texture == null ? null
				: element.intensity() ? GlyphIntensity.view(texture.textureView())
						: texture.textureView(),
				texture == null ? null : texture.sampler());

		GraphicsApi.setPipeline(this.open, this.bound);
		scissor(prepared.scissorState());
		program.bind(this.open);

		if (program.readsGameTransforms()) {
			this.open.setUniform(LegacyGlsl.GAME_TRANSFORMS, prepared.dynamicTransforms());
		}
		this.open.setVertexBuffer(0, info.vertexBuffer().slice());
		this.open.setIndexBuffer(info.indexBuffer(), info.indexType());
		this.open.drawIndexed(info.indexCount(), 1, info.firstIndex(), info.baseVertex(), 0);

		return true;
	}

	private boolean begin(GpuDevice device, Element element, EntityProgram program,
			PreparedRenderType prepared) {
		end();

		if (!element.shadow()) {
			this.owner.beginFrame();
			if (!this.owner.openTargets(device)) {
				return refuse(element, "targets", "the colour targets could not be opened this frame");
			}

			if (!shown()) {
				return refuse(element, "warming", "the chain is still compiling, so nothing written to "
						+ "the pack's targets would reach the screen yet");
			}
		}

		RenderPipeline pipeline = program.prepare(device, element.modelView(), HandDraw.bob(),
				HandDraw.volume());
		if (pipeline == null) {

			return refuse(element, "prepare:" + (element.glint() ? element.element() : element.program()),
					true,
					"the " + element.element() + " program refused to prepare, which it says on its "
							+ "own line above");
		}

		GpuTextureView colour = GameRender.drawColour(prepared);
		GpuTextureView depth = GameRender.drawDepth(prepared);

		RenderPassDescriptor descriptor = program.descriptor(colour, depth);
		if (descriptor == null && element.shadow()) {

			return refuse(element, "no shadow map",
					"the shadow map has no image, so there is nothing for the caster to be drawn into");
		}

		if (descriptor == null && !program.plain()) {

			return refuse(element, "unallocated", "one of the pack's colour targets has no image yet");
		}

		CommandEncoder encoder = device.createCommandEncoder();
		this.open = descriptor == null
				? encoder.createRenderPass(() -> LABEL, colour, Optional.empty(), depth,
						OptionalDouble.empty())
				: GeometryHold.open(encoder, descriptor);
		this.drawing = program;
		this.bound = pipeline;

		return true;
	}

	private boolean refuse(Element element, String reason, boolean lasting, String why) {
		if (this.refused.add(reason)) {

			CoreVK.logger().warn("A draw of the {} {} because {}. {}", element.family(),
					element.shadow() ? "was dropped out of the shadow map"
							: "went back to the game's own shader",
					why, lasting
							? "It is settled for as long as this pack is loaded, so this geometry is "
									+ "drawn by the game on every frame until the pack is read again, "
									+ "steadily rather than as a flicker"
							: "It is then lit by the game for that frame and by the pack on the frames "
									+ "it is served, which reads on screen as a flicker");
		}

		return false;
	}

	private boolean refuse(Element element, String reason, String why) {
		return refuse(element, reason, false, why);
	}

	@SuppressWarnings("ReferenceEquality")
	private void dropped(RenderPipeline pipeline) {
		if (!this.droppedPipelines.add(pipeline)) {
			return;
		}

		if (pipeline == RenderPipelines.GLINT) {
			CoreVK.logger().info("An enchantment's glint is left out of the shadow map. CoreVK has a "
					+ "row for it and cancels the foil while the map is filled, so almost nothing "
					+ "reaches that row there either; what this leaves out is the little it would "
					+ "have drawn, and what it costs is a foil quad missing from a map the item under "
					+ "it already fills, so a shadow keeps its shape and loses a tint");

			return;
		}

		CoreVK.logger().warn("What the game draws with {} casts no shadow this frame, for whichever "
				+ "reason is given above, or because this engine has no shadow row for it. It is "
				+ "dropped rather than handed back: inside the light's walk the game would open its "
				+ "own pass on the target its render type names, which at that point in the frame "
				+ "carries the finished picture", pipeline.getLocation());
	}

	private void end() {
		RenderPass pass = this.open;
		this.open = null;
		this.drawing = null;
		this.bound = null;
		if (pass != null) {
			pass.close();
		}
	}

	private void scissor(ScissorState state) {
		if (state.enabled()) {
			this.open.enableScissor(state.x(), state.y(), state.width(), state.height());
		} else {
			this.open.disableScissor();
		}
	}

	private static PreparedRenderType.Texture image(PreparedRenderType prepared) {
		for (PreparedRenderType.Texture texture : prepared.textures()) {
			if (TEXTURE.equals(texture.name())) {
				return texture;
			}
		}

		return null;
	}

	private void read(OpenedPack shared) {
		try {
			readPrograms(shared);
		} finally {
			this.read = true;
		}
	}

	private void readPrograms(OpenedPack shared) {
		List<Element> served = ELEMENTS.values().stream().filter(EntityDraw::decodable).toList();

		boolean glint = (wanted || HandDraw.wanted()) && decodable(GLINT_EARLY);

		List<List<Element>> groups = new ArrayList<>();

		List<Element> eyes = wanted ? fixed() : List.of();
		if (wanted) {
			List<Element> family = Stream.concat(served.stream(),
							served.stream().map(element -> BLOCK_ELEMENTS.get(element.pipeline())))
					.toList();
			groups.add(family.stream().filter(element -> !element.afterStage()).toList());
			groups.add(family.stream().filter(Element::afterStage).toList());

			groups.add(eyes.stream().filter(element -> !element.lines()).toList());
			groups.add(eyes.stream().filter(Element::lines).toList());

			if (decodable(CRUMBLING_EARLY)) {
				groups.add(List.of(CRUMBLING_EARLY));
				groups.add(List.of(CRUMBLING_LATE));
			}
		}

		if (HandDraw.wanted()) {
			groups.add(twinsOf(served, HAND_ELEMENTS));
			groups.add(twinsOf(served, HAND_WATER_ELEMENTS));
		}

		if (wanted && glint) {
			groups.add(List.of(GLINT_EARLY));
			groups.add(List.of(GLINT_LATE));
		}

		if (HandDraw.wanted() && glint) {
			groups.add(List.of(GLINT_HAND));
			groups.add(List.of(GLINT_HAND_WATER));
		}

		if (wanted && TerrainDraw.shadowsAsked() && this.values.shadowCasters().anyFeature()) {
			groups.add(twinsOf(Stream.concat(served.stream(), eyes.stream()).toList(),
					SHADOW_ELEMENTS));
		}

		List<Element> asked = groups.stream().flatMap(List::stream).toList();
		if (asked.isEmpty()) {
			return;
		}

		try {
			List<PackProgram.GeometryElement> names = asked.stream().map(Element::asked).toList();
			Map<String, PackProgram.Loaded> loaded = shared != null
					? PackProgram.loadGeometry(shared, this.place, names)
					: PackProgram.loadGeometry(this.packPath, this.place, names, this.chosen, this.profile);
			if (loaded.isEmpty()) {
				CoreVK.logger().info("{} serves nothing in {} for the entities, the hand or the "
						+ "glint, so the game keeps its own shader for them",
						this.packPath.getFileName(), this.place.isEmpty() ? "its root" : this.place);

				return;
			}

			this.recordedElement = null;
			this.recordedProgram = null;
			groups.forEach(group -> keep(group, loaded));
		} catch (IOException | RuntimeException e) {
			CoreVK.logger().error("Could not prepare the entity programs of "
					+ this.packPath.getFileName() + ", so the game keeps its own shader for them", e);
		}
	}

	private static boolean decodable(Element element) {
		VertexFormat format = element.pipeline().getVertexFormatBinding(0);
		if (element.format().equals(format)) {
			return true;
		}

		CoreVK.logger().warn("The game draws the {} with {} and this engine decodes {} for it, so the "
				+ "game keeps its own shader for it", element.element(), format, element.format());

		return false;
	}

	private static List<Element> twinsOf(List<Element> served, Map<RenderPipeline, Element> table) {
		return served.stream()
				.map(element -> table.get(element.pipeline()))
				.filter(Objects::nonNull)
				.toList();
	}

	private static List<Element> fixed() {
		return FIXED.values().stream().filter(EntityDraw::decodable).toList();
	}

	private void keep(List<Element> group, Map<String, PackProgram.Loaded> loaded) {

		Map<Half, List<ChainPlan.Attachment>> byFile = new LinkedHashMap<>();
		for (Element element : group) {
			PackProgram.Loaded one = loaded.get(element.element());
			if (one == null) {
				continue;
			}

			Half half = new Half(servedBy(one), element.afterStage(), element.shadow());
			if (byFile.containsKey(half)) {
				continue;
			}

			List<ChainPlan.Attachment> writes = writes(half, one);
			if (writes == null) {
				return;
			}

			byFile.put(half, writes);
		}

		group.stream()
				.filter(element -> loaded.containsKey(element.element()))
				.forEach(element -> this.programs.put(element.element(), EntityProgram.of(
						loaded.get(element.element()), element, this.values, this.load,
						byFile.get(new Half(servedBy(loaded.get(element.element())),
								element.afterStage(), element.shadow())),
						this.chainTargets, this.targets, this.chainRuns)));
	}

	private record Half(String servedBy, boolean afterStage, boolean shadow) {
	}

	private List<ChainPlan.Attachment> writes(Half half, PackProgram.Loaded loaded) {

		if (half.shadow()) {
			return List.of();
		}

		Optional<ChainPlan.Pass> geometry = this.plan.geometryOf(half.servedBy(), half.afterStage());
		if (geometry.isPresent() && !geometry.get().size().equals(TargetSize.ofScreen())) {
			CoreVK.logger().warn("{} writes targets the pack asked to be scaled, so they cannot share "
					+ "a pass with the game's own target and the game keeps its own shader for that "
					+ "half of what it serves", half.servedBy());

			return null;
		}

		List<ChainPlan.Attachment> attachments =
				geometry.map(ChainPlan.Pass::attachments).orElse(List.of());
		if (half.afterStage()) {
			return attachments;
		}

		if (GeometryProgram.covers(notes(loaded), attachments.size(), this.chainRuns)) {
			return attachments;
		}

		if (this.chainRuns && !this.seeded) {
			CoreVK.logger().info("The scene seed is off, and it is the only way the first output of "
					+ "an opaque piece reaches the pack's picture, so the game keeps its own shader "
					+ "for what {} serves: served, it would write every other draw buffer and no "
					+ "colour", half.servedBy());

			return null;
		}

		if (attachments.isEmpty()) {
			return attachments;
		}

		Optional<ChainPlan.Seed> seed = this.plan.seed();
		if (!ChainPlan.leadsWithSeed(seed.orElse(null), geometry.orElse(null))) {
			ChainPlan.Attachment first = attachments.get(0);
			CoreVK.logger().warn("{} writes {} first and the scene seed paints {}, so the first output "
					+ "of an opaque piece would be carried into a target the pack did not ask for: the "
					+ "game keeps its own shader for that half", half.servedBy(),
					TargetName.canonical(first.target()),
					seed.map(where -> TargetName.canonical(where.target())).orElse("nothing"));

			return null;
		}

		return attachments;
	}

	private static TranslatedUnit.Notes notes(PackProgram.Loaded loaded) {
		return loaded.program().stages().get(ProgramStage.FRAGMENT).notes();
	}

	private static String servedBy(PackProgram.Loaded loaded) {
		return loaded.path().substring(loaded.path().lastIndexOf('/') + 1);
	}

	@Override
	Collection<EntityProgram> programs() {
		return this.programs.values();
	}

	@Override
	void rotate() {
		end();
		opaqueFeatures(false);
		translucentFeatures(false);
		if (!this.read) {
			return;
		}

		this.programs.values().forEach(EntityProgram::rotate);
	}

	@Override
	void release() {
		end();
		this.programs.values().forEach(EntityProgram::release);
		this.programs.clear();
		this.recordedElement = null;
		this.recordedProgram = null;

		this.refused.clear();
		this.droppedPipelines.clear();
		this.read = false;
	}
}
