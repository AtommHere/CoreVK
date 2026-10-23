package net.corevk.corevk.pack.model;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public record BufferObject(int index, long size, boolean relative, float scaleX, float scaleY,
		Optional<String> name) {

	public static final int LIMIT = 12;

	public record Reading(List<BufferObject> buffers, List<String> dropped) {

		public Reading {
			buffers = List.copyOf(buffers);
			dropped = List.copyOf(dropped);
		}

		public static Reading empty() {
			return new Reading(List.of(), List.of());
		}

		public boolean hasIndex(int index) {
			for (BufferObject buffer : this.buffers) {
				if (buffer.index() == index) {
					return true;
				}
			}

			return false;
		}

		public boolean hasName(String name) {
			for (BufferObject buffer : this.buffers) {
				if (buffer.name().filter(name::equals).isPresent()) {
					return true;
				}
			}

			return false;
		}
	}

	public String describe() {
		String size = this.relative
				? this.size + " bytes per pixel at " + this.scaleX + "x" + this.scaleY
						+ " of the screen"
				: this.size + " bytes";

		return this.index
				+ this.name.map(name -> " as " + name).orElse("")
				+ " " + size;
	}

	public static String parse(String indexText, String value,
			Map<Integer, BufferObject> buffers) {
		int index;
		try {
			index = Integer.parseInt(indexText);
		} catch (NumberFormatException e) {
			return "index is not a number";
		}

		if (index > LIMIT) {
			return "only indices 0 to " + LIMIT + " are allowed";
		}

		String[] parts = value.split(" ", -1);
		if (parts.length == 0 || parts[0].isEmpty()) {
			return "expected a size";
		}

		long size;
		try {
			size = Long.parseLong(parts[0]);
		} catch (NumberFormatException e) {
			return "size is not a number";
		}

		if (size < 1L) {
			return "size below one disables the buffer";
		}

		if (parts.length <= 2) {
			Optional<String> name = parts.length > 1 && !parts[1].isEmpty()
					? Optional.of(parts[1])
					: Optional.empty();
			buffers.put(index, new BufferObject(index, size, false, 0.0F, 0.0F, name));
			return null;
		}

		if (parts.length < 4) {
			return "a relative buffer takes four words";
		}

		boolean relative = Boolean.parseBoolean(parts[1]);
		float scaleX;
		float scaleY;
		try {
			scaleX = Float.parseFloat(parts[2]);
			scaleY = Float.parseFloat(parts[3]);
		} catch (NumberFormatException e) {
			return "relative scale is not a number";
		}

		buffers.put(index, new BufferObject(index, size, relative, scaleX, scaleY, Optional.empty()));
		return null;
	}
}
