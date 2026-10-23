package net.corevk.corevk.glsl;

import java.util.List;

public enum VertexInputs {

	
	FULLSCREEN,

	
	WORLD,

	
	TERRAIN,

	
	TERRAIN_SEPARATE_AO,

	
	ENTITY,

	
	ENTITY_FULLBRIGHT,

	
	PARTICLE,

	
	GLINT,

	
	CRUMBLING,

	
	MOVING_BLOCK,

	
	LINES,

	
	GLYPH,

	
	SKY,

	
	DISTANT,

	
	CLOUDS;

	
	public boolean synthesizes() {
		return this != WORLD;
	}

	
	public boolean terrain() {
		return this == TERRAIN || this == TERRAIN_SEPARATE_AO;
	}

	
	public boolean separateAo() {
		return this == TERRAIN_SEPARATE_AO;
	}

	
	public boolean fullbright() {
		return this == ENTITY_FULLBRIGHT || this == CRUMBLING;
	}

	
	public boolean overlay() {
		return this == ENTITY || this == ENTITY_FULLBRIGHT;
	}

	
	public List<String> elements() {
		return switch (this) {
			case FULLSCREEN -> LegacyGlsl.FULLSCREEN_ELEMENTS;
			case TERRAIN, TERRAIN_SEPARATE_AO -> SodiumVertex.ATTRIBUTES;
			case ENTITY, ENTITY_FULLBRIGHT -> EntityVertex.ATTRIBUTES;
			case GLINT -> GlintVertex.ATTRIBUTES;
			case CRUMBLING -> CrumblingVertex.ATTRIBUTES;
			case MOVING_BLOCK -> MovingBlockVertex.ATTRIBUTES;
			case LINES -> LinesVertex.ATTRIBUTES;
			case GLYPH -> GlyphVertex.ATTRIBUTES;
			case PARTICLE -> ParticleVertex.ATTRIBUTES;
			case SKY -> SkyVertex.ATTRIBUTES;
			case CLOUDS -> CloudVertex.ATTRIBUTES;
			case DISTANT -> DistantVertex.ATTRIBUTES;
			case WORLD -> List.of();
		};
	}
}
