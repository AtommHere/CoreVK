package net.corevk.corevk.pack.menu;

import java.util.List;

public record MenuPage(String name, List<MenuSlot> slots, int columns) {

	public MenuPage {
		slots = List.copyOf(slots);
	}
}
