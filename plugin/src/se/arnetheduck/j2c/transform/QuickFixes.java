package se.arnetheduck.j2c.transform;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionMethodReference;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.MethodReference;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.PatternInstanceofExpression;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.SuperMethodReference;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclarationStatement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

/**
 * Mechanical fixes for problems, as edits to the original Java source. A fix
 * is offered only when the rewrite keeps the program's meaning and the result
 * compiles; anything less certain is left to the hint.
 *
 * <ul>
 * <li>J2C-LAMBDA-ANONYMOUS-THIS: "var self = this;" before the statement, and
 * self for this in the lambda.</li>
 * <li>J2C-LAMBDA-INTERFACE-SUPER: the Iface.super.m() call moved into a
 * private method of the class.</li>
 * <li>J2C-PATTERN-OUTSIDE-METHOD: the field initializer moved into a private
 * method.</li>
 * <li>J2C-TEST-TIMEOUT: the timeout removed.</li>
 * <li>J2C-TEST-PRIVATE-METHOD: the private modifier removed.</li>
 * </ul>
 */
final class QuickFixes {
	/**
	 * Identifiers for new declarations, unused anywhere in the compilation
	 * unit so they shadow and clash with nothing. The same key always gets
	 * the same name, so fixes for one place produce identical edits.
	 */
	static final class Names {
		private final Set<String> used = new HashSet<String>();
		private final Map<Object, String> byKey = new HashMap<Object, String>();

		Names(CompilationUnit unit) {
			unit.accept(new ASTVisitor() {
				@Override
				public boolean visit(SimpleName node) {
					used.add(node.getIdentifier());
					return false;
				}
			});
		}

		String get(Object key, String base) {
			String name = byKey.get(key);
			if (name == null) {
				name = base;
				for (int i = 2; used.contains(name); ++i) {
					name = base + i;
				}
				used.add(name);
				byKey.put(key, name);
			}
			return name;
		}
	}

	private QuickFixes() {
	}

	// Fixes found while rewriting lambdas, on the original AST with bindings

	/**
	 * Replaces this with a local declared first in the method, for a lambda
	 * or method reference whose innermost class is anonymous. this is the
	 * same everywhere in a method, so declaring it first changes nothing, and
	 * every fix in the method shares one declaration.
	 */
	static List<Fix> anonymousThis(File file, SourceText text, Expression node,
			Names names) {
		ASTNode innermost = innermostClass(node);
		AnonymousClassDeclaration anonymous = innermost instanceof AnonymousClassDeclaration ? (AnonymousClassDeclaration) innermost
				: null;
		Statement statement = anonymous == null ? null
				: firstStatement(node, anonymous);
		if (statement == null) {
			return Collections.emptyList();
		}
		final AnonymousClassDeclaration enclosing = anonymous;
		final ITypeBinding anonymousType = anonymous.resolveBinding();
		if (anonymousType == null) {
			return Collections.emptyList();
		}

		final String self = names.get(Integer.valueOf(statement
				.getStartPosition()), "self");
		final List<SourceEdit> edits = new ArrayList<SourceEdit>();
		final boolean[] failed = { false };
		final File source = file;
		final SourceText sourceText = text;
		final Names allNames = names;
		final Expression root = node;
		node.accept(new ASTVisitor() {
			@Override
			public boolean visit(AnonymousClassDeclaration n) {
				return false;
			}

			@Override
			public boolean visit(TypeDeclarationStatement n) {
				return false;
			}

			@Override
			public boolean visit(ThisExpression n) {
				if (n.getQualifier() == null) {
					edits.add(replace(n, self));
				}
				return false;
			}

			@Override
			public boolean visit(SuperMethodInvocation n) {
				// super.m() cannot be called through a variable
				if (n.getQualifier() == null || isInterface(n.getQualifier()
						.resolveTypeBinding())) {
					failed[0] = true;
				}
				return true;
			}

			@Override
			public boolean visit(SuperFieldAccess n) {
				if (n.getQualifier() == null) {
					failed[0] = true;
				}
				return true;
			}

			@Override
			public boolean visit(SuperMethodReference n) {
				if (n.getQualifier() == null) {
					failed[0] = true;
				}
				return false;
			}

			@Override
			public boolean visit(ExpressionMethodReference n) {
				if (!(n.getExpression() instanceof ThisExpression)
						|| ((ThisExpression) n.getExpression()).getQualifier() != null) {
					return true;
				}
				// The generated class would store the receiver in a field
				// of the anonymous type, so this::m becomes a lambda
				String lambda = boundReferenceLambda(n, self, allNames);
				if (lambda == null) {
					failed[0] = true;
				} else {
					edits.add(replace(n, lambda));
				}
				return false;
			}

			@Override
			public void endVisit(MethodInvocation n) {
				IMethodBinding method = n.resolveMethodBinding();
				if (n.getExpression() != null || method == null
						|| Modifier.isStatic(method.getModifiers())
						|| !hiddenNames(n, root).contains(
								n.getName().getIdentifier())) {
					return;
				}
				ASTNode owner = memberOwner(n, method.getDeclaringClass());
				if (owner instanceof AnonymousClassDeclaration) {
					if (owner != enclosing
							|| (Modifier.isPrivate(method.getModifiers()) && !method
									.getDeclaringClass().getErasure()
									.isEqualTo(anonymousType.getErasure()))) {
						failed[0] = true;
					} else {
						edits.add(SourceEdit.of(source, sourceText,
								n.getStartPosition(), n.getStartPosition(), self
										+ "."));
					}
				}
			}

			private SourceEdit replace(ASTNode n, String newText) {
				return SourceEdit.of(source, sourceText, n.getStartPosition(),
						end(n), newText);
			}
		});
		if (failed[0] || edits.isEmpty()) {
			return Collections.emptyList();
		}

		String declaration = "var " + self + " = this;";
		String indentation = text.indentation(statement.getStartPosition());
		edits.add(0, SourceEdit.of(file, text, statement.getStartPosition(),
				statement.getStartPosition(), indentation == null ? declaration
						+ " " : declaration + text.lineSeparator() + indentation));
		return Collections.singletonList(new Fix("Use a local variable for this ("
				+ declaration + ")", edits));
	}

	private static ASTNode innermostClass(ASTNode node) {
		for (ASTNode parent = node.getParent(); parent != null; parent = parent
				.getParent()) {
			if (parent instanceof AnonymousClassDeclaration
					|| parent instanceof AbstractTypeDeclaration) {
				return parent;
			}
		}
		return null;
	}

	/**
	 * The first statement of the method or initializer of the anonymous class
	 * that node is in, or null when node is in a field initializer
	 */
	private static Statement firstStatement(ASTNode node,
			AnonymousClassDeclaration anonymous) {
		BodyDeclaration member = member(node);
		if (member == null || member.getParent() != anonymous) {
			return null;
		}
		Block body = member instanceof MethodDeclaration ? ((MethodDeclaration) member)
				.getBody() : member instanceof Initializer ? ((Initializer) member)
				.getBody() : null;
		return body == null || body.statements().isEmpty() ? null
				: (Statement) body.statements().get(0);
	}

	/**
	 * Methods that the generated class for a lambda declares itself, so an
	 * unqualified call of one from a lambda between node and root must be
	 * qualified.
	 */
	private static Set<String> hiddenNames(ASTNode node, ASTNode root) {
		Set<String> names = new HashSet<String>();
		for (ASTNode parent = node; parent != null; parent = parent.getParent()) {
			if (parent instanceof LambdaExpression
					|| parent instanceof MethodReference) {
				ITypeBinding target = ((Expression) parent).resolveTypeBinding();
				if (target != null) {
					names.addAll(LambdaDesugarer.memberMethodNames(
							parent.getAST(), target));
				}
			}
			if (parent == root) {
				break;
			}
		}
		return names;
	}

	/** The innermost class around node that has member, as Java resolves it */
	private static ASTNode memberOwner(ASTNode node, ITypeBinding member) {
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
			if (type != null
					&& type.getErasure().isSubTypeCompatible(member.getErasure())) {
				return parent;
			}
		}
		return null;
	}

	/** "(p0) -> self.m(p0)" for this::m, or null when that would differ */
	private static String boundReferenceLambda(ExpressionMethodReference node,
			String self, Names names) {
		IMethodBinding referenced = node.resolveMethodBinding();
		ITypeBinding target = node.resolveTypeBinding();
		IMethodBinding sam = target == null ? null : target
				.getFunctionalInterfaceMethod();
		if (referenced == null || sam == null || referenced.isVarargs()
				|| Modifier.isStatic(referenced.getModifiers())
				|| referenced.getParameterTypes().length != sam
						.getParameterTypes().length) {
			return null;
		}
		StringBuilder parameters = new StringBuilder();
		for (int i = 0; i < sam.getParameterTypes().length; ++i) {
			if (i > 0) {
				parameters.append(", ");
			}
			parameters.append(names.get(node.getStartPosition() + ":" + i,
					"p" + i));
		}
		StringBuilder typeArguments = new StringBuilder();
		for (Object argument : node.typeArguments()) {
			typeArguments.append(typeArguments.length() == 0 ? "<" : ", ")
					.append(argument.toString());
		}
		if (typeArguments.length() > 0) {
			typeArguments.append('>');
		}
		return "(" + parameters + ") -> " + self + "." + typeArguments
				+ node.getName().getIdentifier() + "(" + parameters + ")";
	}

	/**
	 * Moves each Iface.super.m(...) in node into a private method of the class
	 * that implements Iface, which the lambda then calls.
	 */
	static List<Fix> interfaceSuper(final File file, final SourceText text,
			final Expression node, final Names names) {
		final ASTNode owner = innermostClass(node);
		final BodyDeclaration member = member(node);
		if (owner == null || member == null || member.getParent() != owner) {
			return Collections.emptyList();
		}

		final List<SourceEdit> edits = new ArrayList<SourceEdit>();
		final Map<String, String> helpers = new HashMap<String, String>();
		final boolean[] failed = { false };
		final String nl = text.lineSeparator();
		final String indentation = text.indentation(member.getStartPosition());
		if (indentation == null) {
			return Collections.emptyList();
		}
		final String unit = SourceText.indentUnit(indentation);
		// Helpers go last in the class, so fixes from different members that
		// need the same helper share its declaration
		final int closingBrace = end(owner) - 1;
		if (text.text().charAt(closingBrace) != '}'
				|| text.indentation(closingBrace) == null) {
			return Collections.emptyList();
		}
		node.accept(new ASTVisitor() {
			@Override
			public boolean visit(AnonymousClassDeclaration n) {
				return false;
			}

			@Override
			public boolean visit(TypeDeclarationStatement n) {
				return false;
			}

			@Override
			public void endVisit(SuperMethodInvocation n) {
				if (n.getQualifier() == null
						|| !isInterface(n.getQualifier().resolveTypeBinding())) {
					return;
				}
				IMethodBinding method = n.resolveMethodBinding();
				String helper = method == null ? null : helper(method, n
						.getQualifier().getFullyQualifiedName());
				if (helper == null || !n.typeArguments().isEmpty()) {
					failed[0] = true;
					return;
				}
				StringBuilder call = new StringBuilder(helper).append('(');
				for (int i = 0; i < n.arguments().size(); ++i) {
					ASTNode argument = (ASTNode) n.arguments().get(i);
					call.append(i > 0 ? ", " : "").append(
							text.text().substring(argument.getStartPosition(),
									end(argument)));
				}
				// Nested Iface.super calls in the arguments are not rewritten
				for (Object argument : n.arguments()) {
					if (containsInterfaceSuper((ASTNode) argument)) {
						failed[0] = true;
					}
				}
				edits.add(SourceEdit.of(file, text, n.getStartPosition(),
						end(n), call.append(')').toString()));
			}

			@Override
			public boolean visit(SuperMethodReference n) {
				if (n.getQualifier() == null
						|| !isInterface(n.getQualifier().resolveTypeBinding())) {
					return false;
				}
				IMethodBinding method = n.resolveMethodBinding();
				String helper = method == null ? null : helper(method, n
						.getQualifier().getFullyQualifiedName());
				// this::helper names a member of the class, which an
				// anonymous class cannot be
				if (helper == null || !n.typeArguments().isEmpty()
						|| !(owner instanceof AbstractTypeDeclaration)) {
					failed[0] = true;
				} else {
					edits.add(SourceEdit.of(file, text, n.getStartPosition(),
							end(n), "this::" + helper));
				}
				return false;
			}

			/** The helper's name, declaring it on first use */
			private String helper(IMethodBinding method, String qualifier) {
				String key = qualifier + "." + method.getKey();
				String name = helpers.get(key);
				if (name != null) {
					return name;
				}
				if (method.getTypeParameters().length > 0 || method.isVarargs()) {
					return null;
				}
				String returnType = typeName(method.getReturnType());
				StringBuilder parameters = new StringBuilder();
				StringBuilder arguments = new StringBuilder();
				ITypeBinding[] types = method.getParameterTypes();
				for (int i = 0; i < types.length; ++i) {
					String type = typeName(types[i]);
					if (type == null) {
						return null;
					}
					String parameter = names.get(key + ":" + i, "p" + i);
					parameters.append(i > 0 ? ", " : "").append(type)
							.append(' ').append(parameter);
					arguments.append(i > 0 ? ", " : "").append(parameter);
				}
				StringBuilder exceptions = new StringBuilder();
				for (ITypeBinding exception : method.getExceptionTypes()) {
					String type = typeName(exception);
					if (type == null) {
						return null;
					}
					exceptions.append(exceptions.length() == 0 ? " throws "
							: ", ").append(type);
				}
				if (returnType == null) {
					return null;
				}

				String simpleQualifier = qualifier.substring(qualifier
						.lastIndexOf('.') + 1);
				name = names.get(owner.getStartPosition() + "/" + key,
						method.getName() + "From" + simpleQualifier);
				helpers.put(key, name);
				String call = qualifier + ".super." + method.getName() + "("
						+ arguments + ");";
				String declaration = nl + indentation + "private "
						+ returnType + " " + name + "(" + parameters + ")"
						+ exceptions + " {" + nl + indentation + unit
						+ ("void".equals(returnType) ? "" : "return ") + call
						+ nl + indentation + "}" + nl;
				int position = text.lineStart(closingBrace);
				edits.add(SourceEdit.of(file, text, position, position,
						declaration));
				return name;
			}
		});
		if (failed[0] || edits.isEmpty()) {
			return Collections.emptyList();
		}
		return Collections.singletonList(new Fix(
				"Move the Iface.super call into a private method", edits));
	}

	private static boolean containsInterfaceSuper(ASTNode node) {
		final boolean[] found = { false };
		node.accept(new ASTVisitor() {
			@Override
			public boolean visit(SuperMethodInvocation n) {
				if (n.getQualifier() != null
						&& isInterface(n.getQualifier().resolveTypeBinding())) {
					found[0] = true;
				}
				return true;
			}
		});
		return found[0];
	}

	private static boolean isInterface(ITypeBinding type) {
		return type != null && type.isInterface();
	}

	/**
	 * Java source for a type in a declaration anywhere in its class, or null
	 * when it cannot be written there.
	 */
	private static String typeName(ITypeBinding type) {
		if (type.isPrimitive()) {
			return type.getName();
		}
		if (type.isArray()) {
			String element = typeName(type.getElementType());
			return element == null ? null : element
					+ "[]".repeat(type.getDimensions());
		}
		if (type.isTypeVariable()) {
			// A method's type variable is not visible in another method
			return type.getDeclaringMethod() != null ? null : type.getName();
		}
		if (type.isCapture() || type.isWildcardType()
				|| type.isIntersectionType() || type.isAnonymous()
				|| type.isLocal()) {
			return null;
		}
		StringBuilder name = new StringBuilder();
		ITypeBinding erasure = type.getErasure();
		if (erasure.getPackage() != null
				&& "java.lang".equals(erasure.getPackage().getName())
				&& !erasure.isNested()) {
			name.append(erasure.getName());
		} else {
			name.append(erasure.getQualifiedName());
		}
		if (type.isParameterizedType()) {
			name.append('<');
			ITypeBinding[] arguments = type.getTypeArguments();
			for (int i = 0; i < arguments.length; ++i) {
				String argument = typeArgument(arguments[i]);
				if (argument == null) {
					return null;
				}
				name.append(i > 0 ? ", " : "").append(argument);
			}
			name.append('>');
		}
		return name.toString();
	}

	private static String typeArgument(ITypeBinding argument) {
		if (!argument.isWildcardType()) {
			return typeName(argument);
		}
		if (argument.getBound() == null) {
			return "?";
		}
		String bound = typeName(argument.getBound());
		return bound == null ? null : (argument.isUpperbound() ? "? extends "
				: "? super ") + bound;
	}

	// Fixes found after parsing, on the original source without bindings

	/**
	 * The problems with fixes added to those that have a mechanical one. The
	 * problems' locations are in the text J2C parsed, which matches the
	 * original text up to any rewritten lambda on the same line.
	 */
	static List<Problem> addFixes(SourceProject project, SourceUnit source,
			List<Problem> problems) throws IOException {
		boolean fixable = false;
		for (Problem problem : problems) {
			fixable |= problem.fixes.isEmpty() && afterParse(problem.id);
		}
		if (!fixable) {
			return problems;
		}

		File file = source.file();
		String original = project.sourceText(source);
		SourceText text = new SourceText(original);
		CompilationUnit unit = parse(original);
		Names names = new Names(unit);
		List<Problem> result = new ArrayList<Problem>();
		for (Problem problem : problems) {
			List<Fix> fixes = Collections.emptyList();
			if (problem.fixes.isEmpty() && afterParse(problem.id)
					&& file.equals(problem.file)) {
				fixes = fixesAfterParse(file, text, unit, names, problem);
			}
			result.add(fixes.isEmpty() ? problem : problem.withFixes(fixes));
		}
		return result;
	}

	private static boolean afterParse(String id) {
		return ProblemCatalog.PATTERN_OUTSIDE_METHOD.equals(id)
				|| ProblemCatalog.TEST_TIMEOUT.equals(id)
				|| ProblemCatalog.TEST_PRIVATE_METHOD.equals(id);
	}

	private static CompilationUnit parse(String source) {
		ASTParser parser = ASTParser.newParser(AST.JLS17);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		Map<String, String> options = new HashMap<String, String>();
		JavaCore.setComplianceOptions(JavaCore.VERSION_17, options);
		parser.setCompilerOptions(options);
		parser.setSource(source.toCharArray());
		return (CompilationUnit) parser.createAST(null);
	}

	private static List<Fix> fixesAfterParse(File file, SourceText text,
			CompilationUnit unit, Names names, Problem problem) {
		if (ProblemCatalog.PATTERN_OUTSIDE_METHOD.equals(problem.id)) {
			PatternInstanceofExpression pattern = (PatternInstanceofExpression) locate(
					text, unit, problem, new Matcher() {
						@Override
						public ASTNode match(ASTNode node) {
							return node instanceof PatternInstanceofExpression
									&& outsideStatements(node) ? node : null;
						}
					});
			return pattern == null ? Collections.<Fix> emptyList()
					: fieldInitializer(file, text, names, pattern);
		}

		MethodDeclaration method = (MethodDeclaration) locate(text, unit,
				problem, new Matcher() {
					@Override
					public ASTNode match(ASTNode node) {
						// Test problems are reported at the method's name
						return node instanceof SimpleName
								&& node.getParent() instanceof MethodDeclaration
								&& node.getLocationInParent() == MethodDeclaration.NAME_PROPERTY ? node
								.getParent() : null;
					}
				});
		if (method == null) {
			return Collections.emptyList();
		}
		return ProblemCatalog.TEST_TIMEOUT.equals(problem.id) ? removeTimeout(
				file, text, method) : removePrivate(file, text, method);
	}

	private interface Matcher {
		/** What node identifies, or null when it is not a candidate */
		ASTNode match(ASTNode node);
	}

	/**
	 * The candidate at the problem's line and column, or the only candidate
	 * on its line: a rewritten lambda earlier on the line moves the column.
	 */
	private static ASTNode locate(final SourceText text, CompilationUnit unit,
			final Problem problem, final Matcher matcher) {
		final List<ASTNode> onLine = new ArrayList<ASTNode>();
		final ASTNode[] exact = new ASTNode[1];
		unit.accept(new ASTVisitor() {
			@Override
			public void preVisit(ASTNode node) {
				int start = node.getStartPosition();
				if (text.line(start) != problem.line) {
					return;
				}
				ASTNode match = matcher.match(node);
				if (match != null) {
					onLine.add(match);
					if (text.column(start) == problem.column) {
						exact[0] = match;
					}
				}
			}
		});
		return exact[0] != null ? exact[0] : onLine.size() == 1 ? onLine.get(0)
				: null;
	}

	/** As J2C reports it: not in a statement, and not in a lambda */
	private static boolean outsideStatements(ASTNode node) {
		for (ASTNode parent = node.getParent(); parent != null
				&& !(parent instanceof BodyDeclaration); parent = parent
				.getParent()) {
			if (parent instanceof Statement || parent instanceof LambdaExpression) {
				return false;
			}
		}
		return true;
	}

	/**
	 * T f = init; becomes T f = initF(); with private T initF() { return
	 * init; } after the field.
	 */
	private static List<Fix> fieldInitializer(File file, SourceText text,
			Names names, ASTNode pattern) {
		VariableDeclarationFragment fragment = null;
		ASTNode parent = pattern;
		for (; parent != null && !(parent instanceof BodyDeclaration); parent = parent
				.getParent()) {
			if (parent instanceof VariableDeclarationFragment
					&& parent.getParent() instanceof FieldDeclaration
					&& parent.getLocationInParent() == FieldDeclaration.FRAGMENTS_PROPERTY) {
				fragment = (VariableDeclarationFragment) parent;
			}
		}
		if (fragment == null || fragment.getInitializer() == null
				|| !fragment.extraDimensions().isEmpty()) {
			return Collections.emptyList();
		}
		FieldDeclaration field = (FieldDeclaration) fragment.getParent();
		ASTNode type = field.getParent();
		if (type instanceof AnnotationTypeDeclaration
				|| !(type instanceof AbstractTypeDeclaration || type instanceof AnonymousClassDeclaration)) {
			return Collections.emptyList();
		}
		String indentation = text.indentation(field.getStartPosition());
		if (indentation == null) {
			return Collections.emptyList();
		}
		boolean isStatic = Modifier.isStatic(field.getModifiers())
				|| (type instanceof TypeDeclaration && ((TypeDeclaration) type)
						.isInterface());

		String fieldName = fragment.getName().getIdentifier();
		String name = names.get(fragment, "init"
				+ Character.toUpperCase(fieldName.charAt(0))
				+ fieldName.substring(1));
		Expression initializer = fragment.getInitializer();
		String nl = text.lineSeparator();
		String declaration = nl + nl + indentation + "private "
				+ (isStatic ? "static " : "")
				+ text.text().substring(field.getType().getStartPosition(),
						end(field.getType())) + " " + name + "() {" + nl
				+ indentation + SourceText.indentUnit(indentation) + "return "
				+ text.text().substring(initializer.getStartPosition(),
						end(initializer)) + ";" + nl + indentation + "}";
		int position = afterMember(text, field);
		List<SourceEdit> edits = new ArrayList<SourceEdit>();
		edits.add(SourceEdit.of(file, text, initializer.getStartPosition(),
				end(initializer), name + "()"));
		edits.add(SourceEdit.of(file, text, position, position, declaration));
		return Collections.singletonList(new Fix("Move the initializer of "
				+ fieldName + " into a method", edits));
	}

	/** @Timeout and @Test(timeout = ...) removed from the method */
	private static List<Fix> removeTimeout(File file, SourceText text,
			MethodDeclaration method) {
		List<SourceEdit> edits = new ArrayList<SourceEdit>();
		for (Object modifier : method.modifiers()) {
			if (!(modifier instanceof Annotation)) {
				continue;
			}
			Annotation annotation = (Annotation) modifier;
			String name = annotation.getTypeName().getFullyQualifiedName();
			if (name.equals("Timeout") || name.endsWith(".Timeout")) {
				edits.add(removeWithSpace(file, text, annotation));
			} else if ((name.equals("Test") || name.endsWith(".Test"))
					&& annotation instanceof NormalAnnotation) {
				List<?> pairs = ((NormalAnnotation) annotation).values();
				for (int i = 0; i < pairs.size(); ++i) {
					MemberValuePair pair = (MemberValuePair) pairs.get(i);
					if (!pair.getName().getIdentifier().equals("timeout")) {
						continue;
					}
					if (pairs.size() == 1) {
						// @Test(timeout = 100) -> @Test
						edits.add(SourceEdit.of(file, text,
								annotation.getTypeName().getStartPosition()
										+ annotation.getTypeName().getLength(),
								end(annotation), ""));
					} else if (i + 1 < pairs.size()) {
						edits.add(SourceEdit.of(file, text,
								pair.getStartPosition(),
								((ASTNode) pairs.get(i + 1)).getStartPosition(), ""));
					} else {
						edits.add(SourceEdit.of(file, text,
								end((ASTNode) pairs.get(i - 1)), end(pair), ""));
					}
				}
			}
		}
		return edits.isEmpty() ? Collections.<Fix> emptyList() : Collections
				.singletonList(new Fix("Remove the timeout", edits));
	}

	private static List<Fix> removePrivate(File file, SourceText text,
			MethodDeclaration method) {
		for (Object modifier : method.modifiers()) {
			if (modifier instanceof Modifier && ((Modifier) modifier).isPrivate()) {
				Modifier privateModifier = (Modifier) modifier;
				int end = end(privateModifier);
				while (end < text.text().length()
						&& (text.text().charAt(end) == ' ' || text.text()
								.charAt(end) == '\t')) {
					++end;
				}
				return Collections.singletonList(new Fix("Make "
						+ method.getName().getIdentifier() + " package-private",
						Collections.singletonList(SourceEdit.of(file, text,
								privateModifier.getStartPosition(), end, ""))));
			}
		}
		return Collections.emptyList();
	}

	/**
	 * Removes node with the spaces after it, or its whole line when nothing
	 * else is on it.
	 */
	private static SourceEdit removeWithSpace(File file, SourceText text,
			ASTNode node) {
		int start = node.getStartPosition();
		int end = end(node);
		String rest = text.text().substring(end, text.lineEnd(end));
		if (text.indentation(start) != null && rest.trim().isEmpty()) {
			return SourceEdit.of(file, text, text.lineStart(start),
					text.nextLineStart(end), "");
		}
		while (end < text.text().length()
				&& (text.text().charAt(end) == ' ' || text.text().charAt(end) == '\t')) {
			++end;
		}
		return SourceEdit.of(file, text, start, end, "");
	}

	/** The member of a class that node is part of */
	private static BodyDeclaration member(ASTNode node) {
		for (ASTNode parent = node.getParent(); parent != null; parent = parent
				.getParent()) {
			if (parent instanceof BodyDeclaration) {
				return (BodyDeclaration) parent;
			}
		}
		return null;
	}

	/**
	 * Where a new member goes after member: the end of its last line when
	 * only a line comment follows it there, else just after it.
	 */
	private static int afterMember(SourceText text, ASTNode member) {
		int end = end(member);
		String rest = text.text().substring(end, text.lineEnd(end)).trim();
		return rest.isEmpty() || rest.startsWith("//") ? text.lineEnd(end) : end;
	}

	private static int end(ASTNode node) {
		return node.getStartPosition() + node.getLength();
	}
}
