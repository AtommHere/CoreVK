package net.corevk.corevk.glsl;

import java.util.BitSet;

public final class DebugNames {

	
	public static final String VERSION = "names-1";

	private static final int HEADER_WORDS = 5;

	private static final int OP_NAME = 5;
	private static final int OP_MEMBER_NAME = 6;
	private static final int OP_TYPE_ARRAY = 28;
	private static final int OP_TYPE_RUNTIME_ARRAY = 29;
	private static final int OP_TYPE_POINTER = 32;
	private static final int OP_VARIABLE = 59;

	
	private static final int[] KEPT_CLASSES = {0, 1, 2, 3, 9, 11, 12};

	private DebugNames() {
	}

	
	public record Result(int[] words, int dropped) {

		public boolean changed() {
			return this.dropped > 0;
		}
	}

	
	public static Result strip(int[] words) {
		if (!LocalZeroes.readable(words)) {
			return new Result(words, 0);
		}

		BitSet kept = keptIds(words);
		int dropped = 0;
		int keptWords = HEADER_WORDS;
		for (int at = HEADER_WORDS; at < words.length; ) {
			int length = words[at] >>> 16;
			if (drops(words, at, kept)) {
				dropped++;
			} else {
				keptWords += length;
			}
			at += length;
		}

		if (dropped == 0) {
			return new Result(words, 0);
		}

		int[] out = new int[keptWords];
		System.arraycopy(words, 0, out, 0, HEADER_WORDS);
		int write = HEADER_WORDS;
		for (int at = HEADER_WORDS; at < words.length; ) {
			int length = words[at] >>> 16;
			if (!drops(words, at, kept)) {
				System.arraycopy(words, at, out, write, length);
				write += length;
			}
			at += length;
		}

		return new Result(out, dropped);
	}

	
	private static boolean drops(int[] words, int at, BitSet kept) {
		int opcode = words[at] & 0xFFFF;
		int length = words[at] >>> 16;
		// A name is three words at least, the opcode, its target and one word of string. Shorter
		// than that it is not one, whatever the opcode says, and it is kept: this pass drops what
		// it has understood and never what it has not.
		if (length < 3 || (opcode != OP_NAME && opcode != OP_MEMBER_NAME)) {
			return false;
		}

		int target = words[at + 1];

		return target >= 0 && !kept.get(target);
	}

	
	private static BitSet keptIds(int[] words) {
		BitSet kept = new BitSet();
		// Bound is the header's fourth word, so every id a well formed module uses fits under it.
		// Clamped at BOTH ends rather than trusted. Every id costs at least the two words of the
		// instruction that mints it, so a bound above the module's own length is not a bound, and
		// sizing the tables from it would allocate whatever a broken header happened to ask for. A
		// bound a module UNDERSTATES is left alone, and what that costs is written down rather than
		// guarded: an id past the tables is not followed, so the type name of a block declared past
		// the bound would go, and the module saying so is the broken one.
		int[] pointee = new int[Math.min(Math.max(words[3], 1), words.length)];
		int[] element = new int[pointee.length];
		for (int at = HEADER_WORDS; at < words.length; ) {
			int length = words[at] >>> 16;
			int opcode = words[at] & 0xFFFF;
			switch (opcode) {
				case OP_TYPE_POINTER -> {
					if (length >= 4 && inRange(words[at + 1], pointee)) {
						pointee[words[at + 1]] = words[at + 3];
					}
				}
				case OP_TYPE_ARRAY, OP_TYPE_RUNTIME_ARRAY -> {
					if (length >= 3 && inRange(words[at + 1], element)) {
						element[words[at + 1]] = words[at + 2];
					}
				}
				case OP_VARIABLE -> {
					if (length >= 4 && isKept(words[at + 3])) {
						int id = words[at + 2];
						if (id >= 0) {
							kept.set(id);
						}
						int type = inRange(words[at + 1], pointee) ? pointee[words[at + 1]] : 0;
						// An array of arrays of a block is the deepest thing GLSL declares, so two
						// unwraps answer every real module. The loop allows eight and stops there
						// rather than trusting a module not to point an array at itself.
						for (int step = 0; step < 8 && type > 0; step++) {
							kept.set(type);
							int inner = inRange(type, element) ? element[type] : 0;
							if (inner <= 0) {
								break;
							}
							type = inner;
						}
					}
				}
				default -> {
				}
			}
			at += length;
		}

		return kept;
	}

	private static boolean inRange(int id, int[] table) {
		return id > 0 && id < table.length;
	}

	private static boolean isKept(int storageClass) {
		for (int kept : KEPT_CLASSES) {
			if (kept == storageClass) {
				return true;
			}
		}

		return false;
	}
}
