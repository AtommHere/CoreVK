package net.corevk.corevk.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

class PackImagesTest {

	@Test
	void readsTheBlockAtlasName() {
		assertEquals(Identifier.withDefaultNamespace("textures/atlas/blocks.png"),
				PackImages.location("minecraft:textures/atlas/blocks.png"));
	}

	@Test
	void keepsTheFirstTwoPartsOfALongerName() {
		assertEquals(Identifier.fromNamespaceAndPath("mod", "textures/a.png"),
				PackImages.location("mod:textures/a.png:extra"));
	}

	@Test
	void namesNothingWithoutAPath() {
		assertNull(PackImages.location("minecraft:"));
		assertNull(PackImages.location("textures/atlas/blocks.png"));
	}
}
