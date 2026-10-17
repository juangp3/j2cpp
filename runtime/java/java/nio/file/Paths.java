package java.nio.file;

public final class Paths {
	private Paths() {
	}

	public static Path get(String first, String... more) {
		String path;
		if (more.length == 0) {
			path = first;
		} else {
			StringBuilder sb = new StringBuilder();
			sb.append(first);
			for (String segment : more) {
				if (!segment.isEmpty()) {
					if (sb.length() > 0) {
						sb.append('/');
					}
					sb.append(segment);
				}
			}
			path = sb.toString();
		}
		return new UnixPath(UnixPath.normalizeAndCheck(path));
	}
}
