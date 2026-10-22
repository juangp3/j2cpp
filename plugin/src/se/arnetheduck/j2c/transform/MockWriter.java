package se.arnetheduck.j2c.transform;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IPackageBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.TypeDeclaration;

/**
 * Writes a gMock class for each top-level and member interface of the
 * selected units (--mocks), for C++ tests written by hand:
 * mock/&lt;package&gt;/Mock&lt;Interface&gt;.hpp declares
 * &lt;package&gt;::Mock&lt;Interface&gt;, which derives from the translated
 * interface and mocks every abstract method, also inherited ones, with
 * MOCK_METHOD and the erased C++ types the translator uses (so generic
 * methods are mocked with their erasure). Default methods keep their Java
 * bodies; where C++ redeclares one as pure, the mock forwards to it. Where
 * a Java class implementing the interface would get a bridge (an abstract
 * method overriding a generic one with another erasure), the mock has the
 * same bridge, so both C++ signatures reach one mocked method. Methods of
 * java.lang.Object that an interface redeclares keep Object's
 * implementation, as in translated classes. What gMock cannot express is
 * reported as {@link ProblemCatalog#MOCK_UNSUPPORTED}.
 */
final class MockWriter {
	/** MOCK_METHOD takes at most this many parameters */
	static final int MAX_PARAMETERS = 15;

	private MockWriter() {
	}

	/** What the mock does for one C++ virtual function */
	private enum Action {
		/** MOCK_METHOD */
		MOCK,
		/** Calls the default method that C++ redeclared as pure */
		FORWARD,
		/** Calls the method overriding it with another erasure */
		BRIDGE
	}

	/** A C++ virtual function of the interface and what the mock does */
	private static final class Member {
		/** The name and erased parameters, as C++ sees the signature */
		final String key;
		/** Declarations of the signature, by an interface or as a bridge */
		final List<Declaration> declarations = new ArrayList<Declaration>();
		Action action;
		/** The Java method implementing it, for FORWARD and BRIDGE */
		IMethodBinding target;
		ITypeBinding returnType;

		Member(String key) {
			this.key = key;
		}

		IMethodBinding method() {
			return declarations.get(0).method;
		}
	}

	/** A declaration of a C++ virtual function in an interface's header */
	private static final class Declaration {
		final ITypeBinding owner;
		final IMethodBinding method;
		final ITypeBinding returnType;
		final boolean pure;

		Declaration(ITypeBinding owner, IMethodBinding method,
				ITypeBinding returnType, boolean pure) {
			this.owner = owner.getErasure();
			this.method = method.getMethodDeclaration();
			this.returnType = returnType.getErasure();
			this.pure = pure;
		}
	}

	/** The members of one mock, or the problems that prevent it */
	private static final class Plan {
		final ITypeBinding type;
		final List<Member> members = new ArrayList<Member>();
		final List<String> problems = new ArrayList<String>();

		Plan(ITypeBinding type) {
			this.type = type;
		}
	}

	/** The interfaces of a unit that get mocks, with their declarations */
	private static List<TypeDeclaration> interfaces(CompilationUnit ast) {
		final List<TypeDeclaration> ret = new ArrayList<TypeDeclaration>();
		ast.accept(new ASTVisitor() {
			@Override
			public boolean visit(TypeDeclaration node) {
				ITypeBinding binding = node.resolveBinding();
				if (node.isInterface() && binding != null
						&& !binding.isLocal()) {
					ret.add(node);
				}
				return true;
			}
		});
		return ret;
	}

	/** The interfaces in the unit that cannot be mocked, at their names */
	static List<Problem> problems(Transformer ctx, File file,
			CompilationUnit ast) {
		List<Problem> problems = new ArrayList<Problem>();
		for (TypeDeclaration node : interfaces(ast)) {
			Plan plan = plan(ctx, node.resolveBinding());
			int start = node.getName().getStartPosition();
			for (String message : plan.problems) {
				problems.add(Problem.of(ProblemCatalog.MOCK_UNSUPPORTED,
						"gMock class", message).at(file,
						ast.getLineNumber(start),
						ast.getColumnNumber(start) + 1));
			}
		}
		return problems;
	}

	/**
	 * Writes the mocks of the unit's interfaces and adds their paths,
	 * relative to root, to written
	 */
	static void write(Transformer ctx, IPath root, CompilationUnit ast,
			Set<String> written) throws IOException {
		for (TypeDeclaration node : interfaces(ast)) {
			Plan plan = plan(ctx, node.resolveBinding());
			if (!plan.problems.isEmpty()) {
				// Reported by problems before anything is written
				throw new IllegalStateException(plan.problems.get(0));
			}
			written.add(write(ctx, root, plan));
		}
	}

	/** The C++ name of the mock class, without its namespace */
	static String className(ITypeBinding type) {
		return "Mock" + CName.of(type);
	}

	/** The mock's path relative to the output root */
	static String path(ITypeBinding type) {
		IPackageBinding pkg = TransformUtil.elementPackage(type);
		String dir = pkg == null || pkg.getName().isEmpty() ? "" : pkg
				.getName().replace('.', '/') + "/";
		return "mock/" + dir + className(type) + ".hpp";
	}

	private static String write(Transformer ctx, IPath root, Plan plan)
			throws IOException {
		ITypeBinding type = plan.type;
		Set<String> includes = new TreeSet<String>();
		for (Member member : plan.members) {
			// Covariant returns and bridge casts need the complete types
			for (Declaration declaration : member.declarations) {
				if (!declaration.returnType.isEqualTo(member.returnType)) {
					include(ctx, member.returnType, includes);
				}
			}
			if (member.action == Action.BRIDGE) {
				IMethodBinding target = member.target.getMethodDeclaration();
				ITypeBinding[] from = member.method().getParameterTypes();
				ITypeBinding[] to = target.getParameterTypes();
				for (int i = 0; i < to.length; ++i) {
					if (!from[i].getErasure().isEqualTo(to[i].getErasure())) {
						include(ctx, to[i].getErasure(), includes);
					}
				}
				if (!target.getReturnType().getErasure().isEqualTo(
						member.returnType)) {
					include(ctx, target.getReturnType().getErasure(), includes);
				}
			}
		}
		includes.remove(TransformUtil.include(type));

		String file = path(type);
		IPackageBinding pkg = TransformUtil.elementPackage(type);
		String namespace = pkg == null || pkg.getName().isEmpty() ? null
				: CName.of(pkg).replace(".", "::");
		String i1 = TransformUtil.indent(1);
		String i2 = TransformUtil.indent(2);

		PrintWriter out = FileUtil.open(root.append(file).toFile());
		try {
			out.println("// Generated by J2C from "
					+ type.getErasure().getQualifiedName()
					+ ": a gMock class for C++ tests (j2c --mocks).");
			out.println("// Abstract methods are mocked; default methods keep "
					+ "their Java bodies.");
			out.println();
			out.println("#pragma once");
			out.println();
			out.println("#include <gmock/gmock.h>");
			out.println();
			out.println(TransformUtil.include(type));
			for (String include : includes) {
				out.println(include);
			}
			out.println();
			if (namespace != null) {
				out.println("namespace " + namespace + " {");
				out.println();
			}
			out.println("class " + className(type));
			out.println(i1 + ": public virtual " + CName.qualified(type, true));
			out.println("{");
			out.println("public:");
			for (Member member : plan.members) {
				IMethodBinding method = member.method();
				ITypeBinding[] parameters = method.getParameterTypes();
				String returnType = TransformUtil.qualifiedRef(
						member.returnType, true);
				if (member.action == Action.MOCK) {
					StringBuilder types = new StringBuilder();
					for (int i = 0; i < parameters.length; ++i) {
						if (i > 0) {
							types.append(", ");
						}
						types.append(TransformUtil.qualifiedRef(
								parameters[i].getErasure(), true));
					}
					out.println(i1 + "MOCK_METHOD(" + returnType + ", "
							+ CName.of(method) + ", (" + types + "), (override));");
					continue;
				}

				IMethodBinding target = member.target.getMethodDeclaration();
				out.println(i1 + "// "
						+ (member.action == Action.FORWARD ? "The default method "
								+ target.getDeclaringClass().getErasure()
										.getQualifiedName() + "." + target.getName()
								+ ", which C++ declares pure here"
								: "Bridge to " + target.getName() + "("
										+ javaParameters(target)
										+ "), as a Java class implementing "
										+ type.getErasure().getName() + " has"));
				StringBuilder signature = new StringBuilder();
				StringBuilder arguments = new StringBuilder();
				ITypeBinding[] to = target.getParameterTypes();
				for (int i = 0; i < parameters.length; ++i) {
					if (i > 0) {
						signature.append(", ");
						arguments.append(", ");
					}
					signature.append(TransformUtil.qualifiedRef(
							parameters[i].getErasure(), true) + " a" + i);
					if (parameters[i].getErasure().isEqualTo(
							to[i].getErasure())) {
						arguments.append("a" + i);
					} else {
						arguments.append("dynamic_cast< "
								+ TransformUtil.qualifiedRef(to[i].getErasure(),
										true) + " >(a" + i + ")");
					}
				}
				out.println(i1 + returnType + " " + CName.of(method) + "("
						+ signature + ") override");
				out.println(i1 + "{");
				String callee = member.action == Action.FORWARD ? CName
						.qualified(target.getDeclaringClass(), true)
						+ "::" + CName.of(target) : CName.of(target);
				out.println(i2
						+ (TransformUtil.isVoid(member.returnType) ? ""
								: "return ") + callee + "(" + arguments + ");");
				out.println(i1 + "}");
			}
			out.println("};");
			if (namespace != null) {
				out.println();
				out.println("}");
			}
		} finally {
			out.close();
		}
		return file;
	}

	private static void include(Transformer ctx, ITypeBinding type,
			Set<String> includes) {
		if (type.isPrimitive()) {
			return;
		}
		ctx.hardDep(type);
		includes.add(TransformUtil.include(type));
	}

	private static String javaParameters(IMethodBinding method) {
		StringBuilder ret = new StringBuilder();
		for (ITypeBinding parameter : method.getParameterTypes()) {
			if (ret.length() > 0) {
				ret.append(", ");
			}
			ret.append(parameter.getErasure().getName());
		}
		return ret.toString();
	}

	/** The C++ signature of a method: its name and erased parameters */
	private static String key(IMethodBinding method) {
		method = method.getMethodDeclaration();
		StringBuilder ret = new StringBuilder(CName.of(method));
		ret.append('(');
		for (ITypeBinding parameter : method.getParameterTypes()) {
			ret.append(parameter.getErasure().getKey()).append(';');
		}
		return ret.append(')').toString();
	}

	private static boolean isVirtual(IMethodBinding method) {
		return !method.isConstructor()
				&& !Modifier.isStatic(method.getModifiers())
				&& !Modifier.isPrivate(method.getModifiers());
	}

	/** The interface and its superinterfaces, without duplicates */
	private static List<ITypeBinding> hierarchy(ITypeBinding type) {
		List<ITypeBinding> all = new ArrayList<ITypeBinding>();
		all.add(type);
		all.addAll(TypeUtil.interfaces(type));
		List<ITypeBinding> ret = new ArrayList<ITypeBinding>();
		Set<String> seen = new HashSet<String>();
		for (ITypeBinding tb : all) {
			if (seen.add(tb.getErasure().getKey())) {
				ret.add(tb);
			}
		}
		return ret;
	}

	/** Is base a proper superinterface of derived (both erased)? */
	private static boolean isProperBase(ITypeBinding base,
			ITypeBinding derived) {
		if (base.isEqualTo(derived)) {
			return false;
		}
		for (ITypeBinding tb : TypeUtil.interfaces(derived)) {
			if (tb.getErasure().isEqualTo(base)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Works out the members of the mock, modelling the C++ declarations
	 * that the header writers emit for each interface of the hierarchy:
	 * declared methods (pure when abstract, except those a base already
	 * declares), bridges of default methods, and the pure redeclarations of
	 * names inherited from several bases (Header.dupeNames).
	 */
	private static Plan plan(Transformer ctx, ITypeBinding type) {
		Plan plan = new Plan(type);
		ITypeBinding object = ctx.resolve(Object.class);
		List<ITypeBinding> hierarchy = hierarchy(type);

		Map<String, Member> members = new LinkedHashMap<String, Member>();
		for (ITypeBinding tb : hierarchy) {
			ITypeBinding erased = tb.getErasure();
			for (IMethodBinding mb : erased.getDeclaredMethods()) {
				if (!isVirtual(mb) || TransformUtil.baseDeclared(ctx, erased, mb)) {
					continue;
				}
				declare(members, new Declaration(erased, mb, mb.getReturnType(),
						Modifier.isAbstract(mb.getModifiers())));
				if (Modifier.isDefault(mb.getModifiers())) {
					IMethodBinding bridged = bridged(erased, mb);
					if (bridged != null) {
						declare(members, new Declaration(erased, bridged,
								bridged.getReturnType(), false));
					}
				}
			}
			List<ITypeBinding> covariant = new ArrayList<ITypeBinding>();
			for (IMethodBinding dupe : Header.dupeNames(erased, object,
					covariant)) {
				if (isVirtual(dupe)) {
					declare(members, new Declaration(erased, dupe,
							dupe.getReturnType(), true));
				}
			}
		}

		// The Java view of the hierarchy, with type arguments, to find
		// which method implements each C++ signature
		List<IMethodBinding> javaMethods = new ArrayList<IMethodBinding>();
		for (ITypeBinding tb : hierarchy) {
			for (IMethodBinding mb : tb.getDeclaredMethods()) {
				if (isVirtual(mb)) {
					javaMethods.add(mb);
				}
			}
		}

		for (Member member : members.values()) {
			boolean pure = false;
			for (Declaration declaration : finalOverriders(member)) {
				pure |= declaration.pure;
			}

			IMethodBinding implementation = implementation(member, javaMethods);
			boolean sameSignature = implementation == null
					|| key(implementation).equals(member.key);
			boolean isAbstract = implementation == null
					|| Modifier.isAbstract(implementation.getModifiers());
			if (isAbstract && sameSignature) {
				member.action = Action.MOCK;
			} else if (!pure) {
				// A default method or the interface's bridge implements it
				continue;
			} else {
				member.action = sameSignature ? Action.FORWARD : Action.BRIDGE;
				member.target = implementation;
			}

			member.returnType = returnType(member);
			IMethodBinding method = member.method();
			String name = type.getErasure().getQualifiedName() + "."
					+ method.getName() + "(" + javaParameters(method) + ")";
			if (member.returnType == null) {
				StringBuilder types = new StringBuilder();
				for (Declaration declaration : member.declarations) {
					if (types.indexOf(declaration.returnType.getQualifiedName()
							+ ",") < 0) {
						types.append(declaration.returnType.getQualifiedName()
								+ ", ");
					}
				}
				plan.problems.add("Cannot mock " + name + ": its inherited "
						+ "declarations return "
						+ types.substring(0, types.length() - 2)
						+ ", and no erased C++ return type overrides them all");
			} else if (member.action == Action.MOCK
					&& method.getParameterTypes().length > MAX_PARAMETERS) {
				plan.problems.add("Cannot mock " + name + ": it has "
						+ method.getParameterTypes().length
						+ " parameters, and gMock's MOCK_METHOD takes at most "
						+ MAX_PARAMETERS);
			}
			plan.members.add(member);
		}
		return plan;
	}

	private static void declare(Map<String, Member> members,
			Declaration declaration) {
		String key = key(declaration.method);
		Member member = members.get(key);
		if (member == null) {
			member = new Member(key);
			members.put(key, member);
		}
		member.declarations.add(declaration);
	}

	/**
	 * The method a default method gets a bridge for in its interface's
	 * header (TransformUtil.declareBridge), or null
	 */
	private static IMethodBinding bridged(ITypeBinding type,
			IMethodBinding mb) {
		for (IMethodBinding mb2 : TypeUtil.methods(TypeUtil.allBases(type,
				null), TypeUtil.overrides(mb))) {
			// The only bridge, as declareBridge writes one at most
			IMethodBinding declaration = mb2.getMethodDeclaration();
			if (!TransformUtil.sameParameters(mb, declaration, true)) {
				return declaration;
			}
		}
		return null;
	}

	/** The declarations no other declaration's interface derives from */
	private static List<Declaration> finalOverriders(Member member) {
		List<Declaration> ret = new ArrayList<Declaration>();
		outer: for (Declaration a : member.declarations) {
			for (Declaration b : member.declarations) {
				if (isProperBase(a.owner, b.owner)) {
					continue outer;
				}
			}
			ret.add(a);
		}
		return ret;
	}

	/**
	 * The Java method that implements the member's signature in the
	 * interface: the most specific method that is, or overrides, one of
	 * its declarations, preferring a default method; null when none is
	 * found
	 */
	private static IMethodBinding implementation(Member member,
			List<IMethodBinding> javaMethods) {
		List<IMethodBinding> candidates = new ArrayList<IMethodBinding>();
		for (IMethodBinding mb : javaMethods) {
			IMethodBinding md = mb.getMethodDeclaration();
			for (Declaration declaration : member.declarations) {
				if (md.isEqualTo(declaration.method)
						|| md.overrides(declaration.method)) {
					candidates.add(mb);
					break;
				}
			}
		}

		IMethodBinding ret = null;
		outer: for (IMethodBinding a : candidates) {
			ITypeBinding owner = a.getDeclaringClass().getErasure();
			for (IMethodBinding b : candidates) {
				if (isProperBase(owner, b.getDeclaringClass().getErasure())) {
					continue outer;
				}
			}
			if (ret == null || !Modifier.isAbstract(a.getModifiers())
					&& Modifier.isAbstract(ret.getModifiers())) {
				ret = a;
			}
		}
		return ret;
	}

	/**
	 * The erased return type that every declaration's return type accepts
	 * (the most derived, for covariant returns), or null when there is none
	 */
	private static ITypeBinding returnType(Member member) {
		outer: for (Declaration a : member.declarations) {
			for (Declaration b : member.declarations) {
				if (!a.returnType.isEqualTo(b.returnType)
						&& (a.returnType.isPrimitive()
								|| b.returnType.isPrimitive() || !a.returnType
								.isSubTypeCompatible(b.returnType))) {
					continue outer;
				}
			}
			return a.returnType;
		}
		return null;
	}
}
