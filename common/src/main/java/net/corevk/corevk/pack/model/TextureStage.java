package net.corevk.corevk.pack.model;

import java.util.Optional;

public enum TextureStage {

	SETUP,
	BEGIN,
	SHADOWCOMP,
	PREPARE,
	
	GBUFFERS,
	DEFERRED,
	
	COMPOSITE;

	
	public static Optional<TextureStage> parse(String name) {
		return switch (name) {
			case "setup" -> Optional.of(SETUP);
			case "begin" -> Optional.of(BEGIN);
			case "shadowcomp" -> Optional.of(SHADOWCOMP);
			case "prepare" -> Optional.of(PREPARE);
			case "gbuffers" -> Optional.of(GBUFFERS);
			case "deferred" -> Optional.of(DEFERRED);
			case "composite" -> Optional.of(COMPOSITE);
			default -> Optional.empty();
		};
	}

	
	public static Optional<TextureStage> of(String program) {
		String family = ProgramNames.familyOf(program);
		if (ProgramNames.geometry(family)) {
			return Optional.of(GBUFFERS);
		}

		return switch (family) {
			case "setup" -> Optional.of(SETUP);
			case "begin" -> Optional.of(BEGIN);
			case "shadowcomp" -> Optional.of(SHADOWCOMP);
			case "prepare" -> Optional.of(PREPARE);
			case "deferred" -> Optional.of(DEFERRED);
			case "composite", "final" -> Optional.of(COMPOSITE);
			default -> Optional.empty();
		};
	}
}
