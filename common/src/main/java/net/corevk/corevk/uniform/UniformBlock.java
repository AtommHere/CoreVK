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
			// The fog struct is the one shape that cannot be coerced from anything else, so a
			// source that does not hold one answers this name no better than nothing does. Settled
			// here, once, rather than discovered per frame.
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
				// Every element of an array starts on a sixteen byte boundary in std140, which for
				// anything smaller than a vec4 is not what putting them back to back gives. It is the
				// FIRST element that carries the rule, so a one element array pays it too.
				if (member.array()) {
					sink.align(16);
				}

				if (supplied) {
					// Asked once per element and not once per member. Almost every source answers
					// them all alike and pays a call it did not need; the one that does not is
					// gl_TextureMatrix, whose unit one is the light map's and is not the identity the
					// others are. Reading the member once wrote that one value eight times.
					member.source().read(world, this.carrier, element);
					UniformCoercion.write(sink, member.shape(), this.carrier);
				} else {
					member.shape().zero(sink);
				}
			}

			// And once more AFTER the last element, because an array's size is its stride times its
			// length rather than where its last element happens to stop. Without this a vec3[2]
			// would end at twenty eight and the member behind it would start there; the compiler
			// puts that member at thirty two. Only the padding after the last element is missing
			// from the loop above, since every other element is aligned on its way in.
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

			// An unsized or empty dimension is refused rather than taken for one element: a block
			// measured a member short is every member after it landing where nothing reads it.
			if (length < 1) {
				return -1;
			}

			elements *= length;
			open = declaration.indexOf('[', close + 1);
		}

		return elements;
	}
}
