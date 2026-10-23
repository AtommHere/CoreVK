package net.corevk.corevk.render;

import net.corevk.corevk.pack.source.OpenedPack;

import java.util.Collection;

abstract class FamilyDraw {

	
	abstract Collection<? extends DumpedProgram> programs();

	
	abstract void prefetch(OpenedPack shared);

	
	abstract void rotate();

	
	abstract void release();
}
