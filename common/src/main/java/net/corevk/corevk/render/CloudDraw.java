package net.corevk.corevk.render;

import net.corevk.corevk.glsl.PackProgram;
import net.corevk.corevk.pack.option.OptionValue;
import net.corevk.corevk.pack.source.OpenedPack;
import net.corevk.corevk.pack.source.ShaderProperties;
import net.corevk.corevk.pack.target.ChainPlan;
import net.corevk.corevk.pack.target.TargetPlan;
import net.corevk.corevk.pack.model.TargetSize;
import net.corevk.corevk.CoreVK;

import com.mojang.blaze3d.GpuDeviceLossException;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class CloudDraw extends FamilyDraw {

	
	private static volatile boolean wanted;

	
	private static final String PROGRAM = "gbuffers_clouds";

	private final PackChain owner;
	private final Path packPath;
	private final String place;
	private final Map<String, OptionValue> chosen;
	private final String profile;
	private final PackValues values;
	private final int load;
	private final ChainPlan plan;
	private final TargetPlan chainTargets;
	private final boolean chainRuns;
	private final ColorTargets targets;

	
	private final Map<Boolean, CloudProgram> programs = new LinkedHashMap<>();

	
	private volatile boolean read;

	
	private volatile boolean announced;

	
	private CloudProgram drawing;

	CloudDraw(PackChain owner, Path packPath, String place, Map<String, OptionValue> chosen,
			String profile, PackValues values, int load, ChainPlan plan, TargetPlan chainTargets,
			boolean chainRuns, ColorTargets targets) {
		this.owner = owner;
		this.packPath = packPath;
		this.place = place;
		this.chosen = Map.copyOf(chosen);
		this.profile = profile;
		this.values = values;
		this.load = load;
		this.plan = plan;
		this.chainTargets = chainTargets;
		this.chainRuns = chainRuns;
		this.targets = targets;
	}

	
	static void wanted(boolean asked) {
		wanted = asked;
	}

	
	static boolean wanted() {
		return wanted;
	}

	
	void prefetch() {
		prefetch(null);
	}

	
	@Override
	void prefetch(OpenedPack shared) {
		if (!this.read && wanted()) {
			read(shared);
		}
	}

	
	public static Optional<ShaderProperties.CloudSetting> setting() {
		CloudDraw draw = PackChain.clouds();
		if (draw == null || !wanted) {
			return Optional.empty();
		}

		ShaderProperties.CloudSetting asked = draw.values.skyElements().clouds();
		draw.announce(asked);

		return asked == ShaderProperties.CloudSetting.DEFAULT ? Optional.empty() : Optional.of(asked);
	}

	
	private void announce(ShaderProperties.CloudSetting asked) {
		if (this.announced) {
			return;
		}

		this.announced = true;
		if (asked == ShaderProperties.CloudSetting.DEFAULT) {
			CoreVK.logger().info("{} asks nothing of the cloud setting, so the player's own stands "
					+ "and the game opens the pass this engine draws in",
					this.packPath.getFileName());
		} else {
			CoreVK.logger().info("{} asks for clouds={}, so the player's own setting is overruled. "
					+ "Off is a pack saying it draws its own elsewhere, and the game then opens no "
					+ "cloud pass at all: nothing of this engine runs and nothing else would say so",
					this.packPath.getFileName(), asked.name().toLowerCase(Locale.ROOT));
		}
	}

	
	public static RenderPipeline pipeline(boolean fancy) {
		CloudDraw draw = PackChain.clouds();
		GpuDevice device = RenderSystem.tryGetDevice();
		if (draw == null || !wanted || device == null) {
			return null;
		}

		try {
			return draw.prepare(device, fancy);
		} catch (GpuDeviceLossException e) {
			throw e;
		} catch (RuntimeException e) {
			wanted = false;
			CoreVK.logger().error("CoreVK stopped drawing the clouds after an error", e);

			return null;
		}
	}

	
	public static RenderPassDescriptor descriptor(GpuTextureView colour, GpuTextureView depth) {
		CloudDraw draw = PackChain.clouds();
		if (draw == null || draw.drawing == null) {
			return null;
		}

		return draw.drawing.descriptor(colour, depth);
	}

	
	public static void bind(RenderPass pass, RenderPipeline bound) {
		CloudDraw draw = PackChain.clouds();
		if (draw != null && draw.drawing != null && draw.drawing.owns(bound)) {
			draw.drawing.bind(pass);
		}
	}

	
	private boolean shown() {
		return !this.chainRuns || this.owner.drawable();
	}

	
	private void read(OpenedPack shared) {
		try {
			Optional<PackProgram.Loaded> loaded = shared != null
					? PackProgram.loadClouds(shared, this.place)
					: PackProgram.loadClouds(this.packPath, this.place, this.chosen, this.profile);
			if (loaded.isEmpty()) {
				CoreVK.logger().info("{} serves nothing in {} for its clouds, so the game keeps its "
						+ "own", this.packPath.getFileName(),
						this.place.isEmpty() ? "its root" : this.place);

				return;
			}

			List<ChainPlan.Attachment> writes = writes();
			// Both of them, because a cloud setting is changed in the middle of a session and neither
			// answer is a compile: a module is only built when a pipeline is first prepared, so the
			// setting nobody plays on costs one Java object and nothing on the device.
			this.programs.put(Boolean.TRUE, CloudProgram.of(loaded.get(), true, this.values, this.load,
					writes, this.chainTargets, this.targets, this.chainRuns));
			this.programs.put(Boolean.FALSE, CloudProgram.of(loaded.get(), false, this.values,
					this.load, writes, this.chainTargets, this.targets, this.chainRuns));

			if (writes.isEmpty()) {
				CoreVK.logger().info("{} has nowhere of its own for its clouds, so they keep the "
						+ "game's target and the full screen layer brings them across",
						this.packPath.getFileName());
			}
		} catch (IOException | RuntimeException e) {
			CoreVK.logger().error("Could not prepare the cloud program of "
					+ this.packPath.getFileName() + ", so the game keeps its own clouds", e);
		} finally {
			this.read = true;
		}
	}

	
	private List<ChainPlan.Attachment> writes() {
		return this.plan.sky(PROGRAM)
				.filter(clouds -> {
					if (clouds.size().equals(TargetSize.ofScreen())) {
						return true;
					}

					CoreVK.logger().warn("{} writes targets the pack asked to be scaled, so its "
							+ "clouds keep the game's own target", clouds.program());

					return false;
				})
				.map(ChainPlan.Pass::attachments)
				.orElse(List.of());
	}

	private RenderPipeline prepare(GpuDevice device, boolean fancy) {
		if (!this.read) {
			return null;
		}

		CloudProgram program = this.programs.get(fancy);
		this.drawing = program;
		if (program == null) {
			return null;
		}

		// The same two calls the terrain and the sky make, and for the same reasons. The clouds are
		// drawn late in the frame and will not usually be what opens it, but they can be: the sky is
		// refused in the Nether and the terrain can be turned off in one line.
		this.owner.beginFrame();
		if (!this.owner.openTargets(device)) {
			return null;
		}

		if (!shown()) {
			return null;
		}

		return program.prepare(device);
	}

	
	@Override
	Collection<CloudProgram> programs() {
		return this.programs.values();
	}

	
	@Override
	void rotate() {
		this.drawing = null;
		if (!this.read) {
			return;
		}

		this.programs.values().forEach(CloudProgram::rotate);
	}

	@Override
	void release() {
		this.programs.values().forEach(CloudProgram::release);
		this.programs.clear();
		this.drawing = null;
		this.read = false;
	}
}
