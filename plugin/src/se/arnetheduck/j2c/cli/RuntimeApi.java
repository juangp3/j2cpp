package se.arnetheduck.j2c.cli;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.RecordDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;

import se.arnetheduck.j2c.transform.SourceProject;
import se.arnetheduck.j2c.transform.SourceUnit;

/**
 * The Java API a J2C runtime provides: its types (by binary name), their
 * supertypes, and their non-private members, keyed as name(erased parameter
 * types) for methods, &lt;init&gt;(...) for constructors, and the bare name
 * for fields. j2c report compares the JDK API a program uses with it.
 *
 * The bundled runtime's API is generated into runtime/api.txt (packaged in
 * the JAR with the runtime, see BundledRuntime) by running this class's main with the runtime directory:
 *
 * <pre>
 * java -cp target/j2c-cli.jar se.arnetheduck.j2c.cli.RuntimeApi runtime &gt; runtime/api.txt
 * </pre>
 */
final class RuntimeApi {
	/** The bundled runtime's API inside the JAR */
	static final String RESOURCE = "/" + BundledRuntime.PREFIX + "api.txt";
	/** The bundled runtime's README inside the JAR */
	static final String README_RESOURCE = "/" + BundledRuntime.PREFIX
			+ "README.md";

	private static final String HEADER = "# J2C runtime API: the types and non-private members of runtime/java,\n"
			+ "# which j2c report compares the JDK API a program uses with.\n"
			+ "# Generated; do not edit. Regenerate with\n"
			+ "#   java -cp target/j2c-cli.jar se.arnetheduck.j2c.cli.RuntimeApi runtime > runtime/api.txt\n";

	/** Binary type name to its direct supertypes */
	private final Map<String, List<String>> supertypes = new TreeMap<String, List<String>>();
	/** Binary type name to its member keys */
	private final Map<String, Set<String>> members = new TreeMap<String, Set<String>>();
	/** Where it came from: BundledRuntime.NAME or the runtime directory */
	final String source;

	private RuntimeApi(String source) {
		this.source = source;
	}

	/** The API of the runtime bundled in the JAR, or null without one */
	static RuntimeApi bundled() throws IOException {
		InputStream in = RuntimeApi.class.getResourceAsStream(RESOURCE);
		if (in == null) {
			return null;
		}
		try {
			return read(new InputStreamReader(in, StandardCharsets.UTF_8),
					BundledRuntime.NAME);
		} finally {
			in.close();
		}
	}

	/** The bundled runtime's README, or null without one */
	static String bundledReadme() throws IOException {
		InputStream in = RuntimeApi.class.getResourceAsStream(README_RESOURCE);
		if (in == null) {
			return null;
		}
		try {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} finally {
			in.close();
		}
	}

	static RuntimeApi parse(String text, String source) throws IOException {
		return read(new StringReader(text), source);
	}

	private static RuntimeApi read(Reader reader, String source)
			throws IOException {
		RuntimeApi api = new RuntimeApi(source);
		BufferedReader lines = new BufferedReader(reader);
		Set<String> current = null;
		for (String line = lines.readLine(); line != null; line = lines
				.readLine()) {
			if (line.startsWith("#") || line.trim().isEmpty()) {
				continue;
			}
			if (line.startsWith("\t") || line.startsWith(" ")) {
				if (current != null) {
					current.add(line.trim());
				}
				continue;
			}
			String[] names = line.trim().split(" ");
			List<String> supers = new ArrayList<String>();
			for (int i = 1; i < names.length; ++i) {
				supers.add(names[i]);
			}
			api.supertypes.put(names[0], supers);
			current = new TreeSet<String>();
			api.members.put(names[0], current);
		}
		return api;
	}

	/** Reads the API from a runtime directory's Java sources */
	static RuntimeApi fromDirectory(File runtime) throws Exception {
		File java = new File(runtime, "java");
		if (!new File(java, "java/lang/Object.java").isFile()) {
			throw new IllegalArgumentException("Not a J2C runtime (missing "
					+ "java/java/lang/Object.java): " + runtime);
		}
		List<File> files = new ArrayList<File>();
		collect(java, files);
		Collections.sort(files);
		SourceProject project = new SourceProject(
				Collections.singletonList(java), Collections.<File> emptyList(),
				StandardCharsets.UTF_8);
		project.setSystemLibrary(false);
		List<SourceUnit> units = new ArrayList<SourceUnit>();
		for (File file : files) {
			units.add(project.getUnit(file));
		}
		final RuntimeApi api = new RuntimeApi(runtime.getPath());
		project.parse(units, new NullProgressMonitor(),
				new SourceProject.UnitHandler() {
					@Override
					public void accept(SourceUnit unit, CompilationUnit ast) {
						ast.accept(new ASTVisitor() {
							@Override
							public boolean visit(TypeDeclaration node) {
								return add(node);
							}

							@Override
							public boolean visit(EnumDeclaration node) {
								return add(node);
							}

							@Override
							public boolean visit(RecordDeclaration node) {
								return add(node);
							}

							@Override
							public boolean visit(AnnotationTypeDeclaration node) {
								return add(node);
							}

							private boolean add(AbstractTypeDeclaration node) {
								if (node.getParent().getNodeType() == ASTNode.COMPILATION_UNIT
										|| node.getParent() instanceof AbstractTypeDeclaration) {
									api.add(node.resolveBinding());
								}
								return true;
							}
						});
					}
				});
		return api;
	}

	private static void collect(File directory, List<File> files) {
		File[] entries = directory.listFiles();
		if (entries == null) {
			return;
		}
		for (File entry : entries) {
			if (entry.isDirectory()) {
				collect(entry, files);
			} else if (entry.getName().endsWith(".java")
					&& !entry.getName().equals("package-info.java")
					&& !entry.getName().equals("module-info.java")) {
				files.add(entry);
			}
		}
	}

	private void add(ITypeBinding type) {
		if (type == null || Modifier.isPrivate(type.getModifiers())) {
			return;
		}
		String name = erasedName(type);
		List<String> supers = new ArrayList<String>();
		if (type.getSuperclass() != null) {
			supers.add(erasedName(type.getSuperclass()));
		}
		for (ITypeBinding iface : type.getInterfaces()) {
			supers.add(erasedName(iface));
		}
		Set<String> keys = new TreeSet<String>();
		for (IMethodBinding method : type.getDeclaredMethods()) {
			if (!Modifier.isPrivate(method.getModifiers())) {
				keys.add(methodKey(method));
			}
		}
		for (IVariableBinding field : type.getDeclaredFields()) {
			if (!Modifier.isPrivate(field.getModifiers())) {
				keys.add(field.getName());
			}
		}
		supertypes.put(name, supers);
		members.put(name, keys);
	}

	/** Writes the API in the format {@link #parse} reads */
	void write(PrintStream out) {
		StringBuilder text = new StringBuilder(HEADER);
		for (Map.Entry<String, Set<String>> type : members.entrySet()) {
			text.append(type.getKey());
			for (String supertype : supertypes.get(type.getKey())) {
				text.append(' ').append(supertype);
			}
			text.append('\n');
			for (String member : type.getValue()) {
				text.append('\t').append(member).append('\n');
			}
		}
		out.print(text);
		out.flush();
	}

	boolean hasType(String binaryName) {
		return members.containsKey(binaryName);
	}

	int typeCount() {
		return members.size();
	}

	int memberCount(String binaryName) {
		Set<String> keys = members.get(binaryName);
		return keys == null ? 0 : keys.size();
	}

	Set<String> types() {
		return members.keySet();
	}

	/**
	 * Whether the type, a supertype, or Object declares the member. A class
	 * without a declared constructor has the default one.
	 */
	boolean hasMember(String binaryName, String key) {
		if (!hasType(binaryName)) {
			return false;
		}
		if (key.startsWith("<init>(")) {
			Set<String> own = members.get(binaryName);
			if (own.contains(key)) {
				return true;
			}
			for (String member : own) {
				if (member.startsWith("<init>(")) {
					return false;
				}
			}
			return key.equals("<init>()");
		}
		Set<String> seen = new HashSet<String>();
		Deque<String> todo = new ArrayDeque<String>();
		todo.add(binaryName);
		todo.add("java.lang.Object");
		while (!todo.isEmpty()) {
			String type = todo.poll();
			if (!seen.add(type)) {
				continue;
			}
			Set<String> keys = members.get(type);
			if (keys == null) {
				continue;
			}
			if (keys.contains(key)) {
				return true;
			}
			todo.addAll(supertypes.get(type));
		}
		return false;
	}

	/** name(erased parameter types), or &lt;init&gt;(...) for a constructor */
	static String methodKey(IMethodBinding method) {
		IMethodBinding declaration = method.getMethodDeclaration();
		StringBuilder key = new StringBuilder(declaration.isConstructor() ? "<init>"
				: declaration.getName());
		key.append('(');
		ITypeBinding[] parameters = declaration.getParameterTypes();
		for (int i = 0; i < parameters.length; ++i) {
			if (i > 0) {
				key.append(',');
			}
			key.append(erasedName(parameters[i]));
		}
		return key.append(')').toString();
	}

	/** The binary name of the type's erasure, with [] for arrays */
	static String erasedName(ITypeBinding type) {
		if (type.isArray()) {
			StringBuilder name = new StringBuilder(erasedName(type
					.getElementType()));
			for (int i = 0; i < type.getDimensions(); ++i) {
				name.append("[]");
			}
			return name.toString();
		}
		if (type.isPrimitive()) {
			return type.getName();
		}
		// The erasure of a type variable is the erasure of its first bound
		ITypeBinding erasure = type.getErasure();
		if (erasure.isTypeVariable() || erasure.isCapture()
				|| erasure.isWildcardType()) {
			return "java.lang.Object";
		}
		String binary = erasure.getBinaryName();
		return binary != null ? binary : erasure.getQualifiedName();
	}

	public static void main(String[] args) throws Exception {
		if (args.length != 1) {
			System.err.println("Usage: RuntimeApi <runtime-directory>");
			System.exit(2);
		}
		PrintStream out = Main.utf8(System.out);
		// Parsing reports nothing on stdout, but keep it for the API alone
		System.setOut(System.err);
		fromDirectory(new File(args[0])).write(out);
	}
}
