package net.corevk.corevk.render;

import net.corevk.corevk.glsl.GlslTranslator;
import net.corevk.corevk.pack.id.BlockIds;
import net.corevk.corevk.pack.id.NameIds;
import net.corevk.corevk.pack.option.OptionIndex;
import net.corevk.corevk.pack.option.SettingSet;
import net.corevk.corevk.pack.model.RenderStage;
import net.corevk.corevk.pack.source.OpenedPack;
import net.corevk.corevk.pack.source.ShaderPackSource;
import net.corevk.corevk.pack.source.ShaderProperties;
import net.corevk.corevk.pack.source.ShadowCasters;
import net.corevk.corevk.pack.source.ShadowCullState;
import net.corevk.corevk.pack.target.PackDirectives;
import net.corevk.corevk.pack.model.BufferObject;
import net.corevk.corevk.pack.texture.CustomStorage;
import net.corevk.corevk.pack.model.ImageInformation;
import net.corevk.corevk.uniform.expr.CustomUniforms;
import net.corevk.corevk.uniform.NoiseTexture;
import net.corevk.corevk.uniform.UniformCatalog;
import net.corevk.corevk.uniform.UniformGaps;
import net.corevk.corevk.uniform.values.CelestialValues;
import net.corevk.corevk.uniform.WorldState;
import net.corevk.corevk.CoreVK;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4fc;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.IntStream;

public final class PackValues {

	
	private static final int CHUNK_BLOCKS = 16;

	private final FrameState state = new FrameState();

	
	private final Set<String> declared = new LinkedHashSet<>();

	private final List<String> problems = new ArrayList<>();

	private CustomUniforms customs;
	private ShaderProperties.SkyElements skyElements = new ShaderProperties.SkyElements(true, true,
			true, true, ShaderProperties.CloudSetting.DEFAULT);
	private ShaderProperties.Weather weather = new ShaderProperties.Weather(true, true);
	private boolean rainDepth;

	
	private ShadowCasters shadowCasters = ShadowCasters.DEFAULT;

	
	private boolean dhShadow = true;

	
	private boolean avoidsDhClouds;

	
	private ShadowCullState shadowCull = ShadowCullState.DEFAULT;

	private boolean separateAo;
	private boolean breaksAnisotropy;
	private Optional<String> particleOrdering = Optional.empty();
	private NoiseTexture.Image noiseImage;
	private PackImages packImages = PackImages.none();
	private ImageInformation.Reading storageImages = ImageInformation.Reading.empty();
	private BufferObject.Reading bufferObjects = BufferObject.Reading.empty();
	private UniformCatalog catalog = UniformCatalog.engine();
	private UniformCatalog geometry = UniformCatalog.geometry();
	private UniformCatalog shadowGeometry = UniformCatalog.shadowGeometry();

	private PackValues() {
	}

	
	public static PackValues read(OpenedPack pack, String dimension) throws IOException {
		PackValues values = new PackValues();

		ShaderPackSource source = pack.source();
		OptionIndex options = pack.options();
		ShaderProperties properties = pack.properties();
		SettingSet settings = pack.settings();

		values.state.directives(PackDirectives.read(source, settings, dimension));
		// Here because it is the first moment the answer exists and the last one before a unit is
		// translated: the map's chain is a directive of the PACK, folded over every fragment stage
		// of the dimension, and what it decides in the translation is whether a shadowtex lookup
		// keeps the level of detail the pack wrote or is pinned to the base with the rest.
		GlslTranslator.askShadowChains(values.state.directives().shadowDepthMipmap(0),
				values.state.directives().shadowDepthMipmap(1));
		values.state.endFlashShadows(properties.endFlashShadows(settings.globalDefines(options)));
		values.state.oldHandLight(properties.oldHandLight(settings.globalDefines(options)));
		values.skyElements = properties.skyElements(settings.globalDefines(options));
		values.weather = properties.weather(settings.globalDefines(options));
		values.rainDepth = properties.rainDepth(settings.globalDefines(options));
		values.shadowCasters = properties.shadowCasters(settings.globalDefines(options));
		values.dhShadow = properties.dhShadow(settings.globalDefines(options));
		ShaderProperties.CloudSetting dhClouds = properties.dhClouds(settings.globalDefines(options));
		values.avoidsDhClouds = dhClouds == ShaderProperties.CloudSetting.OFF
				|| (dhClouds == ShaderProperties.CloudSetting.DEFAULT
						&& values.skyElements.clouds() == ShaderProperties.CloudSetting.OFF);
		values.shadowCull = properties.shadowCull(settings.globalDefines(options));
		values.separateAo = properties.separateAo(settings.globalDefines(options));
		values.breaksAnisotropy = properties.breaksAnisotropy(settings.globalDefines(options));
		values.particleOrdering = properties.particleOrdering(settings.globalDefines(options));
		values.declare(properties, settings.globalDefines(options));
		values.readNoise(properties, source, settings.globalDefines(options));
		values.packImages =
				PackImages.read(properties, settings.globalDefines(options), source);
		values.storageImages = properties.imageDirectives(settings.globalDefines(options));
		values.bufferObjects = properties.bufferObjects(settings.globalDefines(options));
		CustomStorage.install(values.bufferObjects);

		// Read against the same settings as everything above, which is not a formality: BSL wraps
		// all its declarations in one conditional and keeps a fifth of them under the #else, so a
		// reading with an empty table measures a different pack.
		FaceShading.install(properties.oldLighting(settings.globalDefines(options)));
		BlockStateIds.install(BlockIds.read(source, settings.globalDefines(options)));
		PackNameIds.install(
				NameIds.read(source, settings.globalDefines(options), NameIds.Kind.ENTITY),
				NameIds.read(source, settings.globalDefines(options), NameIds.Kind.ITEM));

		return values;
	}

	
	public NoiseTexture.Image noiseImage() {
		return this.noiseImage;
	}

	
	PackImages packImages() {
		return this.packImages;
	}

	
	ImageInformation.Reading storageImages() {
		return this.storageImages;
	}

	
	public UniformCatalog catalog() {
		return this.catalog;
	}

	
	public UniformCatalog geometryCatalog() {
		return this.geometry;
	}

	
	public UniformCatalog shadowGeometryCatalog() {
		return this.shadowGeometry;
	}

	
	public void convention(Vector4fc convention) {
		this.state.convention(convention);
	}

	
	public void modelView(Matrix4fc matrix, Matrix4fc bob) {
		this.state.passModelView(matrix, bob);
	}

	
	public void projection(Matrix4fc matrix) {
		this.state.passProjection(matrix);
	}

	
	public void passColour(Vector4fc colour) {
		this.state.passColour(colour);
	}

	
	public void passAlphaTest(float reference) {
		this.state.passAlphaTest(reference);
	}

	
	public void renderStage(RenderStage stage) {
		this.state.renderStage(stage);
	}

	
	public Matrix4f shadowFrustum(Matrix4f dest) {
		ViewMatrices view = this.state.view();

		return dest.set(view.drawnShadowProjection()).mul(view.drawnShadowModelView());
	}

	
	public ShadowCullPlan shadowCullPlan(int userChunks, int renderDistanceChunks, Vector3f light,
			Matrix4f camera, boolean voxelised) {
		ViewMatrices view = this.state.view();
		camera.set(view.gbufferProjection()).mul(view.gbufferModelView());

		PackDirectives directives = this.state.directives();
		float multiplier = directives.shadowDistanceRenderMul();
		float bound;
		float safeZone = -1.0F;

		switch (this.shadowCull) {
			case DISTANCE -> {
				float distance = directives.shadowDistance() * multiplier;
				bound = distance <= 0.0F || distance > renderDistanceChunks * (float) CHUNK_BLOCKS
						? -1.0F : distance;
			}
			case SAFE_ZONE -> {
				float effective = multiplier < 0.0F ? 1.0F : multiplier;
				safeZone = directives.voxelDistance() * effective;
				bound = directives.shadowDistance() * effective;
			}
			default -> bound = shadowRenderDistance(userChunks, renderDistanceChunks);
		}

		return new ShadowCullPlan(this.shadowCull, voxelised,
				CelestialValues.shadowLightVector(this.state, light), camera, bound, safeZone);
	}

	
	public void drawnShadowPair(Matrix4f modelView, Matrix4f projection) {
		ViewMatrices view = this.state.view();
		modelView.set(view.drawnShadowModelView());
		projection.set(view.drawnShadowProjection());
	}

	
	public int noiseResolution() {
		return Math.round(this.state.noiseTextureResolution());
	}

	
	public int shadowResolution() {
		return this.state.directives().shadowMapResolution();
	}

	
	public float shadowRenderDistance(int userChunks, int renderDistanceChunks) {
		return distanceFor(this.state.directives().shadowDistanceRenderMul(), userChunks,
				renderDistanceChunks);
	}

	
	private float distanceFor(float multiplier, int userChunks, int renderDistanceChunks) {
		float blocks = multiplier < 0.0F
				? userChunks * (float) CHUNK_BLOCKS
				: this.state.directives().shadowDistance() * multiplier;

		return blocks >= renderDistanceChunks * (float) CHUNK_BLOCKS ? -1.0F : blocks;
	}

	
	public OptionalInt forcedShadowRenderDistanceChunks() {
		PackDirectives directives = this.state.directives();
		if (!directives.forcesShadowRenderDistance()) {
			return OptionalInt.empty();
		}

		int blocks = (int) (directives.shadowDistance() * directives.shadowDistanceRenderMul());

		return OptionalInt.of((blocks + CHUNK_BLOCKS - 1) / CHUNK_BLOCKS);
	}

	
	public float entityShadowDistance(int userChunks, int renderDistanceChunks) {
		float multiplier = this.state.directives().entityShadowDistanceMul();
		if (multiplier == 1.0F || multiplier < 0.0F) {
			return shadowRenderDistance(userChunks, renderDistanceChunks);
		}

		return distanceFor(this.state.directives().shadowDistanceRenderMul() * multiplier,
				userChunks, renderDistanceChunks);
	}

	
	public List<PackDirectives.ShadowColour> shadowColours() {
		return IntStream.range(0, ShadowTargets.MAX_COLOURS)
				.mapToObj(index -> this.state.directives().shadowColour(index))
				.toList();
	}

	
	public List<PackDirectives.ShadowDepth> shadowDepths() {
		return List.of(this.state.directives().shadowDepth(0),
				this.state.directives().shadowDepth(1));
	}

	
	public float sunPathRotation() {
		return this.state.directives().sunPathRotation();
	}

	
	public ShaderProperties.SkyElements skyElements() {
		return this.skyElements;
	}

	
	public ShaderProperties.Weather weather() {
		return this.weather;
	}

	
	public boolean rainDepth() {
		return this.rainDepth;
	}

	
	public ShadowCasters shadowCasters() {
		return this.shadowCasters;
	}

	
	public boolean dhShadow() {
		return this.dhShadow;
	}

	
	public boolean avoidsDhClouds() {
		return this.avoidsDhClouds;
	}

	
	public boolean separateAo() {
		return this.separateAo;
	}

	
	public boolean breaksAnisotropy() {
		return this.breaksAnisotropy;
	}

	
	public Optional<String> particleOrdering() {
		return this.particleOrdering;
	}

	
	public WorldState world() {
		return this.state;
	}

	
	ViewMatrices view() {
		return this.state.view();
	}

	
	public void advance() {
		this.state.advance();
		this.customs.update(this.state, this.state.frameTime());

		// Normally empty, and this is the only place able to say anything: an expression that
		// throws is dropped where it is evaluated, and that side of the line has no logger.
		this.customs.drainProblems().forEach(problem ->
				CoreVK.logger().warn("The pack's own {}", problem));
	}

	
	public void leaveWorld() {
		this.state.leaveWorld();
	}

	
	public List<String> notes() {
		List<String> notes = new ArrayList<>();
		if (!this.declared.isEmpty()) {
			notes.add("The pack declares " + this.declared.size() + " values of its own, "
					+ this.customs.exposed().size() + " of which reach a shader");
		}

		this.problems.forEach(problem -> notes.add("Dropped the pack's own " + problem));
		this.storageImages.images().forEach(image -> notes.add("storage image " + image.describe()));
		this.storageImages.dropped().forEach(dropped -> notes.add("Dropped " + dropped));
		this.bufferObjects.buffers().forEach(buffer -> notes.add("storage buffer " + buffer.describe()));
		this.bufferObjects.dropped().forEach(dropped -> notes.add("Dropped " + dropped));
		notes.addAll(this.packImages.notes());

		return notes;
	}

	
	public Gaps classify(List<String> unanswered) {
		List<String> engine = new ArrayList<>();
		List<String> pack = new ArrayList<>();
		List<String> nobody = new ArrayList<>();

		for (String name : unanswered) {
			if (this.declared.contains(name)) {
				pack.add(name);
			} else if (UniformGaps.unanswerable(name) != null) {
				nobody.add(name);
			} else {
				engine.add(name);
			}
		}

		return new Gaps(List.copyOf(engine), List.copyOf(pack), List.copyOf(nobody));
	}

	
	public static Map<String, List<String>> standIns(List<String> members) {
		Map<String, List<String>> named = new LinkedHashMap<>();
		for (String member : members) {
			String reason = UniformGaps.standIn(member);
			if (reason != null) {
				named.computeIfAbsent(reason, _ -> new ArrayList<>()).add(member);
			}
		}

		// Not Map.copyOf, which shuffles its iteration order differently on every run: the groups
		// are printed, and two launches of the same jar must print them in the members' order both
		// times or the journals stop comparing.
		named.replaceAll((_, names) -> List.copyOf(names));

		return Collections.unmodifiableMap(named);
	}

	
	private void readNoise(ShaderProperties properties, ShaderPackSource source,
			Map<String, String> defines) {
		String path = properties.noiseTexturePath(defines).orElse(null);
		if (path == null) {
			return;
		}

		Optional<Path> file = source.file(path);
		if (file.isEmpty()) {
			this.problems.add("noise image: texture.noise names " + path
					+ " and no such file is in the pack, so the generated field stands in");
			return;
		}

		try {
			this.noiseImage = NoiseTexture.decode(source.bytes(file.get()));
		} catch (IOException | RuntimeException e) {
			this.problems.add("noise image " + path + ": " + e.getMessage()
					+ ", so the generated field stands in");
		}
	}

	private void declare(ShaderProperties properties, Map<String, String> defines) {
		CustomUniforms.Builder builder = CustomUniforms.builder();
		for (ShaderProperties.CustomUniform line : properties.customUniforms(defines)) {
			this.declared.add(line.name());
			builder.declare(line.name(), line.type(), line.expression(), line.exposed());
		}

		this.customs = builder.build(UniformCatalog.engine(), this.problems);
		this.catalog = this.customs.layerOn(UniformCatalog.engine());
		this.geometry = this.customs.layerOn(UniformCatalog.geometry());
		this.shadowGeometry = this.customs.layerOn(UniformCatalog.shadowGeometry());
	}

	
	public record Gaps(List<String> engine, List<String> pack, List<String> nobody) {
	}
}
