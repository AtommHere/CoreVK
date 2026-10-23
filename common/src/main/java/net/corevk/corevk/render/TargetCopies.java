package net.corevk.corevk.render;

import net.corevk.corevk.pack.model.TargetName;
import net.corevk.corevk.pack.target.TargetSchedule;
import net.corevk.corevk.CoreVK;

import com.mojang.blaze3d.GpuDeviceLossException;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.textures.GpuTextureView;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

final class TargetCopies {

	static final int FIRST_SAMPLED = 4;

	private record Key(int target, TargetSchedule.Side side) {
	}

	private final Set<Key> asked = new LinkedHashSet<>();

	private final Set<String> said = new HashSet<>();

	private final Map<Key, TargetSurface> copies = new LinkedHashMap<>();

	private final Map<Key, TargetSurface> sources = new LinkedHashMap<>();

	private final Set<Key> refused = new LinkedHashSet<>();

	private boolean written;

	void ask(int target, TargetSchedule.Side side) {
		synchronized (this) {
			this.asked.add(new Key(target, side));
		}
	}

	boolean firstMention(String path, int target, TargetSchedule.Side side) {
		synchronized (this) {
			return this.said.add(path + "|" + target + "|" + side);
		}
	}

	void ensure(BiFunction<Integer, TargetSchedule.Side, TargetSurface> source) {
		List<Key> keys;
		synchronized (this) {
			keys = List.copyOf(this.asked);
		}

		for (Key key : keys) {
			if (this.refused.contains(key)) {
				continue;
			}

			TargetSurface target = source.apply(key.target(), key.side());
			if (target == null) {
				continue;
			}

			TargetSurface copy = this.copies.get(key);
			if (copy != null && copy.width() == target.width() && copy.height() == target.height()) {
				this.sources.put(key, target);
				continue;
			}

			String name = TargetName.canonical(key.target()) + (key.side() == TargetSchedule.Side.ALT
					? " alt" : "");
			try {
				if (copy == null) {

					CoreVK.logger().info("Allocating a copy of {} as {} at {}x{}, read by a "
							+ "translucent geometry program on the half it writes", name,
							target.texture().getFormat(), target.width(), target.height());
					this.copies.put(key, new TargetSurface("CoreVK " + name + " copy",
							target.texture().getFormat(), false, target.width(), target.height()));
				} else {
					copy.resize(target.width(), target.height());
				}
			} catch (GpuDeviceLossException e) {
				throw e;
			} catch (RuntimeException e) {
				this.refused.add(key);
				TargetSurface failed = this.copies.remove(key);
				if (failed != null) {
					failed.close();
				}

				this.sources.remove(key);
				CoreVK.logger().error("CoreVK could not allocate the copy of {} at {}x{}, so the "
						+ "programs reading it on the half they write read one pixel there instead",
						name, target.width(), target.height(), e);
				continue;
			}

			this.written = false;
			this.sources.put(key, target);
		}
	}

	boolean any() {
		return !this.copies.isEmpty();
	}

	void take(CommandEncoder encoder) {
		for (Map.Entry<Key, TargetSurface> entry : this.copies.entrySet()) {
			TargetSurface source = this.sources.get(entry.getKey());
			TargetSurface copy = entry.getValue();
			if (source == null || source.texture() == null || copy.texture() == null) {
				continue;
			}

			encoder.copyTextureToTexture(source.texture(), copy.texture(), 0, 0, 0, 0, 0,
					copy.width(), copy.height());
		}

		this.written = true;
	}

	void forget() {
		this.written = false;
	}

	GpuTextureView view(int target, TargetSchedule.Side side) {
		if (!this.written) {
			return null;
		}

		TargetSurface copy = this.copies.get(new Key(target, side));

		return copy == null ? null : copy.view();
	}

	void release() {
		this.copies.values().forEach(TargetSurface::close);
		this.copies.clear();
		this.sources.clear();
		this.written = false;
	}
}
