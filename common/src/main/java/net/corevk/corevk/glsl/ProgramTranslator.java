package net.corevk.corevk.glsl;

import net.corevk.corevk.pack.model.AlphaTest;
import net.corevk.corevk.pack.model.ProgramStage;
import net.corevk.corevk.pack.source.IncludeExpander.ExpandedUnit;
import net.corevk.corevk.pack.texture.CustomStorage;
import net.corevk.corevk.pack.texture.VolumeAtlas;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ProgramTranslator {

	
	private static final List<ProgramStage> PIPELINE_ORDER = List.of(
			ProgramStage.VERTEX,
			ProgramStage.TESSELLATION_CONTROL,
			ProgramStage.TESSELLATION_EVALUATION,
			ProgramStage.GEOMETRY,
			ProgramStage.FRAGMENT,
			ProgramStage.COMPUTE);

	private ProgramTranslator() {
	}

	
	public record TranslatedProgram(Map<ProgramStage, TranslatedUnit> stages,
			List<TranslatedUnit.Uniform> uniforms, List<TranslatedUnit.Uniform> samplers,
			Set<String> sampled, Map<String, String> synthesized, VertexInputs inputs) {
	}

	
	public static TranslatedProgram translate(Map<ProgramStage, ExpandedUnit> units, String program) {
		return translate(units, VertexInputs.WORLD, program);
	}

	
	public static TranslatedProgram translate(Map<ProgramStage, ExpandedUnit> units, boolean fullscreen) {
		return translate(units, fullscreen ? VertexInputs.FULLSCREEN : VertexInputs.WORLD, "");
	}

	
	public static TranslatedProgram translate(Map<ProgramStage, ExpandedUnit> units,
			VertexInputs inputs, String program) {
		return translate(units, inputs, AlphaTest.OFF, program);
	}

	
	public static TranslatedProgram translate(Map<ProgramStage, ExpandedUnit> units,
			VertexInputs inputs, AlphaTest alphaTest, String program) {
		return translate(units, inputs, alphaTest, false, program);
	}

	
	public static TranslatedProgram translate(Map<ProgramStage, ExpandedUnit> units,
			VertexInputs inputs, AlphaTest alphaTest, boolean coverage, String program) {
		return translate(units, inputs, inputs.elements(), alphaTest, coverage, program, Map.of());
	}

	
	public static Set<String> reads(ExpandedUnit vertex, VertexInputs inputs, AlphaTest alphaTest,
			boolean coverage, String program, Map<String, VolumeAtlas> volumes) {
		// Clocked like the whole translations: this half-translation is real translator work a
		// chunk program pays before its full one, and leaving it out would undercount terrain.
		// The caller keeps the answer per opening, so the passes a file serves clock it once.
		long began = System.nanoTime();
		try {
			return GlslTranslator.prepare(vertex, ProgramStage.VERTEX, inputs, inputs.elements(),
					alphaTest, coverage, program, volumes).reads();
		} finally {
			LoadClock.translation(System.nanoTime() - began);
		}
	}

	
	public static TranslatedProgram translate(Map<ProgramStage, ExpandedUnit> units,
			VertexInputs inputs, List<String> boundElements, AlphaTest alphaTest, String program) {
		return translate(units, inputs, boundElements, alphaTest, false, program, Map.of());
	}

	
	public static TranslatedProgram translate(Map<ProgramStage, ExpandedUnit> units,
			VertexInputs inputs, List<String> boundElements, AlphaTest alphaTest, boolean coverage,
			String program, Map<String, VolumeAtlas> volumes) {
		// Every overload above funnels through here, which is what makes this the one place a
		// program's whole translation can be clocked, and the one place it can be kept.
		//
		// The clock encloses the cache rather than sitting beside it, so a served program is
		// counted like a translated one: the figure is what getting a program cost, whichever way
		// it came, and a load whose translation post has collapsed says so instead of going quiet.
		long began = System.nanoTime();
		try {
			String key = TranslationCache.keyOf(units, inputs, boundElements, alphaTest, coverage,
					program, volumes);
			TranslatedProgram served = TranslationCache.lookup(key, inputs);
			if (served != null) {
				return declared(served);
			}

			TranslatedProgram built =
					translated(units, inputs, boundElements, alphaTest, coverage, program, volumes);
			TranslationCache.store(key, built);

			return declared(built);
		} finally {
			LoadClock.translation(System.nanoTime() - began);
		}
	}

	
	private static TranslatedProgram declared(TranslatedProgram program) {
		program.stages().values().forEach(stage -> stage.notes().storageBlocks()
				.forEach(block -> CustomStorage.declare(block.name(), block.binding())));

		return program;
	}

	private static TranslatedProgram translated(Map<ProgramStage, ExpandedUnit> units,
			VertexInputs inputs, List<String> boundElements, AlphaTest alphaTest, boolean coverage,
			String program, Map<String, VolumeAtlas> volumes) {
		Map<ProgramStage, GlslTranslator.Stage> prepared = new LinkedHashMap<>();
		for (ProgramStage stage : PIPELINE_ORDER) {
			ExpandedUnit unit = units.get(stage);
			if (unit != null) {
				prepared.put(stage, GlslTranslator.prepare(unit, stage, inputs, boundElements,
						alphaTest, coverage, program, volumes));
			}
		}

		// Before anything is read off a stage, because it changes what the stages read. A stage is
		// only ever asked to give up an input against what the stages BEFORE it write, which is the
		// order rebind pairs them in, so this walks the pipeline and not the map it filled.
		if (prepared.containsKey(ProgramStage.VERTEX)) {
			Set<String> provided = new LinkedHashSet<>();
			for (ProgramStage stage : PIPELINE_ORDER) {
				GlslTranslator.Stage prepare = prepared.get(stage);
				if (prepare == null) {
					continue;
				}

				if (stage != ProgramStage.VERTEX) {
					prepare.dropUnprovidedInputs(provided);
				}

				provided.addAll(prepare.provides());
			}

			// What the drop would not take, handed back the other way. Taking it out was the first
			// answer because it is the one that changes nothing; what is left is read by the body, so
			// the only answers remaining are to have the stage before hand it over or to lose the
			// pass. Mellow's deferred1 is drawn over a quad and includes the header its geometry
			// passes use, which declares eleven varyings; three of them are read by functions this
			// pass never calls, and the game refused the whole module over exactly those three.
			owed(prepared);

			// And last, the other way round, because it reads what the two above have just settled:
			// the first takes inputs out and the second adds outputs, and both change the answer.
			withheld(prepared);
		}

		Map<String, TranslatedUnit.Uniform> uniforms = new LinkedHashMap<>();
		Map<String, TranslatedUnit.Uniform> samplers = new LinkedHashMap<>();
		Map<String, String> memoryQualifiers = new LinkedHashMap<>();
		Map<String, String> imageFormats = new LinkedHashMap<>();
		Map<String, String> synthesized = new LinkedHashMap<>();
		Set<String> varyings = new LinkedHashSet<>();

		// A pass of its own and ahead of everything else, because one of these answers changes
		// another: the overlay colour is a varying the vertex stage owes as soon as ANY stage reads
		// it, and owing it is what makes that stage declare the texture it is fetched from. Asking
		// for the samplers in the same walk would collect the vertex stage's before the fragment
		// stage had said it wanted the colour.
		for (GlslTranslator.Stage stage : prepared.values()) {
			varyings.addAll(stage.varyings());
		}

		for (GlslTranslator.Stage stage : prepared.values()) {
			stage.makesOverlayColour(varyings);
		}

		for (GlslTranslator.Stage stage : prepared.values()) {
			synthesized.putAll(stage.synthesized());
			// First declaration of a name wins, as it does within one stage. A name declared
			// under two types in two stages is the pack's problem, and taking the first keeps the
			// answer the same whichever stage is looked at.
			stage.uniforms().forEach(uniform -> uniforms.putIfAbsent(uniform.name(), uniform));
			stage.samplers().forEach(sampler -> samplers.putIfAbsent(sampler.name(), sampler));
			stage.memoryQualifiers().forEach(memoryQualifiers::putIfAbsent);
			stage.imageFormats().forEach(imageFormats::putIfAbsent);
		}

		Set<String> sampled = new LinkedHashSet<>();
		for (GlslTranslator.Stage stage : prepared.values()) {
			sampled.addAll(stage.sampled());
		}

		List<TranslatedUnit.Uniform> block = fixedFunctionFirst(uniforms);
		List<TranslatedUnit.Uniform> bound = sampledFirst(samplers, sampled);
		Set<String> elements = clashingElements(prepared, inputs);

		Map<ProgramStage, TranslatedUnit> translated = new LinkedHashMap<>();
		prepared.forEach((stage, prepare) -> {
			Set<String> shadowed = new LinkedHashSet<>(elements);
			shadowed.addAll(shadowedBy(prepare, block));
			translated.put(stage, prepare.render(block, bound, varyings, shadowed,
					memoryQualifiers, imageFormats));
		});

		return new TranslatedProgram(Map.copyOf(translated), block, bound, Set.copyOf(sampled),
				Map.copyOf(synthesized), inputs);
	}

	
	private static void owed(Map<ProgramStage, GlslTranslator.Stage> prepared) {
		GlslTranslator.Stage previous = null;
		for (GlslTranslator.Stage stage : prepared.values()) {
			if (previous != null) {
				Map<String, String> declarations = stage.unprovided();
				if (!declarations.isEmpty()) {
					previous.owe(declarations);
				}
			}

			previous = stage;
		}
	}

	
	private static void withheld(Map<ProgramStage, GlslTranslator.Stage> prepared) {
		GlslTranslator.Stage previous = null;
		for (Map.Entry<ProgramStage, GlslTranslator.Stage> entry : prepared.entrySet()) {
			if (entry.getKey() == ProgramStage.COMPUTE) {
				continue;
			}

			if (previous != null) {
				previous.withhold(entry.getValue().requires());
			}

			previous = entry.getValue();
		}
	}

	
	private static Set<String> clashingElements(Map<ProgramStage, GlslTranslator.Stage> prepared,
			VertexInputs inputs) {
		Set<String> clashing = new LinkedHashSet<>();

		for (String element : inputs.elements()) {
			if (prepared.values().stream().anyMatch(stage -> stage.declared().contains(element))) {
				clashing.add(element);
			}
		}

		return clashing;
	}

	
	private static Set<String> shadowedBy(GlslTranslator.Stage stage,
			List<TranslatedUnit.Uniform> block) {
		Set<String> shadowed = new LinkedHashSet<>();

		for (TranslatedUnit.Uniform member : block) {
			if (stage.declared().contains(member.name()) && !stage.lifted().contains(member.name())) {
				shadowed.add(member.name());
			}
		}

		return shadowed;
	}

	
	private static List<TranslatedUnit.Uniform> fixedFunctionFirst(
			Map<String, TranslatedUnit.Uniform> uniforms) {
		List<TranslatedUnit.Uniform> block = new ArrayList<>();

		for (String name : LegacyGlsl.FIXED_FUNCTION_MEMBERS.keySet()) {
			TranslatedUnit.Uniform member = uniforms.get(name);
			if (member != null) {
				block.add(member);
			}
		}

		for (Map.Entry<String, TranslatedUnit.Uniform> member : uniforms.entrySet()) {
			if (!LegacyGlsl.FIXED_FUNCTION_MEMBERS.containsKey(member.getKey())) {
				block.add(member.getValue());
			}
		}

		return List.copyOf(block);
	}

	
	private static List<TranslatedUnit.Uniform> sampledFirst(
			Map<String, TranslatedUnit.Uniform> samplers, Set<String> sampled) {
		List<TranslatedUnit.Uniform> bound = new ArrayList<>();
		for (TranslatedUnit.Uniform sampler : samplers.values()) {
			if (sampled.contains(sampler.name())) {
				bound.add(sampler);
			}
		}

		for (TranslatedUnit.Uniform sampler : samplers.values()) {
			if (!sampled.contains(sampler.name())) {
				bound.add(sampler);
			}
		}

		return List.copyOf(bound);
	}
}
