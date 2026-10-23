package net.corevk.corevk.render;

import net.corevk.corevk.glsl.EntityVertex;
import net.corevk.corevk.glsl.MovingBlockVertex;
import net.corevk.corevk.CoreVK;

import com.mojang.blaze3d.GpuDeviceLossException;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;

import java.util.List;

import org.jspecify.annotations.Nullable;

public final class EntityMesh {

	private static final VertexFormat FORMAT = extend();

	private static final VertexFormat MOVING_BLOCK_FORMAT = extendMovingBlock();

	private static boolean carrying;

	private static boolean said;

	private static boolean widerRefused;

	private static boolean rebuildAsked;

	private EntityMesh() {
	}

	public static VertexFormat format() {
		return FORMAT;
	}

	public static VertexFormat movingBlockFormat() {
		return MOVING_BLOCK_FORMAT;
	}

	public static boolean carrying() {
		return carrying;
	}

	public static @Nullable VertexFormat binding(RenderPipeline pipeline,
			@Nullable VertexFormat declared) {
		if (!carrying) {
			return declared;
		}

		@SuppressWarnings("ReferenceEquality")
		boolean entity = declared == DefaultVertexFormat.ENTITY;
		if (entity) {
			return FORMAT;
		}

		@SuppressWarnings("ReferenceEquality")
		boolean block = declared == DefaultVertexFormat.BLOCK;

		return block && movingBlock(pipeline) ? MOVING_BLOCK_FORMAT : declared;
	}

	@SuppressWarnings("ReferenceEquality")
	public static boolean movingBlock(RenderPipeline pipeline) {
		return pipeline == RenderPipelines.SOLID_BLOCK || pipeline == RenderPipelines.CUTOUT_BLOCK
				|| pipeline == RenderPipelines.TRANSLUCENT_BLOCK;
	}

	private static boolean asked() {
		return !widerRefused && (EntityDraw.wanted() || HandDraw.wanted());
	}

	static void ask() {
		boolean asked = asked();
		if (asked == carrying) {

			rebuildAsked = false;

			return;
		}

		Minecraft minecraft = Minecraft.getInstance();
		if (rebuildAsked || minecraft == null || minecraft.level == null) {
			return;
		}

		CoreVK.logger().info("The pack draws the entities or the hand {} now, and the entity mesh "
				+ "only carries these while it does, so the world is built again: {}",
				asked ? "and did not" : "and did", EntityVertex.APPENDED);
		minecraft.levelExtractor.allChanged();
		rebuildAsked = true;
	}

	public static void settle() {
		boolean asked = asked();
		boolean moved = asked != carrying;

		rebuildAsked = false;
		if (said && !moved) {
			return;
		}

		said = true;
		carrying = asked;

		if (moved) {
			GpuDevice device = RenderSystem.getDevice();
			List<RenderPipeline> dropped = GraphicsApi.dropEntityPipelines(device);
			if (dropped != null) {
				try {
					for (RenderPipeline pipeline : dropped) {
						GraphicsApi.compile(device, pipeline, null);
					}
				} catch (GpuDeviceLossException e) {
					throw e;
				} catch (RuntimeException e) {

					if (!asked) {
						throw e;
					}

					widerRefused = true;
					carrying = false;
					GraphicsApi.dropEntityPipelines(device);
					HandDraw.stopped();
					EntityDraw.wanted(false);
					CoreVK.logger().error("An entity pipeline of the game did not compile at the "
							+ "wider entity format, so no pack draws the entities or the hand for the "
							+ "rest of this session", e);

					return;
				}

				CoreVK.logger().info("{} entity pipelines of the game carried the previous "
						+ "stride: compiled again at the one now in force, the old ones set "
						+ "aside for the next safe purge", dropped.size());
			} else {
				CoreVK.logger().info("The game's entity pipelines were compiled under the "
						+ "previous answer and stand until the next resource reload; this "
						+ "backend takes no eviction, and what the stand costs it is unmeasured");
			}
		}

		if (!asked) {
			CoreVK.logger().info("Neither the entities nor the hand are drawn by the pack, so the "
					+ "entity mesh keeps the game's own format and carries none of {}",
					EntityVertex.APPENDED);

			return;
		}

		CoreVK.logger().info("The entity mesh carries {} bytes a vertex instead of {}, the "
				+ "difference being the three identifiers a pack tells one entity, block entity or "
				+ "held item apart by, then the middle of the sprite a polygon is mapped to and the "
				+ "tangent of that mapping: {}",
				FORMAT.getVertexSize(), DefaultVertexFormat.ENTITY.getVertexSize(),
				EntityVertex.APPENDED);
		CoreVK.logger().info("A moving block's mesh carries {} bytes a vertex instead of {}, the "
				+ "difference being the terrain elements a pack reads on a block: {}",
				MOVING_BLOCK_FORMAT.getVertexSize(), DefaultVertexFormat.BLOCK.getVertexSize(),
				MovingBlockVertex.APPENDED);
	}

	private static VertexFormat extend() {
		VertexFormat.Builder builder = VertexFormat.builder(DefaultVertexFormat.ENTITY.getStepRate());
		for (VertexFormatElement element : DefaultVertexFormat.ENTITY.getElements()) {
			builder.addAttribute(element.name(), element.format());
		}

		return builder.addAttribute(EntityVertex.IDENTIFIERS, GpuFormat.RGBA16_UINT)
				.addAttribute(EntityVertex.MID_TEX_COORD, GpuFormat.RG32_FLOAT)
				.addAttribute(EntityVertex.TANGENT, GpuFormat.RGBA8_SNORM)
				.build();
	}

	private static VertexFormat extendMovingBlock() {
		VertexFormat.Builder builder = VertexFormat.builder(DefaultVertexFormat.BLOCK.getStepRate());
		for (VertexFormatElement element : DefaultVertexFormat.BLOCK.getElements()) {
			builder.addAttribute(element.name(), element.format());
		}

		return builder.addAttribute(MovingBlockVertex.NORMAL, GpuFormat.RGBA8_SNORM)
				.addAttribute(EntityVertex.MID_TEX_COORD, GpuFormat.RG32_FLOAT)
				.addAttribute(EntityVertex.TANGENT, GpuFormat.RGBA8_SNORM)
				.addAttribute(MovingBlockVertex.MID_BLOCK, GpuFormat.RGBA8_SNORM)
				.build();
	}
}
