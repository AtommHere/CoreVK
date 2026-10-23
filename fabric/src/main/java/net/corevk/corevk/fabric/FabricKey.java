package net.corevk.corevk.fabric;

import net.corevk.corevk.platform.EngineStages;
import net.corevk.corevk.screen.SettingsKey;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

final class FabricKey {

	private FabricKey() {
	}

	static void register() {
		KeyMappingHelper.registerKeyMapping(SettingsKey.OPEN);
		KeyMappingHelper.registerKeyMapping(SettingsKey.RELOAD);
		KeyMappingHelper.registerKeyMapping(SettingsKey.TOGGLE);
		ClientTickEvents.START_CLIENT_TICK.register(_ -> EngineStages.clientTickStart());
		ClientTickEvents.END_CLIENT_TICK.register(_ -> EngineStages.clientTick());
	}
}
