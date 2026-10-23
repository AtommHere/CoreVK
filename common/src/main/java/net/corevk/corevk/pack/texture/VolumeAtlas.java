package net.corevk.corevk.pack.texture;

import net.corevk.corevk.pack.model.PackTexture;
import net.corevk.corevk.pack.model.PixelType;

public final class VolumeAtlas {

	
	public static final int GUTTER = 1;

	private final int width;
	private final int height;
	private final int depth;
	private final int tilesPerRow;
	private final int rows;
	private final PixelType type;
	private final int components;
	private final boolean clamp;

	private VolumeAtlas(int width, int height, int depth, PixelType type, int components,
			boolean clamp) {
		this.width = width;
		this.height = height;
		this.depth = depth;
		this.tilesPerRow = (int) Math.ceil(Math.sqrt(depth));
		this.rows = (depth + this.tilesPerRow - 1) / this.tilesPerRow;
		this.type = type;
		this.components = components;
		this.clamp = clamp;
	}

	
	public static VolumeAtlas of(PackTexture.Raw raw, boolean clamp) {
		if (!serves(raw)) {
			throw new IllegalArgumentException("A volume of " + raw.sizeX() + "x" + raw.sizeY() + "x"
					+ raw.sizeZ() + " in " + raw.pixelFormat() + " " + raw.pixelType()
					+ " is not one this lays out flat");
		}

		return new VolumeAtlas(raw.sizeX(), raw.sizeY(), raw.sizeZ(), raw.pixelType(),
				raw.pixelFormat().components(), clamp);
	}

	
	public static boolean serves(PackTexture.Raw raw) {
		return raw.shape() == PackTexture.Shape.TEXTURE_3D
				&& raw.sizeX() > 0 && raw.sizeY() > 0 && raw.sizeZ() > 0
				&& RawTexels.holds(raw.pixelType(), raw.pixelFormat());
	}

	
	public boolean fits() {
		return RawTexels.fits(atlasWidth(), atlasHeight(), texelBytes());
	}

	
	public int index(int x, int y, int z) {
		return x + y * this.width + z * this.width * this.height;
	}

	
	public int texel(int x, int y, int z) {
		int column = (z % this.tilesPerRow) * tileStride() + GUTTER + x;
		int row = (z / this.tilesPerRow) * tileHeight() + GUTTER + y;

		return row * atlasWidth() + column;
	}

	
	public byte[] spread(byte[] blob) {
		int in = this.components * channelBytes();
		long texels = (long) this.width * this.height * this.depth;
		if (blob.length < texels * in) {
			throw new IllegalArgumentException("A volume of " + this.width + "x" + this.height + "x"
					+ this.depth + " needs " + texels * in + " bytes and this blob holds "
					+ blob.length);
		}

		int out = texelBytes();
		byte[] one = RawTexels.one(this.type);
		byte[] atlas = new byte[atlasWidth() * atlasHeight() * out];
		for (int z = 0; z < this.depth; z++) {
			// From -1 to width inclusive: the two extra columns and rows are the gutter, and they
			// are filled by the same walk rather than patched on afterwards.
			for (int v = -GUTTER; v < this.height + GUTTER; v++) {
				for (int u = -GUTTER; u < this.width + GUTTER; u++) {
					int x = past(u, this.width);
					int y = past(v, this.height);
					int to = texel(u, v, z) * out;
					System.arraycopy(blob, index(x, y, z) * in, atlas, to, in);
					if (this.components <= RawTexels.ALPHA) {
						System.arraycopy(one, 0, atlas, to + RawTexels.ALPHA * channelBytes(), one.length);
					}
				}
			}
		}

		return atlas;
	}

	
	private int past(int at, int size) {
		return this.clamp ? Math.clamp(at, 0, size - 1) : Math.floorMod(at, size);
	}

	
	public int tileStride() {
		return this.width + 2 * GUTTER;
	}

	
	public int tileHeight() {
		return this.height + 2 * GUTTER;
	}

	public int tilesPerRow() {
		return this.tilesPerRow;
	}

	public int atlasWidth() {
		return this.tilesPerRow * tileStride();
	}

	public int atlasHeight() {
		return this.rows * tileHeight();
	}

	public int width() {
		return this.width;
	}

	public int height() {
		return this.height;
	}

	public int depth() {
		return this.depth;
	}

	
	public PixelType type() {
		return this.type;
	}

	
	public int components() {
		return this.components;
	}

	
	public boolean clamp() {
		return this.clamp;
	}

	
	public int channelBytes() {
		return this.type.channelBytes();
	}

	
	public int texelBytes() {
		return RawTexels.CHANNELS * channelBytes();
	}
}
