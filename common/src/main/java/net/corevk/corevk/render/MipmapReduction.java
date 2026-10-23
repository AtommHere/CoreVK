package net.corevk.corevk.render;

import net.corevk.corevk.mixin.access.CommandEncoderAccessor;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.CommandEncoderBackend;
import com.mojang.blaze3d.textures.GpuTexture;

final class MipmapReduction {

	private MipmapReduction() {
	}

	static boolean generate(CommandEncoder encoder, TargetSurface surface) {
		if (surface == null || surface.levels() <= 1) {
			return false;
		}

		if (!generate(encoder, surface.texture())) {
			return false;
		}

		surface.chainWritten(true);

		return true;
	}

	static boolean generate(CommandEncoder encoder, GpuTexture texture) {
		if (texture == null || texture.getMipLevels() <= 1) {
			return false;
		}

		GeometryHold.flush(() -> "a mip chain being filled");
		CommandEncoderBackend backend = ((CommandEncoderAccessor) encoder).corevk$backend();

		return backend instanceof MipmapCommands commands
				&& commands.corevk$generateMipmaps(texture);
	}
}
