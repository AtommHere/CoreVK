package net.corevk.corevk.render;

import net.corevk.corevk.uniform.WorldState;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.textures.GpuTextureView;

abstract class FamilyProgram implements DumpedProgram {

	protected final GeometryProgram body;

	FamilyProgram(GeometryProgram body) {
		this.body = body;
	}

	
	RenderPassDescriptor descriptor(GpuTextureView colour, GpuTextureView depth) {
		return this.body.descriptor(colour, depth);
	}

	
	void bind(RenderPass pass) {
		this.body.bind(pass);
	}

	
	@Override
	public boolean compile(GpuDevice device) {
		return this.body.compile(device);
	}

	
	@Override
	public boolean compiled() {
		return this.body.compiled();
	}

	
	@Override
	public void forgetCompiled() {
		this.body.forgetCompiled();
	}

	
	@Override
	public boolean warmAhead(AheadCompiler compiler) {
		return this.body.warmAhead(compiler);
	}

	
	@Override
	public void discardAhead() {
		this.body.discardAhead();
	}

	
	@Override
	public String decoded(WorldState world) {
		return this.body.decoded(world);
	}

	
	@Override
	public String path() {
		return this.body.path();
	}

	
	@Override
	public String label() {
		return this.body.label();
	}

	
	boolean owns(RenderPipeline bound) {
		return this.body.owns(bound);
	}

	
	void rotate() {
		this.body.rotate();
	}

	
	void release() {
		this.body.release();
	}
}
