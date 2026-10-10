package se.arnetheduck.j2c.transform;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
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
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
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
import org.eclipse.jdt.core.dom.SwitchCase;
import org.eclipse.jdt.core.dom.SwitchExpression;
import org.eclipse.jdt.core.dom.TypeDeclaration;

import se.arnetheduck.j2c.snippets.GetSetSnippet;
import se.arnetheduck.j2c.snippets.ReplaceInvocation;

public class Transformer {
	private static final String ARRAY_HPP = "/se/arnetheduck/j2c/resources/Array.hpp";
	private static final String OBJECT_ARRAY_HPP = "/se/arnetheduck/j2c/resources/ObjectArray.hpp";
	private static final String SUB_ARRAY_HPP = "/se/arnetheduck/j2c/resources/SubArray.hpp";

	private final SourceProject project;

	private final String name;

	private final IPath root;

	private final SealedTypes sealedTypes;

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

	private final Set<String> done = new HashSet<String>();

	public List<Snippet> snippets = new ArrayList<Snippet>();

	protected AST currentAST;

	public void process(IProgressMonitor monitor, SourceUnit... units)
			throws Exception {
		monitor.subTask("Moving old files");
		renameOld();

		long start = System.currentTimeMillis();

		hardDep(resolve(ClassLoader.class));
		// The ObjectArray.hpp resource includes these unconditionally
		hardDep(resolve(Cloneable.class));
		hardDep(resolve(java.io.Serializable.class));
		selection.addAll(Arrays.asList(units));
		todo.addAll(selection);

		while (!todo.isEmpty()) {
			processSome(monitor);
		}

		writeResources();

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

	private void writeResources() throws IOException {
		// TODO Auto-generated method stub
		FileUtil.writeResource(ARRAY_HPP,
				TransformUtil.headerPath(root, "Array.hpp").toFile());
		FileUtil.writeResource(OBJECT_ARRAY_HPP,
				TransformUtil.headerPath(root, "ObjectArray.hpp").toFile());
		FileUtil.writeResource(SUB_ARRAY_HPP,
				TransformUtil.headerPath(root, "SubArray.hpp").toFile());
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
						if (selection.contains(source)) {
							cur = sel;
						} else {
							cur = ext;
						}
						try {
							List<Problem> problems = problems(source, ast);
							if (!problems.isEmpty()) {
								throw new TranslationException(join(problems));
							}
							writeImpl(monitor, source, ast);
						} finally {
							currentAST = null;
						}
					}
				});
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
	 * without writing anything.
	 */
	public List<Problem> check(IProgressMonitor monitor, SourceUnit... units)
			throws Exception {
		final List<Problem> problems = new ArrayList<Problem>();
		project.parse(Arrays.asList(units), monitor,
				new SourceProject.UnitHandler() {
					@Override
					public void accept(SourceUnit source, CompilationUnit ast) {
						problems.addAll(problems(source, ast));
					}
				});
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
				problems.add(Problem.of("compile error", p.getMessage()).at(
						source.file(), p.getSourceLineNumber()));
			}
		}

		ast.accept(new ASTVisitor() {
			@Override
			public void preVisit(ASTNode node) {
				Problem problem = unsupportedFeature(node);
				if (problem != null) {
					problems.add(problem.at(source.file(),
							ast.getLineNumber(node.getStartPosition())));
				}
			}
		});
		return problems;
	}

	private Problem unsupportedFeature(ASTNode node) {
		if (node instanceof LambdaExpression) {
			return Problem.of("lambda expression",
					"Java 8 lambda expressions are not supported");
		}
		if (node instanceof MethodReference) {
			return Problem.of("method reference",
					"Java 8 method references are not supported");
		}
		if (node instanceof SwitchExpression) {
			SwitchExpression switchExpression = (SwitchExpression) node;
			ITypeBinding selector = switchExpression.getExpression()
					.resolveTypeBinding();
			if (selector == null || selector.isPrimitive()) {
				return null;
			}
			if (!selector.isEnum()
					&& !"java.lang.String".equals(selector.getQualifiedName())) {
				return Problem.of("switch expression selector",
						"switch expressions on " + selector.getName()
								+ " are not supported; switch on the primitive value");
			}
			for (Object statement : switchExpression.statements()) {
				if (statement instanceof SwitchCase
						&& !((SwitchCase) statement).isSwitchLabeledRule()) {
					return Problem.of("switch expression selector",
							"switch expressions on " + selector.getName()
									+ " must use \"case X ->\" rules, not \"case X:\"");
				}
			}
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

		return feature == null ? null : Problem.of(feature, "Java 17 "
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
				writeHeader(null, tb);
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
		ITypeBinding ret;
		String name = clazz.getName();
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
