package net.corevk.corevk.glsl;

import net.corevk.corevk.glsl.GlslLexer.Kind;
import net.corevk.corevk.glsl.GlslLexer.Token;
import net.corevk.corevk.pack.source.IncludeExpander.ExpandedUnit;
import net.corevk.corevk.pack.target.SamplerPlan;
import net.corevk.corevk.pack.target.SamplerTypes;
import net.corevk.corevk.pack.texture.VolumeAtlas;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class VolumeFlattening {

	private static final String VOLUME_LOOKUP = "ofTexture3D_";

	private static final int LOOKUP_ARGUMENTS = 2;

	private static final int LEVELLED_ARGUMENTS = 3;

	private final TokenStream tokens;
	private final ExpandedUnit unit;

	private final Map<String, VolumeAtlas> volumes;

	private final Set<String> packMacros;
	private final Map<String, String> macroAliases;

	private final Map<String, VolumeAtlas> readVolumes = new LinkedHashMap<>();

	private int volumeLookups;
	private int volumesLeftAlone;

	VolumeFlattening(TokenStream tokens, ExpandedUnit unit, Map<String, VolumeAtlas> volumes,
			Set<String> packMacros, Map<String, String> macroAliases) {
		this.tokens = tokens;
		this.unit = unit;
		this.volumes = Collections.unmodifiableMap(new LinkedHashMap<>(volumes));
		this.packMacros = packMacros;
		this.macroAliases = macroAliases;
	}

	Map<String, VolumeAtlas> read() {
		return this.readVolumes;
	}

	int lookups() {
		return this.volumeLookups;
	}

	int leftAlone() {
		return this.volumesLeftAlone;
	}

	void flatten() {
		if (this.volumes.isEmpty()) {
			return;
		}

		int[] lines = this.tokens.lineNumbers();
		this.volumes.forEach((name, atlas) -> flattenOne(name, atlas, lines));
	}

	private void flattenOne(String name, VolumeAtlas atlas, int[] lines) {

		List<Integer> declarations = new ArrayList<>();
		Map<Integer, Integer> lookups = new LinkedHashMap<>();
		boolean elsewhere = this.packMacros.contains(name);
		Set<String> spellings = spellingsOf(name);

		for (int index = 0; index < this.tokens.size(); index++) {
			Token token = this.tokens.get(index);
			if (token.kind() != Kind.IDENTIFIER || !spellings.contains(token.text())
					|| !this.unit.isLive(lines[index])) {
				continue;
			}

			if (token.directive() != null) {

				if (token.text().equals(name) && !aliasBody(index)) {
					elsewhere = true;
				}

				continue;
			}

			int callee = plainLookup(index);
			if (token.text().equals(name) && volumeDeclaration(index)) {
				declarations.add(index);
			} else if (callee >= 0) {
				lookups.put(index, callee);
			} else {
				elsewhere = true;
			}
		}

		if (declarations.isEmpty()) {
			return;
		}

		if (elsewhere) {
			this.volumesLeftAlone++;
			return;
		}

		String forged = SamplerPlan.forged(name);
		for (int declaration : declarations) {
			this.tokens.replace(this.tokens.significantBefore(declaration), "sampler2D");
			this.tokens.replace(declaration, forged);
		}

		lookups.forEach((argument, callee) -> {
			this.tokens.replace(callee, VOLUME_LOOKUP + name);
			this.tokens.replace(argument, forged);
		});

		this.volumeLookups += lookups.size();
		if (!lookups.isEmpty()) {
			this.readVolumes.put(name, atlas);
		}
	}

	private Set<String> spellingsOf(String name) {
		Set<String> spellings = new HashSet<>();
		spellings.add(name);
		boolean grew = true;
		while (grew) {
			grew = false;
			for (Map.Entry<String, String> alias : this.macroAliases.entrySet()) {
				if (spellings.contains(alias.getValue()) && spellings.add(alias.getKey())) {
					grew = true;
				}
			}
		}

		return spellings;
	}

	private boolean aliasBody(int index) {
		for (int scan = index - 1; scan >= 0; scan--) {
			Token token = this.tokens.get(scan);
			if (token.trivia()) {
				continue;
			}

			return token.macroName()
					&& this.tokens.get(index).text().equals(this.macroAliases.get(token.text()));
		}

		return false;
	}

	private boolean volumeDeclaration(int index) {
		int type = this.tokens.significantBefore(index);
		if (type < 0 || this.tokens.get(type).kind() != Kind.IDENTIFIER
				|| !"3D".equals(SamplerTypes.shapeOf(this.tokens.get(type).text()))) {
			return false;
		}

		int cursor = this.tokens.significantBefore(type);
		while (cursor >= 0 && GlslTranslator.isQualifier(this.tokens.get(cursor))) {
			cursor = this.tokens.significantBefore(cursor);
		}

		return cursor >= 0 && this.tokens.get(cursor).identifier("uniform");
	}

	private int plainLookup(int index) {
		int open = this.tokens.significantBefore(index);
		if (open < 0 || !this.tokens.get(open).operator("(")) {
			return -1;
		}

		int callee = this.tokens.significantBefore(open);
		if (callee < 0 || !(this.tokens.get(callee).identifier(GlslTranslator.LOOKUP)
				|| this.tokens.get(callee).identifier(GlslTranslator.LEVELLED_LOOKUP))) {
			return -1;
		}

		int close = this.tokens.matchingBracket(open);
		if (close < 0) {
			return -1;
		}

		int arguments = arguments(open, close);

		return arguments == LOOKUP_ARGUMENTS || arguments == LEVELLED_ARGUMENTS ? callee : -1;
	}

	private int arguments(int open, int close) {
		int depth = 0;
		int count = 1;

		for (int index = open; index < close; index++) {
			Token token = this.tokens.get(index);
			if (token.kind() != Kind.OPERATOR || token.directive() != null) {
				continue;
			}

			String text = token.text();
			if (text.equals("(") || text.equals("[")) {
				depth++;
			} else if (text.equals(")") || text.equals("]")) {
				depth--;
			} else if (depth == 1 && text.equals(",")) {
				count++;
			}
		}

		return count;
	}

	static List<String> helper(String name, VolumeAtlas atlas) {
		String depth = whole(atlas.depth());
		String last = Integer.toString(atlas.depth() - 1);
		String tiles = Integer.toString(atlas.tilesPerRow());

		List<String> lines = new ArrayList<>();
		lines.add("vec4 " + VOLUME_LOOKUP + name + "(sampler2D ofMap, vec3 ofAt) {");
		lines.add(atlas.clamp() ? "\tvec3 ofQ = clamp(ofAt, 0.0, 1.0);" : "\tvec3 ofQ = fract(ofAt);");
		lines.add("\tfloat ofZ = ofQ.z * " + depth + " - 0.5;");
		lines.add("\tfloat ofBase = floor(ofZ);");
		lines.add("\tvec2 ofIn = ofQ.xy * vec2(" + whole(atlas.width()) + ", " + whole(atlas.height())
				+ ") + " + whole(VolumeAtlas.GUTTER) + ";");
		if (atlas.clamp()) {
			lines.add("\tint ofNear = clamp(int(ofBase), 0, " + last + ");");
			lines.add("\tint ofFar = clamp(int(ofBase) + 1, 0, " + last + ");");
		} else {
			lines.add("\tint ofNear = int(mod(ofBase, " + depth + "));");
			lines.add("\tint ofFar = int(mod(ofBase + 1.0, " + depth + "));");
		}

		lines.add("\tvec2 ofTile = vec2(" + whole(atlas.tileStride()) + ", " + whole(atlas.tileHeight())
				+ ");");
		lines.add("\tvec2 ofSize = vec2(" + whole(atlas.atlasWidth()) + ", " + whole(atlas.atlasHeight())
				+ ");");
		lines.add("\tvec2 ofA = (vec2(ofNear % " + tiles + ", ofNear / " + tiles
				+ ") * ofTile + ofIn) / ofSize;");
		lines.add("\tvec2 ofB = (vec2(ofFar % " + tiles + ", ofFar / " + tiles
				+ ") * ofTile + ofIn) / ofSize;");
		lines.add("\treturn mix(texture(ofMap, ofA), texture(ofMap, ofB), clamp(ofZ - ofBase, 0.0, 1.0));");
		lines.add("}");

		lines.add("vec4 " + VOLUME_LOOKUP + name + "(sampler2D ofMap, vec3 ofAt, float ofLevel) {");
		lines.add("\treturn " + VOLUME_LOOKUP + name + "(ofMap, ofAt);");
		lines.add("}");

		return lines;
	}

	private static String whole(int value) {
		return value + ".0";
	}
}
