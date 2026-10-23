package net.corevk.corevk;

import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class ScreenText {

	
	public static final String PACKS_TITLE = "options.corevk.packs_title";

	
	public static final String PAGE_TITLE = "options.corevk.page_title";

	
	public static final String CRASH_API = "options.corevk.crash_graphics_api";
	public static final String CRASH_API_TOOLTIP = "options.corevk.crash_graphics_api_tooltip";
	public static final String CRASH_API_VULKAN = "options.corevk.crash_graphics_api.vulkan";
	public static final String CRASH_API_OPENGL = "options.corevk.crash_graphics_api.opengl";
	public static final String CRASH_API_GAME = "options.corevk.crash_graphics_api.game";

	
	public static final String SHADOW_DISTANCE = "options.corevk.shadow_distance";
	public static final String SHADOW_DISTANCE_TOOLTIP = "options.corevk.shadow_distance_tooltip";
	public static final String SHADOW_DISTANCE_FORCED = "options.corevk.shadow_distance_forced";

	
	public static final String SHADOW_MAP_SCALE = "options.corevk.shadow_map_scale";
	public static final String SHADOW_MAP_SCALE_TOOLTIP = "options.corevk.shadow_map_scale_tooltip";

	
	public static final String RENDER_SCALE = "options.corevk.render_scale";
	public static final String RENDER_SCALE_TOOLTIP = "options.corevk.render_scale_tooltip";

	public static final String TEMPORAL_FOLD = "options.corevk.temporal_fold";
	public static final String TEMPORAL_FOLD_TOOLTIP = "options.corevk.temporal_fold_tooltip";

	
	public static final String MODULE_CACHE_CEILING = "options.corevk.module_cache_ceiling";
	public static final String MODULE_CACHE_CEILING_TOOLTIP =
			"options.corevk.module_cache_ceiling_tooltip";

	
	public static final String COMPILE_CARD = "options.corevk.compile_card";
	public static final String COMPILE_CARD_TOOLTIP = "options.corevk.compile_card_tooltip";

	
	public static final String SHADOW_AMORTISATION = "options.corevk.shadow_amortisation";
	public static final String SHADOW_AMORTISATION_TOOLTIP =
			"options.corevk.shadow_amortisation_tooltip";
	public static final String SHADOW_AMORTISATION_OFF = "options.corevk.shadow_amortisation_off";
	
	public static final String SHADOW_AMORTISATION_FRAME =
			"options.corevk.shadow_amortisation_frame";
	public static final String SHADOW_AMORTISATION_FRAMES =
			"options.corevk.shadow_amortisation_frames";

	
	public static final String SELECT_TITLE = "pack.corevk.select_title";
	public static final String CONFIGURE_TITLE = "pack.corevk.configure_title";

	
	public static final String TITLE = "options.corevk.title";

	
	public static final String PACKS = "options.corevk.packs";

	public static final String APPLY = "options.corevk.apply";

	
	public static final String FOLDER = "options.corevk.folder";

	
	public static final String GUI_HIDE = "options.corevk.gui_hide";
	public static final String GUI_SHOW = "options.corevk.gui_show";

	
	public static final String RESET = "options.corevk.reset";
	public static final String RESET_TOOLTIP = "options.corevk.reset_tooltip";
	public static final String IMPORT_TOOLTIP = "options.corevk.import_tooltip";
	public static final String EXPORT_TOOLTIP = "options.corevk.export_tooltip";

	
	public static final String RESET_CONFIRM = "options.corevk.reset_confirm";

	
	public static final String RESET_CONFIRM_DETAIL = "options.corevk.reset_confirm_detail";

	
	public static final String RELOAD = "options.corevk.reload";

	
	public static final String FULLSCREEN = "options.corevk.fullscreen";

	
	public static final String SET_TO_DEFAULT = "options.corevk.set_to_default";

	public static final String PROFILE = "options.corevk.profile";

	
	public static final String PROFILE_CUSTOM = "options.corevk.profile_custom";

	
	public static final String SHADERS_ENABLED = "options.corevk.shaders_enabled";
	public static final String SHADERS_DISABLED = "options.corevk.shaders_disabled";
	public static final String SHADERS_NONE_PRESENT = "options.corevk.shaders_none_present";

	
	public static final String PACK_DROP = "options.corevk.pack_drop";

	
	public static final String ADDED_PACK = "options.corevk.added_pack";
	public static final String ADDED_PACKS = "options.corevk.added_packs";
	public static final String FAILED_ADD = "options.corevk.failed_add";
	public static final String FAILED_ADD_SINGLE = "options.corevk.failed_add_single";
	public static final String COPY_ERROR = "options.corevk.copy_error";
	public static final String COPY_ERROR_EXISTS = "options.corevk.copy_error_exists";

	
	public static final String TOO_MANY_FILES = "options.corevk.too_many_files";
	public static final String IMPORTED_SETTINGS = "options.corevk.imported_settings";
	public static final String FAILED_IMPORT = "options.corevk.failed_import";

	
	public static final String FORCED = "options.corevk.forced";

	
	public static final String REMOVED = "options.corevk.removed";

	
	public static final String ERROR = "options.corevk.error";

	public static final String NO_PACK = "options.corevk.no_pack";

	
	public static final String PACK_OFF = "options.corevk.pack_off";

	
	public static final String PACK_MISSING = "options.corevk.pack_missing";

	
	public static final String NO_SETTINGS = "options.corevk.no_settings";

	public static final String OPEN_SETTINGS = "key.corevk.open_settings";

	
	public static final String RELOAD_PACK = "key.corevk.reload_pack";
	public static final String PACK_RELOADED = "options.corevk.pack_reloaded";
	public static final String RELOAD_FAILED = "options.corevk.reload_failed";

	
	public static final String TOGGLE_SHADERS = "key.corevk.toggle_shaders";
	public static final String SHADERS_TOGGLED = "options.corevk.shaders_toggled";
	public static final String SHADERS_TURNED_OFF = "options.corevk.shaders_turned_off";
	public static final String TOGGLE_FAILED = "options.corevk.toggle_failed";

	
	public static final String OTHER_BACKEND = "options.corevk.other_backend";
	public static final String GRAPHICS_API = "options.graphicsApi";
	public static final String GRAPHICS_API_VULKAN = "options.graphicsApi.vulkan";

	
	public static final String GRAPHICS_API_OPENGL = "options.graphicsApi.opengl";

	
	public static final String BACKEND_PLACEHOLDER = "options.corevk.backend_placeholder";
	public static final String BACKEND_SWITCH = "options.corevk.backend_switch";
	public static final String BACKEND_RETURN = "options.corevk.backend_return";

	
	public static final String COMPILING = "overlay.corevk.compiling";

	
	public static final String COMPILED = "overlay.corevk.compiled";

	
	public static final String KEY_CATEGORY = "key.category.corevk.keybinds";

	
	public static final int MODIFIED = 0xFFffc94a;

	
	public static MutableComponent fromPack(String text) {
		return Component.literal(text);
	}

	private ScreenText() {
	}
}
