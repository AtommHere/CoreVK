package net.corevk.corevk.pack.program;

import net.corevk.corevk.pack.model.AlphaTest;
import net.corevk.corevk.pack.model.RenderStage;
import net.corevk.corevk.pack.source.ShaderProperties;

import java.util.Map;

public enum TerrainPass {

	SOLID("gbuffers_terrain_solid", AlphaTest.OFF, false, false),

	CUTOUT("gbuffers_terrain_cutout", AlphaTest.CUTOUT, false, false),

	TRANSLUCENT("gbuffers_water", AlphaTest.NON_ZERO, true, false),

	SHADOW_SOLID("shadow_solid", AlphaTest.OFF, false, true),

	SHADOW_CUTOUT("shadow_cutout", AlphaTest.CUTOUT, false, true),

	SHADOW_TRANSLUCENT("shadow_water", AlphaTest.OFF, false, true);

	private final String program;

	@SuppressWarnings("ImmutableEnumChecker")
	private final AlphaTest fallback;
	private final boolean blended;
	private final boolean shadow;

	TerrainPass(String program, AlphaTest fallback, boolean blended, boolean shadow) {
		this.program = program;
		this.fallback = fallback;
		this.blended = blended;
		this.shadow = shadow;
	}

	public String program() {
		return this.program;
	}

	public boolean blended() {
		return this.blended;
	}

	public boolean shadow() {
		return this.shadow;
	}

	public boolean covers() {
		return !this.blended && !this.shadow;
	}

	public TerrainPass inShadow() {
		return switch (this) {
			case SOLID -> SHADOW_SOLID;
			case CUTOUT -> SHADOW_CUTOUT;
			case TRANSLUCENT -> SHADOW_TRANSLUCENT;
			default -> null;
		};
	}

	public boolean afterDeferred() {
		return this == TRANSLUCENT;
	}

	public RenderStage stage() {
		return switch (this) {
			case SOLID, SHADOW_SOLID -> RenderStage.TERRAIN_SOLID;
			case CUTOUT, SHADOW_CUTOUT -> RenderStage.TERRAIN_CUTOUT;
			case TRANSLUCENT, SHADOW_TRANSLUCENT -> RenderStage.TERRAIN_TRANSLUCENT;
		};
	}

	public AlphaTest alphaTest(Map<String, AlphaTest> overrides, String servedBy) {
		return overrides.getOrDefault(servedBy, this.fallback);
	}
}
