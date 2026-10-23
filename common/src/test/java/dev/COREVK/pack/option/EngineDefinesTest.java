package net.corevk.corevk.pack.option;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class EngineDefinesTest {

	@Test
	void posesNoSystemSymbolOnAMac() {
		List<String> systems = systemSymbols(EngineDefines.Os.MAC);

		assertTrue(systems.isEmpty(), "system symbols posed on a Mac: " + systems);
	}

	@Test
	void posesOptiFinesSymbolElsewhere() {
		assertEquals(List.of("MC_OS_WINDOWS"), systemSymbols(EngineDefines.Os.WINDOWS));
		assertEquals(List.of("MC_OS_LINUX"), systemSymbols(EngineDefines.Os.LINUX));
		assertEquals(List.of("MC_OS_UNKNOWN"), systemSymbols(EngineDefines.Os.OTHER));
	}

	private static List<String> systemSymbols(EngineDefines.Os os) {
		Map<String, String> table = EngineDefines.table(new EngineDefines.Environment(
				EngineDefines.DEFAULT_MC_VERSION, os, "", "", 4, false, Map.of(), List.of()));

		return table.keySet().stream().filter(name -> name.startsWith("MC_OS_")).toList();
	}
}
