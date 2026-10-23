package net.corevk.corevk.pack.texture;

import net.corevk.corevk.pack.model.PackTexture;
import net.corevk.corevk.pack.model.PixelType;

public final class RawImage {

	private final int width;
	private final int height;
	private final PixelType type;
	private final int components;

	private RawImage(int width, int height, PixelType type, int components) {
		this.width = width;
		this.height = height;
		this.type = type;
		this.components = components;
	}

	public static RawImage of(PackTexture.Raw raw) {
		if (!serves(raw)) {
			throw new IllegalArgumentException("A texture of " + raw.sizeX() + "x" + raw.sizeY()
					+ " in " + raw.pixelFormat() + " " + raw.pixelType()
					+ " is not one this uploads flat");
		}

		return new RawImage(raw.sizeX(), raw.sizeY(), raw.pixelType(),
				raw.pixelFormat().components());
	}

	public static boolean serves(PackTexture.Raw raw) {
		return raw.shape() == PackTexture.Shape.TEXTURE_2D
				&& raw.sizeX() > 0 && raw.sizeY() > 0
				&& RawTexels.holds(raw.pixelType(), raw.pixelFormat());
	}

	public boolean fits() {
		return RawTexels.fits(this.width, this.height, texelBytes());
	}

	public byte[] widen(byte[] blob) {
		int in = this.components * channelBytes();
		int texels = this.width * this.height;
		if (blob.length < (long) texels * in) {
			throw new IllegalArgumentException("A texture of " + this.width + "x" + this.height
					+ " needs " + (long) texels * in + " bytes and this blob holds " + blob.length);
		}

		int out = texelBytes();
		byte[] one = RawTexels.one(this.type);
		byte[] image = new byte[texels * out];
		for (int texel = 0; texel < texels; texel++) {
			int to = texel * out;
			System.arraycopy(blob, texel * in, image, to, in);
			if (this.components <= RawTexels.ALPHA) {
				System.arraycopy(one, 0, image, to + RawTexels.ALPHA * channelBytes(), one.length);
			}
		}

		return image;
	}

	public int width() {
		return this.width;
	}

	public int height() {
		return this.height;
	}

	public PixelType type() {
		return this.type;
	}

	public int components() {
		return this.components;
	}

	public int channelBytes() {
		return this.type.channelBytes();
	}

	public int texelBytes() {
		return RawTexels.CHANNELS * channelBytes();
	}
}
