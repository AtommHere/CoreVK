package net.corevk.corevk.pack.menu;

import net.corevk.corevk.pack.option.PackOption;

import java.util.ArrayList;
import java.util.List;

public record MenuOption(String name, Form form, String defaultValue, List<String> values,
		boolean slider) {

	private static final List<String> TOGGLE_VALUES = List.of("on", "off");

	public enum Form {
		
		TOGGLE,
		
		CYCLE,
		
		FIXED
	}

	public MenuOption {
		values = List.copyOf(values);
	}

	
	public static MenuOption of(PackOption option, boolean slider) {
		if (option.kind() == PackOption.Kind.TOGGLE) {
			return new MenuOption(option.name(), Form.TOGGLE, option.defaultOff() ? "off" : "on",
					TOGGLE_VALUES, false);
		}

		if (option.kind() == PackOption.Kind.CONST && "bool".equals(option.constType())) {
			return new MenuOption(option.name(), Form.TOGGLE,
					"true".equals(option.defaultText()) ? "on" : "off", TOGGLE_VALUES, false);
		}

		List<String> values = new ArrayList<>(option.values());
		if (!values.isEmpty() && !values.contains(option.defaultText())) {
			values.add(option.defaultText());
		}

		Form form = values.size() < 2 ? Form.FIXED : Form.CYCLE;

		return new MenuOption(option.name(), form, option.defaultText(), values,
				slider && form == Form.CYCLE);
	}

	
	public int indexOf(String value) {
		int found = this.values.indexOf(value);
		if (found >= 0) {
			return found;
		}

		return Math.max(0, this.values.indexOf(this.defaultValue));
	}

	
	public String at(int index) {
		if (this.values.isEmpty()) {
			return this.defaultValue;
		}

		return this.values.get(Math.floorMod(index, this.values.size()));
	}

	public int size() {
		return this.values.size();
	}
}
