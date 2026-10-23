package net.corevk.corevk.mixin;

import net.corevk.corevk.render.DisplayedItem;

import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemModelResolver.class)
public abstract class ItemModelResolverMixin {

	@Inject(method = "appendItemLayers", at = @At("HEAD"), require = 1)
	private void corevk$rememberItem(ItemStackRenderState output, ItemStack item,
			ItemDisplayContext displayContext, Level level, ItemOwner owner, int seed,
			CallbackInfo callback) {
		((DisplayedItem) output)
				.corevk$displayedItem(item.getItem(), item.get(DataComponents.ITEM_MODEL));
	}
}
