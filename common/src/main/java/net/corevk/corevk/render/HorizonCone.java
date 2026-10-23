package net.corevk.corevk.render;

import net.corevk.corevk.CoreVK;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;

final class HorizonCone {

	private static final float TOP = 16.0F;

	private static final float BOTTOM = -16.0F;

	private static final int SIDES = 8;

	private static final int VERTICES = SIDES + 2;

	private static final int MAX_RADIUS = 256;

	private static final String LABEL = "CoreVK horizon cone";

	private GpuBuffer buffer;

	private int radius;

	private boolean drew;

	private boolean refused;

	void update(GpuDevice device) {
		int wanted = radius();
		if (this.buffer != null && this.radius == wanted) {
			return;
		}

		release();
		this.radius = wanted;

		int bytes = VERTICES * DefaultVertexFormat.POSITION.getVertexSize();
		try (ByteBufferBuilder sink = ByteBufferBuilder.exactlySized(bytes)) {
			BufferBuilder builder =
					new BufferBuilder(sink, PrimitiveTopology.TRIANGLE_FAN, DefaultVertexFormat.POSITION);
			build(builder, wanted);

			try (MeshData mesh = builder.buildOrThrow()) {
				this.buffer = device.createBuffer(() -> LABEL, GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
			}
		}
	}

	void draw(RenderPass pass, String program, TerrainDraw.Mask world) {
		if (this.buffer == null || world != TerrainDraw.Mask.WRITTEN) {

			if (world == TerrainDraw.Mask.ABSENT && !this.refused) {
				this.refused = true;
				CoreVK.logger().info("The horizon cone is not drawn: the world's opaque geometry "
						+ "does not mark the pixels it wrote, so its picture reaches the pack's "
						+ "target through the scene seed, and a cone would cut it out of the seed");
			}

			return;
		}

		pass.setVertexBuffer(0, this.buffer.slice());
		pass.draw(VERTICES, 1, 0, 0);

		if (!this.drew) {
			this.drew = true;
			CoreVK.logger().info("The horizon cone is drawn with {}, {} walls out to {} blocks "
					+ "between y {} and y {}, which is the band the game's own sky leaves bare",
					program, SIDES, this.radius, BOTTOM, TOP);
		}
	}

	void release() {
		if (this.buffer != null) {
			this.buffer.close();
			this.buffer = null;
		}

		this.radius = 0;
		this.drew = false;
		this.refused = false;
	}

	private static int radius() {
		Minecraft minecraft = Minecraft.getInstance();
		int blocks = minecraft == null ? MAX_RADIUS : minecraft.options.getEffectiveRenderDistance() * 16;

		return Math.min(blocks, MAX_RADIUS);
	}

	private static void build(VertexConsumer consumer, int radius) {
		consumer.addVertex(0.0F, BOTTOM, 0.0F);

		for (int i = 0; i <= SIDES; i++) {
			double angle = -i * Math.PI * 2.0 / SIDES;
			consumer.addVertex((float) (radius * Math.cos(angle)), TOP,
					(float) (radius * Math.sin(angle)));
		}
	}
}
