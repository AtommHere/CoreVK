package net.corevk.corevk.uniform;

@FunctionalInterface
public interface UniformSource {

	void read(WorldState world, Val out);

	
	default void read(WorldState world, Val out, int element) {
		read(world, out);
	}
}
