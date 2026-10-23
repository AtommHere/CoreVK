package net.corevk.corevk.pack.source;

import net.corevk.corevk.pack.option.OptionIndex;
import net.corevk.corevk.pack.option.OptionValue;
import net.corevk.corevk.pack.option.SettingSet;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

public record OpenedPack(Path packPath, ShaderPackSource source, OptionIndex options,
		ShaderProperties properties, SettingSet settings) implements AutoCloseable {

	
	public static OpenedPack openKept(Path packPath, Map<String, OptionValue> chosen, String profile)
			throws IOException {
		return KeptPack.open(packPath, chosen, profile);
	}

	
	public static void forgetKept() {
		KeptPack.forget();
	}

	
	public static OpenedPack open(Path packPath, Map<String, OptionValue> chosen, String profile)
			throws IOException {
		ShaderPackSource source = ShaderPackSource.open(packPath);
		try {
			OptionIndex options = source.options();
			ShaderProperties properties = ShaderProperties.parse(source);
			Map<String, OptionValue> fromProfile =
					profile.isEmpty() ? Map.of() : properties.expandProfile(profile);
			SettingSet settings =
					SettingSet.resolve(fromProfile, chosen, profile.isEmpty() ? "chosen" : profile);

			return new OpenedPack(packPath, source, options, properties, settings);
		} catch (IOException | RuntimeException e) {
			// The archive is ours the moment it opened, so a failure below has to give it back.
			// Left to the caller it would leak a mounted filesystem per pack that cannot be read.
			source.close();
			throw e;
		}
	}

	
	@Override
	public void close() throws IOException {
		if (KeptPack.holds(this)) {
			this.source.forgetDerived();

			return;
		}

		this.source.close();
	}
}
