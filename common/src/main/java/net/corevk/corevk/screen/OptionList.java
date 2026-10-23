package net.corevk.corevk.screen;

import net.corevk.corevk.pack.menu.MenuOption;
import net.corevk.corevk.pack.menu.MenuPage;
import net.corevk.corevk.pack.menu.MenuSlot;
import net.corevk.corevk.pack.menu.PackMenu;
import net.corevk.corevk.ScreenText;
import net.corevk.corevk.CoreVK;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public final class OptionList extends WidgetList<OptionList.BaseEntry> {

	
	private static final int ROW_HEIGHT = 24;

	
	private static final int ROW_WIDTH = 400;
	private static final int ROW_MARGIN = 12;

	
	private static final Identifier IN_WORLD_BACKGROUND =
			Identifier.withDefaultNamespace("textures/gui/inworld_menu_background.png");

	private final ScreenHost host;
	private final List<PageWidget> cells = new ArrayList<>();

	public OptionList(ScreenHost host, Minecraft minecraft, int width, int height, int top) {
		super(minecraft, width, height, top + 4, ROW_HEIGHT);
		this.host = host;
	}

	@Override
	public int getRowWidth() {
		return Math.min(ROW_WIDTH, this.width - ROW_MARGIN);
	}

	@Override
	protected void extractListBackground(GuiGraphicsExtractor graphics) {
		Identifier texture = this.minecraft.level == null
				? Screen.MENU_BACKGROUND
				: IN_WORLD_BACKGROUND;
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture, getX(), getY(), getRight(),
				getBottom() + (int) scrollAmount(), getWidth(), getHeight(), 32, 32);
	}

	
	@Override
	protected void extractListSeparators(GuiGraphicsExtractor graphics) {
	}

	
	public void show(PackMenu menu, String pageName) {
		clearEntries();
		setScrollAmount(0);
		this.cells.clear();

		boolean sub = !pageName.isEmpty();
		MenuPage page = sub
				? menu.page(pageName).orElseGet(menu::main)
				: menu.main();
		// The pack's own name in bold on its first page, its own name for the page on any other, which
		// is what CoreVK puts here. A page the pack no longer lays out keeps the back button, since that
		// button is then the only way off it.
		Component heading = sub
				? ScreenText.fromPack(this.host.lang().page(pageName))
				: ScreenText.fromPack(menu.packName()).withStyle(ChatFormatting.BOLD);

		addEntry(new HeaderEntry(this.host, heading, sub));
		addCells(page.columns(), page.slots(), menu.profileNames());
	}

	
	public void refresh() {
		this.cells.forEach(cell -> cell.init(this.host));
	}

	private void addCells(int columns, List<MenuSlot> slots, List<String> profiles) {
		List<PageWidget> row = new ArrayList<>();
		for (MenuSlot slot : slots) {
			PageWidget cell = cellFor(slot, profiles);
			cell.init(this.host);
			this.cells.add(cell);
			row.add(cell);

			if (row.size() >= columns) {
				addEntry(new RowEntry(this.host, row));
				// A new list rather than a cleared one: the entry above keeps the one it was given.
				row = new ArrayList<>();
			}
		}

		if (row.isEmpty()) {
			return;
		}

		// Padded out to a full row, so that a last row of one does not draw one cell across the page.
		while (row.size() < columns) {
			row.add(new BlankWidget());
		}

		addEntry(new RowEntry(this.host, row));
	}

	private PageWidget cellFor(MenuSlot slot, List<String> profiles) {
		return switch (slot) {
			case MenuSlot.Blank _ -> new BlankWidget();
			case MenuSlot.Link(String page, boolean resolved) -> new LinkWidget(page, resolved);
			case MenuSlot.Profiles _ -> new ProfileWidget(profiles);
			case MenuSlot.Option(MenuOption option) -> switch (option.form()) {
				case TOGGLE -> new ToggleWidget(option);
				case CYCLE, FIXED -> option.slider()
						? new SliderWidget(option)
						: new ValueWidget(option);
			};
		};
	}

	
	public abstract static class BaseEntry extends ContainerObjectSelectionList.Entry<BaseEntry> {

		protected BaseEntry() {
		}
	}

	
	public static final class RowEntry extends BaseEntry {

		
		private static final int CELL_GAP = 2;
		private static final int ROW_TRIM = 3;

		private final ScreenHost host;
		private final List<PageWidget> cells;

		private int lastWidth = 1;
		private int lastX;

		RowEntry(ScreenHost host, List<PageWidget> cells) {
			this.host = host;
			this.cells = List.copyOf(cells);
		}

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
				boolean hovered, float a) {
			this.lastWidth = Math.max(1, getContentWidth());
			this.lastX = getContentX();

			int room = getContentWidth() - CELL_GAP * (this.cells.size() - 1) - ROW_TRIM;
			float each = (float) room / this.cells.size();

			for (int i = 0; i < this.cells.size(); i++) {
				PageWidget cell = this.cells.get(i);
				boolean under = (hovered && under(mouseX) == i) || getFocused() == cell;

				cell.place(new ScreenRectangle(getContentX() + (int) ((each + CELL_GAP) * i),
						getContentY(), (int) each, getContentHeight() + 2));
				cell.draw(graphics, mouseX, mouseY, under);

				this.host.hovered(cell, under);
			}
		}

		
		private int under(int mouseX) {
			float across = (float) Mth.clamp(mouseX - this.lastX, 0, this.lastWidth) / this.lastWidth;

			return Mth.clamp((int) Math.floor(this.cells.size() * across), 0, this.cells.size() - 1);
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
			return this.cells.get(under((int) event.x())).mouseClicked(event, doubleClick);
		}

		@Override
		public boolean mouseReleased(MouseButtonEvent event) {
			return this.cells.get(under((int) event.x())).mouseReleased(event);
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return this.cells;
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return this.cells;
		}
	}

	
	public static final class HeaderEntry extends BaseEntry {

		private static final Component BACK = Component.literal("< ")
				.append(CommonComponents.GUI_BACK.copy().withStyle(ChatFormatting.ITALIC));

		
		private static final MutableComponent RESET = Component
				.translatable(ScreenText.RESET).withStyle(ChatFormatting.YELLOW);

		private static final MutableComponent RESET_TOOLTIP = Component
				.translatable(ScreenText.RESET_TOOLTIP).withStyle(ChatFormatting.RED);
		private static final MutableComponent IMPORT_TOOLTIP = Component
				.translatable(ScreenText.IMPORT_TOOLTIP)
				.withStyle(style -> style.withColor(TextColor.fromRgb(0xFF4da6ff)));
		private static final MutableComponent EXPORT_TOOLTIP = Component
				.translatable(ScreenText.EXPORT_TOOLTIP)
				.withStyle(style -> style.withColor(TextColor.fromRgb(0xFFfc7d3d)));

		
		private static final int MIN_BUTTON_WIDTH = 42;
		private static final int BUTTON_HEIGHT = 16;

		
		private static final int ICON_BUTTON_WIDTH = 15;

		
		private static final int DIVIDER = 0x66BEBEBE;

		private final ScreenHost host;
		private final Component heading;
		private final @Nullable WidgetRow backRow;
		private final WidgetRow tools = new WidgetRow();
		private final WidgetRow.TextElement reset;
		private final WidgetRow.IconElement importButton;
		private final WidgetRow.IconElement exportButton;

		HeaderEntry(ScreenHost host, Component heading, boolean hasBack) {
			this.host = host;
			this.heading = heading;

			Font font = Minecraft.getInstance().font;
			this.backRow = hasBack
					? new WidgetRow().add(new WidgetRow.TextElement(BACK, _ -> back()),
							Math.max(MIN_BUTTON_WIDTH, font.width(BACK) + 8))
					: null;

			this.reset = new WidgetRow.TextElement(RESET, _ -> reset());
			this.importButton = new WidgetRow.IconElement(ScreenDraw.Icon.IMPORT,
					ScreenDraw.Icon.IMPORT_LIT, _ -> importSettings());
			this.exportButton = new WidgetRow.IconElement(ScreenDraw.Icon.EXPORT,
					ScreenDraw.Icon.EXPORT_LIT, _ -> exportSettings());

			this.tools
					.add(this.importButton, ICON_BUTTON_WIDTH)
					.add(this.exportButton, ICON_BUTTON_WIDTH)
					.add(this.reset, Math.max(MIN_BUTTON_WIDTH, font.width(RESET) + 8));
		}

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
				boolean hovered, float a) {
			int x = getX();
			int y = getY();
			int width = getWidth();
			int bottom = y + getHeight();

			graphics.fill(x - 3, bottom - 2, x + width, bottom - 1, DIVIDER);

			// Scrolled rather than cut, which is what the game does with a name that will not fit and
			// what keeps a pack's own long page names readable.
			graphics.textRenderer().acceptScrolling(this.heading, x + width / 2, x + 5,
					(x + width) - 10 - this.tools.width(), y + 5, y + 15);

			if (this.backRow != null) {
				this.backRow.draw(graphics, x, y, BUTTON_HEIGHT, mouseX, mouseY, a, hovered);
			}

			this.tools.drawRightAligned(graphics, (x + width) - 3, y, BUTTON_HEIGHT, mouseX, mouseY,
					a, hovered);

			Font font = Minecraft.getInstance().font;
			tooltip(graphics, font, this.reset, RESET_TOOLTIP);
			tooltip(graphics, font, this.importButton, IMPORT_TOOLTIP);
			tooltip(graphics, font, this.exportButton, EXPORT_TOOLTIP);
		}

		
		private void tooltip(GuiGraphicsExtractor graphics, Font font, WidgetRow.Element element,
				Component text) {
			if (!element.isHovered() && !element.isFocused()) {
				return;
			}

			ScreenRectangle at = element.getRectangle();
			this.host.onTop(() -> ScreenDraw.textPanel(font, graphics, text,
					at.right() - (font.width(text) + 10), at.position().y() - 16));
		}

		private boolean back() {
			this.host.back();
			ScreenDraw.clickSound();

			return true;
		}

		
		private boolean reset() {
			ScreenDraw.clickSound();
			this.host.resetSettings();

			return true;
		}

		
		private boolean importSettings() {
			ScreenDraw.clickSound();
			if (!canOpenDialog()) {
				return false;
			}

			Minecraft minecraft = Minecraft.getInstance();
			Screen asking = minecraft.gui.screen();
			Path origin = this.host.settingsFile();
			FileDialog
					.choose(FileDialog.Kind.OPEN, "Import Shader Settings from File", origin)
					.whenComplete((chosen, failed) -> {
						if (failed != null) {
							CoreVK.logger().error("CoreVK could not open the import dialog", failed);

							return;
						}

						chosen.ifPresent(file -> minecraft.execute(() -> {
							if (minecraft.gui.screen() == asking) {
								this.host.importSettings(file);
							}
						}));
					});

			return true;
		}

		
		private boolean exportSettings() {
			ScreenDraw.clickSound();
			if (!canOpenDialog()) {
				return false;
			}

			Path origin = this.host.settingsFile();
			FileDialog
					.choose(FileDialog.Kind.SAVE, "Export Shader Settings to File", origin)
					.whenComplete((chosen, failed) -> {
						if (failed != null) {
							CoreVK.logger().error("CoreVK could not open the export dialog", failed);

							return;
						}

						chosen.ifPresent(target -> copySettings(origin, target));
					});

			return true;
		}

		private static void copySettings(Path origin, Path target) {
			Properties settings = new Properties();
			if (Files.exists(origin)) {
				try (InputStream in = Files.newInputStream(origin)) {
					settings.load(in);
				} catch (IOException e) {
					CoreVK.logger().error("CoreVK could not read {}", origin, e);

					return;
				}
			}

			try (OutputStream out = Files.newOutputStream(target)) {
				settings.store(out, null);
			} catch (IOException e) {
				CoreVK.logger().error("CoreVK could not write {}", target, e);
			}
		}

		
		private boolean canOpenDialog() {
			if (!Desktop.fullscreen()) {
				return true;
			}

			this.host.announce(Component.translatable(ScreenText.FULLSCREEN)
					.withStyle(ChatFormatting.RED, ChatFormatting.BOLD));

			return false;
		}

		@Override
		public List<? extends GuiEventListener> children() {
			List<GuiEventListener> children = new ArrayList<>(this.tools.children());
			if (this.backRow != null) {
				children.addAll(this.backRow.children());
			}

			return children;
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
			boolean wentBack = this.backRow != null && this.backRow.mouseClicked(event, doubleClick);

			return wentBack || this.tools.mouseClicked(event, doubleClick);
		}

		@Override
		public boolean keyPressed(KeyEvent event) {
			if (this.backRow != null && this.backRow.keyPressed(event)) {
				return true;
			}

			return this.tools.keyPressed(event);
		}

		
		@Override
		public List<? extends NarratableEntry> narratables() {
			return List.of();
		}
	}
}
