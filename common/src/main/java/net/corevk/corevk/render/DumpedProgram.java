package net.corevk.corevk.render;

import net.corevk.corevk.uniform.WorldState;

import com.mojang.blaze3d.systems.GpuDevice;

interface DumpedProgram {

	String label();

	String path();

	String decoded(WorldState world);

	boolean compile(GpuDevice device);

	boolean compiled();

	void forgetCompiled();

	default boolean warmAhead(AheadCompiler compiler) {
		return false;
	}

	default void discardAhead() {
	}
}
