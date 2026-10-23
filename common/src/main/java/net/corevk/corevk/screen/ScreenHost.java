package net.corevk.corevk.screen;

import net.corevk.corevk.pack.menu.MenuValues;
import net.corevk.corevk.pack.source.PackLang;

import net.minecraft.network.chat.Component;

import java.nio.file.Path;

public interface ScreenHost {

	MenuValues values();

	PackLang lang();

	
	void refresh();

	void openPage(String name);

	
	void back();

	
	boolean showingComment();

	
	void hovered(PageWidget widget, boolean hovered);

	
	void onTop(Runnable draw);

	
	void announce(Component message);

	
	Path settingsFile();

	
	void resetSettings();

	
	void importSettings(Path file);
}
