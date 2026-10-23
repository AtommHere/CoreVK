package net.corevk.corevk.pack.option;

import java.util.List;

public record PackOption(String name, Kind kind, String defaultText, List<String> values,
		boolean defaultOff, String constType, String declaredIn, int line) {

	public enum Kind {
		
		TOGGLE,
		
		VALUE,
		
		CONST
	}

	public PackOption {
		values = List.copyOf(values);
	}

	
	public boolean hasValueList() {
		return !this.values.isEmpty();
	}
}
