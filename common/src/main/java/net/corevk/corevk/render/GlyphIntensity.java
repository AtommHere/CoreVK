package net.corevk.corevk.render;

import net.corevk.corevk.CoreVK;

import com.mojang.blaze3d.GpuDeviceLossException;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

public final class GlyphIntensity {

	private static final Map<GpuTexture, GpuTextureView> VIEWS = new IdentityHashMap<>();

	private static final ThreadLocal<Boolean> SWIZZLING = ThreadLocal.withInitial(() -> false);

	private GlyphIntensity() {
	}

	static GpuTextureView view(GpuTextureView sheet) {
		release();

		GpuTexture texture = sheet.texture();
		if (texture.isClosed() || texture.getFormat() != GpuFormat.R8_UNORM) {
			return sheet;
		}

		GpuTextureView held = VIEWS.get(texture);
		if (held != null) {
			return held;
		}

		GpuDevice device = RenderSystem.tryGetDevice();
		if (device == null) {
			return sheet;
		}

		SWIZZLING.set(true);
		GpuTextureView made;
		try {
			made = device.createTextureView(texture, sheet.baseMipLevel(), sheet.mipLevels());
		} catch (GpuDeviceLossException e) {
			throw e;
		} catch (RuntimeException e) {
			CoreVK.logger().error("Could not build a swizzled view of the font sheet "
					+ texture.getLabel() + ", so the pack reads it with one channel and draws its "
					+ "text red", e);

			return sheet;
		} finally {
			SWIZZLING.set(false);
		}

		VIEWS.put(texture, made);

		return made;
	}

	public static boolean swizzling() {
		return SWIZZLING.get();
	}

	private static void release() {
		Iterator<Map.Entry<GpuTexture, GpuTextureView>> held = VIEWS.entrySet().iterator();
		while (held.hasNext()) {
			Map.Entry<GpuTexture, GpuTextureView> entry = held.next();
			if (!entry.getKey().isClosed()) {
				continue;
			}

			entry.getValue().close();
			held.remove();
		}
	}
}
