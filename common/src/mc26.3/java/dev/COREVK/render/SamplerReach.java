package net.corevk.corevk.render;

import net.corevk.corevk.CoreVK;

import com.mojang.renderpearl.backend.api.SpvModule;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.spvc.Spvc;
import org.lwjgl.util.spvc.SpvcReflectedResource;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public final class SamplerReach {

	private static final int SAMPLED_IMAGE = Spvc.SPVC_RESOURCE_TYPE_SAMPLED_IMAGE;

	private static final boolean DECLARED = Boolean.getBoolean("corevk.declaredSamplers");

	private static final AtomicLong WALKED = new AtomicLong();
	private static final AtomicLong NARROWED = new AtomicLong();
	private static final AtomicLong DROPPED = new AtomicLong();

	private SamplerReach() {
	}

	public interface Narrowable {

		void corevk$unreached(Set<String> unreached);
	}

	public static String cacheWord() {
		return DECLARED ? "declared-samplers" : "reached-samplers";
	}

	public static void narrow(String filename, ByteBuffer spirv, Object module) {
		if (DECLARED || !(module instanceof Narrowable narrowable) || !RawLocals.ours(filename)) {
			return;
		}

		WALKED.incrementAndGet();
		Set<String> unreached = unreached(spirv);
		if (!unreached.isEmpty()) {
			narrowable.corevk$unreached(unreached);
		}
	}

	public static List<SpvModule.Reflection.Descriptor> keep(
			List<SpvModule.Reflection.Descriptor> reflected, int type, Set<String> unreached) {
		if (type != SAMPLED_IMAGE || unreached.isEmpty()) {
			return reflected;
		}

		List<SpvModule.Reflection.Descriptor> kept = new ArrayList<>(reflected.size());
		for (SpvModule.Reflection.Descriptor descriptor : reflected) {
			if (!unreached.contains(descriptor.name())) {
				kept.add(descriptor);
			}
		}

		int gone = reflected.size() - kept.size();
		if (gone > 0) {
			NARROWED.incrementAndGet();
			DROPPED.addAndGet(gone);
		}

		return kept;
	}

	public static void say(long compiled) {
		long walked = WALKED.getAndSet(0L);
		long narrowed = NARROWED.getAndSet(0L);
		long dropped = DROPPED.getAndSet(0L);
		if (DECLARED) {
			CoreVK.logger().warn("Every DECLARED sampler given a binding, asked for by "
					+ "-Dcorevk.declaredSamplers ({} modules built this load, none walked): a pack "
					+ "whose shared include declares more samplers than a stage reads is numbered "
					+ "past Metal's sixteen slots, which on Apple hardware takes an allocated set or is "
					+ "refused",
					compiled);

			return;
		}

		CoreVK.logger().info("Samplers bound from what a module reaches: {} dropped across {} of "
						+ "the {} pack modules walked ({} modules built this load in all, the "
						+ "game's and Sodium's among them)", dropped, narrowed, walked, compiled);
	}

	private static Set<String> unreached(ByteBuffer spirv) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			PointerBuffer pointer = stack.callocPointer(1);
			if (Spvc.spvc_context_create(pointer) != 0) {
				return Set.of();
			}

			long context = pointer.get(0);
			try {
				if (Spvc.spvc_context_parse_spirv(context, spirv.asIntBuffer(),
						spirv.remaining() / 4, pointer) != 0) {
					return Set.of();
				}

				long ir = pointer.get(0);
				if (Spvc.spvc_context_create_compiler(context, 0, ir, 1, pointer) != 0) {
					return Set.of();
				}

				long compiler = pointer.get(0);
				if (Spvc.spvc_compiler_create_shader_resources(compiler, pointer) != 0) {
					return Set.of();
				}

				List<String> declared = sampledImages(stack, pointer.get(0));
				if (declared.isEmpty()) {
					return Set.of();
				}

				if (Spvc.spvc_compiler_get_active_interface_variables(compiler, pointer) != 0) {
					return Set.of();
				}

				long active = pointer.get(0);
				if (Spvc.spvc_compiler_create_shader_resources_for_active_variables(compiler,
						pointer, active) != 0) {
					return Set.of();
				}

				Set<String> unreached = new HashSet<>(declared);
				unreached.removeAll(sampledImages(stack, pointer.get(0)));

				return unreached;
			} finally {
				Spvc.spvc_context_destroy(context);
			}
		}
	}

	private static List<String> sampledImages(MemoryStack stack, long resources) {
		PointerBuffer list = stack.callocPointer(1);
		PointerBuffer count = stack.callocPointer(1);
		if (Spvc.spvc_resources_get_resource_list_for_type(resources, SAMPLED_IMAGE, list,
				count) != 0) {
			return List.of();
		}

		int found = (int) count.get(0);
		if (found == 0) {
			return List.of();
		}

		List<String> names = new ArrayList<>(found);
		SpvcReflectedResource.Buffer reflected = SpvcReflectedResource.create(list.get(0), found);
		for (int index = 0; index < found; index++) {

			names.add(reflected.get(index).nameString());
		}

		return names;
	}
}
