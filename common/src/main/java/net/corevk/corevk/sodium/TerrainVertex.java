package net.corevk.corevk.sodium;

import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;

public interface TerrainVertex {

	int corevkBlockId();

	void corevkBlockId(int id);

	int corevkBlockOrigin();

	void corevkBlockOrigin(int packed);

	static ChunkVertexEncoder.Vertex[] stamp(ChunkVertexEncoder.Vertex[] vertices, int id) {
		for (ChunkVertexEncoder.Vertex vertex : vertices) {
			((TerrainVertex) vertex).corevkBlockId(id);
		}

		return vertices;
	}

	static ChunkVertexEncoder.Vertex[] stampOrigin(ChunkVertexEncoder.Vertex[] vertices, int packed) {
		for (ChunkVertexEncoder.Vertex vertex : vertices) {
			((TerrainVertex) vertex).corevkBlockOrigin(packed);
		}

		return vertices;
	}

	static int pack(int x, int y, int z, int emission) {
		return (x & 15) | ((y & 15) << 8) | ((z & 15) << 16) | ((emission & 0xFF) << 24);
	}

	static int origin(int packed, int axis) {
		return (packed >> (axis * 8)) & 0xFF;
	}

	static int emission(int packed) {
		return (packed >>> 24) & 0xFF;
	}
}
