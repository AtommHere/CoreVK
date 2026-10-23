package net.corevk.corevk.render;

import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.textures.GpuTextureView;

import org.joml.Vector4fc;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;

public final class PassDescriptor {

	private final RenderPassDescriptor.Builder builder;

	private PassDescriptor(Supplier<String> label) {
		this.builder = RenderPassDescriptor.builder(label);
	}

	public static PassDescriptor create(Supplier<String> label) {
		return new PassDescriptor(label);
	}

	public PassDescriptor withColorAttachment(GpuTextureView view) {
		this.builder.withColorAttachment(view);
		return this;
	}

	public PassDescriptor withColorAttachment(GpuTextureView view, Optional<Vector4fc> clear) {
		this.builder.withColorAttachment(view, clear);
		return this;
	}

	public PassDescriptor withUnusedColorAttachment() {
		this.builder.withUnusedColorAttachment();
		return this;
	}

	public PassDescriptor withDepthAttachment(GpuTextureView view) {
		this.builder.withDepthAttachment(view);
		return this;
	}

	public PassDescriptor withDepthAttachment(GpuTextureView view, OptionalDouble clear) {
		this.builder.withDepthAttachment(view, clear);
		return this;
	}

	public PassDescriptor withRenderArea(RenderPass.RenderArea area) {
		this.builder.withRenderArea(area);
		return this;
	}

	public RenderPassDescriptor build() {
		return this.builder.build();
	}
}
