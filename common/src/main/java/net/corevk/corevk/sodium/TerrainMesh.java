package net.corevk.corevk.sodium;

import net.corevk.corevk.glsl.SodiumVertex;
import net.corevk.corevk.glsl.TangentFrame;
import net.corevk.corevk.render.BlockStateIds;
import net.corevk.corevk.render.TerrainDraw;
import net.corevk.corevk.CoreVK;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.caffeinemc.mods.sodium.api.util.ColorABGR;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkMeshFormats;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;

import org.lwjgl.system.MemoryUtil;

import java.util.Arrays;
import java.util.List;

public final class TerrainMesh implements ChunkVertexType {

	
	private static TerrainMesh built;

	
	private static boolean broken;

	
	private static List<String> said;

	
	private static final float TEXTURE_SCALE = 32768.0F;

	
	private static final float UNREVERSED_AREA = -1.0F;

	
	private static final float FLATTENED = 1.0E-20F;

	
	private static final ThreadLocal<float[]> FRAMES = ThreadLocal.withInitial(() -> new float[7]);

	private final ChunkVertexType inner = ChunkMeshFormats.COMPACT;
	private final ChunkVertexEncoder innerEncoder = this.inner.getEncoder();
	private final ChunkVertexEncoder encoder = this::encode;
	private final int innerStride = this.inner.getVertexFormat().getVertexSize();

	
	private final List<String> carried;

	
	private final List<Extra> extras;

	
	private final int[] offsets;

	private final VertexFormat format;
	private final int stride;

	
	private static final int ABSENT = -1;

	private TerrainMesh(List<String> carried) {
		if (this.innerStride % Integer.BYTES != 0) {
			throw new IllegalStateException("The chunk mesh is " + this.innerStride + " bytes, which "
					+ "is not a whole number of words, and this engine moves it a word at a time");
		}

		// What holds the layout and the encoder together. Both take their offsets from the table
		// below, which spends one word per element, and the encoder writes each of them with a
		// single memPutInt. An element of any other width would put the two on different bytes
		// without either of them saying so, and the pack would read the neighbour's.
		for (Extra extra : Extra.values()) {
			if (extra.format().blockSize() != Integer.BYTES) {
				throw new IllegalStateException(extra.attribute() + " takes "
						+ extra.format().blockSize() + " bytes, and this engine lays out and writes "
						+ "one word for each of the elements it appends");
			}
		}

		this.carried = List.copyOf(carried);
		this.extras = Arrays.stream(Extra.values())
				.filter(extra -> this.carried.contains(extra.attribute()))
				.toList();
		this.offsets = new int[Extra.values().length];
		Arrays.fill(this.offsets, ABSENT);
		for (int at = 0; at < this.extras.size(); at++) {
			this.offsets[this.extras.get(at).ordinal()] = at * Integer.BYTES;
		}

		this.format = extend(this.inner.getVertexFormat(), this.extras);
		this.stride = this.format.getVertexSize();
	}

	
	public static synchronized ChunkVertexType current() {
		return built;
	}

	
	public static synchronized void settle() {
		// What the pack published, kept apart from what is decided out of it: a list that arrives
		// empty is a pack with nothing to say, and one emptied below is a pack that asked for none of
		// ours. The two decide the same layout and are not the same event, and the log parts them.
		List<String> asked = broken ? List.of() : TerrainDraw.carried();
		List<String> carried = asked;
		// Sodium's own four and nothing of ours is the same layout Sodium already binds, so it is
		// answered with Sodium's own rather than with a copy of it. No pack of the corpus is here,
		// every one of them reading at least the block id, but a pack that read none of the five
		// would be, and wrapping a format to change nothing about it is one more thing to be wrong.
		if (carried.stream().noneMatch(TerrainMesh::ours)) {
			carried = List.of();
		}

		if (!carried.isEmpty() && (built == null || !built.carried.equals(carried))) {
			try {
				built = new TerrainMesh(carried);
			} catch (RuntimeException e) {
				broken = true;
				carried = List.of();
				CoreVK.logger().error("This engine cannot extend the chunk mesh, so the terrain keeps "
						+ "Sodium's own and no pack will draw it", e);
			}
		}

		if (carried.isEmpty()) {
			built = null;
		}

		if (asked.equals(said)) {
			return;
		}

		said = asked;
		if (carried.isEmpty()) {
			if (broken) {
				// Nothing, and before the roads below rather than among them. The error above has
				// named the one cause this road has, and every line below it would be a guess at a
				// question already answered: the pack is still wanted and still published its
				// elements, so both of them would read as true and neither would be.
				return;
			}

			List<String> ours = Arrays.stream(Extra.values()).map(Extra::attribute).toList();
			if (!asked.isEmpty()) {
				// The pack was read and wants none of the five, so Sodium's own layout is what its
				// programs were translated against and there is nothing to append. A normal event
				// and not a failure, which is why it is said at this level; it is said at all
				// because it decides the stride of every vertex in the world and would otherwise be
				// indistinguishable in the log from the pack below that decides the same stride.
				CoreVK.logger().info("This pack's chunk programs read none of {}, so nothing is "
						+ "appended and they draw from the format Sodium gave the mesh", ours);

				return;
			}

			if (TerrainDraw.asked()) {
				// THREE roads share this one and nothing here can part them, so all three are named
				// rather than the likeliest one guessed at. The commonest by far is a load that
				// never reached the pack's chunk programs at all: PackChain raises this flag off the
				// options and only opens the pack afterwards, so a required flag it does not serve,
				// a chain with no final, a refusal and an archive that will not open all leave the
				// flag standing with nothing behind it. Then a pack that was read and serves no
				// chunk program, where the world keeps the game's own shader and the pack's sky and
				// chain go on being drawn. Then this having run before TerrainDraw.read published
				// anything, which is the order this method depends on and cannot check.
				//
				// Said at this level and not louder because the first road is the one that arrives,
				// and every way into it has already said what went wrong in its own words. What this
				// adds is the consequence for the mesh, which none of them mentions.
				CoreVK.logger().info("The pack's own terrain program is wanted and nothing has been "
						+ "published for it, so the mesh keeps the format Sodium gave it and carries "
						+ "none of {}. Either this pack's load never reached its chunk programs, or "
						+ "it serves none of its own, or they had not been read when the chunk "
						+ "renderer was built", ours);

				return;
			}

			// Said out loud, this being the branch that would otherwise be silent. Without naming a
			// cause, because there are three and this cannot tell them apart: a terrain= line, no
			// pack chosen yet, which is every first launch of a fresh instance, and a terrain
			// program that threw.
			CoreVK.logger().info("The pack's own terrain program is not wanted, so the mesh keeps the "
					+ "format Sodium gave it and carries none of {}", ours);

			return;
		}

		CoreVK.logger().info("The chunk mesh carries {} bytes a vertex instead of {}, the difference "
				+ "being what this pack reads and Sodium does not carry: {}",
				built.stride, built.innerStride,
				built.extras.stream().map(Extra::attribute).toList());
	}

	
	private static boolean ours(String attribute) {
		return Arrays.stream(Extra.values()).anyMatch(extra -> extra.attribute().equals(attribute));
	}

	@Override
	public VertexFormat getVertexFormat() {
		return this.format;
	}

	@Override
	public ChunkVertexEncoder getEncoder() {
		return this.encoder;
	}

	
	private static VertexFormat extend(VertexFormat base, List<Extra> extras) {
		VertexFormat.Builder builder = VertexFormat.builder(base.getStepRate());
		for (VertexFormatElement element : base.getElements()) {
			builder.addAttribute(element.name(), element.offset(), element.format().blockSize(),
					element.format(), 1);
		}

		int at = base.getVertexSize();
		for (Extra extra : extras) {
			builder.addAttribute(extra.attribute(), at, extra.format().blockSize(), extra.format(), 1);
			at += Integer.BYTES;
		}

		return builder.build();
	}

	
	private int offset(Extra extra) {
		return this.offsets[extra.ordinal()];
	}

	
	private enum Extra {

		
		BLOCK_ID(SodiumVertex.BLOCK_ID, GpuFormat.R32_UINT),

		
		MID_TEX_COORD(SodiumVertex.MID_TEX_COORD, GpuFormat.RG16_UINT),

		
		MID_BLOCK(SodiumVertex.MID_BLOCK, GpuFormat.RGBA8_SINT),

		
		TANGENT_FRAME(SodiumVertex.TANGENT_FRAME, GpuFormat.R32_UINT),

		
		TINT_AND_AO(SodiumVertex.TINT_AND_AO, GpuFormat.RGBA8_UNORM);

		private final String attribute;
		private final GpuFormat format;

		Extra(String attribute, GpuFormat format) {
			this.attribute = attribute;
			this.format = format;
		}

		String attribute() {
			return this.attribute;
		}

		GpuFormat format() {
			return this.format;
		}
	}

	
	private long encode(long pointer, int materialBits, ChunkVertexEncoder.Vertex[] vertices,
			int sectionIndex) {
		this.innerEncoder.write(pointer, materialBits, vertices, sectionIndex);

		// One value for the whole quad, taken before anything moves: it is the middle of the sprite
		// and not a property of a corner, so the four vertices carry the same number. CoreVK does the
		// same and packs it the same way, which is what lets a pack divide it by the number it
		// already divides its own texture coordinate by.
		//
		// Guarded like the frame below it, and for a plainer reason than the frame's: an element the
		// mesh does not carry has nowhere to be written, so working it out would be arithmetic on
		// every quad of the world for a word that is then thrown away.
		int middle = offset(Extra.MID_TEX_COORD) == ABSENT ? 0 : midTexCoord(vertices);

		// A property of the QUAD and not of a corner, like the middle above: the four vertices carry
		// the same word, and a pack that reads a normal per vertex on chunk geometry is reading what
		// the face is, not what the corner is. The normal and the tangent share it, so a pack reading
		// one of the two pays for both and a pack reading neither pays for neither.
		int frame = offset(Extra.TANGENT_FRAME) == ABSENT ? 0 : TangentFrame.pack(frame(vertices));

		// One of the two that are a property of the CORNER, so it is asked for inside the loop and
		// only the question is hoisted out of it.
		boolean blockMiddle = offset(Extra.MID_BLOCK) != ABSENT;

		// Backwards over the vertices, because a vertex moves up by the difference of the two strides
		// times its own index, and one that has not been moved yet is the source of the move before
		// it: at twenty bytes of difference the second vertex lands on [40, 60) and the third is
		// still to be read from [40, 60). Word by word from the top of each vertex costs nothing and
		// is what keeps the move right at any pair of strides, which is what this now needs: the
		// difference is four bytes for a pack that reads one of the five and twenty for one that
		// reads them all, and at four the two ranges of one vertex DO overlap.
		for (int at = vertices.length - 1; at >= 0; at--) {
			long from = pointer + (long) at * this.innerStride;
			long to = pointer + (long) at * this.stride;
			// The first vertex does not move, both strides placing it at the pointer.
			for (int word = at == 0 ? -1 : this.innerStride - Integer.BYTES; word >= 0;
					word -= Integer.BYTES) {
				MemoryUtil.memPutInt(to + word, MemoryUtil.memGetInt(from + word));
			}

			long extra = to + this.innerStride;
			write(extra, Extra.BLOCK_ID,
					((TerrainVertex) vertices[at]).corevkBlockId() & BlockStateIds.PACKED_MASK);
			write(extra, Extra.MID_TEX_COORD, middle);
			write(extra, Extra.MID_BLOCK, blockMiddle ? midBlock(vertices[at]) : 0);
			write(extra, Extra.TANGENT_FRAME, frame);
			// The two fields the encoder was handed, kept apart instead of multiplied together, out
			// of Sodium's own published helper. Sodium's word beside it keeps the product, so this
			// one is read by a pack that asked for it and by nothing else.
			write(extra, Extra.TINT_AND_AO, ColorABGR.withAlpha(vertices[at].color, vertices[at].ao));
		}

		return pointer + (long) vertices.length * this.stride;
	}

	
	private void write(long extra, Extra element, int value) {
		int at = offset(element);
		if (at != ABSENT) {
			MemoryUtil.memPutInt(extra + at, value);
		}
	}

	
	private static float[] frame(ChunkVertexEncoder.Vertex[] vertices) {
		float[] frame = FRAMES.get();

		// Emptied rather than allocated, and only the three Newell sums into: the tangent's three are
		// written outright on every road out of here, the last of them being perpendicular's, and the
		// handedness on the line below.
		frame[0] = 0.0F;
		frame[1] = 0.0F;
		frame[2] = 0.0F;

		// The answer for a quad no triangle of which has an area to measure. A triangle that has one
		// overwrites it below, whether or not it goes on to yield a direction.
		frame[6] = handedness(UNREVERSED_AREA);

		// Newell: every edge of the loop contributes, so a quad whose four corners are not in one
		// plane still answers the plane they are closest to instead of the plane of its first three.
		for (int at = 0; at < vertices.length; at++) {
			ChunkVertexEncoder.Vertex current = vertices[at];
			ChunkVertexEncoder.Vertex next = vertices[(at + 1) % vertices.length];
			frame[0] += (current.y - next.y) * (current.z + next.z);
			frame[1] += (current.z - next.z) * (current.x + next.x);
			frame[2] += (current.x - next.x) * (current.y + next.y);
		}

		normalise(frame, 0);

		// The second triangle when the first has nothing to say, which is CoreVK's own retry in
		// computeTangentForQuad: three corners of a quad can share a texture coordinate while the
		// fourth does not, and taking the perpendicular there would throw away a tangent the other
		// half of the same quad holds. Four corners is a contract on both roads in: push refuses any
		// other length before the encoder is called, and writeExternal, which checks nothing, is fed
		// the sorter's own array, allocated by ChunkVertexEncoder.Vertex.uninitializedQuad.
		if (!tangent(frame, vertices[0], vertices[1], vertices[2])
				&& !tangent(frame, vertices[2], vertices[3], vertices[0])) {
			perpendicular(frame);
		}

		orthogonalise(frame);

		return frame;
	}

	
	private static void orthogonalise(float[] frame) {
		float along = frame[0] * frame[3] + frame[1] * frame[4] + frame[2] * frame[5];
		frame[3] -= frame[0] * along;
		frame[4] -= frame[1] * along;
		frame[5] -= frame[2] * along;
		if (frame[3] * frame[3] + frame[4] * frame[4] + frame[5] * frame[5] > FLATTENED) {
			normalise(frame, 3);

			return;
		}

		basis(frame);
	}

	
	private static void basis(float[] frame) {
		float side = frame[2] >= 0.0F ? 1.0F : -1.0F;
		float scale = -1.0F / (side + frame[2]);
		frame[3] = 1.0F + side * frame[0] * frame[0] * scale;
		frame[4] = side * frame[0] * frame[1] * scale;
		frame[5] = -side * frame[0];
		normalise(frame, 3);
	}

	
	private static boolean tangent(float[] frame, ChunkVertexEncoder.Vertex a,
			ChunkVertexEncoder.Vertex b, ChunkVertexEncoder.Vertex c) {
		float dv1 = b.v - a.v;
		float dv2 = c.v - a.v;
		float area = (b.u - a.u) * dv2 - (c.u - a.u) * dv1;
		if (Math.abs(area) < 1.0E-9F) {
			return false;
		}

		// Before the direction, because this triangle can fail to yield one and its area is measured
		// all the same: the sign is the mapping's answer either way, and the fallback below has none.
		frame[6] = handedness(area);

		float scale = 1.0F / area;
		frame[3] = ((b.x - a.x) * dv2 - (c.x - a.x) * dv1) * scale;
		frame[4] = ((b.y - a.y) * dv2 - (c.y - a.y) * dv1) * scale;
		frame[5] = ((b.z - a.z) * dv2 - (c.z - a.z) * dv1) * scale;

		// A determinant that is not zero does not promise a direction: three corners on one line
		// with a mapping that is not affine give one, and the tangent still comes out as nothing.
		// CoreVK refuses this case too, and it is what makes its retry fire at all. On what terms it
		// refuses is not the same, and the javadoc above says how. The sum below is of absolute
		// values, so two components cannot cancel each other into a false refusal.
		if (Math.abs(frame[3]) + Math.abs(frame[4]) + Math.abs(frame[5]) < 1.0E-9F) {
			return false;
		}

		normalise(frame, 3);

		return true;
	}

	
	private static void normalise(float[] frame, int at) {
		float length = (float) Math.sqrt(frame[at] * frame[at] + frame[at + 1] * frame[at + 1]
				+ frame[at + 2] * frame[at + 2]);
		if (length < 1.0E-9F) {
			frame[at] = 0.0F;
			frame[at + 1] = 1.0F;
			frame[at + 2] = 0.0F;

			return;
		}

		frame[at] /= length;
		frame[at + 1] /= length;
		frame[at + 2] /= length;
	}

	
	private static float handedness(float textureArea) {
		return textureArea < 0.0F ? 1.0F : -1.0F;
	}

	
	private static void perpendicular(float[] frame) {
		// Crossed with the less aligned of the x and y axes, so the result is never a zero.
		boolean upright = Math.abs(frame[1]) < Math.abs(frame[0]);
		frame[3] = upright ? -frame[2] : 0.0F;
		frame[4] = upright ? 0.0F : frame[2];
		frame[5] = upright ? frame[0] : -frame[1];
		normalise(frame, 3);
	}

	
	private static int midBlock(ChunkVertexEncoder.Vertex vertex) {
		int origin = ((TerrainVertex) vertex).corevkBlockOrigin();

		return (offset(TerrainVertex.origin(origin, 0), vertex.x) & 0xFF)
				| ((offset(TerrainVertex.origin(origin, 1), vertex.y) & 0xFF) << 8)
				| ((offset(TerrainVertex.origin(origin, 2), vertex.z) & 0xFF) << 16)
				| (TerrainVertex.emission(origin) << 24);
	}

	
	private static int offset(int block, float vertex) {
		return (int) Math.floor((block + 0.5F - vertex) * 64.0F);
	}

	
	private static int midTexCoord(ChunkVertexEncoder.Vertex[] vertices) {
		if (vertices.length == 0) {
			return 0;
		}

		float u = 0.0F;
		float v = 0.0F;
		for (ChunkVertexEncoder.Vertex vertex : vertices) {
			u += vertex.u;
			v += vertex.v;
		}

		u /= vertices.length;
		v /= vertices.length;

		return (Math.round(u * TEXTURE_SCALE) & 0xFFFF) | ((Math.round(v * TEXTURE_SCALE) & 0xFFFF) << 16);
	}
}
