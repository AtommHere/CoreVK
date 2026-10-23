package net.corevk.corevk.pack.model;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

public record ImageInformation(String name, Optional<String> sampler, PackTexture.Shape shape,
		PixelFormat pixelFormat, TargetFormat.Resolution internalFormat, PixelType pixelType,
		int width, int height, int depth, boolean clear, boolean relative, float relativeWidth,
		float relativeHeight) {

	
	public static final int LIMIT = 16;

	
	public record Reading(List<ImageInformation> images, List<String> dropped) {

		public Reading {
			images = List.copyOf(images);
			dropped = List.copyOf(dropped);
		}

		public static Reading empty() {
			return new Reading(List.of(), List.of());
		}
	}

	
	public String describe() {
		String size = this.relative
				? this.relativeWidth + "x" + this.relativeHeight + " of the screen"
				: switch (this.shape) {
					case TEXTURE_1D -> Integer.toString(this.width);
					case TEXTURE_2D -> this.width + "x" + this.height;
					case TEXTURE_3D -> this.width + "x" + this.height + "x" + this.depth;
					case TEXTURE_RECTANGLE -> this.width + "x" + this.height;
				};

		return this.name
				+ this.sampler.map(sampler -> " as " + sampler).orElse("")
				+ " " + this.shape.name() + " " + this.internalFormat.declared() + " " + size
				+ (this.clear ? ", cleared each frame" : "");
	}

	
	public static String parse(String name, String value, Map<String, String> defines,
			List<ImageInformation> images) {
		String[] parts = value.split(" ", -1);
		if (parts.length < 6) {
			return "expected at least six words";
		}

		Optional<String> sampler = parts[0].isEmpty() || parts[0].equalsIgnoreCase("none")
				? Optional.empty()
				: Optional.of(parts[0]);
		Optional<PixelFormat> format = PixelFormat.parse(parts[1]);
		TargetFormat.Resolution internal = TargetFormat.resolve(parts[2]);
		Optional<PixelType> pixelType = PixelType.parse(parts[3]);
		if (format.isEmpty() || pixelType.isEmpty()
				|| internal.reason() == TargetFormat.Reason.UNKNOWN) {
			return "format " + parts[1] + " internal " + parts[2] + " pixel type " + parts[3];
		}

		boolean clear = Boolean.parseBoolean(parts[4]);
		boolean relative = Boolean.parseBoolean(parts[5]);
		PackTexture.Shape shape;
		int width;
		int height;
		int depth;
		float relativeWidth = 0;
		float relativeHeight = 0;
		if (relative) {
			if (parts.length != 8) {
				return "a relative image takes two size words";
			}

			try {
				relativeWidth = Float.parseFloat(parts[6]);
				relativeHeight = Float.parseFloat(parts[7]);
			} catch (NumberFormatException e) {
				return "relative size is not a number";
			}

			shape = PackTexture.Shape.TEXTURE_2D;
			width = 0;
			height = 0;
			depth = 0;
		} else if (parts.length == 7) {
			OptionalInt size = dimension(parts[6], defines);
			if (size.isEmpty()) {
				return "size is not a number";
			}

			shape = PackTexture.Shape.TEXTURE_1D;
			width = size.getAsInt();
			height = 0;
			depth = 0;
		} else if (parts.length == 8) {
			OptionalInt sizeX = dimension(parts[6], defines);
			OptionalInt sizeY = dimension(parts[7], defines);
			if (sizeX.isEmpty() || sizeY.isEmpty()) {
				return "size is not a number";
			}

			shape = PackTexture.Shape.TEXTURE_2D;
			width = sizeX.getAsInt();
			height = sizeY.getAsInt();
			depth = 0;
		} else if (parts.length == 9) {
			OptionalInt sizeX = dimension(parts[6], defines);
			OptionalInt sizeY = dimension(parts[7], defines);
			OptionalInt sizeZ = dimension(parts[8], defines);
			if (sizeX.isEmpty() || sizeY.isEmpty() || sizeZ.isEmpty()) {
				return "size is not a number";
			}

			shape = PackTexture.Shape.TEXTURE_3D;
			width = sizeX.getAsInt();
			height = sizeY.getAsInt();
			depth = sizeZ.getAsInt();
		} else {
			return "unknown image type";
		}

		images.add(new ImageInformation(name, sampler, shape, format.get(), internal,
				pixelType.get(), width, height, depth, clear, relative, relativeWidth,
				relativeHeight));

		return null;
	}

	
	private static OptionalInt dimension(String token, Map<String, String> defines) {
		try {
			return OptionalInt.of(Integer.parseInt(token));
		} catch (NumberFormatException e) {
			String value = defines.get(token);
			if (value == null) {
				return OptionalInt.empty();
			}

			try {
				return OptionalInt.of(Integer.parseInt(value.trim()));
			} catch (NumberFormatException ignored) {
				return OptionalInt.empty();
			}
		}
	}
}
