package net.corevk.corevk.settings;

import net.corevk.corevk.pack.menu.MenuValues;
import net.corevk.corevk.pack.menu.PackMenu;
import net.corevk.corevk.pack.option.OptionIndex;
import net.corevk.corevk.pack.option.OptionValue;
import net.corevk.corevk.pack.source.PackLang;
import net.corevk.corevk.pack.source.ShaderPackSource;
import net.corevk.corevk.pack.source.ShaderProperties;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record PackSession(Path gameDirectory, Path packPath, String packFileName, PackMenu menu,
		Set<String> declared, SettingsFile.Carried carried, SettingsFile.Stored saved,
		Map<String, OptionValue> forced) {

	public PackSession {
		declared = Set.copyOf(declared);
	}

	public static PackSession read(Path gameDirectory, Path packPath, String languageCode)
			throws IOException {
		String packFileName = packPath.getFileName().toString();

		PackMenu menu;
		Set<String> declared;
		// One opening for both, the index being what the menu is built from anyway. Read here rather
		// than left to the loader further down because the layers are resolved before a program is
		// translated, and a value dropped has to be named at the moment it is dropped.
		try (ShaderPackSource source = ShaderPackSource.open(packPath)) {
			OptionIndex index = source.options();
			declared = index.names();
			menu = PackMenu.build(source.packName(), index, ShaderProperties.parse(source),
					PackLang.read(source, languageCode));
		}

		// Before the reading and never after it: what it writes is what the reading then finds, so
		// there is one answer and not a first load that behaves unlike every later one.
		//
		// Caught here rather than left to the loader, and that is the point of catching it at all:
		// a shaderpacks folder the player cannot write to, or an old file another process is
		// holding, would otherwise take the whole pack down through the loader's own catch. What is
		// at stake is settings that are still on disk, so the pack draws with the pack's defaults
		// and the old file waits for the next load.
		//
		// Swallowed rather than reported, and the caller says it instead: nothing in this package
		// nor in pack/ IMPORTS a Minecraft API, which is what lets both be compiled and run against
		// the corpus without starting the game. One logger would end that.
		SettingsFile.Carried carried;
		try {
			carried = SettingsFile.migrate(gameDirectory, packFileName, menu);
		} catch (IOException e) {
			carried = new SettingsFile.Carried(SettingsFile.Carry.FAILED, "", SettingsFile.legacy(gameDirectory, packFileName));
		}

		return new PackSession(gameDirectory, packPath, packFileName, menu, declared, carried,
				SettingsFile.read(SettingsFile.of(gameDirectory, packFileName)),
				SettingsLayers.forced(gameDirectory));
	}

	
	public Path settingsFile() {
		return SettingsFile.of(this.gameDirectory, this.packFileName);
	}

	
	public SettingsLayers.Resolved settings() {
		return SettingsLayers.resolve(this.saved, this.forced);
	}

	
	public String profileInfo() {
		return MenuValues.of(this.menu, this.saved.values(), forcedText()).profileInfo();
	}

	
	public List<String> stale() {
		List<String> stale = new ArrayList<>();
		for (String name : this.saved.values().keySet()) {
			// A name options.txt also carries is left out, and it is the only exclusion here: that
			// file is answered for word by word by EngineOptions.announceForced, which knows
			// whether the pack declares it, and two lines about one word read as two words.
			if (this.menu.option(name).isEmpty() && !this.forced.containsKey(name)) {
				stale.add(name);
			}
		}

		return List.copyOf(stale);
	}

	
	public Map<String, String> forcedText() {
		Map<String, String> text = new LinkedHashMap<>();
		this.forced.forEach((name, value) -> text.put(name, value.asText()));

		return Collections.unmodifiableMap(text);
	}
}
