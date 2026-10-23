package net.corevk.corevk.render;

import net.corevk.corevk.glsl.TranslatedUnit;
import net.corevk.corevk.uniform.UniformBlock;
import net.corevk.corevk.uniform.UniformCatalog;
import net.corevk.corevk.uniform.UniformSink;
import net.corevk.corevk.uniform.WorldState;

import com.mojang.blaze3d.buffers.Std140Builder;

import java.util.List;

final class PackUniforms {

	private final UniformBlock block;

	PackUniforms(List<TranslatedUnit.Uniform> members) {
		this(members, UniformCatalog.engine());
	}

	
	PackUniforms(List<TranslatedUnit.Uniform> members, UniformCatalog catalog) {
		this.block = new UniformBlock(members, catalog);
	}

	int size() {
		return this.block.size();
	}

	
	List<String> unsupplied() {
		return this.block.unanswered();
	}

	void write(Std140Builder into, WorldState world) {
		this.block.write(new Std140Sink(into), world);
	}

	
	void write(UniformSink into, WorldState world) {
		this.block.write(into, world);
	}
}
