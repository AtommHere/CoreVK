package net.corevk.corevk.pack.source;

public enum ShadowCullState {

	DEFAULT,

	ADVANCED,

	SAFE_ZONE,

	DISTANCE;

	public static ShadowCullState of(String word) {
		return switch (word) {
			case "false" -> DISTANCE;
			case "true" -> ADVANCED;
			case "reversed", "safe_zone" -> SAFE_ZONE;
			default -> null;
		};
	}
}
