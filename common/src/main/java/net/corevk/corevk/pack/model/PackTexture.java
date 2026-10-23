package net.corevk.corevk.pack.model;

import java.util.Locale;
import java.util.Optional;

public record PackTexture(String sampler, Optional<TextureStage> stage, String path,
		Optional<Raw> raw, boolean blur, boolean clamp) {

	public enum Shape { TEXTURE_1D, TEXTURE_2D, TEXTURE_3D, TEXTURE_RECTANGLE }

	public record Raw(Shape shape, TargetFormat.Resolution internalFormat, int sizeX, int sizeY,
			int sizeZ, PixelFormat pixelFormat, PixelType pixelType) {

		public long bytes() {
			return (long) this.sizeX * Math.max(this.sizeY, 1) * Math.max(this.sizeZ, 1)
					* this.pixelType.bytesPerTexel(this.pixelFormat);
		}
	}

	public boolean png() {
		return this.raw.isEmpty();
	}

	public boolean gameResource() {
		return gameResource(this.path);
	}

	public static boolean gameResource(String path) {
		return path.indexOf(':') >= 0;
	}

	public String describe() {
		return this.stage.map(stage -> stage.name().toLowerCase(Locale.ROOT) + " " + this.sampler
						+ " overridden by ")
				.orElse(this.sampler + " supplied by ") + this.path
				+ this.raw.map(raw -> " (" + raw.shape() + " " + raw.internalFormat().declared() + " "
						+ raw.sizeX() + "x" + raw.sizeY() + "x" + raw.sizeZ() + ")").orElse("");
	}
}
