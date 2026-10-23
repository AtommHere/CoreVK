package net.corevk.corevk.uniform;

import net.corevk.corevk.glsl.TranslatedUnit;

import java.util.ArrayList;
import java.util.List;

public final class UniformBlock {

	private final List<Member> members;
	private final List<String> unanswered;
	private final int size;

	private final Val carrier = new Val();

	private record Member(String name, UniformShape shape, int elements, boolean array,
			UniformSource source) {
	}

	public UniformBlock(List<TranslatedUnit.Uniform> members, UniformCatalog catalog) {
		List<Member> resolved = new ArrayList<>(members.size());
		List<String> missing = new ArrayList<>();

		for (TranslatedUnit.Uniform member : members) {
			UniformShape shape = UniformShape.of(member.type());
			if (shape == null) {
				throw new IllegalStateException(
						"Cannot size " + member.declaration() + ": nothing here knows the type " + member.type());
			}

			int declared = arrayLength(member.declaration());
			if (declared < 0) {
				throw new IllegalStateException(
						"Cannot size " + member.declaration() + ": the array length is not a literal");
			}

			boolean array = declared > 0;

			UniformSource source = catalog.source(member.name());

			if (source != null && (shape == UniformShape.FOG) != (catalog.natural(member.name()) == UniformShape.FOG)) {
				source = null;
			}

			if (source == null) {
				missing.add(member.name());
			}

			resolved.add(new Member(member.name(), shape, array ? declared : 1, array, source));
		}

		this.members = List.copyOf(resolved);
		this.unanswered = List.copyOf(missing);
		this.size = walk(new Std140Counter(), null).size();
	}

	public int size() {
		return this.size;
	}

	public List<String> unanswered() {
		return this.unanswered;
	}

	public void write(UniformSink sink, WorldState world) {
		walk(sink, world);
	}

	private <T extends UniformSink> T walk(T sink, WorldState world) {
		for (Member member : this.members) {
			boolean supplied = member.source() != null && world != null;

			sink.member(member.name(), member.elements(), supplied);

			for (int element = 0; element < member.elements(); element++) {

				if (member.array()) {
					sink.align(16);
				}

				if (supplied) {

					member.source().read(world, this.carrier, element);
					UniformCoercion.write(sink, member.shape(), this.carrier);
				} else {
					member.shape().zero(sink);
				}
			}

			if (member.array()) {
				sink.align(16);
			}
		}

		return sink;
	}

	static int arrayLength(String declaration) {
		int open = declaration.indexOf('[');
		if (open < 0) {
			return 0;
		}

		int elements = 1;
		while (open >= 0) {
			int close = declaration.indexOf(']', open);
			if (close < 0) {
				return -1;
			}

			int length;
			try {
				length = Integer.parseInt(declaration.substring(open + 1, close).trim());
			} catch (NumberFormatException e) {
				return -1;
			}

			if (length < 1) {
				return -1;
			}

			elements *= length;
			open = declaration.indexOf('[', close + 1);
		}

		return elements;
	}
}
