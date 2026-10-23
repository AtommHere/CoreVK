package net.corevk.corevk.render;

import net.corevk.corevk.cache.ModuleCache;
import net.corevk.corevk.glsl.PackProgram;
import net.corevk.corevk.glsl.SharedMemory;
import net.corevk.corevk.glsl.TranslatedUnit;
import net.corevk.corevk.mixin.access.CommandEncoderAccessor;
import net.corevk.corevk.mixin.access.GpuDeviceAccessor;
import net.corevk.corevk.mixin.access.VulkanCommandEncoderAccessor;
import net.corevk.corevk.pack.model.ProgramNames;
import net.corevk.corevk.pack.model.ProgramStage;
import net.corevk.corevk.pack.model.RenderStage;
import net.corevk.corevk.pack.model.TargetName;
import net.corevk.corevk.pack.model.TextureStage;
import net.corevk.corevk.pack.source.OpenedPack;
import net.corevk.corevk.pack.target.SamplerPlan;
import net.corevk.corevk.pack.target.TargetSchedule;
import net.corevk.corevk.pack.texture.CustomImages;
import net.corevk.corevk.render.storage.GpuRecording;
import net.corevk.corevk.render.storage.StorageBuffers;
import net.corevk.corevk.render.storage.StorageImages;
import net.corevk.corevk.uniform.ClipSpace;
import net.corevk.corevk.uniform.UniformCatalog;
import net.corevk.corevk.CoreVK;

import com.mojang.blaze3d.GpuDeviceLossException;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.GpuDeviceBackend;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vulkan.VulkanCommandEncoder;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanGpuBuffer;
import com.mojang.blaze3d.vulkan.VulkanGpuSampler;
import com.mojang.blaze3d.vulkan.VulkanGpuTextureView;
import com.mojang.blaze3d.vulkan.VulkanUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.util.vma.Vma;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.vulkan.KHRPushDescriptor;
import org.lwjgl.vulkan.KHRSynchronization2;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VK13;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkComputePipelineCreateInfo;
import org.lwjgl.vulkan.VkDependencyInfo;
import org.lwjgl.vulkan.VkDescriptorBufferInfo;
import org.lwjgl.vulkan.VkDescriptorImageInfo;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.lwjgl.vulkan.VkMemoryBarrier2;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.lwjgl.vulkan.VkWriteDescriptorSet;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class PackCompute implements AutoCloseable {

	private static final int SHADERC_VULKAN_1_2 = 4202496;
	private static final int SHADERC_COMPUTE = 2;

	
	private static final String MODULE_CACHE_STAGE = "COMPUTE/shaderc-opt2-vulkan1.2";

	
	private static final int PUSH_DESCRIPTORS = 32;

	
	private static final Pattern COLOUR_IMAGE = Pattern.compile("\\bcolorimg(\\d+)\\b");

	
	private final List<Pass> passes;

	
	private final Map<String, List<Pass>> chained;

	
	private final Map<String, List<Pass>> alone;

	
	private final Set<Integer> storageTargets;

	private boolean announced;

	
	private final Set<String> announcedChains = new LinkedHashSet<>();

	private PackCompute(List<Pass> passes, Map<String, List<Pass>> chained,
			Map<String, List<Pass>> alone, Set<Integer> storageTargets) {
		this.passes = List.copyOf(passes);
		this.chained = Map.copyOf(chained);
		this.alone = Map.copyOf(alone);
		this.storageTargets = Set.copyOf(storageTargets);
	}

	static PackCompute none() {
		return new PackCompute(List.of(), Map.of(), Map.of(), Set.of());
	}

	
	Set<Integer> storageTargets() {
		return this.storageTargets;
	}

	
	boolean hangsOff(String program) {
		return this.chained.containsKey(program);
	}

	
	boolean readsCenterDepth() {
		return readsCenterDepth(this.chained) || readsCenterDepth(this.alone);
	}

	private static boolean readsCenterDepth(Map<String, List<Pass>> computes) {
		for (List<Pass> passes : computes.values()) {
			for (Pass pass : passes) {
				for (TranslatedUnit.Uniform sampler : pass.compute.loaded().program().samplers()) {
					if (sampler.name().equals(SamplerPlan.centerDepth())) {
						return true;
					}
				}
			}
		}

		return false;
	}

	
	static PackCompute load(OpenedPack pack, String place, List<String> computes, int load,
			UniformCatalog shadowCatalog, UniformCatalog chainCatalog, Set<String> running,
			Set<String> passing) {
		List<Pass> passes = new ArrayList<>();
		Map<String, List<Pass>> chained = new LinkedHashMap<>();
		Map<String, List<Pass>> alone = new LinkedHashMap<>();
		Set<Integer> storageTargets = new LinkedHashSet<>();
		for (String name : computes) {
			boolean shadow = ProgramNames.shadowComposite(ProgramNames.familyOf(name));
			Optional<String> base = ProgramNames.computeBase(name);
			// Setup has no moment in this frame yet. The plan already names it.
			if (!shadow && base.isEmpty()) {
				continue;
			}

			// A program the place merely draws no pass for was never asked about: it is the
			// reference's own invalid source, and its computes run there, so they run here. What is
			// left over is a program taken out of the frame on purpose, by the pass filter at the
			// user's ask or by this engine refusing a sampler it cannot bind, and a final nothing
			// draws, whose computes the reference builds with the final and never without it. The
			// plan tells the three apart in its notes; here they take the same road.
			boolean standalone = !shadow && passing.contains(base.get());
			if (!shadow && !standalone && !running.contains(base.get())) {
				CoreVK.logger().warn("compute {} is not dispatched: nothing of this chain runs {}, "
						+ "and the plan's notes say what took it out", name, base.get());
				continue;
			}

			String path = place.isEmpty() ? name : place + "/" + name;
			try {
				Optional<PackProgram.Compute> compute = PackProgram.loadCompute(pack, path);
				if (compute.isEmpty()) {
					continue;
				}

				List<String> unusable = StorageImages.unusableViews(compute.get().loaded().program()
						.samplers().stream().map(TranslatedUnit.Uniform::name).toList());
				if (!unusable.isEmpty()) {
					CoreVK.logger().warn("compute {} is not dispatched: it reads {} through a view "
							+ "this device cannot sample and store in that format", path, unusable);
					continue;
				}

				// Ahead of the roads parting and once for the program: any of the three can be
				// missing a word, and a word means the same on all of them, that the directive it
				// belongs to is read as absent. What that absence costs is the size said below.
				// Said here rather than beside a size, where a word off a road the dispatch never
				// took would read as the reason for a size that was read correctly.
				List<String> unread = compute.get().unresolved();
				if (!unread.isEmpty()) {
					CoreVK.logger().info("compute {} reads no number for {}, so that directive "
							+ "counts as absent", path, String.join(", ", unread));
				}

				// Left undispatched rather than dispatched at a guessed size. A program on one of
				// the screen roads is sized by dividing the screen by its own local size, and a
				// local size this engine cannot read as a number would have to be invented: read
				// as one where the shader means sixteen, the guess asks for a group per pixel and
				// stalls the frame instead of drawing it wrong.
				if (!compute.get().sized()) {
					CoreVK.logger().warn("compute {} is not dispatched: it leaves its work "
							+ "group count to the engine and writes its local size as something "
							+ "other than a number, so how much work it asks for cannot be read",
							path);
					continue;
				}

				Pass pass = new Pass(compute.get(), shadow ? shadowCatalog : chainCatalog, load,
						path, shadow ? null : base.get());
				if (shadow) {
					passes.add(pass);
					CoreVK.logger().info("Loaded shadow compute {} ({})", path, sizing(compute.get()));
				} else if (standalone) {
					alone.computeIfAbsent(base.get(), _ -> new ArrayList<>()).add(pass);
					storageTargets.addAll(colourImagesOf(compute.get()));
					CoreVK.logger().info("Loaded compute {} at the moment of {}, which draws "
							+ "nothing here ({})", path, base.get(), sizing(compute.get()));
				} else {
					chained.computeIfAbsent(base.get(), _ -> new ArrayList<>()).add(pass);
					storageTargets.addAll(colourImagesOf(compute.get()));
					CoreVK.logger().info("Loaded compute {} before {} ({})", path, base.get(),
							sizing(compute.get()));
				}

				// The one binding the pack's own text cannot be read for, said here for a compute
				// as PackPass.describe says it for a pass. Past all three roads rather than on one
				// of them, and once per program: a compute of a full screen family takes the
				// default whether or not the program it hangs off draws anything, and Pegasus,
				// whose prepare stage is four computes and no pass, would never have said so.
				List<String> defaulted = compute.get().loaded().samplers().defaulted();
				if (!defaulted.isEmpty()) {
					CoreVK.logger().info("{} reads {} by default under {}", path,
							TargetName.canonical(SamplerPlan.DEFAULT_TARGET),
							String.join(", ", defaulted));
				}
			} catch (IOException | RuntimeException e) {
				CoreVK.logger().warn("compute {} could not be translated: {}", path, e.toString());
			}
		}

		chained.values().forEach(list -> list.sort(
				Comparator.comparing(pass -> ProgramNames.computeLetter(pass.name))));
		alone.values().forEach(list -> list.sort(
				Comparator.comparing(pass -> ProgramNames.computeLetter(pass.name))));

		return new PackCompute(passes, chained, alone, storageTargets);
	}

	
	private static Set<Integer> colourImagesOf(PackProgram.Compute compute) {
		Set<Integer> indices = new LinkedHashSet<>();
		TranslatedUnit unit = compute.loaded().program().stages().get(ProgramStage.COMPUTE);
		if (unit == null) {
			return indices;
		}

		Matcher matcher = COLOUR_IMAGE.matcher(unit.text());
		while (matcher.find()) {
			indices.add(Integer.parseInt(matcher.group(1)));
		}

		return indices;
	}

	
	void dispatchBefore(String program, CommandEncoder encoder, GpuDevice device, PackValues values,
			ColorTargets targets, TargetSchedule.Bound step, GpuTextureView depth,
			GpuTextureView distant, int width, int height) {
		dispatchChain(this.chained.get(program), program, encoder, device, values, targets, step,
				depth, distant, width, height);
	}

	
	void dispatchAlone(String program, CommandEncoder encoder, GpuDevice device, PackValues values,
			ColorTargets targets, TargetSchedule.Bound step, GpuTextureView depth,
			GpuTextureView distant, int width, int height) {
		dispatchChain(this.alone.get(program), program, encoder, device, values, targets, step,
				depth, distant, width, height);
	}

	
	Set<String> standingAlone() {
		return this.alone.keySet();
	}

	private void dispatchChain(List<Pass> attached, String program, CommandEncoder encoder,
			GpuDevice device, PackValues values, ColorTargets targets, TargetSchedule.Bound step,
			GpuTextureView depth, GpuTextureView distant, int width, int height) {
		if (attached == null || attached.isEmpty()) {
			return;
		}

		// The hold first, as the head-of-frame dispatch does: the first pass of a chain half can
		// still have the geometry's render pass object open over it, and ending the pass
		// underneath would leave that object to close a pass the encoder no longer has.
		GeometryHold.flush(() -> "the compute dispatch at " + program);
		GpuRecording.endPass(encoder);
		VulkanCommandEncoder recorder = recorder(encoder);
		VulkanDevice vulkan = vulkan(device);
		if (recorder == null || vulkan == null) {
			return;
		}

		VkCommandBuffer commands = ((VulkanCommandEncoderAccessor) recorder).corevk$commandBuffer();
		try (MemoryStack stack = MemoryStack.stackPush()) {
			beforeChainCompute(commands, stack);
		}

		for (Pass pass : attached) {
			try {
				pass.dispatch(vulkan, recorder, commands, values, targets, width, height, step, depth,
						distant);
			} catch (GpuDeviceLossException e) {
				throw e;
			} catch (RuntimeException e) {
				if (pass.failed.add(e.toString())) {
					CoreVK.logger().warn("compute {} failed: {}", pass.path, e.toString());
				}
			}
		}

		try (MemoryStack stack = MemoryStack.stackPush()) {
			afterChainCompute(commands, stack);
		}

		if (this.announcedChains.add(program)) {
			CoreVK.logger().info("Dispatched {} compute pass(es) at {}", attached.size(), program);
		}
	}

	void dispatch(PackValues values, ColorTargets targets) {
		// A frame the targets refused is one no pass of the chain draws, and this dispatch runs
		// ahead of the call that would refuse it again, and of the first call of all after a
		// load: a compute pushed here would be handed names nothing stands behind, each thrown
		// and said once, over a frame nothing reads.
		if (this.passes.isEmpty() || !targets.usable()) {
			return;
		}

		GpuDevice device = RenderSystem.tryGetDevice();
		if (device == null) {
			return;
		}

		CommandEncoder encoder = device.createCommandEncoder();
		// The hold first, and through its own door: it keeps a RenderPass OBJECT open across
		// draws, so ending the pass underneath it would leave that object to close a pass the
		// encoder no longer has, which is a crash at the next flush and not here.
		GeometryHold.flush(() -> "the shadow compute dispatch");
		GpuRecording.endPass(encoder);
		VulkanCommandEncoder recorder = recorder(encoder);
		VulkanDevice vulkan = vulkan(device);
		if (recorder == null || vulkan == null) {
			return;
		}

		VkCommandBuffer commands = ((VulkanCommandEncoderAccessor) recorder).corevk$commandBuffer();
		values.convention(ClipSpace.FORWARD);
		values.modelView(null, null);
		values.projection(null);
		values.passColour(null);
		values.renderStage(RenderStage.NONE);
		try (MemoryStack stack = MemoryStack.stackPush()) {
			afterShadowGeometry(commands, stack);
		}

		// Asked of the game and not of ColorTargets, which is the same number one frame late or
		// nought on the first. ColorTargets.screenWidth is only written by its ensure(), which
		// runs inside the world render, while this dispatch is placed at the head of the frame
		// before any of it: on the first frame after a pack loads the fields are still nought,
		// which is a dispatch of no groups at all, and every resize afterwards would size a frame
		// by the window it had before. CoreVK asks the same object at the same moment,
		// ShadowCompositeRenderer.java:213-214.
		Minecraft minecraft = Minecraft.getInstance();
		RenderTarget main = minecraft == null ? null : minecraft.gameRenderer.mainRenderTarget();
		int width = main == null ? 0 : main.width;
		int height = main == null ? 0 : main.height;
		for (Pass pass : this.passes) {
			try {
				pass.dispatch(vulkan, recorder, commands, values, targets, width, height, null, null, null);
			} catch (GpuDeviceLossException e) {
				throw e;
			} catch (RuntimeException e) {
				if (pass.failed.add(e.toString())) {
					CoreVK.logger().warn("shadow compute {} failed: {}", pass.path, e.toString());
				}
			}
		}

		try (MemoryStack stack = MemoryStack.stackPush()) {
			afterCompute(commands, stack);
		}

		if (!this.announced) {
			this.announced = true;
			CoreVK.logger().info("Dispatched {} shadow compute pass(es) at the head of the frame",
					this.passes.size());
		}
	}

	
	@Override
	public void close() {
		this.passes.forEach(Pass::close);
		this.chained.values().forEach(list -> list.forEach(Pass::close));
		this.alone.values().forEach(list -> list.forEach(Pass::close));
	}

	private static VulkanCommandEncoder recorder(CommandEncoder encoder) {
		return ((CommandEncoderAccessor) encoder).corevk$backend() instanceof VulkanCommandEncoder vulkan
				? vulkan
				: null;
	}

	private static VulkanDevice vulkan(GpuDevice device) {
		GpuDeviceBackend backend = ((GpuDeviceAccessor) device).corevk$backend();
		return backend instanceof VulkanDevice found ? found : null;
	}

	
	private static String sizing(PackProgram.Compute compute) {
		if (compute.fixed()) {
			return compute.groupsX() + "x" + compute.groupsY() + "x" + compute.groupsZ() + " groups";
		}

		String covered = compute.relative()
				? compute.renderX() + " by " + compute.renderY() + " of the screen"
				: "the whole screen";
		return covered + ", " + compute.localX() + "x" + compute.localY() + " pixels to a group";
	}

	
	private static GpuTextureView engineView(ColorTargets targets, String name) {
		ShadowTargets shadow = targets.shadow();
		return switch (name) {
			case "noisetex" -> targets.noise();
			// The HW spelling reads the same image as its plain twin, which is the whole of what
			// SEPARATE_HARDWARE_SAMPLERS asks for. A compute makes every comparison in arithmetic
			// whatever the pack declared, so nothing but the name reaches this line.
			case "shadowtex0", "shadowtex0HW" ->
					orWhite(targets, shadow == null ? null : shadow.depth());
			case "shadowtex1", "shadowtex1HW" ->
					orWhite(targets, shadow == null ? null : shadow.depthWithoutTranslucents());
			// Every name SamplerPlan reads as a shadow colour, the bare shadowcolor with them, and
			// the white stand-in for one no program of the place named. That is the rule a full
			// screen pass follows for the same names (PackPass.java:640), and the ceiling is not
			// consulted here because it does not have to be: a buffer the pack may not reach was
			// never allocated, so colour() answers null for it and white is what the compute reads,
			// exactly as it does for a buffer nothing has written.
			//
			// White covers one more case than it should, and that is a divergence rather than a
			// rule: which shadow buffers get allocated is read off the FRAGMENT stages of the place
			// alone (TargetPlan.read fills shadowNamed there and nowhere else), so a shadowcolor
			// that only a COMPUTE of the place names is never seen, and this hands the compute
			// white for a buffer the pack does mean to read. CoreVK opens it from the compute itself,
			// addShadowSamplers calling createIfEmpty for each shadowcolor the program declares, on
			// the compute path as on the composite one (CompositeRenderer.java:465,
			// samplers/CoreVKSamplers.java:156-164). Nothing of the corpus asks for it: every
			// shadowcolor a compute of it names is named by a fragment stage of the same place too.
			default -> SamplerPlan.isShadowColour(name)
					? orWhite(targets, shadow == null ? null : shadow.colour(SamplerPlan.shadowColour(name)))
					: null;
		};
	}

	private static GpuTextureView orWhite(ColorTargets targets, GpuTextureView view) {
		return view == null ? targets.white() : view;
	}

	
	private static long samplerFor(ColorTargets targets, SamplerPlan samplers, String name) {
		boolean noise = "noisetex".equals(name);
		// Every other name keeps the answer this line gave before there was one place to ask:
		// customImageFilter is NEAREST for a name no image directive declared.
		FilterMode filter = noise ? FilterMode.LINEAR : switch (samplers.binding(name).kind()) {
			case SHADOW_COLOUR -> FilterMode.LINEAR;
			case SHADOW_DEPTH -> targets.shadow().depthFilter(samplers.withoutTranslucents(name));
			default -> PackPass.customImageFilter(name);
		};

		// The map's chain is the third road's half of one answer, asked of the map like the filter:
		// it is filled at the tail of the stage that drew it and stands for the whole frame, so a
		// compute reads it exactly as the pass it hangs off does. Nothing else a compute binds
		// carries a chain a dispatch could safely climb.
		boolean mipmaps = !noise && samplers.binding(name).kind() == SamplerPlan.Kind.SHADOW_DEPTH
				&& targets.shadow().depthMipmapped(samplers.withoutTranslucents(name));

		return ((VulkanGpuSampler) PackPass.sampler(noise, filter, mipmaps)).vkSampler();
	}

	
	private static void afterShadowGeometry(VkCommandBuffer commands, MemoryStack stack) {
		shaderBarrier(commands, stack,
				VK13.VK_PIPELINE_STAGE_2_ALL_GRAPHICS_BIT
						| VK13.VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
				VK13.VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT
						| VK13.VK_ACCESS_2_SHADER_STORAGE_READ_BIT,
				VK13.VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
				VK13.VK_ACCESS_2_SHADER_STORAGE_READ_BIT
						| VK13.VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT
						| VK13.VK_ACCESS_2_SHADER_SAMPLED_READ_BIT);
	}

	
	private static void afterCompute(VkCommandBuffer commands, MemoryStack stack) {
		shaderBarrier(commands, stack, VK13.VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
				VK13.VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT,
				VK13.VK_PIPELINE_STAGE_2_ALL_GRAPHICS_BIT
						| VK13.VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
				VK13.VK_ACCESS_2_SHADER_SAMPLED_READ_BIT
						| VK13.VK_ACCESS_2_SHADER_STORAGE_READ_BIT
						| VK13.VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT);
	}

	
	private static void beforeChainCompute(VkCommandBuffer commands, MemoryStack stack) {
		shaderBarrier(commands, stack, VK13.VK_PIPELINE_STAGE_2_ALL_GRAPHICS_BIT,
				VK13.VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT
						| VK13.VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT,
				VK13.VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
				VK13.VK_ACCESS_2_SHADER_SAMPLED_READ_BIT
						| VK13.VK_ACCESS_2_SHADER_STORAGE_READ_BIT
						| VK13.VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT);
	}

	
	private static void afterChainCompute(VkCommandBuffer commands, MemoryStack stack) {
		shaderBarrier(commands, stack, VK13.VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
				VK13.VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT,
				VK13.VK_PIPELINE_STAGE_2_ALL_GRAPHICS_BIT
						| VK13.VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
				VK13.VK_ACCESS_2_SHADER_SAMPLED_READ_BIT
						| VK13.VK_ACCESS_2_COLOR_ATTACHMENT_READ_BIT
						| VK13.VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT
						| VK13.VK_ACCESS_2_SHADER_STORAGE_READ_BIT);
	}

	private static void shaderBarrier(VkCommandBuffer commands, MemoryStack stack, long srcStage,
			long srcAccess, long dstStage, long dstAccess) {
		VkMemoryBarrier2.Buffer barrier = VkMemoryBarrier2.calloc(1, stack).sType$Default();
		barrier.srcStageMask(srcStage);
		barrier.srcAccessMask(srcAccess);
		barrier.dstStageMask(dstStage);
		barrier.dstAccessMask(dstAccess);
		VkDependencyInfo dependency = VkDependencyInfo.calloc(stack).sType$Default();
		dependency.pMemoryBarriers(barrier);
		KHRSynchronization2.vkCmdPipelineBarrier2KHR(commands, dependency);
	}

	private static final class Pass {

		private final PackProgram.Compute compute;
		private final PackUniforms uniforms;
		private final String path;
		private final String name;

		
		private final String program;

		
		private final TextureStage textureStage;

		private final String label;
		private final Set<String> failed = new LinkedHashSet<>();
		private MappableRingBuffer block;
		private long shaderModule;
		private long setLayout;
		
		private boolean allocatedSets;
		private long pipelineLayout;
		private long pipeline;
		private List<ComputeShader.Binding> entries = List.of();
		private boolean compiled;

		
		private long sharedBytes;
		private long sharedBuffer;
		private long sharedAllocation;

		private Pass(PackProgram.Compute compute, UniformCatalog catalog, int load, String path,
				String program) {
			this.compute = compute;
			this.path = path;
			this.name = path.substring(path.lastIndexOf('/') + 1);
			this.program = program;
			this.textureStage = program == null
					? TextureStage.SHADOWCOMP
					: TextureStage.of(program).orElse(null);
			this.label = "pack/" + load + "/" + path + "/compute";
			this.uniforms = new PackUniforms(compute.loaded().program().uniforms(), catalog);
		}

		
		private void dispatch(VulkanDevice vulkan, VulkanCommandEncoder recorder,
				VkCommandBuffer commands, PackValues values, ColorTargets targets, int width,
				int height, TargetSchedule.Bound step, GpuTextureView depth, GpuTextureView distant) {
			if (!this.compiled) {
				compile(vulkan);
			}

			if (this.pipeline == 0L) {
				return;
			}

			writeBlock(values);
			try (MemoryStack stack = MemoryStack.stackPush()) {
				VulkanCommandEncoder.memoryBarrier(commands, stack);
				VK12.vkCmdBindPipeline(commands, VK12.VK_PIPELINE_BIND_POINT_COMPUTE, this.pipeline);
				pushDescriptors(recorder, commands, stack, targets, step, depth, distant);
				// The screen and not the shadow map: CoreVK sizes a shadow composite's compute off
				// the main render target, ShadowCompositeRenderer.java:213-214, and the resolution
				// of the shadow map is what it sizes the shadow GEOMETRY computes off instead,
				// CoreVKRenderingPipeline.java:908.
				int[] groups = this.compute.groupsAt(width, height);
				VK12.vkCmdDispatch(commands, groups[0], groups[1], groups[2]);
			}

			if (this.block != null) {
				this.block.rotate();
			}
		}

		private void compile(VulkanDevice vulkan) {
			this.compiled = true;
			TranslatedUnit unit = this.compute.loaded().program().stages().get(ProgramStage.COMPUTE);
			if (unit == null) {
				return;
			}

			// ComputeShader builds the module, through the disk store and with the zeroes the game's
			// compiler road gets, and clocks what that cost: the steps differ between the two games
			// the tree builds for, and the layout and the pipeline below do not.
			ComputeShader.Compiled compiled;
			try {
				compiled = ComputeShader.build(vulkan, this.label, sharedMemory(unit.text()),
						MODULE_CACHE_STAGE, Pass::compileSpirv);
			} catch (GpuDeviceLossException e) {
				throw e;
			} catch (Exception e) {
				CoreVK.logger().warn("compute {} SPIR-V failed: {}", this.path, e.toString());
				return;
			}

			if (compiled == null) {
				return;
			}

			this.shaderModule = compiled.module();
			this.entries = compiled.entries();
			// Named and still dispatched, which is what the graphics path does with the same
			// ceiling: a device is only obliged to allow this many descriptors in one pushed set,
			// and going past it is undefined rather than slow. Said here, once, so that a driver
			// error later has a line in the log that predicted it.
			if (this.entries.size() > PUSH_DESCRIPTORS) {
				CoreVK.logger().warn("compute {} pushes {} descriptors in one set, "
						+ "past the {} a device commonly allows at once", this.path,
						this.entries.size(), PUSH_DESCRIPTORS);
			}

			try (MemoryStack stack = MemoryStack.stackPush()) {
				createLayout(vulkan, stack);
				createPipeline(vulkan, stack);
				createSharedBuffer(vulkan, stack);
			} catch (GpuDeviceLossException e) {
				throw e;
			} catch (RuntimeException e) {
				destroy(vulkan);
				CoreVK.logger().warn("compute {} pipeline failed: {}", this.path, e.toString());
			}

			if (this.pipeline != 0L) {
				CoreVK.logger().info("Compiled compute {} ({})", this.path,
						sizing(this.compute));
			}
		}

		
		private String sharedMemory(String translated) {
			if (!SharedMemory.moltenVk() || !SharedMemory.mentioned(translated)) {
				return translated;
			}

			String preprocessed = preprocess(translated);
			if (preprocessed == null) {
				return translated;
			}

			SharedMemory.Reading reading = SharedMemory.read(preprocessed);
			if (reading.unread() != null) {
				CoreVK.logger().warn("compute {} declares shared memory this engine cannot size, "
						+ "{}, so it keeps its threadgroup memory on Metal", this.path, reading.unread());
				return translated;
			}

			if (!reading.over()) {
				return translated;
			}

			if (this.compute.groupsX() != 1 || this.compute.groupsY() != 1
					|| this.compute.groupsZ() != 1) {
				CoreVK.logger().warn("compute {} asks Metal for {} bytes of threadgroup memory, past "
						+ "the {} it allows, and does not dispatch a single work group, where one buffer "
						+ "would not be the memory each group has of its own, so Metal refuses it",
						this.path, reading.threadgroupBytes(), SharedMemory.THREADGROUP_BYTES);
				return translated;
			}

			this.sharedBytes = reading.bufferBytes();
			CoreVK.logger().info("compute {} asks Metal for {} bytes of threadgroup memory, past the "
					+ "{} it allows, so its shared variables are served from a storage buffer of {} bytes",
					this.path, reading.threadgroupBytes(), SharedMemory.THREADGROUP_BYTES,
					this.sharedBytes);
			return reading.moved();
		}

		private static ByteBuffer compileSpirv(String source) {
			long compiler = Shaderc.shaderc_compiler_initialize();
			long options = compileOptions();
			ByteBuffer sourceBuffer = MemoryUtil.memUTF8(source, false);
			ByteBuffer filename = MemoryUtil.memUTF8("shadowcomp.csh");
			ByteBuffer entry = MemoryUtil.memUTF8("main");
			long result = 0L;
			try {
				result = Shaderc.shaderc_compile_into_spv(compiler, sourceBuffer, SHADERC_COMPUTE,
						filename, entry, options);
				int status = Shaderc.shaderc_result_get_compilation_status(result);
				if (status != 0) {
					CoreVK.logger().warn("compute shaderc: {}",
							Shaderc.shaderc_result_get_error_message(result));
					return null;
				}

				ByteBuffer spirv = Shaderc.shaderc_result_get_bytes(result);
				ByteBuffer copy = MemoryUtil.memCalloc(spirv.remaining());
				MemoryUtil.memCopy(spirv, copy);
				return copy;
			} finally {
				if (result != 0L) {
					Shaderc.shaderc_result_release(result);
				}

				MemoryUtil.memFree(entry);
				MemoryUtil.memFree(filename);
				MemoryUtil.memFree(sourceBuffer);
				Shaderc.shaderc_compile_options_release(options);
				Shaderc.shaderc_compiler_release(compiler);
			}
		}

		
		private static long compileOptions() {
			long options = Shaderc.shaderc_compile_options_initialize();
			Shaderc.shaderc_compile_options_set_target_env(options, 0, SHADERC_VULKAN_1_2);
			Shaderc.shaderc_compile_options_set_auto_bind_uniforms(options, true);
			Shaderc.shaderc_compile_options_set_auto_map_locations(options, true);
			Shaderc.shaderc_compile_options_set_generate_debug_info(options);
			// Performance, and for the LAYOUT before speed: the pack's common include declares
			// samplers a compute never reads, an unoptimised module keeps them, and reflection then
			// demands a binding for every one. Optimised, the dead declarations fall out and the
			// entries are the names the shader touches.
			Shaderc.shaderc_compile_options_set_optimization_level(options, 2);
			return options;
		}

		
		private static String preprocess(String source) {
			long compiler = Shaderc.shaderc_compiler_initialize();
			long options = compileOptions();
			ByteBuffer sourceBuffer = MemoryUtil.memUTF8(source, false);
			ByteBuffer filename = MemoryUtil.memUTF8("shadowcomp.csh");
			ByteBuffer entry = MemoryUtil.memUTF8("main");
			long result = 0L;
			try {
				result = Shaderc.shaderc_compile_into_preprocessed_text(compiler, sourceBuffer,
						SHADERC_COMPUTE, filename, entry, options);
				if (result == 0L) {
					return null;
				}

				ByteBuffer bytes = Shaderc.shaderc_result_get_bytes(result);
				if (Shaderc.shaderc_result_get_compilation_status(result) != 0 || bytes == null) {
					return null;
				}

				return StandardCharsets.UTF_8.decode(bytes).toString();
			} finally {
				if (result != 0L) {
					Shaderc.shaderc_result_release(result);
				}

				MemoryUtil.memFree(entry);
				MemoryUtil.memFree(filename);
				MemoryUtil.memFree(sourceBuffer);
				Shaderc.shaderc_compile_options_release(options);
				Shaderc.shaderc_compiler_release(compiler);
			}
		}

		
		private void createSharedBuffer(VulkanDevice vulkan, MemoryStack stack) {
			if (this.sharedBytes <= 0L) {
				return;
			}

			VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc(stack).sType$Default();
			bufferInfo.size(this.sharedBytes);
			bufferInfo.usage(VK12.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
			bufferInfo.sharingMode(VK12.VK_SHARING_MODE_EXCLUSIVE);
			VmaAllocationCreateInfo allocationInfo = VmaAllocationCreateInfo.calloc(stack);
			allocationInfo.usage(8);
			LongBuffer bufferPtr = stack.callocLong(1);
			PointerBuffer allocationPtr = stack.callocPointer(1);
			VulkanUtils.crashIfFailure(vulkan,
					Vma.vmaCreateBuffer(vulkan.vma(), bufferInfo, allocationInfo, bufferPtr,
							allocationPtr, null),
					"compute shared memory");
			this.sharedBuffer = bufferPtr.get(0);
			this.sharedAllocation = allocationPtr.get(0);
		}

		
		private boolean servesShared(String name) {
			return this.sharedBytes > 0L && SharedMemory.BLOCK.equals(name);
		}

		private void createLayout(VulkanDevice vulkan, MemoryStack stack) {
			int count = Math.max(1, this.entries.size());
			VkDescriptorSetLayoutBinding.Buffer bindings =
					VkDescriptorSetLayoutBinding.calloc(this.entries.isEmpty() ? 0 : count, stack);
			for (int i = 0; i < this.entries.size(); i++) {
				ComputeShader.Binding entry = this.entries.get(i);
				boolean storage = CustomImages.storage(entry.name())
						|| StorageImages.storageBinding(entry.name())
						|| COLOUR_IMAGE.matcher(entry.name()).matches();
				int type;
				if (entry.buffer()) {
					type = StorageBuffers.named(entry.name()) || servesShared(entry.name())
							? VK12.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER
							: VK12.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
				} else {
					type = storage
							? VK12.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE
							: VK12.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
				}
				bindings.get(i).binding(i).descriptorType(type).descriptorCount(1)
						.stageFlags(VK12.VK_SHADER_STAGE_COMPUTE_BIT);
			}

			VkDescriptorSetLayoutCreateInfo layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
					.sType$Default()
					.flags(1)
					.pBindings(bindings);
			this.allocatedSets = WideSamplerSets.dropPush(layoutInfo, this.label);
			LongBuffer layoutPtr = stack.callocLong(1);
			VulkanUtils.crashIfFailure(vulkan,
					VK12.vkCreateDescriptorSetLayout(vulkan.vkDevice(), layoutInfo, null, layoutPtr),
					"compute set layout");
			this.setLayout = layoutPtr.get(0);
			LongBuffer setLayouts = stack.callocLong(1).put(0, this.setLayout);
			VkPipelineLayoutCreateInfo pipelineLayoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
					.sType$Default()
					.pSetLayouts(setLayouts);
			LongBuffer pipelineLayoutPtr = stack.callocLong(1);
			VulkanUtils.crashIfFailure(vulkan,
					VK12.vkCreatePipelineLayout(vulkan.vkDevice(), pipelineLayoutInfo, null,
							pipelineLayoutPtr),
					"compute pipeline layout");
			this.pipelineLayout = pipelineLayoutPtr.get(0);
		}

		private void createPipeline(VulkanDevice vulkan, MemoryStack stack) {
			VkPipelineShaderStageCreateInfo stage = VkPipelineShaderStageCreateInfo.calloc(stack)
					.sType$Default();
			stage.stage(VK12.VK_SHADER_STAGE_COMPUTE_BIT);
			stage.module(this.shaderModule);
			stage.pName(stack.UTF8("main"));
			VkComputePipelineCreateInfo pipelineInfo = VkComputePipelineCreateInfo.calloc(stack)
					.sType$Default();
			pipelineInfo.stage(stage);
			pipelineInfo.layout(this.pipelineLayout);
			VkComputePipelineCreateInfo.Buffer infos = VkComputePipelineCreateInfo.calloc(1, stack);
			infos.put(0, pipelineInfo);
			LongBuffer pipelinePtr = stack.callocLong(1);
			VulkanUtils.crashIfFailure(vulkan,
					VK12.vkCreateComputePipelines(vulkan.vkDevice(), 0L, infos, null, pipelinePtr),
					"compute pipeline");
			this.pipeline = pipelinePtr.get(0);
		}

		private void writeBlock(PackValues values) {
			int bytes = Math.max(16, this.uniforms.size());
			if (this.block == null) {
				this.block = new MappableRingBuffer(() -> "corevk shadowcomp " + this.path,
						GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, bytes);
			}

			try (GpuBufferSlice.MappedView view = this.block.currentBuffer().map(false, true)) {
				ByteBuffer data = view.data();
				data.position(0);
				this.uniforms.write(Std140Builder.intoBuffer(data), values.world());
			}
		}

		private void pushDescriptors(VulkanCommandEncoder recorder, VkCommandBuffer commands,
				MemoryStack stack, ColorTargets targets, TargetSchedule.Bound step,
				GpuTextureView depth, GpuTextureView distant) {
			if (this.entries.isEmpty()) {
				return;
			}

			VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(this.entries.size(), stack);
			for (int i = 0; i < this.entries.size(); i++) {
				ComputeShader.Binding entry = this.entries.get(i);
				VkWriteDescriptorSet write = writes.get(i).sType$Default();
				write.dstBinding(i);
				write.dstArrayElement(0);
				write.descriptorCount(1);
				if (entry.buffer()) {
					if (servesShared(entry.name())) {
						VkDescriptorBufferInfo.Buffer bufferInfo = VkDescriptorBufferInfo.calloc(1, stack);
						bufferInfo.buffer(this.sharedBuffer);
						bufferInfo.offset(0L);
						bufferInfo.range(this.sharedBytes);
						write.descriptorType(VK12.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER);
						write.pBufferInfo(bufferInfo);
						continue;
					}

					if (StorageBuffers.named(entry.name())) {
						StorageBuffers.Bound bound = StorageBuffers.bound(entry.name());
						if (bound == null) {
							throw new IllegalStateException("Missing storage buffer " + entry.name());
						}

						VkDescriptorBufferInfo.Buffer bufferInfo = VkDescriptorBufferInfo.calloc(1, stack);
						bufferInfo.buffer(bound.buffer());
						bufferInfo.offset(0L);
						bufferInfo.range(bound.range());
						write.descriptorType(VK12.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER);
						write.pBufferInfo(bufferInfo);
						continue;
					}

					int bytes = Math.max(16, this.uniforms.size());
					GpuBufferSlice slice = this.block.currentBuffer().slice(0, bytes);
					VkDescriptorBufferInfo.Buffer bufferInfo = VkDescriptorBufferInfo.calloc(1, stack);
					bufferInfo.buffer(((VulkanGpuBuffer) slice.buffer()).vkBuffer());
					bufferInfo.offset(slice.offset());
					bufferInfo.range(slice.length());
					write.descriptorType(VK12.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER);
					write.pBufferInfo(bufferInfo);
					continue;
				}

				StorageImages.Bound bound = StorageImages.bound(entry.name());
				VkDescriptorImageInfo.Buffer imageInfo = VkDescriptorImageInfo.calloc(1, stack);

				// A texture the pack ships answers here exactly as it answers a full screen pass:
				// which image, how it is filtered and how it is addressed outside zero to one are
				// all the pack's to say, in one directive and the .mcmeta beside the file. Asked
				// BEFORE the colour targets for the reason PackPass asks it first as well: a pack
				// may lay a lookup table over the name of a real target for one stage, and reading
				// the name first would hand the compute a copy of the scene as a lookup table.
				ColorTargets.PackBinding supplied = bound == null
						? targets.packTexture(this.textureStage, entry.name())
						: null;
				if (supplied != null && supplied.view() instanceof VulkanGpuTextureView served) {
					imageInfo.sampler(((VulkanGpuSampler) PackPass.sampler(supplied.repeat(),
							supplied.filter(), supplied.mipmaps())).vkSampler());
					imageInfo.imageView(served.vkImageView());
					imageInfo.imageLayout(VK12.VK_IMAGE_LAYOUT_GENERAL);
					write.descriptorType(VK12.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
					write.pImageInfo(imageInfo);
					continue;
				}

				// A name the pack took over for a texture nothing could be put behind. The pass
				// beside this compute reads one black texel for it (PackPass.java:672), so the compute
				// reads the same rather than the colour target of that name or a throw that takes it
				// out of the frame. A file the pack does not ship and a picture refused at its header
				// never land here: PackTextures hands their name back, as CoreVK does, and the colour
				// target lookup below answers it like any other.
				SamplerPlan.Binding taken = this.compute.loaded().samplers().binding(entry.name());
				if (bound == null && taken.kind() == SamplerPlan.Kind.PACK_TEXTURE && !taken.defaulted()
						&& targets.black() instanceof VulkanGpuTextureView blank) {
					imageInfo.sampler(samplerFor(targets, this.compute.loaded().samplers(), entry.name()));
					imageInfo.imageView(blank.vkImageView());
					imageInfo.imageLayout(VK12.VK_IMAGE_LAYOUT_GENERAL);
					write.descriptorType(VK12.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
					write.pImageInfo(imageInfo);
					continue;
				}

				if (bound == null && step != null && colourTarget(write, imageInfo, entry.name(), targets,
						this.compute.loaded().samplers(), step, this.program)) {
					continue;
				}

				if (bound == null) {
					// The depth of the pass this compute hangs off, under the three names, the far
					// terrain's and the smoothed centre depth, answered by the methods that answer
					// the pass itself: CoreVK gives a compute the depth samplers of its pass
					// (CompositeRenderer.java:462 and :470), and Reverie's cloud compute reads
					// depthtex1 to know where the sky ends. Ahead of the engine set below because
					// that set carries no depth, and a name it did not carry threw on every
					// dispatch, so the compute never ran and said so once.
					GpuTextureView read = switch (SamplerPlan.classify(entry.name())) {
						case DEPTH -> PackPass.depth(entry.name(), targets, depth);
						case DISTANT_DEPTH -> PackPass.distant(entry.name(), targets, distant);
						case CENTER_DEPTH -> PackPass.centerDepth(targets);
						default -> null;
					};
					if (read instanceof VulkanGpuTextureView served) {
						imageInfo.sampler(samplerFor(targets, this.compute.loaded().samplers(), entry.name()));
						imageInfo.imageView(served.vkImageView());
						imageInfo.imageLayout(VK12.VK_IMAGE_LAYOUT_GENERAL);
						write.descriptorType(VK12.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
						write.pImageInfo(imageInfo);
						continue;
					}

					// The samplers the engine serves every pack program, noisetex and the shadow
					// set among them: shadowcomp reads them the way a composite does, and only a
					// name neither the pack's images nor this set carries is a real miss.
					if (engineView(targets, entry.name()) instanceof VulkanGpuTextureView served) {
						imageInfo.sampler(samplerFor(targets, this.compute.loaded().samplers(), entry.name()));
						imageInfo.imageView(served.vkImageView());
						imageInfo.imageLayout(VK12.VK_IMAGE_LAYOUT_GENERAL);
						write.descriptorType(VK12.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
						write.pImageInfo(imageInfo);
						continue;
					}

					// And last, the default sampler of the family this compute hangs off. CoreVK
					// builds a compute of a stage with the same call and the same full screen flag
					// as the passes of that stage (CompositeRenderer.java:454 beside :398), so a
					// name that reads the screen in composite1.fsh reads it in composite1_a.csh as
					// well, and a rule posed on one and not the other kills the compute over a
					// declaration the pass takes in its stride. The plan already worked out what
					// colortex0 is for this program, standing overrides and all; a shadow compute
					// has no such default and its plan carries none.
					SamplerPlan.Binding byDefault =
							this.compute.loaded().samplers().binding(entry.name());
					if (byDefault.defaulted()) {
						String screen = TargetName.canonical(SamplerPlan.DEFAULT_TARGET);
						// The colour target needs the step, its half being the pass's; the file the
						// pack lays over the target does not, being one image whichever half the
						// compute is dispatched on, so the second road is not held behind the first
						// one's condition.
						if (byDefault.kind() == SamplerPlan.Kind.COLORTEX && step != null
								&& colourTarget(write, imageInfo, screen, targets,
										this.compute.loaded().samplers(), step, this.program)) {
							continue;
						}

						ColorTargets.PackBinding laid = targets.packTexture(this.textureStage, screen);
						if (laid != null && laid.view() instanceof VulkanGpuTextureView served) {
							imageInfo.sampler(((VulkanGpuSampler) PackPass.sampler(laid.repeat(),
									laid.filter(), laid.mipmaps())).vkSampler());
							imageInfo.imageView(served.vkImageView());
							imageInfo.imageLayout(VK12.VK_IMAGE_LAYOUT_GENERAL);
							write.descriptorType(VK12.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
							write.pImageInfo(imageInfo);
							continue;
						}

						// And one black texel where the default resolved to nothing, which is what
						// the pass beside this compute binds for the very same name
						// (PackPass.java:724). The pass draws on black there; taking the compute
						// out of the frame instead would be this engine answering one declaration
						// two ways.
						if (targets.black() instanceof VulkanGpuTextureView blank) {
							imageInfo.sampler(samplerFor(targets, this.compute.loaded().samplers(), entry.name()));
							imageInfo.imageView(blank.vkImageView());
							imageInfo.imageLayout(VK12.VK_IMAGE_LAYOUT_GENERAL);
							write.descriptorType(VK12.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
							write.pImageInfo(imageInfo);
							continue;
						}
					}

					throw new IllegalStateException("Missing storage image " + entry.name());
				}

				boolean storage = bound.storage();
				imageInfo.sampler(storage ? 0L
						: samplerFor(targets, this.compute.loaded().samplers(), entry.name()));
				imageInfo.imageView(bound.view());
				imageInfo.imageLayout(VK12.VK_IMAGE_LAYOUT_GENERAL);
				write.descriptorType(storage
						? VK12.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE
						: VK12.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
				write.pImageInfo(imageInfo);
			}

			if (this.allocatedSets) {
				WideSamplerSets.bind(recorder, commands, VK12.VK_PIPELINE_BIND_POINT_COMPUTE,
						this.pipelineLayout, 0, this.setLayout, writes);
				return;
			}

			KHRPushDescriptor.vkCmdPushDescriptorSetKHR(commands,
					VK12.VK_PIPELINE_BIND_POINT_COMPUTE, this.pipelineLayout, 0, writes);
		}

		
		private static boolean colourTarget(VkWriteDescriptorSet write,
				VkDescriptorImageInfo.Buffer imageInfo, String name, ColorTargets targets,
				SamplerPlan samplers, TargetSchedule.Bound step, String program) {
			Matcher image = COLOUR_IMAGE.matcher(name);
			if (image.matches()) {
				int index = Integer.parseInt(image.group(1));
				TargetSurface surface = targets.surface(index, step.read(index));
				if (surface == null || !surface.storage()
						|| !(surface.storageView() instanceof VulkanGpuTextureView view)) {
					throw new IllegalStateException(name + " is not a target created writable from "
							+ "a compute");
				}

				imageInfo.sampler(0L);
				imageInfo.imageView(view.vkImageView());
				imageInfo.imageLayout(VK12.VK_IMAGE_LAYOUT_GENERAL);
				write.descriptorType(VK12.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE);
				write.pImageInfo(imageInfo);
				return true;
			}

			OptionalInt index = TargetName.index(name);
			if (index.isEmpty()) {
				return false;
			}

			TargetSurface surface = targets.surface(index.getAsInt(), step.read(index.getAsInt()));

			// One black texel for a target this place never allocated, which is the answer a full
			// screen pass gives the same name (PackPass.java:724). Throwing here took the whole
			// compute out of the frame over a declaration the pass beside it draws with.
			if (surface == null || !(surface.view() instanceof VulkanGpuTextureView view)) {
				if (!(targets.black() instanceof VulkanGpuTextureView blank)) {
					return false;
				}

				imageInfo.sampler(samplerFor(targets, samplers, name));
				imageInfo.imageView(blank.vkImageView());
				imageInfo.imageLayout(VK12.VK_IMAGE_LAYOUT_GENERAL);
				write.descriptorType(VK12.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
				write.pImageInfo(imageInfo);
				return true;
			}

			// Clamped, filtered and mipmapped the way the pass this hangs off binds the same
			// target: the pack's own indexing stays inside the image, an integer format takes no
			// filtering, and the chain is only in reach once something has written it and the
			// pass reads the target at a lod. CoreVK has one texture with one set of parameters
			// for both, so a compute and its pass sample it the same way.
			boolean mipmaps = surface.chainWritten()
					&& targets.lodReads(program).contains(index.getAsInt());
			imageInfo.sampler(((VulkanGpuSampler) PackPass.sampler(false,
					targets.filter(index.getAsInt()), mipmaps)).vkSampler());
			imageInfo.imageView(view.vkImageView());
			imageInfo.imageLayout(VK12.VK_IMAGE_LAYOUT_GENERAL);
			write.descriptorType(VK12.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
			write.pImageInfo(imageInfo);
			return true;
		}

		private void close() {
			GpuDevice device = RenderSystem.tryGetDevice();
			VulkanDevice vulkan = device == null ? null : vulkan(device);
			if (vulkan != null) {
				destroy(vulkan);
			}

			if (this.block != null) {
				GpuRecording.destroyLater(this.block::close);
				this.block = null;
			}
		}

		
		private void destroy(VulkanDevice vulkan) {
			long pipeline = this.pipeline;
			long pipelineLayout = this.pipelineLayout;
			long setLayout = this.setLayout;
			long shaderModule = this.shaderModule;
			long sharedBuffer = this.sharedBuffer;
			long sharedAllocation = this.sharedAllocation;
			this.pipeline = 0L;
			this.pipelineLayout = 0L;
			this.setLayout = 0L;
			this.shaderModule = 0L;
			this.sharedBuffer = 0L;
			this.sharedAllocation = 0L;
			GpuRecording.destroyLater(() -> {
				if (sharedBuffer != 0L) {
					Vma.vmaDestroyBuffer(vulkan.vma(), sharedBuffer, sharedAllocation);
				}

				if (pipeline != 0L) {
					VK12.vkDestroyPipeline(vulkan.vkDevice(), pipeline, null);
				}

				if (pipelineLayout != 0L) {
					VK12.vkDestroyPipelineLayout(vulkan.vkDevice(), pipelineLayout, null);
				}

				if (setLayout != 0L) {
					VK12.vkDestroyDescriptorSetLayout(vulkan.vkDevice(), setLayout, null);
				}

				if (shaderModule != 0L) {
					VK12.vkDestroyShaderModule(vulkan.vkDevice(), shaderModule, null);
				}
			});
		}
	}
}
