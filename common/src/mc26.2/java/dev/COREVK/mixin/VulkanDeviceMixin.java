package net.corevk.corevk.mixin;

import net.corevk.corevk.mixin.access.RenderPipelineAccessor;
import net.corevk.corevk.render.EntityMesh;
import net.corevk.corevk.render.PackChain;
import net.corevk.corevk.render.StalePipelines;
import net.corevk.corevk.render.GeometryHold;
import net.corevk.corevk.CoreVK;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanRenderPipeline;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.buffers.GpuBuffer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

@Mixin(VulkanDevice.class)
public abstract class VulkanDeviceMixin implements StalePipelines {

	
	@Inject(method = {
			"createTexture(Ljava/util/function/Supplier;ILcom/mojang/blaze3d/GpuFormat;IIII)Lcom/mojang/blaze3d/textures/GpuTexture;",
			"createTexture(Ljava/lang/String;ILcom/mojang/blaze3d/GpuFormat;IIII)Lcom/mojang/blaze3d/textures/GpuTexture;"
	}, at = @At("HEAD"), require = 2)
	private void corevk$flushBeforeTextureAllocation(CallbackInfoReturnable<GpuTexture> callback) {
		GeometryHold.flushIdle(() -> "texture allocation");
	}

	
	@Inject(method = "createBuffer(Ljava/util/function/Supplier;ILjava/nio/ByteBuffer;)"
			+ "Lcom/mojang/blaze3d/buffers/GpuBuffer;", at = @At("HEAD"), require = 1)
	private void corevk$flushBeforeBufferUpload(CallbackInfoReturnable<GpuBuffer> callback) {
		GeometryHold.flushIdle(() -> "initial buffer upload");
	}

	
	@Unique
	private static final boolean COREVK$KEEP_ACROSS_RELOAD = Boolean.parseBoolean(
			System.getProperty("corevk.keepPackAcrossReload", "true"));

	@Shadow
	@Final
	private Map<RenderPipeline, VulkanRenderPipeline> pipelineCache;

	
	@Unique
	private final List<VulkanRenderPipeline> corevk$setAside = new ArrayList<>();

	
	@Unique
	private final Map<RenderPipeline, VulkanRenderPipeline> corevk$carriedOver =
			new LinkedHashMap<>();

	
	@Unique
	private boolean corevk$closing;

	@Override
	public List<RenderPipeline> corevk$dropEntityPipelines() {
		List<RenderPipeline> dropped = new ArrayList<>();
		Iterator<Map.Entry<RenderPipeline, VulkanRenderPipeline>> held =
				this.pipelineCache.entrySet().iterator();
		while (held.hasNext()) {
			Map.Entry<RenderPipeline, VulkanRenderPipeline> entry = held.next();
			if (!declaresGameEntity(entry.getKey())) {
				continue;
			}

			this.corevk$setAside.add(entry.getValue());
			dropped.add(entry.getKey());
			held.remove();
		}

		return dropped;
	}

	
	@Unique
	private static boolean declaresGameEntity(RenderPipeline pipeline) {
		if (EntityMesh.movingBlock(pipeline)) {
			return true;
		}

		@Nullable VertexFormat[] declared = ((RenderPipelineAccessor) pipeline).corevk$declaredFormats();
		for (VertexFormat format : declared) {
			@SuppressWarnings("ReferenceEquality")
			boolean entity = format == DefaultVertexFormat.ENTITY;
			if (entity) {
				return true;
			}
		}

		return false;
	}

	@Override
	public boolean corevk$adopt(RenderPipeline pipeline, VulkanRenderPipeline compiled) {
		return this.pipelineCache.putIfAbsent(pipeline, compiled) == null;
	}

	
	@Inject(method = "clearPipelineCache", at = @At("HEAD"), require = 1)
	private void corevk$carryTheLivePackOver(CallbackInfo callback) {
		// Anything a purge that threw before its tail left standing is DESTROYED with this purge
		// rather than put back, and the difference is the whole of it. Those keys left the map and
		// were never returned, so the chain missed on them the very next frame, compiled fresh ones
		// and filled the map with those; putting the old values back would overwrite live entries
		// with dead ones, leak the fresh pipelines the chain is still binding, and leave the two
		// disagreeing for the rest of the session. They join the set aside instead, which the tail
		// frees after the wait this purge already pays for.
		this.corevk$setAside.addAll(this.corevk$carriedOver.values());
		this.corevk$carriedOver.clear();

		boolean carry = COREVK$KEEP_ACROSS_RELOAD && !this.corevk$closing;
		int load = PackChain.liveLoad();
		String live = load == 0 ? null : "pipeline/pack/" + load + "/";
		int ofThePack = 0;
		int carried = 0;
		Iterator<Map.Entry<RenderPipeline, VulkanRenderPipeline>> held =
				this.pipelineCache.entrySet().iterator();
		while (held.hasNext()) {
			Map.Entry<RenderPipeline, VulkanRenderPipeline> entry = held.next();
			// Counted for every load of the pack and not only the live one, or a session whose pack
			// was stopped would read "0 pipelines of the pack" off a purge destroying its whole set.
			if (!ofAPack(entry.getKey())) {
				continue;
			}

			ofThePack++;
			if (!carry || live == null || !entry.getKey().getLocation().getPath().startsWith(live)) {
				continue;
			}

			carried++;
			this.corevk$carriedOver.put(entry.getKey(), entry.getValue());
			held.remove();
		}

		CoreVK.logger().info("Device purge: {} pipelines of the pack in the cache, {} carried over "
				+ "it{}, property=corevk.keepPackAcrossReload", ofThePack, carried,
				this.corevk$closing ? ", the device is closing" : "");
	}

	
	@Inject(method = "close", at = @At("HEAD"), require = 1)
	private void corevk$deviceClosing(CallbackInfo callback) {
		this.corevk$closing = true;
	}

	
	@Unique
	private static boolean ofAPack(RenderPipeline pipeline) {
		Identifier location = pipeline.getLocation();
		String namespace = location.getNamespace();

		return (namespace.equals(CoreVK.MOD_ID) || namespace.startsWith(CoreVK.MOD_ID + "_"))
				&& location.getPath().startsWith("pipeline/pack/");
	}

	@Inject(method = "clearPipelineCache", at = @At("TAIL"), require = 1)
	private void corevk$closeThePurge(CallbackInfo callback) {
		this.corevk$setAside.forEach(VulkanRenderPipeline::destroy);
		this.corevk$setAside.clear();
		// Put back into a map the emptying has just cleared, so nothing here can land on a key the
		// purge left standing.
		this.pipelineCache.putAll(this.corevk$carriedOver);
		this.corevk$carriedOver.clear();
	}
}
