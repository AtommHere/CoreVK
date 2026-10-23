package net.corevk.corevk.render.pbr;

import net.corevk.corevk.pack.option.EngineDefines;
import net.corevk.corevk.render.PackDefines;
import net.corevk.corevk.CoreVK;

import com.mojang.blaze3d.GpuDeviceLossException;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

public final class PbrAtlases {

	private static final Identifier FORMAT = Identifier.withDefaultNamespace(
			"optifine/texture.properties");

	private static final Map<Identifier, PbrAtlas> ATLASES = new HashMap<>();

	private static final String LAB_PBR = "lab-pbr";

	private static EngineDefines.TextureFormat format;

	private PbrAtlases() {
	}

	public static void stitched(Identifier atlas, GpuTexture texture,
			Collection<TextureAtlasSprite> sprites) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null) {
			return;
		}

		ResourceManager resources = minecraft.getResourceManager();

		EngineDefines.TextureFormat declared = format(resources);
		if (!Objects.equals(declared, format)) {
			format = declared;
		}

		PbrAtlas previous = ATLASES.remove(atlas);
		if (previous != null) {
			previous.close();
		}

		try {
			PbrAtlas read = PbrAtlas.read(atlas, texture, sprites, resources, labPbr());
			if (read != null) {
				ATLASES.put(atlas, read);
			}
		} catch (GpuDeviceLossException e) {
			throw e;
		} catch (RuntimeException e) {

			CoreVK.logger().error("The material maps of {} could not be built, so its sprites keep "
					+ "the flat values", atlas, e);
		}
	}

	public static GpuTextureView view(GpuTextureView atlas, PbrMap map) {
		if (atlas == null) {
			return null;
		}

		for (PbrAtlas built : ATLASES.values()) {
			if (built.follows(atlas)) {
				return built.view(map);
			}
		}

		return null;
	}

	public static boolean supplies(PbrMap map) {
		for (PbrAtlas built : ATLASES.values()) {
			if (built.view(map) != null) {
				return true;
			}
		}

		return false;
	}

	public static boolean labPbr() {
		return format != null;
	}

	public static EngineDefines.TextureFormat format() {
		return format;
	}

	public static void close() {
		ATLASES.values().forEach(PbrAtlas::close);
		ATLASES.clear();
	}

	static EngineDefines.TextureFormat format(ResourceManager resources) {
		Optional<Resource> resource = resources.getResource(FORMAT);
		if (resource.isEmpty()) {
			return null;
		}

		Properties properties = new Properties();
		try (InputStream stream = resource.get().open()) {
			properties.load(stream);
		} catch (Exception e) {
			CoreVK.logger().warn("{} could not be read, so the material maps are reduced and "
					+ "filtered as if it named no format", FORMAT, e);

			return null;
		}

		String declared = properties.getProperty("format", "").trim();
		int slash = declared.indexOf('/');
		if (!LAB_PBR.equals(slash < 0 ? declared : declared.substring(0, slash))) {
			return null;
		}

		return new EngineDefines.TextureFormat(LAB_PBR, version(declared, slash));
	}

	private static String version(String declared, int slash) {
		if (slash < 0) {
			return null;
		}

		int next = declared.indexOf('/', slash + 1);
		String version = declared.substring(slash + 1, next < 0 ? declared.length() : next);

		return version.isEmpty() ? null : version;
	}
}
