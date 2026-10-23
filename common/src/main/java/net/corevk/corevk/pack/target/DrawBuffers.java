package net.corevk.corevk.pack.target;

import net.corevk.corevk.pack.model.TargetName;
import net.corevk.corevk.pack.source.IncludeExpander;

import java.util.ArrayList;
import java.util.List;

public final class DrawBuffers {

	private static final List<Integer> PAIR = List.of(0, 1);

	private DrawBuffers() {
	}

	public static List<Integer> parse(IncludeExpander.ExpandedUnit unit) {
		String text = liveText(unit);
		int drawBuffersAt = directiveStart(text, "DRAWBUFFERS");
		int renderTargetsAt = directiveStart(text, "RENDERTARGETS");

		if (drawBuffersAt < 0 && renderTargetsAt < 0) {
			return List.of();
		}

		boolean runTogether = drawBuffersAt > renderTargetsAt;
		String value = directiveValue(text, runTogether ? drawBuffersAt : renderTargetsAt,
				runTogether ? "DRAWBUFFERS" : "RENDERTARGETS");
		if (value == null) {
			return List.of();
		}

		List<Integer> slots = new ArrayList<>();

		if (runTogether) {
			for (int i = 0; i < value.length(); i++) {
				char digit = value.charAt(i);
				if (digit >= '0' && digit <= '9') {
					slots.add(digit - '0');
				}
			}

			return List.copyOf(slots);
		}

		for (String part : value.split(",", -1)) {
			addRenderTarget(slots, part.trim());
		}

		return List.copyOf(slots);
	}

	public static List<Integer> shadowColours(List<Integer> declared, int existing) {
		if (declared.isEmpty()) {
			return PAIR;
		}

		return declared.stream().anyMatch(index -> index >= existing) ? PAIR : List.copyOf(declared);
	}

	private static String liveText(IncludeExpander.ExpandedUnit unit) {
		List<String> lines = unit.lines();
		StringBuilder text = new StringBuilder();

		for (int line = 0; line < lines.size(); line++) {
			if (line > 0) {
				text.append('\n');
			}

			if (unit.isLive(line)) {
				text.append(lines.get(line));
			}
		}

		return text.toString();
	}

	private static int directiveStart(String text, String name) {
		int at = text.lastIndexOf(name + ":");
		if (at < 0) {
			return -1;
		}

		return text.substring(0, at).stripTrailing().endsWith("/*") ? at : -1;
	}

	private static String directiveValue(String text, int at, String name) {
		String rest = text.substring(at + name.length() + 1);
		int close = rest.indexOf("*/");

		return close < 0 ? null : rest.substring(0, close).trim();
	}

	private static void addRenderTarget(List<Integer> slots, String text) {
		if (text.isEmpty() || text.length() > 2 || !text.chars().allMatch(Character::isDigit)) {
			return;
		}

		int slot = Integer.parseInt(text);
		if (slot < TargetName.MAX_TARGETS) {
			slots.add(slot);
		}
	}
}
