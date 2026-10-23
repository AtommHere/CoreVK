package net.corevk.corevk;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class HostReport {

	
	private static final String VULKAN = "Vulkan";

	
	private static final String UNKNOWN = "unknown";

	
	private static final String CHLORIDE_CONFIG = "config/chloride-client.toml";

	
	private static final String CHLORIDE_ID = "chloride";

	
	private static volatile boolean saidInWorld;

	
	private static final List<Entry> CHLORIDE = List.of(
			new Entry("fastBlocks", "chests",
					"Chloride draws chests as static block models. That path is covered over by this "
							+ "engine's final pass, so chests go invisible with no other symptom. Set "
							+ "it to false to see them"),
			new Entry("fastBlocks", "beds",
					"Chloride draws beds as static block models, and they go the way chests do: "
							+ "covered over by the final pass, invisible with no other symptom. Set "
							+ "it to false to see them"),
			new Entry("culling", "tileEntities",
					"Chloride decides on its own which block entities are drawn, by distance. What it "
							+ "takes out is never handed to the pack, so a chest or a sign that is "
							+ "not there is this rather than anything the pack does. Set it to false "
							+ "to have them all"),
			new Entry("culling", "entities",
					"Chloride decides on its own which entities are drawn, by distance, and this one "
							+ "governs every kind but the monsters. What it takes out is never handed "
							+ "to the pack either, so a boat, an item frame or a villager that is "
							+ "missing, or that appears as you walk towards it, is this. Set it to "
							+ "false to have them all"),
			new Entry("culling", "monsters",
					"Chloride decides on its own which monsters are drawn, by a distance of their "
							+ "own. A zombie or a creeper takes this line rather than the one above, "
							+ "so it is the one to set to false when what is missing is a monster"));

	private HostReport() {
	}

	
	public static String backend() {
		GpuDevice device = RenderSystem.tryGetDevice();
		return device == null ? UNKNOWN : device.getDeviceInfo().backendName();
	}

	
	public static boolean otherBackend() {
		String backend = backend();

		return !UNKNOWN.equals(backend) && !VULKAN.equals(backend);
	}

	
	public static void sayInWorld() {
		if (saidInWorld) {
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.gui.screen() != null) {
			return;
		}

		saidInWorld = true;
		if (!otherBackend()) {
			return;
		}

		// CoreVK drawing this session means a player who picked it, and a red line saying the picture
		// is missing would be false for them. Asked of what CoreVK chose at startup rather than of
		// CoreVK being there: after a Vulkan boot that fell back, CoreVK draws nothing and the line is
		// owed.
		if (CoreVKBeside.draws()) {
			return;
		}

		minecraft.gui.hud.getChat().addClientSystemMessage(Component.translatable(
				ScreenText.OTHER_BACKEND, backend(), Component.translatable(ScreenText.GRAPHICS_API),
				Component.translatable(ScreenText.GRAPHICS_API_VULKAN))
				.withStyle(ChatFormatting.RED));
	}

	
	public static void say(Path gameDirectory) {
		sayBackend();

		// Asked before the file is looked for, and that question is the difference between a mod
		// that is installed and has not written its settings yet, which is worth a line because its
		// defaults are then in play, and a mod that is simply not there, which is worth none.
		if (CoreVK.platform().isModLoaded(CHLORIDE_ID)) {
			sayChloride(gameDirectory.resolve(CHLORIDE_CONFIG));
		}
	}

	
	private static void sayBackend() {
		if (!otherBackend()) {
			return;
		}

		if (CoreVKBeside.draws()) {
			CoreVK.logger().info("This game is running the {} backend with CoreVK installed, so CoreVK "
					+ "draws the packs and {} stands aside. Set Graphics API to \"Prefer Vulkan "
					+ "(Experimental)\" under Options, Video Settings, and restart to draw them with {} "
					+ "instead", backend(), CoreVK.MOD_NAME, CoreVK.MOD_NAME);

			return;
		}

		CoreVK.logger().error("This game is running the {} backend and {}'s programs are translated "
				+ "for {} alone. The mod loads, and a pack it is asked for is neither read nor drawn: "
				+ "the game keeps its own image. Set "
				+ "Graphics API to \"Prefer Vulkan (Experimental)\" under Options, Video Settings, "
				+ "and restart. If it was already set there, either a launch argument forced this "
				+ "backend, which the game says higher up, or the Vulkan boot failed and the game "
				+ "fell back: the reason is in the lines the game logged before this one",
				backend(), CoreVK.MOD_NAME, VULKAN);
	}

	
	private static void sayChloride(Path file) {
		List<String> lines;
		try {
			if (!Files.isRegularFile(file)) {
				CoreVK.logger().warn("Chloride's own settings were not read: there is no file at {}. "
						+ "Some of them decide which blocks and which entities are drawn at all, "
						+ "before anything of the pack is asked", file);
				return;
			}

			lines = Files.readAllLines(file);
		} catch (IOException failure) {
			CoreVK.logger().warn("Chloride's own settings were not read: {} could not be opened. Some "
					+ "of them decide which blocks and which entities are drawn at all, before "
					+ "anything of the pack is asked", file, failure);
			return;
		}

		List<String> missing = new ArrayList<>();
		for (Entry entry : CHLORIDE) {
			Boolean written = written(lines, entry.table(), entry.key());
			if (written == null) {
				missing.add(entry.name());
			} else if (written) {
				CoreVK.logger().warn("{} is on in {}. {}", entry.name(), file, entry.cost());
			}
		}

		if (!missing.isEmpty()) {
			// Joined rather than handed over whole: each name is already bracketed, and a list
			// printed as one reads "for [[culling] entities]".
			CoreVK.logger().warn("{} writes no line for {}, so whether Chloride is drawing what that "
					+ "governs was not established. It decides which blocks and which entities reach "
					+ "the pack at all", file, String.join(", ", missing));
		}
	}

	
	private static Boolean written(List<String> lines, String table, String key) {
		String current = "";
		for (String raw : lines) {
			// A comment runs to the end of the line, and both a table header and a value may carry
			// one. Cutting it here rather than at each reading below is also what turns a whole line
			// of comment into an empty one.
			int hash = raw.indexOf('#');
			String line = (hash < 0 ? raw : raw.substring(0, hash)).trim();

			if (line.startsWith("[") && line.endsWith("]")) {
				current = line.substring(1, line.length() - 1).trim();
				continue;
			}

			int equals = line.indexOf('=');
			if (!current.equals(table) || equals < 1
					|| !line.substring(0, equals).trim().equals(key)) {
				continue;
			}

			String value = line.substring(equals + 1).trim();
			if (value.equals("true")) {
				return Boolean.TRUE;
			}

			return value.equals("false") ? Boolean.FALSE : null;
		}

		return null;
	}

	
	private record Entry(String table, String key, String cost) {

		String name() {
			return "[" + this.table + "] " + this.key;
		}
	}
}
