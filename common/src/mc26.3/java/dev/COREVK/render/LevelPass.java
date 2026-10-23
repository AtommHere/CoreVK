package net.corevk.corevk.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.GpuQueryPool;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import org.lwjgl.PointerBuffer;

import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

public final class LevelPass implements RenderPass {

	
	private static @Nullable LevelPass current;

	private final @Nullable LevelPass under;
	private final GpuTextureView colour;
	private final @Nullable GpuTextureView depth;
	private final Supplier<RenderPass> opener;
	private final List<Supplier<String>> groups = new ArrayList<>();
	private @Nullable RenderPass real;
	private int @Nullable [] scissor;
	private boolean closed;

	private LevelPass(@Nullable LevelPass under, RenderPass first, GpuTextureView colour,
			@Nullable GpuTextureView depth, Supplier<RenderPass> opener) {
		this.under = under;
		this.real = first;
		this.colour = colour;
		this.depth = depth;
		this.opener = opener;
	}

	
	public static RenderPass open(RenderPass first, GpuTextureView colour,
			@Nullable GpuTextureView depth, Supplier<RenderPass> opener) {
		LevelPass pass = new LevelPass(current, first, colour, depth, opener);
		current = pass;
		return pass;
	}

	
	public GpuTextureView colour() {
		return this.colour;
	}

	
	public @Nullable GpuTextureView depth() {
		return this.depth;
	}

	
	public static void suspendCurrent() {
		LevelPass pass = current;
		if (pass != null) {
			pass.suspend();
		}
	}

	
	public static boolean holding() {
		LevelPass pass = current;
		return pass != null && pass.real != null;
	}

	private void suspend() {
		RenderPass open = this.real;
		if (open == null) {
			return;
		}

		this.real = null;
		for (int i = 0; i < this.groups.size(); i++) {
			open.popDebugGroup();
		}

		open.close();
	}

	private RenderPass real() {
		RenderPass open = this.real;
		if (open != null) {
			return open;
		}

		if (this.closed) {
			throw new IllegalStateException("The level's pass is used after the level closed it");
		}

		open = this.opener.get();
		this.real = open;
		RenderSystem.bindDefaultUniforms(open);
		for (Supplier<String> group : this.groups) {
			open.pushDebugGroup(group);
		}

		if (this.scissor != null) {
			open.enableScissor(this.scissor[0], this.scissor[1], this.scissor[2], this.scissor[3]);
		}

		return open;
	}

	@Override
	public void pushDebugGroup(Supplier<String> label) {
		this.groups.add(label);
		if (this.real != null) {
			this.real.pushDebugGroup(label);
		}
	}

	@Override
	public void popDebugGroup() {
		if (!this.groups.isEmpty()) {
			this.groups.removeLast();
		}

		if (this.real != null) {
			this.real.popDebugGroup();
		}
	}

	@Override
	public void writeTimestamp(GpuQueryPool pool, int index) {
		real().writeTimestamp(pool, index);
	}

	@Override
	public void setPipeline(CompiledRenderPipeline pipeline) {
		real().setPipeline(pipeline);
	}

	@Override
	public void setUniform(String name, GpuTextureView view, GpuSampler sampler) {
		real().setUniform(name, view, sampler);
	}

	@Override
	public void setUniform(String name, GpuBuffer buffer) {
		real().setUniform(name, buffer);
	}

	@Override
	public void setUniform(String name, GpuBufferSlice slice) {
		real().setUniform(name, slice);
	}

	@Override
	public void pushConstants(ByteBuffer constants) {
		real().pushConstants(constants);
	}

	@Override
	public void enableScissor(int x, int y, int width, int height) {
		this.scissor = new int[] {x, y, width, height};
		real().enableScissor(x, y, width, height);
	}

	@Override
	public void disableScissor() {
		this.scissor = null;
		if (this.real != null) {
			this.real.disableScissor();
		}
	}

	@Override
	public void setVertexBuffer(int slot, GpuBufferSlice slice) {
		real().setVertexBuffer(slot, slice);
	}

	@Override
	public void setIndexBuffer(GpuBuffer buffer, IndexType type) {
		real().setIndexBuffer(buffer, type);
	}

	@Override
	public void drawIndexed(int baseVertex, int firstIndex, int indexCount, int instanceCount,
			int firstInstance) {
		real().drawIndexed(baseVertex, firstIndex, indexCount, instanceCount, firstInstance);
	}

	@Override
	public void multiDrawIndexed(IntBuffer baseVertices, int firstIndex, int indexCount,
			int drawCount) {
		real().multiDrawIndexed(baseVertices, firstIndex, indexCount, drawCount);
	}

	@Override
	public void multiDrawIndexed(PointerBuffer indices, IntBuffer counts, IntBuffer baseVertices,
			int drawCount) {
		real().multiDrawIndexed(indices, counts, baseVertices, drawCount);
	}

	@Override
	public void drawIndexedIndirect(GpuBufferSlice commands, int drawCount) {
		real().drawIndexedIndirect(commands, drawCount);
	}

	@Override
	public <T> void drawMultipleIndexed(Collection<RenderPass.Draw<T>> draws, GpuBuffer indices,
			IndexType type, Collection<String> uniformNames, T user) {
		real().drawMultipleIndexed(draws, indices, type, uniformNames, user);
	}

	@Override
	public void draw(int firstVertex, int vertexCount, int firstInstance, int instanceCount) {
		real().draw(firstVertex, vertexCount, firstInstance, instanceCount);
	}

	@Override
	public void multiDraw(IntBuffer firstVertices, int vertexCount, int instanceCount,
			int drawCount) {
		real().multiDraw(firstVertices, vertexCount, instanceCount, drawCount);
	}

	@Override
	public void multiDraw(IntBuffer firstVertices, IntBuffer vertexCounts, int drawCount) {
		real().multiDraw(firstVertices, vertexCounts, drawCount);
	}

	@Override
	public void drawIndirect(GpuBufferSlice commands, int drawCount) {
		real().drawIndirect(commands, drawCount);
	}

	@Override
	public void close() {
		suspend();
		this.closed = true;
		if (current == this) {
			// The one it was opened over, unless that one has been closed meanwhile, which a
			// storage drawn across the level's end would see.
			LevelPass next = this.under;
			while (next != null && next.closed) {
				next = next.under;
			}

			current = next;
		}
	}
}
