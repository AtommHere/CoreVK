package net.corevk.corevk.render;

import net.corevk.corevk.mixin.access.TextureManagerAccessor;

import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;

import java.util.Map;

final class GameImages {

	private GameImages() {
	}

	static String name(GpuTextureView view) {
		if (view == null) {
			return "one white pixel, this pass having no image of its own";
		}

		String size = view.getWidth(0) + "x" + view.getHeight(0);
		Minecraft minecraft = Minecraft.getInstance();
		TextureManager manager = minecraft == null ? null : minecraft.getTextureManager();
		if (manager == null) {
			return "an unnamed " + size + " image";
		}

		Map<Identifier, AbstractTexture> loaded = ((TextureManagerAccessor) manager).corevk$byPath();
		for (Map.Entry<Identifier, AbstractTexture> entry : loaded.entrySet()) {
			if (view.equals(viewOf(entry.getValue()))) {
				return entry.getKey() + ", " + size;
			}
		}

		return "an image the game does not hold, " + size;
	}

	static GpuTextureView held(Identifier location) {
		Minecraft minecraft = Minecraft.getInstance();
		TextureManager manager = minecraft == null ? null : minecraft.getTextureManager();
		if (manager == null || location == null) {
			return null;
		}

		Map<Identifier, AbstractTexture> loaded = ((TextureManagerAccessor) manager).corevk$byPath();
		AbstractTexture texture = loaded.get(location);
		GpuTextureView view = texture == null ? null : viewOf(texture);
		if (view != null) {
			return view;
		}

		AbstractTexture missing = loaded.get(MissingTextureAtlasSprite.getLocation());

		return missing == null ? null : viewOf(missing);
	}

	private static GpuTextureView viewOf(AbstractTexture texture) {
		try {
			return texture.getTextureView();
		} catch (IllegalStateException absent) {
			return null;
		}
	}
}
