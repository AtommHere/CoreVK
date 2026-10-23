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

		// Nothing of DH's held across a frame DH did not draw, for the reason the drawn field
		// gives. This line stands between the two halves of a frame rather than after both, that
		// being where the game calls this engine back, so a DH that goes quiet is let go of on the
		// second frame after rather than the first; what it cannot do is hold on for the session.
		if (!drawn) {
			forget();
		}
		drawn = false;

		try {
			// Thrown BEFORE the substitution and required rather than best effort: without it DH
			// draws its water half inside its FIRST pass, at the head of the game's opaque chunk
			// group (core/render/renderer/LodRenderer.java:258-263 under a false
			// getDeferTransparentRendering), so the half this engine records for the far side of
			// the deferred stage would be recorded before that stage has run, and the depth taken
			// as dhDepthTex1 would already carry the water. CoreVK throws the same switch for the
			// same reason (compat/dh/LodRendererEvents.java:92). Null while DH is still starting,
			// and then nothing is substituted this frame either: the two go together or not at all.
			if (!deferred && !defer()) {
				return;
			}

			Object standing = rendererField.get(lodRenderer);
			if (standing != substitute) {
				// Null while DH has not bound its renderers yet, which is every frame before the
				// first one it draws. Keeping null would lose DH's own renderer for good, so the
				// substitution waits for something to fall back on.
				if (standing == null) {
					return;
				}

				original = standing;
				rendererField.set(lodRenderer, substitute);
			}

			// After the substitution and never instead of it: what it holds off is a cost, so a
			// road to it that cannot be walked leaves the far terrain served all the same. Says so
			// itself and throws nothing back here.
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

			// Latched on having reached them rather than on their having taken, so that whichever
			// DID take is handed back later. A switch forced and then forgotten would leave a
			// player's own menu answering for this engine long after it stopped drawing.
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
			// hold sets the two switches one after the other, so a throw between them has already
			// forced the first with muted still false, and nothing later would ever hand it back.
			// Clearing returns whatever DID take, and a second throw changes nothing.
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

		// Every step is asked of the type DECLARED at the step above it, which is an interface of
		// DH's published API, rather than of the object in hand: the classes behind them are its
		// own and need not be public for this to reach them.
		Method graphics = field.getType().getMethod("graphics");
		Object graphicsConfig = graphics.invoke(configs);

		Method ambientOcclusion = graphics.getReturnType().getMethod("ambientOcclusion");
		Method fog = graphics.getReturnType().getMethod("fog");

		// The fog switch is the one DH points at rather than the one CoreVK still uses: its drawMode
		// is deprecated in favour of this since API 4.0.0 and reaches the same config entry through
		// a converter (core/api/external/methods/config/client/DhApiFogConfig.java:58-62), which is
		// the entry LodRenderer reads the API's answer out of (render/renderer/LodRenderer.java:189).
		Method enabled = ambientOcclusion.getReturnType().getMethod("enabled");
		Method dhFog = fog.getReturnType().getMethod("enableDhFog");

		// Both are declared on the one config value interface, which is the type the setters are
		// asked of.
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
				// DH has not published its config yet; asked again on the next frame.
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
			// The ordinary case: DH is simply not installed.
			cloudsHoldable = false;
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			// The road is closed below and every later call returns at once, the hand-back at
			// release among them, so a switch still held would stay held for the session with the
			// player's own menu locked on it. It is handed back first, as mute hands its two back,
			// and a second throw changes nothing.
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

			// The switch is published API, so the road to it is short: the delayed holder and the
			// one setter on the proxy's interface.
			renderProxyField = Class.forName(DELAYED).getField("renderProxy");
			deferTransparentMethod = renderProxyField.getType()
					.getMethod("setDeferTransparentRendering", boolean.class);

			resolveGeometry();

			substitute = Proxy.newProxyInstance(DhLods.class.getClassLoader(),
					new Class<?>[] { rendererType }, new Handler());

			usable = true;
			// And the second half of that sentence, because standing in DH's place takes something
			// away as well: DH's own pass fires one DhApiBeforeBufferRenderEvent per buffer
			// (common/render/blaze/BlazeDhTerrainRenderer.java:313-323) and this substitute records
			// the draws itself, so the event stays quiet for a HALF the pack really draws. The
			// granularity is the half and not the frame: a half handed back to DH, no program for
			// it, a broken chain or empty sections (DistantDraw.draw answering false), goes through
			// DH's own renderer and fires the event as ever, and so does everything while install
			// has not landed, this line being logged before either of install's early returns.
			// Said rather than fired: what an outside consumer wants from it is DH's own draw
			// about to happen, and none is about to happen for a half the pack has taken.
			CoreVK.logger().info("Distant Horizons found, its far terrain will be read where that "
					+ "mod hands it to its own renderer; that mod's own per buffer render event "
					+ "stays quiet for a half a pack draws, and still fires for one handed back");
		} catch (ClassNotFoundException e) {
			// The ordinary case: DH is simply not installed. Not worth a line above debug.
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
		// Widened to the types the walk really holds, an Object and an int, so that invokeExact
		// there matches without a cast either side of it.
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
					// Drawn by the pack, or handed straight back to DH below. There is no third answer
					// and no half of one: a pass this engine records and then lets DH record again
					// would draw the far terrain twice, once lit by each engine.
					if (DistantDraw.draw(opaque, sections)) {
						return null;
					}
				} catch (GpuDeviceLossException e) {
					throw e;
				} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
					usable = false;
					// Handed back for good and not merely stopped: this proxy stands in front of DH's
					// own renderer, so a failure that left it standing would go on paying for a
					// reflective hop on every buffer of every frame for nothing.
					restore();
					CoreVK.logger().warn("Distant Horizons' far terrain cannot be read out of the "
							+ "pass it is drawn in, so that mod keeps drawing it with its own shader "
							+ "for the rest of this session", e);
				}
			}

			try {
				return method.invoke(original, args);
			} catch (InvocationTargetException e) {
				// Unwrapped, or the proxy hands DH's own failure back to DH wrapped in one of ours.
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
			// Nothing left to try: the field that was written once cannot be written back. Said at
			// debug alone, the line that brought us here having already told the reader what is lost.
			CoreVK.logger().debug("Distant Horizons' own far terrain renderer cannot be put back", e);
		}

		// And its own frame order with it, so a DH handed back draws exactly as it does where this
		// engine does not stand in the way. CoreVK clears the same switch when it stops overriding
		// (compat/dh/LodRendererEvents.java:92 setting it from a live condition).
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

		// And its own two post passes, which are worth having again the moment their image is the
		// one on screen. Handed back to the PLAYER rather than set true: what the menu says is his,
		// and this engine only ever stood in front of it.
		if (muted) {
			try {
				// Cleared only where the clearing really ran: a throw leaves the debt standing,
				// and so does UNREACHED, so the next restore tries again rather than forgetting
				// that a switch of the player's is still overridden.
				if (hold(null) != UNREACHED) {
					muted = false;
				}
			} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
				CoreVK.logger().debug("Distant Horizons' own ambient occlusion and fog cannot be "
						+ "handed back", e);
			}
		}

		// And the reading of DH's listing, because DH is free to drop what is in it the moment it has
		// its own renderer back: a session that stops drawing a pack would otherwise hold a far
		// view's worth of containers, and the buffers under them, until it draws one again.
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

		// Under the switch both halves are read again whether the listing moved or not, so that a
		// frame reading it and a frame reusing what it read come off ONE jar. What the switch cannot
		// put back is the comparison above, which is what records the listing: the two readings
		// therefore differ by the reading alone, and the comparison's own price, one member read a
		// section that allocates nothing against the dozen reads and the five objects the reading
		// costs, is on both sides.
		if (moved || PassTimings.keepRedoneWork()) {
			opaqueSections = null;
			translucentSections = null;
		}

		List<Section> kept = opaque ? opaqueSections : translucentSections;
		if (kept == null) {
			int dropped = drops;
			kept = build(count, opaque);

			// Kept only where the walk left the road as it found it, which is what the count is
			// there for: checkStride closes the road from inside build and forgets on its way out,
			// and this answer written over that would hold what the forgetting just let go of.
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
			// A handle hands back whatever its target threw, which is anything at all, where every
			// caller up to the proxy is written around the three this class can meet. DH's own
			// member reader declares none of its own, so this is the road nothing walks.
			throw new InvocationTargetException(e);
		}

		if (moved) {
			// Nothing of DH's held past the listing that named it: a far view walked away from would
			// otherwise keep its containers, and the buffers under them, for the whole session.
			Arrays.fill(listed, count, listed.length, null);
			listedCount = count;
		}

		return moved;
	}

	
	private static List<Section> build(int count, boolean opaque)
			throws ReflectiveOperationException {
		Object[] containers = listed;

		// Given the width DH just answered with rather than grown into it. A far view hands out
		// thousands of sections, and a list that starts at ten reaches that by allocating a longer
		// array and copying the old one into it a dozen times over.
		List<Section> sections = new ArrayList<>(count);

		// One list refilled section by section rather than one built per section: the record copies
		// what it is handed, so nothing downstream can be holding this one, and a far view hands out
		// thousands of sections.
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

				// Asked once and kept: the read below it is reflective like every other here, and it
				// answered the same number twice for every buffer of every section of every frame so
				// that one check could have it on the one frame a session where it looks.
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
				// Handed the working list, not a copy of it: the record's own constructor copies, so
				// copying here made the same array twice.
				sections.add(new Section((Integer) cornerX.invoke(corner),
						(Integer) cornerY.invoke(corner), (Integer) cornerZ.invoke(corner), pieces));
			}
		}

		// Counted where it is really built, which is what makes the count worth reading: a frame that
		// reused an answer adds nothing here, so a still camera on a settled far view reads nought.
		PassTimings.censusFarSections(sections.size());

		// Handed over as it stands rather than copied. The copy was one more array as wide as the far
		// view, twice a frame, and it bought nothing: this list is built here and is not the reused
		// one above it. What holds it afterwards reads it and never writes it, which is what lets the
		// same list answer several frames: {@code DistantDraw} keeps it for the light's own stage at
		// the tail of the frame and puts a fresh one in its place.
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
