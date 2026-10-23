package net.corevk.corevk.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import org.lwjgl.sdl.SDLDialog;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDLProperties;
import org.lwjgl.sdl.SDL_DialogFileCallback;
import org.lwjgl.sdl.SDL_DialogFileFilter;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class FileDialog {

	private static final String FILTER = "txt";
	private static final String FILTER_LABEL = "Shader Pack Settings (.txt)";

	private static final Map<Long, CompletableFuture<Optional<Path>>> WAITING =
			new ConcurrentHashMap<>();

	private static final AtomicLong NEXT = new AtomicLong(1);

	public enum Kind {
		OPEN, SAVE
	}

	private FileDialog() {
	}

	public static CompletableFuture<Optional<Path>> choose(Kind kind, String title, Path origin) {
		CompletableFuture<Optional<Path>> answer = new CompletableFuture<>();
		long question = NEXT.getAndIncrement();

		WAITING.put(question, answer);

		try {
			ask(kind, title, origin, question);
		} catch (RuntimeException | LinkageError e) {

			WAITING.remove(question);
			answer.completeExceptionally(e);
		}

		return answer;
	}

	private static void ask(Kind kind, String title, Path origin, long question) {

		RenderSystem.assertOnRenderThread();

		SDL_DialogFileFilter.Buffer filters = Kept.FILTERS;
		long window = Minecraft.getInstance().getWindow().handle();

		int properties = SDLProperties.SDL_CreateProperties();
		if (properties == 0) {
			throw new IllegalStateException(
					"SDL could not make a file dialog: " + SDLError.SDL_GetError());
		}

		try {
			SDLProperties.SDL_SetPointerProperty(properties,
					SDLDialog.SDL_PROP_FILE_DIALOG_WINDOW_POINTER, window);
			SDLProperties.SDL_SetPointerProperty(properties,
					SDLDialog.SDL_PROP_FILE_DIALOG_FILTERS_POINTER, filters.address());
			SDLProperties.SDL_SetNumberProperty(properties,
					SDLDialog.SDL_PROP_FILE_DIALOG_NFILTERS_NUMBER, filters.remaining());
			SDLProperties.SDL_SetStringProperty(properties,
					SDLDialog.SDL_PROP_FILE_DIALOG_TITLE_STRING, title);

			String at = origin.toAbsolutePath().toString();
			SDLProperties.SDL_SetStringProperty(properties,
					SDLDialog.SDL_PROP_FILE_DIALOG_LOCATION_STRING, at);

			int type = kind == Kind.SAVE
					? SDLDialog.SDL_FILEDIALOG_SAVEFILE
					: SDLDialog.SDL_FILEDIALOG_OPENFILE;
			SDLDialog.SDL_ShowFileDialogWithProperties(type, Kept.CALLBACK, question, properties);
		} finally {
			SDLProperties.SDL_DestroyProperties(properties);
		}
	}

	private static void answered(long question, long files, int filter) {
		CompletableFuture<Optional<Path>> answer = WAITING.remove(question);
		if (answer == null) {
			return;
		}

		try {
			if (files == MemoryUtil.NULL) {
				answer.completeExceptionally(new IllegalStateException(
						"SDL could not show a file dialog: " + SDLError.SDL_GetError()));

				return;
			}

			long first = MemoryUtil.memGetAddress(files);
			answer.complete(first == MemoryUtil.NULL
					? Optional.empty()
					: Optional.of(Paths.get(MemoryUtil.memUTF8(first))));
		} catch (RuntimeException e) {
			answer.completeExceptionally(e);
		}
	}

	private static final class Kept {

		static final SDL_DialogFileCallback CALLBACK =
				SDL_DialogFileCallback.create(FileDialog::answered);

		private static final ByteBuffer LABEL = MemoryUtil.memUTF8(FILTER_LABEL);
		private static final ByteBuffer PATTERN = MemoryUtil.memUTF8(FILTER);

		static final SDL_DialogFileFilter.Buffer FILTERS = SDL_DialogFileFilter.calloc(1);

		static {
			FILTERS.get(0).set(LABEL, PATTERN);
		}

		private Kept() {
		}
	}
}
