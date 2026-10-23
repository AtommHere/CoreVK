package net.corevk.corevk.render;

import com.mojang.blaze3d.textures.GpuTexture;

public interface MipmapCommands {

	boolean corevk$generateMipmaps(GpuTexture texture);

	boolean corevk$viewport(int width, int height);
}
