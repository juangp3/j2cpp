package se.arnetheduck.j2c.transform;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FileASTRequestor;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.TypeDeclaration;

public class SourceProject {
	private final List<File> sourcePaths;
	private final List<File> classPaths;
	private final Charset encoding;
	// False when the sources provide java.lang themselves (a J2C runtime)
	private boolean systemLibrary = true;
	private final Map<String, SourceUnit> units = new HashMap<String, SourceUnit>();

	/** Rewritten sources by unit; null when a unit needed no rewriting */
	private final Map<SourceUnit, File> desugared = new HashMap<SourceUnit, File>();
	private final Map<SourceUnit, List<Problem>> desugarProblems = new HashMap<SourceUnit, List<Problem>>();
	/** Mirrors the source roots; shadows them for rewritten units */
	private File desugarRoot;

	/** Receives each parsed unit of a batch */
	public interface UnitHandler {
		void accept(SourceUnit unit, CompilationUnit ast) throws Exception;
	}

	public SourceProject(Collection<File> sourcePaths,
			Collection<File> classPaths, Charset encoding) {
		this.sourcePaths = absolutePaths(sourcePaths);
		this.classPaths = absolutePaths(classPaths);
		this.encoding = encoding;
	}

	/**
	 * Whether the running JDK's class library is visible. A runtime that
	 * provides java.lang as source replaces it.
	 */
	public void setSystemLibrary(boolean systemLibrary) {
		this.systemLibrary = systemLibrary;
	}

	private static List<File> absolutePaths(Collection<File> paths) {
		List<File> result = new ArrayList<File>();
		for (File path : paths) {
			result.add(path.getAbsoluteFile());
		}
		return result;
	}

	private SourceUnit addUnit(File file) {
		String path = file.getAbsoluteFile().getPath();
		SourceUnit unit = units.get(path);
		if (unit == null) {
			unit = new SourceUnit(file);
			units.put(path, unit);
		}
		return unit;
	}

	public SourceUnit getUnit(File file) {
		return addUnit(file);
	}

	/**
	 * Parses the units with one shared compiler environment, which is much
	 * cheaper than resolving bindings for each file separately. Handler
	 * failures stop the remaining units from being handled and are rethrown.
	 */
	public void parse(Collection<SourceUnit> batch, IProgressMonitor monitor,
			final UnitHandler handler) throws Exception {
		desugar(batch, monitor);

		final Map<String, SourceUnit> byPath = new HashMap<String, SourceUnit>();
		for (SourceUnit unit : batch) {
			File rewritten = desugared.get(unit);
			byPath.put((rewritten == null ? unit.file() : rewritten).getPath(),
					unit);
		}
		String[] paths = byPath.keySet().toArray(new String[byPath.size()]);
		Arrays.sort(paths);
		String[] encodings = new String[paths.length];
		Arrays.fill(encodings, encoding.name());

		final Exception[] failure = new Exception[1];
		newParser().createASTs(paths, encodings, new String[0],
				new FileASTRequestor() {
					@Override
					public void acceptAST(String sourceFilePath,
							CompilationUnit ast) {
						if (failure[0] != null) {
							return;
						}
						try {
							handler.accept(byPath.get(sourceFilePath), ast);
						} catch (Exception e) {
							failure[0] = e;
						}
					}
				}, monitor);
		if (failure[0] != null) {
			throw failure[0];
		}
	}

	/** Lambdas and method references in the unit that were not rewritten */
	public List<Problem> desugarProblems(SourceUnit unit) {
		List<Problem> problems = desugarProblems.get(unit);
		return problems == null ? Collections.<Problem> emptyList() : problems;
	}

	/**
	 * Rewrites lambdas and method references in the batch into anonymous
	 * classes (see {@link LambdaDesugarer}). Rewritten units are parsed from
	 * the desugar root instead of their original file.
	 */
	private void desugar(Collection<SourceUnit> batch, IProgressMonitor monitor)
			throws Exception {
		final Map<String, SourceUnit> candidates = new HashMap<String, SourceUnit>();
		final Map<SourceUnit, String> texts = new HashMap<SourceUnit, String>();
		for (SourceUnit unit : batch) {
			if (desugared.containsKey(unit)) {
				continue;
			}
			String text = read(unit.file());
			if (text.contains("->") || text.contains("::")) {
				candidates.put(unit.file().getPath(), unit);
				texts.put(unit, text);
			} else {
				desugared.put(unit, null);
			}
		}
		if (candidates.isEmpty()) {
			return;
		}

		String[] paths = candidates.keySet().toArray(
				new String[candidates.size()]);
		Arrays.sort(paths);
		String[] encodings = new String[paths.length];
		Arrays.fill(encodings, encoding.name());
		final Exception[] failure = new Exception[1];
		newParser().createASTs(paths, encodings, new String[0],
				new FileASTRequestor() {
					@Override
					public void acceptAST(String sourceFilePath,
							CompilationUnit ast) {
						SourceUnit unit = candidates.get(sourceFilePath);
						LambdaDesugarer desugarer = new LambdaDesugarer(
								unit.file(), texts.get(unit), ast);
						try {
							String rewritten = desugarer.desugar();
							desugarProblems.put(unit, desugarer.problems());
							desugared.put(unit, rewritten == null ? null
									: write(unit, rewritten));
						} catch (Exception e) {
							failure[0] = e;
						}
					}
				}, monitor);
		if (failure[0] != null) {
			throw failure[0];
		}
	}

	/** The source text, without a byte order mark, which JDT also skips */
	private String read(File file) throws IOException {
		String text = new String(Files.readAllBytes(file.toPath()), encoding);
		return text.startsWith("\uFEFF") ? text.substring(1) : text;
	}

	private File write(SourceUnit unit, String text) throws IOException {
		if (desugarRoot == null) {
			desugarRoot = Files.createTempDirectory("j2c-desugar").toFile();
			final Path root = desugarRoot.toPath();
			Runtime.getRuntime().addShutdownHook(new Thread() {
				@Override
				public void run() {
					try (Stream<Path> files = Files.walk(root)) {
						files.sorted(Comparator.reverseOrder()).forEach(
								path -> path.toFile().delete());
					} catch (IOException e) {
						// Best effort: the directory is in the temp folder
					}
				}
			});
		}

		File target = new File(desugarRoot, relativePath(unit.file()));
		target.getParentFile().mkdirs();
		Files.write(target.toPath(), text.getBytes(encoding));
		return target;
	}

	private String relativePath(File file) {
		String path = file.getAbsolutePath();
		for (File sourcePath : sourcePaths) {
			String root = sourcePath.getAbsolutePath();
			if (!root.endsWith(File.separator)) {
				root += File.separator;
			}
			if (path.startsWith(root)) {
				return path.substring(root.length());
			}
		}
		return file.getName();
	}

	public ITypeBinding resolve(String typeName) {
		ASTParser parser = newParser();
		parser.setSource(("class __J2CProbe { " + typeName.replace('$', '.')
				+ " value; }").toCharArray());
		parser.setUnitName("__J2CProbe.java");
		CompilationUnit probe = (CompilationUnit) parser.createAST(null);
		ITypeBinding binding = probe.getAST().resolveWellKnownType(typeName);
		if (binding != null) {
			return binding;
		}

		TypeDeclaration declaration = (TypeDeclaration) probe.types().get(0);
		return declaration.getFields()[0].getType().resolveBinding();
	}

	private ASTParser newParser() {
		ASTParser parser = ASTParser.newParser(AST.JLS17);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(true);
		List<File> roots = new ArrayList<File>(sourcePaths);
		if (desugarRoot != null) {
			roots.add(0, desugarRoot);
		}
		String[] encodings = new String[roots.size()];
		Arrays.fill(encodings, encoding.name());
		parser.setEnvironment(paths(classPaths), paths(roots), encodings,
				systemLibrary);
		Map<String, String> options = new HashMap<String, String>();
		JavaCore.setComplianceOptions(JavaCore.VERSION_17, options);
		parser.setCompilerOptions(options);
		return parser;
	}

	private static String[] paths(List<File> paths) {
		String[] result = new String[paths.size()];
		for (int i = 0; i < result.length; i++) {
			result[i] = paths.get(i).getPath();
		}
		return result;
	}

	public SourceUnit sourceUnit(ITypeBinding binding) {
		if (binding == null || binding.isArray() || binding.isPrimitive()) {
			return null;
		}

		String binaryName = binding.getErasure().getBinaryName();
		if (binaryName == null || binaryName.length() == 0) {
			return null;
		}

		int nested = binaryName.indexOf('$');
		if (nested >= 0) {
			binaryName = binaryName.substring(0, nested);
		}
		int packageEnd = binaryName.lastIndexOf('.');
		String packagePath = packageEnd < 0 ? ""
				: binaryName.substring(0, packageEnd).replace('.',
						File.separatorChar);
		String typeName = packageEnd < 0 ? binaryName
				: binaryName.substring(packageEnd + 1);
		for (File sourcePath : sourcePaths) {
			File source = new File(new File(sourcePath, packagePath),
					typeName + ".java");
			if (source.isFile()) {
				return addUnit(source);
			}
		}

		return null;
	}
}
