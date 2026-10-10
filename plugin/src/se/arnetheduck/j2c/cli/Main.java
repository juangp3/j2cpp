package se.arnetheduck.j2c.cli;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;

import se.arnetheduck.j2c.transform.Problem;
import se.arnetheduck.j2c.transform.SourceProject;
import se.arnetheduck.j2c.transform.SourceUnit;
import se.arnetheduck.j2c.transform.TranslationException;
import se.arnetheduck.j2c.transform.Transformer;

public class Main {
	private static final String USAGE = "Usage: j2c (--output <directory> | --check) "
			+ "[--sourcepath <paths>] [--classpath <paths>] [--name <name>] "
			+ "[--sealed-types <advisory|reject>] [--encoding <charset>] "
			+ "<java-file-or-source-directory>..." + System.lineSeparator()
			+ "       j2c --version" + System.lineSeparator()
			+ "--check reports every problem that would stop translation "
			+ "without writing output.";

	public static void main(String[] args) {
		try {
			System.exit(run(args));
		} catch (IllegalArgumentException e) {
			System.err.println(e.getMessage());
			System.err.println(USAGE);
			System.exit(2);
		} catch (TranslationException e) {
			System.err.println("J2C translation failed:");
			System.err.println(e.getMessage());
			System.exit(1);
		} catch (Exception e) {
			System.err.println("J2C translation failed: " + e.getMessage());
			e.printStackTrace(System.err);
			System.exit(1);
		}
	}

	private static int run(String[] args) throws Exception {
		File output = null;
		boolean check = false;
		String name = null;
		Transformer.SealedTypes sealedTypes = Transformer.SealedTypes.ADVISORY;
		Charset encoding = StandardCharsets.UTF_8;
		List<File> sourcePaths = new ArrayList<File>();
		List<File> classPaths = new ArrayList<File>();
		List<File> inputs = new ArrayList<File>();

		for (int i = 0; i < args.length; i++) {
			String arg = args[i];
			if ("--help".equals(arg) || "-h".equals(arg)) {
				System.out.println(USAGE);
				return 0;
			} else if ("--version".equals(arg)) {
				System.out.println("j2c " + version());
				return 0;
			} else if ("--check".equals(arg)) {
				check = true;
			} else if ("--output".equals(arg)) {
				output = new File(value(args, ++i, arg));
			} else if ("--sourcepath".equals(arg)) {
				sourcePaths.addAll(pathList(value(args, ++i, arg)));
			} else if ("--classpath".equals(arg)) {
				classPaths.addAll(pathList(value(args, ++i, arg)));
			} else if ("--name".equals(arg)) {
				name = value(args, ++i, arg);
			} else if ("--sealed-types".equals(arg)) {
				String policy = value(args, ++i, arg);
				if ("advisory".equals(policy)) {
					sealedTypes = Transformer.SealedTypes.ADVISORY;
				} else if ("reject".equals(policy)) {
					sealedTypes = Transformer.SealedTypes.REJECT;
				} else {
					throw new IllegalArgumentException(
							"Invalid --sealed-types value: " + policy
									+ " (expected advisory or reject)");
				}
			} else if ("--encoding".equals(arg)) {
				encoding = charset(value(args, ++i, arg));
			} else if (arg.startsWith("-")) {
				throw new IllegalArgumentException("Unknown option: " + arg);
			} else {
				inputs.add(new File(arg));
			}
		}

		if (check && output != null) {
			throw new IllegalArgumentException(
					"--check does not write output; omit --output");
		}
		if (!check && output == null) {
			throw new IllegalArgumentException("Missing --output directory");
		}
		if (!check && !output.isDirectory()) {
			throw new IllegalArgumentException(
					"Output directory must already exist: " + output);
		}
		if (inputs.isEmpty()) {
			throw new IllegalArgumentException("No Java input was provided");
		}

		for (File input : inputs) {
			if (!input.exists()) {
				throw new IllegalArgumentException("Input does not exist: "
						+ input);
			}
			if (!input.isDirectory() && !input.getName().endsWith(".java")) {
				throw new IllegalArgumentException(
						"Input must be a .java file or source directory: "
								+ input);
			}
		}

		if (sourcePaths.isEmpty()) {
			for (File input : inputs) {
				File sourcePath = input.isDirectory() ? input
						: inferSourcePath(input.getAbsoluteFile(), encoding);
				if (!containsPath(sourcePaths, sourcePath)) {
					sourcePaths.add(sourcePath);
				}
			}
		}

		for (File sourcePath : sourcePaths) {
			if (!sourcePath.isDirectory()) {
				throw new IllegalArgumentException(
						"Source path is not a directory: " + sourcePath);
			}
		}
		for (File classPath : classPaths) {
			if (!classPath.exists()) {
				throw new IllegalArgumentException(
						"Class path does not exist: " + classPath);
			}
		}

		List<File> selectedFiles = new ArrayList<File>();
		for (File input : inputs) {
			if (input.isDirectory()) {
				collectSources(input, selectedFiles);
			} else {
				selectedFiles.add(input);
			}
		}
		if (selectedFiles.isEmpty()) {
			throw new IllegalArgumentException(
					"No Java source files found in the selected inputs");
		}

		SourceProject project = new SourceProject(sourcePaths, classPaths,
				encoding);
		Collection<SourceUnit> units = new ArrayList<SourceUnit>();
		for (File file : selectedFiles) {
			units.add(project.getUnit(file));
		}
		SourceUnit[] selected = units.toArray(new SourceUnit[units.size()]);
		if (check) {
			// Checking writes nothing, so the output root is never used
			Transformer checker = new Transformer(project, "check",
					new Path(new File(".").getAbsolutePath()), sealedTypes);
			return report(checker.check(new NullProgressMonitor(), selected),
					selected.length);
		}

		if (name == null) {
			name = output.getName();
		}

		Transformer transformer = new Transformer(project, name,
				new Path(output.getAbsolutePath()), sealedTypes);
		transformer.process(new NullProgressMonitor(), selected);
		return 0;
	}

	/** Prints each problem and a per-kind summary; nonzero if any */
	private static int report(List<Problem> problems, int fileCount) {
		Map<String, Integer> kinds = new TreeMap<String, Integer>();
		List<File> files = new ArrayList<File>();
		for (Problem problem : problems) {
			System.out.println(problem);
			Integer count = kinds.get(problem.kind);
			kinds.put(problem.kind, count == null ? 1 : count + 1);
			if (!files.contains(problem.file)) {
				files.add(problem.file);
			}
		}

		if (problems.isEmpty()) {
			System.out.println("J2C check: no problems found in " + fileCount
					+ " file(s)");
			return 0;
		}
		System.out.println("J2C check: " + problems.size()
				+ " problem(s) in " + files.size() + " of " + fileCount
				+ " file(s)");
		for (Map.Entry<String, Integer> kind : kinds.entrySet()) {
			System.out.println("  " + kind.getValue() + " x " + kind.getKey());
		}
		return 1;
	}

	private static String version() {
		String version = Main.class.getPackage().getImplementationVersion();
		return version == null ? "development build" : version;
	}

	private static String value(String[] args, int index, String option) {
		if (index >= args.length || args[index].startsWith("--")) {
			throw new IllegalArgumentException("Missing value for " + option);
		}
		return args[index];
	}

	private static Charset charset(String name) {
		try {
			return Charset.forName(name);
		} catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
			throw new IllegalArgumentException("Unsupported --encoding value: "
					+ name);
		}
	}

	private static List<File> pathList(String value) {
		List<File> paths = new ArrayList<File>();
		for (String path : value.split(java.util.regex.Pattern.quote(
				File.pathSeparator))) {
			if (!path.isEmpty()) {
				paths.add(new File(path));
			}
		}
		return paths;
	}

	private static boolean containsPath(List<File> paths, File candidate) {
		String absolute = candidate.getAbsolutePath();
		for (File path : paths) {
			if (absolute.equals(path.getAbsolutePath())) {
				return true;
			}
		}
		return false;
	}

	private static void collectSources(File directory, List<File> sources)
			throws java.io.IOException {
		File[] entries = directory.listFiles();
		if (entries == null) {
			throw new java.io.IOException("Could not read input directory "
					+ directory);
		}
		for (File entry : entries) {
			if (entry.isDirectory()) {
				collectSources(entry, sources);
			} else if (entry.getName().endsWith(".java")) {
				sources.add(entry);
			}
		}
	}

	private static File inferSourcePath(File sourceFile, Charset encoding)
			throws IOException {
		java.util.regex.Matcher packageName = java.util.regex.Pattern.compile(
				"(?m)^\\s*package\\s+([\\w.$]+)\\s*;")
				.matcher(new String(Files.readAllBytes(sourceFile.toPath()),
						encoding));
		if (!packageName.find()) {
			return sourceFile.getParentFile();
		}

		String[] segments = packageName.group(1).split("\\.");
		File root = sourceFile.getParentFile();
		for (int i = segments.length - 1; i >= 0; i--) {
			if (root == null || !root.getName().equals(segments[i])) {
				return sourceFile.getParentFile();
			}
			root = root.getParentFile();
		}
		return root == null ? sourceFile.getParentFile() : root;
	}
}
