package net.corevk.corevk.render;

import net.corevk.corevk.render.timing.PassTimings;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import org.joml.Vector4fc;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;

public final class GeometryHold {

	private static final int JOINS = -1;

	private static final int NO_HOLD = -2;

	private static final int COUNT_DIFFERS = -3;
	private static final int DEPTH_DIFFERS = -4;
	private static final int AREA_DIFFERS = -5;

	private static RenderPass current;
	private static GpuTexture[] colours;
	private static GpuTexture depth;
	private static int areaX;
	private static int areaY;
	private static int areaW;
	private static int areaH;
	private static boolean opening;

	private static boolean drawing;

	private static Supplier<String> ended;

	private GeometryHold() {
	}

	public static boolean opening() {
		return opening;
	}

	public static RenderPass open(CommandEncoder encoder, RenderPassDescriptor descriptor) {
		int fit = fit(descriptor);
		if (fit == JOINS) {
			resetArea(current);
			drawing = true;

			return current;
		}

		if (PassTimings.censusArmed()) {
			PassTimings.censusReopen(descriptor.label(), name(fit));
		}

		flush(null);
		opening = true;
		try {
			current = encoder.createRenderPass(descriptor);
			remember(descriptor);
			drawing = true;

			return current;
		} finally {
			opening = false;
		}
	}

	public static boolean keep(RenderPass pass) {
		boolean held = pass == current;
		if (held) {
			drawing = false;
		}

		return held;
	}

	public static RenderPass leftover(RenderPassDescriptor descriptor) {

		if (opening || current == null || clears(descriptor) || fit(descriptor) != JOINS
				|| !leftoverLabel(descriptor)) {
			return null;
		}

		resetArea(current);
		drawing = true;

		return current;
	}

	public static void flush(Supplier<String> cause) {
		RenderPass pass = current;

		if (pass != null) {
			ended = cause;
		}

		current = null;
		drawing = false;
		colours = null;
		depth = null;
		if (pass != null) {
			pass.close();
		}
	}

	public static void flushIdle(Supplier<String> cause) {
		if (!drawing) {
			flush(cause);
		}
	}

	private static int fit(RenderPassDescriptor descriptor) {
		if (current == null || colours == null) {
			return NO_HOLD;
		}

		List<RenderPassDescriptor.Attachment<Optional<Vector4fc>>> listed =
				descriptor.colorAttachments();
		if (listed.size() != colours.length) {
			return COUNT_DIFFERS;
		}

		for (int index = 0; index < colours.length; index++) {
			RenderPassDescriptor.Attachment<Optional<Vector4fc>> attachment = listed.get(index);
			GpuTexture texture = texture(attachment);
			if (texture != colours[index]) {
				return index;
			}
		}

		GpuTexture nextDepth = descriptor.depthAttachment() == null
				? null
				: descriptor.depthAttachment().textureView().texture();
		if (nextDepth != depth) {
			return DEPTH_DIFFERS;
		}

		RenderPass.RenderArea area = GraphicsApi.renderArea(descriptor);
		boolean sameArea = area != null && area.x() == areaX && area.y() == areaY
				&& area.width() == areaW && area.height() == areaH;

		return sameArea ? JOINS : AREA_DIFFERS;
	}

	private static String name(int fit) {
		if (fit >= 0) {
			return "its colour image " + fit + " is not the one held";
		}

		return switch (fit) {
			case COUNT_DIFFERS -> "it writes a different number of colour images";
			case DEPTH_DIFFERS -> "its depth image is not the one held";
			case AREA_DIFFERS -> "its area is not the one held";
			case NO_HOLD -> ended == null ? "nothing was being held" : "the hold ended on " + ended.get();
			default -> "it joins";
		};
	}

	private static void remember(RenderPassDescriptor descriptor) {
		List<RenderPassDescriptor.Attachment<Optional<Vector4fc>>> listed =
				descriptor.colorAttachments();
		colours = new GpuTexture[listed.size()];
		for (int index = 0; index < listed.size(); index++) {
			colours[index] = texture(listed.get(index));
		}

		depth = descriptor.depthAttachment() == null
				? null
				: descriptor.depthAttachment().textureView().texture();
		RenderPass.RenderArea area = GraphicsApi.renderArea(descriptor);
		if (area == null) {
			areaX = 0;
			areaY = 0;
			areaW = 0;
			areaH = 0;
		} else {
			areaX = area.x();
			areaY = area.y();
			areaW = area.width();
			areaH = area.height();
		}
	}

	private static GpuTexture texture(
			RenderPassDescriptor.Attachment<Optional<Vector4fc>> attachment) {
		if (attachment == null) {
			return null;
		}

		GpuTextureView view = attachment.textureView();

		return view == null ? null : view.texture();
	}

	private static void resetArea(RenderPass pass) {
		pass.disableScissor();
	}

	private static boolean leftoverLabel(RenderPassDescriptor descriptor) {
		Supplier<String> label = descriptor.label();
		if (label == null) {
			return false;
		}

		String name = label.get();

		return name != null && (name.startsWith("Immediate draw")
				|| name.equals("distantHorizons:GenericObjectRenderer"));
	}

	private static boolean clears(RenderPassDescriptor descriptor) {
		for (RenderPassDescriptor.Attachment<Optional<Vector4fc>> attachment
				: descriptor.colorAttachments()) {
			if (attachment != null && present(attachment.clearValue())) {
				return true;
			}
		}

		RenderPassDescriptor.Attachment<OptionalDouble> depthAttachment =
				descriptor.depthAttachment();

		return depthAttachment != null && present(depthAttachment.clearValue());
	}

	private static boolean present(Object clear) {
		return switch (clear) {
			case Optional<?> colour -> colour.isPresent();
			case OptionalDouble depth -> depth.isPresent();
			case null, default -> false;
		};
	}
}
