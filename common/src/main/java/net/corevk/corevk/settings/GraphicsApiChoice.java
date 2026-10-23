package net.corevk.corevk.settings;

import net.corevk.corevk.CoreVK;

import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public enum GraphicsApiChoice {

	VULKAN("vulkan"),

	OPENGL("opengl"),

	GAME("game");

	public static final GraphicsApiChoice DEFAULT = VULKAN;

	private static final String FILE = "graphics-api.txt";

	private final String word;

	GraphicsApiChoice(String word) {
		this.word = word;
	}

	public String word() {
		return this.word;
	}

	public static GraphicsApiChoice read() {
		Path file = file();
		String asked;
		try {
			if (!Files.isRegularFile(file)) {
				return DEFAULT;
			}

			asked = Files.readString(file, StandardCharsets.UTF_8).trim().toLowerCase(Locale.ROOT);
		} catch (IOException | RuntimeException ignored) {
			return DEFAULT;
		}

		for (GraphicsApiChoice choice : values()) {
			if (choice.word.equals(asked)) {
				return choice;
			}
		}

		CoreVK.logger().warn("corevk/{} says \"{}\", which is not one of vulkan, opengl or game, "
				+ "so the default is used", FILE, asked);

		return DEFAULT;
	}

	public static void write(Path gameDirectory, GraphicsApiChoice choice) {
		Path file = gameDirectory.resolve("corevk").resolve(FILE);
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, choice.word + "\n", StandardCharsets.UTF_8);
		} catch (IOException | RuntimeException e) {
			CoreVK.logger().error("CoreVK could not write the graphics API choice to {}", file, e);
		}
	}

	private static Path file() {
		Minecraft minecraft = Minecraft.getInstance();
		Path directory = minecraft == null ? null : minecraft.gameDirectory.toPath();

		return directory == null
				? Path.of("corevk", FILE)
				: directory.resolve("corevk").resolve(FILE);
	}
}
