package net.corevk.corevk.render;

import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.textures.GpuTextureView;

import org.joml.Vector4fc;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;

public final class PassDescriptor {

	private final RenderPassDescriptor descriptor;

	private PassDescriptor(Supplier<String> label) {
		this.descriptor = RenderPassDescriptor.create(label);
	}

	public static PassDescriptor create(Supplier<String> label) {
		return new PassDescriptor(label);
	}

	public PassDescriptor withColorAttachment(GpuTextureView view) {
		this.descriptor.withColorAttachment(view);
		return this;
	}

	public PassDescriptor withColorAttachment(GpuTextureView view, Optional<Vector4fc> clear) {
		this.descriptor.withColorAttachment(view, clear);
		return this;
	}

	public PassDescriptor withUnusedColorAttachment() {
		this.descriptor.withUnusedColorAttachment();
		return this;
	}

	public PassDescriptor withDepthAttachment(GpuTextureView view) {
		this.descriptor.withDepthAttachment(view);
		return this;
	}

	public PassDescriptor withDepthAttachment(GpuTextureView view, OptionalDouble clear) {
		this.descriptor.withDepthAttachment(view, clear);
		return this;
	}

	public PassDescriptor withRenderArea(RenderPass.RenderArea area) {
		this.descriptor.withRenderArea(area);
		return this;
	}

	public RenderPassDescriptor build() {
		return this.descriptor;
	}
}
