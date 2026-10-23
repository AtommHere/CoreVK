package net.corevk.corevk.render;

import net.corevk.corevk.glsl.GlslTranslator;
import net.corevk.corevk.glsl.PackProgram;
import net.corevk.corevk.glsl.TranslatedUnit;
import net.corevk.corevk.mixin.access.GpuDeviceAccessor;
import net.corevk.corevk.pack.target.SamplerPlan;
import net.corevk.corevk.CoreVK;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.GpuDeviceBackend;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import net.minecraft.client.Minecraft;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkSamplerCreateInfo;

import java.nio.LongBuffer;
import java.nio.file.Files;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class ShadowCompare {

	private static final String ARM_FILE = "soft-shadow-compare";

	private static final Map<RenderPipeline, Set<String>> COMPARED =
			Collections.synchronizedMap(new WeakHashMap<>());

	
	private static volatile boolean noted;

	private static boolean announced;

	private static long sampler;

	private ShadowCompare() {
	}

	
	public static void armIfAsked() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null || minecraft.gameDirectory == null) {
			return;
		}

		boolean asked = Files.isRegularFile(minecraft.gameDirectory.toPath()
				.resolve("corevk").resolve(ARM_FILE));
		GlslTranslator.askSoftCompare(asked);
		if (asked && !announced) {
			announced = true;
			CoreVK.logger().warn("Every shadow comparison is made in shader arithmetic, asked for "
					+ "by corevk/{}. The image should not move; the shadow lookups are slower. "
					+ "Remove it and reload the pack to put the comparison back on the sampler",
					ARM_FILE);
		}
	}

	
	static void note(RenderPipeline pipeline, String path, PackProgram.Loaded loaded) {
		Set<String> names = new LinkedHashSet<>();
		for (TranslatedUnit unit : loaded.program().stages().values()) {
			names.addAll(unit.notes().hardwareCompared());
		}

		if (names.isEmpty()) {
			return;
		}

		for (String name : names) {
			if (loaded.samplers().binding(name).kind() != SamplerPlan.Kind.SHADOW_DEPTH) {
				CoreVK.logger().warn("{} declares {} as a comparison sampler, and the pack has "
						+ "put something that is not the shadow map behind the name: the "
						+ "comparison runs against it all the same, which is undefined here as "
						+ "it is under CoreVK", path, name);
			}
		}

		for (TranslatedUnit unit : loaded.program().stages().values()) {
			for (String name : names) {
				if (!unit.notes().hardwareCompared().contains(name)
						&& unit.samplers().stream().anyMatch(one -> one.name().equals(name))) {
					CoreVK.logger().warn("{} declares {} as a comparison sampler in one stage and "
							+ "an ordinary one in another. The sampler is the pipeline's, so the "
							+ "ordinary read goes through the comparison too, which is undefined "
							+ "here as it is under CoreVK", path, name);
				}
			}
		}

		COMPARED.put(pipeline, Set.copyOf(names));
		noted = true;
	}

	
	static void noteBeside(RenderPipeline variant, RenderPipeline base) {
		Set<String> names = COMPARED.get(base);
		if (names != null) {
			COMPARED.put(variant, names);
		}
	}

	
	public static boolean noted() {
		return noted;
	}

	
	public static Set<String> compared(RenderPipeline pipeline) {
		Set<String> names = COMPARED.get(pipeline);

		return names == null ? Set.of() : names;
	}

	
	public static long sampler(VulkanDevice device) {
		if (sampler != 0L) {
			return sampler;
		}

		try (MemoryStack stack = MemoryStack.stackPush()) {
			VkSamplerCreateInfo info = VkSamplerCreateInfo.calloc(stack)
					.sType$Default()
					.magFilter(VK12.VK_FILTER_LINEAR)
					.minFilter(VK12.VK_FILTER_LINEAR)
					.mipmapMode(VK12.VK_SAMPLER_MIPMAP_MODE_NEAREST)
					.addressModeU(VK12.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
					.addressModeV(VK12.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
					.addressModeW(VK12.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
					.compareEnable(true)
					.compareOp(VK12.VK_COMPARE_OP_LESS_OR_EQUAL)
					// Level nought and no further, and that is now a KEPT gap rather than an
					// absence: the map may carry a chain, and CoreVK hands a compared read a
					// mipmapped sampler where the pack asked for one
					// (ShadowRenderTargets.getSamplerFor, MIPPED_LINEAR_HW). Lifting the ceiling
					// here would serve it, and it is not lifted, for two reasons that hold
					// together. This is ONE object for the device's life, so it cannot follow a
					// per-image directive, and no pack of the corpus asks for a chain and a
					// comparison on the same image, so nothing would measure the change. What the
					// ceiling is NOT is a defence against the implicit level a compared read used
					// to keep: the translation pins those now, and an equal ceiling on the ordinary
					// samplers is exactly what was measured not to hold. Lifting it belongs with
					// the second comparison sampler, and with a pack to prove it on.
					.minLod(0.0F)
					.maxLod(0.0F);
			LongBuffer handle = stack.mallocLong(1);
			int result = VK12.vkCreateSampler(device.vkDevice(), info, null, handle);
			if (result != VK12.VK_SUCCESS) {
				throw new IllegalStateException("vkCreateSampler answered " + result);
			}

			sampler = handle.get(0);
		}

		return sampler;
	}

	
	static void close() {
		COMPARED.clear();
		noted = false;
		if (sampler == 0L) {
			return;
		}

		VulkanDevice device = vulkan();
		if (device != null) {
			VK12.vkDestroySampler(device.vkDevice(), sampler, null);
		}

		sampler = 0L;
	}

	private static VulkanDevice vulkan() {
		GpuDevice device = RenderSystem.tryGetDevice();
		if (device == null) {
			return null;
		}

		GpuDeviceBackend backend = ((GpuDeviceAccessor) device).corevk$backend();
		return backend instanceof VulkanDevice vulkan ? vulkan : null;
	}
}
