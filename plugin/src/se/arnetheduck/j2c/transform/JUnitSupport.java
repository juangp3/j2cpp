package se.arnetheduck.j2c.transform;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMemberValuePairBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

/**
 * Finds JUnit 4 and JUnit 5 test classes for the generated GoogleTest
 * wrappers (see GTestWriter), and reports test features the wrappers cannot
 * run. Annotations are matched by qualified name, so the JUnit JARs (or
 * stand-ins declaring the annotations) must be on the class path.
 */
final class JUnitSupport {
	private static final List<String> TEST = Arrays.asList("org.junit.Test",
			"org.junit.jupiter.api.Test");
	private static final List<String> BEFORE_EACH = Arrays.asList(
			"org.junit.Before", "org.junit.jupiter.api.BeforeEach");
	private static final List<String> AFTER_EACH = Arrays.asList(
			"org.junit.After", "org.junit.jupiter.api.AfterEach");
	private static final List<String> BEFORE_ALL = Arrays.asList(
			"org.junit.BeforeClass", "org.junit.jupiter.api.BeforeAll");
	private static final List<String> AFTER_ALL = Arrays.asList(
			"org.junit.AfterClass", "org.junit.jupiter.api.AfterAll");
	private static final List<String> DISABLED = Arrays.asList(
			"org.junit.Ignore", "org.junit.jupiter.api.Disabled");
	private static final List<String> GENERATED_TESTS = Arrays.asList(
			"org.junit.jupiter.params.ParameterizedTest",
			"org.junit.jupiter.api.RepeatedTest",
			"org.junit.jupiter.api.TestFactory",
			"org.junit.jupiter.api.TestTemplate");
	private static final List<String> EXTENSIONS = Arrays.asList(
			"org.junit.runner.RunWith", "org.junit.Rule", "org.junit.ClassRule",
			"org.junit.jupiter.api.extension.ExtendWith",
			"org.junit.jupiter.api.extension.RegisterExtension");
	private static final List<String> MOCKING = Arrays.asList("org.mockito",
			"org.powermock", "org.easymock");

	/** One test method, as the wrapper runs it */
	static final class TestMethod {
		final IMethodBinding method;
		/** Null when enabled, else the reason given (possibly empty) */
		final String disabledReason;
		/** JUnit 4's @Test(expected = ...), or null */
		final ITypeBinding expected;

		TestMethod(IMethodBinding method, String disabledReason,
				ITypeBinding expected) {
			this.method = method;
			this.disabledReason = disabledReason;
			this.expected = expected;
		}
	}

	/** A runnable test class, with lifecycle methods in JUnit's order */
	static final class TestClass {
		final ITypeBinding type;
		final List<IMethodBinding> beforeAll = new ArrayList<IMethodBinding>();
		final List<IMethodBinding> afterAll = new ArrayList<IMethodBinding>();
		final List<IMethodBinding> beforeEach = new ArrayList<IMethodBinding>();
		final List<IMethodBinding> afterEach = new ArrayList<IMethodBinding>();
		final List<TestMethod> tests = new ArrayList<TestMethod>();

		TestClass(ITypeBinding type) {
			this.type = type;
		}
	}

	private JUnitSupport() {
	}

	/** Runnable (concrete, top-level or static nested) test classes */
	static List<TestClass> testClasses(CompilationUnit ast) {
		final List<TestClass> classes = new ArrayList<TestClass>();
		ast.accept(new ASTVisitor() {
			@Override
			public boolean visit(TypeDeclaration node) {
				ITypeBinding type = node.resolveBinding();
				if (type != null && isRunnable(type)) {
					TestClass testClass = describe(type);
					if (!testClass.tests.isEmpty()) {
						classes.add(testClass);
					}
				}
				return true;
			}
		});
		return classes;
	}

	private static boolean isRunnable(ITypeBinding type) {
		return !type.isInterface()
				&& !Modifier.isAbstract(type.getModifiers())
				&& !type.isLocal()
				&& !type.isAnonymous()
				&& (!type.isMember() || Modifier.isStatic(type.getModifiers()));
	}

	private static TestClass describe(ITypeBinding type) {
		List<ITypeBinding> chain = new ArrayList<ITypeBinding>();
		for (ITypeBinding t = type; t != null
				&& !"java.lang.Object".equals(t.getErasure().getQualifiedName()); t = t
				.getSuperclass()) {
			chain.add(0, t.getErasure());
		}

		// Superclass methods first; an override replaces what it overrides,
		// as JUnit only runs the most specific declaration
		Map<String, IMethodBinding> methods = new LinkedHashMap<String, IMethodBinding>();
		for (ITypeBinding t : chain) {
			for (IMethodBinding method : t.getDeclaredMethods()) {
				if (!method.isConstructor()) {
					String signature = signature(method);
					methods.remove(signature);
					methods.put(signature, method);
				}
			}
		}

		String classDisabled = disabledReason(type);
		TestClass testClass = new TestClass(type);
		for (IMethodBinding method : methods.values()) {
			if (Modifier.isPrivate(method.getModifiers())
					|| method.getParameterTypes().length > 0) {
				continue; // reported by problems()
			}
			if (annotation(method, BEFORE_ALL) != null) {
				testClass.beforeAll.add(method);
			}
			if (annotation(method, AFTER_ALL) != null) {
				testClass.afterAll.add(0, method);
			}
			if (annotation(method, BEFORE_EACH) != null) {
				testClass.beforeEach.add(method);
			}
			if (annotation(method, AFTER_EACH) != null) {
				testClass.afterEach.add(0, method);
			}
			IAnnotationBinding test = annotation(method, TEST);
			if (test != null) {
				String disabled = classDisabled != null ? classDisabled
						: disabledReason(method);
				testClass.tests.add(new TestMethod(method, disabled,
						expectedException(test)));
			}
		}
		Collections.sort(testClass.tests, new Comparator<TestMethod>() {
			@Override
			public int compare(TestMethod a, TestMethod b) {
				return a.method.getName().compareTo(b.method.getName());
			}
		});
		return testClass;
	}

	/** Test features the GoogleTest wrappers cannot run */
	static List<Problem> problems(final File file, final CompilationUnit ast) {
		final List<Problem> problems = new ArrayList<Problem>();
		ast.accept(new ASTVisitor() {
			@Override
			public boolean visit(ImportDeclaration node) {
				String name = node.getName().getFullyQualifiedName();
				for (String library : MOCKING) {
					if (name.equals(library) || name.startsWith(library + ".")) {
						add(node, ProblemCatalog.TEST_MOCKING,
								"Mocking library " + name
										+ " is not supported in translated tests");
					}
				}
				return false;
			}

			@Override
			public boolean visit(TypeDeclaration node) {
				ITypeBinding type = node.resolveBinding();
				if (type == null) {
					return true;
				}
				if (annotation(type, Arrays.asList("org.junit.jupiter.api.Nested")) != null) {
					add(node.getName(), ProblemCatalog.TEST_NESTED,
							"@Nested test class " + type.getName()
									+ " is not supported in translated tests");
				}
				IAnnotationBinding extension = annotation(type, EXTENSIONS);
				if (extension != null) {
					add(node.getName(), ProblemCatalog.TEST_EXTENSION, "@"
							+ extension.getName() + " on " + type.getName()
							+ " is not supported in translated tests");
				}
				if (annotation(type, Arrays.asList("org.junit.jupiter.api.TestInstance")) != null) {
					add(node.getName(), ProblemCatalog.TEST_INSTANCE_LIFECYCLE,
							"@TestInstance on " + type.getName()
									+ " is not supported in translated tests");
				}
				return true;
			}

			@Override
			public boolean visit(FieldDeclaration node) {
				for (Object fragment : node.fragments()) {
					IVariableBinding field = ((VariableDeclarationFragment) fragment)
							.resolveBinding();
					IAnnotationBinding rule = field == null ? null : annotation(
							field, EXTENSIONS);
					if (rule != null) {
						add(node, ProblemCatalog.TEST_EXTENSION, "@" + rule.getName()
								+ " field " + field.getName()
								+ " is not supported in translated tests");
					}
				}
				return true;
			}

			@Override
			public boolean visit(MethodDeclaration node) {
				IMethodBinding method = node.resolveBinding();
				if (method == null) {
					return true;
				}
				IAnnotationBinding generated = annotation(method, GENERATED_TESTS);
				if (generated != null) {
					add(node.getName(), ProblemCatalog.TEST_UNSUPPORTED_KIND, "@"
							+ generated.getName() + " method " + method.getName()
							+ " is not supported in translated tests");
				}
				IAnnotationBinding extension = annotation(method, EXTENSIONS);
				if (extension != null) {
					add(node.getName(), ProblemCatalog.TEST_EXTENSION, "@"
							+ extension.getName() + " on " + method.getName()
							+ " is not supported in translated tests");
				}
				IAnnotationBinding test = annotation(method, TEST);
				if (annotation(method, Arrays.asList("org.junit.jupiter.api.Timeout")) != null
						|| (test != null && member(test, "timeout") instanceof Long && (Long) member(
								test, "timeout") > 0)) {
					add(node.getName(), ProblemCatalog.TEST_TIMEOUT, "Timeout on "
							+ method.getName()
							+ " is not supported in translated tests");
				}

				boolean runs = test != null || annotation(method, BEFORE_ALL) != null
						|| annotation(method, AFTER_ALL) != null
						|| annotation(method, BEFORE_EACH) != null
						|| annotation(method, AFTER_EACH) != null;
				if (runs && Modifier.isPrivate(method.getModifiers())) {
					add(node.getName(), ProblemCatalog.TEST_PRIVATE_METHOD,
							"Private test or lifecycle method " + method.getName()
									+ " cannot be run by the translated tests");
				}
				if (runs && method.getParameterTypes().length > 0) {
					add(node.getName(), ProblemCatalog.TEST_PARAMETERS,
							"Test or lifecycle method " + method.getName()
									+ " has parameters, which translated tests cannot supply");
				}
				return true;
			}

			private void add(ASTNode node, String id, String message) {
				problems.add(Problem.of(id, "unit test", message).at(file,
						ast.getLineNumber(node.getStartPosition()),
						ast.getColumnNumber(node.getStartPosition()) + 1));
			}
		});
		return problems;
	}

	private static String signature(IMethodBinding method) {
		StringBuilder signature = new StringBuilder(method.getName()).append('(');
		for (ITypeBinding parameter : method.getParameterTypes()) {
			signature.append(parameter.getErasure().getQualifiedName()).append(',');
		}
		return signature.append(')').toString();
	}

	private static String disabledReason(IBinding binding) {
		IAnnotationBinding disabled = annotation(binding, DISABLED);
		if (disabled == null) {
			return null;
		}
		Object value = member(disabled, "value");
		return value instanceof String ? (String) value : "";
	}

	private static ITypeBinding expectedException(IAnnotationBinding test) {
		Object expected = member(test, "expected");
		// JUnit 4's default is the marker class org.junit.Test.None
		return expected instanceof ITypeBinding
				&& !((ITypeBinding) expected).getQualifiedName().equals(
						"org.junit.Test.None") ? (ITypeBinding) expected : null;
	}

	private static IAnnotationBinding annotation(IBinding binding,
			List<String> names) {
		for (IAnnotationBinding annotation : binding.getAnnotations()) {
			ITypeBinding type = annotation.getAnnotationType();
			if (type != null && names.contains(type.getQualifiedName())) {
				return annotation;
			}
		}
		return null;
	}

	private static Object member(IAnnotationBinding annotation, String name) {
		for (IMemberValuePairBinding pair : annotation.getAllMemberValuePairs()) {
			if (pair.getName().equals(name)) {
				return pair.getValue();
			}
		}
		return null;
	}
}
