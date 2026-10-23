package net.corevk.corevk.render;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import org.jspecify.annotations.Nullable;

public interface DisplayedItem {

	void corevk$displayedItem(@Nullable Item item, @Nullable Identifier modelId);

	@Nullable Item corevk$displayedItem();

	@Nullable Identifier corevk$displayedModel();
}
