package net.corevk.corevk.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vulkan.VulkanBackend;
import com.mojang.blaze3d.vulkan.VulkanPhysicalDevice;
import com.mojang.blaze3d.vulkan.init.VulkanFeature;
import com.mojang.blaze3d.vulkan.init.VulkanPNextStruct;
import net.corevk.corevk.glsl.SharedMemory;
import net.corevk.corevk.glsl.VendorExtensions;
import net.corevk.corevk.pack.model.ProgramStage;
import net.corevk.corevk.render.BufferBlending;
import net.corevk.corevk.render.GeometryStage;
import net.corevk.corevk.render.WideSamplerSets;
import net.corevk.corevk.CoreVK;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VK11;
import org.lwjgl.vulkan.KHRPortabilitySubset;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceFeatures;
import org.lwjgl.vulkan.VkPhysicalDeviceFeatures2;
import org.lwjgl.vulkan.VkPhysicalDevicePortabilitySubsetFeaturesKHR;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties2;
import org.lwjgl.vulkan.VkPhysicalDeviceSubgroupProperties;
import org.lwjgl.vulkan.VkPhysicalDeviceVulkan11Features;
import org.lwjgl.vulkan.VkPhysicalDeviceVulkan12Features;
import org.lwjgl.vulkan.VkPhysicalDeviceVulkan12Properties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Mixin(VulkanBackend.class)
public abstract class VulkanBackendMixin {

	@Unique
	private static final VulkanFeature VERTEX_STORES = new VulkanFeature(
			VulkanBackend.VK10_FEATURES_STRUCT, "vertexPipelineStoresAndAtomics",
			VkPhysicalDeviceFeatures.VERTEXPIPELINESTORESANDATOMICS);

	@Unique
	private static final VulkanFeature FRAGMENT_STORES = new VulkanFeature(
			VulkanBackend.VK10_FEATURES_STRUCT, "fragmentStoresAndAtomics",
			VkPhysicalDeviceFeatures.FRAGMENTSTORESANDATOMICS);

	@Unique
	private static final VulkanFeature EXTENDED_FORMATS = new VulkanFeature(
			VulkanBackend.VK10_FEATURES_STRUCT, "shaderStorageImageExtendedFormats",
			VkPhysicalDeviceFeatures.SHADERSTORAGEIMAGEEXTENDEDFORMATS);

	@Unique
	private static final VulkanFeature WRITE_WITHOUT_FORMAT = new VulkanFeature(
			VulkanBackend.VK10_FEATURES_STRUCT, "shaderStorageImageWriteWithoutFormat",
			VkPhysicalDeviceFeatures.SHADERSTORAGEIMAGEWRITEWITHOUTFORMAT);

	@Unique
	private static final VulkanFeature INDEPENDENT_BLEND = new VulkanFeature(
			VulkanBackend.VK10_FEATURES_STRUCT, "independentBlend",
			VkPhysicalDeviceFeatures.INDEPENDENTBLEND);

	@Unique
	private static final VulkanFeature SHADER_FLOAT16 = new VulkanFeature(
			VulkanBackend.VK12_FEATURES_STRUCT, "shaderFloat16",
			VkPhysicalDeviceVulkan12Features.SHADERFLOAT16);

	@Unique
	private static final VulkanFeature SHADER_INT8 = new VulkanFeature(
			VulkanBackend.VK12_FEATURES_STRUCT, "shaderInt8",
			VkPhysicalDeviceVulkan12Features.SHADERINT8);

	@Unique
	private static final VulkanFeature SHADER_INT16 = new VulkanFeature(
			VulkanBackend.VK10_FEATURES_STRUCT, "shaderInt16",
			VkPhysicalDeviceFeatures.SHADERINT16);

	@Unique
	private static final VulkanFeature SUBGROUP_EXTENDED = new VulkanFeature(
			VulkanBackend.VK12_FEATURES_STRUCT, "shaderSubgroupExtendedTypes",
			VkPhysicalDeviceVulkan12Features.SHADERSUBGROUPEXTENDEDTYPES);

	@Unique
	private static final VulkanFeature STORAGE_16 = new VulkanFeature(
			VulkanBackend.VK11_FEATURES_STRUCT, "storageBuffer16BitAccess",
			VkPhysicalDeviceVulkan11Features.STORAGEBUFFER16BITACCESS);

	@Unique
	private static final VulkanFeature UNIFORM_STORAGE_16 = new VulkanFeature(
			VulkanBackend.VK11_FEATURES_STRUCT, "uniformAndStorageBuffer16BitAccess",
			VkPhysicalDeviceVulkan11Features.UNIFORMANDSTORAGEBUFFER16BITACCESS);

	@Unique
	private static final VulkanFeature STORAGE_8 = new VulkanFeature(
			VulkanBackend.VK12_FEATURES_STRUCT, "storageBuffer8BitAccess",
			VkPhysicalDeviceVulkan12Features.STORAGEBUFFER8BITACCESS);

	@Unique
	private static final VulkanFeature UNIFORM_STORAGE_8 = new VulkanFeature(
			VulkanBackend.VK12_FEATURES_STRUCT, "uniformAndStorageBuffer8BitAccess",
			VkPhysicalDeviceVulkan12Features.UNIFORMANDSTORAGEBUFFER8BITACCESS);

	@Unique
	private static final VulkanFeature GEOMETRY_SHADER = new VulkanFeature(
			VulkanBackend.VK10_FEATURES_STRUCT, "geometryShader",
			VkPhysicalDeviceFeatures.GEOMETRYSHADER);

	@Unique
	private static final Map<ProgramStage, Integer> STAGE_BITS = Map.of(
			ProgramStage.VERTEX, VK10.VK_SHADER_STAGE_VERTEX_BIT,
			ProgramStage.FRAGMENT, VK10.VK_SHADER_STAGE_FRAGMENT_BIT,
			ProgramStage.GEOMETRY, VK10.VK_SHADER_STAGE_GEOMETRY_BIT,
			ProgramStage.COMPUTE, VK10.VK_SHADER_STAGE_COMPUTE_BIT,
			ProgramStage.TESSELLATION_CONTROL, VK10.VK_SHADER_STAGE_TESSELLATION_CONTROL_BIT,
			ProgramStage.TESSELLATION_EVALUATION, VK10.VK_SHADER_STAGE_TESSELLATION_EVALUATION_BIT);

	@Unique
	private static final VulkanPNextStruct PORTABILITY_FEATURES_STRUCT = new VulkanPNextStruct(
			KHRPortabilitySubset.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PORTABILITY_SUBSET_FEATURES_KHR,
			VkPhysicalDevicePortabilitySubsetFeaturesKHR.SIZEOF);

	@Unique
	private static final VulkanFeature MUTABLE_COMPARISON = new VulkanFeature(
			PORTABILITY_FEATURES_STRUCT, "mutableComparisonSamplers",
			VkPhysicalDevicePortabilitySubsetFeaturesKHR.MUTABLECOMPARISONSAMPLERS);

	@Unique
	private static final String PORTABILITY_EXTENSION = "VK_KHR_portability_subset";

	@Unique
	private static final String COMPARISON = "the comparison sampler a pack's shadow lookups are "
			+ "bound with is outside what the device promises, which the validation layer refuses";

	@Unique
	private static final String VOXELS = "voxel lighting will not write";

	@Unique
	private static final String NARROW = "a pack computing in 16 or 8 bit types cannot be built";

	@Unique
	private static final String NARROW_SUBGROUP =
			"a pack reducing 16 or 8 bit values across a subgroup cannot be built";

	@Unique
	private static final String PER_BUFFER = "a pack requiring PER_BUFFER_BLENDING is refused and "
			+ "every other one keeps a single blend function for all the targets a pass writes";

	@Unique
	private static final String GEOMETRY = "a program shipping a geometry stage is refused unless "
			+ "that stage only hands each corner on, which is folded into the fragment stage";

	@WrapOperation(method = "createDevice(JLcom/mojang/blaze3d/shaders/ShaderSource;"
			+ "Lcom/mojang/blaze3d/shaders/GpuDebugOptions;Ljava/lang/Runnable;)"
			+ "Lcom/mojang/blaze3d/systems/GpuDevice;", require = 1,
			at = @At(value = "INVOKE",
					target = "Lcom/mojang/blaze3d/vulkan/VulkanBackend;createDevice("
							+ "Ljava/util/Collection;Lcom/mojang/blaze3d/vulkan/VulkanPhysicalDevice;"
							+ "Ljava/util/Set;)Lorg/lwjgl/vulkan/VkDevice;"))
	private VkDevice corevk$deviceFeatures(Collection<String> extensions,
			VulkanPhysicalDevice physical, Set<VulkanFeature> features,
			Operation<VkDevice> original) {
		List<String> enabled = new ArrayList<>();
		enable(physical, features, VERTEX_STORES, enabled, VOXELS);
		enable(physical, features, FRAGMENT_STORES, enabled, VOXELS);
		enable(physical, features, EXTENDED_FORMATS, enabled, VOXELS);
		enable(physical, features, WRITE_WITHOUT_FORMAT, enabled, VOXELS);
		BufferBlending.serve(enable(physical, features, INDEPENDENT_BLEND, enabled, PER_BUFFER));
		GeometryStage.serve(enable(physical, features, GEOMETRY_SHADER, enabled, GEOMETRY));
		if (physical.hasDeviceExtension(PORTABILITY_EXTENSION)) {
			enable(physical, features, MUTABLE_COMPARISON, enabled, COMPARISON);
		}

		boolean moltenVk =
				physical.vkPhysicalDeviceDriverProperties().driverID() == VK12.VK_DRIVER_ID_MOLTENVK;
		WideSamplerSets.serve(moltenVk,
				physical.vkPhysicalDeviceProperties().limits().maxPerStageDescriptorSamplers(),
				moltenVk ? tableSamplers(physical) : 0);
		VendorExtensions.serveMoltenVk(moltenVk);

		SharedMemory.serve(moltenVk);

		List<String> vendor = VendorExtensions.serve(physical::hasDeviceExtension, extensions::add);
		if (!vendor.isEmpty()) {
			CoreVK.logger().info("Vulkan vendor extensions: {}", String.join(", ", vendor));
		}

		Set<ProgramStage> noSubgroups = VendorExtensions.serveSubgroupStages(subgroupStages(physical));
		if (!noSubgroups.isEmpty()) {
			CoreVK.logger().info("Vulkan subgroup operations absent from stages: {}", noSubgroups);
		}

		enable(physical, features, SHADER_FLOAT16, enabled, NARROW);
		enable(physical, features, SHADER_INT8, enabled, NARROW);
		enable(physical, features, SHADER_INT16, enabled, NARROW);
		enable(physical, features, SUBGROUP_EXTENDED, enabled, NARROW_SUBGROUP);
		enable(physical, features, STORAGE_16, enabled, NARROW);
		enable(physical, features, UNIFORM_STORAGE_16, enabled, NARROW);
		enable(physical, features, STORAGE_8, enabled, NARROW);
		enable(physical, features, UNIFORM_STORAGE_8, enabled, NARROW);
		if (!enabled.isEmpty()) {
			CoreVK.logger().info("Vulkan device features: {}", String.join(", ", enabled));
		}

		return original.call(extensions, physical, features);
	}

	@Unique
	private static boolean enable(VulkanPhysicalDevice physical, Set<VulkanFeature> features,
			VulkanFeature feature, List<String> enabled, String cost) {
		if (!supported(physical, feature)) {
			CoreVK.logger().warn("Vulkan device does not support {}, so {}", feature.name(), cost);
			return false;
		}

		features.add(feature);
		enabled.add(feature.name());

		return true;
	}

	@Unique
	private static Set<ProgramStage> subgroupStages(VulkanPhysicalDevice physical) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			VkPhysicalDeviceSubgroupProperties subgroups =
					VkPhysicalDeviceSubgroupProperties.calloc(stack).sType$Default();
			VkPhysicalDeviceProperties2 properties =
					VkPhysicalDeviceProperties2.calloc(stack).sType$Default().pNext(subgroups);
			VK11.vkGetPhysicalDeviceProperties2(physical.vkPhysicalDevice(), properties);
			int bits = subgroups.supportedStages();

			Set<ProgramStage> stages = EnumSet.noneOf(ProgramStage.class);
			for (Map.Entry<ProgramStage, Integer> stage : STAGE_BITS.entrySet()) {
				if ((bits & stage.getValue()) != 0) {
					stages.add(stage.getKey());
				}
			}

			return stages;
		}
	}

	@Unique
	private static int tableSamplers(VulkanPhysicalDevice physical) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			VkPhysicalDeviceVulkan12Properties vulkan12 =
					VkPhysicalDeviceVulkan12Properties.calloc(stack).sType$Default();
			VkPhysicalDeviceProperties2 properties =
					VkPhysicalDeviceProperties2.calloc(stack).sType$Default().pNext(vulkan12);
			VK11.vkGetPhysicalDeviceProperties2(physical.vkPhysicalDevice(), properties);
			return vulkan12.maxPerStageDescriptorUpdateAfterBindSamplers();
		}
	}

	@Unique
	private static boolean supported(VulkanPhysicalDevice physical, VulkanFeature feature) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			VkPhysicalDeviceFeatures2 queried = VkPhysicalDeviceFeatures2.calloc(stack).sType$Default();
			feature.struct().findOrCreateStructInPNextChain(queried, stack);
			VK12.vkGetPhysicalDeviceFeatures2(physical.vkPhysicalDevice(), queried);
			return feature.get(queried);
		}
	}
}
