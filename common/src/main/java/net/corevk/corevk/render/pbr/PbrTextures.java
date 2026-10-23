package net.corevk.corevk.render.pbr;

import net.corevk.corevk.mixin.access.TextureManagerAccessor;
import net.corevk.corevk.CoreVK;

import com.mojang.blaze3d.GpuDeviceLossException;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.stream.Collectors;

public final class PbrTextures {

	private static final int USAGE = GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING;

	private static final Map<Identifier, Maps> BY_NAME = new HashMap<>();

	private static final Map<GpuTexture, Maps> BY_TEXTURE = new WeakHashMap<>();

	private static final Set<GpuTexture> WANTED = Collections.newSetFromMap(new IdentityHashMap<>());

	private static final Maps NOTHING = new Maps();

	private PbrTextures() {
	}

	public static GpuTextureView view(GpuTextureView bound, PbrMap map) {
		if (bound == null) {
			return null;
		}

		Maps built = BY_TEXTURE.get(bound.texture());
		if (built == null) {
			WANTED.add(bound.texture());

			return null;
		}

		return built.views.get(map);
	}

	public static void load() {
		if (WANTED.isEmpty()) {
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null) {
			WANTED.clear();

			return;
		}

		Map<Identifier, AbstractTexture> loaded =
				((TextureManagerAccessor) minecraft.getTextureManager()).corevk$byPath();
		ResourceManager resources = minecraft.getResourceManager();

		for (GpuTexture wanted : WANTED) {
			BY_TEXTURE.put(wanted, read(wanted, loaded, resources));
		}

		WANTED.clear();
	}

	public static void close() {
		BY_NAME.values().forEach(Maps::close);
		BY_NAME.clear();
		BY_TEXTURE.clear();
		WANTED.clear();
	}

	private static Maps read(GpuTexture wanted, Map<Identifier, AbstractTexture> loaded,
			ResourceManager resources) {
		Identifier name = named(wanted, loaded);
		if (name == null) {
			return NOTHING;
		}

		Maps replaced = BY_NAME.remove(name);
		if (replaced != null) {
			replaced.close();
		}

		Maps built = new Maps();
		try {
			for (PbrMap map : PbrMap.values()) {
				read(built, map, beside(name, map), resources);
			}
		} catch (GpuDeviceLossException e) {
			throw e;
		} catch (RuntimeException e) {

			built.close();
			CoreVK.logger().error("The material maps of {} could not be built, so it keeps the flat "
					+ "values", name, e);

			return NOTHING;
		}

		if (built.views.isEmpty()) {
			return NOTHING;
		}

		BY_NAME.put(name, built);
		CoreVK.logger().info("The resource pack answers {} for {}", built.answered(), name);

		return built;
	}

	private static Identifier named(GpuTexture wanted, Map<Identifier, AbstractTexture> loaded) {
		for (AbstractTexture texture : loaded.values()) {
			if (texture instanceof SimpleTexture simple && holds(simple, wanted)) {
				return simple.resourceId();
			}
		}

		return null;
	}

	@SuppressWarnings("ReferenceEquality")
	private static boolean holds(AbstractTexture texture, GpuTexture wanted) {
		try {
			return texture.getTexture() == wanted;
		} catch (IllegalStateException absent) {
			return false;
		}
	}

	private static Identifier beside(Identifier texture, PbrMap map) {
		String path = texture.getPath();
		int dot = path.lastIndexOf('.');
		String suffixed = dot > path.lastIndexOf('/')
				? path.substring(0, dot) + map.suffix() + path.substring(dot)
				: path + map.suffix();

		return Identifier.fromNamespaceAndPath(texture.getNamespace(), suffixed);
	}

	private static void read(Maps into, PbrMap map, Identifier location, ResourceManager resources) {
		Optional<Resource> resource = resources.getResource(location);
		if (resource.isEmpty()) {
			return;
		}

		NativeImage image;
		try (InputStream stream = resource.get().open()) {
			image = NativeImage.read(stream);
		} catch (IOException | RuntimeException e) {
			CoreVK.logger().warn("{} could not be read, so its texture keeps the flat {} value",
					location, map.sampler(), e);

			return;
		}

		try {
			GpuDevice device = RenderSystem.getDevice();
			GpuTexture texture = device.createTexture(location::toString, USAGE,
					GpuFormat.RGBA8_UNORM, image.getWidth(), image.getHeight(), 1, 1);

			into.textures.put(map, texture);
			into.views.put(map, device.createTextureView(texture));
			device.createCommandEncoder().writeToTexture(texture, image);
		} finally {
			image.close();
		}
	}

	private static final class Maps {

		private final Map<PbrMap, GpuTexture> textures = new EnumMap<>(PbrMap.class);
		private final Map<PbrMap, GpuTextureView> views = new EnumMap<>(PbrMap.class);

		String answered() {
			return this.views.keySet().stream().map(PbrMap::sampler)
					.collect(Collectors.joining(" and "));
		}

		void close() {

			this.views.values().forEach(GpuTextureView::close);
			this.views.clear();
			this.textures.values().forEach(GpuTexture::close);
			this.textures.clear();
		}
	}
}
