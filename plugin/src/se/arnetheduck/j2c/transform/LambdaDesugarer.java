package se.arnetheduck.j2c.transform;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.ArrayType;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.CreationReference;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionMethodReference;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.MethodReference;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.SuperMethodReference;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclarationStatement;
import org.eclipse.jdt.core.dom.TypeMethodReference;
import org.eclipse.jdt.core.dom.VariableDeclaration;

/**
 * Rewrites lambdas and method references into the anonymous classes they are
 * equivalent to, which the writers already translate. The result is Java
 * source that is parsed again, so the compiler checks every rewrite.
 * Replacements keep the line breaks of the original text so diagnostics keep
 * their line numbers.
 */
final class LambdaDesugarer {
	/** Why a lambda or method reference cannot be rewritten */
	private static final class Unsupported extends Exception {
		private static final long serialVersionUID = 1L;

		/** ProblemCatalog ID */
		final String id;

		Unsupported(String id, String reason) {
			super(reason);
			this.id = id;
		}
	}

	private final File file;
	private final String source;
	private final CompilationUnit ast;
	private final Map<ASTNode, String> replacements = new HashMap<ASTNode, String>();
	/** Text inserted at source positions, such as intersection interfaces */
	private final Map<Integer, String> insertions = new HashMap<Integer, String>();
	/** Generated interface names by their extends clause */
	private final Map<String, String> intersections = new HashMap<String, String>();
	private final List<Problem> problems = new ArrayList<Problem>();

	LambdaDesugarer(File file, String source, CompilationUnit ast) {
		this.file = file;
		this.source = source;
		this.ast = ast;
	}

	List<Problem> problems() {
		return problems;
	}

	/** The rewritten source, or null when there was nothing to rewrite */
	String desugar() {
		ast.accept(new ASTVisitor() {
			// Post-order, so nested lambdas are rewritten before the lambdas
			// and method references that contain them
			@Override
			public void endVisit(LambdaExpression node) {
				convert(node, "lambda expression");
			}

			@Override
			public void endVisit(ExpressionMethodReference node) {
				convert(node, "method reference");
			}

			@Override
			public void endVisit(TypeMethodReference node) {
				convert(node, "method reference");
			}

			@Override
			public void endVisit(SuperMethodReference node) {
				convert(node, "method reference");
			}

			@Override
			public void endVisit(CreationReference node) {
				convert(node, "method reference");
			}
		});

		return replacements.isEmpty() ? null : text(0, source.length(), null);
	}

	private void convert(Expression node, String kind) {
		Map<ASTNode, String> before = new HashMap<ASTNode, String>(replacements);
		Map<Integer, String> insertionsBefore = new HashMap<Integer, String>(
				insertions);
		Map<String, String> intersectionsBefore = new HashMap<String, String>(
				intersections);
		try {
			String replacement = node instanceof LambdaExpression ? lambda((LambdaExpression) node)
					: methodReference((MethodReference) node);
			// The generated class implements every component of an
			// intersection cast such as (Runnable & Serializable), which
			// makes the cast itself redundant
			ASTNode parent = node.getParent();
			if (parent instanceof CastExpression
					&& ((CastExpression) parent).getType().isIntersectionType()) {
				replacements.put(parent, replacement);
			} else {
				replacements.put(node, replacement);
			}
		} catch (Unsupported e) {
			// Drop scope rewrites made for this node; nested conversions
			// that succeeded earlier are kept
			replacements.clear();
			replacements.putAll(before);
			insertions.clear();
			insertions.putAll(insertionsBefore);
			intersections.clear();
			intersections.putAll(intersectionsBefore);
			problems.add(Problem.of(e.id, kind, "Java 8 " + kind
					+ " cannot be translated: " + e.getMessage()).at(file,
					ast.getLineNumber(node.getStartPosition()),
					ast.getColumnNumber(node.getStartPosition()) + 1));
		}
	}

	private String lambda(LambdaExpression node) throws Unsupported {
		ITypeBinding target = node.resolveTypeBinding();
		IMethodBinding method = node.resolveMethodBinding();
		if (target == null || method == null) {
			throw new Unsupported(ProblemCatalog.LAMBDA_UNRESOLVED,
					"its target type could not be resolved");
		}
		IMethodBinding sam = functionalMethod(target);

		StringBuilder header = new StringBuilder();
		header.append("new ").append(implemented(node, target)).append("() { public ")
				.append(type(method.getReturnType(), true)).append(' ')
				.append(sam.getName()).append('(');
		for (int i = 0; i < node.parameters().size(); ++i) {
			VariableDeclaration parameter = (VariableDeclaration) node
					.parameters().get(i);
			if (i > 0) {
				header.append(", ");
			}
			ITypeBinding parameterType = method.getParameterTypes()[i];
			if (parameter instanceof SingleVariableDeclaration
					&& ((SingleVariableDeclaration) parameter).isVarargs()) {
				header.append(type(parameterType.getComponentType(), true))
						.append("...");
			} else {
				header.append(type(parameterType, true));
			}
			header.append(' ').append(parameter.getName().getIdentifier());
		}
		header.append(") ").append(throwsClause(sam));

		ASTNode body = node.getBody();
		rewriteScope(body, node, target);
		String bodyText = text(body);
		String lineBreaks = lineBreaks(node.getStartPosition(),
				body.getStartPosition());
		if (body instanceof Block) {
			return header + lineBreaks + bodyText + " }";
		}
		String statement = isVoid(method.getReturnType()) ? bodyText + ";"
				: "return " + bodyText + ";";
		return header + "{ " + lineBreaks + statement + " } }";
	}

	private String methodReference(MethodReference node) throws Unsupported {
		ITypeBinding target = node.resolveTypeBinding();
		IMethodBinding referenced = node.resolveMethodBinding();
		// Array constructor references have no method binding
		boolean arrayCreation = node instanceof CreationReference
				&& ((CreationReference) node).getType().isArrayType();
		if (target == null || (referenced == null && !arrayCreation)) {
			throw new Unsupported(ProblemCatalog.LAMBDA_UNRESOLVED,
					"its target type could not be resolved");
		}
		IMethodBinding sam = functionalMethod(target);

		ITypeBinding[] parameterTypes = sam.getParameterTypes();
		String[] names = new String[parameterTypes.length];
		StringBuilder parameters = new StringBuilder();
		for (int i = 0; i < names.length; ++i) {
			names[i] = "p" + i;
			if (i > 0) {
				parameters.append(", ");
			}
			parameters.append(type(parameterTypes[i], true)).append(' ')
					.append(names[i]);
		}

		String typeArguments = typeArguments(node.typeArguments());
		String field = "";
		String call;
		if (node instanceof CreationReference) {
			Type created = ((CreationReference) node).getType();
			if (created.isArrayType()) {
				ArrayType array = (ArrayType) created;
				call = "new " + text(array.getElementType()) + "[" + names[0]
						+ "]" + "[]".repeat(array.getDimensions() - 1);
			} else {
				String createdType = text(created);
				ITypeBinding createdBinding = created.resolveBinding();
				if (createdBinding != null && createdBinding.isGenericType()
						&& !(created instanceof ParameterizedType)) {
					createdType += "<>";
				}
				call = "new " + typeArguments + createdType + "("
						+ arguments(names, 0) + ")";
			}
		} else if (node instanceof SuperMethodReference) {
			SuperMethodReference reference = (SuperMethodReference) node;
			String qualifier = reference.getQualifier() == null ? enclosingTypeName(node)
					: text(reference.getQualifier());
			call = qualifier + ".super." + typeArguments
					+ reference.getName().getIdentifier() + "("
					+ arguments(names, 0) + ")";
		} else {
			String name;
			ASTNode qualifier;
			boolean typeQualifier;
			if (node instanceof TypeMethodReference) {
				TypeMethodReference reference = (TypeMethodReference) node;
				name = reference.getName().getIdentifier();
				Type type = reference.getType();
				qualifier = type instanceof ParameterizedType ? ((ParameterizedType) type)
						.getType() : type;
				typeQualifier = true;
			} else {
				ExpressionMethodReference reference = (ExpressionMethodReference) node;
				name = reference.getName().getIdentifier();
				qualifier = reference.getExpression();
				typeQualifier = qualifier instanceof Name
						&& ((Name) qualifier).resolveBinding() instanceof ITypeBinding;
			}

			if (Modifier.isStatic(referenced.getModifiers())) {
				call = text(qualifier) + "." + typeArguments + name + "("
						+ arguments(names, 0) + ")";
			} else if (typeQualifier) {
				// Unbound receiver: the first parameter is the target object
				call = names[0] + "." + typeArguments + name + "("
						+ arguments(names, 1) + ")";
			} else {
				// Bound receiver: evaluated once, when the reference is created
				Expression receiver = (Expression) qualifier;
				rewriteScope(receiver, node, target);
				field = "private final "
						+ type(receiver.resolveTypeBinding(), true)
						+ " receiver = " + text(receiver) + "; ";
				call = "receiver." + typeArguments + name + "("
						+ arguments(names, 0) + ")";
			}
		}

		String statement = isVoid(sam.getReturnType()) ? call + ";" : "return "
				+ call + ";";
		return "new " + implemented(node, target) + "() { " + field + "public "
				+ typeParameters(sam) + type(sam.getReturnType(), true) + " "
				+ sam.getName() + "("
				+ parameters + ") " + throwsClause(sam) + "{ " + statement
				+ " } }"
				+ lineBreaks(node.getStartPosition(), end(node));
	}

	private static IMethodBinding functionalMethod(ITypeBinding target)
			throws Unsupported {
		IMethodBinding sam = target.getFunctionalInterfaceMethod();
		if (sam == null && target.isIntersectionType()) {
			// Java allows at most one abstract method across the components
			for (ITypeBinding bound : target.getTypeBounds()) {
				if (bound.getFunctionalInterfaceMethod() != null) {
					sam = bound.getFunctionalInterfaceMethod();
				}
			}
		}
		if (sam == null) {
			throw new Unsupported(ProblemCatalog.LAMBDA_UNRESOLVED,
					"its target is not a functional interface");
		}
		return sam;
	}

	/** "<T extends A & B, U> " for a generic functional method, else "" */
	private static String typeParameters(IMethodBinding sam)
			throws Unsupported {
		ITypeBinding[] parameters = sam.getTypeParameters();
		if (parameters.length == 0) {
			return "";
		}
		StringBuilder text = new StringBuilder("<");
		for (int i = 0; i < parameters.length; ++i) {
			if (i > 0) {
				text.append(", ");
			}
			text.append(parameters[i].getName());
			ITypeBinding[] bounds = parameters[i].getTypeBounds();
			for (int j = 0; j < bounds.length; ++j) {
				if (bounds.length == 1
						&& "java.lang.Object".equals(bounds[j].getQualifiedName())) {
					break;
				}
				text.append(j == 0 ? " extends " : " & ").append(
						type(bounds[j], false));
			}
		}
		return text.append("> ").toString();
	}

	/**
	 * What the anonymous class implements: the grounded functional interface,
	 * or for an intersection target such as (Runnable & Serializable) a member
	 * interface extending every component, declared in the top-level class
	 * so instanceof keeps working for all of them.
	 */
	private String implemented(ASTNode node, ITypeBinding target)
			throws Unsupported {
		if (!target.isIntersectionType()) {
			return groundType(target);
		}

		StringBuilder extendsClause = new StringBuilder();
		for (ITypeBinding bound : target.getTypeBounds()) {
			if (mentionsTypeVariable(bound)) {
				throw new Unsupported(
						ProblemCatalog.LAMBDA_INTERSECTION_TYPE_VARIABLE,
						"its intersection target uses a type "
						+ "variable, which a member interface cannot see");
			}
			if (extendsClause.length() > 0) {
				extendsClause.append(", ");
			}
			extendsClause.append(groundType(bound));
		}

		String key = extendsClause.toString();
		String name = intersections.get(key);
		if (name == null) {
			name = "J2C_Intersection_" + (intersections.size() + 1);
			intersections.put(key, name);
			AbstractTypeDeclaration top = topLevelType(node);
			// Before the closing brace, on the same line. A leading ";" ends
			// an enum constant list; elsewhere it is an empty declaration
			int position = end(top) - 1;
			String declaration = (top instanceof EnumDeclaration ? "; " : " ")
					+ "interface " + name + " extends " + key + " {} ";
			String existing = insertions.get(position);
			insertions.put(position, existing == null ? declaration : existing
					+ declaration);
		}
		return name;
	}

	private static boolean mentionsTypeVariable(ITypeBinding type) {
		if (type == null) {
			return false;
		}
		if (type.isTypeVariable() || type.isCapture()) {
			return true;
		}
		if (type.isArray()) {
			return mentionsTypeVariable(type.getElementType());
		}
		if (type.isWildcardType()) {
			return mentionsTypeVariable(type.getBound());
		}
		for (ITypeBinding argument : type.getTypeArguments()) {
			if (mentionsTypeVariable(argument)) {
				return true;
			}
		}
		return false;
	}

	private static AbstractTypeDeclaration topLevelType(ASTNode node)
			throws Unsupported {
		for (ASTNode n = node; n != null; n = n.getParent()) {
			if (n instanceof AbstractTypeDeclaration
					&& n.getParent() instanceof CompilationUnit) {
				return (AbstractTypeDeclaration) n;
			}
		}
		throw new Unsupported(ProblemCatalog.LAMBDA_UNTRANSLATED,
				"its top-level class could not be found");
	}

	/**
	 * Code moved into the anonymous class must keep meaning what it meant in
	 * the enclosing class: "this", "super" and unqualified calls whose name
	 * the functional interface (or Object) also declares are qualified.
	 */
	private void rewriteScope(ASTNode root, final ASTNode lambda,
			ITypeBinding target) throws Unsupported {
		final Set<String> hidden = memberMethodNames(target);
		final Unsupported[] failure = new Unsupported[1];
		root.accept(new ASTVisitor() {
			@Override
			public boolean visit(AnonymousClassDeclaration node) {
				return false;
			}

			@Override
			public boolean visit(TypeDeclarationStatement node) {
				return false;
			}

			@Override
			public void endVisit(ThisExpression node) {
				if (node.getQualifier() == null) {
					qualify(node, ".this");
				}
			}

			@Override
			public void endVisit(SuperMethodInvocation node) {
				if (node.getQualifier() == null) {
					qualify(node, ".");
				}
			}

			@Override
			public void endVisit(SuperFieldAccess node) {
				if (node.getQualifier() == null) {
					qualify(node, ".");
				}
			}

			@Override
			public void endVisit(MethodInvocation node) {
				IMethodBinding method = node.resolveMethodBinding();
				if (node.getExpression() != null || method == null
						|| !hidden.contains(node.getName().getIdentifier())) {
					return;
				}
				try {
					String prefix = Modifier.isStatic(method.getModifiers()) ? typeName(method
							.getDeclaringClass()) + "."
							: enclosingTypeName(lambda, method.getDeclaringClass())
									+ ".this.";
					replacements.put(node, prefix
							+ text(node.getStartPosition(), end(node), node));
				} catch (Unsupported e) {
					failure[0] = e;
				}
			}

			private void qualify(ASTNode node, String suffix) {
				try {
					String name = enclosingTypeName(lambda);
					String original = text(node.getStartPosition(), end(node),
							node);
					// "this" -> "Outer.this", "super.x" -> "Outer.super.x"
					replacements.put(node, node instanceof ThisExpression ? name
							+ suffix : name + suffix + original);
				} catch (Unsupported e) {
					failure[0] = e;
				}
			}
		});
		if (failure[0] != null) {
			throw failure[0];
		}
	}

	/** Names that an anonymous class implementing target would declare */
	private Set<String> memberMethodNames(ITypeBinding target) {
		Set<String> names = new HashSet<String>();
		collectMethodNames(target.getErasure(), names);
		ITypeBinding object = ast.getAST().resolveWellKnownType(
				"java.lang.Object");
		if (object != null) {
			collectMethodNames(object, names);
		}
		return names;
	}

	private static void collectMethodNames(ITypeBinding type, Set<String> names) {
		for (IMethodBinding method : type.getDeclaredMethods()) {
			names.add(method.getName());
		}
		for (ITypeBinding parent : type.getInterfaces()) {
			collectMethodNames(parent.getErasure(), names);
		}
	}

	/** The innermost named class around node, for "Name.this" */
	private static String enclosingTypeName(ASTNode node) throws Unsupported {
		return enclosingTypeName(node, null);
	}

	/**
	 * The innermost named class around node that is, or inherits from,
	 * member (any class when member is null).
	 */
	private static String enclosingTypeName(ASTNode node, ITypeBinding member)
			throws Unsupported {
		for (ASTNode parent = node.getParent(); parent != null; parent = parent
				.getParent()) {
			ITypeBinding type;
			if (parent instanceof AnonymousClassDeclaration) {
				type = ((AnonymousClassDeclaration) parent).resolveBinding();
			} else if (parent instanceof AbstractTypeDeclaration) {
				type = ((AbstractTypeDeclaration) parent).resolveBinding();
			} else {
				continue;
			}

			if (member != null && type != null
					&& !type.getErasure().isSubTypeCompatible(member.getErasure())) {
				continue;
			}
			if (parent instanceof AnonymousClassDeclaration) {
				throw new Unsupported(ProblemCatalog.LAMBDA_ANONYMOUS_THIS,
						"it refers to an enclosing anonymous class, which cannot be named");
			}
			return ((AbstractTypeDeclaration) parent).getName().getIdentifier();
		}
		throw new Unsupported(ProblemCatalog.LAMBDA_UNTRANSLATED,
				"its enclosing class could not be found");
	}

	/**
	 * The functional interface with top-level wildcards replaced by their
	 * bounds, as an anonymous class must implement (JLS 9.9).
	 */
	private static String groundType(ITypeBinding target) throws Unsupported {
		if (!target.isParameterizedType()) {
			return type(target, false);
		}
		StringBuilder name = new StringBuilder(typeName(target));
		name.append('<');
		ITypeBinding[] arguments = target.getTypeArguments();
		for (int i = 0; i < arguments.length; ++i) {
			if (i > 0) {
				name.append(", ");
			}
			name.append(type(arguments[i], true));
		}
		return name.append('>').toString();
	}

	/**
	 * Java source for a type. With ground, a wildcard or captured wildcard is
	 * replaced by its bound; nested type arguments keep their wildcards.
	 */
	private static String type(ITypeBinding type, boolean ground)
			throws Unsupported {
		if (type.isCapture()) {
			type = type.getWildcard();
		}
		if (type.isWildcardType()) {
			if (ground) {
				return type(wildcardBound(type), false);
			}
			ITypeBinding bound = type.getBound();
			return bound == null ? "?" : "?"
					+ (type.isUpperbound() ? " extends " : " super ")
					+ type(bound, false);
		}
		if (type.isPrimitive() || type.isTypeVariable()) {
			return type.getName();
		}
		if (type.isArray()) {
			return type(type.getElementType(), false)
					+ "[]".repeat(type.getDimensions());
		}
		if (type.isIntersectionType() || type.isAnonymous()
				|| type.isNullType()) {
			throw new Unsupported(ProblemCatalog.LAMBDA_NON_DENOTABLE_TYPE,
					"the type " + type.getName()
					+ " cannot be written in Java source");
		}

		String name = typeName(type);
		if (!type.isParameterizedType()) {
			return name;
		}
		StringBuilder parameterized = new StringBuilder(name).append('<');
		ITypeBinding[] arguments = type.getTypeArguments();
		for (int i = 0; i < arguments.length; ++i) {
			if (i > 0) {
				parameterized.append(", ");
			}
			parameterized.append(type(arguments[i], false));
		}
		return parameterized.append('>').toString();
	}

	private static ITypeBinding wildcardBound(ITypeBinding wildcard) {
		if (wildcard.getBound() != null) {
			return wildcard.getBound();
		}
		ITypeBinding generic = wildcard.getGenericTypeOfWildcardType();
		if (generic != null) {
			ITypeBinding[] bounds = generic.getTypeParameters()[wildcard
					.getRank()].getTypeBounds();
			// A bound mentioning other type parameters is not in scope here;
			// its erasure is
			if (bounds.length > 0) {
				return bounds[0].getErasure();
			}
		}
		return wildcard.getErasure().isWildcardType() ? null : wildcard
				.getErasure();
	}

	/** The name of a class or interface, without type arguments */
	private static String typeName(ITypeBinding type) throws Unsupported {
		ITypeBinding erasure = type.getErasure();
		if (erasure.isAnonymous()) {
			throw new Unsupported(ProblemCatalog.LAMBDA_NON_DENOTABLE_TYPE,
					"the type " + type.getName()
					+ " cannot be written in Java source");
		}
		String simple = withoutArguments(erasure.getName());
		if (erasure.isLocal()) {
			return simple;
		}
		if (erasure.isMember()) {
			return typeName(erasure.getDeclaringClass()) + "." + simple;
		}
		return withoutArguments(erasure.getQualifiedName());
	}

	private static String withoutArguments(String name) {
		int arguments = name.indexOf('<');
		return arguments < 0 ? name : name.substring(0, arguments);
	}

	private static String throwsClause(IMethodBinding sam) throws Unsupported {
		ITypeBinding[] exceptions = sam.getExceptionTypes();
		if (exceptions.length == 0) {
			return "";
		}
		StringBuilder clause = new StringBuilder("throws ");
		for (int i = 0; i < exceptions.length; ++i) {
			if (i > 0) {
				clause.append(", ");
			}
			clause.append(type(exceptions[i], true));
		}
		return clause.append(' ').toString();
	}

	private String typeArguments(List<?> arguments) {
		if (arguments.isEmpty()) {
			return "";
		}
		StringBuilder text = new StringBuilder("<");
		for (int i = 0; i < arguments.size(); ++i) {
			if (i > 0) {
				text.append(", ");
			}
			text.append(text((ASTNode) arguments.get(i)));
		}
		return text.append('>').toString();
	}

	private static String arguments(String[] names, int from) {
		StringBuilder text = new StringBuilder();
		for (int i = from; i < names.length; ++i) {
			if (i > from) {
				text.append(", ");
			}
			text.append(names[i]);
		}
		return text.toString();
	}

	private static boolean isVoid(ITypeBinding type) {
		return type.isPrimitive() && "void".equals(type.getName());
	}

	private static int end(ASTNode node) {
		return node.getStartPosition() + node.getLength();
	}

	/** Line breaks in the original range, to keep later lines in place */
	private String lineBreaks(int start, int end) {
		StringBuilder breaks = new StringBuilder();
		for (int i = start; i < end; ++i) {
			if (source.charAt(i) == '\n') {
				breaks.append('\n');
			}
		}
		return breaks.toString();
	}

	/** The node's source with rewrites applied, including its own */
	private String text(ASTNode node) {
		String replacement = replacements.get(node);
		return replacement != null ? replacement : text(
				node.getStartPosition(), end(node), node);
	}

	/**
	 * The source in [start, end) with the outermost replaced nodes inside it
	 * substituted, excluding self.
	 */
	private String text(int start, int end, ASTNode self) {
		// Node replacements, plus insertions as zero-length edits; each edit
		// is {start, end, index into texts}
		List<int[]> edits = new ArrayList<int[]>();
		List<String> texts = new ArrayList<String>();
		for (Map.Entry<ASTNode, String> replacement : replacements.entrySet()) {
			ASTNode node = replacement.getKey();
			if (node != self && node.getStartPosition() >= start
					&& end(node) <= end) {
				edits.add(new int[] { node.getStartPosition(), end(node),
						texts.size() });
				texts.add(replacement.getValue());
			}
		}
		for (Map.Entry<Integer, String> insertion : insertions.entrySet()) {
			int position = insertion.getKey();
			if (position >= start && position < end) {
				edits.add(new int[] { position, position, texts.size() });
				texts.add(insertion.getValue());
			}
		}
		Collections.sort(edits, new Comparator<int[]>() {
			@Override
			public int compare(int[] a, int[] b) {
				int byStart = Integer.compare(a[0], b[0]);
				return byStart != 0 ? byStart : Integer.compare(b[1] - b[0],
						a[1] - a[0]);
			}
		});

		StringBuilder text = new StringBuilder();
		int position = start;
		for (int[] edit : edits) {
			if (edit[0] < position) {
				continue; // inside a replacement already applied
			}
			text.append(source, position, edit[0]);
			text.append(texts.get(edit[2]));
			position = edit[1];
		}
		return text.append(source, position, end).toString();
	}
}
