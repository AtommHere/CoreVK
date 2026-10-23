package net.corevk.corevk.dh;

import net.corevk.corevk.render.DistantDraw;
import net.corevk.corevk.render.DistantMesh;
import net.corevk.corevk.render.timing.PassTimings;
import net.corevk.corevk.CoreVK;

import com.mojang.blaze3d.GpuDeviceLossException;
import com.mojang.blaze3d.buffers.GpuBuffer;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class DhLods {

	private static final String TERRAIN_RENDERER =
			"com.seibel.distanthorizons.core.wrapperInterfaces.render.renderPass.IDhTerrainRenderer";

	private static final String LOD_RENDERER =
			"com.seibel.distanthorizons.core.render.renderer.LodRenderer";

	private static final String BUFFER_CONTAINER = "com.seibel.distanthorizons.core.dataObjects."
			+ "render.bufferBuilding.LodBufferContainer";

	private static final String SORTED_SET =
			"com.seibel.distanthorizons.core.util.objects.SortedArraySet";

	private static final String BUFFER_WRAPPER = "com.seibel.distanthorizons.common.render.blaze."
			+ "wrappers.buffer.BlazeVertexBufferWrapper";

	private static final String DELAYED = "com.seibel.distanthorizons.api.DhApi$Delayed";

	private static final int SWITCHES = 2;
	private static final int UNREACHED = -1;

	private static final Object[] NOTHING = new Object[0];

	private static boolean resolved;

	private static volatile boolean usable;

	public static boolean usable() {
		return usable;
	}

	private static Field renderProxyField;
	private static Method deferTransparentMethod;

	private static boolean deferred;

	private static boolean muted;

	private static boolean mutable = true;

	private static boolean cloudsHeld;

	private static boolean cloudsHoldable = true;

	private static Object original;

	private static Object substitute;

	private static Object lodRenderer;
	private static Field rendererField;

	private static Field opaqueBuffersField;
	private static Field translucentBuffersField;
	private static Field cornerField;
	private static Field vertexBufferField;
	private static Method indexBufferMethod;
	private static Field indexCountField;
	private static Field vertexCountField;
	private static Field uploadedField;

	private static Method setSize;

	private static MethodHandle setGet;

	private static Method cornerX;
	private static Method cornerY;
	private static Method cornerZ;

	private static boolean reported;

	private static boolean strideChecked;

	private static Object[] listed = NOTHING;

	private static int listedCount;

	private static List<Section> opaqueSections;
	private static List<Section> translucentSections;

	private static int drops;

	private static boolean drawn;

	private DhLods() {
	}

	public static void install() {
		if (!resolved) {
			resolve();
		}

		if (!usable) {
			return;
		}

		if (!drawn) {
			forget();
		}
		drawn = false;

		try {

			if (!deferred && !defer()) {
				return;
			}

			Object standing = rendererField.get(lodRenderer);
			if (standing != substitute) {

				if (standing == null) {
					return;
				}

				original = standing;
				rendererField.set(lodRenderer, substitute);
			}

			mute();
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			usable = false;
			CoreVK.logger().warn("Distant Horizons' far terrain cannot be taken over, so that mod "
					+ "keeps drawing it with its own shader for the rest of this session", e);
		}
	}

	private static boolean defer() throws ReflectiveOperationException {
		Object proxy = renderProxyField.get(null);
		if (proxy == null) {
			return false;
		}

		deferTransparentMethod.invoke(proxy, true);
		deferred = true;
		CoreVK.logger().info("Distant Horizons defers its water half behind the deferred stage, "
				+ "which is where the pack's own dh_water pass stands");

		return true;
	}

	private static void mute() {
		if (muted || !mutable) {
			return;
		}

		try {
			int held = hold(Boolean.FALSE);
			if (held == UNREACHED) {
				return;
			}

			muted = true;
			if (held == SWITCHES) {
				CoreVK.logger().info("Distant Horizons holds off its own ambient occlusion and "
						+ "fog, both of which draw over an image this engine never reads");
			} else {
				CoreVK.logger().info("Distant Horizons lets {} of its {} post passes be held off "
						+ "through its API, so it keeps drawing the rest over an image this engine "
						+ "never reads", held, SWITCHES);
			}
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {

			try {
				hold(null);
			} catch (ReflectiveOperationException | RuntimeException | LinkageError swallowed) {
				e.addSuppressed(swallowed);
			}

			mutable = false;
			CoreVK.logger().info("Distant Horizons keeps drawing its own ambient occlusion and fog "
					+ "over an image this engine never reads, which costs a frame and shows "
					+ "nothing: {}", e.toString());
		}
	}

	private static int hold(Boolean value) throws ReflectiveOperationException {
		Field field = Class.forName(DELAYED).getField("configs");
		Object configs = field.get(null);
		if (configs == null) {
			return UNREACHED;
		}

		Method graphics = field.getType().getMethod("graphics");
		Object graphicsConfig = graphics.invoke(configs);

		Method ambientOcclusion = graphics.getReturnType().getMethod("ambientOcclusion");
		Method fog = graphics.getReturnType().getMethod("fog");

		Method enabled = ambientOcclusion.getReturnType().getMethod("enabled");
		Method dhFog = fog.getReturnType().getMethod("enableDhFog");

		Class<?> configValue = enabled.getReturnType();

		int held = 0;
		for (Object one : List.of(enabled.invoke(ambientOcclusion.invoke(graphicsConfig)),
				dhFog.invoke(fog.invoke(graphicsConfig)))) {
			if (set(configValue, one, value)) {
				held++;
			}
		}

		return held;
	}

	private static boolean set(Class<?> configValue, Object one, Boolean value)
			throws ReflectiveOperationException {
		if (value == null) {
			return Boolean.TRUE.equals(configValue.getMethod("clearValue").invoke(one));
		}

		Method named;
		try {
			named = configValue.getMethod("setValue", Object.class, String.class);
		} catch (NoSuchMethodException e) {
			return Boolean.TRUE.equals(
					configValue.getMethod("setValue", Object.class).invoke(one, value));
		}

		return Boolean.TRUE.equals(named.invoke(one, value, CoreVK.MOD_NAME));
	}

	public static void clouds(boolean avoid) {
		if (avoid == cloudsHeld || !cloudsHoldable) {
			return;
		}

		try {
			Boolean took = holdClouds(avoid ? Boolean.FALSE : null);
			if (took == null) {

				return;
			}

			cloudsHeld = avoid;
			if (avoid) {
				CoreVK.logger().info(took
						? "Distant Horizons holds off its own clouds, which the pack asks to keep out "
								+ "of its sky"
						: "Distant Horizons does not let its own clouds be held off through its API, so "
								+ "it keeps drawing them over a sky whose pack asked to keep them out");
			}
		} catch (ClassNotFoundException e) {

			cloudsHoldable = false;
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {

			try {
				holdClouds(null);
			} catch (ReflectiveOperationException | RuntimeException | LinkageError swallowed) {
				e.addSuppressed(swallowed);
			}

			cloudsHeld = false;
			cloudsHoldable = false;
			CoreVK.logger().info("Distant Horizons keeps drawing its own clouds whatever the pack asks, "
					+ "its cloud switch not being one this engine can reach: {}", e.toString());
		}
	}

	private static Boolean holdClouds(Boolean value) throws ReflectiveOperationException {
		Field field = Class.forName(DELAYED).getField("configs");
		Object configs = field.get(null);
		if (configs == null) {
			return null;
		}

		Method graphics = field.getType().getMethod("graphics");
		Method genericRendering = graphics.getReturnType().getMethod("genericRendering");
		Method cloudRendering = genericRendering.getReturnType().getMethod("cloudRenderingEnabled");
		Object one = cloudRendering.invoke(genericRendering.invoke(graphics.invoke(configs)));

		return set(cloudRendering.getReturnType(), one, value);
	}

	public record Section(int x, int y, int z, List<Piece> pieces) {

		public Section {
			pieces = List.copyOf(pieces);
		}
	}

	public record Piece(GpuBuffer vertices, GpuBuffer indices, int indexCount) {
	}

	private static void resolve() {
		resolved = true;

		try {
			Class<?> rendererType = Class.forName(TERRAIN_RENDERER);
			Class<?> lodRendererType = Class.forName(LOD_RENDERER);

			lodRenderer = lodRendererType.getField("INSTANCE").get(null);
			rendererField = lodRendererType.getDeclaredField("terrainRenderer");
			rendererField.setAccessible(true);

			renderProxyField = Class.forName(DELAYED).getField("renderProxy");
			deferTransparentMethod = renderProxyField.getType()
					.getMethod("setDeferTransparentRendering", boolean.class);

			resolveGeometry();

			substitute = Proxy.newProxyInstance(DhLods.class.getClassLoader(),
					new Class<?>[] { rendererType }, new Handler());

			usable = true;

			CoreVK.logger().info("Distant Horizons found, its far terrain will be read where that "
					+ "mod hands it to its own renderer; that mod's own per buffer render event "
					+ "stays quiet for a half a pack draws, and still fires for one handed back");
		} catch (ClassNotFoundException e) {

			usable = false;
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			usable = false;
			CoreVK.logger().info("Distant Horizons is installed but not in a shape its far terrain "
					+ "can be taken out of, so that mod keeps drawing it: {}", e.toString());
		}
	}

	private static void resolveGeometry() throws ReflectiveOperationException {
		Class<?> container = Class.forName(BUFFER_CONTAINER);
		opaqueBuffersField = container.getField("vboOpaqueWrappers");
		translucentBuffersField = container.getField("vboTransparentWrappers");
		cornerField = container.getField("minCornerBlockPos");

		cornerX = cornerField.getType().getMethod("getX");
		cornerY = cornerField.getType().getMethod("getY");
		cornerZ = cornerField.getType().getMethod("getZ");

		Class<?> set = Class.forName(SORTED_SET);
		setSize = set.getMethod("size");

		setGet = MethodHandles.publicLookup().unreflect(set.getMethod("get", int.class))
				.asType(MethodType.methodType(Object.class, Object.class, int.class));

		Class<?> wrapper = Class.forName(BUFFER_WRAPPER);
		vertexBufferField = wrapper.getField("vertexGpuBuffer");
		indexBufferMethod = wrapper.getMethod("getIndexGpuBuffer");
		indexCountField = wrapper.getField("indexCount");
		vertexCountField = wrapper.getField("vertexCount");
		uploadedField = wrapper.getField("uploaded");
	}

	private static final class Handler implements InvocationHandler {

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			if (usable && "render".equals(method.getName()) && args != null && args.length == 4) {
				boolean opaque = (Boolean) args[1];
				try {
					List<Section> sections = sections(args[2], opaque);
					report(sections, opaque);

					if (DistantDraw.draw(opaque, sections)) {
						return null;
					}
				} catch (GpuDeviceLossException e) {
					throw e;
				} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
					usable = false;

					restore();
					CoreVK.logger().warn("Distant Horizons' far terrain cannot be read out of the "
							+ "pass it is drawn in, so that mod keeps drawing it with its own shader "
							+ "for the rest of this session", e);
				}
			}

			try {
				return method.invoke(original, args);
			} catch (InvocationTargetException e) {

				throw e.getCause();
			}
		}
	}

	public static void handBack() {
		clouds(false);

		if (substitute != null && original != null) {
			restore();
		}
	}

	private static void restore() {
		try {
			rendererField.set(lodRenderer, original);
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {

			CoreVK.logger().debug("Distant Horizons' own far terrain renderer cannot be put back", e);
		}

		if (deferred) {
			deferred = false;
			try {
				Object proxy = renderProxyField.get(null);
				if (proxy != null) {
					deferTransparentMethod.invoke(proxy, false);
				}
			} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
				CoreVK.logger().debug("Distant Horizons' deferred water switch cannot be put back", e);
			}
		}

		if (muted) {
			try {

				if (hold(null) != UNREACHED) {
					muted = false;
				}
			} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
				CoreVK.logger().debug("Distant Horizons' own ambient occlusion and fog cannot be "
						+ "handed back", e);
			}
		}

		forget();
	}

	private static void forget() {
		drops++;
		listed = NOTHING;
		listedCount = 0;
		opaqueSections = null;
		translucentSections = null;
	}

	private static List<Section> sections(Object set, boolean opaque)
			throws ReflectiveOperationException {
		drawn = true;

		int count = (Integer) setSize.invoke(set);
		boolean moved = moved(set, count);

		if (moved || PassTimings.keepRedoneWork()) {
			opaqueSections = null;
			translucentSections = null;
		}

		List<Section> kept = opaque ? opaqueSections : translucentSections;
		if (kept == null) {
			int dropped = drops;
			kept = build(count, opaque);

			if (drops == dropped) {
				if (opaque) {
					opaqueSections = kept;
				} else {
					translucentSections = kept;
				}
			}
		}

		return kept;
	}

	private static boolean moved(Object set, int count) throws ReflectiveOperationException {
		boolean moved = count != listedCount;
		if (listed.length < count) {
			listed = new Object[count];
			moved = true;
		}

		try {
			for (int index = 0; index < count; index++) {
				Object one = setGet.invokeExact(set, index);
				if (listed[index] != one) {
					listed[index] = one;
					moved = true;
				}
			}
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable e) {

			throw new InvocationTargetException(e);
		}

		if (moved) {

			Arrays.fill(listed, count, listed.length, null);
			listedCount = count;
		}

		return moved;
	}

	private static List<Section> build(int count, boolean opaque)
			throws ReflectiveOperationException {
		Object[] containers = listed;

		List<Section> sections = new ArrayList<>(count);

		List<Piece> pieces = new ArrayList<>();

		for (int index = 0; index < count; index++) {
			Object one = containers[index];
			Object[] wrappers =
					(Object[]) (opaque ? opaqueBuffersField : translucentBuffersField).get(one);
			if (wrappers == null) {
				continue;
			}

			pieces.clear();
			for (Object wrapper : wrappers) {
				if (wrapper == null || !uploadedField.getBoolean(wrapper)) {
					continue;
				}

				int written = vertexCountField.getInt(wrapper);
				if (written <= 0) {
					continue;
				}

				Object vertices = vertexBufferField.get(wrapper);
				Object indices = indexBufferMethod.invoke(wrapper);
				if (vertices instanceof GpuBuffer vertexBuffer
						&& indices instanceof GpuBuffer indexBuffer
						&& !vertexBuffer.isClosed()) {
					checkStride(vertexBuffer, written);
					pieces.add(new Piece(vertexBuffer, indexBuffer, indexCountField.getInt(wrapper)));
				}
			}

			if (!pieces.isEmpty()) {
				Object corner = cornerField.get(one);

				sections.add(new Section((Integer) cornerX.invoke(corner),
						(Integer) cornerY.invoke(corner), (Integer) cornerZ.invoke(corner), pieces));
			}
		}

		PassTimings.censusFarSections(sections.size());

		return sections;
	}

	private static void report(List<Section> sections, boolean opaque) {
		if (reported || sections.isEmpty()) {
			return;
		}

		reported = true;
		int pieces = sections.stream().mapToInt(one -> one.pieces().size()).sum();
		int indices = sections.stream().flatMap(one -> one.pieces().stream())
				.mapToInt(Piece::indexCount).sum();
		CoreVK.logger().info("Distant Horizons' far terrain is reachable on this backend: {} "
				+ "sections, {} buffers, {} indices in the {} half", sections.size(), pieces, indices,
				opaque ? "opaque" : "translucent");
	}

	private static void checkStride(GpuBuffer vertices, int count) {
		if (strideChecked || count <= 0) {
			return;
		}

		strideChecked = true;
		long stride = vertices.size() / count;
		if (stride != DistantMesh.STRIDE) {
			usable = false;
			restore();
			CoreVK.logger().warn("Distant Horizons writes {} bytes a vertex where this engine's "
					+ "format declares {}, so that mod keeps drawing its far terrain with its own "
					+ "shader for the rest of this session: read through the wrong format, its "
					+ "buffers would draw a landscape out of the wrong bytes", stride,
					DistantMesh.STRIDE);
		}
	}
}
