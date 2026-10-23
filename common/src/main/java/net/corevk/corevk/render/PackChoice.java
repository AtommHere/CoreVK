package net.corevk.corevk.render;

import net.corevk.corevk.glsl.LoadClock;
import net.corevk.corevk.glsl.PackProgram;
import net.corevk.corevk.glsl.TranslationCache;
import net.corevk.corevk.HostReport;
import net.corevk.corevk.pack.load.PackLoader;
import net.corevk.corevk.pack.load.PackReport;
import net.corevk.corevk.pack.option.EngineDefines;
import net.corevk.corevk.pack.option.OptionValue;
import net.corevk.corevk.pack.option.SettingSet;
import net.corevk.corevk.pack.source.OpenedPack;
import net.corevk.corevk.pack.source.ShaderPackSource;
import net.corevk.corevk.pack.target.PackDirectives;
import net.corevk.corevk.pack.texture.CustomImages;
import net.corevk.corevk.pack.texture.CustomStorage;
import net.corevk.corevk.render.pbr.PbrAtlases;
import net.corevk.corevk.render.storage.StorageBuffers;
import net.corevk.corevk.render.storage.StorageImages;
import net.corevk.corevk.render.timing.PassTimings;
import net.corevk.corevk.settings.PackFile;
import net.corevk.corevk.settings.PackSession;
import net.corevk.corevk.settings.SettingsFile;
import net.corevk.corevk.settings.SettingsLayers;
import net.corevk.corevk.CoreVK;
import com.mojang.blaze3d.GpuDeviceLossException;
import net.minecraft.client.GraphicsPreset;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.util.Util;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.RejectedExecutionException;

public final class PackChoice {

	
	public static final String NO_PACK = PackFile.NONE;

	
	private static volatile Path reported;
	private static volatile PackSession session;
	private static volatile String lastError;
	private static volatile List<String> removed = List.of();
	private static volatile boolean packsFirst = true;
	private static volatile boolean packOff;

	
	private static volatile boolean packMissing;

	
	private static volatile PackFile askedFor = PackFile.EMPTY;

	
	private static volatile int shadowDistance = PackFile.DEFAULT_SHADOW_DISTANCE;

	private PackChoice() {
	}

	
	public static void load(Path gameDirectory) {
		PassTimings.resetCensus();
		// Here and not lower down: it decides what the translation emits, and every road
		// below this point that reads a pack translates one.
		DriverTrig.read(gameDirectory);
		// Beside it, for the compiles rather than the translations: it decides the bytes every
		// module of this load is made of, and the key it is stored under.
		RawLocals.read(gameDirectory);
		// Beside the trig switch and for the same reason: what follows is what these tallies
		// are a tally of. The cache empties its own on the same line, so that its counts and the
		// clock's milliseconds are read off one load.
		//
		// Two loads can smear into each other here, in both directions, exactly as they do for
		// the clock: a family worker of the chain that has just been released lands its own
		// hits after this reset, and a swap empties the tallies while the outgoing chain's
		// workers are still translating.
		LoadClock.reset();
		TranslationCache.reset();
		// The storage blocks the translator files away, emptied at the head of a load and NOT beside
		// the CustomImages line in release(), though the two are installed on the same line.
		//
		// What parts them is not when they are asked. VulkanBindGroupLayoutMixin asks both in the
		// same method, while the layout is built. It is that only this one is asked again on the
		// OTHER side of the same decision: the layout takes VK_DESCRIPTOR_TYPE_STORAGE_BUFFER from
		// StorageBuffers.named and the descriptor write takes it from StorageBuffers.bound, and both
		// of those come back here. A layout is cached with its pipeline and a release does not empty
		// that cache, so emptying this between the two makes the write go in as a uniform buffer
		// against a slot the layout already declared storage. The image half cannot do that: the
		// write side reads StorageImages.bound, which walks an allocation of its own and never asks
		// CustomImages, so an empty table there costs a filter mode for a frame and no more. The road
		// that used to reach it was leaving a world, which releases the chain and does NOT replace
		// it, so the first frame of the next world was drawn by that same chain before the question
		// of whether the world had moved was reached at all; PackChain.beforeLevel asks it at the
		// head of that frame now, so the reload stands in front of the drawing rather than behind it.
		//
		// What emptying here buys is bounded, not absolute. The pack-load worker of the chain that
		// has just gone translates on a background thread and every program it reads reinstalls what
		// ITS pack declared, so it can put the outgoing answers back after this line. prefetchFamily
		// stops between two families once the chain is released, which leaves the one family already
		// under way, and that is the whole of what can still land here.
		CustomStorage.clear();
		// Down before the folder is read rather than raised only where a pack asks for it: every
		// road out of this method below has to leave the terrain sampler answering for the pack
		// that will draw, and most of them draw no pack at all.
		TerrainSampler.breaksAnisotropy(false);
		// Taken before anything reads the folder, so that the count printed at the end of the load
		// covers every opening the load made and not only the translation's.
		int openings = ShaderPackSource.openings();
		LegacyTerrainFilter.read(gameDirectory);
		session = null;
		lastError = null;
		removed = List.of();
		packsFirst = true;
		packOff = false;
		packMissing = false;
		// Emptied before the read below rather than after it, so that a file that cannot be read at
		// all leaves the screen naming nothing rather than whatever the load before it asked for.
		askedFor = PackFile.EMPTY;
		PackChain live = null;
		try {
			// Read here rather than inside choose(), which the empty folder road below returns ahead
			// of. That road is the one a player takes who has just renamed or deleted their only pack,
			// and without the name this far up it is the one road that cannot say which of the two
			// happened: the folder is empty, which is true, and is not what they did.
			askedFor = PackFile.read(packFile(gameDirectory));
			// Off that same read rather than a second one, and before anything can refuse the load:
			// this line belongs to the player and not to the pack, and every return below is reached
			// with no pack drawn and the shadow distance still theirs. The render scale rides the
			// same rule; whether it ENGAGES is asked per frame against drawingPack(), so the roads
			// below that draw nothing need no line of their own to keep the world at the window.
			shadowDistance = askedFor.shadowDistance();
			RenderScale.wanted(askedFor.renderScale());
			// Here and not lower down, because it is read during the expansion of the pack's very
			// first unit: the shadow map's size is a declaration of the pack's own text, and the
			// scale is applied by rewriting that declaration rather than by allocating something
			// else, so it has to be in force before a line is expanded.
			// Pushed here and said further down. This is the one place that knows the setting
			// before anything is expanded, which is what it has to be in force for, but the roads
			// below reach an empty folder and a pack switched off as well, and there is nothing
			// to rewrite on either.
			SettingSet.shadowMapScale(askedFor.shadowMapScale());

			// Both ways out of the next two blocks end with no pack drawing anything, and NEITHER mesh
			// carries what a pack reads once none wants it: said here as well as on the road that
			// loads a pack, or picking None after a pack would leave the extra bytes on every vertex
			// with nothing left to read them. The refusals further down do not say it, and the note on
			// the one at the head of the load prices what that costs.
			//
			// Three switches for two meshes: the hand is drawn from the entity mesh and carries a
			// switch of its own, so putting a pack away has to take that one down too or the entity
			// mesh goes on carrying for a family nothing serves.
			List<Path> packs = PackLoader.candidates(gameDirectory);
			if (packs.isEmpty()) {
				TerrainDraw.wanted(false);
				EntityDraw.wanted(false);
				HandDraw.wanted(false);
				// The empty folder is the smaller half of the truth whenever a pack was named: what a
				// player who has just deleted or renamed their only one needs told is which name went
				// unanswered, and the folder being empty is why.
				if (namedAPack()) {
					packIsMissing(gameDirectory);
					// And the opening a previous load left standing goes with it, here and on every
					// other road out of this method that draws no pack: none of them reaches
					// OpenedPack.openKept, so nothing would ever replace what is held, and a player
					// who picks None would leave a mounted archive and a pack's worth of source
					// strings standing for the rest of the session.
					OpenedPack.forgetKept();

					return;
				}

				lastError = "No shader pack in " + PackLoader.directory(gameDirectory);
				CoreVK.logger().info("No shader pack in {}, nothing to draw",
						PackLoader.directory(gameDirectory));
				OpenedPack.forgetKept();

				return;
			}

			Path pack = choose(packs, askedFor).orElse(null);
			if (pack == null) {
				// Both roads out of here draw nothing, so both take the meshes down, and only then are
				// they told apart: what parts them is whether nothing was asked for or whether what was
				// asked for is not there.
				TerrainDraw.wanted(false);
				EntityDraw.wanted(false);
				HandDraw.wanted(false);
				if (namedAPack()) {
					packIsMissing(gameDirectory);
					OpenedPack.forgetKept();

					return;
				}

				// Nothing is left behind and nothing is reported as an error: this is the same path as
				// an empty folder, taken because it was asked for rather than because nothing was found.
				packOff = true;
				CoreVK.logger().info("No pack asked for, so none of the {} in {} is read and the game "
						+ "draws its own image. Pick one in the settings screen, or name it in {}",
						packs.size(), PackLoader.directory(gameDirectory), packFile(gameDirectory));
				OpenedPack.forgetKept();
				return;
			}

			// Chosen, and neither read, drawn nor offered for picking. On a backend this engine is not
			// written for, its pages open the offer to switch to Vulkan instead (PackScreens) and its
			// keys do nothing (SettingsKey), so nothing asks for the pack's settings, and every switch
			// here stays down so that nothing of the pack reaches a mesh or a frame: the programs are
			// translated against Vulkan's depth and clip conventions, and what they drew when let run
			// elsewhere was a picture credible and wrong, which reads as a pack fault. The game's own
			// image is the better answer. Asked before the pack is opened rather than after it, so
			// that beside CoreVK the log carries no reading of a pack CoreVK is not the one drawing.
			// HostReport says it once in the log at startup and, unless CoreVK draws there, once in chat
			// on entering a world; lastError is kept all the same, for whatever asks it next.
			if (HostReport.otherBackend()) {
				TerrainDraw.wanted(false);
				EntityDraw.wanted(false);
				HandDraw.wanted(false);
				lastError = ShaderPackSource.nameOf(pack) + " is not drawn on the "
						+ HostReport.backend() + " backend: set Graphics API to \"Prefer Vulkan "
						+ "(Experimental)\" under Options, Video Settings, and restart";
				OpenedPack.forgetKept();
				return;
			}

			SettingsLayers.Resolved settings = open(gameDirectory, pack);

			Map<String, OptionValue> chosen = new LinkedHashMap<>(settings.chosen());
			// Reserved keys rather than options: no pack declares a setting under any of these
			// names, and each names something this mod does rather than a value the pack has. The
			// fourth, profile, never reaches here: the settings layer takes it out and carries it
			// apart, because it is the side that writes it back into the pack's own file.
			EngineOptions.Read engine = EngineOptions.take(chosen);
			packsFirst = engine.packsFirst();
			PackChain.chainWanted(engine.chain());

			TerrainDraw.wanted(engine.terrain());
			TerrainDraw.shadowWanted(engine.shadow());
			SkyDraw.wanted(engine.sky());
			EntityDraw.wanted(engine.entities());
			HandDraw.wanted(engine.hand());
			CloudDraw.wanted(engine.clouds());
			WeatherDraw.wanted(engine.weather());
			ParticleDraw.wanted(engine.particles());
			PackDump.configure(engine.dump(),
					gameDirectory.resolve(CoreVK.MOD_ID).resolve("dump.txt"));

			// Refused by name, and before a single program is TRANSLATED. Required flags this
			// engine does not serve keep the pack on its fallback; optional CUSTOM_IMAGES is
			// posed from EngineDefines when the storage-image pipe is served, and is not a
			// required flag in the corpus.
			//
			// **Here and not one line earlier**, and TerrainDraw.wanted above is the reason. It settles
			// what the chunk mesh carries, and returning ahead of it would leave that answer at its
			// default, so a refused pack would decide the mesh for whichever pack was picked after
			// it. The mesh follows the pack, so what that costs is a rebuilt world rather than a
			// session. The session is published by then as well, so the
			// settings screen shows the pack it is refusing rather than an empty list, which is
			// where the two other refusals of this method stand.
			//
			// What this order does NOT do is take the terrain back from a pack refused below: the
			// mesh then carries what a pack reads while none is drawn, which costs twenty bytes a
			// vertex and one rebuild, and no picture. One pack of the corpus reaches it.
			//
			// A deliberate divergence, and worth naming because it is one: CoreVK refuses a required
			// flag only when the name is unknown to it or the hardware cannot serve it, so it draws
			// Reverie where this does not. It can afford to, having built all four.
			//
			// Read from the one file rather than from the report above, because a refusal has to
			// hold at every load and the report is taken once.
			List<String> required = new ArrayList<>(PackLoader.properties(pack).requiredFeatures());
			// Only the names this engine has not built refuse the pack: the block emission
			// attribute rides in the chunk element, a pack declaring HIGHER_SHADOWCOLOR draws the
			// light into the eight it asked for, a storage block is bound off the pack's own
			// bufferObject, compute passes are dispatched at the head of the frame and before the
			// pass they hang off, a per buffer blend directive is built into the attachment whose
			// rank its target holds, a blending entity draw reaches the pack's own
			// gbuffers_entities_translucent and a blending block entity its block twin, and custom
			// images are served now, so a pack that cannot draw without one of those is simply right
			// about what it needs. The list is the one EngineDefines poses
			// COREVK_FEATURE_ for, and the two have to move together: a define is a promise, and a
			// refusal is the same promise refused.
			//
			// Matched without regard to case, and that is not politeness: the list above is kept in
			// the pack's own spelling, and CoreVK never reads it that way. It upper-cases a declared
			// name before it looks the flag up (features/FeatureFlags.java:70), and the message it
			// prints to pack authors spells the name in lower case,
			// "corevk.features.required/optional = ssbo" (shaderpack/ShaderPack.java:216). A pack
			// that wrote what that message told it to would be refused here on a name this engine
			// serves. ShaderProperties.declares already reads the same lists that way.
			Set<String> served = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
			served.addAll(List.of("BLOCK_EMISSION_ATTRIBUTE", "COMPUTE_SHADERS", "CUSTOM_IMAGES",
					"ENTITY_TRANSLUCENT", PackDirectives.HIGHER_SHADOWCOLOR, "SSBO",
					"SEPARATE_HARDWARE_SAMPLERS"));
			// The one name of the list the DEVICE answers for rather than the engine, and CoreVK makes
			// the same call on the same ground: its own flag is usable only where the driver parts
			// the blend state of one attachment from the next (features/FeatureFlags.java:15). Ours
			// is asked for at device creation and the answer is what a pipeline can really be built
			// with, so a machine without it refuses the pack instead of drawing it with one function
			// where the pack wrote two.
			if (BufferBlending.served()) {
				served.add("PER_BUFFER_BLENDING");
			}

			required.removeIf(served::contains);

			if (!required.isEmpty()) {
				PackChain.stop();
				String names = String.join(", ", required);
				String named = ShaderPackSource.nameOf(pack);
				lastError = named + " requires " + names
						+ ", which this engine does not serve";
				CoreVK.logger().error("{} requires {}, which this engine does not serve, so "
						+ "nothing is drawn", named, names);
				OpenedPack.forgetKept();
				return;
			}

			// ONE opening of the archive for everything below, and it is what a player feels. The
			// place, the values, the chain, the chunk programs and every shadow compute used to
			// open the pack for themselves: for a zip that mounts the archive again each time, and
			// each of them then walked every source file of the pack line by line to rebuild the
			// same index of the same settings. Half a dozen identical readings and one more per
			// compute program, on the thread that draws, is the freeze after picking a pack.
			//
			// Nothing below may keep a path taken from it. ShaderPackSource says why: closing the
			// zip invalidates every path out of it, and what that leaves is a
			// ClosedFileSystemException on an unrelated read much later.
			if (askedFor.shadowMapScale() < PackFile.MAX_SHADOW_MAP_SCALE) {
				// Under the pack rather than beside the allocation, because the map's own line knows
				// one number and not where it came from. What is said is what the engine does, and
				// the size in force is the pack's own setting for the name where it has one.
				CoreVK.logger().info("The shadow map scale stands at {}%, so the size in force for this pack's "
					+ "shadow map, its own setting for it included, is rewritten to that fraction wherever it "
					+ "is written as a plain number", askedFor.shadowMapScale());
			}

			// Before OpenedPack.open, not after. SettingSet.resolve copies EngineDefines.table as
			// it is built, and the expander's liveness is that copy. The translator writes the
			// live machine() table back out as #define lines. Installed after the copy, the two
			// disagree: DISTANT_HORIZONS lands in the header, the DH uniforms stay in the body
			// because liftUniforms will not move a dead line, and Vulkan refuses a non-opaque
			// uniform outside a block. Complementary's deferred1 died that way on a DH toggle.
			// CoreVK has one table for both jobs, StandardMacros.java:64-65.
			PackDefines.install();
			report(pack);

			// The kept variant, and this reading is the only one in the engine entitled to it: it runs
			// on the render thread and two of them never overlap, which is what an opening's memos
			// require. Every other reader opens one of its own.
			try (OpenedPack opened = OpenedPack.openKept(pack, chosen, settings.profile())) {
				// The world decides the directory, and the pack decides which world that is: a folder
				// may be named anything and mapped in dimension.properties, so the name is read from
				// the pack rather than composed from the dimension.
				String place = PackPlace.place(opened.source());
				String world = PackPlace.world();

				PackValues values = PackValues.read(opened, place);
				// The terrain sampler is a static that outlives any one chain, and this is the
				// first point in the load where the pack's own answer exists. It has to be in force
				// before a chunk pass begins rather than when the chain is installed: the sampler is
				// settled from the renderer's begin, which the world's first draw reaches, and a
				// pack that cannot bear anisotropy would otherwise read one frame with it.
				TerrainSampler.breaksAnisotropy(values.breaksAnisotropy());

				long began = System.nanoTime();
				PackProgram.Chain chain =
						PackProgram.loadChain(opened, place, engine.passes(), engine.families());
				CoreVK.logger().info("Read {} programs of {} in {} ms", chain.programs().size(),
						chain.packName(), (System.nanoTime() - began) / 1_000_000L);

				// A refusal is a rule of the API this engine cannot bend, named with the program that
				// broke it. Dropping that program instead would move the half every later pass reads.
				List<String> refusals = chain.chain().refusals();
				if (!refusals.isEmpty()) {
					PackChain.stop();
					refusals.forEach(refusal -> CoreVK.logger().error("{}", refusal));
					// The first one, in the pack's own terms, so that the screen says why nothing is
					// drawn rather than sending the reader to the log for all of them.
					lastError = chain.packName() + " cannot be drawn as it stands: " + refusals.get(0);
					CoreVK.logger().error("{} cannot be drawn as it stands, nothing will be drawn",
							chain.packName());
					return;
				}

				announceRemoved(chain);
				live = new PackChain(chain, values, world, engine.seed(), opened, pack, chosen,
						settings.profile());
				// Terrain first, and it finishes before the other families start: two readers on one
				// zip at once is a race, and Sodium takes the mesh format here before any pass asks
				// for a shader. The other families then translate on a worker, so Complementary
				// Unbound's entities overlap the composite compiles and never sit on the render thread.
				PackChain.activate(live);
				live.terrain.read(opened);
				// After the two reads this thread makes and before the worker starts on the rest,
				// so the count on the line is a number rather than a snapshot of a tally still
				// moving.
				DriverTrig.announce();
				// The count, so that what a load costs is a figure in the log rather than a feeling.
				// It covers the whole of this method, the settings reading included, and the report
				// and the families are deliberately outside it: both run on a worker, off the thread
				// the player is waiting on, the families through one opening of their own.
				CoreVK.logger().info("Opened {} {} times to load it", chain.packName(),
						ShaderPackSource.openings() - openings);
				// Once per installed chain, whatever else this load does or fails to do later:
				// the second report, with the families in, only exists when a warmup fans out.
				// Flattening and translating only, because this method does no device work by
				// design: the modules follow on the first draws and the workers, and land in the
				// next report. The flattening is said first because it is paid first, and because
				// it is what a load whose translations all come off disk is left with. It names
				// the chain's units and not the pack's, which is what parts it from the pack
				// report's own expansion line: that walk counts every entry point the pack ships
				// and is made once ever, this one counts what this load built and is made at each.
				CoreVK.logger().info("Flattening the chain's units cost {} ms over {} of them "
						+ "with {} more handed back by an opening that had already built them, the "
						+ "pack report's own walk not counted; translating cost {} ms over {} "
						+ "translator calls before the chain went live; the modules follow on the "
						+ "draws and the workers, counted into the report that closes the warmup",
						LoadClock.expansionMillis(), LoadClock.expanded(),
						LoadClock.expansionsServed(), LoadClock.translationMillis(),
						LoadClock.translated());
				// Both ways, whichever way it went. A cache that only speaks when it helps leaves
				// every later reading of a log ambiguous, since a silence would then mean either
				// that nothing was served or that nothing was asked. It rides the line above rather
				// than standing on its own, so a load this method turned back from is silent on
				// both counts rather than on one of the two.
				CoreVK.logger().info("Translation cache: {} programs served from disk, {} "
								+ "translated, counted at the same point of the load as the line "
								+ "above", TranslationCache.served(), TranslationCache.translated());
				// Said from here and not from there: the cache lives in a package that names
				// nothing the game brings, so it keeps the note and this takes it. Once a run is
				// the cache's own latch and not this take. Here is also as early as a load can
				// say it and no earlier: a load that turned back above, or a family translating
				// on the worker started below, leaves the note for the load after this one.
				String refused = TranslationCache.takeRefusal();
				if (!refused.isEmpty()) {
					CoreVK.logger().warn("In the translation cache, {}, so it was translated "
							+ "instead. Said once a run: nothing about it stops a pack loading",
							refused);
				}
			}

			live.startFamilyPrefetch();
			turnOffImprovedTransparency();
			if (!engine.chain()) {
				EngineOptions.announceChainOff();
			}

			if (!engine.entities()) {
				EngineOptions.announceEntitiesOff(gameDirectory);
			}

			if (!engine.hand()) {
				EngineOptions.announceHandOff(gameDirectory);
			}

			if (!engine.clouds()) {
				EngineOptions.announceCloudsOff(gameDirectory);
			}

			if (!engine.weather()) {
				EngineOptions.announceWeatherOff(gameDirectory);
			}
		} catch (GpuDeviceLossException e) {
			throw e;
		} catch (IOException | RuntimeException e) {
			PackChain.stop();
			lastError = "Could not prepare this pack: " + e;
			CoreVK.logger().error("CoreVK could not prepare a pack's chain", e);
		}
	}

	
	private static void report(Path pack) {
		if (pack.equals(reported)) {
			return;
		}

		reported = pack;
		try {
			Util.backgroundExecutor().execute(() -> {
				try {
					PackReport.log(PackLoader.load(pack));
				} catch (IOException | RuntimeException e) {
					// Said by the report so that it keeps the report's own prefix: the failure
					// belongs to a measurement or a count, and the load has the last word on
					// whether the archive opens at all.
					PackReport.couldNotRead(ShaderPackSource.nameOf(pack), e);
					reported = null;
				}
			});
		} catch (RejectedExecutionException e) {
			reported = null;
		}
	}

	
	private static boolean namedAPack() {
		PackFile asked = askedFor;

		return asked.wantsPack() && !asked.namesNone();
	}

	
	private static void packIsMissing(Path gameDirectory) {
		packMissing = true;
		lastError = "No pack named '" + askedFor.name() + "' in "
				+ PackLoader.directory(gameDirectory) + ", so nothing is drawn";
		CoreVK.logger().error("{} names '{}', and no pack of that name is in {}, so nothing is drawn."
				+ " Check the name, or pick a pack in the settings screen", packFile(gameDirectory),
				askedFor.name(), PackLoader.directory(gameDirectory));
	}

	
	public static Path packFile(Path gameDirectory) {
		return gameDirectory.resolve(CoreVK.MOD_ID).resolve("pack.txt");
	}

	
	public static void turnOffImprovedTransparency() {
		if (!PackChain.drawingPack()) {
			return;
		}

		Options options = Minecraft.getInstance().options;
		if (options.improvedTransparency().get()) {
			options.improvedTransparency().set(false);
			options.graphicsPreset().set(GraphicsPreset.CUSTOM);
		}
	}

	
	private static Optional<Path> choose(List<Path> packs, PackFile asked) {
		if (!asked.wantsPack()) {
			return Optional.empty();
		}

		for (Path pack : packs) {
			if (pack.getFileName().toString().equals(asked.name())) {
				return Optional.of(pack);
			}
		}

		String wanted = asked.name().toLowerCase(Locale.ROOT);
		for (Path pack : packs) {
			if (pack.getFileName().toString().toLowerCase(Locale.ROOT).equals(wanted)) {
				return Optional.of(pack);
			}
		}

		if (asked.namesNone()) {
			return Optional.empty();
		}

		for (Path pack : packs) {
			if (pack.getFileName().toString().toLowerCase(Locale.ROOT).contains(wanted)) {
				return Optional.of(pack);
			}
		}

		return Optional.empty();
	}

	
	private static SettingsLayers.Resolved open(Path gameDirectory, Path pack) throws IOException {
		Minecraft minecraft = Minecraft.getInstance();
		PackSession opened = PackSession.read(gameDirectory, pack,
				minecraft == null ? "en_us" : minecraft.options.languageCode);
		session = opened;

		CoreVK.logger().info("{} lays out {} settings pages and {} settings, named by {}",
				opened.packFileName(), opened.menu().pages().size(), opened.menu().optionCount(),
				opened.menu().lang().file().isEmpty()
						? "nothing, so its own identifiers are shown"
						: opened.menu().lang().file());

		// Named rather than counted: each one is a name the pack forgot to declare or a page it
		// forgot to write, which is one line to fix in the pack and nothing we can do about here.
		List<String> unshown = opened.menu().warnings();
		if (!unshown.isEmpty()) {
			CoreVK.logger().warn("{} entries of this pack's menu name nothing it has, and are shown"
					+ " blank or greyed: {}", unshown.size(), unshown);
		}

		// At most once per pack, and three of the four answers move nothing at all. Worth a line because
		// a carried load is the only one where the values on screen were somewhere else a moment ago,
		// because a profile chosen before the move comes back as the values it named rather than as
		// its name.
		// Said here and not where the carry-over happens: the settings packages import no Minecraft
		// API, which is what lets them be run against the corpus without starting the game, and one
		// logger down there would end that.
		switch (opened.carried().carry()) {
			case MOVED -> CoreVK.logger().info("Moved {} of this pack's settings into {}, which is"
					+ " the file CoreVK reads too. They were in {}, which this engine kept before, and"
					+ " that file is now renamed aside. A profile chosen back then counts here as the"
					+ " values it named, the old file having stored only what differed from it",
					opened.saved().values().size(), opened.settingsFile(),
					SettingsFile.legacy(gameDirectory, opened.packFileName()));
			// Warn and not info: this is the one shape of old file that cannot be carried over, and
			// nothing else will say so. It is left exactly where it is.
			case UNKNOWN_PROFILE -> CoreVK.logger().warn("{} holds settings this engine kept before"
					+ " they moved, under the profile {}, which this version of the pack no longer"
					+ " declares. What it stored is only the difference from that profile, so it"
					+ " cannot be completed and nothing has been moved. Pick the settings again and"
					+ " press Apply, or edit {} by hand",
					opened.carried().file(), opened.carried().profile(), opened.settingsFile());
			// The exception itself is lost to the rule above, so the path carries the diagnosis: it
			// is the file that could not be read where that is what failed, and the shared one where
			// it is the folder that cannot be written.
			case FAILED -> CoreVK.logger().warn("Could not move this pack's settings, and {} is what"
					+ " could not be read or written. Nothing has been changed on disk, the pack is"
					+ " drawn with its own defaults, and the next load tries again",
					opened.carried().file());
			case NOTHING -> { }
		}

		List<String> stale = opened.stale();
		if (!stale.isEmpty()) {
			CoreVK.logger().info("{} settings in {} name nothing {} shows and are kept as they are:"
					+ " {}", stale.size(), opened.settingsFile(), opened.packFileName(), stale);
		}

		EngineOptions.announceForced(gameDirectory, opened);

		return opened.settings();
	}

	
	private static void announceRemoved(PackProgram.Chain chain) {
		Map<String, PackProgram.Refusal> refused = chain.removed();
		if (refused.isEmpty()) {
			return;
		}

		List<String> said = new ArrayList<>();
		refused.forEach((program, refusal) -> said.add(
				(chain.place().isEmpty() ? program : chain.place() + "/" + program)
						+ " is not run: it " + refusal.reason()));

		removed = List.copyOf(said);
		said.forEach(line -> CoreVK.logger().warn("{}", line));
		CoreVK.logger().warn("{} passes of {} are out for that reason and the rest of the chain "
				+ "runs, rebuilt on what is left", refused.size(), chain.packName());
	}

	
	public static Optional<PackSession> session() {
		return Optional.ofNullable(session);
	}

	
	public static List<String> removedPasses() {
		return removed;
	}

	
	public static Optional<String> lastError() {
		String error = lastError;
		if (error == null && PackChain.stopped()) {
			error = "This pack stopped drawing after an error, see the log";
		}

		return Optional.ofNullable(error);
	}

	
	static void error(String error) {
		lastError = error;
	}

	
	public static boolean opensOnPacks() {
		return packsFirst;
	}

	
	public static boolean noPackWanted() {
		return packOff;
	}

	
	public static boolean packMissing() {
		return packMissing;
	}

	
	public static PackFile askedFor() {
		return askedFor;
	}

	
	public static int shadowDistance() {
		return shadowDistance;
	}

	
	public static void shadowDistance(Path gameDirectory, int chunks) {
		// Taken on first and put away second, so that a folder that cannot be written still moves
		// the image the player just asked to move. What they lose then is the next session, not
		// this one, and the line below is what says so.
		shadowDistance = PackFile.EMPTY.withShadowDistance(chunks).shadowDistance();

		Path file = packFile(gameDirectory);
		try {
			PackFile.write(file, PackFile.read(file).withShadowDistance(chunks));
		} catch (IOException | RuntimeException e) {
			CoreVK.logger().error("CoreVK could not write the shadow distance to {}", file, e);
		}
	}

	
	public static int renderScale() {
		return askedFor.renderScale();
	}

	
	public static void renderScale(Path gameDirectory, int percent) {
		askedFor = askedFor.withRenderScale(percent);
		RenderScale.wanted(askedFor.renderScale());

		Path file = packFile(gameDirectory);
		try {
			PackFile.write(file, PackFile.read(file).withRenderScale(percent));
		} catch (IOException | RuntimeException e) {
			CoreVK.logger().error("CoreVK could not write the render scale to {}", file, e);
		}
	}

	
	public static int shadowMapScale() {
		return askedFor.shadowMapScale();
	}

	
	public static void shadowMapScale(Path gameDirectory, int percent) {
		askedFor = askedFor.withShadowMapScale(percent);
		// Off the record and not off the argument, which is what the two settings either side of
		// this one do: the record is where the range is enforced, so an out of range percentage
		// would otherwise rewrite the declaration at the raw number while every reader of the
		// setting, the allocation's own line included, named the bounded one.
		SettingSet.shadowMapScale(askedFor.shadowMapScale());

		Path file = packFile(gameDirectory);
		try {
			PackFile.write(file, PackFile.read(file).withShadowMapScale(percent));
		} catch (IOException | RuntimeException e) {
			CoreVK.logger().error("CoreVK could not write the shadow map scale to {}", file, e);
		}

		reload(gameDirectory);
	}

	
	static boolean reloadIfTheWorldMoved(Path gameDirectory) {
		boolean stale = PackDefines.stale();
		boolean moved = PackPlace.moved();
		if (!stale && !moved) {
			return false;
		}

		if (moved) {
			CoreVK.logger().info("Left {} for {}: a dimension replaces the root rather than layering "
					+ "over it, so the whole pack is read, translated and its colour targets allocated "
					+ "again, which is the hitch at the portal", PackPlace.settled(), PackPlace.world());
		} else if (PackDefines.distantHorizonsMoved()) {
			CoreVK.logger().info("Distant Horizons is drawing a far terrain now, or has stopped, so "
					+ "the pack is read again against DISTANT_HORIZONS: the symbol is what a pack "
					+ "branches its distant land on, and it cannot be right for both");
		} else if (PackDefines.textureFormatMoved()) {
			CoreVK.logger().info("The resource packs {}, so the pack is read again against "
					+ "MC_TEXTURE_FORMAT_LAB_PBR and the revision beside it: those are what a "
					+ "pack branches its material decode on, and they cannot be right for both",
					textureFormatDeclared());
		} else {
			CoreVK.logger().info("The world's own registries are here now, reloading the pack so "
					+ "what names a tag resolves against them");
		}

		// Straight to the reading, without forgetting the report first, and this is the one road
		// where that is right: nobody asked for this reload, the world moved under the pack.
		//
		// What it costs is worth knowing: the archive IS read again, and an archive edited on the
		// disk under the same name is drawn with its new content and still carries the report of the
		// old one, until somebody asks for a reload. Comparing content rather than a path would cost
		// more than the report it saves.
		readAgain(gameDirectory);

		return true;
	}

	
	private static String textureFormatDeclared() {
		EngineDefines.TextureFormat format = PbrAtlases.format();
		if (format == null) {
			return "declare no texture format any more";
		}

		String version = format.version() == null ? "" : " " + format.version();

		return "declare " + format.name() + version + " now";
	}

	
	public static void reload(Path gameDirectory) {
		// Forgotten before the reading and not restored after it, which is the only order that
		// works: the decision to print is taken inside load, so anything put back once load has
		// returned changes nothing but the name of a pack that has already been reported. Every
		// caller of this method is somebody asking for the pack to be read again, and a pack edited
		// on the disk under the same name would otherwise be reported once for the whole session.
		reported = null;
		readAgain(gameDirectory);
	}

	
	private static void readAgain(Path gameDirectory) {
		// The chain stops answering BEFORE anything of it is freed, and the order is the whole
		// of issue 111. Every accessor below reads this field, and a pass of the frame in
		// hand asks one of them: the sky asks for its own draw as it opens its render pass.
		// Freed first, that pass was handed a chain whose colour targets had just been closed
		// and built its descriptor on the views of them, which is a pointer into freed memory
		// and a lost device seconds later. Cleared first, the same pass finds nothing, and
		// falls back to the game's own shader for the one frame it takes to reload.
		// The stop is lifted with it, so that a pack that failed to compile can be fixed and
		// tried again without leaving the game.
		PackChain previous = PackChain.takeDown();

		if (previous != null) {
			// Guarded like the same call in leaveWorld, and the three lines below are why this road
			// needs it more than that one: the load and both settle() calls stand after it, and a
			// throw leaving here skips all three, which leaves a session drawing no pack at all with
			// the reload still asked for. Where the throw goes then depends on which caller asked.
			// The world moving under the pack reaches this from PackChain.beforeLevel, which stands
			// in front of the level and outside draw()'s try, and from the head of draw() on the one
			// road that skips the level render's wrap; the settings screen, the pack key and the
			// shadow map scale reach it from their own event, outside any frame. None is under a
			// catch.
			//
			// Nothing is disabled here, where leaveWorld's catch disables: that one keeps the chain
			// it failed to free and has to stop it being drawn, while this one has already dropped
			// its chain and is about to read a pack the failure says nothing about.
			try {
				previous.release();
			} catch (GpuDeviceLossException e) {
				// The one thing a failed free can raise that is not about the free. The game's
				// Vulkan backend raises it wherever a call comes back VK_ERROR_DEVICE_LOST
				// (VulkanUtils.crashIfFailure) and catches it in no place at all, which is what
				// makes it the end of the session rather than the end of a release: the load below
				// would allocate its targets against a device that is gone. Rethrown as it stands,
				// so the report opens on the lost device rather than on a line about video memory.
				throw e;
			} catch (RuntimeException e) {
				// Said here rather than left to whatever comes next, and said once: the chain is
				// unreachable by the time this is caught, so nothing frees the rest of it later and
				// no other line would ever name what it still holds. What it holds is buffers and
				// images; the pipelines and shader modules of that load are not part of it, since
				// the next device purge frees them whether a release reached them or not. That
				// purge carries the LIVE load over itself now (VulkanDeviceMixin), and the load
				// being replaced here is not the live one by the time it runs.
				CoreVK.logger().error("CoreVK could not hand back everything the pack being "
						+ "replaced held, so the buffers and images its release had not reached "
						+ "stay allocated until the game is closed", e);
			}
		}
		load(gameDirectory);
		// Taken whatever the load did with them. A pack that cannot be read at all settled nothing,
		// and without these it would be read again on the very next frame, and every frame after
		// that, for as long as the player stays where it failed.
		PackDefines.settle();
		PackPlace.settle();
	}
}
