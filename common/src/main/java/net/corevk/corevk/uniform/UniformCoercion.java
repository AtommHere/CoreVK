package net.corevk.corevk.uniform;

public final class UniformCoercion {

	private UniformCoercion() {
	}

	public static void write(UniformSink sink, UniformShape declared, Val value) {

		if ((declared == UniformShape.FOG) != (value.rank() == UniformShape.FOG.rank())) {
			declared.zero(sink);

			return;
		}

		declared.write(sink, value);
	}
}
