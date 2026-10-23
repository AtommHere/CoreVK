package net.corevk.corevk.render;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.util.Mth;

final class TargetSurface implements AutoCloseable {

	
	private static final int USAGE = GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC
			| GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT;

	private final String label;
	private final GpuFormat format;
	private final boolean mipped;

	
	private final boolean storage;

	private GpuTexture texture;
	private GpuTextureView view;

	
	private GpuTextureView baseView;

	private int width;
	private int height;

	
	private boolean chainWritten;

	
	TargetSurface(String label, GpuFormat format, boolean mipped, int width, int height) {
		this(label, format, mipped, false, width, height);
	}

	
	TargetSurface(String label, GpuFormat format, boolean mipped, boolean storage, int width,
			int height) {
		this.label = label;
		this.format = format;
		this.mipped = mipped;
		this.storage = storage;
		allocate(width, height);
	}

	
	static int levelsFor(boolean mipped, int width, int height) {
		return mipped ? Mth.log2(Math.max(width, height)) + 1 : 1;
	}

	int width() {
		return this.width;
	}

	int height() {
		return this.height;
	}

	int levels() {
		return this.texture == null ? 0 : this.texture.getMipLevels();
	}

	GpuTexture texture() {
		return this.texture;
	}

	
	boolean chainWritten() {
		return this.chainWritten;
	}

	
	void chainWritten(boolean written) {
		this.chainWritten = written;
	}

	
	long baseBytes() {
		return (long) this.width * this.height * this.format.blockSize();
	}

	
	long bytes() {
		long texels = 0L;
		for (int level = 0; level < levels(); level++) {
			texels += (long) Math.max(1, this.width >> level) * Math.max(1, this.height >> level);
		}

		return texels * this.format.blockSize();
	}

	
	GpuTextureView view() {
		return this.view;
	}

	
	GpuTextureView storageView() {
		return this.baseView == null ? this.view : this.baseView;
	}

	
	boolean storage() {
		return this.storage;
	}

	
	boolean resize(int width, int height) {
		if (this.texture != null && this.width == width && this.height == height) {
			return false;
		}

		close();
		allocate(width, height);

		return true;
	}

	private void allocate(int width, int height) {
		GpuDevice device = RenderSystem.getDevice();
		int levels = levelsFor(this.mipped, width, height);

		this.width = width;
		this.height = height;
		// A new image, so its levels are whatever the driver left there until the reduction says
		// otherwise. Set before anything can read it rather than after, because a resize is exactly
		// the moment a chain silently stops being true.
		this.chainWritten = false;

		// Everything or nothing. A view that throws after the texture was made would leave that
		// texture with no owner: the caller only learns of a surface once the constructor returns,
		// so a half built one is never put in a map, never closed, and not even a resource reload
		// gets it back. The chain makes this real rather than theoretical, since one surface now
		// creates up to a dozen views instead of one.
		try {
			// The flag is read by the mixin on the game's usage conversion, inside this one call,
			// and lowered whatever the call did: a throw that left it up would mark the next
			// texture anybody creates.
			TextureUsage.requestStorage(this.storage);
			try {
				this.texture = device.createTexture(this.label, USAGE, this.format, width, height, 1,
						levels);
			} finally {
				TextureUsage.requestStorage(false);
			}

			this.view = device.createTextureView(this.texture);

			if (levels > 1) {
				this.baseView = device.createTextureView(this.texture, 0, 1);
			}
		} catch (RuntimeException e) {
			close();
			throw e;
		}
	}

	
	@Override
	public void close() {
		if (this.baseView != null) {
			this.baseView.close();
			this.baseView = null;
		}

		if (this.view != null) {
			this.view.close();
			this.view = null;
		}

		if (this.texture != null) {
			this.texture.close();
			this.texture = null;
		}
	}
}
