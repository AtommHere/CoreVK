package net.corevk.corevk.mixin;

import net.corevk.corevk.glsl.EntityVertex;
import net.corevk.corevk.glsl.MovingBlockVertex;
import net.corevk.corevk.mixin.access.ByteBufferBuilderAccessor;
import net.corevk.corevk.render.EntityFrame;
import net.corevk.corevk.render.EntityIdentifiers;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import com.mojang.blaze3d.PrimitiveTopology;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BufferBuilder.class)
public abstract class BufferBuilderMixin {

	@Shadow
	@Final
	private VertexFormat format;

	@Shadow
	@Final
	private ByteBufferBuilder buffer;

	@Unique
	private int corevk$midTexCoord;

	@Unique
	private int corevk$identifiers;

	@Unique
	private int corevk$midBlock;

	@Unique
	private int corevk$tangent;

	@Unique
	private int corevk$position;

	@Unique
	private int corevk$texCoord;

	@Unique
	private int corevk$normal;

	@Unique
	private int corevk$corners;

	@Unique
	private long @Nullable [] corevk$corner;

	@Unique
	private int corevk$written;

	@Unique
	private float @Nullable [] corevk$read;

	@Unique
	private float @Nullable [] corevk$face;

	@Inject(method = "<init>", at = @At("RETURN"), require = 1)
	private void corevk$findElements(ByteBufferBuilder buffer, PrimitiveTopology topology,
			VertexFormat format, CallbackInfo callback) {
		this.corevk$midTexCoord = -1;

		int midTexCoord = corevk$offsetOf(EntityVertex.MID_TEX_COORD);
		if (midTexCoord < 0) {
			return;
		}

		int tangent = corevk$offsetOf(EntityVertex.TANGENT);
		int position = corevk$offsetOf("Position");
		int texCoord = corevk$offsetOf("UV0");
		int normal = corevk$offsetOf("Normal");

		if (tangent < 0 || position < 0 || texCoord < 0 || normal < 0) {
			return;
		}

		this.corevk$identifiers = corevk$offsetOf(EntityVertex.IDENTIFIERS);
		this.corevk$midBlock = corevk$offsetOf(MovingBlockVertex.MID_BLOCK);
		this.corevk$midTexCoord = midTexCoord;
		this.corevk$tangent = tangent;
		this.corevk$position = position;
		this.corevk$texCoord = texCoord;
		this.corevk$normal = normal;
		this.corevk$corners = switch (topology) {
			case QUADS -> 4;
			case TRIANGLES -> 3;
			default -> 0;
		};
		this.corevk$corner = new long[4];
		this.corevk$read = new float[20];
		this.corevk$face = new float[3];
	}

	@Inject(method = "beginVertex", at = @At("RETURN"), require = 1)
	private void corevk$writeVertex(CallbackInfoReturnable<Long> callback) {
		if (this.corevk$midTexCoord < 0) {
			return;
		}

		corevk$fill();

		long pointer = callback.getReturnValueJ();
		if (this.corevk$identifiers >= 0) {
			long identifiers = pointer + this.corevk$identifiers;
			MemoryUtil.memPutShort(identifiers, (short) EntityIdentifiers.entity());
			MemoryUtil.memPutShort(identifiers + 2L, (short) EntityIdentifiers.blockEntity());
			MemoryUtil.memPutShort(identifiers + 4L, (short) EntityIdentifiers.item());
			MemoryUtil.memPutShort(identifiers + 6L, (short) 0);
		}

		if (this.corevk$midBlock >= 0) {
			MemoryUtil.memPutInt(pointer + this.corevk$midBlock, 0);
		}

		MemoryUtil.memPutFloat(pointer + this.corevk$midTexCoord, 0.0F);
		MemoryUtil.memPutFloat(pointer + this.corevk$midTexCoord + 4L, 0.0F);
		MemoryUtil.memPutInt(pointer + this.corevk$tangent, EntityFrame.FLAT);

		long[] corner = this.corevk$corner;
		if (corner != null && this.corevk$corners > 0) {
			corner[this.corevk$written++] =
					pointer - ((ByteBufferBuilderAccessor) this.buffer).corevk$pointer();
		}
	}

	@Inject(method = "build", at = @At("HEAD"), require = 1)
	private void corevk$finishPolygon(CallbackInfoReturnable<MeshData> callback) {
		if (this.corevk$midTexCoord >= 0) {
			corevk$fill();
		}
	}

	@Unique
	private void corevk$fill() {
		long[] corner = this.corevk$corner;
		float[] read = this.corevk$read;
		float[] face = this.corevk$face;
		int corners = this.corevk$corners;
		if (corner == null || read == null || face == null || corners == 0
				|| this.corevk$written < corners) {
			return;
		}

		this.corevk$written = 0;
		long base = ((ByteBufferBuilderAccessor) this.buffer).corevk$pointer();
		float midU = 0.0F;
		float midV = 0.0F;
		for (int at = 0; at < corners; at++) {
			long vertex = base + corner[at];
			int into = at * 5;
			read[into] = MemoryUtil.memGetFloat(vertex + this.corevk$position);
			read[into + 1] = MemoryUtil.memGetFloat(vertex + this.corevk$position + 4L);
			read[into + 2] = MemoryUtil.memGetFloat(vertex + this.corevk$position + 8L);
			read[into + 3] = MemoryUtil.memGetFloat(vertex + this.corevk$texCoord);
			read[into + 4] = MemoryUtil.memGetFloat(vertex + this.corevk$texCoord + 4L);
			midU += read[into + 3];
			midV += read[into + 4];
		}

		midU /= corners;
		midV /= corners;

		if (this.corevk$midBlock >= 0) {
			for (int at = 0; at < corners; at++) {
				MemoryUtil.memPutInt(base + corner[at] + this.corevk$midBlock,
						corevk$midBlock(read[at * 5], read[at * 5 + 1], read[at * 5 + 2]));
			}
		}

		if (corners == 4) {
			corevk$fillQuad(base, corner, read, face, midU, midV);

			return;
		}

		for (int at = 0; at < corners; at++) {
			long vertex = base + corner[at];
			int normal = MemoryUtil.memGetInt(vertex + this.corevk$normal);
			corevk$write(vertex, midU, midV, EntityFrame.tangent(EntityFrame.unpack(normal, 0),
					EntityFrame.unpack(normal, 1), EntityFrame.unpack(normal, 2), true,
					read[0], read[1], read[2], read[3], read[4],
					read[5], read[6], read[7], read[8], read[9],
					read[10], read[11], read[12], read[13], read[14]));
		}
	}

	@Unique
	private void corevk$fillQuad(long base, long[] corner, float[] read, float[] face, float midU,
			float midV) {
		if (!EntityFrame.faceNormal(face, read[0], read[1], read[2], read[5], read[6], read[7],
				read[10], read[11], read[12], read[15], read[16], read[17])) {
			int given = MemoryUtil.memGetInt(base + corner[0] + this.corevk$normal);
			face[0] = EntityFrame.unpack(given, 0);
			face[1] = EntityFrame.unpack(given, 1);
			face[2] = EntityFrame.unpack(given, 2);
		} else if (this.corevk$midBlock >= 0) {
			int normal = EntityFrame.pack(face[0], face[1], face[2], 0.0F);
			for (int at = 0; at < 4; at++) {
				MemoryUtil.memPutInt(base + corner[at] + this.corevk$normal, normal);
			}
		}

		int tangent = EntityFrame.tangent(face[0], face[1], face[2], false,
				read[0], read[1], read[2], read[3], read[4],
				read[5], read[6], read[7], read[8], read[9],
				read[10], read[11], read[12], read[13], read[14]);
		for (int at = 0; at < 4; at++) {
			corevk$write(base + corner[at], midU, midV, tangent);
		}
	}

	@Unique
	private void corevk$write(long vertex, float midU, float midV, int tangent) {
		MemoryUtil.memPutFloat(vertex + this.corevk$midTexCoord, midU);
		MemoryUtil.memPutFloat(vertex + this.corevk$midTexCoord + 4L, midV);
		MemoryUtil.memPutInt(vertex + this.corevk$tangent, tangent);
	}

	@Unique
	private static int corevk$midBlock(float x, float y, float z) {
		return ((int) ((0.5F - x) * 64.0F) & 0xFF) | (((int) ((0.5F - y) * 64.0F) & 0xFF) << 8)
				| (((int) ((0.5F - z) * 64.0F) & 0xFF) << 16) | (0xFF << 24);
	}

	@Unique
	private int corevk$offsetOf(String element) {
		VertexFormatElement found = this.format.getElement(element);

		return found == null ? -1 : found.offset();
	}
}
