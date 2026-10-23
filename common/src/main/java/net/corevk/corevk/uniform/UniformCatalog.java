package net.corevk.corevk.uniform;

import net.corevk.corevk.uniform.values.CameraValues;
import net.corevk.corevk.uniform.values.CelestialValues;
import net.corevk.corevk.uniform.values.DhValues;
import net.corevk.corevk.uniform.values.DrawValues;
import net.corevk.corevk.uniform.values.GeometryValues;
import net.corevk.corevk.uniform.values.MatrixValues;
import net.corevk.corevk.uniform.values.PlayerValues;
import net.corevk.corevk.uniform.values.ShadowGeometryValues;
import net.corevk.corevk.uniform.values.ShadowMatrixValues;
import net.corevk.corevk.uniform.values.TimeValues;
import net.corevk.corevk.uniform.values.WeatherValues;
import net.corevk.corevk.uniform.values.WorldValues;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class UniformCatalog {

	private static volatile UniformCatalog engine;
	private static volatile UniformCatalog geometry;
	private static volatile UniformCatalog shadowGeometry;

	private final Map<String, Entry> entries;

	private UniformCatalog(Map<String, Entry> entries) {
		this.entries = entries;
	}

	private record Entry(UniformShape natural, UniformSource source) {
	}

	
	public static UniformCatalog engine() {
		UniformCatalog built = engine;
		if (built != null) {
			return built;
		}

		synchronized (UniformCatalog.class) {
			if (engine == null) {
				Builder builder = builder();
				DrawValues.register(builder);
				CameraValues.register(builder);
				MatrixValues.register(builder);
				ShadowMatrixValues.register(builder);
				DhValues.register(builder);
				CelestialValues.register(builder);
				TimeValues.register(builder);
				WeatherValues.register(builder);
				WorldValues.register(builder);
				PlayerValues.register(builder);
				coreMatrices(builder);
				engine = builder.build();
			}

			return engine;
		}
	}

	
	public static UniformCatalog geometry() {
		UniformCatalog built = geometry;
		if (built != null) {
			return built;
		}

		synchronized (UniformCatalog.class) {
			if (geometry == null) {
				Builder builder = builder(engine());
				GeometryValues.register(builder);
				coreMatrices(builder);
				geometry = builder.build();
			}

			return geometry;
		}
	}

	
	public static UniformCatalog shadowGeometry() {
		UniformCatalog built = shadowGeometry;
		if (built != null) {
			return built;
		}

		synchronized (UniformCatalog.class) {
			if (shadowGeometry == null) {
				Builder builder = builder(geometry());
				ShadowGeometryValues.register(builder);
				coreMatrices(builder);
				shadowGeometry = builder.build();
			}

			return shadowGeometry;
		}
	}

	
	private static void coreMatrices(Builder builder) {
		builder.alias("modelViewMatrix", "of_ModelViewMatrix");
		builder.alias("projectionMatrix", "of_ProjectionMatrix");
		builder.alias("textureMatrix", "of_TextureMatrix");
	}

	public static Builder builder() {
		return new Builder(Map.of());
	}

	
	public static Builder builder(UniformCatalog base) {
		return new Builder(base.entries);
	}

	
	public UniformSource source(String name) {
		Entry entry = this.entries.get(name);

		return entry == null ? null : entry.source();
	}

	public UniformShape natural(String name) {
		Entry entry = this.entries.get(name);

		return entry == null ? null : entry.natural();
	}

	public Set<String> names() {
		return Collections.unmodifiableSet(this.entries.keySet());
	}

	public static final class Builder {

		private final Map<String, Entry> entries;

		
		private final Set<String> added = new HashSet<>();

		private Builder(Map<String, Entry> base) {
			this.entries = new LinkedHashMap<>(base);
		}

		
		public Builder add(String name, UniformShape natural, UniformSource source) {
			if (!this.added.add(name)) {
				throw new IllegalStateException(name + " is registered twice in the same catalogue");
			}

			this.entries.put(name, new Entry(natural, source));

			return this;
		}

		
		public Builder alias(String name, String existing) {
			Entry entry = this.entries.get(existing);
			if (entry == null) {
				throw new IllegalStateException(
						"Cannot make " + name + " a second name for " + existing + ", which is not registered");
			}

			return add(name, entry.natural(), entry.source());
		}

		public UniformCatalog build() {
			return new UniformCatalog(Collections.unmodifiableMap(new LinkedHashMap<>(this.entries)));
		}
	}
}
