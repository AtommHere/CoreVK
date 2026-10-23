package net.corevk.corevk.pack.option;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OptionRewriter {

	private static final Pattern DEFINE =
			Pattern.compile("^(\\s*)(//\\s*)?#\\s*define\\s+([A-Za-z_]\\w*)\\b(.*)$");
	private static final Pattern CONSTANT =
			Pattern.compile("^(\\s*)const\\s+(int|float|bool|uint)\\s+([A-Za-z_]\\w*)\\s*=\\s*([^;]+);(.*)$");

	private static final Pattern VALUE_LIST_COMMENT = Pattern.compile("(//\\s*\\[.*)$");

	private static final String BOOL = "bool";

	private OptionRewriter() {
	}

	private static final String SHADOW_MAP_RESOLUTION = "shadowMapResolution";

	public static String apply(String line, Map<String, OptionValue> chosen, int scale) {
		Matcher define = DEFINE.matcher(line);
		if (define.matches()) {
			OptionValue value = chosen.get(define.group(3));
			if (value == null) {
				return line;
			}

			String indent = define.group(1);
			String name = define.group(3);
			Matcher list = VALUE_LIST_COMMENT.matcher(define.group(4));
			String tail = list.find() ? " " + list.group(1) : "";

			if (!value.isBoolean()) {
				return indent + "#define " + name + " " + value.text() + tail;
			}

			return indent + (value.asBoolean() ? "#define " : "//#define ") + name + tail;
		}

		Matcher constant = CONSTANT.matcher(line);
		if (constant.matches()) {

			OptionValue value = ConstOptions.isOption(constant.group(3))
					? chosen.get(constant.group(3)) : null;

			boolean scaled = scale != 100 && SHADOW_MAP_RESOLUTION.equals(constant.group(3));
			if (value == null && !scaled) {
				return line;
			}

			if (value != null && value.isBoolean() && !BOOL.equals(constant.group(2))) {
				return line;
			}

			String text = value == null ? constant.group(4).trim()
				: value.isBoolean() ? Boolean.toString(value.asBoolean()) : value.text();

			if (scaled) {

				String through = through(text, scale);
				if (through == null && value == null) {
					return line;
				}

				if (through != null) {
					text = through;
				}
			}

			return constant.group(1) + "const " + constant.group(2) + " " + constant.group(3)
					+ " = " + text + ";" + constant.group(5);
		}

		return line;
	}

	private static String through(String text, int scale) {
		int declared;
		try {
			declared = Integer.parseInt(text);
		} catch (NumberFormatException e) {
			return null;
		}

		return Integer.toString(Math.max(1, Math.round(declared * scale / 100.0F)));
	}
}
