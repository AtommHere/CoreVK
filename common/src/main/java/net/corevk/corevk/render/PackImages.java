package net.corevk.corevk.render;

import net.corevk.corevk.pack.model.PackTexture;
import net.corevk.corevk.pack.model.PixelType;
import net.corevk.corevk.pack.model.TextureStage;
import net.corevk.corevk.pack.source.ShaderPackSource;
import net.corevk.corevk.pack.source.ShaderProperties;
import net.corevk.corevk.pack.target.SamplerPlan;
import net.corevk.corevk.pack.texture.PackTextures;
import net.corevk.corevk.pack.texture.RawImage;
import net.corevk.corevk.pack.texture.VolumeAtlas;
import net.corevk.corevk.render.pbr.PbrAtlases;
import net.corevk.corevk.uniform.NoiseTexture;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

final class PackImages {

	
	private static final long LOUD_BYTES = 32L * 1024L * 1024L;

	
	private static final int MAX_RESOURCE_BYTES = 8 * 1024 * 1024;

	
	@SuppressWarnings("ArrayRecordComponent")
	record Image(PackTexture texture, int width, int height, byte[] pixels, GpuFormat format,
			String shape, boolean live) {

		
		Image(PackTexture texture, int width, int height, byte[] pixels, GpuFormat format,
				String shape) {
			this(texture, width, height, pixels, format, shape, false);
		}

		long bytes() {
			return this.pixels.length;
		}
	}

	private final PackTextures declared;

	
	private final List<Image> images;

	private final Map<PackTexture, Image> byTexture;

	
	private final Map<String, Image> volumes;

	private final List<String> notes;

	private PackImages(PackTextures declared, List<Image> images, Map<PackTexture, Image> byTexture,
			Map<String, Image> volumes, List<String> notes) {
		this.declared = declared;
		this.images = List.copyOf(images);
		this.byTexture = Map.copyOf(byTexture);
		this.volumes = Map.copyOf(volumes);
		this.notes = List.copyOf(notes);
	}

	
	static PackImages none() {
		return new PackImages(PackTextures.empty(), List.of(), Map.of(), Map.of(), List.of());
	}

	
	static PackImages read(ShaderProperties properties, Map<String, String> defines,
			ShaderPackSource source) throws IOException {
		PackTextures declared = PackTextures.read(properties, defines, source);
		Map<String, VolumeAtlas> flat = declared.volumes();
		List<Image> images = new ArrayList<>();
		Map<PackTexture, Image> byTexture = new LinkedHashMap<>();
		Map<String, Image> volumes = new LinkedHashMap<>();
		List<String> notes = new ArrayList<>(declared.notes());

		declared.refused().forEach(refused -> notes.add("Dropped " + refused));

		for (PackTexture texture : declared.supplied()) {
			// The layout is this declaration's own, not the name's: iterationT points colortex8 at a
			// volume for the sampler3D and at a PNG for the sampler2D, exactly as the reference
			// files the two apart, and handing the atlas to the PNG would spread an image over
			// nothing and leave six programs reading a lookup table.
			VolumeAtlas atlas = texture.raw().filter(VolumeAtlas::serves).isPresent()
					? flat.get(texture.sampler())
					: null;

			// The FIRST volume under that name is the one whose layout was printed into every
			// shader, and PackTextures keeps the first too, so a second one is handed that same
			// atlas rather than a second copy of it. A pack writing two files for one name on two
			// stages would otherwise have the last file spread over the first one's tiles, and what
			// comes out of that still looks like noise; writing the SAME file twice, which is what
			// a directive per stage makes a pack do, spread iterationT's atmosphere table twice for
			// one lookup.
			Image already = atlas == null ? null : volumes.get(texture.sampler());
			if (already != null) {
				// The extents count as much as the path: the same file declared at two sizes is one
				// of the two layouts printed into the shaders and one blob spread over the other's
				// tiles, which comes out looking exactly like the noise it is not.
				if (!already.texture().path().equals(texture.path())
						|| !already.texture().raw().equals(texture.raw())) {
					notes.add(texture.path() + " is a second volume under the name "
							+ texture.sampler() + ", and the shaders read " + already.shape()
							+ " of " + already.texture().path());
				}

				byTexture.put(texture, already);
				continue;
			}

			Image image = decode(texture, atlas, source, notes);
			if (image == null) {
				continue;
			}

			images.add(image);
			byTexture.put(texture, image);
			if (atlas != null) {
				volumes.put(texture.sampler(), image);
			}
		}

		return new PackImages(declared, images, byTexture, volumes, notes);
	}

	
	private static Image decode(PackTexture texture, VolumeAtlas atlas, ShaderPackSource source,
			List<String> notes) {
		if (texture.gameResource()) {
			return gameResource(texture, notes);
		}

		Optional<Path> file = source.file(texture.path());
		if (file.isEmpty()) {
			// Looked for once already when the directive was read, so reaching this means the pack
			// moved under us between the two openings.
			notes.add(texture.path() + " is no longer in the pack, so " + texture.sampler()
					+ " reads one black pixel");

			return null;
		}

		try {
			if (atlas != null) {
				// Read to the length the declaration announces and no further, which is what lets a
				// blob past the ceiling a shader source gets be read at all: iterationT's atmosphere
				// table is four times that ceiling.
				PackTexture.Raw raw = texture.raw().orElseThrow();

				return new Image(texture, atlas.atlasWidth(), atlas.atlasHeight(),
						atlas.spread(source.head(file.get(), raw.bytes())), format(atlas.type()),
						raw.sizeX() + "x" + raw.sizeY() + "x" + raw.sizeZ() + " laid out flat as "
								+ atlas.atlasWidth() + "x" + atlas.atlasHeight());
			}

			// A blob that is a plain two dimensional texture goes up as it stands, its channels
			// widened to the four the engine allocates. Read to the declared length for the reason
			// the atlas is: the ceiling above this was written for a shader source.
			PackTexture.Raw plain = texture.raw().filter(RawImage::serves).orElse(null);
			if (plain != null) {
				RawImage image = RawImage.of(plain);

				return new Image(texture, image.width(), image.height(),
						image.widen(source.head(file.get(), plain.bytes())), format(image.type()),
						image.width() + "x" + image.height());
			}

			// Any other blob means what its own format says it means, and nothing here turns one of
			// those into an image: a shape that is neither a volume nor a plain texture, or either
			// of those in a channel type or order nothing lays out. A one dimensional blob and a
			// rectangle are read through samplers this backend does not bind; the corpus ships
			// neither.
			if (!texture.png()) {
				PackTexture.Raw refused = texture.raw().orElseThrow();
				notes.add(texture.path() + " is a raw " + refused.shape() + " of "
						+ refused.pixelFormat() + " " + refused.pixelType()
						+ ", which this engine does not lay out, so "
						+ texture.sampler() + " reads one black pixel");

				return null;
			}

			NoiseTexture.Image decoded = NoiseTexture.decode(source.bytes(file.get()));

			return new Image(texture, decoded.width(), decoded.height(), decoded.rgba(), GpuFormat.RGBA8_UNORM,
					decoded.width() + "x" + decoded.height());
		} catch (NoiseTexture.TooLarge e) {
			notes.add(texture.path() + " is refused, " + e.getMessage() + ", so " + texture.sampler()
					+ " reads one black pixel");

			return null;
		} catch (IOException | RuntimeException e) {
			notes.add(texture.path() + " could not be read: " + e.getMessage() + ", so "
					+ texture.sampler() + " reads one black pixel");

			return null;
		}
	}

	
	private static Image gameResource(PackTexture texture, List<String> notes) {
		String path = texture.path();
		Identifier location = location(path);
		Minecraft client = Minecraft.getInstance();
		if (location == null || client == null) {
			notes.add(path + " is not a resource this client can be asked for, so "
					+ texture.sampler() + " reads one black pixel");

			return null;
		}

		Optional<Resource> resource = client.getResourceManager().getResource(location);
		if (resource.isEmpty()) {
			notes.add(path + " is no file any loaded resource pack ships, so " + texture.sampler()
					+ " reads the texture the game holds under that name as each draw binds it,"
					+ " which is how an atlas is served, and the game's missing texture while it"
					+ " holds none");

			return new Image(texture, 0, 0, new byte[0], GpuFormat.RGBA8_UNORM,
					"the game's own texture, looked up at every bind", true);
		}

		try (InputStream stream = resource.get().open()) {
			byte[] bytes = stream.readNBytes(MAX_RESOURCE_BYTES + 1);
			if (bytes.length > MAX_RESOURCE_BYTES) {
				return missing(texture, notes, path + " holds more than the " + MAX_RESOURCE_BYTES
						+ " bytes a texture is read under");
			}

			NoiseTexture.Image decoded = NoiseTexture.decode(bytes);

			return new Image(texture, decoded.width(), decoded.height(), decoded.rgba(), GpuFormat.RGBA8_UNORM,
					decoded.width() + "x" + decoded.height());
		} catch (IOException | RuntimeException e) {
			return missing(texture, notes, path + " could not be read: " + e.getMessage());
		}
	}

	
	static Identifier location(String path) {
		String[] parts = path.split(":", 0);

		return parts.length < 2 ? null : Identifier.tryBuild(parts[0], parts[1]);
	}

	
	private static Image missing(PackTexture texture, List<String> notes, String reason) {
		notes.add(reason + ", so " + texture.sampler() + " reads the game's missing texture");

		try (NativeImage image = MissingTextureAtlasSprite.generateMissingImage()) {
			int[] texels = image.getPixelsABGR();
			byte[] rgba = new byte[texels.length * 4];
			for (int at = 0; at < texels.length; at++) {
				rgba[at * 4] = (byte) texels[at];
				rgba[at * 4 + 1] = (byte) (texels[at] >> 8);
				rgba[at * 4 + 2] = (byte) (texels[at] >> 16);
				rgba[at * 4 + 3] = (byte) (texels[at] >>> 24);
			}

			return new Image(texture, image.getWidth(), image.getHeight(), rgba, GpuFormat.RGBA8_UNORM,
					image.getWidth() + "x" + image.getHeight() + " missing texture");
		}
	}

	
	List<Image> images() {
		return this.images;
	}

	
	Image find(TextureStage stage, String sampler) {
		String name = SamplerPlan.behind(sampler);
		if (!name.equals(sampler)) {
			return this.volumes.get(name);
		}

		// No stage means a program no family claims, which is a program nothing runs. Answered
		// rather than looked up: a stage override is written for one of the seven and there is no
		// honest way to guess which one an eighth would have been.
		return stage == null
				? null
				: this.declared.resolve(stage, name).map(this.byTexture::get).orElse(null);
	}

	
	List<String> notes() {
		return this.notes;
	}

	
	static String describe(Image image) {
		PackTexture texture = image.texture();
		boolean flat = texture.raw()
				.filter(raw -> raw.shape() == PackTexture.Shape.TEXTURE_3D)
				.isPresent();

		return texture.stage().map(stage -> stage.name().toLowerCase(Locale.ROOT))
				.orElse("every stage") + " " + texture.sampler() + " reads " + texture.path()
				+ ", " + image.shape() + ", " + (texture.blur() ? "linear" : "nearest") + " and "
				+ (texture.clamp() ? "clamped" : "repeating")
				+ (flat ? ", the " + (texture.clamp() ? "clamp" : "repeat") + " done by the shader"
						: "");
	}

	
	private static GpuFormat format(PixelType type) {
		return switch (type) {
			case UNSIGNED_BYTE -> GpuFormat.RGBA8_UNORM;
			case UNSIGNED_SHORT -> GpuFormat.RGBA16_UNORM;
			case HALF_FLOAT -> GpuFormat.RGBA16_FLOAT;
			case FLOAT -> GpuFormat.RGBA32_FLOAT;
			default -> throw new IllegalStateException(type + " is not a type a blob goes up in");
		};
	}

	
	long bytes() {
		long total = 0L;
		for (Image image : this.images) {
			total += image.bytes();
		}

		return total;
	}

	
	boolean loud() {
		return bytes() > LOUD_BYTES;
	}
}
