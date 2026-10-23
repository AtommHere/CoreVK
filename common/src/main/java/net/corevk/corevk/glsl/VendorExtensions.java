package net.corevk.corevk.glsl;

import net.corevk.corevk.pack.model.ProgramStage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class VendorExtensions {

	
	private static final Map<String, String> DEVICE_EXTENSIONS = deviceExtensions();

	
	private static final List<String> SUBGROUP_PREFIXES =
			List.of("GL_KHR_shader_subgroup_", "GL_EXT_shader_subgroup_extended_types_");

	private static volatile Set<String> absent = Collections.unmodifiableSet(
			new TreeSet<>(DEVICE_EXTENSIONS.keySet()));

	private static volatile Set<ProgramStage> subgroupsAbsentIn =
			Collections.unmodifiableSet(EnumSet.noneOf(ProgramStage.class));

	private static volatile boolean moltenVk;

	private VendorExtensions() {
	}

	
	public static List<String> serve(Predicate<String> hasDeviceExtension, Consumer<String> enable) {
		Set<String> missing = new TreeSet<>();
		List<String> enabled = new ArrayList<>();
		DEVICE_EXTENSIONS.forEach((glsl, device) -> {
			if (device.isEmpty() || !hasDeviceExtension.test(device)) {
				missing.add(glsl);
			} else {
				enable.accept(device);
				enabled.add(device);
			}
		});
		absent = Collections.unmodifiableSet(missing);

		return enabled;
	}

	
	public static Set<ProgramStage> serveSubgroupStages(Set<ProgramStage> supported) {
		Set<ProgramStage> lacking = EnumSet.allOf(ProgramStage.class);
		lacking.removeAll(supported);
		subgroupsAbsentIn = Collections.unmodifiableSet(lacking);

		return subgroupsAbsentIn;
	}

	
	static boolean absent(String glslExtension, ProgramStage stage) {
		return absent.contains(glslExtension)
				|| (subgroupsAbsentIn.contains(stage) && subgroup(glslExtension));
	}

	
	public static void serveMoltenVk(boolean moltenVkDriver) {
		moltenVk = moltenVkDriver;
	}

	
	static boolean moltenVk() {
		return moltenVk;
	}

	
	public static String key() {
		String names = String.join(",", absent);
		String stages = subgroupsAbsentIn.isEmpty() ? names : subgroupsAbsentIn.stream()
				.map(Enum::name)
				.collect(Collectors.joining(",", names + ";subgroups absent in ", ""));

		return moltenVk ? stages + ";MoltenVK" : stages;
	}

	
	static String hidden(String glslExtension) {
		return "OF_ABSENT_" + glslExtension;
	}

	private static boolean subgroup(String glslExtension) {
		for (String prefix : SUBGROUP_PREFIXES) {
			if (glslExtension.startsWith(prefix)) {
				return true;
			}
		}

		return false;
	}

	private static Map<String, String> deviceExtensions() {
		Map<String, String> table = new LinkedHashMap<>();
		// Extensions that stand on their own: enabling the device extension is all a module
		// using them needs.
		table.put("GL_AMD_shader_trinary_minmax", "VK_AMD_shader_trinary_minmax");
		table.put("GL_AMD_gpu_shader_half_float", "VK_AMD_gpu_shader_half_float");
		table.put("GL_AMD_gpu_shader_int16", "VK_AMD_gpu_shader_int16");
		table.put("GL_AMD_gcn_shader", "VK_AMD_gcn_shader");
		table.put("GL_AMD_shader_explicit_vertex_parameter", "VK_AMD_shader_explicit_vertex_parameter");
		table.put("GL_AMD_shader_fragment_mask", "VK_AMD_shader_fragment_mask");
		table.put("GL_AMD_shader_image_load_store_lod", "VK_AMD_shader_image_load_store_lod");
		table.put("GL_AMD_texture_gather_bias_lod", "VK_AMD_texture_gather_bias_lod");
		table.put("GL_NV_shader_subgroup_partitioned", "VK_NV_shader_subgroup_partitioned");
		// Absent on every device: no Vulkan form, a second extension or a device feature of their
		// own that is not enabled here, so the module would be invalid with the extension enabled
		// alone.
		table.put("GL_NV_gpu_shader5", "");
		table.put("GL_AMD_shader_ballot", "");
		table.put("GL_INTEL_shader_integer_functions2", "");
		table.put("GL_NV_shader_sm_builtins", "");
		table.put("GL_ARM_shader_core_builtins", "");

		return Collections.unmodifiableMap(table);
	}
}
