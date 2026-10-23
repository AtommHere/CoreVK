package net.corevk.corevk.glsl;

import net.corevk.corevk.pack.model.ProgramStage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class TranslatedProgramCodec {

	static final String FORMAT = "corevk-translation-6";

	private TranslatedProgramCodec() {
	}

	static byte[] write(ProgramTranslator.TranslatedProgram program) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();

		try (DataOutputStream out = new DataOutputStream(bytes)) {
			text(out, program.inputs().name());

			out.writeInt(program.stages().size());
			for (Map.Entry<ProgramStage, TranslatedUnit> stage : program.stages().entrySet()) {
				text(out, stage.getKey().name());
				unit(out, stage.getValue());
			}

			uniforms(out, program.uniforms());
			uniforms(out, program.samplers());
			names(out, List.copyOf(program.sampled()));

			out.writeInt(program.synthesized().size());
			for (Map.Entry<String, String> made : program.synthesized().entrySet()) {
				text(out, made.getKey());
				text(out, made.getValue());
			}
		}

		return bytes.toByteArray();
	}

	static ProgramTranslator.TranslatedProgram read(byte[] raw, int length, VertexInputs inputs)
			throws IOException {
		try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(raw, 0, length))) {
			String written = text(in);
			if (!written.equals(inputs.name())) {
				throw new IOException("a stored translation was made for " + written);
			}

			Map<ProgramStage, TranslatedUnit> stages = new LinkedHashMap<>();
			for (int left = in.readInt(); left > 0; left--) {
				stages.put(ProgramStage.valueOf(text(in)), unit(in));
			}

			List<TranslatedUnit.Uniform> uniforms = uniforms(in);
			List<TranslatedUnit.Uniform> samplers = uniforms(in);
			Set<String> sampled = Set.copyOf(names(in));

			Map<String, String> synthesized = new LinkedHashMap<>();
			for (int left = in.readInt(); left > 0; left--) {
				synthesized.put(text(in), text(in));
			}

			return new ProgramTranslator.TranslatedProgram(Map.copyOf(stages), uniforms, samplers,
					sampled, Map.copyOf(synthesized), inputs);
		} catch (IllegalArgumentException | NullPointerException e) {

			throw new IOException(e);
		}
	}

	private static void unit(DataOutputStream out, TranslatedUnit unit) throws IOException {
		text(out, unit.entry());
		text(out, unit.stage().name());
		text(out, unit.text());
		notes(out, unit.notes());

		out.writeInt(unit.drawBuffers().size());
		for (int buffer : unit.drawBuffers()) {
			out.writeInt(buffer);
		}

		uniforms(out, unit.blockMembers());
		uniforms(out, unit.samplers());
	}

	private static TranslatedUnit unit(DataInputStream in) throws IOException {
		String entry = text(in);
		ProgramStage stage = ProgramStage.valueOf(text(in));
		String body = text(in);
		TranslatedUnit.Notes notes = notes(in);

		List<Integer> drawBuffers = new ArrayList<>();
		for (int left = in.readInt(); left > 0; left--) {
			drawBuffers.add(in.readInt());
		}

		return new TranslatedUnit(entry, stage, body, notes, List.copyOf(drawBuffers),
				uniforms(in), uniforms(in));
	}

	private static void notes(DataOutputStream out, TranslatedUnit.Notes notes) throws IOException {
		out.writeInt(notes.fragmentOutputs());
		out.writeInt(notes.dynamicFragData());
		out.writeInt(notes.uniformConflicts());
		out.writeInt(notes.shadowCalls());
		out.writeInt(notes.unwrappedShadow());
		out.writeInt(notes.strippedExtensions());
		out.writeInt(notes.depthEpilogue());
		out.writeInt(notes.alphaEpilogue());
		out.writeInt(notes.coverage());
		out.writeInt(notes.depthLookups());
		out.writeInt(notes.parameterLookups());
		out.writeInt(notes.pinnedLookups());
		out.writeInt(notes.unpinnedParameterLookups());
		out.writeInt(notes.fragCoordZ());
		out.writeInt(notes.fragCoordXyz());
		out.writeInt(notes.fragCoordUnhandled());
		out.writeInt(notes.fragDepthWrites());
		out.writeInt(notes.fragDepthUnhandled());
		names(out, notes.conflictNames());
		names(out, notes.comparedSamplers());
		names(out, notes.hardwareCompared());
		names(out, notes.retypedSamplers());
		storageBlocks(out, notes.storageBlocks());
		out.writeInt(notes.volumeLookups());
		out.writeInt(notes.volumesLeftAlone());
		out.writeInt(notes.trigCalls());
		out.writeInt(notes.gameTextureMatrix());
		out.writeInt(notes.gameModelView());
	}

	private static TranslatedUnit.Notes notes(DataInputStream in) throws IOException {
		return new TranslatedUnit.Notes(in.readInt(), in.readInt(), in.readInt(), in.readInt(),
				in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readInt(),
				in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readInt(),
				in.readInt(), in.readInt(),
				names(in), names(in), names(in), names(in), storageBlocks(in),
				in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readInt());
	}

	private static void storageBlocks(DataOutputStream out,
			List<TranslatedUnit.StorageBlock> blocks) throws IOException {
		out.writeInt(blocks.size());
		for (TranslatedUnit.StorageBlock block : blocks) {
			text(out, block.name());
			out.writeInt(block.binding());
		}
	}

	private static List<TranslatedUnit.StorageBlock> storageBlocks(DataInputStream in)
			throws IOException {
		List<TranslatedUnit.StorageBlock> blocks = new ArrayList<>();
		for (int left = in.readInt(); left > 0; left--) {
			blocks.add(new TranslatedUnit.StorageBlock(text(in), in.readInt()));
		}

		return List.copyOf(blocks);
	}

	private static void uniforms(DataOutputStream out, List<TranslatedUnit.Uniform> uniforms)
			throws IOException {
		out.writeInt(uniforms.size());
		for (TranslatedUnit.Uniform uniform : uniforms) {
			text(out, uniform.name());
			text(out, uniform.type());
			text(out, uniform.declaration());
		}
	}

	private static List<TranslatedUnit.Uniform> uniforms(DataInputStream in) throws IOException {
		List<TranslatedUnit.Uniform> uniforms = new ArrayList<>();
		for (int left = in.readInt(); left > 0; left--) {
			uniforms.add(new TranslatedUnit.Uniform(text(in), text(in), text(in)));
		}

		return List.copyOf(uniforms);
	}

	private static void names(DataOutputStream out, List<String> names) throws IOException {
		out.writeInt(names.size());
		for (String name : names) {
			text(out, name);
		}
	}

	private static List<String> names(DataInputStream in) throws IOException {
		List<String> names = new ArrayList<>();
		for (int left = in.readInt(); left > 0; left--) {
			names.add(text(in));
		}

		return List.copyOf(names);
	}

	private static void text(DataOutputStream out, String text) throws IOException {
		byte[] raw = text.getBytes(StandardCharsets.UTF_8);
		out.writeInt(raw.length);
		out.write(raw);
	}

	private static String text(DataInputStream in) throws IOException {
		int length = in.readInt();
		if (length < 0 || length > in.available()) {
			throw new IOException("a stored translation claims a string of " + length + " bytes");
		}

		byte[] raw = new byte[length];
		in.readFully(raw);

		return new String(raw, StandardCharsets.UTF_8);
	}
}
