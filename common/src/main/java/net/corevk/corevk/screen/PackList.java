package net.corevk.corevk.screen;

import net.corevk.corevk.pack.load.PackLoader;
import net.corevk.corevk.ScreenText;
import net.corevk.corevk.CoreVK;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.List;

public final class PackList extends AbstractSelectionList<PackList.BaseEntry> {

	private static final Component DROP_LABEL = Component
			.translatable(ScreenText.PACK_DROP)
			.withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY);

	
	private static final Identifier IN_WORLD_BACKGROUND =
			Identifier.withDefaultNamespace("textures/gui/inworld_menu_background.png");

	
	private static final float INVISIBLE = 0.02F;

	
	private static final float FAINTEST = 0.01F;

	
	private static final URI CURSEFORGE_PACKS =
			URI.create("https://www.curseforge.com/minecraft/search?class=shaders");
	private static final URI MODRINTH_PACKS = URI.create("https://modrinth.com/shaders");

	private static final int ROW_HEIGHT = 20;

	
	private static final int SCROLLBAR_INSET = 6;

	
	private static final int ROW_WIDTH = 308;
	private static final int ROW_MARGIN = 50;

	
	private static final int ROW_NUDGE = 2;

	
	private static final int TEXT_HEIGHT = 11;

	private static final int WHITE = 0xFFFFFFFF;
	private static final int LABEL_GREY = 0xFFC2C2C2;

	
	private static final int APPLIED_YELLOW = 0xFFFFF263;

	
	private static final int DISABLED_GREY = 0xFFA2A2A2;

	private final PackHost host;
	private final Path gameDirectory;
	private final ToggleEntry toggleRow;
	private final @Nullable WatchService watcher;
	private final @Nullable WatchKey key;

	private boolean watching;
	private @Nullable PackEntry applied;

	public PackList(PackHost host, Minecraft minecraft, Path gameDirectory, String chosen,
			boolean shadersEnabled, int width, int height, int top) {
		super(minecraft, width, height, top + 4, ROW_HEIGHT);
		this.host = host;
		this.gameDirectory = gameDirectory;
		this.toggleRow = new ToggleEntry(this, shadersEnabled);

		WatchService service = null;
		WatchKey registered = null;
		try {
			service = FileSystems.getDefault().newWatchService();
			registered = PackLoader.directory(gameDirectory).register(service,
					StandardWatchEventKinds.ENTRY_CREATE,
					StandardWatchEventKinds.ENTRY_MODIFY,
					StandardWatchEventKinds.ENTRY_DELETE);
			this.watching = true;
		} catch (IOException | RuntimeException e) {
			// A folder that cannot be watched is not a broken screen: the list is built once either
			// way, and what is lost is a pack dropped in while it is open appearing on its own.
			CoreVK.logger().warn("CoreVK could not watch {} for new packs",
					PackLoader.directory(gameDirectory), e);
			this.watching = false;
		}

		this.watcher = service;
		this.key = registered;

		refresh(chosen);
	}

	@Override
	protected int scrollBarX() {
		return this.width - SCROLLBAR_INSET;
	}

	
	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
	}

	@Override
	public int getRowWidth() {
		return Math.min(ROW_WIDTH, this.width - ROW_MARGIN);
	}

	@Override
	public int getRowTop(int row) {
		return super.getRowTop(row) + ROW_NUDGE;
	}

	
	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isUp() && getFocused() == children().getFirst()) {
			return true;
		}

		return super.keyPressed(event);
	}

	
	@Override
	public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
			float a) {
		if (this.watching && this.key != null) {
			for (WatchEvent<?> event : this.key.pollEvents()) {
				if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
					continue;
				}

				refresh(chosenName());
				break;
			}

			// A key that cannot be reset is a folder that went away, and nothing is watched after that.
			this.watching = this.key.reset();
		}

		super.extractWidgetRenderState(graphics, mouseX, mouseY, a);
	}

	
	public void close() throws IOException {
		if (this.key != null) {
			this.key.cancel();
		}

		if (this.watcher != null) {
			this.watcher.close();
		}
	}

	@Override
	protected void extractListBackground(GuiGraphicsExtractor graphics) {
		float alpha = this.host.listAlpha();
		if (alpha < INVISIBLE) {
			return;
		}

		Identifier texture = this.minecraft.level == null
				? Screen.MENU_BACKGROUND
				: IN_WORLD_BACKGROUND;
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture, getX(), getY(), getRight(),
				getBottom() + (int) scrollAmount(), getWidth(), getHeight(), 32, 32);
	}

	@Override
	protected void extractListSeparators(GuiGraphicsExtractor graphics) {
		float alpha = this.host.listAlpha();
		if (alpha < INVISIBLE) {
			return;
		}

		// The fade reaches the blit's own colour, which is the one place on this screen where it is
		// honoured at all: everything else CoreVK hands it to is a widget alpha nothing reads.
		int tint = ARGB.colorFromFloat(Math.max(alpha, FAINTEST), 1.0F, 1.0F, 1.0F);
		boolean inWorld = this.minecraft.level != null;
		Identifier header = inWorld ? Screen.INWORLD_HEADER_SEPARATOR : Screen.HEADER_SEPARATOR;
		Identifier footer = inWorld ? Screen.INWORLD_FOOTER_SEPARATOR : Screen.FOOTER_SEPARATOR;

		graphics.blit(RenderPipelines.GUI_TEXTURED, header, getX(), getY() - 2, 0.0F, 0.0F,
				getWidth(), 2, 32, 2, tint);
		graphics.blit(RenderPipelines.GUI_TEXTURED, footer, getX(), getBottom(), 0.0F, 0.0F,
				getWidth(), 2, 32, 2, tint);
	}

	
	public void refresh(String chosen) {
		clearEntries();

		List<Path> packs;
		try {
			packs = PackLoader.candidates(this.gameDirectory);
		} catch (IOException | RuntimeException e) {
			CoreVK.logger().error("CoreVK could not list {}",
					PackLoader.directory(this.gameDirectory), e);
			addEntry(this.toggleRow);
			this.toggleRow.packsPresent = false;
			// Not translated, and CoreVK gives the reason for leaving its own untranslated: this is seen
			// very rarely, and what it is really for is getting the reader to the log, where the cause
			// is. Usually a folder this game cannot read.
			addEntry(new LabelEntry(Component
					.literal("Your shader pack folder could not be read. See the log.")
					.withStyle(ChatFormatting.RED)));

			return;
		}

		addEntry(this.toggleRow);
		// CoreVK offers somewhere to get a pack when there is none, and the toggle is dead for the same
		// reason: switching shaders on with an empty folder would do nothing and say nothing.
		this.toggleRow.packsPresent = !packs.isEmpty();
		if (packs.isEmpty()) {
			// Untranslated, as CoreVK leaves its own: the address it opens is in English either way.
			addEntry(new PinnedEntry(Component.literal("Download Shaders from CurseForge"),
					() -> openPackSite(CURSEFORGE_PACKS)));
			addEntry(new PinnedEntry(Component.literal("Download Shaders from Modrinth"),
					() -> openPackSite(MODRINTH_PACKS)));
		}

		PackEntry selected = null;
		for (Path pack : packs) {
			String name = pack.getFileName().toString();
			PackEntry entry = new PackEntry(this, name);
			addEntry(entry);

			if (name.equals(chosen)) {
				selected = entry;
			}
		}

		if (selected != null) {
			setSelected(selected);
			setFocused(selected);
			centerScrollOn(selected);
			this.applied = selected;
		}

		addEntry(new LabelEntry(DROP_LABEL));
	}

	
	public String chosenName() {
		return getSelected() instanceof PackEntry entry ? entry.packName : "";
	}

	
	public boolean shadersEnabled() {
		return this.toggleRow.shadersEnabled;
	}

	
	public boolean packsPresent() {
		return this.toggleRow.packsPresent;
	}

	
	public void markSelectedApplied() {
		this.applied = getSelected() instanceof PackEntry entry ? entry : null;
	}

	
	public void select(String name) {
		for (BaseEntry entry : children()) {
			if (entry instanceof PackEntry pack && pack.packName.equals(name)) {
				setSelected(entry);

				return;
			}
		}
	}

	
	private void openPackSite(URI address) {
		ConfirmLinkScreen.confirmLinkNow(this.minecraft.gui.screen(), address, true);
	}

	
	public abstract static class BaseEntry extends AbstractSelectionList.Entry<BaseEntry> {

		protected BaseEntry() {
		}
	}

	
	public static final class LabelEntry extends BaseEntry {

		private final Component label;

		LabelEntry(Component label) {
			this.label = label;
		}

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
				boolean hovered, float a) {
			graphics.centeredText(Minecraft.getInstance().font, this.label,
					getContentX() + getContentWidth() / 2 - 2,
					getContentY() + (getContentHeight() - TEXT_HEIGHT) / 2, LABEL_GREY);
		}
	}

	
	public static final class ToggleEntry extends BaseEntry {

		private final PackList list;

		private boolean shadersEnabled;
		private boolean packsPresent = true;

		ToggleEntry(PackList list, boolean shadersEnabled) {
			this.list = list;
			this.shadersEnabled = shadersEnabled;
		}

		private void setShadersEnabled(boolean shadersEnabled) {
			this.shadersEnabled = shadersEnabled;
			this.list.host.shadersToggled();
		}

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
				boolean hovered, float a) {
			int x = getContentX();
			int y = getContentY();
			int width = getContentWidth();
			int height = getContentHeight();

			ScreenDraw.button(graphics, x - 2, y - 2, width, height + 2, hovered, !this.packsPresent);
			graphics.centeredText(Minecraft.getInstance().font, label(), x + width / 2 - 2,
					y + (height - TEXT_HEIGHT) / 2, WHITE);
		}

		private Component label() {
			if (!this.packsPresent) {
				return Component.translatable(ScreenText.SHADERS_NONE_PRESENT)
						.withStyle(ChatFormatting.GRAY);
			}

			return Component.translatable(this.shadersEnabled
					? ScreenText.SHADERS_ENABLED
					: ScreenText.SHADERS_DISABLED);
		}

		private boolean press() {
			if (!this.packsPresent) {
				return false;
			}

			setShadersEnabled(!this.shadersEnabled);
			ScreenDraw.clickSound();

			return true;
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
			return press();
		}

		@Override
		public boolean keyPressed(KeyEvent event) {
			return event.isConfirmation() && press();
		}

		@Override
		public @Nullable ComponentPath nextFocusPath(FocusNavigationEvent event) {
			return isFocused() ? null : ComponentPath.leaf(this);
		}

		@Override
		public boolean isFocused() {
			return this.list.getFocused() == this;
		}
	}

	
	public static final class PinnedEntry extends BaseEntry {

		private final Component label;
		private final Runnable action;

		PinnedEntry(Component label, Runnable action) {
			this.label = label;
			this.action = action;
		}

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
				boolean hovered, float a) {
			int x = getContentX();
			int y = getContentY();
			int width = getContentWidth();
			int height = getContentHeight();

			ScreenDraw.button(graphics, x - 2, y - 2, width, height + 2, hovered, false);
			graphics.centeredText(Minecraft.getInstance().font, this.label, x + width / 2 - 2,
					y + (height - TEXT_HEIGHT) / 2, WHITE);
		}

		private boolean press() {
			ScreenDraw.clickSound();
			this.action.run();

			return true;
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
			return press();
		}

		@Override
		public boolean keyPressed(KeyEvent event) {
			return event.isConfirmation() && press();
		}
	}

	
	public static final class PackEntry extends BaseEntry {

		private final PackList list;
		private final String packName;

		private ScreenRectangle bounds = ScreenRectangle.empty();

		PackEntry(PackList list, String packName) {
			this.list = list;
			this.packName = packName;
		}

		public String packName() {
			return this.packName;
		}

		@Override
		public ScreenRectangle getRectangle() {
			return this.bounds;
		}

		private boolean isApplied() {
			return this.list.applied == this;
		}

		private boolean isSelected() {
			return this.list.getSelected() == this;
		}

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
				boolean hovered, float a) {
			int x = getContentX();
			int y = getContentY();
			int width = getContentWidth();
			int height = getContentHeight();
			this.bounds = new ScreenRectangle(x, y, width, height);

			if (hovered) {
				ScreenDraw.button(graphics, x - 2, y - 2, width + 4, height + 4, true, false);
			}

			Font font = Minecraft.getInstance().font;
			boolean enabled = this.list.toggleRow.shadersEnabled;
			boolean under = isMouseOver(mouseX, mouseY);

			// Measured against the bold form so that a name does not grow past the row the moment the
			// mouse is over it, and cut with the ellipsis written out rather than through the shared
			// helper, which is what CoreVK does here.
			String name = this.packName;
			if (font.width(Component.literal(name).withStyle(ChatFormatting.BOLD))
					> this.list.getRowWidth() - 3) {
				name = font.plainSubstrByWidth(name, this.list.getRowWidth() - 8) + "...";
			}

			MutableComponent text = Component.literal(name);
			if (under) {
				text = text.withStyle(ChatFormatting.BOLD);
			}

			int color = WHITE;
			if (enabled && isApplied()) {
				color = APPLIED_YELLOW;
			}

			if (!enabled && !under) {
				color = DISABLED_GREY;
			}

			graphics.centeredText(font, text, x + width / 2 - 2,
					y + (height - TEXT_HEIGHT) / 2, color);
		}

		
		private boolean press() {
			boolean did = false;
			if (!this.list.toggleRow.shadersEnabled) {
				this.list.toggleRow.setShadersEnabled(true);
				did = true;
			}

			if (!isSelected()) {
				// Itself, where CoreVK looks its own row up by an index it was built with,
				// ShaderPackSelectionList.java:524. The index is the position among the list's rows
				// rather than among the packs, so it counts the toggle above them, and it would count
				// wrong the day another row joins it. The row is already the answer.
				this.list.setSelected(this);
				did = true;
			}

			this.list.host.packChosen();

			return did;
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
			return event.button() == InputConstants.MOUSE_BUTTON_LEFT && press();
		}

		@Override
		public boolean keyPressed(KeyEvent event) {
			return event.isConfirmation() && press();
		}

		@Override
		public @Nullable ComponentPath nextFocusPath(FocusNavigationEvent event) {
			return isFocused() ? null : ComponentPath.leaf(this);
		}

		@Override
		public boolean isFocused() {
			return this.list.getFocused() == this;
		}
	}
}
