package net.corevk.corevk.pack.texture;

import net.corevk.corevk.pack.model.BufferObject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CustomStorage {

	private static volatile BufferObject.Reading declared = BufferObject.Reading.empty();

	private static final Map<String, Integer> bindings = new ConcurrentHashMap<>();

	private CustomStorage() {
	}

	public static void install(BufferObject.Reading reading) {
		declared = reading;
	}

	public static void clear() {
		declared = BufferObject.Reading.empty();
		bindings.clear();
	}

	public static void declare(String name, int binding) {
		if (name == null || name.isEmpty()) {
			return;
		}

		bindings.put(name, binding);
	}

	public static boolean named(String name) {
		if (declared.hasName(name)) {
			return true;
		}

		Integer index = bindings.get(name);
		return index != null && index >= 0 && declared.hasIndex(index);
	}

	public static BufferObject.Reading reading() {
		return declared;
	}

	public static int indexOf(String name) {
		for (BufferObject buffer : declared.buffers()) {
			if (buffer.name().filter(name::equals).isPresent()) {
				return buffer.index();
			}
		}

		Integer index = bindings.get(name);
		return index == null ? -1 : index;
	}
}
