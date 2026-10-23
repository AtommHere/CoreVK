package net.corevk.corevk.pack.source;

public final class BlockComments {

	private BlockComments() {
	}

	
	public static boolean openAfter(String line, boolean commented) {
		for (int at = 0; at < line.length() - 1; at++) {
			if (commented) {
				if (line.charAt(at) == '*' && line.charAt(at + 1) == '/') {
					commented = false;
					at++;
				}
			} else if (line.charAt(at) == '/' && line.charAt(at + 1) == '/') {
				return false;
			} else if (line.charAt(at) == '/' && line.charAt(at + 1) == '*') {
				commented = true;
				at++;
			}
		}

		return commented;
	}
}
