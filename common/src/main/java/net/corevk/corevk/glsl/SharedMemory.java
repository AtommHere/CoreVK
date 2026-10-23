package net.corevk.corevk.glsl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SharedMemory {

	
	public static final long THREADGROUP_BYTES = 32768L;

	
	public static final String BLOCK = "OfSharedMemory";

	
	private static final String BARRIER = "#define barrier() (memoryBarrierBuffer(), barrier())";

	private static final Pattern WORD = Pattern.compile("\\bshared\\b");

	private static final Pattern TOKEN = Pattern.compile("[A-Za-z_]\\w*|\\d+[uU]?|\\S");

	private static final Pattern NAME = Pattern.compile("[A-Za-z_]\\w*");

	private static final Pattern COUNT = Pattern.compile("(\\d+)[uU]?");

	private static final Pattern VERSION = Pattern.compile("(?m)^[ \\t]*#[ \\t]*version\\b.*$");

	
	private static final Set<String> QUALIFIERS = Set.of("shared", "highp", "mediump", "lowp", "precise");

	private static final Map<String, Layout> TYPES = types();

	private static volatile boolean moltenVk;

	private SharedMemory() {
	}

	
	public record Reading(long threadgroupBytes, long bufferBytes, String moved, String unread) {

		
		public boolean over() {
			return this.threadgroupBytes > THREADGROUP_BYTES;
		}
	}

	
	public static void serve(boolean moltenVkDriver) {
		moltenVk = moltenVkDriver;
	}

	
	public static boolean moltenVk() {
		return moltenVk;
	}

	
	public static boolean mentioned(String text) {
		return WORD.matcher(text).find();
	}

	
	public static Reading read(String preprocessed) {
		String scanned = withoutDirectives(preprocessed);
		List<int[]> spans = new ArrayList<>();
		StringBuilder members = new StringBuilder();
		long threadgroup = 0L;
		long offset = 0L;
		long widest = 4L;
		int depth = 0;
		int start = 0;
		for (int i = 0; i < scanned.length(); i++) {
			char c = scanned.charAt(i);
			if (c == '{') {
				depth++;
				continue;
			}

			if (c == '}') {
				depth = Math.max(0, depth - 1);
				if (depth == 0) {
					start = i + 1;
				}

				continue;
			}

			if (c != ';' || depth != 0) {
				continue;
			}

			String statement = scanned.substring(start, i);
			int from = start + (statement.length() - statement.stripLeading().length());
			start = i + 1;
			List<String> tokens = tokens(statement);
			if (!declaresShared(tokens)) {
				continue;
			}

			Declaration declaration = declaration(tokens);
			// A directive inside the statement would be lost with the span it sits in.
			if (declaration == null || preprocessed.substring(from, i).indexOf('#') >= 0) {
				return new Reading(-1L, 0L, null, statement.strip());
			}

			Layout layout = declaration.layout();
			for (long count : declaration.counts()) {
				long metal = layout.stride() * Math.max(1L, count);
				threadgroup += metal;
				offset = aligned(offset, layout.align()) + (count == 0L ? layout.size() : metal);
				widest = Math.max(widest, layout.align());
			}

			members.append(declaration.member()).append(' ');
			spans.add(new int[] {from, i + 1});
		}

		long bytes = aligned(offset, widest);
		if (threadgroup <= THREADGROUP_BYTES) {
			return new Reading(threadgroup, bytes, null, null);
		}

		return new Reading(threadgroup, bytes, moved(preprocessed, spans, members.toString()), null);
	}

	
	private static String moved(String preprocessed, List<int[]> spans, String members) {
		StringBuilder text = new StringBuilder(preprocessed.length() + 256);
		int copied = 0;
		for (int k = 0; k < spans.size(); k++) {
			int[] span = spans.get(k);
			text.append(preprocessed, copied, span[0]);
			text.append(k == 0
					? "layout(std430) coherent buffer " + BLOCK + " { " + members + "};"
					: blank(preprocessed.substring(span[0], span[1])));
			copied = span[1];
		}

		text.append(preprocessed, copied, preprocessed.length());
		Matcher version = VERSION.matcher(text);
		if (version.find()) {
			text.insert(version.end(), "\n" + BARRIER);
		} else {
			text.insert(0, BARRIER + "\n");
		}

		return text.toString();
	}

	private record Layout(long size, long align, long stride) {
	}

	
	private record Declaration(String member, Layout layout, List<Long> counts) {
	}

	
	private static Map<String, Layout> types() {
		Map<String, Layout> table = new HashMap<>();
		for (String scalar : List.of("float", "int", "uint")) {
			table.put(scalar, new Layout(4L, 4L, 4L));
		}

		for (int n = 2; n <= 4; n++) {
			long align = n == 2 ? 8L : 16L;
			for (String prefix : List.of("vec", "ivec", "uvec")) {
				table.put(prefix + n, new Layout(4L * n, align, align));
			}

			for (int rows = 2; rows <= 4; rows++) {
				long column = rows == 2 ? 8L : 16L;
				Layout matrix = new Layout(n * column, column, n * column);
				table.put("mat" + n + "x" + rows, matrix);
				if (rows == n) {
					table.put("mat" + n, matrix);
				}
			}
		}

		return Map.copyOf(table);
	}

	private static boolean declaresShared(List<String> tokens) {
		int parentheses = 0;
		for (String token : tokens) {
			if ("(".equals(token)) {
				parentheses++;
			} else if (")".equals(token)) {
				parentheses--;
			} else if (parentheses == 0 && "shared".equals(token)) {
				return true;
			}
		}

		return false;
	}

	
	private static Declaration declaration(List<String> tokens) {
		int at = 0;
		while (at < tokens.size() && QUALIFIERS.contains(tokens.get(at))) {
			at++;
		}

		Layout layout = at < tokens.size() ? TYPES.get(tokens.get(at)) : null;
		if (layout == null) {
			return null;
		}

		at++;
		List<Long> counts = new ArrayList<>();
		try {
			long typeCount = 0L;
			while (at < tokens.size() && "[".equals(tokens.get(at))) {
				long[] dimension = dimension(tokens, at);
				if (dimension == null) {
					return null;
				}

				typeCount = Math.multiplyExact(Math.max(1L, typeCount), dimension[0]);
				at = (int) dimension[1];
			}

			while (true) {
				if (at >= tokens.size() || !NAME.matcher(tokens.get(at)).matches()
						|| TYPES.containsKey(tokens.get(at)) || QUALIFIERS.contains(tokens.get(at))) {
					return null;
				}

				at++;
				long count = typeCount;
				while (at < tokens.size() && "[".equals(tokens.get(at))) {
					long[] dimension = dimension(tokens, at);
					if (dimension == null) {
						return null;
					}

					count = Math.multiplyExact(Math.max(1L, count), dimension[0]);
					at = (int) dimension[1];
				}

				counts.add(count);
				if (at == tokens.size()) {
					break;
				}

				if (!",".equals(tokens.get(at))) {
					return null;
				}

				at++;
			}
		} catch (ArithmeticException wide) {
			return null;
		}

		List<String> member = new ArrayList<>();
		for (String token : tokens) {
			if (!"shared".equals(token)) {
				member.add(token);
			}
		}

		return new Declaration(String.join(" ", member) + ";", layout, List.copyOf(counts));
	}

	
	private static long[] dimension(List<String> tokens, int at) {
		long product = 1L;
		int next = at + 1;
		while (next < tokens.size()) {
			Matcher count = COUNT.matcher(tokens.get(next));
			if (!count.matches()) {
				return null;
			}

			try {
				product = Math.multiplyExact(product, Long.parseLong(count.group(1)));
			} catch (NumberFormatException wide) {
				return null;
			}

			next++;
			if (next < tokens.size() && "]".equals(tokens.get(next))) {
				return product > 0L ? new long[] {product, next + 1L} : null;
			}

			if (next >= tokens.size() || !"*".equals(tokens.get(next))) {
				return null;
			}

			next++;
		}

		return null;
	}

	private static List<String> tokens(String statement) {
		List<String> tokens = new ArrayList<>();
		Matcher matcher = TOKEN.matcher(statement);
		while (matcher.find()) {
			tokens.add(matcher.group());
		}

		return tokens;
	}

	
	private static String withoutDirectives(String text) {
		StringBuilder scanned = new StringBuilder(text.length());
		for (String line : text.split("\n", -1)) {
			if (scanned.length() > 0) {
				scanned.append('\n');
			}

			scanned.append(line.stripLeading().startsWith("#") ? " ".repeat(line.length()) : line);
		}

		return scanned.toString();
	}

	private static String blank(String text) {
		StringBuilder blank = new StringBuilder(text.length());
		for (int i = 0; i < text.length(); i++) {
			blank.append(text.charAt(i) == '\n' ? '\n' : ' ');
		}

		return blank.toString();
	}

	private static long aligned(long offset, long align) {
		return (offset + align - 1L) / align * align;
	}
}
