package java.nio.file;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.NativeIo;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnmappableCharacterException;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * The file operations of Files over POSIX calls. Text is decoded and
 * encoded strictly (malformed input throws), as in the JDK. Symbolic links
 * are always followed (LinkOption is accepted and ignored); copy, move,
 * walk, find, attributes, temporary files and channels are absent;
 * DELETE_ON_CLOSE, SPARSE, SYNC and DSYNC throw
 * UnsupportedOperationException.
 */
public final class Files {
	private Files() {
	}

	private static byte[] bytes(Path path) {
		return NativeIo.path(path.toString());
	}

	/** The JDK's translation of errno into an exception */
	private static IOException translate(int error, Path file) {
		String name = file.toString();
		if (error == NativeIo.ACCESS_DENIED) {
			return new AccessDeniedException(name);
		}
		if (error == NativeIo.NO_SUCH_FILE) {
			return new NoSuchFileException(name);
		}
		if (error == NativeIo.ALREADY_EXISTS) {
			return new FileAlreadyExistsException(name);
		}
		return new FileSystemException(name, null, NativeIo.errorText(error));
	}

	private static int open(Path path, int flags) throws IOException {
		int fd = NativeIo.open(bytes(path), flags);
		if (fd < 0) {
			throw translate(-fd, path);
		}
		return fd;
	}

	public static InputStream newInputStream(Path path, OpenOption... options) throws IOException {
		for (OpenOption option : options) {
			if (option == StandardOpenOption.APPEND || option == StandardOpenOption.WRITE) {
				throw new UnsupportedOperationException("'" + option + "' not allowed");
			}
			checkSupported(option);
		}
		return NativeIo.inputStream(open(path, NativeIo.READ));
	}

	public static OutputStream newOutputStream(Path path, OpenOption... options) throws IOException {
		boolean create = false;
		boolean createNew = false;
		boolean truncate = false;
		boolean append = false;
		if (options.length == 0) {
			create = true;
			truncate = true;
		}
		for (OpenOption option : options) {
			if (option == null) {
				throw new NullPointerException();
			}
			checkSupported(option);
			if (option == StandardOpenOption.READ) {
				throw new IllegalArgumentException("READ not allowed");
			} else if (option == StandardOpenOption.CREATE) {
				create = true;
			} else if (option == StandardOpenOption.CREATE_NEW) {
				createNew = true;
			} else if (option == StandardOpenOption.TRUNCATE_EXISTING) {
				truncate = true;
			} else if (option == StandardOpenOption.APPEND) {
				append = true;
			}
		}
		if (append && truncate) {
			throw new IllegalArgumentException("APPEND + TRUNCATE_EXISTING not allowed");
		}
		int flags = NativeIo.WRITE;
		if (createNew) {
			flags |= NativeIo.CREATE_NEW;
		} else if (create) {
			flags |= NativeIo.CREATE;
		}
		if (truncate) {
			flags |= NativeIo.TRUNCATE;
		}
		if (append) {
			flags |= NativeIo.APPEND;
		}
		return NativeIo.outputStream(open(path, flags));
	}

	private static void checkSupported(OpenOption option) {
		if (option == StandardOpenOption.DELETE_ON_CLOSE || option == StandardOpenOption.SPARSE
				|| option == StandardOpenOption.SYNC || option == StandardOpenOption.DSYNC) {
			throw new UnsupportedOperationException(option + " is not supported by the J2C runtime");
		}
	}

	public static Path createFile(Path path, FileAttribute<?>... attrs) throws IOException {
		int fd = open(path, NativeIo.WRITE | NativeIo.CREATE_NEW);
		NativeIo.close(fd);
		return path;
	}

	public static Path createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
		int result = NativeIo.mkdir(bytes(dir));
		if (result != 0) {
			throw translate(result, dir);
		}
		return dir;
	}

	public static Path createDirectories(Path dir, FileAttribute<?>... attrs) throws IOException {
		try {
			createAndCheckIsDirectory(dir);
			return dir;
		} catch (FileAlreadyExistsException x) {
			throw x;
		} catch (IOException x) {
			// The parent may not exist
		}
		dir = dir.toAbsolutePath();
		Path parent = dir.getParent();
		while (parent != null) {
			if (NativeIo.kind(bytes(parent)) != NativeIo.MISSING) {
				break;
			}
			parent = parent.getParent();
		}
		if (parent == null) {
			throw new FileSystemException(dir.toString(), null, "Unable to determine if root directory exists");
		}
		Path child = parent;
		for (Path name : parent.relativize(dir)) {
			child = child.resolve(name);
			createAndCheckIsDirectory(child);
		}
		return dir;
	}

	private static void createAndCheckIsDirectory(Path dir) throws IOException {
		try {
			createDirectory(dir);
		} catch (FileAlreadyExistsException x) {
			if (!isDirectory(dir)) {
				throw x;
			}
		}
	}

	public static void delete(Path path) throws IOException {
		int result = NativeIo.remove(bytes(path));
		if (result == NativeIo.NOT_EMPTY) {
			throw new DirectoryNotEmptyException(path.toString());
		}
		if (result != 0) {
			throw translate(result, path);
		}
	}

	public static boolean deleteIfExists(Path path) throws IOException {
		int result = NativeIo.remove(bytes(path));
		if (result == NativeIo.NO_SUCH_FILE) {
			return false;
		}
		if (result == NativeIo.NOT_EMPTY) {
			throw new DirectoryNotEmptyException(path.toString());
		}
		if (result != 0) {
			throw translate(result, path);
		}
		return true;
	}

	public static boolean exists(Path path, LinkOption... options) {
		return NativeIo.kind(bytes(path)) > 0;
	}

	public static boolean notExists(Path path, LinkOption... options) {
		return NativeIo.kind(bytes(path)) == NativeIo.MISSING;
	}

	public static boolean isDirectory(Path path, LinkOption... options) {
		return NativeIo.kind(bytes(path)) == NativeIo.DIRECTORY;
	}

	public static boolean isRegularFile(Path path, LinkOption... options) {
		return NativeIo.kind(bytes(path)) == NativeIo.REGULAR;
	}

	public static boolean isReadable(Path path) {
		return NativeIo.access(bytes(path), 4) == 0;
	}

	public static boolean isWritable(Path path) {
		return NativeIo.access(bytes(path), 2) == 0;
	}

	public static boolean isExecutable(Path path) {
		return NativeIo.access(bytes(path), 1) == 0;
	}

	public static boolean isHidden(Path path) throws IOException {
		Path name = path.getFileName();
		return name != null && name.toString().startsWith(".");
	}

	public static long size(Path path) throws IOException {
		long result = NativeIo.size(bytes(path));
		if (result < 0) {
			throw translate((int) -result, path);
		}
		return result;
	}

	public static Stream<Path> list(Path dir) throws IOException {
		int kind = NativeIo.kind(bytes(dir));
		if (kind < 0) {
			throw translate(-kind, dir);
		}
		if (kind == NativeIo.MISSING) {
			throw new NoSuchFileException(dir.toString());
		}
		if (kind != NativeIo.DIRECTORY) {
			throw new NotDirectoryException(dir.toString());
		}
		byte[] names = NativeIo.list(bytes(dir));
		if (names == null) {
			throw new AccessDeniedException(dir.toString());
		}
		List<Path> entries = new ArrayList<Path>();
		for (String name : NativeIo.names(names)) {
			entries.add(dir.resolve(name));
		}
		return entries.stream();
	}

	public static BufferedReader newBufferedReader(Path path, Charset cs) throws IOException {
		CharsetDecoder decoder = cs.newDecoder();
		return new BufferedReader(new InputStreamReader(newInputStream(path), decoder));
	}

	public static BufferedReader newBufferedReader(Path path) throws IOException {
		return newBufferedReader(path, StandardCharsets.UTF_8);
	}

	public static BufferedWriter newBufferedWriter(Path path, Charset cs, OpenOption... options)
			throws IOException {
		CharsetEncoder encoder = cs.newEncoder();
		return new BufferedWriter(new OutputStreamWriter(newOutputStream(path, options), encoder));
	}

	public static BufferedWriter newBufferedWriter(Path path, OpenOption... options) throws IOException {
		return newBufferedWriter(path, StandardCharsets.UTF_8, options);
	}

	public static byte[] readAllBytes(Path path) throws IOException {
		InputStream in = newInputStream(path);
		try {
			return in.readAllBytes();
		} finally {
			in.close();
		}
	}

	public static String readString(Path path) throws IOException {
		return readString(path, StandardCharsets.UTF_8);
	}

	public static String readString(Path path, Charset cs) throws IOException {
		java.util.Objects.requireNonNull(path);
		java.util.Objects.requireNonNull(cs);
		byte[] ba = readAllBytes(path);
		CharsetDecoder decoder = cs.newDecoder();
		char[] chars = new char[ba.length];
		decoder.j2cDecode(ba, 0, ba.length, chars, 0, chars.length, true);
		return new String(chars, 0, decoder.j2cOutPosition());
	}

	public static List<String> readAllLines(Path path, Charset cs) throws IOException {
		BufferedReader reader = newBufferedReader(path, cs);
		try {
			List<String> result = new ArrayList<String>();
			for (;;) {
				String line = reader.readLine();
				if (line == null) {
					break;
				}
				result.add(line);
			}
			return result;
		} finally {
			reader.close();
		}
	}

	public static List<String> readAllLines(Path path) throws IOException {
		return readAllLines(path, StandardCharsets.UTF_8);
	}

	public static Stream<String> lines(Path path, Charset cs) throws IOException {
		final BufferedReader br = newBufferedReader(path, cs);
		return br.lines().onClose(new Runnable() {
			public void run() {
				try {
					br.close();
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			}
		});
	}

	public static Stream<String> lines(Path path) throws IOException {
		return lines(path, StandardCharsets.UTF_8);
	}

	public static Path write(Path path, byte[] bytes, OpenOption... options) throws IOException {
		java.util.Objects.requireNonNull(bytes);
		OutputStream out = newOutputStream(path, options);
		try {
			out.write(bytes, 0, bytes.length);
		} finally {
			out.close();
		}
		return path;
	}

	public static Path write(Path path, Iterable<? extends CharSequence> lines, Charset cs, OpenOption... options)
			throws IOException {
		java.util.Objects.requireNonNull(lines);
		CharsetEncoder encoder = cs.newEncoder();
		OutputStream out = newOutputStream(path, options);
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out, encoder));
		try {
			for (CharSequence line : lines) {
				writer.append(line);
				writer.newLine();
			}
		} finally {
			writer.close();
		}
		return path;
	}

	public static Path write(Path path, Iterable<? extends CharSequence> lines, OpenOption... options)
			throws IOException {
		return write(path, lines, StandardCharsets.UTF_8, options);
	}

	public static Path writeString(Path path, CharSequence csq, OpenOption... options) throws IOException {
		return writeString(path, csq, StandardCharsets.UTF_8, options);
	}

	public static Path writeString(Path path, CharSequence csq, Charset cs, OpenOption... options)
			throws IOException {
		java.util.Objects.requireNonNull(path);
		java.util.Objects.requireNonNull(csq);
		java.util.Objects.requireNonNull(cs);
		char[] chars = String.valueOf(csq).toCharArray();
		CharsetEncoder encoder = cs.newEncoder();
		byte[] bytes = new byte[chars.length * 3];
		try {
			encoder.j2cEncode(chars, 0, chars.length, bytes, 0, bytes.length, true);
		} catch (CharacterCodingException e) {
			// The JDK reports an unpaired surrogate as unmappable here
			throw new UnmappableCharacterException(1);
		}
		byte[] exact = new byte[encoder.j2cOutPosition()];
		System.arraycopy(bytes, 0, exact, 0, exact.length);
		return write(path, exact, options);
	}
}
