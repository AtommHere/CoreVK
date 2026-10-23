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
			// Back in agreement, so whatever was asked for is owed no longer and the next real
			// disagreement gets its own line. This is the road a load takes when it turns one switch
			// off and the other leaves the answer where it was.
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
		// Before the way out and not after it: this runs whether or not the answer moved, and a settle
		// that found nothing to do is exactly the one that would leave the flag standing.
		rebuildAsked = false;
		if (said && !moved) {
			return;
		}

		said = true;
		carrying = asked;

		// ONLY where the answer really moved, and the two conditions are not one. The first settle of
		// a session has never said anything and must print, but a session nobody picked a pack in
		// settles false onto false and has nothing to drop: dropping anyway is a full recompile of
		// every static pipeline at the first world join, for a stride that did not change.
		//
		// Where it did move, a drop is owed: the game precompiles every static pipeline at every
		// resource load and caches it by identity (ShaderManager.apply, VulkanDevice.pipelineCache),
		// this backend bakes the stride in at compile, so the entity ones standing here carry the
		// other answer's stride. Left standing they read the mesh at the wrong offsets from now on,
		// which on screen is the hand and every fallback entity as triangles stretched across the
		// picture, and it does not heal: the cache is only emptied at a resource reload, so the
		// defect holds for the rest of the session. Measured before it was believed: hand=off on a
		// stock bench, one world join, and the artefacts stood until a pack toggle forced the
		// reload.
		//
		// AND NOTHING IS DESTROYED HERE ALL THE SAME, which was this engine's half of issue 111.
		// Destruction waits the device idle, and there is no instant of a running session where
		// that is safe: this backend records continuously, so at any positional hook something
		// already recorded still names what a purge frees. Freed here, on a pack load in a running
		// world, the device was lost seconds later with nothing in the log; moved to the head of
		// the frame, it took the boot's world join down instead. So the two halves of eviction are
		// split along what each one can bear. Leaving the map is map work, safe anywhere, and
		// enough on its own: what no lookup can answer with, no NEW draw binds. The compiled
		// pipelines wait in the mixin for clearPipelineCache, the game's one safe road, which
		// quiesces first and now frees them with the rest. And the recompile happens at once
		// rather than lazily at the next bind. The null asks the backend's default source, and it
		// is never read: a source is only consulted on a miss of the device's shader module cache
		// (VulkanDevice.getOrCompileShader, keyed by id, type and defines), the drop leaves that
		// cache standing, and the moved answer changes none of the three keys. The recompile
		// therefore reuses the very modules the last resource load compiled, core shaders a
		// resource pack replaced included, and only the pipeline around them takes the new stride.
		//
		// The GL backend is left exactly as it stood, debt included, and the old line still says
		// the answer moved there. Whether GL owes the same eviction is UNPROVEN in both
		// directions: its encoder rebuilds the vertex array from the live getter at every draw
		// (GlCommandEncoder), which reads as immune, but GlProgram.link binds attribute locations
		// once from the format it is handed, which reads as the same bake by another name. Nobody
		// has drawn the artefact there, and an eviction shipped unmeasured would be this fix's own
		// plausible-and-wrong.
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
					// A pipeline the driver refuses throws out of precompilePipeline rather than coming
					// back invalid, and this runs inside Sodium's renderer setup, outside every catch
					// of the frame. Going back to the game's own format is the game's own compile, and
					// a refusal there is not this engine's to answer.
					if (!asked) {
						throw e;
					}

					// Going to the wider format, what was refused is the format, so the mesh stops
					// carrying it. Whatever this loop already compiled at the wider format leaves the
					// map again, so every entity pipeline of the game compiles at its own format at its
					// next bind, and the pack's entities and hand stand down for the session, their
					// programs reading a mesh that no longer carries. The order is what
					// asks for no rebuild: the answer goes back first and the hand is taken down without
					// asking, so the entity switch is the one question left and it finds agreement.
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
