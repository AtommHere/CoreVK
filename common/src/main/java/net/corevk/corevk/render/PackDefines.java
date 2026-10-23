package net.corevk.corevk.render;

import net.corevk.corevk.dh.DhDepth;
import net.corevk.corevk.pack.option.EngineDefines;
import net.corevk.corevk.render.pbr.PbrAtlases;
import net.corevk.corevk.uniform.BiomeCategory;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Util;
import net.minecraft.world.level.biome.Biomes;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class PackDefines {

	
	private static volatile long installed;

	
	private static volatile boolean distant;

	
	private static volatile EngineDefines.TextureFormat format;

	private PackDefines() {
	}

	
	public static void install() {
		// Beside the machine table because it is the same moment: a launch-wide answer the
		// translation has to hold before the first unit is read, and this is the one caller that
		// stands before every read with the game directory in reach.
		ShadowCompare.armIfAsked();
		EngineDefines.machine(gather());
		settle();
	}

	
	public static void settle() {
		installed = stamp();
		distant = DhDepth.present();
		format = PbrAtlases.format();
	}

	
	public static boolean stale() {
		return stamp() != installed || distantHorizonsMoved() || textureFormatMoved();
	}

	
	public static boolean textureFormatMoved() {
		return !Objects.equals(PbrAtlases.format(), format);
	}

	
	public static boolean distantHorizonsMoved() {
		return DhDepth.present() != distant;
	}

	
	public static boolean distantHorizons() {
		return distant;
	}

	private static long stamp() {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft == null ? null : minecraft.level;

		return level == null ? 0L : System.identityHashCode(level.registryAccess());
	}

	private static EngineDefines.Environment gather() {
		Minecraft minecraft = Minecraft.getInstance();
		GpuDevice device = RenderSystem.tryGetDevice();
		String vendor = "";
		String renderer = "";
		if (device != null) {
			// The device name is what OptiFine classifies as the renderer: the vendor string
			// names who made the chip, the device name names which one it is.
			vendor = device.getDeviceInfo().vendorName();
			renderer = device.getDeviceInfo().name();
		}

		int mipmap = minecraft == null ? 4 : minecraft.options.mipmapLevels().get();

		// Asked of the one class that knows whether the far terrain reaches the pack at all, and not
		// of the loader's mod list. An installed Distant Horizons this engine cannot get a depth
		// image or a projection out, or whose own rendering is switched off, leaves the far terrain
		// flat, and a pack told otherwise would light a picture that has none.
		// The material convention comes from the last stitch rather than from the file, which is the
		// same answer the reduction and the sampler already run on: a pack told one thing and a
		// specular map reduced under another would be two conventions in one picture.
		return new EngineDefines.Environment(EngineDefines.DEFAULT_MC_VERSION, os(), vendor,
				renderer, mipmap, DhDepth.present(), biomeIds(), categories(), PbrAtlases.format(),
				BufferBlending.served());
	}

	private static Map<String, Integer> biomeIds() {
		// Touches the game's class so that its registration has run by now: the table fills as
		// Biomes initialises, nothing on a client's boot path promises that has happened before
		// the first pack is read, and an empty table here would write no symbol and notice
		// nothing later. Initialising it is free of everything but interning some keys.
		Objects.requireNonNull(Biomes.THE_VOID);

		Map<String, Integer> ids = new LinkedHashMap<>();
		List<String> names = BiomeClassifier.names();
		for (int id = 0; id < names.size(); id++) {
			ids.put(names.get(id), id);
		}

		return ids;
	}

	
	private static List<String> categories() {
		return Arrays.stream(BiomeCategory.values())
				.map(category -> category.name().toUpperCase(Locale.ROOT))
				.toList();
	}

	private static EngineDefines.Os os() {
		return switch (Util.getPlatform()) {
			case WINDOWS -> EngineDefines.Os.WINDOWS;
			case OSX -> EngineDefines.Os.MAC;
			case LINUX -> EngineDefines.Os.LINUX;
			// Solaris has no OptiFine symbol of its own, so it lands on the same one as unknown.
			case SOLARIS, UNKNOWN -> EngineDefines.Os.OTHER;
		};
	}
}
