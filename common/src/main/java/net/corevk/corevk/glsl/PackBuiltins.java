package net.corevk.corevk.glsl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class PackBuiltins {

	private static final Map<String, String> HELPERS = helpers();

	private PackBuiltins() {
	}

	static String helper(String builtin) {
		return HELPERS.get(builtin);
	}

	static List<String> definitions(Set<String> called) {
		List<String> lines = new ArrayList<>();
		for (String builtin : HELPERS.keySet()) {
			if (called.contains(builtin)) {
				lines.add(definition(builtin));
			}
		}

		return lines;
	}

	private static String definition(String builtin) {
		String name = HELPERS.get(builtin);
		return switch (builtin) {
			case "packUnorm4x8" -> "uint " + name + "(vec4 ofV) {"
					+ " uvec4 ofB = uvec4(round(clamp(ofV, 0.0, 1.0) * 255.0));"
					+ " return ofB.x | (ofB.y << 8u) | (ofB.z << 16u) | (ofB.w << 24u); }";
			case "unpackUnorm4x8" -> "vec4 " + name + "(uint ofP) {"
					+ " return vec4(uvec4(ofP, ofP >> 8u, ofP >> 16u, ofP >> 24u) & 255u) / 255.0; }";
			case "packUnorm2x16" -> "uint " + name + "(vec2 ofV) {"
					+ " uvec2 ofB = uvec2(round(clamp(ofV, 0.0, 1.0) * 65535.0));"
					+ " return ofB.x | (ofB.y << 16u); }";
			case "unpackUnorm2x16" -> "vec2 " + name + "(uint ofP) {"
					+ " return vec2(uvec2(ofP, ofP >> 16u) & 65535u) / 65535.0; }";
			case "packSnorm4x8" -> "uint " + name + "(vec4 ofV) {"
					+ " uvec4 ofB = uvec4(ivec4(round(clamp(ofV, -1.0, 1.0) * 127.0))) & 255u;"
					+ " return ofB.x | (ofB.y << 8u) | (ofB.z << 16u) | (ofB.w << 24u); }";
			case "unpackSnorm4x8" -> "vec4 " + name + "(uint ofP) {"
					+ " ivec4 ofB = ivec4((uvec4(ofP, ofP >> 8u, ofP >> 16u, ofP >> 24u) & 255u)"
					+ " ^ 128u) - 128;"
					+ " return clamp(vec4(ofB) / 127.0, -1.0, 1.0); }";
			case "packSnorm2x16" -> "uint " + name + "(vec2 ofV) {"
					+ " uvec2 ofB = uvec2(ivec2(round(clamp(ofV, -1.0, 1.0) * 32767.0))) & 65535u;"
					+ " return ofB.x | (ofB.y << 16u); }";
			case "unpackSnorm2x16" -> "vec2 " + name + "(uint ofP) {"
					+ " ivec2 ofB = ivec2((uvec2(ofP, ofP >> 16u) & 65535u) ^ 32768u) - 32768;"
					+ " return clamp(vec2(ofB) / 32767.0, -1.0, 1.0); }";
			default -> throw new IllegalArgumentException(builtin);
		};
	}

	private static Map<String, String> helpers() {
		Map<String, String> table = new LinkedHashMap<>();
		table.put("packUnorm4x8", "ofPackUnorm4x8");
		table.put("unpackUnorm4x8", "ofUnpackUnorm4x8");
		table.put("packUnorm2x16", "ofPackUnorm2x16");
		table.put("unpackUnorm2x16", "ofUnpackUnorm2x16");
		table.put("packSnorm4x8", "ofPackSnorm4x8");
		table.put("unpackSnorm4x8", "ofUnpackSnorm4x8");
		table.put("packSnorm2x16", "ofPackSnorm2x16");
		table.put("unpackSnorm2x16", "ofUnpackSnorm2x16");

		return Collections.unmodifiableMap(table);
	}
}
