package net.corevk.corevk.pack.menu;

public sealed interface MenuSlot {

	record Blank() implements MenuSlot {
	}

	record Option(MenuOption option) implements MenuSlot {
	}

	record Link(String page, boolean resolved) implements MenuSlot {
	}

	record Profiles() implements MenuSlot {
	}
}
