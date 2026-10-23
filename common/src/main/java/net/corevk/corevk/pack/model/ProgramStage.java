package net.corevk.corevk.pack.model;

import java.util.Locale;
import java.util.Optional;

public enum ProgramStage {

	VERTEX("vsh"),
	FRAGMENT("fsh"),
	GEOMETRY("gsh"),
	COMPUTE("csh"),
	TESSELLATION_CONTROL("tcs"),
	TESSELLATION_EVALUATION("tes");

	private final String extension;

	ProgramStage(String extension) {
		this.extension = extension;
	}

	public String extension() {
		return this.extension;
	}

	public static Optional<ProgramStage> fromExtension(String extension) {
		String lower = extension.toLowerCase(Locale.ROOT);
		for (ProgramStage stage : values()) {
			if (stage.extension.equals(lower)) {
				return Optional.of(stage);
			}
		}

		return Optional.empty();
	}

	public static Optional<ProgramStage> ofFile(String fileName) {
		int dot = fileName.lastIndexOf('.');

		return dot < 0 ? Optional.empty() : fromExtension(fileName.substring(dot + 1));
	}
}
