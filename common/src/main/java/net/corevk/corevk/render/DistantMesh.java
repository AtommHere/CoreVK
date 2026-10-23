package net.corevk.corevk.render;

import net.corevk.corevk.glsl.DistantVertex;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import java.util.List;
import java.util.Map;

public final class DistantMesh {

	private static final Map<String, Integer> OFFSETS = Map.of(
			DistantVertex.POSITION, 0,
			DistantVertex.META, 6,
			DistantVertex.COLOUR, 8,
			DistantVertex.MATERIAL, 12,
			DistantVertex.NORMAL, 13);

	private static final Map<String, GpuFormat> FORMATS = Map.of(
			DistantVertex.POSITION, GpuFormat.RGB16_UINT,
			DistantVertex.META, GpuFormat.R16_UINT,
			DistantVertex.COLOUR, GpuFormat.RGBA8_UNORM,
			DistantVertex.MATERIAL, GpuFormat.R8_UINT,
			DistantVertex.NORMAL, GpuFormat.R8_UINT);

	public static final int STRIDE = 16;

	private DistantMesh() {
	}

	static VertexFormat format(List<String> carried) {

		VertexFormat.Builder builder = VertexFormat.builder(0);
		for (int index = 0; index < carried.size(); index++) {
			String element = carried.get(index);
			int at = OFFSETS.get(element);

			int next = index + 1 < carried.size() ? OFFSETS.get(carried.get(index + 1)) : STRIDE;
			builder.addAttribute(element, next - at, FORMATS.get(element));
		}

		return builder.build();
	}
}
