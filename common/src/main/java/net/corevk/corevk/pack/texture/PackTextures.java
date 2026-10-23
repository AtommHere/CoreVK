package net.corevk.corevk.pack.texture;

import net.corevk.corevk.pack.model.PackTexture;
import net.corevk.corevk.pack.model.PixelFormat;
import net.corevk.corevk.pack.model.PixelType;
import net.corevk.corevk.pack.model.TargetFormat;
import net.corevk.corevk.pack.model.TargetName;
import net.corevk.corevk.pack.model.TextureStage;
import net.corevk.corevk.pack.source.ShaderPackSource;
import net.corevk.corevk.pack.source.ShaderProperties;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

public final class PackTextures {

	private static final String CUSTOM_PREFIX = "customTexture.";
	private static final String STAGE_PREFIX = "texture.";

	
	private static final int PNG_TOKENS = 1;
	private static final int RAW_1D_TOKENS = 6;
	private static final int RAW_2D_TOKENS = 7;
	private static final int RAW_3D_TOKENS = 8;

	
	private static final long PNG_SIGNATURE = 0x89504E470D0A1A0AL;

	
	private static final int IHDR_LENGTH = 13;
	private static final int IHDR_TYPE = 0x49484452;

	
	private static final int PNG_HEADER_BYTES = 16;

	private final List<PackTexture> supplied;
	private final List<Refused> refused;
	private final List<String> notes;
	private final Map<TextureStage, Map<String, PackTexture>> overrides;
	private final Map<TextureStage, Set<String>> diverted;
	private final Set<String> divertedEverywhere;
	private final Map<String, PackTexture> named;

	
	public record Refused(String key, String value, String reason, Optional<TextureStage> stage,
			String sampler) {

		private static Refused of(String key, String value, String reason) {
			return new Refused(key, value, reason, Optional.empty(), "");
		}

		@Override
		public String toString() {
			return this.key + "=" + this.value + ": " + this.reason;
		}
	}

	private PackTextures(List<PackTexture> supplied, List<Refused> refused, List<String> notes) {
		this.supplied = List.copyOf(supplied);
		this.refused = List.copyOf(refused);
		this.notes = List.copyOf(notes);

		Map<TextureStage, Map<String, PackTexture>> overrides = new EnumMap<>(TextureStage.class);
		Map<String, PackTexture> named = new LinkedHashMap<>();
		for (PackTexture texture : supplied) {
			texture.stage().ifPresentOrElse(
					stage -> overrides.computeIfAbsent(stage, _ -> new LinkedHashMap<>())
							.put(texture.sampler(), texture),
					() -> named.put(texture.sampler(), texture));
		}

		Map<TextureStage, Set<String>> diverted = new EnumMap<>(TextureStage.class);
		Set<String> divertedEverywhere = new LinkedHashSet<>();
		for (Refused one : refused) {
			if (one.sampler().isEmpty()) {
				continue;
			}

			// A refused customTexture names no stage and takes its name over in all of them, exactly
			// as an honoured one does. Kept apart from the stage map rather than written into all
			// seven, so that what the pack said stays readable in what is stored.
			one.stage().ifPresentOrElse(
					stage -> diverted.computeIfAbsent(stage, _ -> new LinkedHashSet<>())
							.add(one.sampler()),
					() -> divertedEverywhere.add(one.sampler()));
		}

		this.overrides = Map.copyOf(overrides);
		this.diverted = Map.copyOf(diverted);
		this.divertedEverywhere = Set.copyOf(divertedEverywhere);
		this.named = Map.copyOf(named);
	}

	
	public static PackTextures empty() {
		return new PackTextures(List.of(), List.of(), List.of());
	}

	
	public static PackTextures read(ShaderProperties properties, Map<String, String> defines,
			ShaderPackSource source) throws IOException {
		List<PackTexture> supplied = new ArrayList<>();
		List<Refused> refused = new ArrayList<>();
		List<String> notes = new ArrayList<>();

		for (Map.Entry<String, String> line : properties.customTextures(defines).entrySet()) {
			read(line.getKey(), line.getValue(), source, supplied, refused, notes);
		}

		return new PackTextures(supplied, refused, notes);
	}

	private static void read(String key, String value, ShaderPackSource source,
			List<PackTexture> supplied, List<Refused> refused, List<String> notes)
			throws IOException {
		Optional<TextureStage> stage;
		String sampler;

		if (key.startsWith(CUSTOM_PREFIX)) {
			stage = Optional.empty();
			// Dots and all: this name belongs to nobody but the pack, so nothing may be cut off it.
			sampler = key.substring(CUSTOM_PREFIX.length());
		} else {
			String rest = key.substring(STAGE_PREFIX.length());
			int dot = rest.indexOf('.');
			if (dot < 0) {
				refused.add(Refused.of(key, value, "names no stage to override the sampler in"));
				return;
			}

			stage = TextureStage.parse(rest.substring(0, dot));
			if (stage.isEmpty()) {
				refused.add(Refused.of(key, value,
						"names the stage " + rest.substring(0, dot) + ", which is not one of the seven"));
				return;
			}

			// Truncated at its first dot, as CoreVK truncates it. A sampler name has no dot in it, so
			// what follows one is whatever the pack thought it was adding, and it is not part of the
			// name being overridden.
			String tail = rest.substring(dot + 1);
			int inner = tail.indexOf('.');
			sampler = inner < 0 ? tail : tail.substring(0, inner);
		}

		if (sampler.isEmpty()) {
			refused.add(Refused.of(key, value, "names no sampler"));
			return;
		}

		// The name is settled by the key alone, so from here on a refusal carries it and leaves it
		// with nothing behind it. What the value SAYS about the file cannot put the name back: the
		// pack has said which sampler it is taking over, and a word it misspelled in the rest of the
		// line does not unsay it. Letting one typo hand colortex3 back to colour target three would
		// have a deferred read the scene as a lookup table, which is the picture nobody questions,
		// where black is a question. The file itself is the one thing that can, by not being there
		// or by not opening as a PNG; those cases are at the bottom of this method.
		// One space, the separator CoreVK gives the texture directives, and not a run of whitespace:
		// the word count is what tells a PNG from a raw texture there, and an empty word between two
		// real ones moves the value into a count the format gives no meaning to.
		String[] parts = value.trim().split(" ", -1);
		if (parts.length != PNG_TOKENS && (parts.length < RAW_1D_TOKENS || parts.length > RAW_3D_TOKENS)) {
			refused.add(new Refused(key, value, "holds " + parts.length
					+ " words, and the format gives a meaning to 1, 6, 7 and 8", stage, sampler));
			return;
		}

		String path = parts[0];

		// A namespaced path names a resource the GAME owns and hands out through its own manager,
		// and it deliberately does NOT go through the confinement in ShaderPackSource. That is not
		// a hole in it: the confinement exists so that a path the pack wrote cannot leave the
		// pack's directory once normalised, and there is no path here to normalise. What the pack
		// wrote is an identifier the game resolves for itself, out of the resource packs already
		// loaded, exactly as it resolves the identifiers of its own textures; nothing in this
		// engine turns it into a name on disk. PackImages is where it is looked up.
		//
		// Everything the rest of the line declared goes with it, as it goes with CoreVK: a namespaced
		// path is served as a plain image whatever shape and format the words after it announce, no
		// .mcmeta of the pack is consulted for it, and the defaults left standing here are the
		// nearest and repeating pair CoreVK binds such a texture with.
		if (PackTexture.gameResource(path)) {
			supplied.add(new PackTexture(sampler, stage, path, Optional.empty(), false, false));
			return;
		}

		Optional<PackTexture.Raw> raw = parts.length == PNG_TOKENS
				? Optional.empty()
				: rawOf(parts);
		if (parts.length != PNG_TOKENS && raw.isEmpty()) {
			refused.add(new Refused(key, value, "declares a raw texture this cannot read", stage,
					sampler));
			return;
		}

		Optional<Path> file = source.file(path);
		if (file.isEmpty()) {
			// The one refusal that hands the name back, and insidePack is what tells the two apart.
			// A path the pack does not ship is a file the author forgot, and CoreVK drops the whole
			// declaration on it: the sampler never enters the stage's map, so the name goes on
			// meaning what it meant, which for a colour target is that target. Claiming it here
			// instead would leave the pack reading black where CoreVK reads the frame, and that is a
			// difference the pack's own author never saw.
			//
			// A path that leaves the pack once normalised is the other thing entirely, and it stays
			// claimed with the rest: that one is not a mistake this engine should smooth over.
			refused.add(source.insidePack(path)
					? Refused.of(key, value, "points at " + path + ", which the pack does not ship, "
							+ "so " + sampler + " reads what it read before")
					: new Refused(key, value, "points at " + path + ", which is outside the pack",
							stage, sampler));
			return;
		}

		long size = source.size(file.get());
		if (raw.isPresent()) {
			long wanted = raw.get().bytes();
			if (size < wanted) {
				refused.add(new Refused(key, value, path + " holds " + size + " bytes and the "
						+ "declaration asks for " + wanted, stage, sampler));
				return;
			}

			if (size > wanted) {
				notes.add(path + " holds " + size + " bytes for a declaration of " + wanted
						+ ", and the tail is not uploaded");
			}
		} else {
			// A picture CoreVK cannot decode leaves the stage's map exactly as a missing file does
			// (ShaderPack.java:341-345 for the read, CustomTextureManager.java:58-64 for the
			// decode), so the name is handed back on the same terms and every program reads what it
			// read before, the colour target of that name included. Only the header is read here,
			// sixteen bytes, because every opening reads these directives and most of them upload
			// nothing; the pixels are decoded where they are uploaded.
			Optional<String> unreadable = pngHeader(source, file.get(), size);
			if (unreadable.isPresent()) {
				refused.add(Refused.of(key, value, path + " is not a PNG the game reads ("
						+ unreadable.get() + "), so " + sampler + " reads what it read before"));
				return;
			}
		}

		// A raw blob is filtered and clamped unless the pack says otherwise and an image is neither,
		// which is OptiFine's rule rather than a taste: a blob carries a lookup table as often as an
		// image, and a table read past its edge or between its entries answers with something that
		// was never in it.
		String meta = meta(source, path);
		boolean sampled = raw.isPresent();

		PackTexture texture = new PackTexture(sampler, stage, path, raw,
				field(meta, "blur").orElse(sampled), field(meta, "clamp").orElse(sampled));

		// A volume is served by spreading it over an atlas, so it is the atlas that has to be a
		// texture a device would take, and the declaration says nothing about how wide that comes
		// out: the length of the blob is checked and its shape is not. Refused by name, like the
		// rest, so the sampler reads black.
		if (flattenable(texture)) {
			VolumeAtlas atlas = VolumeAtlas.of(raw.orElseThrow(), texture.clamp());
			if (!atlas.fits()) {
				refused.add(new Refused(key, value, path + " lays out flat as " + atlas.atlasWidth()
						+ "x" + atlas.atlasHeight() + ", which is past what a texture can be", stage,
						sampler));
				return;
			}
		} else if (volume(texture)) {
			// A hole rather than a rule, and one that costs the whole pack, so it is said out loud
			// where the refusal that follows cannot say it: a channel type or order the atlas does
			// not lay out, or an extent of nought, and the sampler3D left standing takes every
			// program that declares it. VolumeAtlas.serves says which are laid out and why.
			notes.add(path + " is a volume of " + raw.orElseThrow().pixelFormat() + " "
					+ raw.orElseThrow().pixelType() + ", which is not laid out flat here, so " + sampler
					+ " stays a sampler3D and no program declaring it can be built");
		} else if (raw.filter(RawImage::serves).isPresent()) {
			// The plain case, measured the same way and for the same reason. The declaration IS the
			// size here, so there is nothing to work out: what is asked is only whether a device
			// would take it.
			RawImage image = RawImage.of(raw.orElseThrow());
			if (!image.fits()) {
				refused.add(new Refused(key, value, path + " is declared " + image.width() + "x"
						+ image.height() + ", which is past what a texture can be", stage, sampler));
				return;
			}
		}

		supplied.add(texture);
	}

	
	private static Optional<PackTexture.Raw> rawOf(String[] parts) {
		PackTexture.Shape shape = switch (parts.length) {
			case RAW_1D_TOKENS -> PackTexture.Shape.TEXTURE_1D;
			case RAW_2D_TOKENS -> flat(parts[1]);
			default -> PackTexture.Shape.TEXTURE_3D;
		};

		if (shape == null) {
			return Optional.empty();
		}

		// The sizes always start at the fourth word and there are as many of them as the count
		// says: one more word than a 1D texture is one more axis.
		int dimensions = parts.length - RAW_1D_TOKENS + 1;
		int[] size = new int[3];
		try {
			for (int axis = 0; axis < dimensions; axis++) {
				size[axis] = Integer.parseInt(parts[3 + axis]);
			}
		} catch (NumberFormatException e) {
			return Optional.empty();
		}

		for (int axis = 0; axis < dimensions; axis++) {
			if (size[axis] <= 0) {
				return Optional.empty();
			}
		}

		Optional<PixelFormat> pixelFormat = PixelFormat.parse(parts[parts.length - 2]);
		Optional<PixelType> pixelType = PixelType.parse(parts[parts.length - 1]);
		if (pixelFormat.isEmpty() || pixelType.isEmpty()) {
			return Optional.empty();
		}

		TargetFormat.Resolution internal = TargetFormat.resolve(parts[2]);
		if (internal.reason() == TargetFormat.Reason.UNKNOWN) {
			return Optional.empty();
		}

		return Optional.of(new PackTexture.Raw(shape, internal, size[0], size[1], size[2],
				pixelFormat.get(), pixelType.get()));
	}

	
	private static PackTexture.Shape flat(String word) {
		String name = word.trim().toUpperCase(Locale.ROOT);

		if (name.equals(PackTexture.Shape.TEXTURE_2D.name())) {
			return PackTexture.Shape.TEXTURE_2D;
		}

		return name.equals(PackTexture.Shape.TEXTURE_RECTANGLE.name())
				? PackTexture.Shape.TEXTURE_RECTANGLE
				: null;
	}

	
	private static Optional<String> pngHeader(ShaderPackSource source, Path file, long size) {
		if (size < PNG_HEADER_BYTES) {
			return Optional.of("PNG header missing");
		}

		ByteBuffer header;
		try {
			header = ByteBuffer.wrap(source.head(file, PNG_HEADER_BYTES));
		} catch (IOException e) {
			return Optional.of(e.getMessage());
		}

		if (header.getLong(0) != PNG_SIGNATURE) {
			return Optional.of("bad PNG signature");
		}

		if (header.getInt(8) != IHDR_LENGTH) {
			return Optional.of("bad length for the IHDR chunk");
		}

		return header.getInt(12) == IHDR_TYPE
				? Optional.empty()
				: Optional.of("bad type for the IHDR chunk");
	}

	
	private static String meta(ShaderPackSource source, String path) throws IOException {
		Optional<Path> meta = source.file(path + ".mcmeta");

		return meta.isEmpty() ? "" : String.join(" ", source.readLines(meta.get()));
	}

	
	private static Optional<Boolean> field(String text, String name) {
		int at = text.indexOf('"' + name + '"');
		if (at < 0) {
			return Optional.empty();
		}

		int colon = text.indexOf(':', at);
		if (colon < 0) {
			return Optional.empty();
		}

		String tail = text.substring(colon + 1).stripLeading();
		if (tail.startsWith("true")) {
			return Optional.of(true);
		}

		return tail.startsWith("false") ? Optional.of(false) : Optional.empty();
	}

	
	public List<PackTexture> supplied() {
		return this.supplied;
	}

	
	public Map<String, VolumeAtlas> volumes() {
		Map<String, VolumeAtlas> flat = new LinkedHashMap<>();
		for (PackTexture texture : this.supplied) {
			if (flattenable(texture)) {
				flat.putIfAbsent(texture.sampler(),
						VolumeAtlas.of(texture.raw().orElseThrow(), texture.clamp()));
			}
		}

		return Collections.unmodifiableMap(flat);
	}

	private static boolean flattenable(PackTexture texture) {
		return texture.raw().filter(VolumeAtlas::serves).isPresent();
	}

	
	private static boolean volume(PackTexture texture) {
		return texture.raw()
				.filter(raw -> raw.shape() == PackTexture.Shape.TEXTURE_3D)
				.isPresent();
	}

	
	public List<Refused> refused() {
		return this.refused;
	}

	
	public List<String> notes() {
		return this.notes;
	}

	
	public Optional<PackTexture> resolve(TextureStage stage, String sampler) {
		Map<String, PackTexture> forStage = this.overrides.getOrDefault(stage, Map.of());
		for (String spelling : spellings(sampler)) {
			PackTexture found = forStage.get(spelling);
			if (found != null) {
				return Optional.of(found).filter(texture -> !volume(texture));
			}
		}

		return Optional.ofNullable(this.named.get(sampler)).filter(texture -> !volume(texture));
	}

	
	public Set<String> suppliedTo(TextureStage stage) {
		Set<String> names = new LinkedHashSet<>();
		this.named.forEach((sampler, texture) -> {
			if (!volume(texture)) {
				names.add(sampler);
			}
		});
		this.overrides.getOrDefault(stage, Map.of()).forEach((sampler, texture) -> {
			if (!volume(texture)) {
				names.addAll(spellings(sampler));
			}
		});
		this.diverted.getOrDefault(stage, Set.of())
				.forEach(sampler -> names.addAll(spellings(sampler)));
		this.divertedEverywhere.forEach(sampler -> names.addAll(spellings(sampler)));

		return names;
	}

	
	public Set<String> picturesTo(TextureStage stage) {
		Set<String> names = new LinkedHashSet<>();
		this.overrides.getOrDefault(stage, Map.of()).forEach((sampler, texture) -> {
			if (texture.raw().isEmpty()) {
				names.addAll(spellings(sampler));
			}
		});

		return names;
	}

	
	private static List<String> spellings(String sampler) {
		OptionalInt index = TargetName.index(sampler);
		if (index.isEmpty()) {
			return List.of(sampler);
		}

		return TargetName.legacyAlias(index.getAsInt())
				.map(alias -> List.of(TargetName.canonical(index.getAsInt()), alias))
				.orElse(List.of(TargetName.canonical(index.getAsInt())));
	}
}
