package net.corevk.corevk.mixin.access;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderTarget.class)
public interface RenderTargetAccessor {

	@Accessor("colorTexture")
	GpuTexture corevk$colorTexture();

	@Accessor("colorTexture")
	void corevk$colorTexture(GpuTexture texture);

	@Accessor("colorTextureView")
	GpuTextureView corevk$colorTextureView();

	@Accessor("colorTextureView")
	void corevk$colorTextureView(GpuTextureView view);

	@Accessor("depthTexture")
	GpuTexture corevk$depthTexture();

	@Accessor("depthTexture")
	void corevk$depthTexture(GpuTexture texture);

	@Accessor("depthTextureView")
	GpuTextureView corevk$depthTextureView();

	@Accessor("depthTextureView")
	void corevk$depthTextureView(GpuTextureView view);
}
