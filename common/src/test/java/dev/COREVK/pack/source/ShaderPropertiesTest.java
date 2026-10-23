package net.corevk.corevk.pack.source;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ShaderPropertiesTest {

	@TempDir
	Path pack;

	@Test
	void readsOnAsFancy() throws IOException {
		assertEquals(ShaderProperties.CloudSetting.FANCY, dhClouds("clouds = off", "dhClouds = on"));
	}

	@Test
	void readsFancyAsFancy() throws IOException {
		assertEquals(ShaderProperties.CloudSetting.FANCY, dhClouds("dhClouds=fancy"));
	}

	@Test
	void readsOff() throws IOException {
		assertEquals(ShaderProperties.CloudSetting.OFF, dhClouds("dhClouds = off"));
	}

	@Test
	void leavesAWordOfTheCloudsLineUnread() throws IOException {
		assertEquals(ShaderProperties.CloudSetting.DEFAULT, dhClouds("dhClouds = fast"));
	}

	@Test
	void leavesAMissingLineUnread() throws IOException {
		assertEquals(ShaderProperties.CloudSetting.DEFAULT, dhClouds("clouds = off"));
	}

	private ShaderProperties.CloudSetting dhClouds(String... lines) throws IOException {
		Path shaders = Files.createDirectories(this.pack.resolve("shaders"));
		Files.write(shaders.resolve("shaders.properties"), List.of(lines));
		try (ShaderPackSource source = ShaderPackSource.open(this.pack)) {
			return ShaderProperties.parse(source).dhClouds(Map.of());
		}
	}
}
