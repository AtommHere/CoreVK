package net.corevk.corevk.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;

public class WidgetList<E extends ContainerObjectSelectionList.Entry<E>>
		extends ContainerObjectSelectionList<E> {

	private static final int SCROLLBAR_INSET = 6;

	public WidgetList(Minecraft minecraft, int width, int height, int top, int itemHeight) {
		super(minecraft, width, height, top, itemHeight);
	}

	@Override
	protected int scrollBarX() {
		return this.width - SCROLLBAR_INSET;
	}
}
