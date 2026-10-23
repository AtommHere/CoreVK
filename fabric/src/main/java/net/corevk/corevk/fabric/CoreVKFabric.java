package net.corevk.corevk.fabric;

import net.corevk.corevk.CoreVK;

import net.fabricmc.api.ClientModInitializer;

public final class CoreVKFabric implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		CoreVK.initClient(new FabricPlatform());
		FabricKey.register();
	}
}
