package net.corevk.corevk.platform;

import java.nio.file.Path;

public interface CoreVKPlatform {

	String loaderName();

	String loaderVersion();

	String modVersion();

	String minecraftVersion();

	Path gameDirectory();

	boolean isModLoaded(String modId);
}
