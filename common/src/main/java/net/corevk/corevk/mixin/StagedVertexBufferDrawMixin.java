package net.corevk.corevk.mixin;

import net.corevk.corevk.render.BlockEntityMark;

import net.minecraft.client.renderer.StagedVertexBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(StagedVertexBuffer.Draw.class)
public abstract class StagedVertexBufferDrawMixin implements BlockEntityMark {

	@Unique
	private boolean corevk$blockEntity;

	@Override
	public boolean corevk$fromBlockEntity() {
		return this.corevk$blockEntity;
	}

	@Override
	public void corevk$fromBlockEntity(boolean fromBlockEntity) {
		this.corevk$blockEntity = fromBlockEntity;
	}
}
