package net.corevk.corevk.sodium;

import net.corevk.corevk.cache.ModuleCache;
import net.corevk.corevk.HostReport;
import net.corevk.corevk.CoreVKBeside;
import net.corevk.corevk.render.PackChoice;
import net.corevk.corevk.render.ShadowAmortisation;
import net.corevk.corevk.render.StartupGuard;
import net.corevk.corevk.render.TemporalAccumulation;
import net.corevk.corevk.render.TerrainDraw;
import net.corevk.corevk.screen.CompileCard;
import net.corevk.corevk.screen.PackScreens;
import net.corevk.corevk.ScreenText;
import net.corevk.corevk.settings.GraphicsApiChoice;
import net.corevk.corevk.settings.PackFile;
import net.corevk.corevk.CoreVK;

import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.ConfigState;
import net.caffeinemc.mods.sodium.api.config.option.OptionImpact;
import net.caffeinemc.mods.sodium.api.config.option.Range;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.ModOptionsBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.OptionBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.TextureFilteringMethod;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Set;

public final class ConfigEntry implements ConfigEntryPoint {

	private static final Identifier ICON =
			Identifier.fromNamespaceAndPath(CoreVK.MOD_ID, "textures/gui/config-icon.png");

	private static final int THEME = 0xFF2E6FD9;

	private static final Identifier FILTERING = Identifier.parse("sodium:quality.filtering_mode");

	private static final Identifier SHADOW_DISTANCE =
			Identifier.fromNamespaceAndPath(CoreVK.MOD_ID, "shadow_distance");

	private static final Identifier RENDER_SCALE =
			Identifier.fromNamespaceAndPath(CoreVK.MOD_ID, "render_scale");

	private static final Identifier SHADOW_MAP_SCALE =
			Identifier.fromNamespaceAndPath(CoreVK.MOD_ID, "shadow_map_scale");

	private static final Identifier GRAPHICS_API =
			Identifier.fromNamespaceAndPath(CoreVK.MOD_ID, "graphics_api");

	private static final Identifier MODULE_CACHE_CEILING =
			Identifier.fromNamespaceAndPath(CoreVK.MOD_ID, "module_cache_ceiling");

	private static final Identifier SHADOW_AMORTISATION =
			Identifier.fromNamespaceAndPath(CoreVK.MOD_ID, "shadow_amortisation");

	private static final Identifier TEMPORAL_FOLD =
			Identifier.fromNamespaceAndPath(CoreVK.MOD_ID, "temporal_fold");

	private static final Identifier COMPILE_CARD =
			Identifier.fromNamespaceAndPath(CoreVK.MOD_ID, "compile_card");

	private static final Set<TextureFilteringMethod> WITHOUT_RGSS =
			Set.of(TextureFilteringMethod.NONE, TextureFilteringMethod.ANISOTROPIC);
	private static final Set<TextureFilteringMethod> EVERY_METHOD =
			Set.of(TextureFilteringMethod.values());

	@Override
	public void registerConfigLate(ConfigBuilder builder) {
		ModOptionsBuilder options = builder.registerOwnModOptions()

				.setNonTintedIcon(ICON)
				.setColorTheme(builder.createColorTheme().setBaseThemeRGB(THEME))
				.addPage(builder.createExternalPage()
						.setName(Component.translatable(ScreenText.PACKS_TITLE))
						.setScreenConsumer(parent ->
								Minecraft.getInstance().gui.setScreen(PackScreens.open(parent))));

		if (!HostReport.otherBackend()) {
			options.addPage(builder.createOptionPage()
					.setName(Component.translatable(ScreenText.PAGE_TITLE))
					.addOptionGroup(builder.createOptionGroup()
							.addOption(shadowDistance(builder))
							.addOption(shadowMapScale(builder))
							.addOption(renderScale(builder))
							.addOption(temporalFold(builder))
							.addOption(shadowAmortisation(builder))
							.addOption(graphicsApi(builder))
							.addOption(moduleCacheCeiling(builder))
							.addOption(compileCard(builder))));
		}

		if (HostReport.otherBackend() || CoreVKBeside.draws()) {
			return;
		}

		options.registerOptionOverlay(FILTERING,
				builder.createEnumOption(FILTERING, TextureFilteringMethod.class)
						.setAllowedValuesProvider(
								state -> TerrainDraw.asked() ? WITHOUT_RGSS : EVERY_METHOD,
								ConfigState.UPDATE_ON_REBUILD));
	}

	private static OptionBuilder renderScale(ConfigBuilder builder) {
		return builder.createIntegerOption(RENDER_SCALE)
				.setName(Component.translatable(ScreenText.RENDER_SCALE))
				.setTooltip(_ -> Component.translatable(ScreenText.RENDER_SCALE_TOOLTIP))
				.setDefaultValue(PackFile.DEFAULT_RENDER_SCALE)
				.setRange(new Range(PackFile.MIN_RENDER_SCALE, PackFile.MAX_RENDER_SCALE, 5))
				.setBinding(percent -> PackChoice.renderScale(CoreVK.platform().gameDirectory(),
								percent),
						PackChoice::renderScale)
				.setValueFormatter(percent -> Component.literal(percent + "%"))

				.setStorageHandler(() -> {})
				.setImpact(OptionImpact.HIGH);
	}

	private static OptionBuilder shadowMapScale(ConfigBuilder builder) {
		return builder.createIntegerOption(SHADOW_MAP_SCALE)
				.setName(Component.translatable(ScreenText.SHADOW_MAP_SCALE))
				.setTooltip(_ -> Component.translatable(ScreenText.SHADOW_MAP_SCALE_TOOLTIP))
				.setDefaultValue(PackFile.DEFAULT_SHADOW_MAP_SCALE)
				.setRange(new Range(PackFile.MIN_SHADOW_MAP_SCALE, PackFile.MAX_SHADOW_MAP_SCALE, 5))
				.setBinding(percent -> PackChoice.shadowMapScale(CoreVK.platform().gameDirectory(),
								percent),
						PackChoice::shadowMapScale)
				.setValueFormatter(percent -> Component.literal(percent + "%"))
				.setStorageHandler(() -> {})
				.setImpact(OptionImpact.HIGH);
	}

	private static OptionBuilder graphicsApi(ConfigBuilder builder) {
		return builder.createEnumOption(GRAPHICS_API, GraphicsApiChoice.class)
				.setName(Component.translatable(ScreenText.CRASH_API))
				.setTooltip(_ -> Component.translatable(ScreenText.CRASH_API_TOOLTIP))
				.setDefaultValue(GraphicsApiChoice.DEFAULT)
				.setBinding(chosen -> {
					GraphicsApiChoice.write(CoreVK.platform().gameDirectory(), chosen);
					StartupGuard.forget();
				}, GraphicsApiChoice::read)
				.setElementNameProvider(chosen -> Component.translatable(switch (chosen) {
					case VULKAN -> ScreenText.CRASH_API_VULKAN;
					case OPENGL -> ScreenText.CRASH_API_OPENGL;
					case GAME -> ScreenText.CRASH_API_GAME;
				}))

				.setStorageHandler(() -> {});
	}

	private static OptionBuilder temporalFold(ConfigBuilder builder) {
		return builder.createBooleanOption(TEMPORAL_FOLD)
				.setName(Component.translatable(ScreenText.TEMPORAL_FOLD))
				.setTooltip(_ -> Component.translatable(ScreenText.TEMPORAL_FOLD_TOOLTIP))
				.setDefaultValue(TemporalAccumulation.DEFAULT_WANTED)
				.setBinding(TemporalAccumulation::setWanted, TemporalAccumulation::wanted)
				.setEnabledProvider(state -> state.readIntOption(RENDER_SCALE) < PackFile.MAX_RENDER_SCALE,
						RENDER_SCALE)
				.setStorageHandler(() -> {})
				.setImpact(OptionImpact.MEDIUM);
	}

	private static OptionBuilder shadowAmortisation(ConfigBuilder builder) {
		return builder.createIntegerOption(SHADOW_AMORTISATION)
				.setName(Component.translatable(ScreenText.SHADOW_AMORTISATION))
				.setTooltip(_ -> Component.translatable(ScreenText.SHADOW_AMORTISATION_TOOLTIP))
				.setDefaultValue(ShadowAmortisation.DEFAULT_FRAMES)
				.setRange(new Range(ShadowAmortisation.MIN_FRAMES, ShadowAmortisation.MAX_FRAMES, 1))
				.setBinding(ShadowAmortisation::setFrames, ShadowAmortisation::frames)
				.setValueFormatter(frames -> switch (frames) {
					case 0 -> Component.translatable(ScreenText.SHADOW_AMORTISATION_OFF);
					case 1 -> Component.translatable(ScreenText.SHADOW_AMORTISATION_FRAME, frames);
					default -> Component.translatable(ScreenText.SHADOW_AMORTISATION_FRAMES, frames);
				})
				.setStorageHandler(() -> {})
				.setImpact(OptionImpact.HIGH);
	}

	private static OptionBuilder moduleCacheCeiling(ConfigBuilder builder) {
		return builder.createIntegerOption(MODULE_CACHE_CEILING)
				.setName(Component.translatable(ScreenText.MODULE_CACHE_CEILING))
				.setTooltip(_ -> Component.translatable(ScreenText.MODULE_CACHE_CEILING_TOOLTIP))
				.setDefaultValue(ModuleCache.DEFAULT_CEILING_MIB)
				.setRange(new Range(ModuleCache.MIN_CEILING_MIB, ModuleCache.MAX_CEILING_MIB,
						ModuleCache.CEILING_STEP_MIB))
				.setBinding(ModuleCache::setCeilingMib, ModuleCache::ceilingMib)
				.setValueFormatter(mib -> Component.literal(mib + " MiB"))
				.setStorageHandler(() -> {})
				.setImpact(OptionImpact.LOW);
	}

	private static OptionBuilder compileCard(ConfigBuilder builder) {
		return builder.createBooleanOption(COMPILE_CARD)
				.setName(Component.translatable(ScreenText.COMPILE_CARD))
				.setTooltip(_ -> Component.translatable(ScreenText.COMPILE_CARD_TOOLTIP))
				.setDefaultValue(CompileCard.DEFAULT_WANTED)
				.setBinding(CompileCard::setWanted, CompileCard::wanted)
				.setStorageHandler(() -> {});
	}

	private static OptionBuilder shadowDistance(ConfigBuilder builder) {
		return builder.createIntegerOption(SHADOW_DISTANCE)
				.setName(Component.translatable(ScreenText.SHADOW_DISTANCE))
				.setTooltip(_ -> Component.translatable(
						TerrainDraw.forcedShadowDistanceChunks().isPresent()
								? ScreenText.SHADOW_DISTANCE_FORCED
								: ScreenText.SHADOW_DISTANCE_TOOLTIP))
				.setDefaultValue(PackFile.DEFAULT_SHADOW_DISTANCE)
				.setRange(new Range(PackFile.MIN_SHADOW_DISTANCE, PackFile.MAX_SHADOW_DISTANCE, 1))
				.setBinding(chunks -> PackChoice.shadowDistance(CoreVK.platform().gameDirectory(),
								chunks),
						() -> Math.clamp(TerrainDraw.shadowDistanceChunks(),
								PackFile.MIN_SHADOW_DISTANCE, PackFile.MAX_SHADOW_DISTANCE))

				.setValueFormatter(chunks -> chunks <= 0
						? CommonComponents.OPTION_OFF
						: Component.translatable("options.chunks", chunks))

				.setEnabledProvider(_ -> TerrainDraw.forcedShadowDistanceChunks().isEmpty(),
						ConfigState.UPDATE_ON_REBUILD)

				.setStorageHandler(() -> {})
				.setImpact(OptionImpact.HIGH);
	}
}
