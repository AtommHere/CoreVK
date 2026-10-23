package net.corevk.corevk.render;

import net.corevk.corevk.pack.target.PackDirectives;
import net.corevk.corevk.pack.target.TargetDirectives;
import net.corevk.corevk.CoreVK;

import com.mojang.blaze3d.GpuDeviceLossException;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.util.Mth;
import org.joml.Vector4f;
import org.joml.Vector4fc;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

final class ShadowTargets {

	
	private static final double FAR = 1.0;

	
	private static final int MAX_RESOLUTION = 16384;

	
	private static final GpuFormat DEPTH_FORMAT = GpuFormat.D32_FLOAT;

	
	private static final int USAGE = GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC
			| GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT;

	
	static final int MAX_COLOURS = PackDirectives.MAX_SHADOW_COLOURS;

	
	private static final String CLEAR_LABEL = "CoreVK pending shadow clears";

	private final int resolution;
	private final List<PackDirectives.ShadowColour> asked;
	private final List<GpuFormat> formats;
	private final List<Vector4fc> clearColours;

	
	private final List<PackDirectives.ShadowDepth> depths;

	
	private final int ceiling;

	
	private final List<Integer> live;

	
	private final boolean[] unstarted = new boolean[MAX_COLOURS];

	
	private final TargetSurface[] colours = new TargetSurface[MAX_COLOURS];

	
	private GpuTexture depth;
	private GpuTextureView depthView;

	
	private GpuTextureView depthAttachment;

	
	private GpuTexture noTranslucents;
	private GpuTextureView noTranslucentsView;

	
	private GpuTexture keptDepth;
	private final GpuTexture[] keptColour = new GpuTexture[MAX_COLOURS];
	private boolean kept;
	private boolean warnedCopy;
	private boolean saidRestored;

	
	private boolean copied;

	
	private final boolean[] chainWritten = new boolean[2];

	private boolean broken;

	
	private final Vector4fc[] pendingColour = new Vector4fc[MAX_COLOURS];
	private boolean pendingDepth;

	
	ShadowTargets(int resolution, List<PackDirectives.ShadowColour> asked,
			List<PackDirectives.ShadowDepth> depths, Set<Integer> named, int ceiling) {
		// Clamped rather than refused: a directive that survived a setting nobody expanded can be
		// any number at all, and a shadow map is not worth taking the pack down for.
		this.resolution = Math.clamp(resolution, 1, MAX_RESOLUTION);
		if (this.resolution != resolution) {
			CoreVK.logger().warn("The pack asks for a shadow map of {} texels, which is outside what "
					+ "this engine will allocate, so it is drawn at {}", resolution, this.resolution);
		}

		this.asked = List.copyOf(asked);
		this.formats = this.asked.stream().map(one -> GpuFormats.of(one.format().used())).toList();
		// The same correction the colour targets make, and it stops where theirs stops: a format
		// that gained an alpha channel on the way to the device starts opaque, because in GL the
		// three component texture the pack wrote against always sampled as one and the promoted
		// image returns what is really there - but a pack that named a clear colour itself wrote
		// four components and is handed the four it wrote. Forcing the alpha over the pack's own
		// value would be this engine overruling it on a channel it was explicit about.
		this.clearColours = this.asked.stream().map(one -> {
			TargetDirectives.Colour colour = one.clearColour();

			return (Vector4fc) new Vector4f(colour.r(), colour.g(), colour.b(),
					one.format().alphaAdded() && !one.declaresClearColour() ? 1.0F : colour.a());
		}).toList();

		this.depths = List.copyOf(depths);
		this.ceiling = Math.clamp(ceiling, 1, MAX_COLOURS);
		SortedSet<Integer> live = new TreeSet<>(Set.of(0));
		named.stream().filter(index -> index > 0 && index < this.ceiling).forEach(live::add);
		this.live = List.copyOf(live);
	}

	
	FilterMode depthFilter(boolean withoutTranslucents) {
		return this.depths.get(withoutTranslucents ? 1 : 0).nearest()
				? FilterMode.NEAREST
				: FilterMode.LINEAR;
	}

	
	boolean depthMipmapped(boolean withoutTranslucents) {
		if (!this.depths.get(withoutTranslucents ? 1 : 0).mipmap()) {
			return false;
		}

		return this.chainWritten[withoutTranslucents && this.copied ? 1 : 0];
	}

	
	private int levels(int index) {
		if (!this.depths.get(index).mipmap()) {
			return 1;
		}

		if (!GpuFormats.blitsBothWays(DEPTH_FORMAT)) {
			return 1;
		}

		return Math.max(1, Mth.log2(this.resolution));
	}

	
	private boolean chainRefused() {
		return (this.depths.get(0).mipmap() || this.depths.get(1).mipmap())
				&& !GpuFormats.blitsBothWays(DEPTH_FORMAT);
	}

	
	boolean ensure() {
		if (this.broken) {
			return false;
		}

		if (this.depth != null) {
			return true;
		}

		try {
			int depthLevels = levels(0);
			this.depth = RenderSystem.getDevice().createTexture(() -> "CoreVK shadow depth", USAGE,
					DEPTH_FORMAT, this.resolution, this.resolution, 1, depthLevels);
			this.depthView = RenderSystem.getDevice().createTextureView(this.depth);
			this.depthAttachment = depthLevels > 1
					? RenderSystem.getDevice().createTextureView(this.depth, 0, 1)
					: this.depthView;

			for (int index : this.live) {
				this.colours[index] = new TargetSurface("CoreVK shadowcolor" + index,
						this.formats.get(index), false, this.resolution, this.resolution);
				this.unstarted[index] = true;
			}

			clear(RenderSystem.getDevice().createCommandEncoder());
			// The size and no word about why it is that size. This class cannot tell a pack that
			// asked for a small map from a player who asked for a smaller one: what it is handed is
			// one number, already through whatever was applied upstream, and a caption naming the
			// player's setting would be a claim it cannot make. PackChain says the setting is in
			// force, once, where it knows that.
			CoreVK.logger().info("Shadow map allocated at {}x{}, storing the forward depth window, "
				+ "with {}", this.resolution, this.resolution, describe());

			return true;
		} catch (GpuDeviceLossException e) {
			throw e;
		} catch (RuntimeException e) {
			this.broken = true;
			// The images go with the refusal, so that a map allocated and not emptied is never the
			// one a lookup lands on: ensure answering false only stops the pass being drawn, where
			// the gbuffers bind whatever depth() still holds.
			release();
			CoreVK.logger().error("CoreVK could not allocate the shadow map, so no shadow pass will "
					+ "run and every shadowtex lookup keeps reading one pixel", e);

			return false;
		}
	}

	
	void clear(CommandEncoder encoder) {
		stash();
		flushPending(encoder);
	}

	
	void defer() {
		stash();
	}

	Optional<Vector4fc> takeColourClear(int index) {
		Vector4fc colour = this.pendingColour[index];
		this.pendingColour[index] = null;
		return colour == null ? Optional.empty() : Optional.of(colour);
	}

	OptionalDouble takeDepthClear() {
		if (!this.pendingDepth) {
			return OptionalDouble.empty();
		}

		this.pendingDepth = false;
		return OptionalDouble.of(FAR);
	}

	
	void flushPending(CommandEncoder encoder) {
		if (this.depth == null) {
			return;
		}

		List<GpuTextureView> colours = new ArrayList<>(this.live.size());
		List<Vector4fc> colourValues = new ArrayList<>(this.live.size());
		for (int index : this.live) {
			if (this.pendingColour[index] == null) {
				continue;
			}

			GpuTextureView view = colour(index);
			if (view == null) {
				continue;
			}

			colours.add(view);
			colourValues.add(this.pendingColour[index]);
			this.pendingColour[index] = null;
		}

		boolean depth = this.pendingDepth;
		this.pendingDepth = false;
		if (colours.isEmpty() && !depth) {
			return;
		}

		if (colours.isEmpty()) {
			encoder.clearDepthTexture(this.depth, FAR);
			return;
		}

		PassDescriptor descriptor = PassDescriptor.create(() -> CLEAR_LABEL);
		for (int index = 0; index < colours.size(); index++) {
			descriptor.withColorAttachment(colours.get(index), Optional.of(colourValues.get(index)));
		}

		if (depth) {
			descriptor.withDepthAttachment(this.depthAttachment, OptionalDouble.of(FAR));
		}

		descriptor.withRenderArea(new RenderPass.RenderArea(0, 0, this.resolution, this.resolution));
		encoder.createRenderPass(descriptor.build()).close();
	}

	private void stash() {
		this.copied = false;
		this.pendingDepth = false;
		for (int index : this.live) {
			this.pendingColour[index] = null;
		}

		if (this.depth == null) {
			return;
		}

		// The chain stops standing for the map the moment the map is emptied, exactly as the copy
		// beside it does: what the levels hold is an average of a picture this call is throwing
		// away, and a lookup climbing to one would read the last frame's world under this frame's
		// base. The tail of the stage fills them again before anything reads them.
		this.chainWritten[0] = false;
		this.chainWritten[1] = false;
		this.pendingDepth = true;
		for (int index : this.live) {
			if (this.colours[index] != null && wanted(index)) {
				this.pendingColour[index] = this.clearColours.get(index);
			}
		}
	}

	
	private boolean wanted(int index) {
		if (this.asked.get(index).clear() || this.unstarted[index]) {
			this.unstarted[index] = false;

			return true;
		}

		return false;
	}

	
	private String describe() {
		StringBuilder text = new StringBuilder();
		for (int index : this.live) {
			text.append(text.isEmpty() ? "" : " and ")
					.append("shadowcolor").append(index)
					.append(" as ").append(this.formats.get(index))
					.append(this.asked.get(index).clear() ? "" : ", which the pack keeps between frames");
		}

		int zero = levels(0);
		int one = levels(1);
		if (zero > 1 || one > 1) {
			// What each NAME is read at rather than what each image holds: the copy shadowtex1 is
			// read through is allocated the first time the stage takes it, and a pack no program of
			// which names it never has one at all.
			text.append(", and the pack asks for a chain the light fills every frame, ")
					.append(zero).append(" levels where it reads shadowtex0 and ")
					.append(one).append(" where it reads shadowtex1");
		} else if (chainRefused()) {
			// Said where the map's own line is, because it is the map that is smaller than the pack
			// asked for: a chain nobody can fill would be read as a coarser image and hold whatever
			// the driver left in it, so the map keeps one level and every lookup reads the base.
			text.append(", and the pack asks for a chain this device will not fill on a depth "
					+ "format, so the map carries one level and every lookup reads it");
		}

		return text.toString();
	}

	
	void copyWithoutTranslucents(CommandEncoder encoder) {
		if (this.depth == null || this.broken) {
			return;
		}

		if (this.noTranslucents == null) {
			// The source's own format rather than an assumed one, the same rule the world's depth
			// copy follows: a copy whose format differs from its source is refused outright.
			// Three usages and not the four the map itself takes: nothing ever draws into this
			// image, it is written by the copy alone. COPY_SRC is here for the chain, a blit
			// reading the level above the one it writes.
			this.noTranslucents = RenderSystem.getDevice().createTexture(() -> "CoreVK shadowtex1",
					GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC
							| GpuTexture.USAGE_TEXTURE_BINDING,
					this.depth.getFormat(), this.resolution, this.resolution, 1,
					levels(1));
			this.noTranslucentsView = RenderSystem.getDevice().createTextureView(this.noTranslucents);
		}

		encoder.copyTextureToTexture(this.depth, this.noTranslucents, 0, 0, 0, 0, 0, this.resolution,
				this.resolution);
		this.copied = true;
	}

	
	void generateMipmaps(CommandEncoder encoder) {
		if (this.depth == null || this.broken) {
			return;
		}

		this.chainWritten[0] = MipmapReduction.generate(encoder, this.depth);
		// Its own chain over its own base, which the copy has just written. Left out, the levels of
		// shadowtex1 would hold the average of whatever the last fill saw, which is a frame of the
		// world older than the base under them.
		this.chainWritten[1] = this.noTranslucents != null
				&& MipmapReduction.generate(encoder, this.noTranslucents);
	}

	
	void keep(CommandEncoder encoder) {
		if (this.depth == null || this.broken || !copyable(this.depth)) {
			return;
		}

		if (this.keptDepth == null) {
			this.keptDepth = store("CoreVK kept shadow depth", this.depth.getFormat());
		}

		encoder.copyTextureToTexture(this.depth, this.keptDepth, 0, 0, 0, 0, 0, this.resolution,
				this.resolution);
		for (int index : this.live) {
			GpuTexture colour = colourTexture(index);
			if (colour == null) {
				continue;
			}

			if (this.keptColour[index] == null) {
				this.keptColour[index] = store("CoreVK kept shadowcolor" + index,
						colour.getFormat());
			}

			encoder.copyTextureToTexture(colour, this.keptColour[index], 0, 0, 0, 0, 0,
					this.resolution, this.resolution);
		}

		if (!this.kept) {
			CoreVK.logger().info("Shadow map store taken, so a later frame may put the opaque world "
					+ "back instead of walking for it");
		}

		this.kept = true;
	}

	
	boolean restore(CommandEncoder encoder) {
		if (!this.kept || this.depth == null || this.broken || this.keptDepth == null) {
			return false;
		}

		encoder.copyTextureToTexture(this.keptDepth, this.depth, 0, 0, 0, 0, 0, this.resolution,
				this.resolution);
		for (int index : this.live) {
			GpuTexture colour = colourTexture(index);
			if (colour != null && this.keptColour[index] != null) {
				encoder.copyTextureToTexture(this.keptColour[index], colour, 0, 0, 0, 0, 0,
						this.resolution, this.resolution);
			}
		}

		this.copied = false;
		// The store holds level nought alone, so a restore leaves the levels above it standing for
		// a map that is no longer under them. The tail of the stage fills them again.
		this.chainWritten[0] = false;
		this.chainWritten[1] = false;
		if (!this.saidRestored) {
			this.saidRestored = true;
			CoreVK.logger().info("Shadow map put back from the store, so this frame draws only "
					+ "what moves and the water");
		}

		return true;
	}

	
	boolean hasKept() {
		return this.kept && !this.broken;
	}

	private GpuTexture store(String name, GpuFormat format) {
		return RenderSystem.getDevice().createTexture(() -> name,
				GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC, format,
				this.resolution, this.resolution, 1, 1);
	}

	private GpuTexture colourTexture(int index) {
		TargetSurface surface = this.colours[index];

		return surface == null ? null : surface.texture();
	}

	
	private boolean copyable(GpuTexture depth) {
		if ((depth.usage() & GpuTexture.USAGE_COPY_DST) != 0) {
			return true;
		}

		if (!this.warnedCopy) {
			this.warnedCopy = true;
			CoreVK.logger().warn("The shadow map cannot be copied into on this device, so it is "
					+ "drawn every frame and the reuse setting does nothing");
		}

		return false;
	}

	
	GpuTextureView depth() {
		return this.depthView;
	}

	
	GpuTextureView depthAttachment() {
		return this.depthAttachment;
	}

	
	GpuTextureView depthWithoutTranslucents() {
		return this.copied ? this.noTranslucentsView : depth();
	}

	
	GpuTextureView colour(int index) {
		if (index < 0 || index >= MAX_COLOURS) {
			return null;
		}

		TargetSurface surface = this.colours[index];

		return surface == null ? null : surface.view();
	}

	int resolution() {
		return this.resolution;
	}

	
	int colourCeiling() {
		return this.ceiling;
	}

	
	GpuFormat format(int index) {
		return this.formats.get(index);
	}

	void release() {
		this.copied = false;
		this.chainWritten[0] = false;
		this.chainWritten[1] = false;
		// The one-level view first, and only where it is an object of its own: on a map with no
		// chain it IS the whole view, and closing it twice closes a handle somebody else may still
		// hold under the other name.
		if (this.depthAttachment != null && this.depthAttachment != this.depthView) {
			this.depthAttachment.close();
		}

		this.depthAttachment = null;
		if (this.depthView != null) {
			this.depthView.close();
			this.depthView = null;
		}

		if (this.depth != null) {
			this.depth.close();
			this.depth = null;
		}

		for (int index = 0; index < MAX_COLOURS; index++) {
			if (this.colours[index] != null) {
				this.colours[index].close();
				this.colours[index] = null;
			}
		}

		if (this.noTranslucentsView != null) {
			this.noTranslucentsView.close();
			this.noTranslucentsView = null;
		}

		if (this.noTranslucents != null) {
			this.noTranslucents.close();
			this.noTranslucents = null;
		}

		// The store goes with the images it was taken from: a map kept at one size is not a map at
		// another, and the restore would be refused for a size mismatch rather than for the reason.
		this.kept = false;
		if (this.keptDepth != null) {
			this.keptDepth.close();
			this.keptDepth = null;
		}

		for (int index = 0; index < MAX_COLOURS; index++) {
			if (this.keptColour[index] != null) {
				this.keptColour[index].close();
				this.keptColour[index] = null;
			}
		}
	}
}
