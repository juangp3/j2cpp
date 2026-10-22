package se.arnetheduck.j2c.transform;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.WeakHashMap;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.PatternInstanceofExpression;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodReference;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.RecordDeclaration;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;

import se.arnetheduck.j2c.snippets.GetSetSnippet;
import se.arnetheduck.j2c.snippets.ReplaceInvocation;

public class Transformer {
	private static final String ARRAY_HPP = "/se/arnetheduck/j2c/resources/Array.hpp";
	private static final String OBJECT_ARRAY_HPP = "/se/arnetheduck/j2c/resources/ObjectArray.hpp";
	private static final String SUB_ARRAY_HPP = "/se/arnetheduck/j2c/resources/SubArray.hpp";
	private static final String ARRAY_CPP = "/se/arnetheduck/j2c/resources/j2c_array.cpp";

	private final SourceProject project;

	private final String name;

	private final IPath root;

	private final SealedTypes sealedTypes;

	/** Whether JUnit tests get GoogleTest wrappers (see GTestWriter) */
	private boolean tests;
	private final List<JUnitSupport.TestClass> testClasses = new ArrayList<JUnitSupport.TestClass>();
	/** Where annotated methods are declared, by method binding key */
	private final Map<String, MethodLocation> methodLocations = new HashMap<String, MethodLocation>();
	/** The GoogleTest tests written, in wrapper order */
	private final List<GeneratedTest> generatedTests = new ArrayList<GeneratedTest>();
	private boolean junitAssertions;
	/** Whether generated C++ carries #line directives (see LineDirectives) */
	private boolean lineDirectives;
	/** Whether interfaces get gMock classes (see MockWriter) */
	private boolean mocks;

	private final Stats direct = new Stats();
	private final Stats deps = new Stats();

	public enum SealedTypes {
		ADVISORY, REJECT
	}

	public final static class ICUComparator implements
			Comparator<SourceUnit> {
		@Override
		public int compare(SourceUnit o1, SourceUnit o2) {
			return o1.compareTo(o2);
		}
	}

	public Transformer(SourceProject project, String name, IPath root)
			throws Exception {
		this(project, name, root, SealedTypes.ADVISORY);
	}

	public Transformer(SourceProject project, String name, IPath root,
			SealedTypes sealedTypes) throws Exception {
		this.project = project;
		this.name = name;
		this.root = root;
		this.sealedTypes = sealedTypes;

		snippets.add(new GetSetSnippet());
		snippets.add(new ReplaceInvocation());
	}

	public final Set<SourceUnit> selection = new TreeSet<SourceUnit>(
			new ICUComparator());

	/** Compilation units that are scheduled for processing */
	public final Set<SourceUnit> todo = new TreeSet<SourceUnit>(
			new ICUComparator());

	/** Type bindings that didn't have a corresponding compilation unit */
	private Set<ITypeBinding> hardDeps = new TreeSet<ITypeBinding>(
			new BindingComparator());

	private final Map<String, ForwardWriter.Info> forwards = new HashMap<String, ForwardWriter.Info>();

	private final Map<String, String[]> parameterNames = new HashMap<String, String[]>();

	private final MakefileWriter.Info sel = new MakefileWriter.Info();
	private final MakefileWriter.Info ext = new MakefileWriter.Info();

	private MakefileWriter.Info cur;

	private File runtimeCpp;

	private final Set<String> done = new HashSet<String>();

	/** Selected units checked by validate, which the writers trust */
	private final Set<SourceUnit> validated = new TreeSet<SourceUnit>(
			new ICUComparator());

	private final List<Problem> warnings = new ArrayList<Problem>();

	public List<Snippet> snippets = new ArrayList<Snippet>();

	protected AST currentAST;

	/**
	 * A J2C runtime's C++ directory: native/ replaces generated native stubs,
	 * src/ holds hooks (class_, _j, init_jvm, make_args, unimplemented_)
	 */
	public void setRuntimeCpp(File runtimeCpp) {
		this.runtimeCpp = runtimeCpp;
	}

	/** Generate GoogleTest wrappers for JUnit tests in the selection */
	public void setTests(boolean tests) {
		this.tests = tests;
	}

	public boolean translatesTests() {
		return tests;
	}

	/** The GoogleTest tests written by process, with their Java methods */
	public List<GeneratedTest> generatedTests() {
		return Collections.unmodifiableList(generatedTests);
	}

	/** A method declaration's file and the line of its name */
	static final class MethodLocation {
		final File file;
		final int line;

		MethodLocation(File file, int line) {
			this.file = file;
			this.line = line;
		}
	}

	/** Generate gMock classes for the interfaces in the selection */
	public void setMocks(boolean mocks) {
		this.mocks = mocks;
	}

	/** Emit #line directives mapping the generated C++ to the Java source */
	public void setLineDirectives(boolean lineDirectives) {
		this.lineDirectives = lineDirectives;
	}

	public boolean lineDirectives() {
		return lineDirectives;
	}

	/**
	 * The quoted path of a generated file in its #line directives, or null
	 * without them
	 */
	public String linePath(File generated) {
		return lineDirectives ? LineDirectives.quote(LineDirectives.path(
				generated, root.toFile())) : null;
	}

	/** Called when a JUnit assertion was translated into j2c_junit.hpp */
	public void usesJUnitAssertions() {
		junitAssertions = true;
	}

	/**
	 * Translates the units and the types they depend on. The units are
	 * validated first (see {@link #validate}), so a selected unit with
	 * problems stops the translation before anything is written. The monitor
	 * then counts written units (beginTask with an unknown total, then
	 * worked(1) after each) and stops the translation with
	 * OperationCanceledException when cancelled, leaving what was written so
	 * far.
	 */
	public void process(IProgressMonitor monitor, SourceUnit... units)
			throws Exception {
		validate(monitor, units);
		monitor.beginTask("Translating", IProgressMonitor.UNKNOWN);
		monitor.subTask("Moving old files");
		renameOld();

		long start = System.currentTimeMillis();

		hardDep(resolve(ClassLoader.class));
		// Every class declares class_() and getClass0(), and SubArray.hpp
		// throws ArrayStoreException; a runtime must provide both
		hardDep(resolve(Class.class));
		hardDep(resolve(ArrayStoreException.class));
		// j2c_array.cpp throws these for Array.hpp's checks, with a message
		hardDep(resolve(ArrayIndexOutOfBoundsException.class));
		hardDep(resolve(NegativeArraySizeException.class));
		hardDep(resolve(String.class));
		// Classes a runtime's hooks call, which the program may never reach
		if (runtimeCpp != null) {
			File required = new File(runtimeCpp.getParentFile(),
					"required-classes.txt");
			if (required.isFile()) {
				for (String line : java.nio.file.Files.readAllLines(required.toPath(),
						java.nio.charset.StandardCharsets.UTF_8)) {
					line = line.trim();
					if (!line.isEmpty() && !line.startsWith("#")) {
						hardDep(resolve(line));
					}
				}
			}
		}
		// The ObjectArray.hpp resource includes these unconditionally
		hardDep(resolve(Cloneable.class));
		hardDep(resolve(java.io.Serializable.class));
		if (tests) {
			// The GoogleTest and assertion helpers use these
			hardDep(resolve(Throwable.class));
			hardDep(resolve(AssertionError.class));
		}
		selection.addAll(Arrays.asList(units));
		todo.addAll(selection);

		while (!todo.isEmpty()) {
			checkCanceled(monitor);
			processSome(monitor);
		}
		checkCanceled(monitor);

		writeResources();
		writeTests();
		writeRuntime();

		new ForwardWriter(this, root).write(forwards.values());

		MakefileWriter mw = new MakefileWriter(root);
		mw.write(name, sel, ext);

		monitor.done();

		System.out.println("Selected stats:");
		System.out.println(direct);

		System.out.println("Dependency stats:");
		System.out.println(deps);

		System.out.println("Done (" + (System.currentTimeMillis() - start)
				+ " ms).");
	}

	/**
	 * Checks every unit, as {@link #check} does, and throws a
	 * TranslationException with all of their problems when any has one, before
	 * anything is written. Units validated earlier are not checked again, so
	 * a caller can validate before preparing the output and then call
	 * {@link #process}. The monitor counts validated units.
	 */
	public void validate(final IProgressMonitor monitor, SourceUnit... units)
			throws Exception {
		selection.addAll(Arrays.asList(units));
		List<SourceUnit> pending = new ArrayList<SourceUnit>();
		for (SourceUnit unit : units) {
			if (!validated.contains(unit)) {
				pending.add(unit);
			}
		}
		if (pending.isEmpty()) {
			return;
		}
		final List<Problem> problems = new ArrayList<Problem>();
		monitor.beginTask("Validating " + pending.size() + " file(s)",
				pending.size());
		project.parse(pending, monitor, new SourceProject.UnitHandler() {
			@Override
			public void accept(SourceUnit source, CompilationUnit ast)
					throws Exception {
				List<Problem> found = problems(source, ast);
				if (!found.isEmpty()) {
					problems.addAll(QuickFixes.addFixes(project, source, found));
				}
				validated.add(source);
				monitor.subTask("Validated " + source.file().getPath());
				monitor.worked(1);
			}
		});
		if (!problems.isEmpty()) {
			throw new TranslationException(join(problems), problems);
		}
	}

	/**
	 * Warnings about the translation: dependency units with Java errors,
	 * which only got a header (J2C-DEPENDENCY-ERRORS)
	 */
	public List<Problem> warnings() {
		return Collections.unmodifiableList(warnings);
	}

	private static void checkCanceled(IProgressMonitor monitor) {
		if (monitor.isCanceled()) {
			throw new OperationCanceledException();
		}
	}

	private void collectTests(CompilationUnit ast) {
		for (JUnitSupport.TestClass testClass : JUnitSupport.testClasses(ast)) {
			testClasses.add(testClass);
			for (JUnitSupport.TestMethod test : testClass.tests) {
				if (test.expected != null) {
					hardDep(test.expected);
				}
			}
		}
	}

	/**
	 * Records where the annotated methods (test and lifecycle methods among
	 * them) are declared; rewritten lambdas keep every line in place
	 */
	private void recordMethodLocations(File file, final CompilationUnit ast) {
		final File source = file.getAbsoluteFile().toPath().normalize().toFile();
		ast.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodDeclaration node) {
				IMethodBinding binding = node.resolveBinding();
				boolean annotated = false;
				for (Object modifier : node.modifiers()) {
					annotated |= modifier instanceof org.eclipse.jdt.core.dom.Annotation;
				}
				if (binding != null && annotated) {
					methodLocations.put(binding.getMethodDeclaration().getKey(),
							new MethodLocation(source, ast.getLineNumber(node
									.getName().getStartPosition())));
				}
				return true;
			}
		});
	}

	private void writeTests() throws IOException {
		if (testClasses.isEmpty() && !junitAssertions) {
			return;
		}
		FileUtil.writeResource(JUNIT_HPP, root.append("src").append(
				"j2c_junit.hpp").toFile());
		if (testClasses.isEmpty()) {
			return;
		}
		FileUtil.writeResource(GTestWriter.HELPER_HPP, root.append("test")
				.append("j2c_gtest.hpp").toFile());
		for (JUnitSupport.TestClass testClass : testClasses) {
			sel.tests.add(GTestWriter.write(root, testClass, methodLocations,
					generatedTests));
		}
	}

	private static final String JUNIT_HPP = "/se/arnetheduck/j2c/resources/j2c_junit.hpp";

	private void writeResources() throws IOException {
		// TODO Auto-generated method stub
		FileUtil.writeResource(ARRAY_HPP,
				TransformUtil.headerPath(root, "Array.hpp").toFile());
		FileUtil.writeResource(OBJECT_ARRAY_HPP,
				TransformUtil.headerPath(root, "ObjectArray.hpp").toFile());
		FileUtil.writeResource(SUB_ARRAY_HPP,
				TransformUtil.headerPath(root, "SubArray.hpp").toFile());
		FileUtil.writeResource(ARRAY_CPP,
				TransformUtil.headerPath(root, "j2c_array.cpp").toFile());
		sel.impls.add("src/j2c_array.cpp");
	}

	/** Copies a runtime's C++ into the output (see setRuntimeCpp) */
	private void writeRuntime() throws IOException {
		if (runtimeCpp == null) {
			return;
		}
		// native/ replaces the generated stub of a class the program uses
		List<String> natives = new ArrayList<String>();
		listFiles(new File(runtimeCpp, "native"), "", natives);
		for (String rel : natives) {
			File source = new File(new File(runtimeCpp, "native"), rel);
			for (String prefix : new String[] { "", "ext/" }) {
				File generated = new File(root.toFile(), prefix + "native/" + rel);
				if (generated.isFile()) {
					FileUtil.copy(source, generated);
				}
			}
		}

		// src/ holds the hooks and other hand-written C++
		List<String> sources = new ArrayList<String>();
		listFiles(new File(runtimeCpp, "src"), "", sources);
		for (String rel : sources) {
			if (rel.endsWith(".cpp") || rel.endsWith(".hpp")) {
				File target = new File(root.toFile(), "runtime/" + rel);
				target.getParentFile().mkdirs();
				FileUtil.copy(new File(new File(runtimeCpp, "src"), rel), target);
				if (rel.endsWith(".cpp")) {
					ext.runtime.add("runtime/" + rel);
				}
			}
		}
	}

	/** Files below a directory, as sorted paths with forward slashes */
	private static void listFiles(File directory, String prefix,
			List<String> files) {
		File[] entries = directory.listFiles();
		if (entries == null) {
			return;
		}
		Arrays.sort(entries);
		for (File entry : entries) {
			if (entry.isDirectory()) {
				listFiles(entry, prefix + entry.getName() + "/", files);
			} else {
				files.add(prefix + entry.getName());
			}
		}
	}

	public String getName() {
		return name;
	}

	private void renameOld() throws IOException {
		File r = root.toFile();
		File p = new File(System.getProperty("java.io.tmpdir"));

		File from = new File(p, r.getName() + 5);
		if (from.exists()) {
			clear(from);
		}

		for (int i = 4; i > 0; --i) {
			from = new File(p, r.getName() + i);
			File to = new File(p, r.getName() + (i + 1));
			moveFiles(from, to);
		}

		moveFiles(r, new File(p, r.getName() + 1));
	}

	private void clear(File from) {
		for (File f : from.listFiles()) {
			if (f.isDirectory()) {
				clear(f);
			}

			f.delete();
		}
	}

	private void moveFiles(File from, File to) throws IOException {
		if (from.exists()) {
			to.mkdir();

			for (File f : from.listFiles()) {
				if (f.isDirectory()) {
					moveFiles(f, new File(to, f.getName()));
					f.delete(); // Will only delete empty folders
				} else if (f.getName().endsWith(".o")
						|| f.getName().endsWith(".a")) {
					f.delete();
				} else if (f.getName().equals("Makefile")
						|| f.getName().endsWith(".hpp")
						|| f.getName().endsWith(".cpp")) {
					File tf = new File(to, f.getName());

					if (!f.renameTo(tf)) {
						FileUtil.copy(f, tf);
						f.delete();
					}
				}
			}
		}
	}

	private void processSome(IProgressMonitor monitor) throws Exception {
		SourceUnit[] units = new SourceUnit[Math.min(256,
				todo.size())];
		Iterator<SourceUnit> it = todo.iterator();
		for (int n = 0; it.hasNext() && n < units.length; n++) {
			units[n] = it.next();
			it.remove();
		}

		write(monitor, units);
		writeDeps(monitor);
	}

	private void write(final IProgressMonitor monitor, SourceUnit... units)
			throws Exception {
		project.parse(Arrays.asList(units), monitor,
				new SourceProject.UnitHandler() {
					@Override
					public void accept(SourceUnit source, CompilationUnit ast)
							throws Exception {
						recordParameterNames(ast);
						currentAST = ast.getAST();
						if (lineDirectives) {
							// The original file, also for rewritten lambdas,
							// whose rewrite keeps every line in place
							ast.setProperty(LineDirectives.FILE_PROPERTY,
									linePath(source.file()));
						}
						if (selection.contains(source)) {
							cur = sel;
						} else {
							cur = ext;
						}
						try {
							// Selected units were all validated before writing
							List<Problem> problems = validated.contains(source) ? Collections
									.<Problem> emptyList() : problems(source, ast);
							if (!selection.contains(source)
									&& hasCompileError(problems)) {
								// A dependency that does not compile: its
								// bindings still give a header and stubs
								writeHeaderOnly(source, ast, problems);
								return;
							}
							if (!problems.isEmpty()) {
								problems = QuickFixes.addFixes(project, source,
										problems);
								throw new TranslationException(join(problems), problems);
							}
							if (tests) {
								// Also in dependencies: a test may be inherited
								recordMethodLocations(source.file(), ast);
							}
							if (tests && selection.contains(source)) {
								collectTests(ast);
							}
							writeImpl(monitor, source, ast);
							if (mocks && selection.contains(source)) {
								MockWriter.write(Transformer.this, root, ast,
										sel.mocks);
							}
						} finally {
							currentAST = null;
						}
					}
				});
	}

	private static boolean hasCompileError(List<Problem> problems) {
		for (Problem problem : problems) {
			if (ProblemCatalog.COMPILE_ERROR.equals(problem.id)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Writes a dependency unit with Java errors as it would a type without
	 * source: headers and stubs from its types' bindings, and no
	 * implementation. Reports a J2C-DEPENDENCY-ERRORS warning at its first
	 * error.
	 */
	private void writeHeaderOnly(SourceUnit source, CompilationUnit ast,
			List<Problem> problems) throws Exception {
		Problem first = null;
		int errors = 0;
		for (Problem problem : problems) {
			if (ProblemCatalog.COMPILE_ERROR.equals(problem.id)) {
				if (first == null) {
					first = problem;
				}
				errors++;
			}
		}
		Problem warning = Problem.of(ProblemCatalog.DEPENDENCY_ERRORS,
				"dependency with errors", "Dependency has " + errors
						+ " Java error(s), so only its header and stubs were "
						+ "generated; the first: " + first.message).at(
				first.file, first.line, first.column);
		warnings.add(warning);
		System.err.println("J2C: warning: " + warning + " [" + warning.id
				+ "]");

		cur = ext;
		for (AbstractTypeDeclaration type : (Iterable<AbstractTypeDeclaration>) ast
				.types()) {
			ITypeBinding tb = type.resolveBinding();
			if (tb == null) {
				continue;
			}
			writeHeader(source, tb);
			addDoneNested(tb);
		}
	}

	/** Member types, whose headers TypeBindingHeaderWriter also writes */
	private void addDoneNested(ITypeBinding tb) {
		for (ITypeBinding nested : tb.getDeclaredTypes()) {
			addDone(nested, false);
			addDoneNested(nested);
		}
	}

	private void recordParameterNames(CompilationUnit ast) {
		ast.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodDeclaration node) {
				IMethodBinding method = node.resolveBinding();
				String key = methodKey(method);
				if (key == null) {
					return true;
				}

				String[] names = new String[node.parameters().size()];
				for (int i = 0; i < names.length; ++i) {
					SingleVariableDeclaration parameter = (SingleVariableDeclaration) node
							.parameters().get(i);
					names[i] = parameter.getName().getIdentifier();
				}
				parameterNames.put(key, names);
				return true;
			}
		});
	}

	private static String methodKey(IMethodBinding method) {
		if (method == null) {
			return null;
		}

		IMethodBinding declaration = method.getMethodDeclaration();
		return declaration == null ? null : declaration.getKey();
	}

	public String paramName(IMethodBinding method, int index) {
		String[] names = parameterNames.get(methodKey(method));
		if (names != null && index >= 0 && index < names.length) {
			return CName.keywords(names[index]);
		}
		return TransformUtil.paramName(method, index);
	}

	/**
	 * Reports every problem in the units that would stop their translation,
	 * without writing anything. The monitor counts checked units and stops
	 * the check with OperationCanceledException when cancelled.
	 */
	public List<Problem> check(IProgressMonitor monitor, SourceUnit... units)
			throws Exception {
		return check(monitor, null, units);
	}

	/** Receives each checked unit, with its parsed AST and its problems */
	public interface CheckListener {
		void checked(SourceUnit source, CompilationUnit ast,
				List<Problem> problems) throws Exception;
	}

	/**
	 * Like {@link #check(IProgressMonitor, SourceUnit...)}, also handing each
	 * unit's AST (of the source after lambda rewriting, which keeps line
	 * numbers) to the listener, when it is not null
	 */
	public List<Problem> check(final IProgressMonitor monitor,
			final CheckListener listener, SourceUnit... units) throws Exception {
		selection.addAll(Arrays.asList(units));
		final List<Problem> problems = new ArrayList<Problem>();
		monitor.beginTask("Checking " + units.length + " file(s)", units.length);
		project.parse(Arrays.asList(units), monitor,
				new SourceProject.UnitHandler() {
					@Override
					public void accept(SourceUnit source, CompilationUnit ast)
							throws Exception {
						List<Problem> found = QuickFixes.addFixes(project,
								source, problems(source, ast));
						problems.addAll(found);
						if (listener != null) {
							listener.checked(source, ast, found);
						}
						monitor.subTask("Checked " + source.file().getPath());
						monitor.worked(1);
					}
				});
		monitor.done();
		return problems;
	}

	private static String join(List<Problem> problems) {
		StringBuilder text = new StringBuilder();
		for (Problem problem : problems) {
			if (text.length() > 0) {
				text.append(System.lineSeparator());
			}
			text.append(problem);
		}
		return text.toString();
	}

	/**
	 * Compile errors and syntax that the writers would otherwise silently
	 * mistranslate, found before any output is written.
	 */
	private List<Problem> problems(final SourceUnit source,
			final CompilationUnit ast) {
		final List<Problem> problems = new ArrayList<Problem>();
		for (IProblem p : ast.getProblems()) {
			if (p.isError()) {
				problems.add(Problem.of(ProblemCatalog.COMPILE_ERROR,
						"compile error", p.getMessage()).at(source.file(),
						p.getSourceLineNumber(),
						ast.getColumnNumber(p.getSourceStart()) + 1));
			}
		}

		if (tests && selection.contains(source)) {
			problems.addAll(JUnitSupport.problems(source.file(), ast));
		}

		if (mocks && selection.contains(source)) {
			problems.addAll(MockWriter.problems(this, source.file(), ast));
		}

		// Lambdas and method references are rewritten into anonymous classes
		// before parsing; any left were explained by the rewriter
		final Set<Integer> explained = new HashSet<Integer>();
		for (Problem problem : project.desugarProblems(source)) {
			problems.add(problem);
			explained.add(problem.line);
		}

		ast.accept(new ASTVisitor() {
			@Override
			public void preVisit(ASTNode node) {
				int line = ast.getLineNumber(node.getStartPosition());
				if ((node instanceof LambdaExpression || node instanceof MethodReference)
						&& explained.contains(line)) {
					return;
				}
				Problem problem = unsupportedFeature(node);
				if (problem != null) {
					problems.add(problem.at(source.file(), line,
							ast.getColumnNumber(node.getStartPosition()) + 1));
				}
			}
		});
		return problems;
	}

	private Problem unsupportedFeature(ASTNode node) {
		if (node instanceof LambdaExpression) {
			return Problem.of(ProblemCatalog.LAMBDA_UNTRANSLATED,
					"lambda expression",
					"Java 8 lambda expression could not be translated");
		}
		if (node instanceof MethodReference) {
			return Problem.of(ProblemCatalog.LAMBDA_UNTRANSLATED,
					"method reference",
					"Java 8 method reference could not be translated");
		}
		if (node instanceof PatternInstanceofExpression) {
			// Pattern variables are declared before their enclosing statement
			for (ASTNode parent = node.getParent(); parent != null
					&& !(parent instanceof BodyDeclaration); parent = parent
					.getParent()) {
				if (parent instanceof Statement) {
					return null;
				}
			}
			return Problem.of(ProblemCatalog.PATTERN_OUTSIDE_METHOD,
					"instanceof pattern",
					"instanceof patterns outside method bodies, such as in "
							+ "field initializers, are not supported");
		}
		if (sealedTypes == SealedTypes.REJECT) {
			return sealedFeature(node);
		}
		return null;
	}

	private static Problem sealedFeature(ASTNode node) {
		String feature = null;
		if (node instanceof AbstractTypeDeclaration) {
			ITypeBinding binding = ((AbstractTypeDeclaration) node)
					.resolveBinding();
			if (binding != null
					&& (Modifier.isSealed(binding.getModifiers())
							|| Modifier.isNonSealed(
									binding.getModifiers()))) {
				feature = Modifier.isSealed(binding.getModifiers())
						? "sealed type" : "non-sealed type";
			} else if (Modifier.isSealed(
					((AbstractTypeDeclaration) node).getModifiers())) {
				feature = "sealed type";
			} else if (Modifier.isNonSealed(
					((AbstractTypeDeclaration) node).getModifiers())) {
				feature = "non-sealed type";
			}
		}

		if (feature == null) {
			return null;
		}
		return Problem.of(feature.equals("sealed type") ? ProblemCatalog.SEALED_REJECTED
				: ProblemCatalog.NON_SEALED_REJECTED, feature, "Java 17 "
				+ feature + " rejected by --sealed-types reject");
	}

	private void writeHeader(SourceUnit unit, ITypeBinding tb)
			throws Exception {
		addDone(tb, sel == cur);
		TypeBindingHeaderWriter hw = new TypeBindingHeaderWriter(getRoot(unit),
				this, tb);
		hw.write();
	}

	private void writeImpl(IProgressMonitor monitor, SourceUnit unit,
			CompilationUnit cu) throws Exception {
		monitor.subTask("Processing " + unit.file().getPath() + " ("
				+ done.size() + " types done, "
				+ todo.size() + " units and " + hardDeps.size()
				+ " dependencies pending)");
		checkCanceled(monitor);
		UnitInfo ui = new UnitInfo();

		cu.accept(new TypeInfoVisitor(ui));

		IPath unitRoot = getRoot(unit);

		for (AbstractTypeDeclaration type : (Iterable<AbstractTypeDeclaration>) cu
				.types()) {
			if (type instanceof TypeDeclaration) {
				TypeDeclaration td = (TypeDeclaration) type;
				write(ui, unitRoot, td);
			} else if (type instanceof AnnotationTypeDeclaration) {
				AnnotationTypeDeclaration td = (AnnotationTypeDeclaration) type;
				write(ui, unitRoot, td);
			} else if (type instanceof EnumDeclaration) {
				EnumDeclaration td = (EnumDeclaration) type;

				write(ui, unitRoot, td);
			} else if (type instanceof RecordDeclaration) {
				write(ui, unitRoot, (RecordDeclaration) type);
			}
		}

		for (ITypeBinding tb : ui.types.keySet()) {
			addDone(tb, cur == sel);
		}
		monitor.worked(1);
	}

	public void write(UnitInfo ui, IPath unitRoot, EnumDeclaration node) {
		TypeInfo typeInfo = ui.types.get(node.resolveBinding());
		try {
			ImplWriter iw = new ImplWriter(unitRoot, this, ui, typeInfo);
			iw.write(node);

			HeaderWriter hw = new HeaderWriter(unitRoot, this, ui, typeInfo);
			hw.write(node);

			writeExtras(unitRoot, typeInfo);
		} catch (Exception e) {
			throw generationFailure(typeInfo, e);
		}
	}

	public void write(UnitInfo ui, IPath unitRoot,
			AnnotationTypeDeclaration node) {
		TypeInfo typeInfo = ui.types.get(node.resolveBinding());
		try {
			ImplWriter iw = new ImplWriter(unitRoot, this, ui, typeInfo);
			iw.write(node);

			HeaderWriter hw = new HeaderWriter(unitRoot, this, ui, typeInfo);
			hw.write(node);

			writeExtras(unitRoot, typeInfo);
		} catch (Exception e) {
			throw generationFailure(typeInfo, e);
		}
	}

	public void write(UnitInfo ui, IPath unitRoot, TypeDeclaration node) {
		TypeInfo typeInfo = ui.types.get(node.resolveBinding());
		try {
			ImplWriter iw = new ImplWriter(unitRoot, this, ui, typeInfo);
			iw.write(node);

			HeaderWriter hw = new HeaderWriter(unitRoot, this, ui, typeInfo);
			hw.write(node);

			writeExtras(unitRoot, typeInfo);
		} catch (Exception e) {
			throw generationFailure(typeInfo, e);
		}
	}

	public void write(UnitInfo ui, IPath unitRoot, RecordDeclaration node) {
		TypeInfo typeInfo = ui.types.get(node.resolveBinding());
		try {
			ImplWriter iw = new ImplWriter(unitRoot, this, ui, typeInfo);
			iw.write(node);

			HeaderWriter hw = new HeaderWriter(unitRoot, this, ui, typeInfo);
			hw.write(node);

			writeExtras(unitRoot, typeInfo);
		} catch (Exception e) {
			throw generationFailure(typeInfo, e);
		}
	}

	public void write(UnitInfo ui, IPath unitRoot,
			AnonymousClassDeclaration node) {
		TypeInfo typeInfo = ui.types.get(node.resolveBinding());
		try {
			ImplWriter iw = new ImplWriter(unitRoot, this, ui, typeInfo);
			iw.write(node);

			HeaderWriter hw = new HeaderWriter(unitRoot, this, ui, typeInfo);
			hw.write(node);

			writeExtras(unitRoot, typeInfo);
		} catch (Exception e) {
			throw generationFailure(typeInfo, e);
		}
	}

	private RuntimeException generationFailure(TypeInfo typeInfo,
			Exception cause) {
		return new IllegalStateException("Failed to translate "
				+ typeInfo.type().getQualifiedName(), cause);
	}

	private void writeExtras(IPath unitRoot, TypeInfo ti) throws Exception {
		if (ti.hasNatives()) {
			StubWriter sw = new StubWriter(unitRoot, this, ti.type());
			sw.write(true, true);
		}

		if (ti.hasMain()) {
			// The main wrapper catches uncaught exceptions
			hardDep(resolve(Throwable.class));
			String filename = MainWriter.write(unitRoot, ti.type());
			sel.mains.add("src/" + filename);
		}
	}

	private void addDone(ITypeBinding tb, boolean isSel) {
		tb = tb.getErasure();
		if (done.add(tb.getBinaryName())) {
			if (isSel) {
				direct.add(tb);
			} else {
				deps.add(tb);
			}
		}
	}

	private void writeDeps(IProgressMonitor monitor) throws Exception {
		final Set<ITypeBinding> arrays = new TreeSet<ITypeBinding>(
				new BindingComparator());
		while (!hardDeps.isEmpty()) {
			final List<ITypeBinding> bindings = new ArrayList<ITypeBinding>();

			for (ITypeBinding tb : hardDeps) {
				if (done.contains(tb.getErasure().getBinaryName())) {
					continue;
				}

				if (tb.isPrimitive() || tb.isNullType()) {
					continue;
				}

				boolean isSel = false;
				if (tb.isArray()) {
					arrays.add(tb);
				} else {
					SourceUnit unit = getSourceUnit(tb);
					if (unit == null) {
						bindings.add(tb);
					} else {
						todo.add(unit);
						isSel = selection.contains(unit);
					}
				}

				addDone(tb, isSel);
			}

			hardDeps.clear();

			cur = ext;
			for (ITypeBinding tb : bindings) {
				checkCanceled(monitor);
				writeHeader(null, tb);
				monitor.subTask("Wrote the header of " + tb.getQualifiedName());
				monitor.worked(1);
			}

			cur = sel;
		}
	}

	public SourceUnit getSourceUnit(ITypeBinding tb) {
		return project.sourceUnit(tb);
	}

	private IPath getRoot(SourceUnit unit) {
		if (unit != null && selection.contains(unit)) {
			return root;
		}

		return root.append("ext");
	}

	public void hardDep(ITypeBinding dep) {
		if (dep != null && !done.contains(dep.getErasure().getBinaryName())) {
			TransformUtil.addDep(dep, hardDeps);
		}

		softDep(dep);
	}

	public void softDep(ITypeBinding dep) {
		if (dep == null)
			return;

		if (dep.isNullType())
			return;

		dep = dep.getErasure();

		if (dep.isArray()) {
			ITypeBinding ct = dep.getComponentType();
			if (!ct.isPrimitive() && !TransformUtil.same(ct, Object.class))
				return;
		}

		if (!forwards.containsKey(dep.getBinaryName())) {
			ForwardWriter.Info info = new ForwardWriter.Info(dep);
			forwards.put(dep.getBinaryName(), info);
		}
	}

	private final Map<String, ITypeBinding> bindings = new WeakHashMap<String, ITypeBinding>();

	public ITypeBinding resolve(Class<?> clazz) {
		return resolve(clazz.getName());
	}

	public ITypeBinding resolve(String name) {
		ITypeBinding ret;
		if (currentAST != null) {
			ret = currentAST.resolveWellKnownType(name);
			if (ret != null) {
				return ret;
			}
		}

		ret = bindings.get(name);
		if (ret != null) {
			return ret;
		}

		ret = project.resolve(name);
		if (ret == null) {
			throw new IllegalStateException("Could not resolve Java type "
					+ name);
		}
		bindings.put(name, ret);
		return ret;
	}

	public void addImpl(ITypeBinding tb) {
		cur.impls.add(TransformUtil.implPath(root, tb, "").makeRelativeTo(root)
				.toString());
	}

	public void addNative(ITypeBinding tb) {
		cur.natives.add(TransformUtil.implPath(root, tb, TransformUtil.NATIVE)
				.makeRelativeTo(root).toString());
	}

	public void addStub(ITypeBinding tb) {
		cur.stubs.add(TransformUtil.implPath(root, tb, TransformUtil.STUB)
				.makeRelativeTo(root).toString());
	}
}
