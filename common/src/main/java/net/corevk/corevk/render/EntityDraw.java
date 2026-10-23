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

			// One crumbling contract, the camera's, answering the light map at full light: the map
			// files no twin for it, the javadoc of CRUMBLING_EARLY saying why the reference's map
			// never receives this draw.
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

		// The blending run. All of them but the swirl reach CoreVK through getTranslucent, which is one
		// function and not seven rows: pipeline/CoreVKPipelines.java:35,36,38,39,55,56,83. The swirl is
		// the exception and the comment on its own row says what it is pinned to instead.
		put(new Element(RenderPipelines.ENTITY_TRANSLUCENT, "translucent", ENTITIES_TRANSLUCENT,
				CUTOUT));
		put(new Element(RenderPipelines.ENTITY_TRANSLUCENT_CULL, "translucent_cull",
				ENTITIES_TRANSLUCENT, CUTOUT));
		// The cracks over a wolf's armour, which 26.3 renamed after that one caller and left the same
		// pipeline under the same location; GamePipelines names it on each game.
		put(new Element(GamePipelines.armorTranslucent(), "armor_translucent", ENTITIES_TRANSLUCENT,
				CUTOUT));
		put(new Element(RenderPipelines.ITEM_TRANSLUCENT, "item_translucent", ENTITIES_TRANSLUCENT,
				CUTOUT));
		put(new Element(RenderPipelines.BANNER_PATTERN, "banner", ENTITIES_TRANSLUCENT, CUTOUT));
		// The dark oval a mob is given on the ground, the game's own entity_shadow render type. Named
		// for what it is rather than after that type: this class already draws into a shadow map, and
		// an element called shadow would read as a piece of it in the log and in the identifier.
		put(new Element(RenderPipelines.ENTITY_SHADOW, "ground_shadow", ENTITIES_TRANSLUCENT, CUTOUT));
		// The wind a breeze throws, an ordinary row of this run: CoreVK sends it through the same
		// getTranslucent as the six above (pipeline/CoreVKPipelines.java:56).
		put(new Element(RenderPipelines.BREEZE_WIND, "breeze_wind", ENTITIES_TRANSLUCENT, CUTOUT));
		// The swirl over a charged creeper or an armoured wither, and THE ONE ROW OF THIS TABLE THAT
		// BLENDS AND ASKS FOR THE WRITING HALF'S PROGRAM. CoreVK pins it to ENTITIES_CUTOUT, which is
		// ProgramId.Entities, rather than sending it through getTranslucent like its neighbours
		// (pipeline/CoreVKPipelines.java:60), so the name is the opaque one while the pass is the
		// blending one. Nothing here has to be told that twice: the side is read off the pipeline,
		// which blends, and the name is what the row says.
		put(new Element(RenderPipelines.ENERGY_SWIRL, "energy_swirl", ENTITIES, CUTOUT));

		// The text, still in the blending run and drawn among the game's translucent features with
		// the six above it: the see-through name plates, the name plates and the texts are the
		// third, fourth and fifth of the six phases executeTranslucent walks
		// (feature/FeatureRenderDispatcher.java:213-215), which is the window this half is served
		// in. CoreVK routes the whole family to ProgramId.EntitiesTrans through getText and
		// getTextIntensity (pipeline/CoreVKPipelines.java:72-78 into
		// pipeline/programs/ShaderKey.java:62-63), which is the name this run already asks for, so a
		// glyph is drawn by whichever file serves a mob's translucent half. Every row carries the
		// tenth those keys carry.
		//
		// THREE grayscale rows where CoreVK's table has two intensity pipelines: 26.2 renamed the word
		// and added TEXT_GRAYSCALE_POLYGON_OFFSET, which CoreVK has no entry for at all, and it is
		// served like the offset row beside it for the reason TEXT_POLYGON_OFFSET is served like TEXT.
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
		// The box a text display draws behind its lines, and the two rows of this family that PINNED
		// holds to their own program: CoreVK keys them on a constant where the six above go through
		// getText. Not a name plate's box, which is an effect over the font's white glyph and comes
		// in on the six above with the letters it sits behind; the two render types below have one
		// caller in the client and it is DisplayRenderer.TextDisplayRenderer. Neither exists on 26.3,
		// which submits that box as text, so there it arrives on the six rows above.
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
		// Derived from the table above, which Java has already run: static initialisers go in source
		// order. One row per row and no filter, so this table cannot be missing the pipeline some
		// block entity turns out to use. Four of the rows it makes are unreachable by any block entity
		// renderer of the game - the item, the end crystal beam, the breeze's wind and the energy
		// swirl, the last three having only entity renderers and layers - and they are made anyway: a row
		// too many is a compiled module nobody selects, a row too few is the skull defect again, and
		// only one of those two is visible on screen.
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
		// Derived from the mob table like the block one above and for the same reason: a row too many
		// is a compiled module nobody selects, a row too few is a piece of the hand silently drawn by
		// the game in the middle of one the pack drew.
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
				// No twin for the lines, on the crack's argument (CRUMBLING_EARLY carries it, and
				// neither crack piece is in a table this files from): CoreVK keys two of the three to
				// SHADOW_LINES (pipeline/CoreVKPipelines.java:115,117) and its shadow stage replays
				// only what it submits itself, so the key sits unused there, and the light's walk
				// here submits the same two families (ShadowGeometry.walk). A twin would be a
				// compiled module nothing selects.
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
		// MovingBlockFeatureRenderer uses these three pipelines for falling blocks and blocks carried
		// by pistons. CoreVK assigns them directly rather than consulting the entity, block-entity or
		// hand phase (CoreVKPipelines.java:25-30), so they belong in this fixed table too.
		FIXED.put(RenderPipelines.SOLID_BLOCK, new Element(RenderPipelines.SOLID_BLOCK,
				"moving_solid", TERRAIN_SOLID, AlphaTest.OFF));
		FIXED.put(RenderPipelines.CUTOUT_BLOCK, new Element(RenderPipelines.CUTOUT_BLOCK,
				"moving_cutout", TERRAIN_CUTOUT, AlphaTest.CUTOUT));
		FIXED.put(RenderPipelines.TRANSLUCENT_BLOCK, new Element(RenderPipelines.TRANSLUCENT_BLOCK,
				"moving_translucent", BLOCK, CUTOUT));
		// The three lines pipelines CoreVK keys to the line program (pipeline/CoreVKPipelines.java:40-42),
		// no alpha test (ShaderKey.java:72). The fourth built on the same snippet, LINES_DEPTH_BIAS
		// (RenderPipelines.java:574-580), CoreVK keys to nothing and neither does this. Under
		// OUTLINE, which CoreVK poses on the block outline alone through the render type it wraps
		// around that one draw (mixin/MixinLevelRenderer.java:270-274): here every lines draw of
		// the window carries it, and the others riding these pipelines are the gizmos and the debug
		// shapes, drawn by nobody playing. The side flag is an entity row's, so false and unread:
		// the three pipelines blend, and blended() is what puts them after the deferred stage.
		FIXED.put(RenderPipelines.LINES, new Element(RenderPipelines.LINES, "lines", LINE,
				AlphaTest.OFF, RenderStage.OUTLINE, false, false));
		FIXED.put(RenderPipelines.LINES_TRANSLUCENT, new Element(RenderPipelines.LINES_TRANSLUCENT,
				"lines_translucent", LINE, AlphaTest.OFF, RenderStage.OUTLINE, false, false));
		FIXED.put(RenderPipelines.SECONDARY_BLOCK_OUTLINE,
				new Element(RenderPipelines.SECONDARY_BLOCK_OUTLINE, "outline_secondary", LINE,
						AlphaTest.OFF, RenderStage.OUTLINE, false, false));
	}

	static {
		// Here and not beside the call for the mob table: this one is declared below it, so a block up
		// there would read it while it is still empty and lose every row of it without a word.
		shadowTwins(FIXED);
	}

	
	@SuppressWarnings("ReferenceEquality")
	private static Element element(RenderPipeline pipeline) {
		// Asked before the other two and not after, because both of their marks can be up inside it:
		// the walk for the light submits block entities, which raises the block entity mark, and a
		// chest asked the other way round would be given gbuffers_block in the middle of the shadow
		// map. CoreVK has no such order to keep, its shadow table being a second table consulted
		// instead of the main one rather than a branch inside it (pipeline/CoreVKPipelines.java:85-134
		// against :25-83).
		//
		// Ahead of FIXED as well, and for that same reason rather than against it: FIXED is a row of
		// the MAIN table, and under CoreVK the main table is not the one consulted while the map is
		// drawn. The marks FIXED stands in front of are ours and are the hand's and the block
		// entity's; the shadow table is a table and not a mark.
		if (shadowFeatures) {
			return SHADOW_ELEMENTS.get(pipeline);
		}

		Element fixed = FIXED.get(pipeline);
		if (fixed != null) {
			return fixed;
		}

		// Where the table's row for it stood and for the table's reason, a constant in CoreVK that
		// consults neither mark; the window chooses the piece, as it does for the glint below, the
		// block a crack lies on deciding which of the game's two halves it is executed in.
		if (pipeline == RenderPipelines.CRUMBLING) {
			return translucentFeatures ? CRUMBLING_LATE : CRUMBLING_EARLY;
		}

		if (HandDraw.drawing()) {
			if (pipeline == RenderPipelines.GLINT) {
				return HandDraw.drawingSolid() ? GLINT_HAND : GLINT_HAND_WATER;
			}

			return (HandDraw.drawingSolid() ? HAND_ELEMENTS : HAND_WATER_ELEMENTS).get(pipeline);
		}

		// The window decides which of the two picture pieces answers, and it is one of the two places
		// in this class where a window is read to CHOOSE a row rather than to refuse one, the crack's
		// above being the other. It has to be: the two differ in the side of the deferred stage they
		// are bound against, and nothing about a glint draw says which of the game's two halves it
		// was submitted into.
		if (pipeline == RenderPipelines.GLINT) {
			return translucentFeatures ? GLINT_LATE : GLINT_EARLY;
		}

		// One table or the other and no falling between them: the block table is built from every row
		// of the mob one, so a pipeline either has both halves or neither. The two hand tables are
		// built the same way, from the same rows.
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
		// The three marks of the block entities go with it, and this is their frame boundary. They
		// are raised and lowered around calls rather than switched, so an unbalanced one is a mob
		// lit as a chest, and it would otherwise last the rest of the session. Dropping them at the
		// first of the two closings costs the second nothing: a submission is marked while the level
		// is walked, which is over before either window opens, and the mark a draw is read by is set
		// again one line before every draw.
		BlockEntityGeometry.clear();

		// And the three identifiers with them, for the same reason and with one difference: they are
		// dropped at the end of every submission as well, so what this catches is a submission that
		// left by a road with no return of its own.
		EntityIdentifiers.clear();

		// Closing the window closes the pass, and that is not the same safety net as the one at the
		// end of a group. What the game does next is not the same for the two closings and both
		// refuse an open pass: after the first it copies a depth between targets, which the encoder
		// refuses outright; after the second it composes the feature layer, draws the outlines and
		// then the translucent chunk group (LevelRenderer.java:456-464,470-475), each of which opens
		// a pass of its own where only one may be open. A pass that only the frame boundary closed
		// would already have cost the frame by then, either way.
		endGroup();
	}

	
	public static boolean draw(PreparedRenderType prepared, StagedVertexBuffer.ExecuteInfo info) {
		if (served(prepared, info)) {
			return true;
		}

		// INSIDE THE LIGHT'S WALK THERE IS NO SUCH THING AS HANDING A DRAW BACK.
		// Every no above ends with the caller drawing it itself, on the target its render type names,
		// and that target at the end of a frame is the finished picture: the caster would be painted
		// across the image the player is looking at, once per frame, for as long as the reason lasts.
		// So a no becomes a drop here, whatever the reason was and not only for a pipeline the table
		// has no row for. What it costs is written in ShadowGeometry: that caster casts no shadow,
		// which is a hole in the map rather than a mark on the screen.
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
		// Two families and two conditions, because they are served at different moments. The
		// entities live inside the two windows the level's features are drawn in; the hand is
		// submitted by this engine itself, twice, and both of those moments fall outside the
		// windows. Each family is guarded by its own switch and neither borrows the other's.
		//
		// Asked of the ROW rather than of the moment, which is what carrying a fixed table costs:
		// an eyes row is the answer even inside a hand pass, and it is not a hand row, so it may not
		// take the hand's switch. What it does take is its own window, and inside a hand pass that
		// window is shut, so the game keeps that draw. CoreVK would serve it, gbuffers_spidereyes being
		// its constant there too; the obstacle is that this engine binds a piece to one side of the
		// deferred stage at the LOAD, and an eyes row is bound to the side its window is on. No
		// renderer of the game can reach it, and that is read off both pipelines rather than one: the
		// eyes render type is asked for by the ender dragon and by the ender, phantom and spider eye
		// layers (EnderDragonRenderer:37, EnderEyesLayer:14, PhantomEyesLayer:14, SpiderEyesLayer:14),
		// and the emissive one by the warden's five emissive layers and the breeze's eyes
		// (WardenRenderer:34,44,54,60,65 and BreezeEyesLayer:19 through RenderTypes:515). None of the
		// ten draws in a hand pass.
		boolean inMoment = (element != null && element.hand()) ? HandDraw.wanted()
				: wanted && element != null && inWindow(element);
		// And the mesh has to be carrying, whatever the moment says. Every row but the glint's, the
		// crumbling's, the lines' and the text's is read against one of EntityMesh's two formats, so
		// a draw served before the mesh settled would bind a wider layout over the game's vertex.
		// It lasts from the load that asked until the extract that rebuilds the world, which is one
		// frame in the ordinary case.
		//
		// All four are held behind it all the same, on the argument the glint is held out on below:
		// they come in by this door, and a run of draws is one pass with one program.
		//
		// What it costs is NOT the same thing on the two roads, and saying it once would be wrong on
		// the second. In the picture's windows a no hands the draw back, so the frame's mobs are drawn
		// by the game rather than by the pack. Inside the light's walk there is no handing back: draw
		// turns every no into a drop, so those casters are drawn by nobody and the map has holes for
		// that frame. The glint is held out with them rather than let through: it comes in by this
		// door, and a run of draws is one pass with one program.
		if (!inMoment || !EntityMesh.carrying() || device == null || element == null) {
			draw.end();

			return false;
		}

		// Asked of the picture's rows alone. A shadow row is not drawn into the target the game named
		// at all: its pass carries the map, so which of the four the submission was addressed to
		// decides nothing here, and refusing on it would drop a caster out of the map for a reason
		// that belongs to a picture this draw never touches.
		if (!element.shadow() && !onMainTarget(prepared)) {
			draw.end();

			// The FAMILY is in the key beside the target and the piece is not, because two families now
			// name one target: the glint's translucent render type is addressed to ITEM_ENTITY_TARGET
			// like six of the entity ones, so a key on the target alone would let whichever came first
			// speak for both and the log would never say the glint went back too. Keying on the piece
			// instead would say it once per row, which is six lines for the one thing that happened.
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
			// Said before the pass is closed and not after: closing one the failure left in a bad
			// state can throw in its turn, and the second throw would carry away the only line that
			// says what went wrong first.
			//
			// All three go down together, and that is not caution: they come in by one door with
			// one set of tables, so whatever this door failed at will fail again on the next draw of
			// either. Leaving the hand on would keep the same throw coming, once a frame, with the
			// line that explains it printed only the first time.
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

			// The reason that was silent, and one of the two settled by the LOAD rather than by the
			// frame.
			//
			// It names the PIECE and claims nothing about the others, which took two reviews to get
			// right: the map really can hold one half and not the other. The two names of this family
			// walk disjoint chains, gbuffers_block through the terrain and gbuffers_entities through
			// the textured pair, so a pack that ships the first road and not the second resolves
			// every chest and no mob, and the load drops the mob pieces one at a time rather than
			// giving up on the family. What that paints is every chest lit by the pack and every mob
			// beside it lit by the game, for as long as the pack is loaded.
			//
			// No pack of the corpus is in that position, measured over its twenty five places. The
			// line is written for the pack that will be, because this is the one shape of failure
			// that looks like a decision rather than a fault.
			return refuse(element, "missing:" + element.element(), true,
					"the load left no program for the " + element.element() + " piece");
		}

		if (this.drawing != program && !begin(device, element, program, prepared)) {
			return false;
		}

		// The image belongs to the DRAW and not to the pass: one pipeline draws every mob on screen
		// and each of them brings its own skin, so this is set again for every draw recorded.
		//
		// A grayscale font sheet is handed over swizzled, which is the one place this door does not
		// bind the game's own view: the sheet holds one channel and a pack has no IS_GRAYSCALE line
		// to make four of it. GlyphIntensity says what it costs and where CoreVK does the same.
		PreparedRenderType.Texture texture = image(prepared);
		program.texture(texture == null ? null
				: element.intensity() ? GlyphIntensity.view(texture.textureView())
						: texture.textureView(),
				texture == null ? null : texture.sampler());

		GraphicsApi.setPipeline(this.open, this.bound);
		scissor(prepared.scissorState());
		program.bind(this.open);

		// The game's own transforms, for the same reason the image above is set again per draw and
		// with a sharper one: what a pack reads as gl_TextureMatrix[0] is the matrix its render type
		// was PREPARED with, and two breezes on screen carry two of them inside one run. Bound from
		// the slice rather than rebuilt, which is what CoreVK does as well: it declares the same block
		// (transform/transformer/VanillaTransformer.java:52-57) and registers the game's buffer under
		// this very name (pipeline/programs/ExtendedShader.java:107).
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

		// NONE OF THESE THREE inside the light's walk, and TerrainDraw skips the same three there.
		// The walk stands after the chain has closed the frame, so both per frame guards are down
		// again: opening here advances the value store a SECOND time, which turns every
		// gbufferPrevious* of the next frame into the current one, and clears the pack's colour
		// targets over what the chain has just written into them. Neither call has anything to give a
		// caster either: it is drawn into the map, which the stage ensured and emptied before any of
		// this, and it reads no colour target.
		if (!element.shadow()) {
			this.owner.beginFrame();
			if (!this.owner.openTargets(device)) {
				return refuse(element, "targets", "the colour targets could not be opened this frame");
			}

			// The third asks about the road a colour target takes back to the screen, and a caster
			// takes no such road. What that turns on, and why the answer here is not the one
			// TerrainDraw gives for its own shadow half, is in shown().
			if (!shown()) {
				return refuse(element, "warming", "the chain is still compiling, so nothing written to "
						+ "the pack's targets would reach the screen yet");
			}
		}

		// The matrix belongs to the RUN and not to the draw, which is what makes it settleable here:
		// what varies with the draw is read out of the game's own block instead, Element.modelView
		// saying what is left for this one. Null for everything but the hand, and written into the
		// block a few lines down. The bob and the volume go in beside it and come from the same
		// place, the pass being drawn; they are asked of HandDraw rather than carried on the row, so
		// that the two cannot be two answers: it holds neither between its passes.
		RenderPipeline pipeline = program.prepare(device, element.modelView(), HandDraw.bob(),
				HandDraw.volume());
		if (pipeline == null) {
			// Lasting, and it took a review to see it: a program that would not compile, or that
			// declares a storage block, latches broken in GeometryProgram and never unlatches, so
			// this answers null for the rest of the load rather than for this frame.
			// Keyed by PROGRAM NAME and not by reason alone, unlike the frame's three: the two halves
			// of the family are usually two files, so one key would name whichever failed first and
			// leave the other silent. Not by piece either, or a file that will not compile would say
			// so once per piece it serves, which is ten lines for one fault.
			//
			// The glint is the exception and takes the piece, because its four are ONE name: they are
			// four compiled modules against four different sets of attachments, so the argument above
			// runs the other way and one key would hide three failures behind the first.
			return refuse(element, "prepare:" + (element.glint() ? element.element() : element.program()),
					true,
					"the " + element.element() + " program refused to prepare, which it says on its "
							+ "own line above");
		}

		// The two images the game would have drawn into, worked out as PreparedRenderType works them
		// out: the overrides are the game's own way of sending a phase somewhere else, and the layer
		// that carries its translucent features is one of them. On 26.3 there are no overrides and
		// the answer is the main target, GameRender carrying why.
		GpuTextureView colour = GameRender.drawColour(prepared);
		GpuTextureView depth = GameRender.drawDepth(prepared);

		RenderPassDescriptor descriptor = program.descriptor(colour, depth);
		if (descriptor == null && element.shadow()) {
			// The two arguments above were ignored: a shadow program builds its descriptor out of the
			// map and never out of the target the game named. Null here means the map is not there,
			// which the stage should already have refused, so this is the second door rather than the
			// first and it is worth its own sentence: with the map gone, a pass opened on the game's
			// own attachment would paint the picture with a program written for a depth buffer.
			return refuse(element, "no shadow map",
					"the shadow map has no image, so there is nothing for the caster to be drawn into");
		}

		if (descriptor == null && !program.plain()) {
			// The colour targets are not there yet, which is the first frame or two and the frames
			// after a resize. A plain pass would carry one attachment against a pipeline holding a
			// state per target the pack asked for, and setPipeline refuses that by name.
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
			// What happens next is not the same in the light's walk, and saying the wrong one is
			// worse than saying nothing: a reader told the game took over goes looking for geometry
			// lit by the wrong engine, where what is really there is geometry missing from the map.
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

		// The deliberate one is named apart from the rest, because reporting a parity choice as a
		// fault is how a reader is sent hunting something that is working. The walk also submits
		// the beacon beams and the bolts, which no shadow table anywhere has a row for, and those
		// are the same silence the camera's table gives them. The name plates and the texts are no
		// longer among them, the shadow table having a row for each. A mob's ground oval is not
		// among what reaches here either: EntityRenderDispatcherMixin keeps it off the mob while the
		// map is drawn.
		//
		// It is nearly a match rather than the gap it first reads as.
		// CoreVK carries a shadow row for the glint (pipeline/CoreVKPipelines.java:111) and then cancels
		// the foil while the map is filled (mixin/ItemStackMixin.java:14-17, whose comment is that a
		// glint is not visible in a shadow anyway). That cancel is on ItemStack.hasFoil, and it
		// reaches further than the name suggests: CoreVK's light walks the world a second time and
		// EXTRACTS and submits every render state inside the pass it brackets
		// (shadows/ShadowRenderer.java:396 and :638 around :684 and :713), so the reads that decide a
		// foil are inside it and answered no. What escapes is a foil decided somewhere other than the
		// stack, which is the thrown trident, reading its own entity data. So the row CoreVK carries is
		// reached by almost nothing, and what this engine leaves out is what that row would have
		// drawn.
		if (pipeline == RenderPipelines.GLINT) {
			CoreVK.logger().info("An enchantment's glint is left out of the shadow map. CoreVK has a "
					+ "row for it and cancels the foil while the map is filled, so almost nothing "
					+ "reaches that row there either; what this leaves out is the little it would "
					+ "have drawn, and what it costs is a foil quad missing from a map the item under "
					+ "it already fills, so a shadow keeps its shape and loses a tint");

			return;
		}

		// The reason is NOT asserted here, and that took a review to see: every no from served ends
		// up on this line, a missing device and a refused program among them, and each of those has
		// already said what it was on a line of its own. Naming the table as the cause would send a
		// reader looking for a missing row when the fault is in the load.
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

		// Asked once for the four pieces that share the glint's one pipeline, and only where a switch
		// asked for one of them: four answers would be four lines about one format, and an answer
		// nobody asked for would blame a pack for a family that is off.
		boolean glint = (wanted || HandDraw.wanted()) && decodable(GLINT_EARLY);

		// The block entity half of each served piece, and the two hand halves, added here rather than
		// filtered again: all four tables share the pipelines of the first, so a format this engine
		// cannot decode has already been reported once and saying it four times would read as four
		// defects.
		//
		// GROUPS and not one list, because what stands or falls together is not the same in each.
		// The entity names walk one picture and hold together WITHIN a half, and the two halves
		// settle apart, which is the particles' position; the hand's two passes are two moments of
		// the frame that share no target, so one of them can be served while the other is not. Five
		// where every switch is on, the eyes being a group of their own for the reason given below.
		List<List<Element>> groups = new ArrayList<>();

		// Asked once and held, because it is asked for twice: once as a group of the picture and once
		// as the rows the shadow table takes from it. Calling fixed() again would send a format this
		// engine cannot decode to the log a second time, and read as two defects.
		List<Element> eyes = wanted ? fixed() : List.of();
		if (wanted) {
			List<Element> family = Stream.concat(served.stream(),
							served.stream().map(element -> BLOCK_ELEMENTS.get(element.pipeline())))
					.toList();
			groups.add(family.stream().filter(element -> !element.afterStage()).toList());
			groups.add(family.stream().filter(Element::afterStage).toList());

			// A group of its own and not part of the blending half above, for the reason the
			// particles are two groups: what stands or falls together is what shares a picture. The
			// eyes ask for a name of their own that walks its own chain, gbuffers_spidereyes through
			// the textured pair, so it commonly resolves to a different file with different draw
			// buffers; put in with the entities, a place that could not answer for that one file
			// would take every mob and every chest down with it. Apart, the worst it can paint is a
			// mob the pack lit wearing eyes the game drew, which is the smaller of the two pictures.
			//
			// And the lines apart from the eyes, on the same argument one step further: the line
			// program walks its own chain too, and a place that could not answer for it would
			// otherwise take the eyes and the moving blocks back to the game with the outline.
			groups.add(eyes.stream().filter(element -> !element.lines()).toList());
			groups.add(eyes.stream().filter(Element::lines).toList());

			// A group of its own for each crack piece, on the glint's argument below: a crack is
			// drawn ON TOP of a block rather than beside anything, so a piece this place cannot
			// answer for goes back to the game alone. It matters more for the early piece than it
			// looks: that one asks for the near side of gbuffers_damagedblock, which writes() can
			// refuse on the seed's account where the far side never could be, and inside the eyes'
			// group that refusal would have taken the eyes and the moving blocks with it.
			if (decodable(CRUMBLING_EARLY)) {
				groups.add(List.of(CRUMBLING_EARLY));
				groups.add(List.of(CRUMBLING_LATE));
			}
		}

		if (HandDraw.wanted()) {
			groups.add(twinsOf(served, HAND_ELEMENTS));
			groups.add(twinsOf(served, HAND_WATER_ELEMENTS));
		}

		// A group of its own for each glint piece, and NOT a row added to the four above it.
		//
		// A group stands or falls together, and the reason that holds inside the entity family is that
		// those programs write into one picture: a piece whose answer could not be settled is drawn by
		// the game, and a chest lit by the game beside a mob lit by the pack is a disagreement about
		// what lights them. A glint is not beside anything. It is drawn ON TOP of a piece, so a glint
		// left to the game over an item the pack drew is what every frame looks like today, and taking
		// the mobs down with it would trade one family for four.
		if (wanted && glint) {
			groups.add(List.of(GLINT_EARLY));
			groups.add(List.of(GLINT_LATE));
		}

		if (HandDraw.wanted() && glint) {
			groups.add(List.of(GLINT_HAND));
			groups.add(List.of(GLINT_HAND_WATER));
		}

		// One group for the whole shadow table, where the picture takes two: the map has no deferred
		// stage to stand either side of, and every row writes the same shadow image even though moving
		// blocks now ask for their own shadow program names.
		//
		// Asked for only where the pack draws something through the feature renderers at all. A pack
		// that keeps the entities and the block entities out of its map wants no shadow_entities read,
		// and reading it anyway would be a translation and a compilation spent on a program no draw
		// can ever select. The ground shadow is not in the table, so this really is empty for such a
		// pack rather than nearly empty.
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
		// The side is the pipeline's answer for the entity rows and the row's own flag for the
		// hand's: a hand pass is drawn wholly on one side of the deferred stage whatever its rows'
		// pipelines blend, the solid one before it and the water one after, so a hand row made from
		// a blending mob pipeline still belongs to the side its PASS is drawn on.
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
		// A shadow half writes the map and nothing else. GeometryProgram gives a shadow pass no slots
		// at all, so the list is never read for one, and asking the plan for it would ask about a
		// name the plan's geometry table has no row for. Answered empty rather than left to fall
		// through, because the two paragraphs below would both give it the wrong answer: the seed has
		// nothing to do with a draw buffer nought that is shadowcolor0, and refusing there would take
		// every moving caster out of the map the moment somebody wrote seed=off.
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

		// Asked before either question about the seed, because it is what decides that neither of
		// them is this half's to answer: with the mask placed, draw buffer nought is the pack's own
		// target and the seed is kept off every pixel this half wrote.
		if (GeometryProgram.covers(notes(loaded), attachments.size(), this.chainRuns)) {
			return attachments;
		}

		// The scene seed is now the ONLY road this half's first output has into the pack's picture,
		// so a run with it switched off would write every other draw buffer and no albedo at all.
		// What that paints is a mob shaped hole of normals and specular over the terrain's own
		// colours, which is exactly the plausible and wrong picture the switch exists to rule out.
		// Only where the chain runs, and the condition is not a refinement. With chain=off nothing
		// of the pack reaches the screen through a final, so draw buffer nought stays on the game's
		// own target and is the picture: the seed has nothing to carry and is never even drawn.
		// Refusing the family there would take away the one configuration that tells a wrong
		// gbuffer from a wrong composite, which is what these switches exist for.
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

		// Asked of the plan's own predicate and not written out again here, which is the shape the
		// rest of this method just took: the notes ChainPlan builds hold the same question for the
		// families still riding on the seed, and two readings of it would be free to drift.
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
		// With the programs, or "once per load" would mean once per session: what is read again after
		// this can refuse again, for the same reason or for another, and a reader watching a portal
		// would see the first reading's lines and nothing after them.
		this.refused.clear();
		this.droppedPipelines.clear();
		this.read = false;
	}
}
