package net.corevk.corevk.screen;

import net.corevk.corevk.HostReport;
import net.corevk.corevk.CoreVK;
import net.corevk.corevk.render.PackChain;
import net.corevk.corevk.render.PackChoice;
import net.corevk.corevk.settings.PackSession;
import net.corevk.corevk.sodium.ShadowTerrain;

import net.minecraft.client.Minecraft;

import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

import org.jspecify.annotations.Nullable;

public final class CoreVKDebugEntry implements DebugScreenEntry {

	public static final Identifier ID = Identifier.fromNamespaceAndPath(CoreVK.MOD_ID, CoreVK.MOD_ID);

	private static final Identifier GROUP = Identifier.fromNamespaceAndPath(CoreVK.MOD_ID, "name");

	private static final String PREFIX = "[" + CoreVK.MOD_NAME + "] ";

	private static PackSession profiledSession;
	private static String profileLine = "";

	@Override
	public void display(DebugScreenDisplayer displayer, @Nullable Level level,
			@Nullable LevelChunk clientChunk, @Nullable LevelChunk serverChunk) {
		if (HostReport.otherBackend()) {
			return;
		}

		displayer.addToGroup(GROUP, PREFIX + "Version: " + CoreVK.platform().modVersion());
		PackChain.compilingWords().ifPresent(words ->
				displayer.addToGroup(GROUP, PREFIX + words.getString()));

		PackChoice.session().ifPresentOrElse(session -> {
			displayer.addToGroup(GROUP, PREFIX + "Shaderpack: " + session.packFileName());
			displayer.addToGroup(GROUP, profileLine(session));
			if (level != null) {
				shadowLines(displayer);
			}
		}, () -> displayer.addToGroup(GROUP, PREFIX + undrawnLine()));
	}

	private static String profileLine(PackSession session) {
		if (session != profiledSession) {
			profiledSession = session;
			profileLine = PREFIX + session.profileInfo();
		}

		return profileLine;
	}

	private static void shadowLines(DebugScreenDisplayer displayer) {
		ShadowTerrain.Walk walk = ShadowTerrain.lastWalk();
		int drawn = walk == null ? 0 : walk.drawn();
		int total = walk == null ? 0 : walk.total();
		displayer.addToGroup(GROUP, PREFIX + "Shadows: C: " + drawn + "/" + total
				+ " D: " + Minecraft.getInstance().options.getEffectiveRenderDistance());
	}

	private static String undrawnLine() {
		if (PackChoice.noPackWanted()) {
			return "Shaders are disabled";
		}
		if (PackChoice.packMissing()) {
			return "Shaderpack: " + PackChoice.askedFor().name() + " (missing from the folder)";
		}
		return PackChoice.lastError()
				.map(error -> "Shaderpack refused: " + error)
				.orElse("Shaderpack: none");
	}
}
