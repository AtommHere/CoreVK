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

		try (ShaderPackSource source = ShaderPackSource.open(packPath)) {
			OptionIndex index = source.options();
			declared = index.names();
			menu = PackMenu.build(source.packName(), index, ShaderProperties.parse(source),
					PackLang.read(source, languageCode));
		}

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
