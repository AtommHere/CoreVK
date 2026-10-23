package net.corevk.corevk.glsl;

import net.corevk.corevk.glsl.GlslTranslator.Output;
import net.corevk.corevk.glsl.VaryingSplit.SplitArray;
import net.corevk.corevk.glsl.VaryingSplit.SplitMatrix;
import net.corevk.corevk.glsl.VaryingSplit.SplitStruct;
import net.corevk.corevk.glsl.VaryingSplit.StructMember;
import net.corevk.corevk.pack.model.AlphaTest;
import net.corevk.corevk.pack.model.ProgramStage;
import net.corevk.corevk.pack.texture.CustomImages;
import net.corevk.corevk.pack.texture.VolumeAtlas;
import net.corevk.corevk.uniform.ClipSpace;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

record Emitter(ProgramStage stage, VertexInputs inputs, List<String> bound, AlphaTest alphaTest,
		Set<String> extensions,
		Map<String, String> engineDefines, Map<String, String> memoryQualifiers,
		Map<String, String> imageFormats, Set<String> used,
		Set<String> declaredNames, Map<String, String> synthesized,
		Map<String, VolumeAtlas> readVolumes, Map<Integer, Output> packOutputs,
		int maxFragmentOutput, Map<String, String> owedOutputs, VaryingSplit splits,
		int gameTextureMatrix, int gameModelView, int softRewrites, int trigCalls, int hashCalls,
		Set<String> packBuiltinCalls,
		boolean mainWrapped, boolean depthEpilogue, boolean terrainPrologue,
		boolean distantPrologue, boolean entityWrapped, boolean linesWrapped,
		boolean alphaEpilogue, boolean covers,
		boolean wrapsFragment, boolean ordered, boolean namesFragDepth, boolean makesOverlayColour,
		boolean coreProfile) {

	private static final String VERSION = "#version 460 core";

	private static final String UNIFORM_BLOCK = "OfGlobals";

	private static final Set<String> INTEGER_MEMBER_TYPES = Set.of(
			"int", "ivec2", "ivec3", "ivec4", "uint", "uvec2", "uvec3", "uvec4",
			"double", "dvec2", "dvec3", "dvec4");

	private static final String OVERLAY_TEXEL = "ofOverlayTexel";

	private static final String FOG_STRUCT =
			"struct OfFog { vec4 color; float density; float start; float end; float scale; };";

	private static final String COVERAGE = "ofCoverage";

	private static final String FAR_PLANE = ClipSpace.REVERSED.z < 0.0F ? "0.0" : "1.0";

	String header(List<TranslatedUnit.Uniform> block, List<TranslatedUnit.Uniform> samplers,
			Set<String> varyings, Set<String> shadowed) {
		List<String> lines = new ArrayList<>();
		lines.add(VERSION);

		this.extensions.forEach(extension -> lines.add("#extension " + extension + " : enable"));

		for (Map.Entry<String, String> define : this.engineDefines.entrySet()) {
			lines.add(define.getValue().isEmpty()
					? "#define " + define.getKey()
					: "#define " + define.getKey() + " " + define.getValue());
		}

		if (block.stream().anyMatch(member -> member.name().equals("of_Fog"))) {
			lines.add(FOG_STRUCT);
		}

		if (!block.isEmpty()) {
			lines.add("layout(std140) uniform " + UNIFORM_BLOCK + " {");
			for (TranslatedUnit.Uniform member : block) {
				lines.add("\t" + member.declaration() + ";");
			}

			lines.add("};");
		}

		if (this.gameTextureMatrix > 0 || this.gameModelView > 0) {
			lines.addAll(GameTransforms.BLOCK);
		}

		for (TranslatedUnit.Uniform sampler : samplers) {
			lines.add(declareOpaque(sampler));
		}

		if (this.stage == ProgramStage.VERTEX) {
			switch (this.inputs) {
				case FULLSCREEN -> {
					lines.addAll(LegacyGlsl.FULLSCREEN_ATTRIBUTES);
					VertexPrologue.globals(this.used, this.synthesized, Map.of()).forEach((name, type) -> {
						String element = this.coreProfile ? LegacyGlsl.fullscreenElement(name, type) : null;
						lines.add(element != null
								? "#define " + name + " " + element
								: VertexPrologue.declaration(name, type));
					});
				}
				case TERRAIN, TERRAIN_SEPARATE_AO -> lines.addAll(SodiumVertex.prologue(this.bound,
						this.used, this.synthesized, this.inputs.separateAo()));
				case ENTITY, ENTITY_FULLBRIGHT -> lines.addAll(
						EntityVertex.prologue(this.used, this.synthesized, this.inputs.fullbright()));
				case GLINT -> lines.addAll(GlintVertex.prologue(this.used, this.synthesized));
				case CRUMBLING -> lines.addAll(CrumblingVertex.prologue(this.used, this.synthesized));
				case MOVING_BLOCK -> lines.addAll(
						MovingBlockVertex.prologue(this.used, this.synthesized));
				case GLYPH -> lines.addAll(
						GlyphVertex.prologue(this.bound, this.used, this.synthesized));
				case LINES -> {
					lines.addAll(LinesVertex.prologue(this.used, this.synthesized));
					lines.addAll(LinesVertex.widen());
				}
				case PARTICLE -> lines.addAll(ParticleVertex.prologue(this.used, this.synthesized));
				case SKY -> lines.addAll(SkyVertex.prologue(this.bound, this.used, this.synthesized));
				case CLOUDS -> lines.addAll(CloudVertex.prologue(this.used, this.synthesized));
				case DISTANT -> lines.addAll(
						DistantVertex.prologue(this.bound, this.used, this.synthesized));
				case WORLD -> {
					for (Map.Entry<String, String> attribute : LegacyGlsl.FIXED_ATTRIBUTES.entrySet()) {
						if (this.used.contains(attribute.getKey())) {
							lines.add("in " + attribute.getValue() + ";");
						}
					}

					for (Map.Entry<String, String> attribute : LegacyGlsl.ENGINE_ATTRIBUTES.entrySet()) {
						if (this.used.contains(attribute.getKey())
								&& !this.declaredNames.contains(attribute.getKey())) {
							lines.add("in " + attribute.getValue() + ";");
						}
					}
				}
			}

			lines.add("invariant gl_Position;");
		}

		if (this.softRewrites > 0) {
			lines.add("float " + GlslTranslator.SHADOW_COMPARE
					+ "(sampler2D ofMap, vec3 ofAt, ivec2 ofOffset) {"
					+ " vec2 ofSize = vec2(textureSize(ofMap, 0));"
					+ " vec2 ofUv = ofAt.xy + vec2(ofOffset) / ofSize;"
					+ " vec4 ofTests = step(vec4(ofAt.z), textureGather(ofMap, ofUv, 0));"
					+ " vec2 ofPart = fract(ofUv * ofSize - 0.5);"
					+ " return mix(mix(ofTests.w, ofTests.z, ofPart.x),"
					+ " mix(ofTests.x, ofTests.y, ofPart.x), ofPart.y); }");
			lines.add("float " + GlslTranslator.SHADOW_COMPARE + "(sampler2D ofMap, vec3 ofAt) {"
					+ " return " + GlslTranslator.SHADOW_COMPARE + "(ofMap, ofAt, ivec2(0)); }");
			lines.add("float " + GlslTranslator.SHADOW_COMPARE
					+ "(sampler2D ofMap, vec3 ofAt, float ofLod) {"
					+ " return " + GlslTranslator.SHADOW_COMPARE + "(ofMap, ofAt, ivec2(0)); }");
			lines.add("float " + GlslTranslator.SHADOW_COMPARE
					+ "(sampler2D ofMap, vec3 ofAt, float ofLod, ivec2 ofOffset) {"
					+ " return " + GlslTranslator.SHADOW_COMPARE + "(ofMap, ofAt, ofOffset); }");

			lines.add("float " + GlslTranslator.SHADOW_COMPARE
					+ "(sampler2D ofMap, vec3 ofAt, ivec2 ofOffset, float ofBias) {"
					+ " return " + GlslTranslator.SHADOW_COMPARE + "(ofMap, ofAt, ofOffset); }");
		}

		if (this.trigCalls > 0) {
			for (String shape : new String[] {"float", "vec2", "vec3", "vec4"}) {
				lines.add(shape + " " + GlslTranslator.REDUCED_SIN + "(" + shape + " ofX) {"
						+ " " + shape + " ofK = floor(ofX * 0.15915494);"
						+ " " + shape + " ofR = ofX - ofK * 6.28125 - ofK * 1.9353072e-3;"
						+ " ofR -= 6.2831855 * step(3.1415927, ofR);"
						+ " " + shape + " ofS = sign(ofR);"
						+ " " + shape + " ofA = 1.5707964 - abs(abs(ofR) - 1.5707964);"
						+ " " + shape + " ofZ = ofA * ofA;"
						+ " return clamp(ofS * ofA * (1.0 + ofZ * (-1.6666654611e-1"
						+ " + ofZ * (8.3321608736e-3 + ofZ * (-1.9515295891e-4)))), -1.0, 1.0); }");
				lines.add(shape + " " + GlslTranslator.REDUCED_COS + "(" + shape + " ofX) {"
						+ " return " + GlslTranslator.REDUCED_SIN + "(ofX + 1.5707964); }");
			}
		}

		if (this.hashCalls > 0) {
			lines.add("float " + GlslTranslator.HASH + "(vec2 ofP) {"
					+ " uvec2 ofV = floatBitsToUint(ofP);"
					+ " uint ofN = (ofV.x * 1597334677u) ^ (ofV.y * 3812015801u);"
					+ " ofN ^= ofN >> 16u;"
					+ " ofN *= 2246822519u;"
					+ " ofN ^= ofN >> 13u;"
					+ " ofN *= 3266489917u;"
					+ " ofN ^= ofN >> 16u;"
					+ " return float(ofN) * 2.3283064365386963e-10; }");
			lines.add("float " + GlslTranslator.HASH + "(vec3 ofP) {"
					+ " uvec3 ofV = floatBitsToUint(ofP);"
					+ " uint ofN = ofV.x ^ (ofV.y * 1597334677u) ^ (ofV.z * 3812015801u);"
					+ " ofN ^= ofN >> 16u;"
					+ " ofN *= 2246822519u;"
					+ " ofN ^= ofN >> 13u;"
					+ " ofN *= 3266489917u;"
					+ " ofN ^= ofN >> 16u;"
					+ " return float(ofN) * 2.3283064365386963e-10; }");
		}

		lines.addAll(PackBuiltins.definitions(this.packBuiltinCalls));

		this.readVolumes.forEach((name, atlas) -> lines.addAll(VolumeFlattening.helper(name, atlas)));

		if (varyings.contains(GlslTranslator.FOG_COORD)) {
			lines.add((this.stage == ProgramStage.VERTEX ? "out" : "in") + " float "
					+ GlslTranslator.FOG_COORD + ";");
		}

		if (this.stage == ProgramStage.VERTEX && this.used.contains(GlslTranslator.FRONT_COLOUR)) {
			lines.add("vec4 " + GlslTranslator.FRONT_COLOUR + ";");
		}

		if (varyings.contains(GlslTranslator.ENTITY_COLOR)) {
			lines.add((this.stage == ProgramStage.VERTEX ? "out" : "in") + " vec4 "
					+ GlslTranslator.ENTITY_COLOR + ";");
		}

		for (String identifier : GlslTranslator.ENTITY_IDS) {
			if (varyings.contains(identifier)) {
				lines.add("flat " + (this.stage == ProgramStage.VERTEX ? "out" : "in") + " int "
						+ identifier + ";");
			}
		}

		for (SplitMatrix split : this.splits.matrices()) {
			lines.add(split.matrixType() + " " + split.name() + ";");
			String storage = split.input() ? "in" : "out";
			String qualified = split.qualifier().isEmpty()
					? storage + " " + split.columnType()
					: split.qualifier() + " " + storage + " " + split.columnType();
			for (int column = 0; column < split.columns(); column++) {
				lines.add(qualified + " " + VaryingSplit.matrixColumnName(split.name(), column) + ";");
			}
		}

		Set<String> defined = new HashSet<>();
		for (SplitStruct split : this.splits.structs()) {
			if (defined.add(split.structType())) {
				StringBuilder definition = new StringBuilder("struct ").append(split.structType())
						.append(" { ");
				for (StructMember member : split.members()) {
					definition.append(member.type()).append(' ').append(member.name()).append("; ");
				}

				lines.add(definition.append("};").toString());
			}

			lines.add(split.structType() + " " + split.name() + ";");
			String storage = split.input() ? "in" : "out";
			for (StructMember member : split.members()) {

				String qualifier = split.qualifier();
				if (INTEGER_MEMBER_TYPES.contains(member.type()) && !qualifier.contains("flat")) {
					qualifier = qualifier.isEmpty() ? "flat" : "flat " + qualifier;
				}

				String qualified = qualifier.isEmpty()
						? storage + " " + member.type()
						: qualifier + " " + storage + " " + member.type();
				lines.add(qualified + " "
						+ VaryingSplit.structMemberName(split.name(), member.name()) + ";");
			}
		}

		for (SplitArray split : this.splits.arrays()) {
			lines.add(split.type() + " " + split.name() + "[" + split.size() + "];");
			String storage = split.input() ? "in" : "out";
			String qualifier = split.qualifier();
			if (INTEGER_MEMBER_TYPES.contains(split.type()) && !qualifier.contains("flat")) {
				qualifier = qualifier.isEmpty() ? "flat" : "flat " + qualifier;
			}

			String qualified = qualifier.isEmpty()
					? storage + " " + split.type()
					: qualifier + " " + storage + " " + split.type();
			for (int element = 0; element < split.size(); element++) {
				lines.add(qualified + " " + VaryingSplit.arrayElementName(split.name(), element) + ";");
			}
		}

		for (int slot = 0; slot <= this.maxFragmentOutput; slot++) {
			Output output = this.packOutputs.get(slot);
			lines.add("layout(location = " + slot + ") out "
					+ (output == null ? "vec4" : output.type()) + " " + outputName(slot, shadowed) + ";");
		}

		if (this.covers) {
			lines.add("layout(location = " + (this.maxFragmentOutput + 1) + ") out float "
					+ COVERAGE + ";");
		}

		if (this.ordered) {
			StringBuilder order = new StringBuilder("void " + GlslTranslator.ORDER_OUTPUTS + "() {");
			for (int slot = 0; slot <= this.maxFragmentOutput; slot++) {
				order.append(' ').append(outputName(slot, shadowed)).append(';');
			}

			if (this.covers) {
				order.append(' ').append(COVERAGE).append(';');
			}

			lines.add(order.append(" }").toString());
		}

		this.owedOutputs.forEach((name, qualified) -> lines.add(outDeclaration(name, qualified)));

		return String.join("\n", lines) + "\n";
	}

	String wrapper(Set<String> varyings, Set<String> shadowed) {
		if (!this.mainWrapped) {
			return "";
		}

		String body = this.linesWrapped
				? LinesVertex.OFFSET + " = Normal.xyz; " + GlslTranslator.PACK_MAIN
						+ "(); vec4 of_LineEnd = "
						+ "gl_Position; gl_Position = vec4(0.0); " + LinesVertex.OFFSET
						+ " = vec3(0.0); " + GlslTranslator.PACK_MAIN + "(); " + LinesVertex.WIDEN
						+ "(gl_Position, of_LineEnd);"
				: GlslTranslator.PACK_MAIN + "();";

		return "void main() { "
				+ (this.terrainPrologue ? SodiumVertex.PROLOGUE + "(); " : "")
				+ (this.distantPrologue ? DistantVertex.PROLOGUE + "(); " : "")
				+ overlayPrologue()
				+ identifierPrologue(varyings)
				+ (wrapsFragment() ? GlslTranslator.ORDER_OUTPUTS + "(); " : "")
				+ coveragePrologue()
				+ owedPrologue()
				+ this.splits.matrixPrologue()
				+ this.splits.structPrologue()
				+ this.splits.arrayPrologue()
				+ body
				+ this.splits.matrixEpilogue()
				+ this.splits.structEpilogue()
				+ this.splits.arrayEpilogue()
				+ (this.depthEpilogue ? " gl_Position.z = " + GlslTranslator.DEPTH_CONV
						+ ".x * gl_Position.z + " + GlslTranslator.DEPTH_CONV
						+ ".y * gl_Position.w;" : "")
				+ (this.alphaEpilogue
						? " " + this.alphaTest.discard(outputName(0, shadowed) + ".a")
						: "")
				+ (this.covers ? " " + COVERAGE + " = "
						+ (this.inputs == VertexInputs.SKY ? FAR_PLANE : writtenDepth()) + ";" : "")
				+ " }\n";
	}

	private boolean owesInitialisers() {
		return this.mainWrapped && !this.owedOutputs.isEmpty();
	}

	private String owedPrologue() {
		if (!owesInitialisers()) {
			return "";
		}

		StringBuilder assignments = new StringBuilder();
		this.owedOutputs.forEach((name, qualified) ->
				assignments.append(initialiser(name, qualified)).append(' '));

		return assignments.toString();
	}

	private String writtenDepth() {
		return this.namesFragDepth ? "gl_FragDepth" : "gl_FragCoord.z";
	}

	private String coveragePrologue() {
		return this.covers && this.namesFragDepth && this.inputs != VertexInputs.SKY
				? "gl_FragDepth = gl_FragCoord.z; "
				: "";
	}

	private String overlayPrologue() {
		if (!this.makesOverlayColour) {
			return "";
		}

		return "vec4 " + OVERLAY_TEXEL + " = texelFetch(" + GlslTranslator.OVERLAY + ", UV1, 0); "
				+ GlslTranslator.ENTITY_COLOR + " = vec4(" + OVERLAY_TEXEL + ".rgb, 1.0 - "
				+ OVERLAY_TEXEL + ".a); "
				+ GlslTranslator.ENTITY_COLOR + ".rgb *= float(" + GlslTranslator.ENTITY_COLOR
				+ ".a != 0.0); ";
	}

	private String identifierPrologue(Set<String> varyings) {
		if (!this.entityWrapped) {
			return "";
		}

		StringBuilder written = new StringBuilder();
		for (int lane = 0; lane < GlslTranslator.ENTITY_IDS.size(); lane++) {
			String identifier = GlslTranslator.ENTITY_IDS.get(lane);
			if (varyings.contains(identifier)) {
				written.append(identifier).append(" = int(")
						.append(EntityVertex.IDENTIFIERS).append('[').append(lane).append("]); ");
			}
		}

		return written.toString();
	}

	private String outputName(int slot, Set<String> shadowed) {
		Output output = this.packOutputs.get(slot);
		if (output == null) {
			return "ofFragData" + slot;
		}

		return shadowed.contains(output.name()) ? "ofOwn_" + output.name() : output.name();
	}

	private String declareOpaque(TranslatedUnit.Uniform sampler) {
		String memory = this.memoryQualifiers.getOrDefault(sampler.name(), "");
		String tail = (memory.isEmpty() ? "" : memory + " ") + "uniform " + sampler.declaration()
				+ ";";
		if (!LegacyGlsl.isImageType(sampler.type())) {
			return tail;
		}

		return Optional.ofNullable(this.imageFormats.get(sampler.name()))
				.or(() -> CustomImages.layoutFormat(sampler.name()))
				.map(format -> "layout(" + format + ") " + tail)
				.orElse(tail);
	}

	private static String outDeclaration(String name, String qualified) {
		int type = qualified.lastIndexOf(' ');

		return type < 0
				? "out " + qualified + " " + name + ";"
				: qualified.substring(0, type) + " out " + qualified.substring(type + 1) + " " + name + ";";
	}

	private static String initialiser(String name, String qualified) {
		int type = qualified.lastIndexOf(' ');

		return name + " = " + qualified.substring(type + 1) + "(0);";
	}
}
