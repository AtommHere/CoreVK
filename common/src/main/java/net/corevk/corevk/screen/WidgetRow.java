package net.corevk.corevk.screen;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public final class WidgetRow {

	
	private static final int DEFAULT_SPACING = 1;

	
	private final Map<Element, Integer> widths = new LinkedHashMap<>();

	private final int spacing;

	private int x;
	private int y;
	private int width;
	private int height;

	public WidgetRow(int spacing) {
		this.spacing = spacing;
	}

	public WidgetRow() {
		this(DEFAULT_SPACING);
	}

	
	public int width() {
		return this.width;
	}

	public WidgetRow add(Element element, int width) {
		Integer had = this.widths.put(element, width);
		// Adding an element twice sets its width rather than counting it twice, which is what lets
		// setWidth go through here.
		this.width += width + this.spacing - (had == null ? 0 : had + this.spacing);

		return this;
	}

	public void setWidth(Element element, int width) {
		if (this.widths.containsKey(element)) {
			add(element, width);
		}
	}

	
	public void draw(GuiGraphicsExtractor graphics, int x, int y, int height, int mouseX, int mouseY,
			float a, boolean rowHovered) {
		this.x = x;
		this.y = y;
		this.height = height;

		int at = x;
		for (Map.Entry<Element, Integer> held : this.widths.entrySet()) {
			int width = held.getValue();
			held.getKey().draw(graphics, at, y, width, height, mouseX, mouseY, a,
					rowHovered && over(at, width, mouseX, mouseY));
			at += width + this.spacing;
		}
	}

	
	public void drawRightAligned(GuiGraphicsExtractor graphics, int x, int y, int height, int mouseX,
			int mouseY, float a, boolean rowHovered) {
		draw(graphics, x - this.width, y, height, mouseX, mouseY, a, rowHovered);
	}

	private boolean over(int sectionX, int sectionWidth, double mouseX, double mouseY) {
		return mouseX > sectionX && mouseX < sectionX + sectionWidth
				&& mouseY > this.y && mouseY < this.y + this.height;
	}

	private Optional<Element> hovered(double mouseX, double mouseY) {
		int at = this.x;
		for (Map.Entry<Element, Integer> held : this.widths.entrySet()) {
			int width = held.getValue();
			if (over(at, width, mouseX, mouseY)) {
				return Optional.of(held.getKey());
			}

			at += width + this.spacing;
		}

		return Optional.empty();
	}

	private Optional<Element> focused() {
		return this.widths.keySet().stream().filter(Element::isFocused).findFirst();
	}

	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		return hovered(event.x(), event.y())
				.map(element -> element.mouseClicked(event, doubleClick))
				.orElse(false);
	}

	public boolean mouseReleased(MouseButtonEvent event) {
		return hovered(event.x(), event.y()).map(element -> element.mouseReleased(event))
				.orElse(false);
	}

	public boolean keyPressed(KeyEvent event) {
		return focused().map(element -> element.keyPressed(event)).orElse(false);
	}

	public List<? extends GuiEventListener> children() {
		return List.copyOf(new ArrayList<>(this.widths.keySet()));
	}

	
	public abstract static class Element implements GuiEventListener {

		public boolean disabled;

		private boolean hovered;
		private boolean focused;
		private ScreenRectangle bounds = ScreenRectangle.empty();

		public void draw(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
				int mouseX, int mouseY, float a, boolean hovered) {
			this.bounds = new ScreenRectangle(x, y, width, height);
			// Taken on BEFORE the face is drawn, where CoreVK assigns it after,
			// {@code CoreVKElementRow.java:162-164}. Its face therefore lights from the previous frame's
			// reading while its label lights from this one, so entering and leaving an element shows one
			// frame of the two disagreeing. Nothing about a pack depends on it and the fix is the order
			// of two statements.
			this.hovered = hovered;

			ScreenDraw.button(graphics, x, y, width, height, this.hovered || isFocused(),
					this.disabled);
			drawLabel(graphics, x, y, width, height, mouseX, mouseY, a, this.hovered);
		}

		public abstract void drawLabel(GuiGraphicsExtractor graphics, int x, int y, int width,
				int height, int mouseX, int mouseY, float a, boolean hovered);

		public boolean isHovered() {
			return this.hovered;
		}

		@Override
		public boolean isFocused() {
			return this.focused;
		}

		@Override
		public void setFocused(boolean focused) {
			this.focused = focused;
		}

		@Override
		public @Nullable ComponentPath nextFocusPath(FocusNavigationEvent event) {
			return isFocused() ? null : ComponentPath.leaf(this);
		}

		@Override
		public ScreenRectangle getRectangle() {
			return this.bounds;
		}
	}

	
	public abstract static class Pressable<T extends Pressable<T>> extends Element {

		private final Function<T, Boolean> onClick;

		protected Pressable(Function<T, Boolean> onClick) {
			this.onClick = onClick;
		}

		@SuppressWarnings("unchecked")
		private boolean press() {
			return this.onClick.apply((T) this);
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
			if (this.disabled) {
				return false;
			}

			return event.button() == InputConstants.MOUSE_BUTTON_LEFT
					? press()
					: super.mouseClicked(event, doubleClick);
		}

		@Override
		public boolean keyPressed(KeyEvent event) {
			return event.isConfirmation() && press();
		}
	}

	
	public static final class IconElement extends Pressable<IconElement> {

		private final ScreenDraw.Icon icon;
		private final ScreenDraw.Icon lit;

		public IconElement(ScreenDraw.Icon icon, ScreenDraw.Icon lit,
				Function<IconElement, Boolean> onClick) {
			super(onClick);
			this.icon = icon;
			this.lit = lit;
		}

		public IconElement(ScreenDraw.Icon icon, Function<IconElement, Boolean> onClick) {
			this(icon, icon, onClick);
		}

		@Override
		public void drawLabel(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
				int mouseX, int mouseY, float a, boolean hovered) {
			ScreenDraw.Icon drawn = !this.disabled && (hovered || isFocused()) ? this.lit : this.icon;
			int iconX = x + (width - drawn.width()) / 2;
			int iconY = y + (height - drawn.height()) / 2;

			drawn.draw(graphics, iconX, iconY);
		}
	}

	
	public static final class TextElement extends Pressable<TextElement> {

		
		private static final int LINE = 8;

		private final Font font = Minecraft.getInstance().font;

		private Component text;

		public TextElement(Component text, Function<TextElement, Boolean> onClick) {
			super(onClick);
			this.text = text;
		}

		public void setText(Component text) {
			this.text = text;
		}

		public Component text() {
			return this.text;
		}

		@Override
		public void drawLabel(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
				int mouseX, int mouseY, float a, boolean hovered) {
			int textX = x + (width - this.font.width(this.text)) / 2;
			int textY = y + (height - LINE) / 2;

			graphics.text(this.font, this.text, textX, textY, 0xFFFFFFFF);
		}
	}
}
