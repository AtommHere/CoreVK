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

			// Turning a switch off comments the line out rather than defining it to false: the
			// pack tests it with #ifdef, which any definition at all would satisfy.
			return indent + (value.asBoolean() ? "#define " : "//#define ") + name + tail;
		}

		Matcher constant = CONSTANT.matcher(line);
		if (constant.matches()) {
			// A constant off the closed list is never a setting. A chosen value for one can
			// still arrive, from a hand-written line of the pack's settings file, and it has to
			// change nothing: the reference does not hold such a name as an option, so a shared
			// file must not edit the declaration here either. This line-at-a-time rewriter has
			// no option index, so the list is the whole of its gate: a hand-written value for a
			// listed name the index refuses, for want of a value list or of anything testing
			// it, is still applied here where the reference would drop it. Only a hand can
			// write that line, no screen offering the name.
			OptionValue value = ConstOptions.isOption(constant.group(3))
					? chosen.get(constant.group(3)) : null;

			// The scale is the one thing that rewrites a declaration nobody chose a value for, so it
			// is asked before the line is given back. A hundred changes nothing at all, which is what
			// makes an untouched setting free and keeps every reading below it out of this file.
			boolean scaled = scale != 100 && SHADOW_MAP_RESOLUTION.equals(constant.group(3));
			if (value == null && !scaled) {
				return line;
			}

			// A switch says nothing about a constant that holds a number, so that one is left
			// alone. On a const bool it is written out as true or false, the only pair such a
			// declaration takes: a constant is read as an expression rather than tested with
			// #ifdef, so commenting the line out the way a #define is would leave the name
			// undeclared, and skipping it would drop the setting without a word.
			if (value != null && value.isBoolean() && !BOOL.equals(constant.group(2))) {
				return line;
			}

			// The declaration's own expression when nothing was chosen, which is the road a scale
			// takes on a pack that offers no setting for this name.
			String text = value == null ? constant.group(4).trim()
				: value.isBoolean() ? Boolean.toString(value.asBoolean()) : value.text();

			if (scaled) {
				// A scale that cannot be applied never costs a chosen value: the declaration is left
				// standing only where there was nothing to write anyway, so a setting for this name
				// still reaches the shader when the scale cannot read what it holds.
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
