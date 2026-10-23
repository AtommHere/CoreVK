package net.corevk.corevk.sodium;

import net.corevk.corevk.glsl.EntityVertex;
import net.corevk.corevk.render.EntityFrame;
import net.corevk.corevk.render.EntityIdentifiers;
import net.corevk.corevk.render.EntityMesh;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.caffeinemc.mods.sodium.api.vertex.serializer.VertexSerializer;
import net.caffeinemc.mods.sodium.api.vertex.serializer.VertexSerializerRegistry;

import org.lwjgl.system.MemoryUtil;

import java.util.Objects;

public final class EntityMeshSerializer implements VertexSerializer {

	private static final int WRITTEN = DefaultVertexFormat.ENTITY.getVertexSize();

	private static final int CARRIED = EntityMesh.format().getVertexSize();

	private static final int IDENTIFIERS = carried(EntityVertex.IDENTIFIERS);

	private static final int MID_TEX_COORD = carried(EntityVertex.MID_TEX_COORD);

	private static final int TANGENT = carried(EntityVertex.TANGENT);

	private static final int POSITION = written("Position");

	private static final int TEX_COORD = written("UV0");

	private static final int NORMAL = written("Normal");

	private EntityMeshSerializer() {
	}

	private static int carried(String element) {
		return Objects.requireNonNull(EntityMesh.format().getElement(element),
				"The entity mesh was built without " + element).offset();
	}

	private static int written(String element) {
		return Objects.requireNonNull(DefaultVertexFormat.ENTITY.getElement(element),
				"The game's entity format has no " + element).offset();
	}

	public static void register() {
		VertexSerializerRegistry.instance().registerSerializer(DefaultVertexFormat.ENTITY,
				EntityMesh.format(), new EntityMeshSerializer());
	}

	@Override
	public void serialize(long written, long carried, int vertices) {
		short entity = (short) EntityIdentifiers.entity();
		short blockEntity = (short) EntityIdentifiers.blockEntity();
		short item = (short) EntityIdentifiers.item();

		int quads = vertices >> 2;
		long from = written;
		long into = carried;
		for (int quad = 0; quad < quads; quad++) {
			long second = from + WRITTEN;
			long third = second + WRITTEN;
			long fourth = third + WRITTEN;

			int normal = MemoryUtil.memGetInt(from + NORMAL);
			float midU = (MemoryUtil.memGetFloat(from + TEX_COORD)
					+ MemoryUtil.memGetFloat(second + TEX_COORD)
					+ MemoryUtil.memGetFloat(third + TEX_COORD)
					+ MemoryUtil.memGetFloat(fourth + TEX_COORD)) * 0.25F;
			float midV = (MemoryUtil.memGetFloat(from + TEX_COORD + 4L)
					+ MemoryUtil.memGetFloat(second + TEX_COORD + 4L)
					+ MemoryUtil.memGetFloat(third + TEX_COORD + 4L)
					+ MemoryUtil.memGetFloat(fourth + TEX_COORD + 4L)) * 0.25F;
			int tangent = EntityFrame.tangent(EntityFrame.unpack(normal, 0),
					EntityFrame.unpack(normal, 1), EntityFrame.unpack(normal, 2), false,
					MemoryUtil.memGetFloat(from + POSITION),
					MemoryUtil.memGetFloat(from + POSITION + 4L),
					MemoryUtil.memGetFloat(from + POSITION + 8L),
					MemoryUtil.memGetFloat(from + TEX_COORD),
					MemoryUtil.memGetFloat(from + TEX_COORD + 4L),
					MemoryUtil.memGetFloat(second + POSITION),
					MemoryUtil.memGetFloat(second + POSITION + 4L),
					MemoryUtil.memGetFloat(second + POSITION + 8L),
					MemoryUtil.memGetFloat(second + TEX_COORD),
					MemoryUtil.memGetFloat(second + TEX_COORD + 4L),
					MemoryUtil.memGetFloat(third + POSITION),
					MemoryUtil.memGetFloat(third + POSITION + 4L),
					MemoryUtil.memGetFloat(third + POSITION + 8L),
					MemoryUtil.memGetFloat(third + TEX_COORD),
					MemoryUtil.memGetFloat(third + TEX_COORD + 4L));

			for (int corner = 0; corner < 4; corner++) {
				copy(from, into, entity, blockEntity, item);
				MemoryUtil.memPutFloat(into + MID_TEX_COORD, midU);
				MemoryUtil.memPutFloat(into + MID_TEX_COORD + 4L, midV);
				MemoryUtil.memPutInt(into + TANGENT, tangent);
				from += WRITTEN;
				into += CARRIED;
			}
		}

		for (int vertex = quads << 2; vertex < vertices; vertex++) {
			copy(from, into, entity, blockEntity, item);
			MemoryUtil.memPutFloat(into + MID_TEX_COORD, 0.0F);
			MemoryUtil.memPutFloat(into + MID_TEX_COORD + 4L, 0.0F);
			MemoryUtil.memPutInt(into + TANGENT, EntityFrame.FLAT);
			from += WRITTEN;
			into += CARRIED;
		}
	}

	private static void copy(long from, long into, short entity, short blockEntity, short item) {
		MemoryUtil.memCopy(from, into, WRITTEN);
		MemoryUtil.memPutShort(into + IDENTIFIERS, entity);
		MemoryUtil.memPutShort(into + IDENTIFIERS + 2L, blockEntity);
		MemoryUtil.memPutShort(into + IDENTIFIERS + 4L, item);
		MemoryUtil.memPutShort(into + IDENTIFIERS + 6L, (short) 0);
	}
}
