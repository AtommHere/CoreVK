package net.corevk.corevk.screen;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class FileDialog {

	
	private static final ExecutorService DIALOGS = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "CoreVK file dialog");
		// Daemon, so that a dialog nobody answered cannot keep the game from closing.
		thread.setDaemon(true);

		return thread;
	});

	
	private static final String FILTER = "*.txt";
	private static final String FILTER_LABEL = "Shader Pack Settings (.txt)";

	public enum Kind {
		OPEN, SAVE
	}

	private FileDialog() {
	}

	
	public static CompletableFuture<Optional<Path>> choose(Kind kind, String title, Path origin) {
		CompletableFuture<Optional<Path>> answer = new CompletableFuture<>();

		DIALOGS.submit(() -> {
			try {
				answer.complete(Optional.ofNullable(ask(kind, title, origin)).map(Paths::get));
			} catch (RuntimeException | LinkageError e) {
				// A platform with no dialog to offer is not a broken screen, so the failure goes back
				// through the future and the caller says it once rather than throwing on this thread,
				// where nothing would see it.
				answer.completeExceptionally(e);
			}
		});

		return answer;
	}

	private static String ask(Kind kind, String title, Path origin) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			PointerBuffer filters = stack.mallocPointer(1);
			filters.put(stack.UTF8(FILTER));
			filters.flip();

			String at = origin.toAbsolutePath().toString();

			return kind == Kind.SAVE
					? TinyFileDialogs.tinyfd_saveFileDialog(title, at, filters, FILTER_LABEL)
					: TinyFileDialogs.tinyfd_openFileDialog(title, at, filters, FILTER_LABEL, false);
		}
	}
}
