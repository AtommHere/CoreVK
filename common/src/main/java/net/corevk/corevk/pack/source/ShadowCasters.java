package net.corevk.corevk.pack.source;

public record ShadowCasters(boolean terrain, boolean translucent, boolean entities, boolean player,
		boolean blockEntities, boolean lightBlockEntities) {

	
	public static final ShadowCasters DEFAULT =
			new ShadowCasters(true, true, true, false, true, false);

	
	public boolean anyFeature() {
		return this.entities || this.player || this.blockEntities || this.lightBlockEntities;
	}

	
	public boolean anyBlockEntity() {
		return this.blockEntities || this.lightBlockEntities;
	}

	
	public boolean emittersOnly() {
		return !this.blockEntities && this.lightBlockEntities;
	}
}
