package net.corevk.corevk.fabric;

import net.corevk.corevk.platform.CoreVKPlatform;
import net.corevk.corevk.CoreVK;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

final class FabricPlatform implements CoreVKPlatform {

	@Override
	public String loaderName() {
		return "Fabric";
	}

	@Override
	public String loaderVersion() {
		return versionOf("fabricloader");
	}

	@Override
	public String modVersion() {
		return versionOf(CoreVK.MOD_ID);
	}

	@Override
	public String minecraftVersion() {
		return versionOf("minecraft");
	}

	@Override
	public Path gameDirectory() {
		return FabricLoader.getInstance().getGameDir();
	}

	@Override
	public boolean isModLoaded(String modId) {
		return FabricLoader.getInstance().isModLoaded(modId);
	}

	private static String versionOf(String modId) {
		return FabricLoader.getInstance()
				.getModContainer(modId)
				.map(container -> container.getMetadata().getVersion().getFriendlyString())
				.orElse("unknown");
	}
}
