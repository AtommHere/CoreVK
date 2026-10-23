package net.corevk.corevk.pack.source;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

public final class SourceMentions {

	
	private static final String PASTING = "##";

	private final Set<String> found;

	private final boolean pasting;

	private SourceMentions(Set<String> found, boolean pasting) {
		this.found = Set.copyOf(found);
		this.pasting = pasting;
	}

	
	public static SourceMentions of(ShaderPackSource source, Set<String> names) throws IOException {
		Set<String> found = new LinkedHashSet<>();
		boolean pasting = false;

		for (Path file : source.sourceFiles()) {
			for (String line : source.readLines(file)) {
				if (line.contains(PASTING)) {
					pasting = true;
				}

				for (String name : names) {
					if (line.contains(name)) {
						found.add(name);
					}
				}
			}

			// Only once everything is known: the pasting flag makes every name answer yes, so
			// leaving early on a full set would miss a paste in a file further down and hand back an
			// answer narrower than the pack deserves.
			if (found.size() == names.size() && pasting) {
				break;
			}
		}

		if (found.size() == names.size()) {
			return new SourceMentions(found, pasting);
		}

		for (Path file : source.otherFiles()) {
			// Empty for anything that is not text, a pack's own textures included, and decided on
			// the head of the file rather than on its name.
			String text = source.searchableText(file);
			for (String name : names) {
				if (text.contains(name)) {
					found.add(name);
				}
			}

			if (found.size() == names.size()) {
				break;
			}
		}

		return new SourceMentions(found, pasting);
	}

	
	public static SourceMentions unread() {
		return new SourceMentions(Set.of(), true);
	}

	
	public boolean maybe(String name) {
		return this.pasting || this.found.contains(name);
	}
}
