package net.corevk.corevk.mixin;

import net.corevk.corevk.render.BlockStateIds;
import net.corevk.corevk.render.DisplayedItem;
import net.corevk.corevk.render.EntityIdentifiers;
import net.corevk.corevk.render.PackNameIds;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SolidBucketItem;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemStackRenderState.class)
public abstract class ItemStackRenderStateMixin implements DisplayedItem {

	
	@Unique
	private static final int BLOCK_ITEM = 1;

	@Unique
	private @Nullable Item corevk$item;

	@Unique
	private @Nullable Identifier corevk$model;

	
	@Unique
	private int corevk$heldBlockEntity;

	@Override
	public void corevk$displayedItem(@Nullable Item item, @Nullable Identifier modelId) {
		this.corevk$item = item;
		this.corevk$model = modelId;
	}

	@Override
	public @Nullable Item corevk$displayedItem() {
		return this.corevk$item;
	}

	@Override
	public @Nullable Identifier corevk$displayedModel() {
		return this.corevk$model;
	}

	@Inject(method = "clear", at = @At("HEAD"), require = 1)
	private void corevk$forgetItem(CallbackInfo callback) {
		this.corevk$item = null;
		this.corevk$model = null;
	}

	@Inject(method = "submit", at = @At("HEAD"), require = 1)
	private void corevk$begin(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords,
			int overlayCoords, int outlineColor, CallbackInfo callback) {
		this.corevk$heldBlockEntity = EntityIdentifiers.blockEntity();

		Item item = this.corevk$item;
		if (item == null) {
			return;
		}

		// A bucket of powder snow is a block item and is not drawn as one, which is the one exception
		// CoreVK carries and it carries it by class.
		if (item instanceof BlockItem block && !(item instanceof SolidBucketItem)) {
			EntityIdentifiers.blockEntity(BLOCK_ITEM);
			// Nought and not the minus one the table answers everywhere else, which is CoreVK's own
			// number here and the only place it departs from its own default: it asks for this one
			// value with getOrDefault(state, 0) (ItemStackStateLayerMixin.java:57) where every other
			// read of the same table takes the map's -1.
			EntityIdentifiers.item(
					Math.max(BlockStateIds.id(block.getBlock().defaultBlockState()), 0));

			return;
		}

		// The model the item names comes first and its registry key second, so that a resource pack
		// which points two items at one model has the pack answer once for both.
		Identifier name = this.corevk$model != null
				? this.corevk$model
				: BuiltInRegistries.ITEM.getKey(item);
		EntityIdentifiers.item(name == null ? -1 : PackNameIds.item(name));
	}

	@Inject(method = "submit", at = @At("RETURN"), require = 1)
	private void corevk$end(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords,
			int overlayCoords, int outlineColor, CallbackInfo callback) {
		EntityIdentifiers.blockEntity(this.corevk$heldBlockEntity);
		EntityIdentifiers.item(0);
	}
}
