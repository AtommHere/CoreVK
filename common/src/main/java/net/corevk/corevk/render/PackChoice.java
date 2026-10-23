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

		DriverTrig.read(gameDirectory);

		RawLocals.read(gameDirectory);

		LoadClock.reset();
		TranslationCache.reset();

		CustomStorage.clear();

		TerrainSampler.breaksAnisotropy(false);

		int openings = ShaderPackSource.openings();
		LegacyTerrainFilter.read(gameDirectory);
		session = null;
		lastError = null;
		removed = List.of();
		packsFirst = true;
		packOff = false;
		packMissing = false;

		askedFor = PackFile.EMPTY;
		PackChain live = null;
		try {

			askedFor = PackFile.read(packFile(gameDirectory));

			shadowDistance = askedFor.shadowDistance();
			RenderScale.wanted(askedFor.renderScale());

			SettingSet.shadowMapScale(askedFor.shadowMapScale());

			List<Path> packs = PackLoader.candidates(gameDirectory);
			if (packs.isEmpty()) {
				TerrainDraw.wanted(false);
				EntityDraw.wanted(false);
				HandDraw.wanted(false);

				if (namedAPack()) {
					packIsMissing(gameDirectory);

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

				TerrainDraw.wanted(false);
				EntityDraw.wanted(false);
				HandDraw.wanted(false);
				if (namedAPack()) {
					packIsMissing(gameDirectory);
					OpenedPack.forgetKept();

					return;
				}

				packOff = true;
				CoreVK.logger().info("No pack asked for, so none of the {} in {} is read and the game "
						+ "draws its own image. Pick one in the settings screen, or name it in {}",
						packs.size(), PackLoader.directory(gameDirectory), packFile(gameDirectory));
				OpenedPack.forgetKept();
				return;
			}

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

			List<String> required = new ArrayList<>(PackLoader.properties(pack).requiredFeatures());

			Set<String> served = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
			served.addAll(List.of("BLOCK_EMISSION_ATTRIBUTE", "COMPUTE_SHADERS", "CUSTOM_IMAGES",
					"ENTITY_TRANSLUCENT", PackDirectives.HIGHER_SHADOWCOLOR, "SSBO",
					"SEPARATE_HARDWARE_SAMPLERS"));

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

			if (askedFor.shadowMapScale() < PackFile.MAX_SHADOW_MAP_SCALE) {

				CoreVK.logger().info("The shadow map scale stands at {}%, so the size in force for this pack's "
					+ "shadow map, its own setting for it included, is rewritten to that fraction wherever it "
					+ "is written as a plain number", askedFor.shadowMapScale());
			}

			PackDefines.install();
			report(pack);

			try (OpenedPack opened = OpenedPack.openKept(pack, chosen, settings.profile())) {

				String place = PackPlace.place(opened.source());
				String world = PackPlace.world();

				PackValues values = PackValues.read(opened, place);

				TerrainSampler.breaksAnisotropy(values.breaksAnisotropy());

				long began = System.nanoTime();
				PackProgram.Chain chain =
						PackProgram.loadChain(opened, place, engine.passes(), engine.families());
				CoreVK.logger().info("Read {} programs of {} in {} ms", chain.programs().size(),
						chain.packName(), (System.nanoTime() - began) / 1_000_000L);

				List<String> refusals = chain.chain().refusals();
				if (!refusals.isEmpty()) {
					PackChain.stop();
					refusals.forEach(refusal -> CoreVK.logger().error("{}", refusal));

					lastError = chain.packName() + " cannot be drawn as it stands: " + refusals.get(0);
					CoreVK.logger().error("{} cannot be drawn as it stands, nothing will be drawn",
							chain.packName());
					return;
				}

				announceRemoved(chain);
				live = new PackChain(chain, values, world, engine.seed(), opened, pack, chosen,
						settings.profile());

				PackChain.activate(live);
				live.terrain.read(opened);

				DriverTrig.announce();

				CoreVK.logger().info("Opened {} {} times to load it", chain.packName(),
						ShaderPackSource.openings() - openings);

				CoreVK.logger().info("Flattening the chain's units cost {} ms over {} of them "
						+ "with {} more handed back by an opening that had already built them, the "
						+ "pack report's own walk not counted; translating cost {} ms over {} "
						+ "translator calls before the chain went live; the modules follow on the "
						+ "draws and the workers, counted into the report that closes the warmup",
						LoadClock.expansionMillis(), LoadClock.expanded(),
						LoadClock.expansionsServed(), LoadClock.translationMillis(),
						LoadClock.translated());

				CoreVK.logger().info("Translation cache: {} programs served from disk, {} "
								+ "translated, counted at the same point of the load as the line "
								+ "above", TranslationCache.served(), TranslationCache.translated());

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

		List<String> unshown = opened.menu().warnings();
		if (!unshown.isEmpty()) {
			CoreVK.logger().warn("{} entries of this pack's menu name nothing it has, and are shown"
					+ " blank or greyed: {}", unshown.size(), unshown);
		}

		switch (opened.carried().carry()) {
			case MOVED -> CoreVK.logger().info("Moved {} of this pack's settings into {}, which is"
					+ " the file CoreVK reads too. They were in {}, which this engine kept before, and"
					+ " that file is now renamed aside. A profile chosen back then counts here as the"
					+ " values it named, the old file having stored only what differed from it",
					opened.saved().values().size(), opened.settingsFile(),
					SettingsFile.legacy(gameDirectory, opened.packFileName()));

			case UNKNOWN_PROFILE -> CoreVK.logger().warn("{} holds settings this engine kept before"
					+ " they moved, under the profile {}, which this version of the pack no longer"
					+ " declares. What it stored is only the difference from that profile, so it"
					+ " cannot be completed and nothing has been moved. Pick the settings again and"
					+ " press Apply, or edit {} by hand",
					opened.carried().file(), opened.carried().profile(), opened.settingsFile());

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

		reported = null;
		readAgain(gameDirectory);
	}

	private static void readAgain(Path gameDirectory) {

		PackChain previous = PackChain.takeDown();

		if (previous != null) {

			try {
				previous.release();
			} catch (GpuDeviceLossException e) {

				throw e;
			} catch (RuntimeException e) {

				CoreVK.logger().error("CoreVK could not hand back everything the pack being "
						+ "replaced held, so the buffers and images its release had not reached "
						+ "stay allocated until the game is closed", e);
			}
		}
		load(gameDirectory);

		PackDefines.settle();
		PackPlace.settle();
	}
}
