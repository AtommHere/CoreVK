package net.corevk.corevk.uniform;

import java.util.LinkedHashMap;
import java.util.Map;

public final class UniformGaps {

	private static final Map<String, String> STAND_INS = standIns();

	private static final Map<String, String> UNANSWERABLE = unanswerable();

	private UniformGaps() {
	}

	public static String standIn(String name) {
		return STAND_INS.get(name);
	}

	public static String unanswerable(String name) {
		return UNANSWERABLE.get(name);
	}

	private static Map<String, String> unanswerable() {
		Map<String, String> reasons = new LinkedHashMap<>();

		reasons.put("farPlane", "no engine answers it, CoreVK included, so a pack reads the same "
				+ "nought there");

		String shimNotWired = "CoreVK registers it in a shim nothing in that repository calls, so it "
				+ "is a nought there too";
		reasons.put("velocity", shimNotWired);

		String voxyMod = "it belongs to the Voxy mod, and no engine answers it with that mod absent";
		reasons.put("vxRenderDistance", voxyMod);
		reasons.put("vxProj", voxyMod);
		reasons.put("vxProjInv", voxyMod);
		reasons.put("vxProjPrev", voxyMod);
		reasons.put("vxModelView", voxyMod);
		reasons.put("vxModelViewInv", voxyMod);
		reasons.put("vxModelViewPrev", voxyMod);

		reasons.put("previouscameraPositionFract", "the name is misspelt: CoreVK supplies "
				+ "previousCameraPositionFract, with a capital C, and so does this engine");

		String packsOwn = "no engine registers it, CoreVK included, and the pack that reads it does "
				+ "not declare it either";

		reasons.put("playerPosition", packsOwn);

		reasons.put("isNether", packsOwn);

		reasons.put("gbufferPreviousModelViewInverse", packsOwn);

		reasons.put("clouds_offset", packsOwn);

		reasons.put("Moon_Weather_properties", packsOwn);

		reasons.put("rainReflectionStrength", packsOwn);

		reasons.put("focolortex5", packsOwn);

		reasons.put("skyIntensity", packsOwn);
		reasons.put("skyIntensityNight", packsOwn);
		reasons.put("moonIntensity", packsOwn);
		reasons.put("sunIntensity", packsOwn);
		reasons.put("sunColor", packsOwn);
		reasons.put("nsunColor", packsOwn);

		reasons.put("lightCol", packsOwn);

		return Map.copyOf(reasons);
	}

	private static Map<String, String> standIns() {
		Map<String, String> reasons = new LinkedHashMap<>();

		reasons.put("currentColorSpace", "there is no settings screen to choose it from, which is "
				+ "also what CoreVK answers outside the mode concerned");

		reasons.put("constantMood", "CoreVK reads it through an interface it mixes into the player");

		return Map.copyOf(reasons);
	}

}
