package se.arnetheduck.j2c.cli;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NameQualifiedType;
import org.eclipse.jdt.core.dom.QualifiedType;
import org.eclipse.jdt.core.dom.RecordDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.TypeDeclaration;

import se.arnetheduck.j2c.transform.Problem;
import se.arnetheduck.j2c.transform.ProblemCatalog;
import se.arnetheduck.j2c.transform.SourceUnit;
import se.arnetheduck.j2c.transform.Transformer;

/**
 * The migration readiness report: how much of the input translates, what
 * blocks the rest, and which JDK API the code uses compared with what the
 * J2C runtime provides.
 *
 * A file, type or method is translatable when no problem that j2c --check
 * reports lies within its lines (for a type, including its nested types and
 * members). A problem blocks its file, every type around it and the method
 * around it. Types are named type declarations (classes, interfaces, enums,
 * records, annotations, including nested and local ones); methods are the
 * methods and constructors declared in them, not those of anonymous classes
 * (which belong to the method around them).
 */
final class Report {
	/** Problems of test features, reported with --tests */
	static final String TEST_PREFIX = "J2C-TEST-";
	/** Locations listed for each problem id */
	private static final int TOP_LOCATIONS = 5;
	/** JDK rows listed in Markdown; the JSON has all of them */
	private static final int MARKDOWN_ROWS = 25;

	/** A type or method declaration and the lines it spans */
	static final class Span {
		final String name;
		final int start;
		final int end;
		boolean blocked;

		Span(String name, int start, int end) {
			this.name = name;
			this.start = start;
			this.end = end;
		}
	}

	static final class FileInfo {
		final File file;
		String packageName = "";
		final List<Span> types = new ArrayList<Span>();
		final List<Span> methods = new ArrayList<Span>();
		final List<Problem> problems = new ArrayList<Problem>();

		FileInfo(File file) {
			this.file = file;
		}
	}

	static final class TypeUse {
		final String name;
		final String binaryName;
		int uses;
		boolean provided;
		final Map<String, MemberUse> members = new TreeMap<String, MemberUse>();

		TypeUse(String name, String binaryName) {
			this.name = name;
			this.binaryName = binaryName;
		}
	}

	static final class MemberUse {
		/** name(parameter types), <init>(...) for constructors, or a field name */
		final String name;
		final String key;
		int uses;
		boolean provided;

		MemberUse(String name, String key) {
			this.name = name;
			this.key = key;
		}
	}

	static final class Result {
		J2cService.CheckResult check;
		final List<FileInfo> files = new ArrayList<FileInfo>();
		final Map<String, TypeUse> jdk = new TreeMap<String, TypeUse>();
		RuntimeApi runtime;
		boolean tests;
	}

	private final J2cService service;
	/** Markdown names files relative to this directory */
	private final File base;

	Report(J2cService service, File base) {
		this.service = service;
		this.base = base;
	}

	Result run(J2cService.Request request) throws Exception {
		final Result result = new Result();
		result.tests = request.tests;
		result.runtime = request.runtime != null ? RuntimeApi
				.fromDirectory(request.runtime) : RuntimeApi.bundled();
		final Map<File, FileInfo> files = new TreeMap<File, FileInfo>();
		result.check = service.check(request, new Transformer.CheckListener() {
			@Override
			public void checked(SourceUnit source, CompilationUnit ast,
					List<Problem> problems) {
				FileInfo info = new FileInfo(source.file());
				info.problems.addAll(problems);
				if (ast.getPackage() != null) {
					info.packageName = ast.getPackage().getName()
							.getFullyQualifiedName();
				}
				ast.accept(new Scanner(ast, info, result.jdk));
				files.put(source.file(), info);
			}
		});
		result.files.addAll(files.values());
		for (FileInfo file : result.files) {
			for (Problem problem : file.problems) {
				block(file.types, problem.line);
				block(file.methods, problem.line);
			}
		}
		for (TypeUse type : result.jdk.values()) {
			type.provided = result.runtime != null
					&& result.runtime.hasType(type.binaryName);
			for (MemberUse member : type.members.values()) {
				member.provided = type.provided
						&& result.runtime.hasMember(type.binaryName, member.key);
			}
		}
		return result;
	}

	private static void block(List<Span> spans, int line) {
		for (Span span : spans) {
			if (line >= span.start && line <= span.end) {
				span.blocked = true;
			}
		}
	}

	/** Collects declarations and JDK API uses of one unit */
	private static final class Scanner extends ASTVisitor {
		private final CompilationUnit ast;
		private final FileInfo info;
		private final Map<String, TypeUse> jdk;

		Scanner(CompilationUnit ast, FileInfo info, Map<String, TypeUse> jdk) {
			this.ast = ast;
			this.info = info;
			this.jdk = jdk;
		}

		private int startLine(org.eclipse.jdt.core.dom.ASTNode node) {
			return ast.getLineNumber(node.getStartPosition());
		}

		private int endLine(org.eclipse.jdt.core.dom.ASTNode node) {
			return ast.getLineNumber(node.getStartPosition()
					+ Math.max(node.getLength() - 1, 0));
		}

		private boolean type(AbstractTypeDeclaration node) {
			ITypeBinding binding = node.resolveBinding();
			String name = null;
			if (binding != null) {
				name = binding.getQualifiedName();
				if (name.isEmpty()) {
					name = binding.getBinaryName();
				}
			}
			if (name == null || name.isEmpty()) {
				name = node.getName().getIdentifier();
			}
			info.types.add(new Span(name, startLine(node), endLine(node)));
			return true;
		}

		@Override
		public boolean visit(TypeDeclaration node) {
			return type(node);
		}

		@Override
		public boolean visit(EnumDeclaration node) {
			return type(node);
		}

		@Override
		public boolean visit(RecordDeclaration node) {
			return type(node);
		}

		@Override
		public boolean visit(AnnotationTypeDeclaration node) {
			return type(node);
		}

		@Override
		public boolean visit(MethodDeclaration node) {
			if (node.getParent() instanceof AbstractTypeDeclaration) {
				IMethodBinding binding = node.resolveBinding();
				String owner = info.types.isEmpty() ? "" : enclosingType(node);
				String name = binding == null ? node.getName().getIdentifier()
						+ "(...)" : displayKey(RuntimeApi.methodKey(binding));
				if (node.isConstructor() && binding != null) {
					name = name.replaceFirst("^<init>", node.getName()
							.getIdentifier());
				}
				info.methods.add(new Span(owner + "." + name, startLine(node),
						endLine(node)));
			}
			return true;
		}

		private String enclosingType(MethodDeclaration node) {
			ITypeBinding type = ((AbstractTypeDeclaration) node.getParent())
					.resolveBinding();
			if (type != null && !type.getQualifiedName().isEmpty()) {
				return type.getQualifiedName();
			}
			return ((AbstractTypeDeclaration) node.getParent()).getName()
					.getIdentifier();
		}

		@Override
		public boolean visit(SimpleType node) {
			typeUse(node.resolveBinding());
			return true;
		}

		@Override
		public boolean visit(QualifiedType node) {
			typeUse(node.resolveBinding());
			return true;
		}

		@Override
		public boolean visit(NameQualifiedType node) {
			typeUse(node.resolveBinding());
			return true;
		}

		@Override
		public boolean visit(MethodInvocation node) {
			IMethodBinding method = node.resolveMethodBinding();
			if (method != null) {
				ITypeBinding receiver = node.getExpression() == null ? null
						: node.getExpression().resolveTypeBinding();
				memberUse(receiver, method.getDeclaringClass(),
						RuntimeApi.methodKey(method));
			}
			return true;
		}

		@Override
		public boolean visit(SuperMethodInvocation node) {
			IMethodBinding method = node.resolveMethodBinding();
			if (method != null) {
				memberUse(null, method.getDeclaringClass(),
						RuntimeApi.methodKey(method));
			}
			return true;
		}

		@Override
		public boolean visit(ClassInstanceCreation node) {
			IMethodBinding constructor = node.resolveConstructorBinding();
			// An anonymous class's own constructor is not the JDK's; its
			// type is counted as a type use
			if (constructor != null && node.getAnonymousClassDeclaration() == null) {
				memberUse(null, constructor.getDeclaringClass(),
						RuntimeApi.methodKey(constructor));
			}
			return true;
		}

		@Override
		public boolean visit(SuperConstructorInvocation node) {
			IMethodBinding constructor = node.resolveConstructorBinding();
			if (constructor != null) {
				memberUse(null, constructor.getDeclaringClass(),
						RuntimeApi.methodKey(constructor));
			}
			return true;
		}

		@Override
		public boolean visit(SimpleName node) {
			IBinding binding = node.resolveBinding();
			if (binding instanceof IVariableBinding
					&& ((IVariableBinding) binding).isField()
					&& !node.isDeclaration()) {
				IVariableBinding field = ((IVariableBinding) binding)
						.getVariableDeclaration();
				memberUse(null, field.getDeclaringClass(), field.getName());
			}
			return true;
		}

		/** The use's type: the receiver's when it is a JDK type */
		private void memberUse(ITypeBinding receiver, ITypeBinding declaring,
				String key) {
			TypeUse type = typeUse(isJdk(normalize(receiver)) ? receiver
					: declaring);
			if (type == null) {
				return;
			}
			MemberUse member = type.members.get(key);
			if (member == null) {
				member = new MemberUse(displayKey(key), key);
				type.members.put(key, member);
			}
			member.uses++;
		}

		private TypeUse typeUse(ITypeBinding binding) {
			ITypeBinding type = normalize(binding);
			if (!isJdk(type)) {
				return null;
			}
			String binaryName = RuntimeApi.erasedName(type);
			TypeUse use = jdk.get(binaryName);
			if (use == null) {
				use = new TypeUse(type.getErasure().getQualifiedName(),
						binaryName);
				jdk.put(binaryName, use);
			}
			use.uses++;
			return use;
		}
	}

	private static ITypeBinding normalize(ITypeBinding type) {
		if (type == null) {
			return null;
		}
		if (type.isArray()) {
			type = type.getElementType();
		}
		return type.isPrimitive() || type.isNullType() ? null : type.getErasure();
	}

	/** Types of the Java class library: java.*, javax.* and jdk.* */
	private static boolean isJdk(ITypeBinding type) {
		if (type == null || type.isRecovered() || type.isTypeVariable()
				|| type.isAnonymous() || type.isLocal()
				|| type.getPackage() == null) {
			return false;
		}
		String name = type.getPackage().getName();
		return name.startsWith("java.") || name.startsWith("javax.")
				|| name.startsWith("jdk.");
	}

	/** add(java.lang.Object) as add(Object), java.util.Map$Entry as Map.Entry */
	static String displayKey(String key) {
		int open = key.indexOf('(');
		if (open < 0) {
			return key;
		}
		StringBuilder display = new StringBuilder(key.substring(0, open + 1));
		String parameters = key.substring(open + 1, key.length() - 1);
		if (!parameters.isEmpty()) {
			String separator = "";
			for (String parameter : parameters.split(",")) {
				display.append(separator).append(simpleName(parameter));
				separator = ", ";
			}
		}
		return display.append(')').toString();
	}

	private static String simpleName(String binaryName) {
		String name = binaryName.substring(binaryName.lastIndexOf('.') + 1);
		return name.replace('$', '.');
	}

	// JSON

	private static int translatable(List<Span> spans) {
		int count = 0;
		for (Span span : spans) {
			if (!span.blocked) {
				count++;
			}
		}
		return count;
	}

	private static double percent(int part, int total) {
		return total == 0 ? 100.0 : Math.round(part * 1000.0 / total) / 10.0;
	}

	private static Map<String, Object> count(int total, int translatable) {
		Map<String, Object> json = new LinkedHashMap<String, Object>();
		json.put("total", total);
		json.put("translatable", translatable);
		json.put("percent", percent(translatable, total));
		return json;
	}

	/** Problem counts by id, most frequent first */
	private static final class IdCount {
		final String id;
		final List<Problem> problems = new ArrayList<Problem>();
		final List<File> files = new ArrayList<File>();
		int fixable;

		IdCount(String id) {
			this.id = id;
		}
	}

	private static List<IdCount> byId(List<Problem> problems) {
		Map<String, IdCount> counts = new TreeMap<String, IdCount>();
		for (Problem problem : problems) {
			IdCount count = counts.get(problem.id);
			if (count == null) {
				count = new IdCount(problem.id);
				counts.put(problem.id, count);
			}
			count.problems.add(problem);
			if (!count.files.contains(problem.file)) {
				count.files.add(problem.file);
			}
			if (!problem.fixes.isEmpty()) {
				count.fixable++;
			}
		}
		List<IdCount> sorted = new ArrayList<IdCount>(counts.values());
		Collections.sort(sorted, new Comparator<IdCount>() {
			@Override
			public int compare(IdCount a, IdCount b) {
				if (a.problems.size() != b.problems.size()) {
					return b.problems.size() - a.problems.size();
				}
				return a.id.compareTo(b.id);
			}
		});
		return sorted;
	}

	private static List<Problem> sortedByLocation(List<Problem> problems) {
		List<Problem> sorted = new ArrayList<Problem>(problems);
		Collections.sort(sorted, new Comparator<Problem>() {
			@Override
			public int compare(Problem a, Problem b) {
				int file = a.file.getPath().compareTo(b.file.getPath());
				if (file != 0) {
					return file;
				}
				return a.line != b.line ? a.line - b.line : a.column - b.column;
			}
		});
		return sorted;
	}

	private static List<Problem> problems(Result result) {
		List<Problem> problems = new ArrayList<Problem>();
		for (FileInfo file : result.files) {
			problems.addAll(file.problems);
		}
		return problems;
	}

	private static List<Problem> testProblems(List<Problem> problems) {
		List<Problem> tests = new ArrayList<Problem>();
		for (Problem problem : problems) {
			if (problem.id.startsWith(TEST_PREFIX)) {
				tests.add(problem);
			}
		}
		return tests;
	}

	private static Map<String, Object> blockersJson(List<Problem> problems) {
		Map<String, Object> json = new LinkedHashMap<String, Object>();
		int fixable = 0;
		List<File> files = new ArrayList<File>();
		for (Problem problem : problems) {
			if (!problem.fixes.isEmpty()) {
				fixable++;
			}
			if (!files.contains(problem.file)) {
				files.add(problem.file);
			}
		}
		json.put("problemCount", problems.size());
		json.put("filesWithProblems", files.size());
		json.put("fixable", fixable);
		List<Object> ids = new ArrayList<Object>();
		for (IdCount count : byId(problems)) {
			Map<String, Object> item = new LinkedHashMap<String, Object>();
			ProblemCatalog.Entry entry = ProblemCatalog.get(count.id);
			item.put("id", count.id);
			item.put("title", entry == null ? null : entry.title);
			item.put("count", count.problems.size());
			item.put("files", count.files.size());
			item.put("fixable", count.fixable);
			item.put("hint", entry == null ? null : entry.hint);
			List<Object> locations = new ArrayList<Object>();
			for (Problem problem : sortedByLocation(count.problems)) {
				if (locations.size() == TOP_LOCATIONS) {
					break;
				}
				Map<String, Object> location = new LinkedHashMap<String, Object>();
				location.put("file", problem.file.getPath());
				location.put("line", problem.line);
				location.put("column", problem.column);
				location.put("message", problem.message);
				locations.add(location);
			}
			item.put("locations", locations);
			ids.add(item);
		}
		json.put("byId", ids);
		return json;
	}

	/** Packages with their files and problems, most problems first */
	private static final class PackageInfo {
		final String name;
		int files;
		int translatableFiles;
		final List<Problem> problems = new ArrayList<Problem>();

		PackageInfo(String name) {
			this.name = name;
		}
	}

	private static List<PackageInfo> packages(Result result) {
		Map<String, PackageInfo> packages = new TreeMap<String, PackageInfo>();
		for (FileInfo file : result.files) {
			PackageInfo info = packages.get(file.packageName);
			if (info == null) {
				info = new PackageInfo(file.packageName);
				packages.put(file.packageName, info);
			}
			info.files++;
			if (file.problems.isEmpty()) {
				info.translatableFiles++;
			}
			info.problems.addAll(file.problems);
		}
		List<PackageInfo> sorted = new ArrayList<PackageInfo>(packages.values());
		Collections.sort(sorted, new Comparator<PackageInfo>() {
			@Override
			public int compare(PackageInfo a, PackageInfo b) {
				if (a.problems.size() != b.problems.size()) {
					return b.problems.size() - a.problems.size();
				}
				return a.name.compareTo(b.name);
			}
		});
		return sorted;
	}

	private static Map<String, Integer> idCounts(List<Problem> problems) {
		Map<String, Integer> counts = new TreeMap<String, Integer>();
		for (Problem problem : problems) {
			Integer count = counts.get(problem.id);
			counts.put(problem.id, count == null ? 1 : count + 1);
		}
		return counts;
	}

	/** JDK types by uses, most used first */
	private static List<TypeUse> jdkTypes(Result result) {
		List<TypeUse> types = new ArrayList<TypeUse>(result.jdk.values());
		Collections.sort(types, new Comparator<TypeUse>() {
			@Override
			public int compare(TypeUse a, TypeUse b) {
				return a.uses != b.uses ? b.uses - a.uses : a.name
						.compareTo(b.name);
			}
		});
		return types;
	}

	private static List<MemberUse> members(TypeUse type) {
		List<MemberUse> members = new ArrayList<MemberUse>(type.members.values());
		Collections.sort(members, new Comparator<MemberUse>() {
			@Override
			public int compare(MemberUse a, MemberUse b) {
				return a.uses != b.uses ? b.uses - a.uses : a.name
						.compareTo(b.name);
			}
		});
		return members;
	}

	/** A JDK member with its type, for lists across types */
	private static final class Qualified {
		final TypeUse type;
		final MemberUse member;

		Qualified(TypeUse type, MemberUse member) {
			this.type = type;
			this.member = member;
		}

		String display() {
			if (member.name.startsWith("<init>(")) {
				return "new " + type.name + member.name.substring("<init>".length());
			}
			return type.name + "." + member.name;
		}
	}

	/** Members not in the runtime, most used first */
	private static List<Qualified> missingMembers(Result result) {
		List<Qualified> missing = new ArrayList<Qualified>();
		for (TypeUse type : result.jdk.values()) {
			for (MemberUse member : type.members.values()) {
				if (!member.provided) {
					missing.add(new Qualified(type, member));
				}
			}
		}
		Collections.sort(missing, new Comparator<Qualified>() {
			@Override
			public int compare(Qualified a, Qualified b) {
				if (a.member.uses != b.member.uses) {
					return b.member.uses - a.member.uses;
				}
				return a.display().compareTo(b.display());
			}
		});
		return missing;
	}

	private static final class JdkSummary {
		int types;
		int typesProvided;
		int members;
		int membersProvided;
		int memberUses;
		int memberUsesProvided;
	}

	private static JdkSummary jdkSummary(Result result) {
		JdkSummary summary = new JdkSummary();
		for (TypeUse type : result.jdk.values()) {
			summary.types++;
			if (type.provided) {
				summary.typesProvided++;
			}
			for (MemberUse member : type.members.values()) {
				summary.members++;
				summary.memberUses += member.uses;
				if (member.provided) {
					summary.membersProvided++;
					summary.memberUsesProvided += member.uses;
				}
			}
		}
		return summary;
	}

	Map<String, Object> json(Result result, List<String> inputs) {
		Map<String, Object> json = J2cService.header("report");
		List<Problem> problems = problems(result);
		json.put("status", problems.isEmpty() ? "ok" : "problems");
		json.put("files", result.files.size());
		json.put("tests", result.tests);

		int translatableFiles = 0;
		int types = 0;
		int translatableTypes = 0;
		int methods = 0;
		int translatableMethods = 0;
		for (FileInfo file : result.files) {
			if (file.problems.isEmpty()) {
				translatableFiles++;
			}
			types += file.types.size();
			translatableTypes += translatable(file.types);
			methods += file.methods.size();
			translatableMethods += translatable(file.methods);
		}
		Map<String, Object> totals = new LinkedHashMap<String, Object>();
		totals.put("files", count(result.files.size(), translatableFiles));
		totals.put("types", count(types, translatableTypes));
		totals.put("methods", count(methods, translatableMethods));
		json.put("totals", totals);

		Map<String, Object> blockers = blockersJson(problems);
		List<Object> packageList = new ArrayList<Object>();
		for (PackageInfo info : packages(result)) {
			Map<String, Object> item = new LinkedHashMap<String, Object>();
			item.put("package", info.name);
			item.put("files", info.files);
			item.put("translatableFiles", info.translatableFiles);
			item.put("problemCount", info.problems.size());
			item.put("byId", idCounts(info.problems));
			packageList.add(item);
		}
		blockers.put("byPackage", packageList);
		json.put("blockers", blockers);
		json.put("testBlockers", result.tests ? blockersJson(testProblems(problems))
				: null);

		Map<String, Object> jdk = new LinkedHashMap<String, Object>();
		JdkSummary summary = jdkSummary(result);
		jdk.put("runtime", result.runtime == null ? null : result.runtime.source);
		jdk.put("typesUsed", summary.types);
		jdk.put("typesProvided", summary.typesProvided);
		jdk.put("membersUsed", summary.members);
		jdk.put("membersProvided", summary.membersProvided);
		jdk.put("memberUses", summary.memberUses);
		jdk.put("memberUsesProvided", summary.memberUsesProvided);
		List<Object> typeList = new ArrayList<Object>();
		for (TypeUse type : jdkTypes(result)) {
			Map<String, Object> item = new LinkedHashMap<String, Object>();
			item.put("type", type.name);
			item.put("uses", type.uses);
			item.put("provided", type.provided);
			List<Object> memberList = new ArrayList<Object>();
			for (MemberUse member : members(type)) {
				Map<String, Object> entry = new LinkedHashMap<String, Object>();
				entry.put("member", member.name);
				entry.put("uses", member.uses);
				entry.put("provided", member.provided);
				memberList.add(entry);
			}
			item.put("members", memberList);
			typeList.add(item);
		}
		jdk.put("types", typeList);
		json.put("jdk", jdk);

		List<Object> steps = new ArrayList<Object>();
		for (String[] step : nextSteps(result, inputs)) {
			Map<String, Object> item = new LinkedHashMap<String, Object>();
			item.put("text", step[0]);
			item.put("command", step[1]);
			steps.add(item);
		}
		json.put("nextSteps", steps);
		J2cService.putProjects(json, result.check.projects);
		return json;
	}

	/** Each step's text and its command, or null */
	private List<String[]> nextSteps(Result result, List<String> inputs) {
		List<String[]> steps = new ArrayList<String[]>();
		List<Problem> problems = problems(result);
		StringBuilder arguments = new StringBuilder();
		if (result.tests) {
			arguments.append(" --tests");
		}
		for (String input : inputs) {
			arguments.append(' ').append(input.contains(" ") ? "\"" + input
					+ "\"" : input);
		}

		boolean unresolved = false;
		for (ProjectSetup.Project project : result.check.projects) {
			for (Problem warning : project.warnings) {
				if (ProblemCatalog.PROJECT_CLASSPATH_UNRESOLVED.equals(warning.id)) {
					unresolved = true;
				}
			}
		}
		if (unresolved) {
			steps.add(new String[] {
					"The dependency class path could not be resolved, so "
							+ "J2C-COMPILE-ERROR problems may only be missing "
							+ "dependencies: fix the build or pass the JARs with "
							+ "--classpath (MCP: classPath), then report again.",
					null });
		}

		int fixable = 0;
		StringBuilder fixableIds = new StringBuilder();
		List<IdCount> counts = byId(problems);
		for (IdCount count : counts) {
			if (count.fixable > 0) {
				fixable += count.fixable;
				fixableIds.append(fixableIds.length() == 0 ? "" : ", ").append(
						count.id).append(" (").append(count.fixable).append(')');
			}
		}
		if (fixable > 0) {
			steps.add(new String[] {
					"Apply the " + fixable + " mechanical fix(es) of " + fixableIds
							+ ": j2c fix previews them as a diff and j2c fix "
							+ "--apply writes them (MCP: j2c_fix).",
					"j2c fix --apply" + arguments });
		}
		for (IdCount count : counts) {
			int manual = count.problems.size() - count.fixable;
			if (manual == 0) {
				continue;
			}
			ProblemCatalog.Entry entry = ProblemCatalog.get(count.id);
			steps.add(new String[] {
					"Fix " + manual + " " + count.id + " problem(s) in the Java "
							+ "source by hand: "
							+ (entry == null ? "see j2c explain " + count.id
									: entry.hint),
					null });
		}

		List<Qualified> missing = missingMembers(result);
		if (result.runtime == null && !result.jdk.isEmpty()) {
			steps.add(new String[] {
					"No runtime API is available to compare JDK use with; pass "
							+ "--runtime with the J2C runtime directory.", null });
		} else if (!missing.isEmpty()) {
			StringBuilder top = new StringBuilder();
			for (int i = 0; i < missing.size() && i < 3; ++i) {
				top.append(i == 0 ? "" : ", ").append(missing.get(i).display());
			}
			steps.add(new String[] {
					missing.size() + " JDK member(s) the code uses are not in the J2C "
							+ "runtime and would be stubs that fail at run time ("
							+ top + (missing.size() > 3 ? ", ..." : "")
							+ "): avoid them, or add them to the runtime.",
					null });
		}

		if (problems.isEmpty()) {
			steps.add(new String[] {
					"Everything translates: translate into an empty directory and "
							+ "build with make -C <directory>; add --runtime with the J2C "
							+ "runtime directory to run against the runtime.",
					"j2c --output <directory>" + arguments });
		} else {
			steps.add(new String[] {
					"Check again until no problem is left (MCP: j2c_check), then "
							+ "translate.", "j2c --check" + arguments });
		}
		return steps;
	}

	// Markdown

	String markdown(Result result, List<String> inputs) {
		StringBuilder md = new StringBuilder();
		List<Problem> problems = problems(result);
		md.append("# J2C migration readiness report\n\n");
		md.append("Input: ");
		String separator = "";
		for (String input : inputs) {
			md.append(separator).append('`').append(input).append('`');
			separator = ", ";
		}
		md.append(" (").append(result.files.size()).append(" Java file(s)");
		if (result.tests) {
			md.append(", with tests");
		}
		md.append("). J2C ").append(J2cService.version()).append(".\n\n");

		md.append("## Summary\n\n");
		int translatableFiles = 0;
		int types = 0;
		int translatableTypes = 0;
		int methods = 0;
		int translatableMethods = 0;
		for (FileInfo file : result.files) {
			if (file.problems.isEmpty()) {
				translatableFiles++;
			}
			types += file.types.size();
			translatableTypes += translatable(file.types);
			methods += file.methods.size();
			translatableMethods += translatable(file.methods);
		}
		md.append("| | Total | Translatable | Translatable % |\n");
		md.append("| --- | ---: | ---: | ---: |\n");
		row(md, "Files", result.files.size(), translatableFiles);
		row(md, "Types", types, translatableTypes);
		row(md, "Methods", methods, translatableMethods);
		md.append("\nA file, type or method is translatable when none of the "
				+ "problems `j2c --check` reports lies within it. A problem "
				+ "blocks its file, every type around it and the method around "
				+ "it.\n\n");

		md.append("## Blockers\n\n");
		if (problems.isEmpty()) {
			md.append("None: every file translates.\n\n");
		} else {
			blockersMarkdown(md, problems);
			md.append("### By package\n\n");
			md.append("| Package | Files | Translatable files | Problems | Problem IDs |\n");
			md.append("| --- | ---: | ---: | ---: | --- |\n");
			for (PackageInfo info : packages(result)) {
				md.append("| ").append(info.name.isEmpty() ? "(default package)"
						: "`" + info.name + "`").append(" | ").append(info.files)
						.append(" | ").append(info.translatableFiles).append(" | ")
						.append(info.problems.size()).append(" | ");
				String ids = "";
				for (Map.Entry<String, Integer> id : idCounts(info.problems)
						.entrySet()) {
					md.append(ids).append('`').append(id.getKey()).append("` ")
							.append(id.getValue());
					ids = ", ";
				}
				md.append(" |\n");
			}
			md.append('\n');
		}

		if (result.tests) {
			md.append("## Test blockers\n\n");
			List<Problem> tests = testProblems(problems);
			if (tests.isEmpty()) {
				md.append("None: every test feature used can run translated.\n\n");
			} else {
				md.append("Test features that translated tests cannot run, "
						+ "included in the blockers above.\n\n");
				md.append("| Problem | Count | Files | With a quick fix |\n");
				md.append("| --- | ---: | ---: | ---: |\n");
				for (IdCount count : byId(tests)) {
					idRow(md, count);
				}
				md.append('\n');
			}
		}

		jdkMarkdown(md, result);

		md.append("## Next steps\n\n");
		int number = 1;
		for (String[] step : nextSteps(result, inputs)) {
			md.append(number++).append(". ").append(escape(step[0]));
			if (step[1] != null) {
				md.append("\n\n   ```sh\n   ").append(step[1]).append("\n   ```");
			}
			md.append('\n');
		}
		return md.toString();
	}

	private static void row(StringBuilder md, String what, int total,
			int translatable) {
		md.append("| ").append(what).append(" | ").append(total).append(" | ")
				.append(translatable).append(" | ")
				.append(String.format(Locale.ROOT, "%.1f%%", percent(translatable,
						total))).append(" |\n");
	}

	private static void idRow(StringBuilder md, IdCount count) {
		ProblemCatalog.Entry entry = ProblemCatalog.get(count.id);
		md.append("| `").append(count.id).append('`');
		if (entry != null) {
			md.append(": ").append(escape(entry.title));
		}
		md.append(" | ").append(count.problems.size()).append(" | ")
				.append(count.files.size()).append(" | ").append(count.fixable)
				.append(" |\n");
	}

	private void blockersMarkdown(StringBuilder md, List<Problem> problems) {
		int fixable = 0;
		List<File> files = new ArrayList<File>();
		for (Problem problem : problems) {
			if (!problem.fixes.isEmpty()) {
				fixable++;
			}
			if (!files.contains(problem.file)) {
				files.add(problem.file);
			}
		}
		md.append(problems.size()).append(" problem(s) in ").append(files.size())
				.append(" file(s); ").append(fixable)
				.append(" can be fixed automatically with `j2c fix`.\n\n");
		md.append("| Problem | Count | Files | With a quick fix |\n");
		md.append("| --- | ---: | ---: | ---: |\n");
		List<IdCount> counts = byId(problems);
		for (IdCount count : counts) {
			idRow(md, count);
		}
		md.append('\n');
		for (IdCount count : counts) {
			ProblemCatalog.Entry entry = ProblemCatalog.get(count.id);
			md.append("### `").append(count.id).append("`\n\n");
			if (entry != null) {
				md.append(escape(entry.hint)).append("\n\n");
			}
			List<Problem> sorted = sortedByLocation(count.problems);
			for (int i = 0; i < sorted.size() && i < TOP_LOCATIONS; ++i) {
				Problem problem = sorted.get(i);
				md.append("- `").append(relative(problem.file)).append(':')
						.append(problem.line).append(':').append(problem.column)
						.append("`: ").append(escape(problem.message)).append('\n');
			}
			if (sorted.size() > TOP_LOCATIONS) {
				md.append("- and ").append(sorted.size() - TOP_LOCATIONS)
						.append(" more\n");
			}
			md.append('\n');
		}
	}

	private static void jdkMarkdown(StringBuilder md, Result result) {
		md.append("## JDK API use\n\n");
		if (result.jdk.isEmpty()) {
			md.append("The code uses no JDK types.\n\n");
			return;
		}
		JdkSummary summary = jdkSummary(result);
		md.append("Without a runtime, every JDK class is generated as a stub that "
				+ "fails at run time. ");
		if (result.runtime == null) {
			md.append("No runtime API was available to compare with.\n\n");
		} else {
			md.append("The J2C runtime (`--runtime`")
					.append("bundled".equals(result.runtime.source) ? ""
							: " `" + result.runtime.source + "`")
					.append(") provides ").append(summary.typesProvided).append(" of ")
					.append(summary.types).append(" JDK types and ")
					.append(summary.membersProvided).append(" of ")
					.append(summary.members).append(" members the code uses (")
					.append(summary.memberUsesProvided).append(" of ")
					.append(summary.memberUses).append(" uses); the rest would be "
							+ "stubs. Implicit uses (string concatenation, boxing, "
							+ "enhanced for loops) are not counted.\n\n");
		}
		List<TypeUse> types = jdkTypes(result);
		md.append("| JDK type | Uses | Members used | In the runtime |\n");
		md.append("| --- | ---: | ---: | --- |\n");
		for (int i = 0; i < types.size() && i < MARKDOWN_ROWS; ++i) {
			TypeUse type = types.get(i);
			int provided = 0;
			for (MemberUse member : type.members.values()) {
				if (member.provided) {
					provided++;
				}
			}
			md.append("| `").append(type.name).append("` | ").append(type.uses)
					.append(" | ").append(type.members.size()).append(" | ")
					.append(!type.provided ? "no (stub)"
							: provided == type.members.size() ? "yes"
									: "partly (" + provided + " of "
											+ type.members.size() + " members)")
					.append(" |\n");
		}
		if (types.size() > MARKDOWN_ROWS) {
			md.append("\nand ").append(types.size() - MARKDOWN_ROWS)
					.append(" more types (see `--format json`).\n");
		}
		md.append('\n');
		if (result.runtime == null) {
			return;
		}
		List<Qualified> missing = missingMembers(result);
		md.append("### Not in the runtime\n\n");
		if (missing.isEmpty()) {
			md.append("Every JDK member the code uses is in the runtime.\n\n");
			return;
		}
		md.append("| JDK member | Uses |\n");
		md.append("| --- | ---: |\n");
		for (int i = 0; i < missing.size() && i < MARKDOWN_ROWS; ++i) {
			md.append("| `").append(missing.get(i).display()).append("` | ")
					.append(missing.get(i).member.uses).append(" |\n");
		}
		if (missing.size() > MARKDOWN_ROWS) {
			md.append("\nand ").append(missing.size() - MARKDOWN_ROWS)
					.append(" more (see `--format json`).\n");
		}
		md.append('\n');
	}

	/** The path relative to the base directory, with forward slashes */
	private String relative(File file) {
		String path = file.toPath().toAbsolutePath().normalize().toString();
		String prefix = base.toPath().toAbsolutePath().normalize().toString();
		if (!prefix.endsWith(File.separator)) {
			prefix += File.separator;
		}
		if (path.startsWith(prefix)) {
			path = path.substring(prefix.length());
		}
		return path.replace(File.separatorChar, '/');
	}

	/** Text that cannot break a table cell or start Markdown markup */
	private static String escape(String text) {
		return text == null ? "" : text.replace("|", "\\|").replace("\r", "")
				.replace("\n", " ").replace("<", "&lt;");
	}
}
