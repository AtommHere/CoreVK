package net.corevk.corevk.glsl;

import java.util.List;

public final class GameTransforms {

	public static final List<String> BLOCK = List.of(
			"layout(std140) uniform " + LegacyGlsl.GAME_TRANSFORMS + " {",
			"\tmat4 " + LegacyGlsl.GAME_MODEL_VIEW + ";",
			"\tmat4 " + LegacyGlsl.GAME_TEXTURE_MATRIX + ";",
			"\tvec4 of_GameColorModulator;",
			"\tvec3 of_GameModelOffset;",
			"};");

	private GameTransforms() {
	}
}
