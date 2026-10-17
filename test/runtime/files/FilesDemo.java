import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// Files written and read back in the working directory, which the harness
// creates empty for each run (R6)
public class FilesDemo {
	static void failure(Exception e) {
		System.out.println(e.getClass().getName() + ": " + e.getMessage());
	}

	public static void main(String[] args) throws IOException {
		javaIo();
		paths();
		nioFiles();
		coding();
		cleanup();
	}

	static void javaIo() throws IOException {
		System.out.println("-- java.io");
		File dir = new File("work");
		System.out.println(dir.exists() + " " + dir.mkdir() + " " + dir.mkdir() + " " + dir.exists() + " "
				+ dir.isDirectory() + " " + dir.isFile());
		File notes = new File(dir, "notes.txt");
		try (FileWriter writer = new FileWriter(notes)) {
			writer.write("first line\n");
			writer.write("second line\n");
		}
		try (FileWriter writer = new FileWriter("work/notes.txt", true)) {
			writer.write("appended ü\n");
		}
		System.out.println(notes.getPath() + " " + notes.getName() + " " + notes.getParent() + " " + notes.length()
				+ " " + notes.isFile() + " " + notes.canRead() + " " + notes.isAbsolute());
		try (BufferedReader reader = new BufferedReader(new FileReader(notes))) {
			String line;
			while ((line = reader.readLine()) != null) {
				System.out.println("read: " + line);
			}
		}
		try (FileWriter writer = new FileWriter(notes)) {
			writer.write("replaced");
		}
		System.out.println(notes.length());

		try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter("work/table.txt")))) {
			for (int i = 1; i <= 3; i++) {
				out.printf("%d %s %.1f%n", i, "row" + i, i * 1.5);
			}
		}
		try (Scanner scanner = new Scanner(new File("work/table.txt"))) {
			double total = 0;
			while (scanner.hasNextInt()) {
				int id = scanner.nextInt();
				String name = scanner.next();
				total += scanner.nextDouble();
				System.out.println(id + "=" + name);
			}
			System.out.println("total " + total);
		}

		PrintStream ps = new PrintStream("work/stream.txt");
		ps.println("from PrintStream");
		ps.close();
		try (FileOutputStream out = new FileOutputStream("work/stream.txt", true)) {
			out.write("bytes\n".getBytes(StandardCharsets.UTF_8));
			out.write('!');
		}
		try (FileInputStream in = new FileInputStream("work/stream.txt")) {
			System.out.println(in.available() + " " + in.skip(5) + " " + (char) in.read());
			byte[] rest = in.readAllBytes();
			System.out.println(new String(rest, StandardCharsets.UTF_8).replace("\n", "|") + " " + in.read());
		}

		System.out.println(new File("a//b/c/").getPath() + " " + new File("/").getName() + "|" + new File("/").getParent()
				+ " " + new File("/top").getParent() + " " + new File("x").getParent() + " " + new File("", "y").getPath()
				+ " " + new File((String) null, "z").getPath() + " " + new File(new File("p"), "/q").getPath());
		System.out.println(new File("work/missing.txt").length() + " " + new File("work/missing.txt").exists()
				+ " " + new File("work/missing.txt").delete() + " " + new File("work").getAbsolutePath().endsWith("/work")
				+ " " + new File("work/../work/./notes.txt").getCanonicalPath().endsWith("/work/notes.txt"));
		System.out.println(new File("a").equals(new File("a")) + " " + new File("a").compareTo(new File("b")) + " "
				+ new File("a").hashCode() + " " + new File("dir/name.ext").toPath().getFileName());

		try {
			new FileReader("missing.txt");
		} catch (FileNotFoundException e) {
			failure(e);
		}
		try {
			new FileInputStream("work");
		} catch (FileNotFoundException e) {
			failure(e);
		}
		try {
			new FileOutputStream("nodir/out.txt");
		} catch (FileNotFoundException e) {
			failure(e);
		}
		try {
			new Scanner(new File("missing.txt"));
		} catch (FileNotFoundException e) {
			failure(e);
		}

		File nested = new File("work/a/b/c");
		System.out.println(nested.mkdir() + " " + nested.mkdirs() + " " + nested.isDirectory() + " " + nested.mkdirs());
		File renamed = new File("work/renamed.txt");
		System.out.println(new File("work/table.txt").renameTo(renamed) + " " + renamed.exists() + " "
				+ new File("work/table.txt").exists());
		File created = new File("work/created.txt");
		System.out.println(created.createNewFile() + " " + created.createNewFile() + " " + created.length());
		String[] names = dir.list();
		Arrays.sort(names);
		System.out.println(Arrays.toString(names));
		File[] files = dir.listFiles();
		Arrays.sort(files);
		System.out.println(Arrays.toString(files) + " " + notes.list());
		System.out.println(new File("work/a").delete() + " " + new File("work/a/b/c").delete() + " "
				+ created.delete() + " " + created.exists());
	}

	static void paths() {
		System.out.println("-- paths");
		Path p = Paths.get("work", "sub", "file.txt");
		System.out.println(p + " " + p.getFileName() + " " + p.getParent() + " " + p.getRoot() + " " + p.getNameCount()
				+ " " + p.getName(1) + " " + p.subpath(1, 3) + " " + p.isAbsolute());
		Path abs = Path.of("/usr//local/", "bin");
		System.out.println(abs + " " + abs.getRoot() + " " + abs.getParent().getParent() + " "
				+ abs.getParent().getParent().getParent() + " " + Path.of("/").getFileName() + " " + Path.of("/").getParent()
				+ " " + Path.of("").getFileName() + "|" + Path.of("").getNameCount() + " " + Path.of("x").getParent());
		System.out.println(p.resolve("more") + " " + p.resolve("/root") + " " + p.resolveSibling("other.txt") + " "
				+ Path.of("").resolve("x") + " " + abs.resolve(""));
		System.out.println(Path.of("a/./b/../c").normalize() + " " + Path.of("../a/..").normalize() + "|"
				+ Path.of("/../x").normalize() + " " + Path.of("a/b").relativize(Path.of("a/c/d")) + " "
				+ Path.of("/a").relativize(Path.of("/a/b")) + "|" + Path.of("x").relativize(Path.of("x")) + "|");
		System.out.println(p.startsWith("work") + " " + p.startsWith("wor") + " " + p.endsWith("sub/file.txt") + " "
				+ p.endsWith("file") + " " + abs.startsWith("/usr") + " " + abs.startsWith("usr"));
		StringBuilder parts = new StringBuilder();
		for (Path part : abs) {
			parts.append('<').append(part).append('>');
		}
		System.out.println(parts + " " + Path.of("a/b").equals(Paths.get("a", "b")) + " "
				+ (Path.of("a/b").hashCode() == Path.of("a//b/").hashCode()) + " " + Path.of("a").compareTo(Path.of("b"))
				+ " " + Path.of("work").toAbsolutePath().isAbsolute() + " " + Path.of("work").toAbsolutePath().endsWith("work")
				+ " " + Path.of("work/x").toFile().getName());
		try {
			Path.of("/a").relativize(Path.of("b"));
		} catch (IllegalArgumentException e) {
			failure(e);
		}
	}

	static void nioFiles() throws IOException {
		System.out.println("-- java.nio.file");
		Path dir = Path.of("work", "nio");
		System.out.println(Files.exists(dir) + " " + Files.notExists(dir) + " " + Files.createDirectories(dir) + " "
				+ Files.isDirectory(dir) + " " + Files.createDirectories(dir));
		Path deep = Files.createDirectories(Path.of("work/nio/x/y/z"));
		System.out.println(deep.isAbsolute() + " " + deep.endsWith("work/nio/x/y/z") + " " + Files.isDirectory(deep));

		Path text = dir.resolve("text.txt");
		System.out.println(Files.writeString(text, "alpha\nbeta\r\ngamma") + " " + Files.size(text) + " "
				+ Files.isRegularFile(text));
		System.out.println(Files.readString(text).replace("\r", "\\r").replace("\n", "\\n"));
		System.out.println(Files.readAllLines(text));
		Files.writeString(text, "\ndelta", StandardOpenOption.APPEND);
		try (Stream<String> lines = Files.lines(text)) {
			System.out.println(lines.map(String::toUpperCase).collect(Collectors.joining(",")));
		}
		Path listFile = dir.resolve("list.txt");
		Files.write(listFile, List.of("one", "two", "три"));
		System.out.println(Files.readAllLines(listFile, StandardCharsets.UTF_8) + " " + Files.size(listFile));
		Files.write(listFile, Arrays.asList("four"), StandardOpenOption.APPEND);
		try (BufferedReader reader = Files.newBufferedReader(listFile)) {
			System.out.println(reader.lines().count());
		}
		try (BufferedWriter writer = Files.newBufferedWriter(dir.resolve("buffered.txt"))) {
			writer.write("written");
			writer.newLine();
			writer.write("by BufferedWriter");
		}
		System.out.println(Files.readAllLines(dir.resolve("buffered.txt")));
		byte[] data = { 0, 1, 2, (byte) 255 };
		Files.write(dir.resolve("data.bin"), data);
		System.out.println(Arrays.toString(Files.readAllBytes(dir.resolve("data.bin"))));
		System.out.println(Files.list(dir).map(Path::toString).sorted().collect(Collectors.toList()));
		Path created = Files.createFile(dir.resolve("empty.txt"));
		System.out.println(created + " " + Files.size(created));

		try {
			Files.readString(Path.of("work/none.txt"));
		} catch (IOException e) {
			failure(e);
		}
		try {
			Files.size(Path.of("work/none.txt"));
		} catch (IOException e) {
			failure(e);
		}
		try {
			Files.delete(Path.of("work/none.txt"));
		} catch (IOException e) {
			failure(e);
		}
		try {
			Files.delete(dir);
		} catch (IOException e) {
			failure(e);
		}
		try {
			Files.createDirectory(dir);
		} catch (IOException e) {
			failure(e);
		}
		try {
			Files.createDirectories(text);
		} catch (IOException e) {
			failure(e);
		}
		try {
			Files.createFile(created);
		} catch (IOException e) {
			failure(e);
		}
		try {
			Files.writeString(Path.of("work/none.txt"), "x", StandardOpenOption.APPEND);
		} catch (IOException e) {
			failure(e);
		}
		try {
			Files.writeString(text, "x", StandardOpenOption.CREATE_NEW);
		} catch (IOException e) {
			failure(e);
		}
		try {
			Files.readAllLines(Path.of("work/nio/text.txt/inside"));
		} catch (IOException e) {
			failure(e);
		}
		try {
			Files.list(text);
		} catch (IOException e) {
			failure(e);
		}
		try {
			Files.readString(dir);
		} catch (IOException e) {
			failure(e);
		}
		System.out.println(Files.deleteIfExists(created) + " " + Files.deleteIfExists(created) + " " + Files.exists(created));
	}

	static void coding() throws IOException {
		System.out.println("-- coding");
		Path bad = Path.of("work", "bad.txt");
		Files.write(bad, new byte[] { 'o', 'k', ' ', (byte) 0xFF, ' ', (byte) 0xE2, (byte) 0x82, '\n', 'e', 'n', 'd' });
		try {
			Files.readString(bad);
		} catch (IOException e) {
			failure(e);
		}
		try {
			Files.readAllLines(bad);
		} catch (IOException e) {
			failure(e);
		}
		try (Stream<String> lines = Files.lines(bad)) {
			lines.forEach(System.out::println);
		} catch (java.io.UncheckedIOException e) {
			System.out.println("UncheckedIOException: " + e.getCause().getClass().getName() + ": " + e.getCause().getMessage());
		}
		String replaced = new String(Files.readAllBytes(bad), StandardCharsets.UTF_8);
		System.out.println(replaced.replace('\uFFFD', '?').replace("\n", "|"));
		try (BufferedReader reader = new BufferedReader(new FileReader("work/bad.txt"))) {
			System.out.println(reader.readLine().replace('\uFFFD', '?'));
		}
		try (Scanner scanner = new Scanner(Path.of("work/bad.txt"))) {
			System.out.println(scanner.next() + " " + scanner.next().replace('\uFFFD', '?'));
		}
		try (Scanner scanner = new Scanner(new File("work/bad.txt"))) {
			// A File is decoded strictly, and the first malformed byte ends the input
			System.out.println(scanner.hasNext() + " " + scanner.ioException().getClass().getName());
		}
		try {
			Files.writeString(Path.of("work/surrogate.txt"), "a\uD800b");
		} catch (IOException e) {
			failure(e);
		}
		try {
			Files.write(Path.of("work/surrogate.txt"), List.of("ok", "x\uDC00"));
		} catch (IOException e) {
			failure(e);
		}
		System.out.println(Files.readAllLines(Path.of("work/surrogate.txt")));
		try (FileWriter writer = new FileWriter("work/surrogate.txt")) {
			writer.write("y\uDC00z \uD83D\uDE00");
		}
		System.out.println(Files.readString(Path.of("work/surrogate.txt")) + " " + Files.size(Path.of("work/surrogate.txt")));
		Files.writeString(Path.of("work/latin1.txt"), "café", StandardCharsets.ISO_8859_1);
		System.out.println(Files.size(Path.of("work/latin1.txt")) + " "
				+ Files.readString(Path.of("work/latin1.txt"), StandardCharsets.ISO_8859_1) + " "
				+ new String(Files.readAllBytes(Path.of("work/latin1.txt")), StandardCharsets.UTF_8).replace('\uFFFD', '?'));
	}

	static void cleanup() throws IOException {
		System.out.println("-- cleanup");
		List<Path> all;
		try (Stream<Path> entries = Files.list(Path.of("work", "nio"))) {
			all = entries.sorted().collect(Collectors.toList());
		}
		for (Path entry : all) {
			if (Files.isDirectory(entry)) {
				Files.delete(entry.resolve("y/z"));
				Files.delete(entry.resolve("y"));
			}
			Files.delete(entry);
		}
		Files.delete(Path.of("work", "nio"));
		String[] remaining = new File("work").list();
		Arrays.sort(remaining);
		for (String name : remaining) {
			File f = new File("work", name);
			if (f.isDirectory()) {
				new File(f, "b/c").delete();
				new File(f, "b").delete();
			}
			System.out.println("delete " + name + " " + f.delete());
		}
		System.out.println(new File("work").delete() + " " + new File("work").exists());
	}
}
