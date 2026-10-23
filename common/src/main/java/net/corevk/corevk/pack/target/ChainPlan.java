package net.corevk.corevk.pack.target;

import net.corevk.corevk.pack.model.TargetName;
import net.corevk.corevk.pack.model.TargetSize;
import net.corevk.corevk.pack.model.ProgramNames;
import net.corevk.corevk.pack.program.ProgramResolver;
import net.corevk.corevk.pack.program.TerrainPass;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Stream;

public final class ChainPlan {

	
	public static final int MAX_ATTACHMENTS = 8;

	
	private static final int DEFERRED_RANK = ProgramNames.frameRank("deferred");

	
	private static final int PREPARE_RANK = ProgramNames.frameRank("prepare");

	
	private static final int BEGIN_RANK = ProgramNames.frameRank("begin");

	private static final String FINAL = "final";

	
	private static final String SEED_PROGRAM = "gbuffers_terrain";

	private final String place;
	
	private static final String CLOUD_PROGRAM = "gbuffers_clouds";

	
	private static final List<String> SKY_PROGRAMS =
			List.of("gbuffers_skybasic", "gbuffers_skytextured", CLOUD_PROGRAM);

	
	private static final Predicate<Families> NOT_EVERYWHERE = _ -> false;

	
	private static final List<NamedProgram> NAMED_PROGRAMS = Stream.concat(
			// None of the sky's three is counted, the clouds included, but not for one reason: the
			// sky's two hang off a pass the game opens nowhere the place's skybox is NONE, which in
			// vanilla is the Nether, while the clouds hang off a pass of their own that the game
			// opens in the overworld alone. It is spelled out, with what it was measured to cost,
			// where the verdicts are handed their map.
			SKY_PROGRAMS.stream()
					.map(program -> new NamedProgram(program, CLOUD_PROGRAM.equals(program),
							NOT_EVERYWHERE)),
			Stream.of(
					// The entity halves are the two the file has to ask for, and they are drawn in
					// every place once it has: what decides them is the line and not the format.
					new NamedProgram("gbuffers_entities", false, Families::entities),
					new NamedProgram("gbuffers_block", false, Families::entities),
					// The blending half of those same two, on the far side of the stage: the game
					// draws them among its translucent features, which is after the deferreds have
					// run. Both opaque names come back on this side too, because the side is half the
					// key, and the entries further down say why each one is here twice.
					//
					// A name that never enters this list answers empty, and empty is the same word
					// this table uses for a walk it REFUSED, so the caller cannot tell the two
					// apart: both make the family draw one output. That is why the entry matters
					// more than it looks, and it is what the harness gate measures.
					//
					// The TEXT walks these same two and adds no entry: CoreVK sends a glyph to
					// ProgramId.EntitiesTrans and, under the block entity phase, to BlockTrans
					// (pipeline/programs/ShaderKey.java:62-65), and both are drawn in the game's
					// translucent feature window like the mobs. So a name plate and a sign's text
					// take whatever draw buffers these two answer with, and a place that answers
					// empty for them draws a glyph with one output as it draws a mob with one.
					// That one output is the game's target, which inside that window is the layer
					// render/FeatureLayer composes, so the glyph lands there drawn by the PACK
					// rather than by the game. Nothing about the text decides that: it is the
					// family's answer, and FeatureLayer names it beside the other two roads in.
					new NamedProgram("gbuffers_entities_translucent", true,
							Families::entities),
					new NamedProgram("gbuffers_block_translucent", true, Families::entities),
					// The block name on the far side, which is a translucent moving block: CoreVK keys
					// TRANSLUCENT_BLOCK to ProgramId.Block (pipeline/CoreVKPipelines.java:30), and the
					// game draws it among the translucent features. Without it the row resolved no
					// attachment and wrote its one output on the game's target.
					//
					// Not counted, on the hand's argument: a block moves where a piston pushes or sand
					// falls, a per frame answer no per place map may carry.
					new NamedProgram("gbuffers_block", true, NOT_EVERYWHERE),
					// The two blending names on the NEAR side, which 26.3 reaches and 26.2 never does:
					// that game forces a banner's pattern layers among the solid features
					// (render/GamePipelines.solidPhase of 26.3), so the block row and the entity row
					// of the pattern are drawn before the deferreds with their translucent program, as
					// CoreVK draws them. Without these two the early piece resolved no attachment and
					// wrote its one output on the game's target under the seed, and every banner drew
					// as white cloth.
					//
					// Not counted, on the hand's argument: a banner stands where somebody put one, a
					// per frame answer no per place map may carry.
					new NamedProgram("gbuffers_block_translucent", false, NOT_EVERYWHERE),
					new NamedProgram("gbuffers_entities_translucent", false, NOT_EVERYWHERE),
					// The OPAQUE entity name on the far side, which reads like a contradiction and is
					// the energy swirl over a charged creeper: CoreVK pins that row to ENTITIES_CUTOUT
					// outright (pipeline/CoreVKPipelines.java:60) while the game blends it, so it is
					// drawn among the translucent features and still asks for gbuffers_entities. It is
					// the one row of render/EntityDraw whose name and side disagree, and no other walk
					// reaches that pair: the two entries above ask for the blending names, and the
					// opaque entry asks on the near side.
					//
					// Not counted, on the hand's argument rather than the entities': the family this
					// key answers for is one row, and a swirl is drawn where somebody has charged a
					// creeper or armoured a wither, which is a per frame answer no per place map may
					// carry. Where a pack ships no gbuffers_entities_translucent of its own the
					// blending name falls back on this very file, and that entry counts it; three
					// packs of the corpus are in that case and this entry moves nothing for them.
					new NamedProgram("gbuffers_entities", true, NOT_EVERYWHERE),
					// The glowing eyes of a mob, drawn among the translucent features beside the two
					// above and on the same side of the stage. One entry and not two: its two
					// pipelines ask for the one name, both blend, and neither is ever drawn before
					// the deferreds.
					//
					// Not counted, and it is the hand's reason rather than the entities': the line
					// being on does not put one of the six mobs that reach these two pipelines in
					// front of the camera. A verdict that took the eyes for drawn would report
					// targets as written in every place a plan is built for, which is every place
					// none of the six is standing.
					new NamedProgram("gbuffers_spidereyes", true, NOT_EVERYWHERE),
					// The overlay over a block being mined, which straddles the stage on 26.3 for the
					// glint's reason: what it lies on decides the half. That game files the crack
					// over a translucent block among the translucent features, beside the eyes, and
					// the crack over every other block among the SOLID ones
					// (SubmitNodeCollection.submitBreakingBlockModel and submitCrumblingOverlay), so
					// both sides are walked. Left out on the far side, its family drew one output
					// onto the game's cleared target, and the multiply the overlay blends with came
					// out opaque black over every crack. Left out on the near side, the early piece
					// wrote its one output onto the game's own target before the scene seed, and a
					// crack writes no depth, so the seed's cut never carried it in: the block broke
					// with no crack on it at all.
					//
					// Not counted, on the hand's argument: a crack is drawn where somebody is mining
					// a block, which is a per frame answer no per place map may carry.
					new NamedProgram("gbuffers_damagedblock", false, NOT_EVERYWHERE),
					new NamedProgram("gbuffers_damagedblock", true, NOT_EVERYWHERE),
					// The game's lines, the block outline first among them, drawn among the
					// translucent features beside the overlay and on the same side of the stage.
					// The same entry the overlay owed: without it the line program resolved to no
					// attachment, its one output went to the game's cleared target, and what
					// reached the picture of it came through the seed's depth cut, a partial line
					// fighting the face it lies on, or nothing where the seed does not run.
					//
					// Not counted, on the hand's argument: an outline is drawn where somebody is
					// aiming at a block, a per frame answer no per place map may carry.
					new NamedProgram("gbuffers_line", true, NOT_EVERYWHERE),
					// The hand's two passes, which straddle the stage as the particles do and for the
					// same kind of reason: the solid one is drawn among the game's opaque features and
					// the blending one at the end of the level. Not counted, and not by a switch:
					// even with the line written, the hand is only drawn where a player has one,
					// which is no place at all from a third person camera - a per frame answer no
					// per place map may carry, so its entries take the weather's constant.
					//
					// The two really can resolve to ONE file, gbuffers_hand_water falling back on
					// gbuffers_hand, and that is exactly why they are two entries rather than one: a
					// single walk would put one key in and leave the other side of the same file
					// unanswered, which is the silence the head of this list is about.
					new NamedProgram("gbuffers_hand", false, NOT_EVERYWHERE),
					new NamedProgram("gbuffers_hand_water", true, NOT_EVERYWHERE),
					// An enchantment's glint, which straddles the stage for a reason of its own: what
					// CARRIES it decides which half the game executes it in. An enchanted book is
					// submitted among the solid features, foil and all, since the sort looks at the
					// item's own quads and not at its glint (SubmitNodeCollection.java:325), while an
					// enchanted armour piece is submitted among the translucent ones. So both sides are
					// walked, as the hand's two are, and for the same reason: one key in and the other
					// side of the same file unanswered is the silence the head of this list is about.
					//
					// Not counted, and on the hand's argument rather than the weather's: a glint is
					// drawn where somebody is holding or wearing something enchanted, which is a per
					// frame answer no per place map may carry.
					new NamedProgram("gbuffers_armor_glint", false, NOT_EVERYWHERE),
					new NamedProgram("gbuffers_armor_glint", true, NOT_EVERYWHERE),
					// Drawn, and still not counted: the game draws no rain and no snow where there is
					// no weather, which is every place but the overworld.
					new NamedProgram("gbuffers_weather", true, NOT_EVERYWHERE),
					// The one family that straddles the stage: the game submits every particle group
					// twice, and the two land on either side of it. Counted wherever its line is on,
					// and what earns it is that particles are drawn in every place there is.
					//
					// Its opaque half writes the coverage mask as the entities do, so it owns the
					// draw buffers the pack asked for and the seed's own target says nothing about
					// whether its draw was taken.
					new NamedProgram("gbuffers_particles", false, Families::particles),
					new NamedProgram("gbuffers_particles_translucent", true,
							Families::particles),
					// And Distant Horizons' far terrain, on both sides of the stage: that mod draws
					// its own two halves from the head of the game's opaque chunk group and from the
					// head of its translucent one, which is where the world's own two chunk passes
					// stand. Left out of this list, a pack's far terrain program writes its FIRST
					// draw buffer and no other, which is what BSL was measured doing on 18 August
					// 2026: it asks for colortex0 and colortex6 and colortex6 went nowhere.
					//
					// Neither half rides the seed: the family owns its first draw buffer outright,
					// as CoreVK binds its dh programs, and GeometryProgram says in the one place the
					// rule is decided why the seed could not carry it. Neither half is counted, for
					// the reason no family with a switch is: the far terrain is drawn where that mod
					// is installed and running, which is no place in particular.
					new NamedProgram("dh_terrain", false, NOT_EVERYWHERE),
					new NamedProgram("dh_water", true, NOT_EVERYWHERE)))
			.toList();

	
	private record NamedProgram(String program, boolean afterDeferred,
			Predicate<Families> everywhere) {
	}

	private final List<Pass> passes;
	private final int beginEnd;
	private final int prepareEnd;
	private final int deferredEnd;
	private final Pass last;

	
	private final Attachment present;

	private final Seed seed;

	
	private final Map<Key, Pass> attachments;

	
	private final Map<TerrainPass, Key> terrainKeys;

	
	private final Map<String, Key> skyKeys;

	private final List<Integer> swapBack;
	private final List<String> refusals;
	private final List<String> notes;
	private final List<String> history;

	private ChainPlan(String place, List<Pass> passes, Pass last, Attachment present, Seed seed,
			Map<Key, Pass> attachments, Map<TerrainPass, Key> terrainKeys, Map<String, Key> skyKeys,
			List<Integer> swapBack, List<String> refusals, List<String> notes,
			List<String> history) {
		this.place = place;
		this.passes = List.copyOf(passes);
		this.beginEnd = pastRank(this.passes, BEGIN_RANK);
		this.prepareEnd = pastRank(this.passes, PREPARE_RANK);
		this.deferredEnd = pastDeferred(this.passes);
		this.last = last;
		this.present = present;
		this.seed = seed;
		this.attachments = Map.copyOf(attachments);
		this.terrainKeys = Map.copyOf(terrainKeys);
		this.skyKeys = Map.copyOf(skyKeys);
		this.swapBack = List.copyOf(swapBack);
		this.refusals = List.copyOf(refusals);
		this.notes = List.copyOf(notes);
		this.history = List.copyOf(history);
	}

	public record Attachment(int target, TargetSchedule.Side side) {
	}

	
	public record Pass(String program, List<Attachment> attachments, TargetSize size,
			boolean inferred) {

		public Pass {
			attachments = List.copyOf(attachments);
		}

		
		public List<Integer> targets() {
			return this.attachments.stream().map(Attachment::target).toList();
		}

		
		public int frameRank() {
			return ProgramNames.frameRank(ProgramNames.familyOf(this.program));
		}

		
		public boolean deferred() {
			return frameRank() == ProgramNames.frameRank("deferred");
		}
	}

	
	public record Key(String servedBy, boolean afterDeferred) {
	}

	
	public record Seed(int target, TargetSchedule.Side side, String from, int at) {
	}

	
	public record Families(boolean terrain, boolean entities, boolean particles, boolean seed) {

		
		public static final Families DEFAULT = new Families(true, true, true, true);
	}

	
	public static ChainPlan of(TargetPlan plan, ProgramResolver resolver) {
		return of(plan, resolver, List.of(), Families.DEFAULT);
	}

	
	public static ChainPlan of(TargetPlan plan, ProgramResolver resolver, List<String> refused,
			Families families) {
		List<Pass> passes = new ArrayList<>();
		List<String> refusals = new ArrayList<>(refused);
		List<String> notes = new ArrayList<>();
		List<String> history = new ArrayList<>();
		Pass last = null;

		for (String program : plan.running()) {
			if (program.equals(FINAL)) {
				last = new Pass(program, List.of(), TargetSize.ofScreen(), false);
				continue;
			}

			Pass pass = passOf(plan, program, refusals);
			if (pass != null) {
				passes.add(pass);
			}
		}

		// colortex0 on the half the frame ends on, which is what stands in for the final where the
		// pack ships none. present() carries what CoreVK does with the same case and where.
		Attachment present = last != null ? null : new Attachment(0,
				plan.schedule().flippedAtEnd().contains(0)
						? TargetSchedule.Side.ALT
						: TargetSchedule.Side.MAIN);
		if (present != null) {
			notes.add("this place runs no final, so " + TargetName.canonical(present.target())
					+ " is brought to the screen as it stands, which is what CoreVK does");
		}

		// Worked out before the verdicts rather than in the return, so that they are handed the
		// answer instead of asking for it twice: what the chunk passes write is exactly what the
		// verdicts must not blame the clear for.
		//
		// One walk fills the three: the answers land in the shared table and each family keeps the
		// keys it reaches. A program serving two families, which is the ordinary case for a pack
		// whose sky falls back on gbuffers_basic, is therefore walked once, where memoising the two
		// families apart would walk it twice and say anything it had to say twice with it. No pack
		// of the corpus reaches that case, so nothing of it shows in the measurements; it is the
		// shared table that guarantees it rather than each family's own bookkeeping.
		Map<Key, Pass> attachments = new LinkedHashMap<>();
		Map<TerrainPass, Key> terrainKeys = terrainKeysOf(plan, resolver, notes, attachments);
		Map<NamedProgram, Key> namedKeys = namedKeysOf(plan, resolver, notes, attachments);
		Map<String, Key> skyKeys = new LinkedHashMap<>();
		namedKeys.forEach((named, key) -> {
			if (SKY_PROGRAMS.contains(named.program())) {
				skyKeys.put(named.program(), key);
			}
		});

		Seed seed = seedOf(plan, resolver, notes);
		// Where the seed is painted is one question and whether it is painted is another, and only
		// the second belongs to the frame: the families that ride on the seed compare their first
		// output against its target whatever the line says, so the plan's own answer stands and this
		// is what the walk below counts with.
		Seed painted = families.seed() ? seed : null;

		// The terrain and whatever the switches leave, and the rule that admits those and no others
		// is ONE question: does this engine draw that family, with the lines that were really
		// written, in every place a plan is built for? A plan is per PLACE, so a family drawn in
		// the overworld alone cannot be counted at all - counting it reports the world as drawn in
		// the two places the format reserves for the Nether and the End.
		//
		// Three families fail that question however options.txt is written, and each fails it
		// differently. The sky's two hang off the game's own sky pass, and a place whose skybox is
		// NONE opens none at all (LevelRenderer.addSkyPass, the Skybox.NONE gate at :337). Vanilla
		// answers NONE in the Nether and nowhere else, but the field is a datapack one with a codec
		// and OVERWORLD for a default (DimensionType.java:97), so a modded dimension may answer it
		// anywhere and the gate is what this depends on, not the name. Both branches of that pass
		// are served now, the End's two methods as much as the other six, RenderPipelines.END_SKY
		// being assigned gbuffers_skytextured as CoreVK assigns it (CoreVKPipelines.java:69), so what
		// keeps the sky out of this count is that one gate and not two. The clouds are held
		// out by something else entirely: a pass of their own, opened on the cloud setting and on
		// the alpha of the cloud colour (LevelRenderer:220-233) and in the overworld alone.
		// The weather is drawn only where the level has weather,
		// which the weather renderer decides on its own; what is certain here is that it is not every
		// place.
		//
		// The terrain, the entities, the particles and the seed pass it or fail it on their line
		// alone, and asking the line rather than its default is why this method is handed the
		// switches at all. Measured on the corpus, each line moved on its own: reading the defaults
		// instead leaves twenty-five true notes unsaid under terrain=off, nine under particles=off
		// and six under seed=off, and keeps the entities in the map wherever entities=off is
		// written, which no pack of the corpus is caught by.
		//
		// It is measured rather than argued, and the measurement is a trade rather than a clean win.
		// Counting the sky silences Bliss's colortex0 note in all three of its places and two of the
		// three are true. Counting the weather rewrites the colortex12 note of both Complementary in
		// all three of theirs, which makes it true in world0 and makes it name, in the other two, a
		// program that will never run there: four lines lost against two gained. Holding the three out
		// keeps the true notes and pays one false one per family in every place that family really
		// draws, which is the cheaper half of both trades and the one this engine takes.
		//
		// WHAT IT LEAVES OPEN, said rather than hidden: those notes stay wrong, and the only honest
		// way to close them is a per place answer rather than a per name one, which this record
		// cannot carry. The End's sky widened that by one place the day it was served.
		// AND, on the near side, nothing about the seed's own target any more. A family drawn
		// before the deferred stage that writes the coverage mask keeps the seed off the pixels it
		// wrote and takes its first draw buffer in the pack's own targets, so where the seed is
		// painted says nothing about whether its draw was taken. The entities were the first to
		// leave that road and the opaque particles were the last on it; a map still asking would
		// hand a family back on any pack whose program leads with a target other than the seed's,
		// and on every pack under seed=off, where the family is served all the same. Bliss is the
		// pack that shows it: its seed is colortex1 against a first output of colortex2, so its
		// opaque particles were handed back in all three of its places and are now drawn there.
		//
		// WHAT THAT OVER-CLAIMS, said rather than hidden: this map cannot know whether the mask was
		// really placed, which is the translation's answer and per FILE. A pack whose entity program
		// declares eight outputs already, or more draw buffers than outputs, is drawn by the game's
		// shader while these notes count it as drawn. That is the same shape as the per NAME against
		// per FAMILY asymmetry below, it is the rarer of the two directions, and the engine says so
		// by name in the log at the moment it happens.
		//
		// AND at the size of the screen, which is the same condition read off the other side: every
		// family below shares the pass the renderer opened for the game's own target, one render pass
		// has one render area, and each of their files turns the draw down when the pack asked for
		// targets of another size. No pack of the corpus scales one, so this moved nothing on the
		// August 2026 corpus; it
		// is here because the two readings must not be able to disagree. The terrain carries no such
		// condition and is not held to it: it draws into a pass of its own. CoreVK carries no such
		// refusal either way - it scales single targets (ShaderProperties.java:277,
		// TextureScaleOverride) and no size condition was found on its geometry routing - so the
		// refusal is this render-pass shape's price, and where a pack scaled a target a family
		// writes, that family would be drawn by the game's shader, the note this map keeps being
		// what says so. One asymmetry stands with it and is said: this map counts per NAME where
		// render/EntityDraw refuses per FAMILY, so two entity names resolving to two files, one of
		// them refused here, would be counted half drawn while the family served nothing. No pack of
		// the corpus reaches that case.
		//
		// WHAT THIS MAP DELIBERATELY DOES NOT FOLLOW: with chain=off no pass of it draws, so every
		// line these notes carry is about a frame that does not happen, whatever the families say.
		Map<Key, Pass> world = new LinkedHashMap<>();
		if (families.terrain()) {
			terrainKeys.values().forEach(key -> world.put(key, attachments.get(key)));
		}

		for (NamedProgram named : NAMED_PROGRAMS) {
			Key key = named.everywhere().test(families) ? namedKeys.get(named) : null;
			Pass drawing = key == null ? null : attachments.get(key);
			if (drawing != null && drawing.size().equals(TargetSize.ofScreen())) {
				world.put(key, drawing);
			}
		}

		verdicts(plan, painted, world, passes, last, notes, history);

		Set<Integer> back = new TreeSet<>(plan.schedule().flippedAtEnd());
		back.retainAll(plan.persistent());

		// The nulls are dropped on the way out, and they had to be there on the way in: a key that
		// this place cannot answer is remembered as answered so that the three families reaching it
		// say what there is to say once. Past this point nobody asks twice, and an absent key and a
		// key mapped to nothing mean the same thing to every reader.
		Map<Key, Pass> answered = new LinkedHashMap<>();
		attachments.forEach((key, pass) -> {
			if (pass != null) {
				answered.put(key, pass);
			}
		});

		return new ChainPlan(plan.place(), passes, last, present, seed, answered, terrainKeys, skyKeys,
				List.copyOf(back), refusals, notes, history);
	}

	
	private static Map<TerrainPass, Key> terrainKeysOf(TargetPlan plan, ProgramResolver resolver,
			List<String> notes, Map<Key, Pass> into) {
		Map<TerrainPass, Key> keys = new EnumMap<>(TerrainPass.class);
		for (TerrainPass pass : TerrainPass.values()) {
			// A shadow pass writes shadowcolor and never colortex, so this walk has no answer for
			// it: its draw buffers index a set of targets this plan does not hold, and a number read
			// as the wrong family would send the shadow map's albedo into somebody's normals.
			if (pass.shadow()) {
				continue;
			}

			Optional<String> served = resolver.lookup(plan.place(), pass.program())
					.map(ProgramResolver.Resolution::servedBy);
			if (served.isEmpty()) {
				continue;
			}

			Key key = new Key(served.get(), pass.afterDeferred());
			if (answer(plan, key, notes, into) != null) {
				keys.put(pass, key);
			}
		}

		return keys;
	}

	
	private static Pass answer(TargetPlan plan, Key key, List<String> notes, Map<Key, Pass> into) {
		if (!into.containsKey(key)) {
			into.put(key, geometryOf(plan, key.servedBy(), notes, key.afterDeferred()));
		}

		return into.get(key);
	}

	
	private static Map<NamedProgram, Key> namedKeysOf(TargetPlan plan, ProgramResolver resolver,
			List<String> notes, Map<Key, Pass> into) {
		Map<NamedProgram, Key> keys = new LinkedHashMap<>();

		// Computed once per serving FILE AND SIDE and not once per program, the same reason the
		// geometry walk gives above: the fallback tree sends skybasic to gbuffers_basic and both
		// skytextured and clouds to gbuffers_textured, so one file commonly serves two or three of
		// these names, and walking it again would say the same note about it twice. The side is part
		// of it because the clouds and the weather ask on the far one, so a pack with no cloud
		// program of its own really is one file answering twice here, which is what the key is for.
		for (NamedProgram named : NAMED_PROGRAMS) {
			Optional<String> served = resolver.lookup(plan.place(), named.program())
					.map(ProgramResolver.Resolution::servedBy);
			if (served.isEmpty()) {
				continue;
			}

			Key key = new Key(served.get(), named.afterDeferred());
			if (answer(plan, key, notes, into) != null) {
				keys.put(named, key);
			}
		}

		return keys;
	}

	private static Pass passOf(TargetPlan plan, String program, List<String> refusals) {
		List<Integer> writes = plan.writes(program);
		if (writes.isEmpty()) {
			// Every full screen program is inferred to colortex0, so the only way here is a file
			// the expander could not read at all.
			refusals.add(program + " is meant to run and this plan knows nothing it writes, which "
					+ "is what an entry point that could not be read looks like");
			return null;
		}

		return attachmentsOf(plan, program, writes, refusals);
	}

	
	private static Pass geometryOf(TargetPlan plan, String program, List<String> notes,
			boolean afterDeferred) {
		List<Integer> writes = plan.writes(program);
		if (writes.isEmpty()) {
			return null;
		}

		Optional<TargetSchedule.Bound> bound = afterDeferred
				? plan.schedule().stepAfterDeferred(program)
				: plan.schedule().step(program);

		return attachmentsOf(plan, program, writes, notes, bound);
	}

	private static Pass attachmentsOf(TargetPlan plan, String program, List<Integer> writes,
			List<String> problems) {
		return attachmentsOf(plan, program, writes, problems, plan.schedule().step(program));
	}

	private static Pass attachmentsOf(TargetPlan plan, String program, List<Integer> writes,
			List<String> problems, Optional<TargetSchedule.Bound> bound) {
		if (writes.size() > MAX_ATTACHMENTS) {
			problems.add(program + " writes " + writes.size() + " targets and one pass carries at "
					+ "most " + MAX_ATTACHMENTS + ": " + writes);
			return null;
		}

		if (new LinkedHashSet<>(writes).size() != writes.size()) {
			problems.add(program + " names the same target twice in its draw buffers, " + writes
					+ ", and one image cannot be two attachments of one pass");
			return null;
		}

		if (bound.isEmpty()) {
			problems.add(program + " writes " + writes + " and the schedule holds no step for it");
			return null;
		}

		List<Attachment> attachments = new ArrayList<>();
		TargetSize size = null;
		for (int index : writes) {
			if (!plan.allocated().contains(index)) {
				problems.add(program + " writes " + TargetName.canonical(index)
						+ ", which nothing of this place allocates");
				return null;
			}

			TargetSize here = plan.directives().size(index);
			if (size == null) {
				size = here;
			} else if (!size.equals(here)) {
				// One pass, one render area: the backend refuses attachments of two sizes, and it
				// refuses them at the first draw rather than at load time.
				problems.add(program + " writes targets of two sizes, " + describe(size) + " and "
						+ describe(here) + " for " + TargetName.canonical(index));
				return null;
			}

			attachments.add(new Attachment(index, bound.get().write(index)));
		}

		return new Pass(program, attachments, size, plan.inferredWrites().contains(program));
	}

	private static Seed seedOf(TargetPlan plan, ProgramResolver resolver, List<String> notes) {
		Optional<ProgramResolver.Resolution> resolution = resolver.lookup(plan.place(), SEED_PROGRAM);
		if (resolution.isEmpty()) {
			notes.add("nothing here serves " + SEED_PROGRAM + ", so the game's own frame is painted "
					+ "nowhere and every program of the chain reads clear colours");
			return null;
		}

		// The fallback tree is followed rather than the file: Sildur's ships no gbuffers_terrain
		// and inherits it from gbuffers_textured, whose draw buffers are 412 and not 0.
		String from = resolution.get().servedBy();
		List<Integer> writes = plan.writes(from);
		if (writes.isEmpty()) {
			notes.add(from + " serves " + SEED_PROGRAM + " and declares no draw buffer, so there is "
					+ "nowhere to paint the game's own frame");
			return null;
		}

		int target = writes.get(0);
		if (!plan.allocated().contains(target)) {
			notes.add(from + " serves " + SEED_PROGRAM + " and writes " + TargetName.canonical(target)
					+ ", which nothing of this place allocates, so the game's own frame is painted "
					+ "nowhere");
			return null;
		}

		TargetSchedule.Side side = plan.schedule().step(from)
				.map(step -> step.write(target))
				.orElse(TargetSchedule.Side.MAIN);

		return new Seed(target, side, from, plan.geometryAt());
	}

	
	private static void verdicts(TargetPlan plan, Seed seed, Map<Key, Pass> world,
			List<Pass> passes, Pass last, List<String> notes, List<String> history) {
		List<Pass> ordered = new ArrayList<>(passes);
		if (last != null) {
			ordered.add(last);
		}

		// Every target a gbuffers program declares, less the ones the world's own passes really fill.
		// What is left is written by geometry alone and by nothing this engine puts in the pack's
		// targets.
		Set<Integer> undrawn = new TreeSet<>();
		plan.schedule().steps().stream()
				.filter(step -> !step.fullscreen())
				.forEach(step -> undrawn.addAll(step.writes()));

		// One entry per point of the frame the world goes in at, every family drawn on the same side
		// sharing theirs. A set rather than a list, since several of them are commonly one file and
		// hence one identical Pass: the solid and the cutout pass always, and a pack with no cloud
		// program of its own has its clouds and its particles answered by the same one too.
		//
		// The near side goes in where the begins and the prepares end, which is where the seed is
		// painted and where the game really draws it: those two stages run while the level's frame
		// graph is being built, and the opaque terrain, the opaque particles and everything else on
		// that side are drawn after them.
		//
		// AT NOUGHT INSTEAD, AS THIS HAD IT, EVERY PREPARE READING A GBUFFER TARGET WAS CALLED A
		// READER OF A HALF THIS FRAME HAD ALREADY FILLED, and the note it exists to earn was dropped
		// exactly where the order matters most. What such a pass really reads there is whatever left
		// that half standing at the end of the frame before, which is the branch below and not
		// silence.
		int beforeWorld = pastRank(ordered, PREPARE_RANK);
		int afterDeferred = pastDeferred(ordered);
		Map<Integer, Set<Pass>> drawn = new LinkedHashMap<>();
		world.forEach((key, drawing) -> {
			drawn.computeIfAbsent(key.afterDeferred() ? afterDeferred : beforeWorld,
					_ -> new LinkedHashSet<>()).add(drawing);
			undrawn.removeAll(drawing.targets());
		});

		Set<Attachment> filled = new LinkedHashSet<>();
		Set<Attachment> told = new LinkedHashSet<>();
		for (int at = 0; at < ordered.size(); at++) {
			if (seed != null && at == seed.at()) {
				filled.add(new Attachment(seed.target(), seed.side()));
			}

			for (Pass drawing : drawn.getOrDefault(at, Set.of())) {
				filled.addAll(drawing.attachments());
			}

			Pass pass = ordered.get(at);
			Optional<TargetSchedule.Bound> bound = plan.schedule().step(pass.program());

			for (int index : plan.samples(pass.program())) {
				Attachment half = new Attachment(index, bound
						.map(step -> step.read(index))
						.orElse(TargetSchedule.Side.MAIN));
				if (filled.contains(half) || !told.add(half)) {
					continue;
				}

				Verdict said = verdict(plan, undrawn, ordered, drawn, at, half, pass.program());
				(said.temporal() ? history : notes).add(said.text());
			}

			filled.addAll(pass.attachments());
		}
	}

	
	private static int pastDeferred(List<Pass> ordered) {
		return pastRank(ordered, DEFERRED_RANK);
	}

	
	private static int pastRank(List<Pass> ordered, int rank) {
		int at = 0;
		while (at < ordered.size() && ordered.get(at).frameRank() <= rank) {
			at++;
		}

		return at;
	}

	
	private static Verdict verdict(TargetPlan plan, Set<Integer> undrawn, List<Pass> ordered,
			Map<Integer, Set<Pass>> drawn, int at, Attachment half, String reader) {
		boolean kept = plan.persistent().contains(half.target());
		String name = TargetName.canonical(half.target());
		String clear = ", so " + reader + " reads what the clear left there";
		String before = ", so " + reader + " reads the frame before, and the clear colour on the "
				+ "first one";

		// WHAT FILLS IT IS ASKED BEFORE WHY IT IS EMPTY, and the order is the whole of this method.
		// These branches do not add lines to one another, they REPLACE one another, so putting the
		// weakest first costs the reader the strongest. Bliss's colortex0 is the case that proved it:
		// a composite of the pack writes it later in the same frame, and because the sky's programs
		// had put it in undrawn the reader was told instead that no geometry of ours reaches the
		// pack's targets - true about the sky, useless about the target, and it had displaced the one
		// sentence that named the real writer.
		String later = writtenLater(ordered, drawn, at, half);
		if (later != null) {
			// Which of the two the reader really gets is the clear directive's answer and never the
			// order's: a target the pack clears is emptied on BOTH halves at the top of every frame,
			// so nothing of the frame before is left there to be read.
			//
			// The stage that reaches this branch oftenest is the prepares, and the writer it names is
			// then a gbuffers program: a target the world alone fills holds the world of the FRAME
			// BEFORE where a prepare samples it, which is what CoreVK hands one there as well.
			return new Verdict(name + " is not written until " + later + ", later in the same frame"
					+ (kept ? before : clear), kept);
		}

		String off = disabledWriter(plan, half.target());
		if (off != null) {
			return new Verdict(name + " is written by " + off + ", which this place does not run"
					+ clear, false);
		}

		if (undrawn.contains(half.target())) {
			// What ends up here is the geometry no pass of this engine fills in EVERY place: the sky's
			// three, held out by two gates rather than one, since a place whose skybox is none
			// opens no sky pass at all and the clouds hang off a pass of their own that the game
			// opens in the overworld alone, the weather, which is drawn only where the level has
			// weather, and every family whose line is off - the entity halves by default, and the
			// terrain, the particles or the seed's riders wherever somebody writes those lines. Why
			// that is the question, and what holding each out was measured to cost, is where the
			// verdicts are handed their map.
			//
			// The particles are NOT here, and putting them back would have the sentence say that no
			// geometry of ours reaches the pack's targets while a particle program of the pack is
			// writing them: a note that reads like a diagnosis and is a lie, Bliss's colortex9
			// carrying it in all three of its places.
			//
			// The tail is flat, and it is flat because of where this branch now sits: nothing later in
			// the frame writes this half, so the pack keeping the target between frames buys the
			// reader nothing - the frame before had nothing written there either.
			return new Verdict(name + " is written by geometry, none of which this engine draws into "
					+ "the pack's targets" + clear, false);
		}

		// Nothing fills this half at any point of the frame. Whether that is a hole or a history
		// buffer is decided by the pack's own clear directive and by nothing else.
		return kept
				? new Verdict("nothing writes the half of " + name + " that " + reader + " reads, and "
						+ "the pack keeps that target between frames" + before, true)
				: new Verdict(name + " is written by no program of this place, so it holds its clear "
						+ "colour for ever, and " + reader + " reads that", false);
	}

	
	private record Verdict(String text, boolean temporal) {
	}

	
	private static String writtenLater(List<Pass> ordered, Map<Integer, Set<Pass>> drawn, int at,
			Attachment half) {
		for (int next = at + 1; next <= ordered.size(); next++) {
			// The world goes in ahead of the full screen pass standing at the same point, which is
			// the order the walk fills them in.
			for (Pass drawing : drawn.getOrDefault(next, Set.of())) {
				if (drawing.attachments().contains(half)) {
					return drawing.program();
				}
			}

			if (next < ordered.size() && ordered.get(next).attachments().contains(half)) {
				return ordered.get(next).program();
			}
		}

		return null;
	}

	
	private static String disabledWriter(TargetPlan plan, int index) {
		List<String> writers = plan.disabled().entrySet().stream()
				.filter(entry -> plan.writes(entry.getKey()).contains(index))
				.map(entry -> entry.getKey() + " (" + entry.getValue() + ")")
				.toList();

		return writers.isEmpty() ? null : String.join(", ", writers);
	}

	private static String describe(TargetSize size) {
		return size.relative()
				? String.format(Locale.ROOT, "%.3f by %.3f of the screen", size.width(), size.height())
				: (int) size.width() + " by " + (int) size.height() + " pixels";
	}

	public String place() {
		return this.place;
	}

	
	public List<Pass> passes() {
		return this.passes;
	}

	
	public int deferredEnd() {
		return this.deferredEnd;
	}

	
	public int prepareEnd() {
		return this.prepareEnd;
	}

	
	public int beginEnd() {
		return this.beginEnd;
	}

	
	public Optional<Pass> last() {
		return Optional.ofNullable(this.last);
	}

	
	public Optional<Attachment> present() {
		return Optional.ofNullable(this.present);
	}

	
	public Optional<Pass> geometry(TerrainPass pass) {
		return Optional.ofNullable(this.terrainKeys.get(pass)).map(this.attachments::get);
	}

	
	public Optional<Pass> geometryOf(String servedBy, boolean afterDeferred) {
		return Optional.ofNullable(this.attachments.get(new Key(servedBy, afterDeferred)));
	}

	
	public Optional<Pass> sky(String program) {
		return Optional.ofNullable(this.skyKeys.get(program)).map(this.attachments::get);
	}

	public Optional<Seed> seed() {
		return Optional.ofNullable(this.seed);
	}

	
	public static boolean leadsWithSeed(Seed seed, Pass pass) {
		if (seed == null || pass == null || pass.attachments().isEmpty()) {
			return false;
		}

		Attachment first = pass.attachments().get(0);

		return seed.target() == first.target() && seed.side() == first.side();
	}

	
	public List<Integer> swapBack() {
		return this.swapBack;
	}

	
	public List<String> refusals() {
		return this.refusals;
	}

	
	public List<String> notes() {
		return this.notes;
	}

	
	public List<String> history() {
		return this.history;
	}
}
