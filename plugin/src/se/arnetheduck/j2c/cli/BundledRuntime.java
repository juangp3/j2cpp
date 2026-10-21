package se.arnetheduck.j2c.cli;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.CodeSource;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * The J2C runtime packaged in the CLI JAR, selected with --runtime bundled.
 *
 * Maven copies runtime/ (java/, cpp/, required-classes.txt, api.txt and
 * README.md) into the JAR under META-INF/j2c-runtime/. Translation needs it
 * as a directory, so it is extracted into J2C's cache directory
 * (J2C_CACHE_DIR, else ~/.j2c/cache) as runtime-&lt;version&gt;-&lt;hash&gt;,
 * the hash being a SHA-256 of the packaged files' names and contents.
 *
 * An extracted copy is used only while its files are exactly the packaged
 * ones (no file missing, changed or added); otherwise it is replaced. The
 * files are written to a new temporary directory next to it, which is then
 * renamed into place atomically, so concurrent processes never see a
 * half-written runtime: the first rename wins, and the others delete their
 * copies and use the winner's.
 */
final class BundledRuntime {
	/** The --runtime value that selects it */
	static final String NAME = "bundled";
	/** Where the runtime's files are inside the JAR */
	static final String PREFIX = "META-INF/j2c-runtime/";
	/** Written into each extracted copy, last */
	static final String MARKER = ".j2c-runtime";
	/** Temporary and replaced copies left by killed processes */
	private static final long STALE_MILLIS = 60L * 60 * 1000;

	/** The packaged files, read once: path below PREFIX to contents */
	private static Map<String, byte[]> files;
	/** runtime-<version>-<hash> */
	private static String name;
	/** The directories this process extracted or verified */
	private static final Set<File> directories = Collections
			.synchronizedSet(new HashSet<File>());

	private BundledRuntime() {
	}

	/** The extracted runtime, extracting or repairing it first if needed */
	static File directory() throws IOException {
		return directory(ProjectSetup.cacheDirectory());
	}

	static synchronized File directory(File cache) throws IOException {
		load();
		File target = new File(cache, name).getAbsoluteFile();
		if (!matches(target)) {
			target = extract(cache.getAbsoluteFile(), target);
		}
		directories.add(target);
		return target;
	}

	/** Whether directory() returned this directory in this process */
	static boolean isBundled(File directory) {
		return directory != null
				&& directories.contains(directory.getAbsoluteFile());
	}

	private static File extract(File cache, File target) throws IOException {
		Files.createDirectories(cache.toPath());
		removeStale(cache);
		Path temp = Files.createTempDirectory(cache.toPath(), name + ".tmp-");
		try {
			write(temp.toFile());
			for (int attempt = 0; attempt < 5; ++attempt) {
				try {
					Files.move(temp, target.toPath(),
							StandardCopyOption.ATOMIC_MOVE);
					temp = null;
					return target;
				} catch (IOException e) {
					// Already there (FileAlreadyExistsException,
					// DirectoryNotEmptyException, or AccessDeniedException on
					// Windows): another process may have just installed it
				}
				if (matches(target)) {
					return target;
				}
				// A damaged copy: move it aside in one step, so no process
				// reads a directory that is being deleted, then try again
				File aside = new File(cache, name + ".old-" + UUID.randomUUID());
				try {
					Files.move(target.toPath(), aside.toPath(),
							StandardCopyOption.ATOMIC_MOVE);
				} catch (IOException e) {
					// Moved by another process, or in use on Windows
				}
				deleteTree(aside);
				// Give a virus scanner or indexer holding files time to let go
				try {
					Thread.sleep(50L * (attempt + 1));
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					break;
				}
			}
			// Still not replaceable (files in use on Windows): use this
			// process's own complete copy
			File own = temp.toFile();
			temp = null;
			return own;
		} finally {
			if (temp != null) {
				deleteTree(temp.toFile());
			}
		}
	}

	/** Writes the packaged files, then the marker */
	private static void write(File directory) throws IOException {
		for (Map.Entry<String, byte[]> file : files.entrySet()) {
			File target = new File(directory, file.getKey());
			Files.createDirectories(target.getParentFile().toPath());
			Files.write(target.toPath(), file.getValue());
		}
		Files.write(new File(directory, MARKER).toPath(), ("J2C "
				+ J2cService.version() + " runtime, extracted from the CLI JAR "
				+ "for --runtime bundled. J2C replaces this directory when its "
				+ "files change.\n").getBytes(StandardCharsets.UTF_8));
	}

	/** Whether the directory holds exactly the packaged files */
	private static boolean matches(File directory) {
		if (!new File(directory, MARKER).isFile()) {
			return false;
		}
		Set<String> found = new TreeSet<String>();
		list(directory, "", found);
		found.remove(MARKER);
		if (!found.equals(files.keySet())) {
			return false;
		}
		try {
			for (Map.Entry<String, byte[]> file : files.entrySet()) {
				File copy = new File(directory, file.getKey());
				if (copy.length() != file.getValue().length
						|| !Arrays.equals(Files.readAllBytes(copy.toPath()),
								file.getValue())) {
					return false;
				}
			}
		} catch (IOException e) {
			return false;
		}
		return true;
	}

	/** Deletes temporary and replaced copies more than an hour old */
	private static void removeStale(File cache) {
		File[] entries = cache.listFiles();
		if (entries == null) {
			return;
		}
		long now = System.currentTimeMillis();
		for (File entry : entries) {
			String entryName = entry.getName();
			if (entryName.startsWith("runtime-")
					&& (entryName.contains(".tmp-") || entryName.contains(".old-"))
					&& now - entry.lastModified() > STALE_MILLIS) {
				deleteTree(entry);
			}
		}
	}

	private static void load() throws IOException {
		if (files != null) {
			return;
		}
		Map<String, byte[]> loaded = new TreeMap<String, byte[]>();
		File source = codeSource();
		if (source != null && source.isDirectory()) {
			Set<String> paths = new TreeSet<String>();
			File root = new File(source, PREFIX);
			list(root, "", paths);
			for (String path : paths) {
				loaded.put(path, Files.readAllBytes(new File(root, path)
						.toPath()));
			}
		} else if (source != null && source.isFile()) {
			JarFile jar = new JarFile(source);
			try {
				Enumeration<JarEntry> entries = jar.entries();
				while (entries.hasMoreElements()) {
					JarEntry entry = entries.nextElement();
					if (entry.isDirectory()
							|| !entry.getName().startsWith(PREFIX)) {
						continue;
					}
					InputStream in = jar.getInputStream(entry);
					try {
						loaded.put(entry.getName().substring(PREFIX.length()),
								in.readAllBytes());
					} finally {
						in.close();
					}
				}
			} finally {
				jar.close();
			}
		}
		if (!loaded.containsKey("java/java/lang/Object.java")) {
			throw new IllegalArgumentException("This J2C build has no bundled "
					+ "runtime (--runtime bundled); pass a runtime directory "
					+ "instead");
		}
		files = loaded;
		name = "runtime-" + J2cService.version().replaceAll("[^A-Za-z0-9._-]+",
				"-") + "-" + hash(loaded);
	}

	/** The JAR or class directory this class was loaded from, or null */
	private static File codeSource() {
		CodeSource code = BundledRuntime.class.getProtectionDomain()
				.getCodeSource();
		URL location = code == null ? null : code.getLocation();
		if (location == null) {
			return null;
		}
		try {
			return new File(location.toURI());
		} catch (URISyntaxException | IllegalArgumentException e) {
			return null;
		}
	}

	/** The first 16 hex digits of a SHA-256 of the names and contents */
	private static String hash(Map<String, byte[]> contents) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			for (Map.Entry<String, byte[]> file : contents.entrySet()) {
				digest.update((file.getKey() + "\n" + file.getValue().length
						+ "\n").getBytes(StandardCharsets.UTF_8));
				digest.update(file.getValue());
			}
			byte[] hash = digest.digest();
			StringBuilder hex = new StringBuilder();
			for (int i = 0; i < 8; ++i) {
				hex.append(String.format("%02x", hash[i] & 0xff));
			}
			return hex.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	/** Files below a directory, as paths with forward slashes */
	private static void list(File directory, String prefix, Set<String> paths) {
		File[] entries = directory.listFiles();
		if (entries == null) {
			return;
		}
		for (File entry : entries) {
			if (entry.isDirectory()) {
				list(entry, prefix + entry.getName() + "/", paths);
			} else {
				paths.add(prefix + entry.getName());
			}
		}
	}

	/** Deletes a directory tree, without following symbolic links */
	private static void deleteTree(File file) {
		File[] entries = Files.isSymbolicLink(file.toPath()) ? null : file
				.listFiles();
		if (entries != null) {
			for (File entry : entries) {
				deleteTree(entry);
			}
		}
		file.delete();
	}
}
