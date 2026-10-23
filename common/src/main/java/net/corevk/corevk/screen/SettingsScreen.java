package net.corevk.corevk.screen;

import net.corevk.corevk.pack.load.PackLoader;
import net.corevk.corevk.pack.menu.MenuValues;
import net.corevk.corevk.pack.source.PackLang;
import net.corevk.corevk.render.PackChoice;
import net.corevk.corevk.ScreenText;
import net.corevk.corevk.settings.PackFile;
import net.corevk.corevk.settings.PackSession;
import net.corevk.corevk.settings.SettingsFile;
import net.corevk.corevk.uniform.Smoothed;
import net.corevk.corevk.CoreVK;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Stream;

public final class SettingsScreen extends Screen implements PackHost, ScreenHost {

	
	private static final int LIST_TOP = 32;
	private static final int FOOTER_ROOM = 94;

	
	private static final int BUTTON_WIDTH = 100;
	private static final int BUTTON_HEIGHT = 20;
	private static final int BUTTON_PITCH = 104;

	
	private static final int WIDE_BUTTON_WIDTH = 152;
	private static final int WIDE_BUTTON_PITCH = 78;

	
	private static final int EYE_SIZE = 20;
	private static final int EYE_V = 146;

	private static final int COMMENT_PANEL_WIDTH = 314;

	
	private static final int NOTIFICATION_TICKS = 100;

	
	private static final int COMMENT_DELAY = 10;

	
	private static final float BLUR_HALF_LIFE = 2.0F;
	private static final float LIST_HALF_LIFE = 1.0F;

	
	private static final float OPTIONS_BLUR = 0.1F;

	private static final int WHITE = 0xFFFFFFFF;

	private static final Component SELECT_TITLE = Component
			.translatable(ScreenText.SELECT_TITLE)
			.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);

	private static final Component CONFIGURE_TITLE = Component
			.translatable(ScreenText.CONFIGURE_TITLE)
			.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);

	private final @Nullable Screen parent;

	
	private final Component nameLine;

	
	private final Deque<String> history = new ArrayDeque<>();

	
	private final List<Runnable> topLayer = new ArrayList<>();

	private final Smoothed blurFade = new Smoothed();
	private final Smoothed listFade = new Smoothed();

	private @Nullable PackList packList;
	private @Nullable OptionList optionList;
	private @Nullable PackSession session;
	private @Nullable MenuValues values;
	private @Nullable Button viewSwitch;
	private @Nullable Button folderButton;
	private @Nullable Button eyeButton;

	private @Nullable String error;

	private @Nullable Component notification;
	private int notificationTicks;

	private @Nullable PageWidget hoveredCell;
	private Optional<Component> commentTitle = Optional.empty();
	private List<FormattedCharSequence> commentBody = List.of();
	private int commentTicks;

	
	private String page = "";

	private boolean optionsOpen;

	
	private boolean previewing;
	private boolean guiHidden;
	private boolean dropChanges;

	
	private boolean started;

	private float blurAlpha;
	private float listAlpha;
	private long lastFrame;

	public SettingsScreen(@Nullable Screen parent) {
		super(Component.translatable(ScreenText.PACKS_TITLE));
		this.parent = parent;
		this.nameLine = Component.literal(CoreVK.MOD_NAME + " " + CoreVK.platform().modVersion())
				.withStyle(ChatFormatting.GRAY);
		adopt(PackChoice.session().orElse(null));
		// Whatever the file says when there is no pack: the other view would be an empty page, and the
		// list is where one is picked.
		this.optionsOpen = !PackChoice.opensOnPacks() && this.session != null;
	}

	@Override
	protected void init() {
		int bottomCentre = this.width / 2 - BUTTON_WIDTH / 2;
		int topCentre = this.width / 2 - WIDE_BUTTON_WIDTH / 2;
		int listTop = LIST_TOP + 4;
		int listHeight = Math.max(0, this.height - FOOTER_ROOM - listTop);

		// Rebuilt rather than resized, which is what CoreVK does, with the pack the list had kept
		// selected carried across: a resize that lost it would drop a pack chosen and not yet applied.
		String chosen = this.packList == null
				? PackChoice.askedFor().name()
				: this.packList.chosenName();
		boolean enabled = this.packList == null
				? PackChoice.askedFor().enabled()
				: this.packList.shadersEnabled();

		// Let go of the old folder watcher first. CoreVK does not, and leaks a watch key per resize.
		closePackList();
		this.packList = new PackList(this, this.minecraft, gameDirectory(), chosen, enabled,
				this.width, listHeight, LIST_TOP);

		PackSession loaded = this.session;
		if (loaded == null) {
			this.optionsOpen = false;
			this.optionList = null;
		} else {
			this.optionList = new OptionList(this, this.minecraft, this.width, listHeight, LIST_TOP);
			this.optionList.show(loaded.menu(), this.page);
		}

		this.clearWidgets();

		if (!this.guiHidden) {
			addRenderableWidget(this.optionsOpen && this.optionList != null
					? this.optionList
					: this.packList);

			addRenderableWidget(PanelButton.of(bottomCentre - BUTTON_PITCH, this.height - 27,
					BUTTON_WIDTH, BUTTON_HEIGHT, CommonComponents.GUI_CANCEL,
					this::dropChangesAndClose));
			addRenderableWidget(PanelButton.of(bottomCentre, this.height - 27, BUTTON_WIDTH,
					BUTTON_HEIGHT, Component.translatable(ScreenText.APPLY), this::applyChanges));
			addRenderableWidget(PanelButton.of(bottomCentre + BUTTON_PITCH, this.height - 27,
					BUTTON_WIDTH, BUTTON_HEIGHT, CommonComponents.GUI_DONE, this::onClose));

			this.folderButton = addRenderableWidget(PanelButton.of(
					topCentre - WIDE_BUTTON_PITCH, this.height - 51, WIDE_BUTTON_WIDTH,
					BUTTON_HEIGHT, Component.translatable(ScreenText.FOLDER), this::openFolder));
			this.viewSwitch = addRenderableWidget(PanelButton.of(
					topCentre + WIDE_BUTTON_PITCH, this.height - 51, WIDE_BUTTON_WIDTH,
					BUTTON_HEIGHT, Component.empty(), this::switchView));
			refreshViewSwitch();

			// In a world only, exactly like the eye it mirrors. Out of one no pack is drawing, so a
			// reading changes no image; it still rebuilds this screen from what it read, so what is
			// left out there is the loop this button exists for and not the whole of its effect.
			if (this.minecraft.level != null) {
				addRenderableWidget(reload());
			}
		}

		if (this.minecraft.level != null) {
			this.eyeButton = addRenderableWidget(eye());
		} else {
			this.eyeButton = null;
		}

		// Never let a comment panel outlive the page it belonged to, which is CoreVK's own fix for a
		// panel that stayed up after the screen had moved on.
		this.hoveredCell = null;
		this.commentTicks = 0;
	}

	
	private Button reload() {
		return PanelButton.icon(besideRows(true), this.height - 39, EYE_SIZE, ScreenDraw.Icon.REFRESH,
				Component.translatable(ScreenText.RELOAD), () -> {
					this.error = null;
					reloadPack();
				});
	}

	
	private int besideRows(boolean onTheLeft) {
		float edge = this.width / 2.0F + (onTheLeft ? -154.0F : 154.0F);
		float free = onTheLeft ? edge : this.width - edge;

		if (free > 100.0F) {
			return onTheLeft ? 50 - EYE_SIZE : this.width - 50;
		}

		if (free < 20.0F) {
			return onTheLeft ? 0 : this.width - EYE_SIZE;
		}

		return (int) (onTheLeft ? free / 2.0F : edge + free / 2.0F) - EYE_SIZE / 2;
	}

	
	private Button eye() {
		int x = besideRows(false);

		Component label = Component
				.translatable(this.guiHidden ? ScreenText.GUI_SHOW : ScreenText.GUI_HIDE);
		Button button = TextureButton.of(x, this.height - 39, EYE_SIZE, EYE_SIZE,
				this.guiHidden ? EYE_SIZE : 0, EYE_V, EYE_SIZE, ScreenDraw.WIDGETS, label,
				this::toggleHidden);
		button.setTooltip(Tooltip.create(label));
		// Ten seconds, which is CoreVK's: the tooltip would otherwise cover the world this button exists
		// to uncover.
		button.setTooltipDelay(Duration.ofSeconds(10));

		return button;
	}

	@Override
	protected void extractBlurredBackground(GuiGraphicsExtractor graphics) {
		float wanted = (float) this.minecraft.options.getMenuBackgroundBlurriness();
		if (Math.min(wanted, this.blurAlpha) >= 1.0F) {
			graphics.blurBeforeThisStratum();
		}
	}

	
	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		syncWithLoadedPack();
		advanceFades();
		this.started = true;

		if (this.guiHidden) {
			if (this.eyeButton != null) {
				this.eyeButton.extractRenderState(graphics, mouseX, mouseY, a);
			}
		} else {
			super.extractRenderState(graphics, mouseX, mouseY, a);

			graphics.centeredText(this.font, this.title, this.width / 2, 8, WHITE);
			graphics.centeredText(this.font, subtitle(), this.width / 2, 21, WHITE);
			drawComment(graphics);
		}

		// After every list entry, and before the lines at the bottom left, which nothing may cover.
		for (Runnable draw : this.topLayer) {
			draw.run();
		}

		this.topLayer.clear();

		// CoreVK's arrangement, ShaderPackScreen.java:218-226: the name and version on the bottom line,
		// pushed up one by whatever has to be said under them.
		Component note = engineNote();
		if (note.getString().isEmpty()) {
			graphics.text(this.font, this.nameLine, 2, this.height - 10, WHITE);
		} else {
			graphics.text(this.font, note, 2, this.height - 10, WHITE);
			graphics.text(this.font, this.nameLine, 2, this.height - 20, WHITE);
		}
	}

	private void advanceFades() {
		long now = Util.getMillis();
		float dt = this.lastFrame == 0L ? 0.0F : (now - this.lastFrame) / 1000.0F;
		this.lastFrame = now;

		float blur;
		if (this.guiHidden) {
			blur = 0.0F;
		} else if (this.optionsOpen) {
			blur = OPTIONS_BLUR;
		} else {
			blur = (float) this.minecraft.options.getMenuBackgroundBlurriness();
		}

		this.blurAlpha = this.blurFade.updateAndGet(blur, BLUR_HALF_LIFE, BLUR_HALF_LIFE, dt);

		float list = this.guiHidden || this.optionsOpen || !this.started ? 0.0F : 1.0F;
		this.listAlpha = this.listFade.updateAndGet(list, LIST_HALF_LIFE, LIST_HALF_LIFE, dt);
	}

	
	private Component subtitle() {
		Component said = this.notification;
		if (said != null && this.notificationTicks > 0) {
			return said;
		}

		return this.optionsOpen ? CONFIGURE_TITLE : SELECT_TITLE;
	}

	
	private Component engineNote() {
		String failed = this.error == null ? PackChoice.lastError().orElse(null) : this.error;
		if (failed != null) {
			return Component.translatable(ScreenText.ERROR, failed)
					.withStyle(ChatFormatting.RED);
		}

		if (this.session == null) {
			return Component
					.translatable(PackChoice.noPackWanted() ? ScreenText.PACK_OFF : ScreenText.NO_PACK)
					.withStyle(ChatFormatting.GRAY);
		}

		MenuValues current = this.values;
		int forced = current == null ? 0 : current.forcedShown();
		if (forced > 0) {
			return Component.translatable(ScreenText.FORCED, forced)
					.withStyle(ChatFormatting.GOLD);
		}

		List<String> gone = PackChoice.removedPasses();

		return gone.isEmpty()
				? Component.empty()
				: Component.translatable(ScreenText.REMOVED, gone.size())
						.withStyle(ChatFormatting.GRAY);
	}

	private void drawComment(GuiGraphicsExtractor graphics) {
		if (!showingComment()) {
			return;
		}

		int panelHeight = Math.max(50, 18 + this.commentBody.size() * 10);
		int x = this.width / 2 - 157;
		int y = this.height - (panelHeight + 4);

		ScreenDraw.panel(graphics, x, y, COMMENT_PANEL_WIDTH, panelHeight);
		graphics.text(this.font, this.commentTitle.orElse(Component.empty()), x + 4, y + 4, WHITE);
		for (int i = 0; i < this.commentBody.size(); i++) {
			graphics.text(this.font, this.commentBody.get(i), x + 4, y + 16 + i * 10, WHITE);
		}
	}

	@Override
	public void tick() {
		super.tick();

		if (this.notificationTicks > 0) {
			this.notificationTicks--;
		}

		if (this.hoveredCell == null) {
			this.commentTicks = 0;
		} else {
			this.commentTicks++;
		}
	}

	
	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isEscape()) {
			if (this.guiHidden) {
				toggleHidden();

				return true;
			}

			if (!this.history.isEmpty()) {
				back();

				return true;
			}

			if (this.optionsOpen) {
				this.optionsOpen = false;
				rebuildWidgets();

				return true;
			}
		} else if (event.isCycleFocus()) {
			PackList list = this.packList;
			if (!this.optionsOpen && list != null) {
				// CoreVK presses the focused row before switching, so that Tab from the list opens the
				// settings of the pack the keyboard is on rather than of the pack that was applied.
				// Return is named through the game's own table rather than by number, since the game
				// asks whether a key confirms against that same table, and the number is a GLFW key
				// under 26.2 and an SDL scancode under 26.3. F1 below is named the same way for the
				// same reason.
				list.keyPressed(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
			}

			switchView();
			// Cleared after the rebuild put it back, so that the Tab falling through below walks the
			// new view from its start rather than from whatever the rebuild happened to focus.
			setFocused(null);
		} else if (event.key() == InputConstants.KEY_F1 && this.minecraft.level != null) {
			toggleHidden();

			return true;
		}

		// Nothing else reaches a screen that is not being drawn.
		return this.guiHidden || super.keyPressed(event);
	}

	private void toggleHidden() {
		this.guiHidden = !this.guiHidden;
		rebuildWidgets();
	}

	
	private void switchView() {
		this.optionsOpen = !this.optionsOpen;
		if (this.optionsOpen) {
			previewChosen();
		}

		rebuildWidgets();
	}

	
	private void previewChosen() {
		PackList list = this.packList;
		if (list == null) {
			return;
		}

		String chosen = list.chosenName();
		PackSession loaded = PackChoice.session().orElse(null);
		if (chosen.isEmpty() || (loaded != null && chosen.equals(loaded.packFileName()))) {
			this.previewing = false;
			if (loaded != null) {
				adopt(loaded);
			}

			return;
		}

		try {
			Path path = PackLoader.directory(gameDirectory()).resolve(chosen);
			PackSession preview = PackSession.read(gameDirectory(), path,
					this.minecraft == null ? "en_us" : this.minecraft.options.languageCode);
			if (preview.menu().main().slots().isEmpty()) {
				announce(Component.translatable(ScreenText.NO_SETTINGS, preview.packFileName()));
				giveUp(loaded);

				return;
			}

			this.previewing = true;
			adopt(preview);
			this.error = null;
		} catch (IOException | RuntimeException e) {
			this.error = String.valueOf(e.getMessage());
			CoreVK.logger().error("CoreVK could not read {} to show its settings", chosen, e);
			giveUp(loaded);
		}
	}

	
	private void giveUp(@Nullable PackSession loaded) {
		this.optionsOpen = false;
		this.previewing = false;
		adopt(loaded);
	}

	private void refreshViewSwitch() {
		Button button = this.viewSwitch;
		if (button == null) {
			return;
		}

		button.setMessage(Component
				.translatable(this.optionsOpen ? ScreenText.PACKS : ScreenText.TITLE));
		// Live for the pack the LIST has selected, and not for the pack that happens to be loaded.
		// Asking the loaded one was a circle: the first pack anybody ever picks has never been read,
		// so nothing said it had settings, so the button that would have read it stayed dead, and
		// the only way out was to load the pack at its own default profile first, which is exactly
		// what reading without loading exists to avoid.
		//
		// A pack this screen has already read answers for itself, and a pack it has not is taken to
		// have settings until the reading says otherwise. Being wrong that way costs one click and
		// one sentence; being wrong the other way is the circle above.
		PackList list = this.packList;
		PackSession shown = this.session;
		String chosen = list == null ? "" : list.chosenName();
		boolean unread = !chosen.isEmpty() && (shown == null || !chosen.equals(shown.packFileName()));
		boolean laysSomethingOut = shown != null && !shown.menu().main().slots().isEmpty();
		boolean anythingToConfigure = (list == null || list.shadersEnabled())
				&& (unread || laysSomethingOut);
		button.active = this.optionsOpen || anythingToConfigure;
		// Dead only where this screen KNOWS there is nothing, and it says which nothing on hover: a
		// folder with no pack in it, a pack turned off on purpose, a name the folder has not got, and
		// a pack that was read and lays nothing out are four different answers.
		button.setTooltip(button.active ? null : Tooltip.create(nothingToConfigure()));
	}

	
	private Component nothingToConfigure() {
		PackList list = this.packList;
		if (list != null && !list.shadersEnabled()) {
			// The toggle's own words, which is what makes the connection obvious: the sentence on
			// the dead button is the one written on the row right above it.
			return Component.translatable(ScreenText.SHADERS_DISABLED);
		}

		PackSession shown = this.session;
		if (shown != null) {
			return Component.translatable(ScreenText.NO_SETTINGS, shown.packFileName());
		}

		return noPackReason();
	}

	
	private static Component noPackReason() {
		if (PackChoice.packMissing()) {
			return Component.translatable(ScreenText.PACK_MISSING, PackChoice.askedFor().name());
		}

		return Component.translatable(
				PackChoice.noPackWanted() ? ScreenText.PACK_OFF : ScreenText.NO_PACK);
	}

	@Override
	public float listAlpha() {
		return this.listAlpha;
	}

	
	public int blurRadius() {
		return (int) this.blurAlpha;
	}

	@Override
	public void shadersToggled() {
		refreshViewSwitch();
	}

	@Override
	public void packChosen() {
		setFocused(this.folderButton);
		// A preview is of one pack, and the selection has left it: what was set on it would be
		// written into that pack's file by an Apply aimed at another, and the bottom line and its
		// forced count would go on describing a pack that is neither drawing nor chosen. Dropped
		// here, at the click, rather than repaired on the way back to the options view.
		PackList list = this.packList;
		PackSession shown = this.session;
		if (this.previewing && list != null && shown != null
				&& !list.chosenName().equals(shown.packFileName())) {
			giveUp(PackChoice.session().orElse(null));
		}

		// The switch is live for what the list has selected, so it has to hear that the selection
		// moved. Nothing else rebuilds this screen when a row is clicked, and without this the very
		// first pack picked on a fresh install left the button dead until something else did.
		refreshViewSwitch();
	}

	@Override
	public MenuValues values() {
		// Only ever reached from a cell, and a cell only exists when a pack was read.
		return Objects.requireNonNull(this.values, "no pack is loaded");
	}

	@Override
	public PackLang lang() {
		PackSession loaded = this.session;

		return loaded == null ? PackLang.empty() : loaded.menu().lang();
	}

	@Override
	public void refresh() {
		if (this.optionList != null) {
			this.optionList.refresh();
		}
	}

	@Override
	public void openPage(String name) {
		this.history.push(this.page);
		this.page = name;
		showPage();
	}

	@Override
	public void back() {
		this.page = this.history.isEmpty() ? "" : this.history.pop();
		showPage();
	}

	private void showPage() {
		PackSession loaded = this.session;
		if (this.optionList != null && loaded != null) {
			this.optionList.show(loaded.menu(), this.page);
		}
	}

	@Override
	public boolean showingComment() {
		return this.commentTicks > COMMENT_DELAY && this.commentTitle.isPresent()
				&& !this.commentBody.isEmpty();
	}

	
	@Override
	public void hovered(PageWidget widget, boolean hovered) {
		if (hovered && widget != this.hoveredCell) {
			this.hoveredCell = widget;
			this.commentTitle = widget.commentTitle();
			this.commentBody = widget.commentBody().map(this::wrapComment).orElse(List.of());
			this.commentTicks = 0;
		} else if (!hovered && widget == this.hoveredCell) {
			this.hoveredCell = null;
			this.commentTitle = Optional.empty();
			this.commentBody = List.of();
			this.commentTicks = 0;
		}
	}

	
	private List<FormattedCharSequence> wrapComment(Component comment) {
		String text = comment.getString();
		if (text.endsWith(".")) {
			text = text.substring(0, text.length() - 1);
		}

		List<FormattedCharSequence> lines = new ArrayList<>();
		for (String sentence : Arrays.stream(text.split("\\. [ ]*")).toList()) {
			lines.addAll(this.font.split(Component.literal(sentence), COMMENT_PANEL_WIDTH - 8));
		}

		return List.copyOf(lines);
	}

	@Override
	public void onTop(Runnable draw) {
		this.topLayer.add(draw);
	}

	@Override
	public void announce(Component message) {
		this.notification = message;
		this.notificationTicks = NOTIFICATION_TICKS;
	}

	@Override
	public Path settingsFile() {
		PackSession loaded = this.session;

		return loaded == null
				? SettingsFile.of(gameDirectory(), "")
				: loaded.settingsFile();
	}

	
	@Override
	public void resetSettings() {
		PackSession loaded = this.session;
		if (loaded == null) {
			return;
		}

		Minecraft client = this.minecraft;
		client.gui.setScreen(new ConfirmScreen(
				yes -> {
					client.gui.setScreen(this);
					if (yes) {
						emptySettings();
					}
				},
				Component.translatable(ScreenText.RESET_CONFIRM, loaded.packFileName()),
				Component.translatable(ScreenText.RESET_CONFIRM_DETAIL,
						loaded.settingsFile().getFileName().toString())));
	}

	
	private void emptySettings() {
		PackSession loaded = this.session;
		if (loaded == null) {
			return;
		}

		MenuValues current = this.values;
		if (current != null) {
			current.clearPending();
		}

		try {
			SettingsFile.write(loaded.settingsFile(), SettingsFile.Stored.empty());
			this.error = null;
		} catch (IOException | RuntimeException e) {
			this.error = String.valueOf(e.getMessage());
			CoreVK.logger().error("CoreVK could not empty {}", loaded.settingsFile(), e);

			return;
		}

		CoreVK.logger().info("{} is back to the settings it declares itself, {} is emptied",
				loaded.packFileName(), loaded.settingsFile().getFileName());
		reloadPack();
	}

	@Override
	public void importSettings(Path file) {
		MenuValues current = this.values;
		if (current == null) {
			return;
		}

		Map<String, String> read = new LinkedHashMap<>();
		try (InputStream in = Files.newInputStream(file)) {
			Properties properties = new Properties();
			properties.load(in);
			properties.forEach((name, value) -> read.put(name.toString(), value.toString()));
		} catch (IOException | RuntimeException e) {
			CoreVK.logger().error("CoreVK could not import {}", file, e);
			announce(Component
					.translatable(ScreenText.FAILED_IMPORT, name(file))
					.withStyle(ChatFormatting.ITALIC, ChatFormatting.RED));

			return;
		}

		current.queueAll(read);
		announce(Component
				.translatable(ScreenText.IMPORTED_SETTINGS, name(file))
				.withStyle(ChatFormatting.ITALIC, ChatFormatting.YELLOW));
		refresh();
	}

	
	@Override
	public void onFilesDrop(List<Path> files) {
		if (this.optionsOpen) {
			if (files.size() != 1) {
				announce(Component.translatable(ScreenText.TOO_MANY_FILES)
						.withStyle(ChatFormatting.ITALIC, ChatFormatting.RED));

				return;
			}

			importSettings(files.getFirst());

			return;
		}

		addPacks(files);
	}

	private void addPacks(List<Path> files) {
		PackList list = this.packList;
		if (list == null) {
			return;
		}

		List<Path> packs = files.stream().filter(PackLoader::looksLikeAPack).toList();
		Path directory = PackLoader.directory(gameDirectory());

		for (Path pack : packs) {
			String name = name(pack);
			try {
				copyInto(directory, pack);
			} catch (FileAlreadyExistsException e) {
				announce(Component.translatable(ScreenText.COPY_ERROR_EXISTS, name)
						.withStyle(ChatFormatting.ITALIC, ChatFormatting.RED));
				list.refresh(list.chosenName());

				return;
			} catch (IOException | RuntimeException e) {
				CoreVK.logger().warn("CoreVK could not copy the pack dropped from {}", pack, e);
				announce(Component.translatable(ScreenText.COPY_ERROR, name)
						.withStyle(ChatFormatting.ITALIC, ChatFormatting.RED));
				list.refresh(list.chosenName());

				return;
			}
		}

		list.refresh(list.chosenName());

		if (packs.isEmpty()) {
			announce((files.size() == 1
					? Component.translatable(ScreenText.FAILED_ADD_SINGLE, name(files.getFirst()))
					: Component.translatable(ScreenText.FAILED_ADD))
					.withStyle(ChatFormatting.ITALIC, ChatFormatting.RED));

			return;
		}

		if (packs.size() == 1) {
			String name = name(packs.getFirst());
			announce(Component.translatable(ScreenText.ADDED_PACK, name)
					.withStyle(ChatFormatting.ITALIC, ChatFormatting.YELLOW));
			// Selected straight away: somebody who has just dragged a pack in wants to use it.
			list.select(name);
			// And the switch is told, because it is live for what the list has selected and this is
			// the one road to a selection that does not go through a row being pressed. Without it
			// a dropped pack sits selected under a dead button until something unrelated rebuilds
			// the screen.
			refreshViewSwitch();

			return;
		}

		announce(Component.translatable(ScreenText.ADDED_PACKS, packs.size())
				.withStyle(ChatFormatting.ITALIC, ChatFormatting.YELLOW));
	}

	
	private static void copyInto(Path directory, Path pack) throws IOException {
		Files.createDirectories(directory);
		Path target = directory.resolve(name(pack));
		if (Files.exists(target)) {
			throw new FileAlreadyExistsException(target.toString());
		}

		if (!Files.isDirectory(pack)) {
			Files.copy(pack, target, StandardCopyOption.COPY_ATTRIBUTES);

			return;
		}

		try (Stream<Path> tree = Files.walk(pack)) {
			for (Path entry : tree.toList()) {
				Path to = target.resolve(pack.relativize(entry).toString());
				if (Files.isDirectory(entry)) {
					Files.createDirectories(to);
				} else {
					Files.createDirectories(to.getParent());
					Files.copy(entry, to, StandardCopyOption.COPY_ATTRIBUTES);
				}
			}
		}
	}

	private static String name(Path path) {
		Path name = path.getFileName();

		return name == null ? path.toString() : name.toString();
	}

	
	private void applyChanges() {
		PackList list = this.packList;
		if (list == null) {
			return;
		}

		PackFile asked = PackChoice.askedFor();
		String chosen = list.chosenName().isEmpty() ? asked.name() : list.chosenName();
		boolean enabled = list.shadersEnabled();
		boolean samePack = chosen.equals(asked.name());

		// A pack being swapped drops what was clicked on the one before it: a value set on one pack has
		// no meaning in the next one's file, and CoreVK clears its own queue for the same reason.
		//
		// Unless what is on screen IS the pack being swapped to, which is the whole point of reading
		// a pack without loading it: the values were set on it, they belong in its file, and throwing
		// them away here would give back exactly the behaviour this replaced. The low profile someone
		// just picked has to survive the switch, or they pay the high one anyway.
		boolean settingsAreForTheChosenPack = samePack || this.previewing;
		if (!settingsAreForTheChosenPack) {
			MenuValues current = this.values;
			if (current != null) {
				current.clearPending();
			}
		}

		boolean wrote = settingsAreForTheChosenPack && writePendingSettings();
		if (!samePack || enabled != asked.enabled()) {
			wrote |= writePackFile(chosen, enabled);
		}

		if (wrote) {
			reloadPack();
		}

		list.markSelectedApplied();
		refreshViewSwitch();
	}

	
	private boolean writePendingSettings() {
		PackSession loaded = this.session;
		MenuValues current = this.values;
		if (loaded == null || current == null || current.pendingCount() == 0) {
			return false;
		}

		try {
			SettingsFile.Stored onDisk = SettingsFile.read(loaded.settingsFile());
			current.rebase(onDisk.values(), loaded.forcedText());
			SettingsFile.write(loaded.settingsFile(), new SettingsFile.Stored(current.toSave()));
			current.clearPending();
			this.error = null;
		} catch (IOException | RuntimeException e) {
			this.error = String.valueOf(e.getMessage());
			CoreVK.logger().error("CoreVK could not write {}", loaded.settingsFile(), e);
			// The screen stays open and the bottom line carries the message: what was pending is still
			// pending, so nothing has been lost.
			return false;
		}

		return true;
	}

	
	private boolean writePackFile(String chosen, boolean enabled) {
		Path file = PackChoice.packFile(gameDirectory());
		try {
			PackFile.write(file, PackFile.read(file).withChoice(chosen, enabled));
			this.error = null;
		} catch (IOException | RuntimeException e) {
			this.error = String.valueOf(e.getMessage());
			CoreVK.logger().error("CoreVK could not write {}", file, e);

			return false;
		}

		return true;
	}

	
	private void reloadPack() {
		PackChoice.reload(gameDirectory());
	}

	private void dropChangesAndClose() {
		this.dropChanges = true;
		onClose();
	}

	
	@Override
	public void onClose() {
		if (this.dropChanges) {
			MenuValues current = this.values;
			if (current != null) {
				current.clearPending();
			}
		} else {
			applyChanges();
		}

		closePackList();
		this.minecraft.gui.setScreen(this.parent);
	}

	private void closePackList() {
		PackList list = this.packList;
		if (list == null) {
			return;
		}

		try {
			list.close();
		} catch (IOException e) {
			CoreVK.logger().warn("CoreVK could not let go of the pack folder watcher", e);
		}

		this.packList = null;
	}

	
	private void openFolder() {
		Path directory = PackLoader.directory(gameDirectory());
		try {
			Files.createDirectories(directory);
		} catch (IOException e) {
			// Opened anyway: a folder that cannot be created is one the platform will report on better
			// than this line could.
			CoreVK.logger().warn("CoreVK could not create {}", directory, e);
		}

		Desktop.openPath(directory);
	}

	
	// By identity, and here that is the opposite answer to the one EngineOptions gives on its own
	// record, on purpose. The question is whether the render layer has handed over a new reading, not
	// whether the reading says the same thing: a reload of an unchanged pack builds a session equal to
	// the held one, and by value this method would then return early and leave the screen on the menu
	// it read before.
	@SuppressWarnings("ReferenceEquality")
	private void syncWithLoadedPack() {
		PackSession loaded = PackChoice.session().orElse(null);
		PackSession held = this.session;
		if (loaded == held) {
			return;
		}

		// A pack being looked at is not the loaded one BY CONSTRUCTION, so this would swap it away on
		// the very next frame and put the drawing pack's settings back under the player's hands. The
		// look ends when the loaded pack becomes the one on screen, which is Apply having gone
		// through, and that is the case the name check below lets past.
		if (this.previewing && (loaded == null || held == null
				|| !loaded.packFileName().equals(held.packFileName()))) {
			return;
		}

		this.previewing = false;
		MenuValues current = this.values;
		if (loaded != null && held != null && current != null
				&& loaded.packFileName().equals(held.packFileName())) {
			this.session = loaded;
			// On the menu that was just read and not on the one held, since the same file name is not
			// the same pack: a directory pack is edited in place, and a zip is replaced under its own
			// name often enough.
			this.values = current.reread(loaded.menu(), loaded.saved().values(), loaded.forcedText());
			dropMissingPage();
		} else {
			adopt(loaded);
		}

		rebuildWidgets();
	}

	
	private void adopt(@Nullable PackSession loaded) {
		this.session = loaded;
		this.values = loaded == null
				? null
				: MenuValues.of(loaded.menu(), loaded.saved().values(), loaded.forcedText());
		// Out to the list when there is no pack left to configure, which is the view a pack is picked
		// from: turning every pack off otherwise leaves an empty page under a row of buttons.
		if (loaded == null) {
			this.optionsOpen = false;
		}

		dropMissingPage();
	}

	private void dropMissingPage() {
		PackSession loaded = this.session;
		if (loaded == null || (!this.page.isEmpty() && loaded.menu().page(this.page).isEmpty())) {
			this.page = "";
			this.history.clear();
		}
	}

	private Path gameDirectory() {
		PackSession loaded = this.session;

		return loaded == null ? CoreVK.platform().gameDirectory() : loaded.gameDirectory();
	}
}
