package net.corevk.corevk.screen;

import net.corevk.corevk.HostReport;
import net.corevk.corevk.CoreVKBeside;
import net.corevk.corevk.render.PackChoice;
import net.corevk.corevk.ScreenText;
import net.corevk.corevk.settings.PackFile;
import net.corevk.corevk.settings.PackSession;
import net.corevk.corevk.CoreVK;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;

public final class SettingsKey {

	public static final KeyMapping.Category CATEGORY =
			new KeyMapping.Category(Identifier.fromNamespaceAndPath(CoreVK.MOD_ID, "keybinds"));

	public static final KeyMapping OPEN =
			new KeyMapping(ScreenText.OPEN_SETTINGS, InputConstants.KEY_I, CATEGORY);

	public static final KeyMapping RELOAD =
			new KeyMapping(ScreenText.RELOAD_PACK, InputConstants.KEY_R, CATEGORY);

	public static final KeyMapping TOGGLE =
			new KeyMapping(ScreenText.TOGGLE_SHADERS, InputConstants.KEY_K, CATEGORY);

	private static final String COREVK_SCREEN_KEY = "corevk.keybind.shaderPackSelection";

	private SettingsKey() {
	}

	public static void beforeTick() {
		if (!CoreVKBeside.installed() || HostReport.otherBackend()) {
			return;
		}

		KeyMapping corevk = sharedCoreVKKey();
		if (corevk != null) {
			drain(corevk);
		}
	}

	public static void poll() {
		if (HostReport.otherBackend()) {
			drain(OPEN);
			drain(RELOAD);
			drain(TOGGLE);
			return;
		}

		if (drain(OPEN)) {
			Minecraft minecraft = Minecraft.getInstance();
			minecraft.gui.setScreen(PackScreens.open(minecraft.gui.screen()));

			return;
		}

		if (drain(TOGGLE)) {
			toggle();

			return;
		}

		if (drain(RELOAD)) {
			reload();
		}
	}

	private static @Nullable KeyMapping sharedCoreVKKey() {
		KeyMapping corevk = KeyMapping.get(COREVK_SCREEN_KEY);

		return corevk != null && corevk.same(OPEN) ? corevk : null;
	}

	private static boolean drain(KeyMapping key) {
		if (!key.consumeClick()) {
			return false;
		}

		while (key.consumeClick()) {

		}

		return true;
	}

	private static void reload() {
		PackChoice.reload(directory());

		say(PackChoice.lastError()
				.map(reason -> Component.translatable(ScreenText.RELOAD_FAILED, reason)
						.withStyle(ChatFormatting.RED))
				.orElseGet(() -> Component.translatable(ScreenText.PACK_RELOADED)));
	}

	private static void toggle() {
		Path directory = directory();
		Path file = PackChoice.packFile(directory);
		boolean enabled;
		try {
			PackFile onDisk = PackFile.read(file);
			enabled = !onDisk.enabled();
			PackFile.write(file, onDisk.withEnabled(enabled));
		} catch (IOException | RuntimeException e) {
			CoreVK.logger().error("CoreVK could not write {}", file, e);
			say(Component.translatable(ScreenText.TOGGLE_FAILED, String.valueOf(e.getMessage()))
					.withStyle(ChatFormatting.RED));

			return;
		}

		PackChoice.reload(directory);

		if (!enabled) {
			say(Component.translatable(ScreenText.SHADERS_TURNED_OFF));

			return;
		}

		PackFile asked = PackChoice.askedFor();
		MutableComponent refused = refusal(asked);
		if (refused == null) {
			say(Component.translatable(ScreenText.SHADERS_TOGGLED, asked.name()));

			return;
		}

		turnBackOff(directory, file);
		say(refused);
	}

	private static @Nullable MutableComponent refusal(PackFile asked) {
		if (PackChoice.noPackWanted() || !asked.wantsPack() || asked.namesNone()) {
			return Component.translatable(ScreenText.NO_PACK).withStyle(ChatFormatting.YELLOW);
		}

		if (PackChoice.packMissing()) {
			return Component.translatable(ScreenText.PACK_MISSING, asked.name())
					.withStyle(ChatFormatting.RED);
		}

		return PackChoice.lastError()
				.map(reason -> Component.translatable(ScreenText.TOGGLE_FAILED, reason)
						.withStyle(ChatFormatting.RED))
				.orElse(null);
	}

	private static void turnBackOff(Path directory, Path file) {
		try {
			PackFile.write(file, PackFile.read(file).withEnabled(false));
		} catch (IOException | RuntimeException e) {
			CoreVK.logger().error("CoreVK could not write {}", file, e);

			return;
		}

		PackChoice.reload(directory);
	}

	private static Path directory() {
		return PackChoice.session()
				.map(PackSession::gameDirectory)
				.orElseGet(() -> CoreVK.platform().gameDirectory());
	}

	private static void say(MutableComponent said) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player != null) {
			minecraft.player.sendSystemMessage(said);
		}
	}
}
