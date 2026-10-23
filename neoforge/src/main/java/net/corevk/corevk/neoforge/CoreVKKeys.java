package net.corevk.corevk.neoforge;

import net.corevk.corevk.screen.SettingsKey;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

public final class CoreVKKeys {

	private CoreVKKeys() {
	}

	public static void register(IEventBus modBus) {
		modBus.addListener(RegisterKeyMappingsEvent.class, event -> {
			event.registerCategory(SettingsKey.CATEGORY);
			event.register(SettingsKey.OPEN);
			event.register(SettingsKey.RELOAD);
			event.register(SettingsKey.TOGGLE);
		});
	}
}
