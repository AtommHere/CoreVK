package net.corevk.corevk.render.pbr;

import net.corevk.corevk.CoreVK;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.ARGB;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class PbrAtlas implements AutoCloseable {

	private static final int USAGE = GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING
			| GpuTexture.USAGE_RENDER_ATTACHMENT;

	private final GpuTexture base;
	private final Map<PbrMap, GpuTextureView> views = new EnumMap<>(PbrMap.class);
	private final Map<PbrMap, GpuTexture> textures = new EnumMap<>(PbrMap.class);

	private PbrAtlas(GpuTexture base) {
		this.base = base;
	}

	static PbrAtlas read(Identifier atlas, GpuTexture base,
			Collection<TextureAtlasSprite> sprites, ResourceManager resources, boolean labPbr) {
		PbrAtlas built = new PbrAtlas(base);

		try {
			for (PbrMap map : PbrMap.values()) {
				built.fill(atlas, map, sprites, resources, labPbr);
			}
		} catch (RuntimeException e) {
			built.close();

			throw e;
		}

		if (built.views.isEmpty()) {
			return null;
		}

		return built;
	}

	GpuTextureView view(PbrMap map) {
		return this.views.get(map);
	}

	@SuppressWarnings("ReferenceEquality")
	boolean follows(GpuTextureView atlas) {
		return atlas != null && atlas.texture() == this.base;
	}

	@Override
	public void close() {

		this.views.values().forEach(GpuTextureView::close);
		this.views.clear();
		this.textures.values().forEach(GpuTexture::close);
		this.textures.clear();
	}

	private void fill(Identifier atlas, PbrMap map, Collection<TextureAtlasSprite> sprites,
			ResourceManager resources, boolean labPbr) {
		int width = this.base.getWidth(0);
		int height = this.base.getHeight(0);
		int levels = this.base.getMipLevels();

		List<Sprite> found = new ArrayList<>();
		try {
			for (TextureAtlasSprite sprite : sprites) {
				Sprite read = read(sprite, map, resources, labPbr, levels, width);
				if (read != null) {
					found.add(read);
				}
			}

			if (found.isEmpty()) {
				return;
			}

			GpuDevice device = RenderSystem.getDevice();
			GpuTexture texture = device.createTexture(() -> atlas.toString() + map.suffix(), USAGE,
					GpuFormat.RGBA8_UNORM, width, height, 1, levels);
			this.textures.put(map, texture);
			this.views.put(map, device.createTextureView(texture));

			CommandEncoder encoder = device.createCommandEncoder();

			encoder.clearColorTexture(texture, map.missing());

			for (Sprite sprite : found) {
				for (int level = 0; level < levels; level++) {
					encoder.writeToTexture(texture, sprite.levels()[level], level, 0,
							sprite.x() >> level, sprite.y() >> level);
				}
			}

			CoreVK.logger().info("The resource pack answers {} for {} of the {} sprites of {}",
					map.sampler(), found.size(), sprites.size(), atlas);
		} finally {

			found.forEach(Sprite::close);
		}
	}

	private static Sprite read(TextureAtlasSprite sprite, PbrMap map, ResourceManager resources,
			boolean labPbr, int levels, int width) {
		Identifier location = location(sprite.contents().name(), map);
		Optional<Resource> resource = resources.getResource(location);
		if (resource.isEmpty()) {
			return null;
		}

		NativeImage image = null;
		FrameSize frame;
		try {
			try (InputStream stream = resource.get().open()) {
				image = NativeImage.read(stream);
			}

			Optional<AnimationMetadataSection> animation = resource.get().metadata()
					.getSection(AnimationMetadataSection.TYPE);
			NativeImage read = image;
			frame = animation
					.map(section -> section.calculateFrameSize(read.getWidth(), read.getHeight()))
					.orElseGet(() -> new FrameSize(read.getWidth(), read.getHeight()));
		} catch (IOException | RuntimeException e) {

			if (image != null) {
				image.close();
			}

			CoreVK.logger().warn("{} could not be read, so its sprite keeps the flat {} value",
					location, map.sampler(), e);

			return null;
		}

		if (frame.width() > image.getWidth() || frame.height() > image.getHeight()
				|| frame.width() <= 0 || frame.height() <= 0) {
			CoreVK.logger().warn("{} declares a frame of {}x{} and is {}x{}, so its sprite keeps "
					+ "the flat {} value", location, frame.width(), frame.height(), image.getWidth(),
					image.getHeight(), map.sampler());
			image.close();

			return null;
		}

		int target = sprite.contents().width();
		int targetHeight = sprite.contents().height();

		NativeImage first = crop(image, frame.width(), frame.height());
		image.close();

		NativeImage sized = scale(first, target, targetHeight);
		if (sized != first) {
			first.close();
		}

		int padding = Math.max(0, Math.round(sprite.getU0() * width) - sprite.getX());

		NativeImage[] chain = new NativeImage[levels];
		NativeImage level = sized;
		for (int index = 0; index < levels; index++) {
			if (index > 0) {
				NativeImage smaller = reduce(level, map, labPbr);
				if (level != sized) {
					level.close();
				}

				level = smaller;
			}

			chain[index] = pad(level, padding >> index);
		}

		if (level != sized) {
			level.close();
		}

		sized.close();

		return new Sprite(sprite.getX(), sprite.getY(), chain);
	}

	private static Identifier location(Identifier sprite, PbrMap map) {
		String path = sprite.getPath() + map.suffix() + ".png";

		if (path.startsWith("optifine/cit/")) {
			return Identifier.fromNamespaceAndPath(sprite.getNamespace(), path);
		}

		return Identifier.fromNamespaceAndPath(sprite.getNamespace(), "textures/" + path);
	}

	private static NativeImage crop(NativeImage image, int width, int height) {
		if (image.getWidth() == width && image.getHeight() == height) {
			return image.mappedCopy(pixel -> pixel);
		}

		NativeImage frame = new NativeImage(width, height, false);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				frame.setPixel(x, y, image.getPixel(x, y));
			}
		}

		return frame;
	}

	private static NativeImage scale(NativeImage image, int width, int height) {
		if (image.getWidth() == width && image.getHeight() == height) {
			return image;
		}

		if (width % image.getWidth() == 0 && height % image.getHeight() == 0) {
			NativeImage scaled = new NativeImage(width, height, false);
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					scaled.setPixel(x, y, image.getPixel(x * image.getWidth() / width,
							y * image.getHeight() / height));
				}
			}

			return scaled;
		}

		NativeImage scaled = new NativeImage(width, height, false);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				scaled.setPixel(x, y, sample(image, (x + 0.5F) * image.getWidth() / width - 0.5F,
						(y + 0.5F) * image.getHeight() / height - 0.5F));
			}
		}

		return scaled;
	}

	private static int sample(NativeImage image, float x, float y) {
		int left = Math.clamp((int) Math.floor(x), 0, image.getWidth() - 1);
		int top = Math.clamp((int) Math.floor(y), 0, image.getHeight() - 1);
		int right = Math.clamp(left + 1, 0, image.getWidth() - 1);
		int bottom = Math.clamp(top + 1, 0, image.getHeight() - 1);

		float acrossRight = Math.clamp(x - left, 0.0F, 1.0F);
		float downBottom = Math.clamp(y - top, 0.0F, 1.0F);
		float acrossLeft = 1.0F - acrossRight;
		float downTop = 1.0F - downBottom;

		int topLeft = image.getPixel(left, top);
		int topRight = image.getPixel(right, top);
		int bottomLeft = image.getPixel(left, bottom);
		int bottomRight = image.getPixel(right, bottom);

		float weightTopLeft = acrossLeft * downTop;
		float weightTopRight = acrossRight * downTop;
		float weightBottomLeft = acrossLeft * downBottom;
		float weightBottomRight = acrossRight * downBottom;

		return ARGB.color(
				channel(ARGB.alpha(topLeft), ARGB.alpha(topRight), ARGB.alpha(bottomLeft),
						ARGB.alpha(bottomRight), weightTopLeft, weightTopRight, weightBottomLeft,
						weightBottomRight),
				channel(ARGB.red(topLeft), ARGB.red(topRight), ARGB.red(bottomLeft),
						ARGB.red(bottomRight), weightTopLeft, weightTopRight, weightBottomLeft,
						weightBottomRight),
				channel(ARGB.green(topLeft), ARGB.green(topRight), ARGB.green(bottomLeft),
						ARGB.green(bottomRight), weightTopLeft, weightTopRight, weightBottomLeft,
						weightBottomRight),
				channel(ARGB.blue(topLeft), ARGB.blue(topRight), ARGB.blue(bottomLeft),
						ARGB.blue(bottomRight), weightTopLeft, weightTopRight, weightBottomLeft,
						weightBottomRight));
	}

	private static int channel(int topLeft, int topRight, int bottomLeft, int bottomRight,
			float weightTopLeft, float weightTopRight, float weightBottomLeft,
			float weightBottomRight) {
		return Math.round(topLeft * weightTopLeft + topRight * weightTopRight
				+ bottomLeft * weightBottomLeft + bottomRight * weightBottomRight);
	}

	private static NativeImage reduce(NativeImage image, PbrMap map, boolean labPbr) {
		NativeImage smaller = new NativeImage(image.getWidth() >> 1, image.getHeight() >> 1, false);
		for (int y = 0; y < smaller.getHeight(); y++) {
			for (int x = 0; x < smaller.getWidth(); x++) {
				smaller.setPixel(x, y, map.blend(
						image.getPixel(x * 2, y * 2),
						image.getPixel(x * 2 + 1, y * 2),
						image.getPixel(x * 2, y * 2 + 1),
						image.getPixel(x * 2 + 1, y * 2 + 1), labPbr));
			}
		}

		return smaller;
	}

	private static NativeImage pad(NativeImage image, int padding) {
		if (padding <= 0) {
			return image.mappedCopy(pixel -> pixel);
		}

		int width = image.getWidth() + padding * 2;
		int height = image.getHeight() + padding * 2;
		NativeImage padded = new NativeImage(width, height, false);
		for (int y = 0; y < height; y++) {
			int source = Math.clamp(y - padding, 0, image.getHeight() - 1);
			for (int x = 0; x < width; x++) {
				padded.setPixel(x, y,
						image.getPixel(Math.clamp(x - padding, 0, image.getWidth() - 1), source));
			}
		}

		return padded;
	}

	private record Sprite(int x, int y, NativeImage[] levels) {

		void close() {
			for (NativeImage level : this.levels) {
				level.close();
			}
		}
	}
}
