package net.corevk.corevk.uniform;

public final class UniformCoercion {

	private UniformCoercion() {
	}

	public static void write(UniformSink sink, UniformShape declared, Val value) {
		// Rank eight belongs to the fog struct and to nothing else, which is what makes this test
		// enough without a flag of its own on the carrier.
		if ((declared == UniformShape.FOG) != (value.rank() == UniformShape.FOG.rank())) {
			declared.zero(sink);

			return;
		}

		declared.write(sink, value);
	}
}
