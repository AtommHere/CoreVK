package net.corevk.corevk.uniform;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;

import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.Raster;
import java.awt.image.SampleModel;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Random;

public final class NoiseTexture {

	private static final int MAX_SIDE = 16384;

	private static final long MAX_TEXELS = 32L * 1024 * 1024;

	private NoiseTexture() {
	}

	@SuppressWarnings("ArrayRecordComponent")
	public record Image(int width, int height, byte[] rgba) {
	}

	public static final class TooLarge extends IOException {

		private static final long serialVersionUID = 1L;

		TooLarge(String message) {
			super(message);
		}
	}

	public static Image decode(byte[] png) throws IOException {
		try (ImageInputStream stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(png))) {
			Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
			if (!readers.hasNext()) {
				throw new IOException("not an image ImageIO recognises");
			}

			ImageReader reader = readers.next();
			try {
				reader.setInput(stream);
				int width = reader.getWidth(0);
				int height = reader.getHeight(0);
				if (width > MAX_SIDE || height > MAX_SIDE || (long) width * height > MAX_TEXELS) {
					throw new TooLarge("the header says " + width + "x" + height + ", past the "
							+ MAX_SIDE + " a side and the " + MAX_TEXELS
							+ " texels an image of a pack is allowed");
				}

				return pixels(reader.read(0), width, height);
			} finally {
				reader.dispose();
			}
		}
	}

	private static Image pixels(BufferedImage image, int width, int height) {
		byte[] pixels = new byte[width * height * 4];
		if (bands(image)) {
			raster(image, width, height, pixels);
		} else {
			converted(image, width, height, pixels);
		}

		return new Image(width, height, pixels);
	}

	private static boolean bands(BufferedImage image) {

		if (image.getColorModel() instanceof IndexColorModel || image.isAlphaPremultiplied()) {
			return false;
		}

		int space = image.getColorModel().getColorSpace().getType();
		if (space != ColorSpace.TYPE_GRAY && space != ColorSpace.TYPE_RGB) {
			return false;
		}

		SampleModel samples = image.getSampleModel();
		if (samples.getNumBands() > 4) {
			return false;
		}

		for (int band = 0; band < samples.getNumBands(); band++) {
			if (samples.getSampleSize(band) != 8 && samples.getSampleSize(band) != 16) {
				return false;
			}
		}

		return true;
	}

	private static void raster(BufferedImage image, int width, int height, byte[] pixels) {
		Raster raster = image.getRaster();
		int bands = raster.getNumBands();
		int[] shift = new int[bands];
		for (int band = 0; band < bands; band++) {
			shift[band] = image.getSampleModel().getSampleSize(band) == 16 ? 8 : 0;
		}

		int minX = raster.getMinX();
		int minY = raster.getMinY();
		int[] row = new int[width * bands];
		for (int y = 0; y < height; y++) {
			raster.getPixels(minX, minY + y, width, 1, row);
			for (int x = 0; x < width; x++) {
				int at = x * bands;
				int red = sample(row, at, shift, 0);
				int green = bands < 3 ? red : sample(row, at, shift, 1);
				int blue = bands < 3 ? red : sample(row, at, shift, 2);
				int alpha = switch (bands) {
					case 2 -> sample(row, at, shift, 1);
					case 4 -> sample(row, at, shift, 3);
					default -> 255;
				};

				int offset = (y * width + x) * 4;
				pixels[offset] = (byte) red;
				pixels[offset + 1] = (byte) green;
				pixels[offset + 2] = (byte) blue;
				pixels[offset + 3] = (byte) alpha;
			}
		}
	}

	private static int sample(int[] row, int at, int[] shift, int band) {
		return (row[at + band] >> shift[band]) & 0xFF;
	}

	private static void converted(BufferedImage image, int width, int height, byte[] pixels) {
		int[] row = new int[width];
		for (int y = 0; y < height; y++) {
			image.getRGB(0, y, width, 1, row, 0, width);
			for (int x = 0; x < width; x++) {
				int argb = row[x];
				int offset = (y * width + x) * 4;
				pixels[offset] = (byte) (argb >> 16);
				pixels[offset + 1] = (byte) (argb >> 8);
				pixels[offset + 2] = (byte) argb;
				pixels[offset + 3] = (byte) (argb >> 24);
			}
		}
	}

	public static byte[] rgba(int resolution) {
		byte[] pixels = new byte[resolution * resolution * 4];
		Random random = new Random(0);

		for (int x = 0; x < resolution; x++) {
			for (int y = 0; y < resolution; y++) {
				int colour = random.nextInt() | (255 << 24);
				int offset = (x + y * resolution) * 4;
				pixels[offset] = (byte) (colour >> 16);
				pixels[offset + 1] = (byte) (colour >> 8);
				pixels[offset + 2] = (byte) colour;
				pixels[offset + 3] = (byte) 255;
			}
		}

		return pixels;
	}
}
