package se.arnetheduck.j2c.cli;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.compiler.IScanner;
import org.eclipse.jdt.core.compiler.ITerminalSymbols;
import org.eclipse.jdt.core.compiler.InvalidInputException;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.RecordDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;

import se.arnetheduck.j2c.transform.Problem;
import se.arnetheduck.j2c.transform.SourceUnit;

/**
 * Check results kept between the calls of the long-running MCP server, so a
 * re-check compiles only the files that changed and the files whose result
 * may depend on them. Results are always the same as a check from scratch:
 *
 * <ul>
 * <li>A session holds the results for one environment: the source paths, the
 * class path (with each entry's size and modification time), the options and
 * the project build files. Any difference starts a new session, which checks
 * everything.</li>
 * <li>Every Java file under the source paths is read and hashed on each call.
 * A file's tokens outside method and initializer bodies, without comments
 * and whitespace, are its API. A change elsewhere only re-checks that file,
 * since no other file's compile errors depend on it. A changed API, an
 * added or a deleted file also re-checks every file that mentions, as an
 * identifier anywhere in its text, a type declared in a re-checked file (and
 * its package, when the package appears or disappears), repeated until
 * nothing more is added: a file using an inherited member mentions the
 * subclass, which mentions its superclass.</li>
 * <li>Files are re-checked together with the selected files that JDT could
 * not find by name through the source path (secondary types, a package that
 * does not match the folder, a package also on the class path or in the
 * JDK, syntax errors), since a check from scratch always compiles those
 * alongside. Results are kept by that set, so a different selection does not
 * reuse them.</li>
 * <li>A module-info.java or package-info.java change, a module-info.java on
 * the source path, or a type declared in more than one file checks
 * everything.</li>
 * </ul>
 *
 * Memory is bounded by the number of files kept over all sessions; the least
 * recently used sessions are dropped first.
 */
final class CheckCache {
	/** Default for j2c mcp --check-cache */
	static final int DEFAULT_MAX_FILES = 20000;

	/** Runs a check from scratch on some of the selected units */
	interface Checker {
		List<Problem> check(SourceUnit[] units) throws Exception;
	}

	/** What one check did, for logs and tests */
	static final class Stats {
		int files;
		int selected;
		int changed;
		int rechecked;
		boolean full;

		@Override
		public String toString() {
			return "re-checked " + rechecked + " of " + selected
					+ " selected file(s) (" + changed + " of " + files
					+ " source file(s) changed" + (full ? "; full check" : "") + ")";
		}
	}

	/** What is known about one source file */
	private static final class FileState {
		byte[] hash;
		/** Identifiers in the text, sorted; null when unknown (\\u escapes) */
		String[] tokens;
		/** Hash of the declarations without bodies */
		byte[] api;
		/** Simple names of the declared types */
		String[] typeNames;
		/** Qualified names of the top-level types */
		String[] topLevel;
		String packageName;
		boolean syntaxErrors;
		/**
		 * Problems found, by the unit path and the undiscoverable selected
		 * files compiled alongside; cleared when the file may be affected
		 */
		final Map<String, List<Problem>> results = new HashMap<String, List<Problem>>();
	}

	private static final class Session {
		final Map<String, FileState> files = new HashMap<String, FileState>();
		/** Packages on the class path or in the JDK */
		Set<String> externalPackages;
	}

	private final int maxFiles;
	private final LinkedHashMap<String, Session> sessions = new LinkedHashMap<String, Session>(
			16, 0.75f, true);
	private Stats last;

	CheckCache(int maxFiles) {
		this.maxFiles = maxFiles;
	}

	/** What the latest check did */
	Stats last() {
		return last;
	}

	/**
	 * The problems of the units, in unit order, re-checking only what may have
	 * changed since an earlier call with the same environment
	 */
	List<Problem> check(List<File> sourcePaths, List<File> classPaths,
			boolean systemLibrary, String options, List<File> buildFiles,
			Charset encoding, SourceUnit[] units, Checker checker,
			Progress progress) throws Exception {
		progress.phase("Looking for changed files");
		String key = key(sourcePaths, classPaths, systemLibrary, options,
				buildFiles);
		Session session = sessions.get(key);
		if (session == null) {
			session = new Session();
			sessions.put(key, session);
		}
		try {
			List<Problem> problems = check(session, sourcePaths, classPaths,
					systemLibrary, encoding, units, checker, progress);
			evict(key);
			return problems;
		} catch (Exception e) {
			// Cancelled or failed half-way: what is kept may not match the files
			sessions.remove(key);
			throw e;
		}
	}

	private List<Problem> check(Session session, List<File> sourcePaths,
			List<File> classPaths, boolean systemLibrary, Charset encoding,
			SourceUnit[] units, Checker checker, Progress progress)
			throws Exception {
		Stats stats = new Stats();
		last = stats;
		stats.selected = units.length;

		// Every file a lookup through the source path could find, and the
		// selected ones, which may be elsewhere
		Map<String, File> universe = new TreeMap<String, File>();
		for (File sourcePath : sourcePaths) {
			collect(sourcePath, universe);
		}
		Map<String, SourceUnit> selected = new LinkedHashMap<String, SourceUnit>();
		for (SourceUnit unit : units) {
			String path = normalize(unit.file());
			selected.put(path, unit);
			universe.put(path, unit.file());
		}
		stats.files = universe.size();

		boolean full = false;
		Set<String> apiChanged = new HashSet<String>();
		Set<String> changed = new HashSet<String>();
		Set<String> names = new HashSet<String>();
		Map<String, FileState> previous = new HashMap<String, FileState>(
				session.files);
		Set<String> packagesBefore = packages(previous.values());
		for (Map.Entry<String, File> entry : universe.entrySet()) {
			progress.checkCancelled();
			String path = entry.getKey();
			File file = entry.getValue();
			if ("module-info.java".equals(file.getName())) {
				// Modules change how every type is looked up
				full = true;
			}
			byte[] bytes = Files.readAllBytes(file.toPath());
			byte[] hash = sha256(bytes);
			FileState old = previous.remove(path);
			if (old != null && Arrays.equals(old.hash, hash)) {
				continue;
			}
			FileState state = analyze(bytes, hash, encoding);
			session.files.put(path, state);
			changed.add(path);
			if (isInfoFile(file.getName())) {
				full = true;
			} else if (old == null || !Arrays.equals(old.api, state.api)
					|| old.syntaxErrors || state.syntaxErrors) {
				apiChanged.add(path);
				names.addAll(Arrays.asList(state.typeNames));
				if (old != null) {
					names.addAll(Arrays.asList(old.typeNames));
				}
			}
		}
		// Deleted files
		for (Map.Entry<String, FileState> entry : previous.entrySet()) {
			session.files.remove(entry.getKey());
			changed.add(entry.getKey());
			if (isInfoFile(new File(entry.getKey()).getName())) {
				full = true;
			}
			names.addAll(Arrays.asList(entry.getValue().typeNames));
			apiChanged.add(entry.getKey());
		}
		stats.changed = changed.size();
		// A package that appears or disappears can change how qualified names
		// resolve in files that mention it
		Set<String> packagesAfter = packages(session.files.values());
		for (String name : symmetricDifference(packagesBefore, packagesAfter)) {
			names.addAll(Arrays.asList(name.split("\\.")));
		}

		if (duplicateTypes(session.files.values())) {
			full = true;
		}
		if (session.externalPackages == null) {
			session.externalPackages = externalPackages(classPaths, systemLibrary);
		}
		List<String> context = new ArrayList<String>();
		for (String path : selected.keySet()) {
			if (!discoverable(path, session.files.get(path), sourcePaths,
					session)) {
				context.add(path);
			}
		}
		String contextKey = String.join("\n", context);

		Set<String> affected = new HashSet<String>(apiChanged);
		if (!apiChanged.isEmpty()) {
			dependents(session, names, affected);
		}
		affected.addAll(changed);
		for (Map.Entry<String, FileState> entry : session.files.entrySet()) {
			if (full || affected.contains(entry.getKey())) {
				entry.getValue().results.clear();
			}
		}

		List<SourceUnit> batch = new ArrayList<SourceUnit>();
		for (Map.Entry<String, SourceUnit> entry : selected.entrySet()) {
			FileState state = session.files.get(entry.getKey());
			if (!state.results.containsKey(resultKey(entry.getValue(),
					contextKey))) {
				batch.add(entry.getValue());
			}
		}
		stats.full = full;
		if (!batch.isEmpty()) {
			// Compiled alongside, as in a check from scratch
			for (String path : context) {
				SourceUnit unit = selected.get(path);
				if (!batch.contains(unit)) {
					batch.add(unit);
				}
			}
			Map<String, List<Problem>> byUnit = new HashMap<String, List<Problem>>();
			for (SourceUnit unit : batch) {
				byUnit.put(unit.file().getPath(), new ArrayList<Problem>());
			}
			for (Problem problem : checker.check(batch
					.toArray(new SourceUnit[batch.size()]))) {
				List<Problem> list = byUnit.get(problem.file.getPath());
				if (list == null) {
					throw new IllegalStateException("A problem outside the "
							+ "checked files: " + problem);
				}
				list.add(problem);
			}
			for (SourceUnit unit : batch) {
				FileState state = session.files.get(normalize(unit.file()));
				state.results.put(resultKey(unit, contextKey), Collections
						.unmodifiableList(byUnit.get(unit.file().getPath())));
			}
		}
		stats.rechecked = batch.size();

		List<Problem> result = new ArrayList<Problem>();
		for (Map.Entry<String, SourceUnit> entry : selected.entrySet()) {
			result.addAll(session.files.get(entry.getKey()).results.get(resultKey(
					entry.getValue(), contextKey)));
		}
		return result;
	}

	private static String resultKey(SourceUnit unit, String contextKey) {
		return unit.file().getPath() + "\n\n" + contextKey;
	}

	/** Adds the files that may depend on the names, transitively */
	private static void dependents(Session session, Set<String> names,
			Set<String> affected) {
		boolean added = true;
		while (added) {
			added = false;
			for (Map.Entry<String, FileState> entry : session.files.entrySet()) {
				if (affected.contains(entry.getKey())) {
					continue;
				}
				FileState state = entry.getValue();
				if (mentions(state, names)) {
					affected.add(entry.getKey());
					names.addAll(Arrays.asList(state.typeNames));
					added = true;
				}
			}
		}
	}

	private static boolean mentions(FileState state, Set<String> names) {
		if (state.tokens == null) {
			return true;
		}
		for (String name : names) {
			if (Arrays.binarySearch(state.tokens, name) >= 0) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Whether a lookup of the file's types through the source path finds this
	 * file, so it need not be compiled alongside to be seen
	 */
	private static boolean discoverable(String path, FileState state,
			List<File> sourcePaths, Session session) {
		if (state.syntaxErrors) {
			return false;
		}
		if (state.topLevel.length == 0) {
			return true;
		}
		if (session.externalPackages.contains(state.packageName)) {
			return false;
		}
		String fileName = new File(path).getName();
		for (String type : state.topLevel) {
			String simple = type.substring(type.lastIndexOf('.') + 1);
			if (!fileName.equals(simple + ".java")) {
				return false;
			}
		}
		String relative = state.packageName.isEmpty() ? fileName
				: state.packageName.replace('.', File.separatorChar)
						+ File.separator + fileName;
		for (File sourcePath : sourcePaths) {
			String candidate = normalize(new File(sourcePath, relative));
			if (session.files.containsKey(candidate)) {
				return candidate.equals(path);
			}
		}
		return false;
	}

	private static boolean duplicateTypes(Iterable<FileState> files) {
		Set<String> seen = new HashSet<String>();
		for (FileState state : files) {
			for (String type : state.topLevel) {
				if (!seen.add(type)) {
					return true;
				}
			}
		}
		return false;
	}

	private static Set<String> packages(Iterable<FileState> files) {
		Set<String> packages = new HashSet<String>();
		for (FileState state : files) {
			packages.add(state.packageName);
		}
		return packages;
	}

	private static Set<String> symmetricDifference(Set<String> a, Set<String> b) {
		Set<String> result = new TreeSet<String>();
		for (String name : a) {
			if (!b.contains(name)) {
				result.add(name);
			}
		}
		for (String name : b) {
			if (!a.contains(name)) {
				result.add(name);
			}
		}
		result.remove("");
		return result;
	}

	private static boolean isInfoFile(String name) {
		return "module-info.java".equals(name) || "package-info.java".equals(name);
	}

	/** Drops the least recently used sessions over the file limit */
	private void evict(String current) {
		int total = 0;
		for (Session session : sessions.values()) {
			total += session.files.size();
		}
		Iterator<Map.Entry<String, Session>> it = sessions.entrySet().iterator();
		while (total > maxFiles && it.hasNext()) {
			Map.Entry<String, Session> entry = it.next();
			if (entry.getKey().equals(current) && sessions.size() > 1) {
				continue;
			}
			total -= entry.getValue().files.size();
			it.remove();
		}
	}

	/** Reads what the cache needs from one file */
	private static FileState analyze(byte[] bytes, byte[] hash, Charset encoding)
			throws NoSuchAlgorithmException {
		String text = new String(bytes, encoding);
		if (text.startsWith("﻿")) {
			text = text.substring(1);
		}
		FileState state = new FileState();
		state.hash = hash;
		state.tokens = text.contains("\\u") ? null : identifiers(text);

		ASTParser parser = ASTParser.newParser(AST.JLS17);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		Map<String, String> options = new HashMap<String, String>();
		JavaCore.setComplianceOptions(JavaCore.VERSION_17, options);
		parser.setCompilerOptions(options);
		parser.setSource(text.toCharArray());
		CompilationUnit unit = (CompilationUnit) parser.createAST(null);
		for (IProblem problem : unit.getProblems()) {
			if (problem.isError()) {
				state.syntaxErrors = true;
			}
		}
		state.packageName = unit.getPackage() == null ? "" : unit.getPackage()
				.getName().getFullyQualifiedName();
		List<String> topLevel = new ArrayList<String>();
		for (Object type : unit.types()) {
			String name = ((AbstractTypeDeclaration) type).getName()
					.getIdentifier();
			topLevel.add(state.packageName.isEmpty() ? name : state.packageName
					+ "." + name);
		}
		state.topLevel = topLevel.toArray(new String[topLevel.size()]);

		// Bodies, which other files cannot see, are left out of the API
		final List<int[]> bodies = new ArrayList<int[]>();
		final Set<String> typeNames = new TreeSet<String>();
		unit.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodDeclaration node) {
				body(node.getBody());
				return false;
			}

			@Override
			public boolean visit(Initializer node) {
				body(node.getBody());
				return false;
			}

			@Override
			public boolean visit(TypeDeclaration node) {
				typeNames.add(node.getName().getIdentifier());
				return true;
			}

			@Override
			public boolean visit(EnumDeclaration node) {
				typeNames.add(node.getName().getIdentifier());
				return true;
			}

			@Override
			public boolean visit(AnnotationTypeDeclaration node) {
				typeNames.add(node.getName().getIdentifier());
				return true;
			}

			@Override
			public boolean visit(RecordDeclaration node) {
				typeNames.add(node.getName().getIdentifier());
				return true;
			}

			private void body(Block body) {
				if (body != null) {
					bodies.add(new int[] { body.getStartPosition(),
							body.getStartPosition() + body.getLength() });
				}
			}
		});
		state.typeNames = typeNames.toArray(new String[typeNames.size()]);
		if (state.syntaxErrors) {
			// Recovery may read declarations differently in another context
			state.api = hash;
		} else {
			state.api = api(text, bodies, hash);
		}
		return state;
	}

	/**
	 * A hash of the tokens outside method and initializer bodies, without
	 * comments and whitespace: what other files can see of this one
	 */
	private static byte[] api(String text, List<int[]> bodies, byte[] hash)
			throws NoSuchAlgorithmException {
		IScanner scanner = ToolFactory.createScanner(false, false, false,
				JavaCore.VERSION_17);
		scanner.setSource(text.toCharArray());
		StringBuilder api = new StringBuilder(text.length());
		int body = 0;
		try {
			for (int token = scanner.getNextToken(); token != ITerminalSymbols.TokenNameEOF; token = scanner
					.getNextToken()) {
				int start = scanner.getCurrentTokenStartPosition();
				while (body < bodies.size() && start >= bodies.get(body)[1]) {
					++body;
				}
				if (body < bodies.size() && start >= bodies.get(body)[0]) {
					if (start == bodies.get(body)[0]) {
						api.append("{}\n");
					}
					continue;
				}
				api.append(scanner.getCurrentTokenSource()).append('\n');
			}
		} catch (InvalidInputException e) {
			return hash;
		}
		return sha256(api.toString().getBytes(StandardCharsets.UTF_8));
	}

	/** Every identifier-like word in the text, comments and strings included */
	private static String[] identifiers(String text) {
		Set<String> words = new TreeSet<String>();
		int length = text.length();
		for (int i = 0; i < length;) {
			char c = text.charAt(i);
			if (Character.isJavaIdentifierStart(c)) {
				int start = i++;
				while (i < length && Character.isJavaIdentifierPart(text.charAt(i))) {
					++i;
				}
				words.add(text.substring(start, i).intern());
			} else {
				++i;
			}
		}
		return words.toArray(new String[words.size()]);
	}

	private static void collect(File directory, Map<String, File> files)
			throws IOException {
		File[] entries = directory.listFiles();
		if (entries == null) {
			return;
		}
		for (File entry : entries) {
			if (entry.isDirectory()) {
				collect(entry, files);
			} else if (entry.getName().endsWith(".java")) {
				files.put(normalize(entry), entry);
			}
		}
	}

	private static String normalize(File file) {
		return file.getAbsoluteFile().toPath().normalize().toString();
	}

	/** Packages a lookup finds on the class path or in the JDK first */
	private static Set<String> externalPackages(List<File> classPaths,
			boolean systemLibrary) throws IOException {
		Set<String> packages = new HashSet<String>();
		for (File entry : classPaths) {
			if (entry.isDirectory()) {
				classDirectoryPackages(entry, "", packages);
			} else if (entry.isFile()) {
				try (ZipFile zip = new ZipFile(entry)) {
					Enumeration<? extends ZipEntry> entries = zip.entries();
					while (entries.hasMoreElements()) {
						String name = entries.nextElement().getName();
						int slash = name.lastIndexOf('/');
						if (name.endsWith(".class")) {
							String packageName = slash < 0 ? "" : name.substring(0,
									slash);
							if (packageName.startsWith("META-INF/versions/")) {
								packageName = packageName.replaceFirst(
										"^META-INF/versions/[0-9]+/?", "");
							}
							packages.add(packageName.replace('/', '.'));
						}
					}
				} catch (IOException e) {
					// JDT skips an unreadable entry too
				}
			}
		}
		if (systemLibrary) {
			try {
				FileSystem jrt = FileSystems.getFileSystem(URI.create("jrt:/"));
				try (DirectoryStream<Path> list = Files.newDirectoryStream(jrt
						.getPath("/packages"))) {
					for (Path path : list) {
						packages.add(path.getFileName().toString());
					}
				}
			} catch (Exception e) {
				// No module image: the default package can still be told apart
			}
		}
		return packages;
	}

	private static void classDirectoryPackages(File directory, String prefix,
			Set<String> packages) {
		File[] entries = directory.listFiles();
		if (entries == null) {
			return;
		}
		for (File entry : entries) {
			if (entry.isDirectory()) {
				classDirectoryPackages(entry, prefix.isEmpty() ? entry.getName()
						: prefix + "." + entry.getName(), packages);
			} else if (entry.getName().endsWith(".class")) {
				packages.add(prefix);
			}
		}
	}

	/** Everything outside the source files that decides check results */
	private static String key(List<File> sourcePaths, List<File> classPaths,
			boolean systemLibrary, String options, List<File> buildFiles)
			throws IOException, NoSuchAlgorithmException {
		StringBuilder key = new StringBuilder();
		key.append("options=").append(options).append('\n');
		key.append("system=").append(systemLibrary).append(' ')
				.append(System.getProperty("java.home")).append('\n');
		for (File sourcePath : sourcePaths) {
			key.append("source=").append(sourcePath.getAbsolutePath()).append('\n');
		}
		for (File entry : classPaths) {
			key.append("class=").append(entry.getAbsolutePath()).append(' ')
					.append(stamp(entry)).append('\n');
		}
		List<String> builds = new ArrayList<String>();
		for (File file : buildFiles) {
			builds.add(file.getAbsolutePath() + " "
					+ (file.isFile() ? hex(sha256(Files.readAllBytes(file
							.toPath()))) : "missing"));
		}
		Collections.sort(builds);
		for (String build : builds) {
			key.append("build=").append(build).append('\n');
		}
		return hex(sha256(key.toString().getBytes(StandardCharsets.UTF_8)));
	}

	/** Sizes and modification times of a class path entry's files */
	private static String stamp(File entry) throws NoSuchAlgorithmException {
		if (!entry.isDirectory()) {
			return entry.length() + ":" + entry.lastModified();
		}
		StringBuilder text = new StringBuilder();
		stamp(entry, "", text);
		return hex(sha256(text.toString().getBytes(StandardCharsets.UTF_8)));
	}

	private static void stamp(File directory, String prefix, StringBuilder text) {
		File[] entries = directory.listFiles();
		if (entries == null) {
			return;
		}
		Arrays.sort(entries);
		for (File entry : entries) {
			String name = prefix + "/" + entry.getName();
			if (entry.isDirectory()) {
				stamp(entry, name, text);
			} else {
				text.append(name).append(' ').append(entry.length()).append(':')
						.append(entry.lastModified()).append('\n');
			}
		}
	}

	private static byte[] sha256(byte[] bytes) throws NoSuchAlgorithmException {
		return MessageDigest.getInstance("SHA-256").digest(bytes);
	}

	private static String hex(byte[] bytes) {
		StringBuilder text = new StringBuilder();
		for (byte b : bytes) {
			text.append(String.format("%02x", b & 0xff));
		}
		return text.toString();
	}
}
