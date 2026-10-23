package net.corevk.corevk.glsl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class VertexPrologue {

	public static final Set<String> SYNTHESIZED = Collections.unmodifiableSet(new TreeSet<>(
			List.of("mc_Entity", "mc_midTexCoord", "mc_chunkFade", "at_tangent", "at_midBlock",
					"vaPosition", "vaNormal", "vaColor", "vaUV0", "vaUV1", "vaUV2", "dhMaterialId")));

	private static final Map<String, String> BETTER_DEFAULTS = Map.of(
			"at_tangent", "vec4(1.0, 0.0, 0.0, 1.0)",
			"of_Normal", "vec3(0.0, 1.0, 0.0)");

	private VertexPrologue() {
	}

	private static Map<String, String> globals(Set<String> used, Map<String, String> synthesized) {
		return globals(used, synthesized, Map.of());
	}

	public static Map<String, String> globals(Set<String> used, Map<String, String> synthesized,
			Map<String, String> ahead) {
		Map<String, String> globals = new LinkedHashMap<>(synthesized);

		ahead.forEach((name, type) -> {
			if (used.contains(name) && !globals.containsKey(name)) {
				globals.put(name, type);
			}
		});

		for (String name : SYNTHESIZED) {
			if (used.contains(name) && !globals.containsKey(name)) {
				globals.put(name, defaultType(name));
			}
		}

		return globals;
	}

	public static String declaration(String name, String type) {
		return type + " " + name + " = " + value(name, type) + ";";
	}

	public static List<String> tail(Set<String> used, Map<String, String> synthesized) {
		List<String> lines = new ArrayList<>();
		globals(used, synthesized).forEach((name, type) -> lines.add(declaration(name, type)));

		return lines;
	}

	public static List<String> blankTexCoords() {
		List<String> lines = new ArrayList<>();
		for (int unit = 3; unit <= 7; unit++) {
			lines.add("#define of_MultiTexCoord" + unit + " vec4(0.0, 0.0, 0.0, 1.0)");
		}

		return lines;
	}

	public static String elementType(String element) {
		return switch (element) {
			case "Position" -> "vec3";
			case "UV0", EntityVertex.MID_TEX_COORD -> "vec2";
			case "UV1", "UV2" -> "ivec2";
			case EntityVertex.IDENTIFIERS -> "uvec4";
			case "LineWidth" -> "float";
			default -> "vec4";
		};
	}

	public static String value(String name, String type) {
		String better = BETTER_DEFAULTS.get(name);

		return better != null && better.startsWith(type + "(") ? better : zero(type);
	}

	public static String zero(String type) {
		return switch (type) {
			case "float" -> "0.0";
			case "int" -> "0";
			case "uint" -> "0u";
			case "bool" -> "false";
			case "vec2", "vec3", "vec4" -> type + "(0.0)";
			case "ivec2", "ivec3", "ivec4" -> type + "(0)";
			case "uvec2", "uvec3", "uvec4" -> type + "(0u)";
			case "bvec2", "bvec3", "bvec4" -> type + "(false)";

			default -> type + "(0.0)";
		};
	}

	private static String defaultType(String name) {
		return switch (name) {
			case "dhMaterialId" -> "int";
			case "mc_chunkFade" -> "float";
			case "at_midBlock", "vaPosition", "vaNormal" -> "vec3";
			case "vaUV1", "vaUV2" -> "ivec2";
			case "vaUV0" -> "vec2";
			default -> "vec4";
		};
	}
}
