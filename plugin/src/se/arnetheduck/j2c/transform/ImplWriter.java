package se.arnetheduck.j2c.transform;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.AnnotationTypeMemberDeclaration;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.ArrayCreation;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Assignment.Operator;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.BreakStatement;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.ContinueStatement;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.InstanceofExpression;
import org.eclipse.jdt.core.dom.LabeledStatement;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PatternInstanceofExpression;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.PrimitiveType.Code;
import org.eclipse.jdt.core.dom.RecordDeclaration;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.SwitchCase;
import org.eclipse.jdt.core.dom.SwitchExpression;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.SynchronizedStatement;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.ThrowStatement;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclarationStatement;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.TypeParameter;
import org.eclipse.jdt.core.dom.UnionType;
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.eclipse.jdt.core.dom.YieldStatement;

public class ImplWriter extends TransformWriter {
	private static final String i1 = TransformUtil.indent(1);

	private final IPath root;

	private List<MethodDeclaration> constructors = new ArrayList<MethodDeclaration>();

	private final Impl impl;
	private final String qcname;
	private final String name;

	private final List<List<String>> locals = new ArrayList<List<String>>();
	private RecordDeclaration record;

	public ImplWriter(IPath root, Transformer ctx, UnitInfo unitInfo,
			TypeInfo typeInfo) {
		super(ctx, unitInfo, typeInfo);
		this.root = root;

		impl = new Impl(ctx, type, deps);
		qcname = CName.qualified(type, false);
		name = CName.of(type);
	}

	public void write(AnnotationTypeDeclaration node) throws Exception {
		String body = getBody(node.bodyDeclarations());
		writeType(body);
	}

	public void write(AnonymousClassDeclaration node) throws Exception {
		String body = getBody(node.bodyDeclarations());
		writeType(body);
	}

	public void write(EnumDeclaration node) throws Exception {
		String body = getBody(node.enumConstants(), node.bodyDeclarations());
		writeType(body);
	}

	public void write(TypeDeclaration node) throws Exception {
		String body = getBody(node.bodyDeclarations());
		writeType(body);
	}

	public void write(RecordDeclaration node) throws Exception {
		record = node;
		String body = getBody(node.bodyDeclarations());
		writeType(body);
	}

	private String getBody(List<BodyDeclaration> declarations) {
		return getBody(new ArrayList<EnumConstantDeclaration>(), declarations);
	}

	private String getBody(List<EnumConstantDeclaration> enums,
			List<BodyDeclaration> declarations) {
		StringWriter body = new StringWriter();
		out = new PrintWriter(body);

		visitAll(enums);

		visitAll(declarations);

		out.close();
		return body.toString();
	}

	private void writeType(String body) throws Exception {
		String cinit = getCinit();
		String clinit = getClinit();
		String extras = getExtras();

		impl.write(root, extras + body, "", cinit, clinit, false);

		ctx.addImpl(type);
	}

	private String getExtras() {
		StringWriter sw = new StringWriter();
		out = new PrintWriter(sw);

		printDefaultInitCtor();
		printCtors();
		printAnonCtors();
		printInit();
		printRecordMethods();

		out.close();
		out = null;

		return sw.toString();
	}

	private String getCinit() {
		List<VariableDeclarationFragment> consts = typeInfo.getInit(true).strings;
		if (consts.isEmpty()) {
			return null;
		}

		int oi = indent;
		indent = 1;

		PrintWriter old = out;
		StringWriter sw = new StringWriter();
		out = new PrintWriter(sw);
		for (VariableDeclarationFragment vdf : consts) {
			printi();
			vdf.getName().accept(this);
			print(" = ");
			vdf.getInitializer().accept(this);
			println(";");
		}

		indent = oi;

		out.close();
		out = old;

		return sw.toString();
	}

	private String getClinit() {
		InitInfo info = typeInfo.getInit(true);
		if (info.nodes.isEmpty()) {
			return null;
		}

		int oi = indent;
		indent = 1;
		PrintWriter old = out;
		StringWriter sw = new StringWriter();
		out = new PrintWriter(sw);

		printInitInfo(info);

		indent = oi;

		out.close();
		out = old;

		return sw.toString();
	}

	private void printInit() {
		InitInfo info = typeInfo.getInit(false);

		if (info.nodes.isEmpty()) {
			return;
		}

		println("void " + qcname + "::" + CName.INSTANCE_INIT + "()");
		println("{");
		printInitInfo(info);
		println("}");
		println();
	}

	private void printInitInfo(InitInfo info) {
		indent++;

		for (ASTNode node : info.nodes) {
			if (node instanceof VariableDeclarationFragment) {
				VariableDeclarationFragment fragment = (VariableDeclarationFragment) node;
				printi();
				fragment.getName().accept(this);
				print(" = ");
				fragment.getInitializer().accept(this);
				println(";");
				ITypeBinding ib = fragment.getInitializer()
						.resolveTypeBinding();
				if (!ib.isEqualTo(fragment.resolveBinding().getType())) {
					hardDep(ib);
				}
			} else if (node instanceof Initializer) {
				printi();
				Initializer initializer = (Initializer) node;
				initializer.getBody().accept(this);
				println();
			}
		}

		indent--;
	}

	private void printCtors() {
		if (!TypeUtil.isClassLike(type) || type.isAnonymous()) {
			return;
		}

		boolean hasEmpty = false;
		for (MethodDeclaration md : constructors) {
			locals.add(new ArrayList<String>());
			printi(qcname + "::" + name + "(");

			String sep = TransformUtil.printExtraCtorParams(ctx, out, type,
					typeInfo.closures(), deps, false, md.resolveBinding());

			if (md.isCompactConstructor()) {
				print(sep);
				printRecordParameters();
				hasEmpty = record.recordComponents().isEmpty();
			} else if (!md.parameters().isEmpty()) {
				print(sep);
				visitAllCSV(md.parameters(), false);
			} else {
				hasEmpty = true;
			}

			println(") "
					+ TransformUtil.throwsDecl(md.thrownExceptionTypes()));

			indent++;
			printDefaultInitCall(md.resolveBinding());
			indent--;

			println("{");
			indent++;

			printi(CName.CTOR + "(");
			sep = TransformUtil.printEnumCtorCallParams(out, type, "");
			if (md.isCompactConstructor()) {
				print(sep);
				printRecordParameterNames();
			} else if (!md.parameters().isEmpty()) {
				print(sep);
				printNames(md.parameters());
			}

			println(");");

			indent--;
			println("}");
			println();
			locals.remove(locals.size() - 1);
		}

		if (record != null) {
			if (!RecordSupport.hasCanonicalConstructor(record)) {
				printRecordCanonicalConstructor();
			}
			return;
		}

		printEmptyCtor(hasEmpty);
	}

	private void printRecordCanonicalConstructor() {
		print(qcname + "::" + name + "(");
		printRecordParameters();
		println(")");
		indent++;
		printi(": " + name + "(" + TransformUtil.makeDefaultInitTag() + ")");
		indent--;
		println();
		println("{");
		indent++;
		printi(CName.CTOR + "(");
		printRecordParameterNames();
		println(");");
		indent--;
		println("}");
		println();

		print("void " + qcname + "::" + CName.CTOR + "(");
		printRecordParameters();
		println(")");
		println("{");
		indent++;
		if (type.getSuperclass() != null) {
			printlni("super::" + CName.CTOR + "();");
		}
		if (typeInfo.hasInit()) {
			printlni(CName.INSTANCE_INIT + "();");
		}
		for (Object object : record.recordComponents()) {
			SingleVariableDeclaration component = (SingleVariableDeclaration) object;
			IVariableBinding binding = component.resolveBinding();
			printi(CName.of(binding, type) + " = "
					+ CName.keywords(component.getName().getIdentifier())
					+ ";");
			println();
		}
		indent--;
		println("}");
		println();
	}

	private void printRecordParameters() {
		String sep = "";
		for (Object object : record.recordComponents()) {
			SingleVariableDeclaration component = (SingleVariableDeclaration) object;
			IVariableBinding binding = component.resolveBinding();
			print(sep);
			sep = ", ";
			print(TransformUtil.varTypeCName(binding.getModifiers(),
					binding.getType(), type, deps));
			print(" " + CName.keywords(component.getName().getIdentifier()));
		}
	}

	private void printRecordParameterNames() {
		String sep = "";
		for (Object object : record.recordComponents()) {
			SingleVariableDeclaration component = (SingleVariableDeclaration) object;
			print(sep + CName.keywords(component.getName().getIdentifier()));
			sep = ", ";
		}
	}

	private void printRecordMethods() {
		if (record == null) {
			return;
		}

		// equals, hashCode and toString call methods on component values,
		// which needs their complete types
		for (Object object : record.recordComponents()) {
			ITypeBinding componentType = ((SingleVariableDeclaration) object)
					.resolveBinding().getType();
			if (!componentType.isPrimitive()) {
				hardDep(componentType);
			}
		}

		for (Object object : record.recordComponents()) {
			SingleVariableDeclaration component = (SingleVariableDeclaration) object;
			if (RecordSupport.hasAccessor(record, component)) {
				continue;
			}
			IVariableBinding binding = component.resolveBinding();
			// Out-of-class definition: names relative to the record's own
			// scope (such as a sibling nested type) are not visible here
			softDep(binding.getType());
			print(TransformUtil.qualifiedRef(binding.getType(), true));
			print(" " + qcname + "::"
					+ CName.keywords(component.getName().getIdentifier())
					+ (RecordSupport.accessorOverrides(type, component) ? "()"
							: "() const"));
			println();
			println("{");
			indent++;
			printlni("return " + CName.of(binding, type) + ";");
			indent--;
			println("}");
			println();
		}

		if (!RecordSupport.hasEquals(record)) {
			print("bool " + qcname + "::equals("
					+ TransformUtil.relativeRef(ctx.resolve(Object.class),
							type, true)
					+ " other)");
			println();
			println("{");
			indent++;
			printlni("if (this == other) return true;");
			printlni("auto* that = dynamic_cast<" + qcname
					+ "*>(other);");
			printlni("if (that == nullptr) return false;");
			for (Object object : record.recordComponents()) {
				SingleVariableDeclaration component = (SingleVariableDeclaration) object;
				IVariableBinding binding = component.resolveBinding();
				String field = CName.of(binding, type);
				String otherField = "that->" + field;
				String comparison = recordComparison(binding.getType(), field,
						otherField);
				printlni("if (!(" + comparison + ")) return false;");
			}
			printlni("return true;");
			indent--;
			println("}");
			println();
		}

		if (!RecordSupport.hasMethod(record, "hashCode", 0)) {
			print("int " + qcname + "::hashCode()");
			println();
			println("{");
			indent++;
			printlni("std::uint32_t hash = 0u;");
			int componentIndex = 0;
			for (Object object : record.recordComponents()) {
				SingleVariableDeclaration component = (SingleVariableDeclaration) object;
				IVariableBinding binding = component.resolveBinding();
				String value = recordComponentHash(binding.getType(),
						CName.of(binding, type));
				if (componentIndex == 0) {
					printlni("hash = " + value + ";");
				} else {
					printlni("hash = hash * 31u + " + value + ";");
				}
				componentIndex++;
			}
			printlni("return std::bit_cast<std::int32_t>(hash);");
			indent--;
			println("}");
			println();
		}

		if (!RecordSupport.hasMethod(record, "toString", 0)) {
			hardDep(ctx.resolve(StringBuilder.class));
			hardDep(ctx.resolve(String.class));
			print(TransformUtil.relativeRef(ctx.resolve(String.class), type,
					false)
					+ " " + qcname + "::toString()");
			println();
			println("{");
			indent++;
			print("return ::java::lang::StringBuilder(u\""
					+ type.getName() + "[\"_j)");
			if (record.recordComponents().isEmpty()) {
				print(".append(u\"]\"_j)->toString();");
			}
			int componentIndex = 0;
			for (Object object : record.recordComponents()) {
				SingleVariableDeclaration component = (SingleVariableDeclaration) object;
				String componentName = component.getName().getIdentifier();
				print((componentIndex == 0 ? "." : "->") + "append(u\""
						+ componentName + "=\"_j)->append("
						+ CName.of(component.resolveBinding(), type) + ")");
				if (component != record.recordComponents()
						.get(record.recordComponents().size() - 1)) {
					print("->append(u\", \"_j)");
				}
				componentIndex++;
			}
			if (!record.recordComponents().isEmpty()) {
				println("->append(u\"]\"_j)->toString();");
			} else {
				println();
			}
			indent--;
			println("}");
			println();
		}
	}

	private static String recordComparison(ITypeBinding componentType,
			String field, String otherField) {
		if (componentType.isPrimitive()) {
			if (componentType.getName().equals("float")) {
				return floatBits(field) + " == " + floatBits(otherField);
			}
			if (componentType.getName().equals("double")) {
				return doubleBits(field) + " == " + doubleBits(otherField);
			}
			return field + " == " + otherField;
		}
		if (componentType.isArray()) {
			return field + " == " + otherField;
		}
		return "(" + field + " == " + otherField + " || (" + field
				+ " != nullptr && " + field + "->equals(" + otherField
				+ ")))";
	}

	private static String recordComponentHash(ITypeBinding componentType,
			String field) {
		StringBuilder code = new StringBuilder();
		if (componentType.isPrimitive()) {
			switch (componentType.getName()) {
			case "boolean":
				return "(" + field + " ? 1231u : 1237u)";
			case "long":
				return "static_cast<std::uint32_t>(static_cast<std::uint64_t>("
						+ field + ") ^ (static_cast<std::uint64_t>(" + field
						+ ") >> 32))";
			case "float":
				return floatBits(field);
			case "double":
				return "static_cast<std::uint32_t>(static_cast<std::uint64_t>("
						+ doubleBits(field) + ") ^ (static_cast<std::uint64_t>("
						+ doubleBits(field) + ") >> 32))";
			default:
				return "static_cast<std::uint32_t>(" + field + ")";
			}
		}
		if (componentType.isArray()) {
			return "static_cast<std::uint32_t>(std::hash<const void*>{}(static_cast<const void*>("
					+ field + ")))";
		}
		code.append("(").append(field).append(" == nullptr ? 0u : ")
				.append("static_cast<std::uint32_t>(").append(field)
				.append("->hashCode()))");
		return code.toString();
	}

	private static String floatBits(String value) {
		return "(std::isnan(" + value
				+ ") ? 0x7fc00000u : std::bit_cast<std::uint32_t>(" + value
				+ "))";
	}

	private static String doubleBits(String value) {
		return "(std::isnan(" + value
				+ ") ? UINT64_C(0x7ff8000000000000) : "
				+ "std::bit_cast<std::uint64_t>(" + value + "))";
	}

	private void printClInitCall() {
		printlni(CName.STATIC_INIT + "();");
	}

	private void printDefaultInitCall(IMethodBinding ctor) {
		String sep;
		printi(": " + name + "(");
		sep = "";
		if (TransformUtil.hasOuterThis(type)) {
			print(TransformUtil.outerThisName(type));
			sep = ", ";
		}

		if (typeInfo.closures() != null) {
			for (IVariableBinding closure : typeInfo.closures()) {
				print(sep + TransformUtil.ctorClosureName(ctx, closure, type,
						ctor));
				sep = ", ";
			}
		}

		println(sep + TransformUtil.makeDefaultInitTag() + ")");
	}

	private void printAnonCtors() {
		if (!type.isAnonymous()) {
			return;
		}

		// In reality, we should only synthesize the constructor actually being
		// used, but...
		List<IMethodBinding> anonCtors = new ArrayList<IMethodBinding>();
		Header.getAnonCtors(type, anonCtors);
		for (IMethodBinding mb : anonCtors) {
			locals.add(new ArrayList<String>());

			printi(qcname + "::" + name + "(");

			String sep = TransformUtil.printExtraCtorParams(ctx, out, type,
					typeInfo.closures(), deps, false, mb);

			if (mb.getParameterTypes().length > 0) {
				out.print(sep);
				TransformUtil.printParams(ctx, out, type, mb, false, deps);
			}

			println(")");
			printAnonCtorBody(mb);

			locals.remove(locals.size() - 1);
		}

		if (anonCtors.isEmpty()) {
			locals.add(new ArrayList<String>());

			printi(qcname + "::" + name + "(");

			TransformUtil.printExtraCtorParams(ctx, out, type,
					typeInfo.closures(), deps, false, null);

			println(")");
			printAnonCtorBody(null);

			locals.remove(locals.size() - 1);
		}
	}

	private void printAnonCtorBody(IMethodBinding mb) {
		indent++;
		printFieldInit(mb);
		indent--;

		println("{");
		indent++;

		printClInitCall();

		if (typeInfo.hasInit()) {
			printlni(CName.INSTANCE_INIT + "();");
		}

		printi(CName.CTOR + "(");
		String sep = TransformUtil.printEnumCtorCallParams(out, type, "");

		if (mb != null && mb.getParameterTypes().length > 0) {
			for (int i = 0; i < mb.getParameterTypes().length; ++i) {
				print(sep + ctx.paramName(mb, i));
				sep = ", ";
			}
		}
		println(");");

		indent--;
		println("}");
		println();
	}

	private void printDefaultInitCtor() {
		if (!TypeUtil.isClassLike(type) || type.isAnonymous()) {
			return;
		}

		print(qcname + "::" + name + "(");
		TransformUtil.printExtraCtorParams(ctx, out, type, typeInfo.closures(),
				deps, true, null);
		println(")");

		indent++;
		printFieldInit(null);
		indent--;

		println("{");
		indent++;
		printClInitCall();
		indent--;
		println("}");
		println();
	}

	private void printEmptyCtor(boolean hasEmpty) {
		if (!constructors.isEmpty()) {
			return;
		}

		print(qcname + "::" + name + "(");
		TransformUtil.printExtraCtorParams(ctx, out, type, typeInfo.closures(),
				deps, false, null);
		println(")");
		indent++;
		printDefaultInitCall(null);
		indent--;
		println("{");
		indent++;

		printi();
		TransformUtil.printEmptyCtorCall(out, type);
		println(";");
		indent--;
		println("}");
		println();

		if (TransformUtil.needsEmptyCtor(hasEmpty, typeInfo.hasInit(), type)) {
			print("void " + qcname + "::" + CName.CTOR + "(");
			TransformUtil.printEnumCtorParams(ctx, out, type, "", deps);
			println(")");
			println("{");
			if (type.getSuperclass() != null) {
				print(i1 + "super::");
				TransformUtil.printEmptyCtorCall(out, type);
				println(";");
			}

			if (typeInfo.hasInit()) {
				printlni(i1 + CName.INSTANCE_INIT + "();");
			}

			println("}");
			println();
		}
	}

	private void printFieldInit(IMethodBinding ctor) {
		ITypeBinding sb = type.getSuperclass();

		String sep = ": ";
		if (sb != null) {
			print(i1 + sep);
			print("super(");
			if (TransformUtil.hasOuterThis(sb)) {
				String sepx = "";
				for (ITypeBinding tb = type; tb.getDeclaringClass() != null; tb = tb
						.getDeclaringClass().getErasure()) {
					print(sepx);
					sepx = "->";
					hardDep(tb.getDeclaringClass());
					print(TransformUtil.outerThisName(tb));
					if (tb.getDeclaringClass()
							.getErasure()
							.isSubTypeCompatible(
									sb.getDeclaringClass().getErasure())) {
						break;
					}
				}

				print(", ");
			}

			println(TransformUtil.makeDefaultInitTag() + ")");
			sep = ", ";
		}

		if (TransformUtil.hasOuterThis(type)
				&& (sb == null || sb.getDeclaringClass() == null || !type
						.getDeclaringClass().getErasure()
						.isEqualTo(sb.getDeclaringClass().getErasure()))) {
			print(i1 + sep);
			hardDep(type.getDeclaringClass());
			printInit(TransformUtil.outerThisName(type));
			sep = ", ";
		}

		for (VariableDeclarationFragment vd : typeInfo.getInit(false).strings) {
			assert (TransformUtil.constantValue(vd) instanceof String);

			print(i1 + sep);
			vd.getName().accept(this);

			print("(");

			vd.getInitializer().accept(this);

			println(")");

			sep = ", ";
		}

		if (typeInfo.closures() != null) {
			for (IVariableBinding closure : typeInfo.closures()) {
				printi(sep);
				println(CName.of(closure, type) + "("
						+ TransformUtil.ctorClosureName(ctx, closure, type,
								ctor)
						+ ")");
				sep = ", ";
			}
		}
	}

	private void printInit(String n) {
		println(n + "(" + n + ")");
	}

	private static class NodeInfo {
		public final ASTNode node;
		public final String str;

		public NodeInfo(ASTNode node, String str) {
			this.node = node;
			this.str = str;
		}
	}

	private final List<NodeInfo> visits = new ArrayList<NodeInfo>();

	/** C++ names of hoisted instanceof pattern variables, by binding key */
	private final Map<String, String> patternNames = new HashMap<String, String>();
	// Boxed variables printed once as assignment targets, without unboxing
	private final Set<Expression> unboxedTargets = new HashSet<Expression>();
	/** Statements wrapped in braces to scope their pattern variables */
	private final Set<ASTNode> patternBlocks = new HashSet<ASTNode>();

	@Override
	public boolean preVisit2(ASTNode node) {
		for (Snippet snippet : ctx.snippets) {
			if (!snippet.node(ctx, this, node)) {
				return false;
			}
		}

		if (node instanceof Statement) {
			hoistPatterns((Statement) node);
		}

		if (node instanceof Expression) {
			Expression expr = (Expression) node;

			if ((expr.resolveBoxing() || expr.resolveUnboxing())
					&& checkBoxNesting(expr) && !unboxedTargets.remove(expr)) {

				if (expr.resolveBoxing()) {
					ITypeBinding tb2 = boxingType(expr);
					hardDep(tb2);
					print(CName.relative(tb2, type, true) + "::valueOf(");

					visits.add(new NodeInfo(node, ")"));
				} else if (expr.resolveUnboxing()) {
					ITypeBinding tb = expr.resolveTypeBinding().getErasure();
					if (TransformUtil.reverses.containsKey(tb
							.getQualifiedName())) {
						hardDep(tb);
						print("(");
						npc();
						visits.add(new NodeInfo(node, "))->"
								+ TransformUtil.reverses.get(tb
										.getQualifiedName()) + "Value()"));
					}
				}
			}

			if (node instanceof Name) {
				Name name = ((Name) node);
				IBinding b = name.resolveBinding();
				if (b instanceof IVariableBinding) {
					IVariableBinding vb = (IVariableBinding) b;
					castName(node, name, vb);
				}
			}
		}

		return super.preVisit2(node);
	}

	private void castName(ASTNode node, Name name, IVariableBinding vb) {
		if (!vb.isField()) {
			return;
		}

		if (!vb.getVariableDeclaration().getType().isTypeVariable()) {
			return;
		}

		if (!TransformUtil.variableErased(vb.getVariableDeclaration())) {
			return;
		}

		ASTNode parent = name.getParent();
		if (parent instanceof VariableDeclarationFragment
				&& node != ((VariableDeclarationFragment) parent)
						.getInitializer()) {
			return;
		}

		if (parent instanceof SingleVariableDeclaration
				&& node != ((SingleVariableDeclaration) parent)
						.getInitializer()) {
			return;
		}

		if (parent instanceof Name) {
			Name p = (Name) parent;
			if (p.resolveBinding().isEqualTo(vb)) {
				return;
			}
		}

		if (parent instanceof FieldAccess) {
			return;
		}

		if (parent instanceof Assignment) {
			if (((Assignment) parent).getLeftHandSide() == node) {
				return;
			}
		}

		if (parent instanceof EnumConstantDeclaration) {
			return;
		}

		javaCast(vb.getVariableDeclaration().getType().getErasure(),
				vb.getType());
		visits.add(new NodeInfo(node, ")"));
	}

	/**
	 * Java allows conversions on assignment when boxing byte, short, char -
	 * this method returns the type after conversion
	 */
	private static ITypeBinding boxingType(Expression node) {
		ITypeBinding ret = null;
		ASTNode parent = node.getParent();
		if (parent instanceof Assignment) {
			ret = ((Assignment) parent).getLeftHandSide().resolveTypeBinding();
		}

		if (parent instanceof VariableDeclarationFragment) {
			ret = ((VariableDeclarationFragment) parent).resolveBinding()
					.getType();
		}

		if (parent instanceof SingleVariableDeclaration) {
			ret = ((SingleVariableDeclaration) parent).resolveBinding()
					.getType();
		}

		if (ret != null
				&& (TransformUtil.same(ret, Byte.class)
						|| TransformUtil.same(ret, Short.class) || TransformUtil
							.same(ret, Character.class))) {
			return ret;
		}

		return node.getAST().resolveWellKnownType(
				TransformUtil.primitives.get(node.resolveTypeBinding()
						.getName()));
	}

	private static boolean checkBoxNesting(Expression expr) {
		ASTNode parent = expr.getParent();
		if (parent instanceof ParenthesizedExpression) {
			assert (((ParenthesizedExpression) parent).resolveBoxing() || ((ParenthesizedExpression) parent)
					.resolveUnboxing());
			return false;
		}

		if (parent instanceof Name) {
			return false;
		}

		if (parent instanceof MethodInvocation) {
			MethodInvocation mi = (MethodInvocation) parent;
			if (mi.getExpression() == expr || mi.getName() == expr) {
				return false;
			}
		}

		if (parent instanceof FieldAccess
				&& ((FieldAccess) parent).getName() == expr) {
			return false;
		}

		return true;
	}

	/**
	 * Declares the pattern variables of a statement's own instanceof
	 * patterns before it; the patterns assign them as they match. Declaring
	 * in the enclosing block gives Java's flow scoping, so "if (!(o
	 * instanceof T t)) return;" leaves t usable afterwards.
	 */
	private void hoistPatterns(Statement statement) {
		List<PatternInstanceofExpression> patterns = ownPatterns(statement);
		if (patterns.isEmpty()) {
			return;
		}

		ASTNode parent = statement.getParent();
		// Jumping to a case label past an initialized declaration is an error
		boolean inSwitch = parent instanceof SwitchStatement;
		if (!(parent instanceof Block) && !inSwitch
				&& !(parent instanceof LabeledStatement)
				&& !(parent instanceof SwitchExpression)) {
			// An unbraced body, such as "if (c) return o instanceof T t;"
			printlni("{");
			indent++;
			locals.add(new ArrayList<String>());
			patternBlocks.add(statement);
		}

		for (PatternInstanceofExpression pattern : patterns) {
			SingleVariableDeclaration variable = pattern.getRightOperand();
			IVariableBinding binding = variable.resolveBinding();
			ITypeBinding target = variable.getType().resolveBinding();
			hardDep(target);

			// Sibling statements may reuse a name that C++ would see twice
			String name = CName.of(binding);
			while (localInScope(name)) {
				name = name + "_";
			}
			if (!locals.isEmpty()) {
				locals.get(locals.size() - 1).add(name);
			}
			patternNames.put(binding.getKey(), name);
			printlni(CName.relative(target, type, true) + "* " + name
					+ (inSwitch ? ";" : " = nullptr;"));
		}
	}

	/** Patterns whose innermost enclosing statement is statement */
	private static List<PatternInstanceofExpression> ownPatterns(
			final Statement statement) {
		final List<PatternInstanceofExpression> patterns = new ArrayList<PatternInstanceofExpression>();
		statement.accept(new ASTVisitor() {
			@Override
			public boolean visit(PatternInstanceofExpression node) {
				ASTNode owner = node.getParent();
				while (!(owner instanceof Statement)) {
					owner = owner.getParent();
				}
				if (owner == statement) {
					patterns.add(node);
				}
				return true;
			}

			@Override
			public boolean visit(AnonymousClassDeclaration node) {
				return false;
			}

			@Override
			public boolean visit(TypeDeclarationStatement node) {
				return false;
			}
		});
		return patterns;
	}

	@Override
	public void postVisit(ASTNode node) {
		if (patternBlocks.remove(node)) {
			indent--;
			printlni("}");
			locals.remove(locals.size() - 1);
		}

		if (visits.isEmpty()) {
			return;
		}

		NodeInfo last = visits.get(visits.size() - 1);

		if (!last.node.equals(node)) {
			return;
		}

		print(last.str);
		visits.remove(visits.size() - 1);
	}

	@Override
	public boolean visit(AnnotationTypeDeclaration node) {
		ctx.write(unitInfo, root, node);

		return false;
	}

	@Override
	public boolean visit(AnnotationTypeMemberDeclaration node) {
		return false;
	}

	@Override
	public boolean visit(AnonymousClassDeclaration node) {
		ctx.write(unitInfo, root, node);

		return false;
	}

	@Override
	public boolean visit(ArrayAccess node) {
		hardDep(node.getArray().resolveTypeBinding());

		print("(*");
		node.getArray().accept(this);
		print(")[");
		node.getIndex().accept(this);
		print("]");

		return false;
	}

	@Override
	public boolean visit(ArrayCreation node) {
		ITypeBinding tb = node.getType().resolveBinding();
		hardDep(tb);
		boolean parens = TransformUtil.needsExtraParens(node);
		if (parens) {
			print("(");
		}

		if (node.dimensions().size() == 1 || node.getInitializer() != null) {
			print("new " + CName.relative(tb, type, true));
			if (node.getInitializer() != null) {
				node.getInitializer().accept(this);
			} else {
				print("(");
				((Expression) node.dimensions().get(0)).accept(this);
				print(")");
			}

		} else {
			print("__newMultiArray< " + CName.relative(tb, type, true) + " >");
			String csv = "(";
			for (Iterator<Expression> it = node.dimensions().iterator(); it
					.hasNext();) {
				Expression e = it.next();
				print(csv);
				e.accept(this);
				csv = ",";
			}
			print(")");
		}

		if (parens) {
			print(")");
		}
		return false;
	}

	@Override
	public boolean visit(ArrayInitializer node) {
		if (!(node.getParent() instanceof ArrayCreation)) {
			print("(new ");
			ITypeBinding at = node.resolveTypeBinding();
			print(CName.qualified(at, true));
			hardDep(at);
		}

		print("({");
		if (!node.expressions().isEmpty()) {
			String s = "";
			ITypeBinding ct = node.resolveTypeBinding().getComponentType();
			for (Expression e : (List<Expression>) node.expressions()) {
				hardDep(e.resolveTypeBinding());
				if (node.expressions().size() > 1) {
					println();
					printi(TransformUtil.indent(1));
				}

				print(s);
				s = ", ";
				arrayInitCast(ct, e);
			}
		}

		if (node.expressions().size() > 1) {
			println();
			printi();
		}

		print("})");

		if (!(node.getParent() instanceof ArrayCreation)) {
			print(")");
		}

		return false;
	}

	private void arrayInitCast(ITypeBinding ct, Expression e) {
		boolean cast = TransformUtil.isNullLiteral(e)
				|| !ct.isEqualTo(e.resolveTypeBinding());
		if (cast) {
			staticCast(e.resolveTypeBinding(), ct);
		}
		e.accept(this);
		if (cast) {
			print(")");
		}
	}

	@Override
	public boolean visit(Assignment node) {
		Expression lhs = node.getLeftHandSide();
		ITypeBinding ltb = lhs.resolveTypeBinding();
		Expression rhs = node.getRightHandSide();
		ITypeBinding rtb = rhs.resolveTypeBinding();

		hardDep(rtb);

		if (TransformUtil.same(ltb, String.class)
				&& node.getOperator() == Operator.PLUS_ASSIGN) {

			if (lhs instanceof ArrayAccess) {
				ArrayAccess aa = (ArrayAccess) lhs;
				hardDep(aa.getArray().resolveTypeBinding());
				aa.getArray().accept(this);
				print("->set(");
				aa.getIndex().accept(this);
				print(", ");

			} else {
				lhs.accept(this);
				print(" = ");
			}

			print("::java::lang::StringBuilder(");
			lhs.accept(this);
			print(").append(");
			castNull(rhs);
			print(")->toString()");

			if (lhs instanceof ArrayAccess) {
				print(")");
			}
			hardDep(ctx.resolve(StringBuilder.class));

			return false;
		}

		if (node.getOperator() != Operator.ASSIGN && boxedTarget(lhs)) {
			String operator = node.getOperator().toString();
			printBoxedUpdate(lhs, operator.substring(0, operator.length() - 1), rhs);
			return false;
		}

		if (printIntegerAssignment(node)) {
			return false;
		}

		if (node.getOperator() == Operator.RIGHT_SHIFT_UNSIGNED_ASSIGN) {
			lhs.accept(this);
			print(" = ");
			if (ltb.getName().equals("long")) {
				print("static_cast<uint64_t>(");
			} else {
				print("static_cast<uint32_t>(");
			}
			lhs.accept(this);
			print(") >> ");
			rhs.accept(this);

			return false;
		}

		if (node.getOperator().equals(Operator.REMAINDER_ASSIGN)) {
			if (ltb.getName().equals("float") || ltb.getName().equals("double")) {
				deps.setNeedsFmod();
				lhs.accept(this);
				print(" = ");
				print("std::fmod(");
				lhs.accept(this);
				print(", ");
				rhs.accept(this);
				print(")");

				return false;
			}
		}

		if (node.getOperator() == Operator.ASSIGN
				&& lhs instanceof ArrayAccess
				&& !((ArrayAccess) lhs).getArray().resolveTypeBinding()
						.getComponentType().isPrimitive()) {
			ArrayAccess aa = (ArrayAccess) lhs;
			hardDep(aa.getArray().resolveTypeBinding());
			aa.getArray().accept(this);
			print("->set(");
			aa.getIndex().accept(this);
			print(", ");
			rhs.accept(this);
			print(")");
			return false;
		}

		lhs.accept(this);

		print(" " + node.getOperator() + " ");

		boolean cast = false;
		if (TransformUtil.needsJavaCast(rtb, ltb)) {
			javaCast(rtb, ltb);
			cast = true;
		}

		rhs.accept(this);

		if (cast) {
			print(")");
		}

		return false;
	}

	@Override
	public boolean visit(Block node) {
		ASTNode parent = node.getParent();

		locals.add(new ArrayList<String>());
		println("{");

		indent++;

		if (parent instanceof MethodDeclaration
				&& Modifier.isStatic(((MethodDeclaration) parent)
						.getModifiers())) {
			printlni("clinit();");
		}

		visitAll(node.statements());

		indent--;
		printi("}");

		locals.remove(locals.size() - 1);
		return false;
	}

	@Override
	public boolean visit(BreakStatement node) {
		if (node.getLabel() != null) {
			printi("goto ");
			printLabelName(node.getLabel());
			print("_break");
		} else if (!enumSwitches.isEmpty()
				&& breakEnclosingStatement(node) == enumSwitches
						.get(enumSwitches.size() - 1)) {
			printi("goto " + enumSwitchLabel() + ";");

		} else {
			printi("break");
		}

		println(";");
		return false;
	}

	private static boolean isLoopStatement(ASTNode node) {
		return node instanceof ForStatement || node instanceof WhileStatement
				|| node instanceof DoStatement;
	}

	private Statement breakEnclosingStatement(ASTNode node) {
		assert (node != null); // Break without enclosing statement?
		if (node instanceof SwitchStatement || isLoopStatement(node)) {
			return (Statement) node;
		}

		return breakEnclosingStatement(node.getParent());
	}

	@Override
	public boolean visit(CastExpression node) {
		ITypeBinding target = node.getType().resolveBinding();
		ITypeBinding source = node.getExpression().resolveTypeBinding();

		if (target.isIntersectionType()) {
			// (A & B) x checks every component; the result is the first one
			ITypeBinding[] bounds = target.getTypeBounds();
			for (int i = 0; i < bounds.length; ++i) {
				javaCast(i + 1 < bounds.length ? bounds[i + 1] : source,
						bounds[i]);
			}
			node.getExpression().accept(this);
			print(")".repeat(bounds.length));
			return false;
		}

		if (isIntegral(target) && isFloating(operandType(node.getExpression()))) {
			// Java saturates, and maps NaN to 0; byte, short and char narrow
			// from int afterwards
			deps.setNeedsJavaMath();
			boolean wide = target.getName().equals("int")
					|| target.getName().equals("long");
			if (!wide) {
				print("static_cast<" + CName.of(target) + ">(");
			}
			print("jf2i<" + (wide ? CName.of(target) : "int32_t") + ">(");
			node.getExpression().accept(this);
			print(wide ? ")" : "))");
			return false;
		}

		if (target.isPrimitive() && source != null && !source.isPrimitive()
				&& !TransformUtil.isNullLiteral(node.getExpression())
				&& !TransformUtil.reverses.containsKey(source.getErasure()
						.getQualifiedName())) {
			// (int) object: a checked cast to the box, then unboxing
			ITypeBinding box = node.getAST().resolveWellKnownType(
					TransformUtil.primitives.get(target.getName()));
			print("(");
			npc();
			javaCast(source, box);
			node.getExpression().accept(this);
			print(")))->" + target.getName() + "Value()");
			return false;
		}

		if (target.isPrimitive()
				|| TransformUtil.isNullLiteral(node.getExpression())) {
			staticCast(source, target);
		} else {
			javaCast(source, target);
		}

		node.getExpression().accept(this);

		print(")");
		return false;
	}

	@Override
	public boolean visit(CatchClause node) {
		print(" catch (");
		node.getException().accept(this);
		hardDep(node.getException().getType().resolveBinding());
		print(") ");
		node.getBody().accept(this);

		return false;
	}

	@Override
	public boolean visit(ClassInstanceCreation node) {
		boolean parens = TransformUtil.needsExtraParens(node);

		if (parens) {
			print("(");
		}
		print("new ");

		ITypeBinding tb = node.getType().resolveBinding();

		if (node.getAnonymousClassDeclaration() != null) {
			tb = node.getAnonymousClassDeclaration().resolveBinding();
			print(CName.of(tb));
			node.getAnonymousClassDeclaration().accept(this);
		} else {
			print(TransformUtil.typeArguments(node.typeArguments()));

			print(CName.relative(tb, type, true));
		}

		hardDep(tb);

		consArgs(node.getExpression(), node.arguments(),
				node.resolveConstructorBinding(), tb, null, 0);

		if (parens) {
			print(")");
		}
		return false;
	}

	private void consArgs(Expression expression, List<Expression> arguments,
			IMethodBinding mb, ITypeBinding tb, String name, int ordinal) {
		print("(");

		String sep = "";
		if (expression != null) {
			expression.accept(this);
			sep = ", ";
		} else if (TransformUtil.hasOuterThis(tb)) {
			ITypeBinding dce = tb.getDeclaringClass().getErasure();
			if (type.getErasure().isSubTypeCompatible(dce.getErasure())) {
				print("this");
			} else {
				String sep2 = "";
				for (ITypeBinding x = type.getErasure(); x.getDeclaringClass() != null
						&& !x.isSubTypeCompatible(dce); x = x
						.getDeclaringClass().getErasure()) {
					hardDep(x.getDeclaringClass());

					print(sep2 + TransformUtil.outerThisName(x));
					sep2 = "->";
				}
			}
			sep = ", ";
		}

		if (tb.isLocal()) {
			TypeInfo localTypeInfo = unitInfo.types.get(tb);
			if (localTypeInfo != null) {
				for (IVariableBinding closure : localTypeInfo.closures()) {
					print(sep + CName.of(closure));
					sep = ", ";
				}
			}
		}

		if (tb.isEnum()) {
			print("u\"" + name + "\"_j, " + ordinal);
			sep = ", ";
			deps.hard(ctx.resolve(String.class));
		}

		if (!arguments.isEmpty()
				|| (mb != null && mb.getMethodDeclaration().isVarargs())) {
			// A varargs constructor called with no arguments still takes an
			// empty array
			print(sep);
			callArgs(mb, arguments, false, false);
		}

		print(")");
	}

	@Override
	public boolean visit(ConditionalExpression node) {
		ITypeBinding tb = node.resolveTypeBinding();
		if (tb != null && tb.isIntersectionType()) {
			// c ? "text" : 1 has type Object & Serializable & Comparable<...>,
			// which C++ cannot name: use its class bound, else Object
			ITypeBinding common = ctx.resolve(Object.class);
			for (ITypeBinding bound : tb.getTypeBounds()) {
				if (bound.isClass()) {
					common = bound.getErasure();
				}
			}
			tb = common;
		}
		node.getExpression().accept(this);
		print(" ? ");
		cast(node.getThenExpression(), tb, true);
		print(" : ");
		cast(node.getElseExpression(), tb, true);
		return false;
	}

	@Override
	public boolean visit(ConstructorInvocation node) {
		printi(TransformUtil.typeArguments(node.typeArguments()));

		print(CName.CTOR);

		callArgs(node.resolveConstructorBinding(), node.arguments(), true, true);

		println(";");
		return false;
	}

	@Override
	public boolean visit(ContinueStatement node) {
		if (node.getLabel() != null) {
			Statement loop = outerLoop(node);
			if (isLabeled(loop, node.getLabel())) {
				printlni("continue;");
			} else {
				printi("{ ");
				printLabelName(node.getLabel());
				println("_continue = true; break; }");
			}
		} else {
			printlni("continue;");
		}

		return false;
	}

	private void printContinueVar(LabeledStatement s) {
		printi("bool ");
		printLabelName(s.getLabel());
		println("_continue = false;");
	}

	private static final List<Class<? extends Statement>> loopStatements = Arrays
			.asList(DoStatement.class, EnhancedForStatement.class,
					ForStatement.class, WhileStatement.class);

	private static boolean isLabeled(Statement s) {
		return s.getParent() instanceof LabeledStatement;
	}

	private static boolean isLabeled(Statement s, SimpleName label) {
		return isLabeled(s)
				&& ((LabeledStatement) s.getParent()).getLabel()
						.getIdentifier().equals(label.getIdentifier());
	}

	private static Statement outerLoop(ASTNode node) {
		for (ASTNode n = node; n != null; n = n.getParent()) {
			if (loopStatements.contains(n.getClass())) {
				return (Statement) n;
			}
		}

		throw new Error("Expected a loop");
	}

	@Override
	public boolean visit(DoStatement node) {
		printi("do ");
		node.getBody().accept(this);
		print(" while (");
		node.getExpression().accept(this);
		println(");");

		return false;
	}

	@Override
	public boolean visit(EnhancedForStatement node) {
		Expression expr = node.getExpression();
		ITypeBinding eb = expr.resolveTypeBinding();
		hardDep(eb);
		deps.setNpc();
		if (eb.isArray()) {
			printi("for(auto ");
			node.getParameter().getName().accept(this);
			print(" : *");
			npcAccept(expr);
			print(") ");
			handleLoopBody(node, node.getBody());
		} else {
			printi("for (auto _i = ");

			npcAccept(expr);
			println("->iterator(); _i->hasNext(); ) {");

			indent++;
			printi();
			node.getParameter().accept(this);
			print(" = ");
			ITypeBinding tb = node.getParameter().getType().resolveBinding();

			if (tb.isPrimitive()) {
				ITypeBinding tbb = node.getAST().resolveWellKnownType(
						TransformUtil.primitives.get(tb.getName()));
				hardDep(tbb);
				npc();
				javaCast(ctx.resolve(Object.class), tbb);
				println("_i->next()))->" + tb.getName() + "Value();");
			} else {
				javaCast(ctx.resolve(Object.class), tb);
				println("_i->next());");
			}

			printi();
			handleLoopBody(node, node.getBody());

			indent--;
			printlni("}");

			hardDep(ctx.resolve(Iterator.class));
		}

		return false;
	}

	@Override
	public boolean visit(EnumConstantDeclaration node) {
		print(qcname + "* " + qcname + "::");

		node.getName().accept(this);

		ITypeBinding tb = node.getAnonymousClassDeclaration() == null ? type
				: node.getAnonymousClassDeclaration().resolveBinding();

		hardDep(tb);
		print(" = new " + CName.qualified(tb, true));

		EnumDeclaration enumDeclaration = (EnumDeclaration) node.getParent();

		consArgs(null, node.arguments(), node.resolveConstructorBinding(), tb,
				CName.of(node.resolveVariable()), enumDeclaration
						.enumConstants().indexOf(node));

		println(";");

		if (node.getAnonymousClassDeclaration() != null) {
			node.getAnonymousClassDeclaration().accept(this);
		}

		return false;
	}

	@Override
	public boolean visit(EnumDeclaration node) {
		ctx.write(unitInfo, root, node);

		return false;
	}

	@Override
	public boolean visit(FieldAccess node) {
		ITypeBinding tb = node.resolveTypeBinding();
		IVariableBinding vb = node.resolveFieldBinding();
		ITypeBinding tbe = vb.getVariableDeclaration().getType().getErasure();
		boolean cast = TransformUtil.variableErased(vb)
				&& !isLeftHandSide(node);

		Expression expr = node.getExpression();

		boolean hidden = false;
		if (cast) {
			javaCast(tbe, tb);
		} else {
			hidden = hidden(expr.resolveTypeBinding(), vb);
		}

		if (hidden) {
			staticCast(tbe, vb.getDeclaringClass());
		}

		npcAccept(expr);
		hardDep(expr.resolveTypeBinding());

		if (hidden) {
			print(")");
		}

		print("->");

		node.getName().accept(this);

		if (cast) {
			print(")");
		}

		return false;
	}

	@Override
	public boolean visit(FieldDeclaration node) {
		Iterable<VariableDeclarationFragment> fragments = node.fragments();
		boolean isStatic = TransformUtil.isStatic(node);

		for (VariableDeclarationFragment f : fragments) {
			if (!isStatic
					&& TransformUtil.constexprValue(f.resolveBinding()) == null) {
				continue;
			}

			IVariableBinding vb = f.resolveBinding();
			boolean asMethod = TransformUtil.asMethod(vb);

			if (asMethod) {
				ITypeBinding tb = vb.getType();
				print(TransformUtil.varTypeCName(vb.getModifiers(), tb, deps));

				println("& " + qcname + "::" + CName.of(vb) + "()");
				printlni("{");
				indent++;
				printlni("clinit();");
				printlni("return " + CName.of(vb) + "_;");
				indent--;
				println("}");
			}

			printi(TransformUtil.fieldModifiers(type, node.getModifiers(),
					false, TransformUtil.constexprValue(f) != null));

			print(TransformUtil.varTypeCName(vb.getModifiers(), vb.getType(),
					deps));

			print(" ");

			print(qcname + "::");

			if (asMethod) {
				println(CName.of(vb) + "_;");
			} else {
				f.getName().accept(this);
				println(";");
			}

			println();
		}

		return false;
	}

	@Override
	public boolean visit(ForStatement node) {
		printi("for (");

		visitAllCSV(node.initializers(), false);

		print("; ");

		if (node.getExpression() != null) {
			node.getExpression().accept(this);
		}

		print("; ");

		visitAllCSV(node.updaters(), false);

		print(") ");

		handleLoopBody(node, node.getBody());

		return false;
	}

	private boolean skipIndent = false;

	@Override
	public boolean visit(IfStatement node) {
		if (!skipIndent) {
			printi();
		}

		print("if(");
		skipIndent = false;

		node.getExpression().accept(this);

		print(")");

		boolean thenBlock = node.getThenStatement() instanceof Block;
		if (thenBlock) {
			print(" ");
		} else {
			println();
			indent++;
		}

		node.getThenStatement().accept(this);

		if (!thenBlock) {
			indent--;
		}

		if (node.getElseStatement() != null) {
			// An else-if declaring pattern variables needs a block for them
			boolean elseif = skipIndent = node.getElseStatement() instanceof IfStatement
					&& ownPatterns(node.getElseStatement()).isEmpty();
			if (thenBlock) {
				print(" ");
			} else {
				printi();
			}
			print("else");
			boolean elseBlock = skipIndent
					|| node.getElseStatement() instanceof Block;
			if (elseBlock) {
				print(" ");
			} else {
				println();
				indent++;
			}

			node.getElseStatement().accept(this);

			if (!elseBlock) {
				indent--;
			}

			if (!elseif && elseBlock) {
				println();
			}
		} else {
			println();
		}

		return false;
	}


	@Override
	public boolean visit(InfixExpression node) {
		final List<Expression> extendedOperands = node.extendedOperands();
		ITypeBinding tb = node.resolveTypeBinding();
		Expression left = node.getLeftOperand();
		Expression right = node.getRightOperand();

		ITypeBinding lt = left.resolveTypeBinding();
		ITypeBinding rt = right.resolveTypeBinding();

		if (TransformUtil.same(tb, String.class)) {
			print("::java::lang::StringBuilder().append(");

			castNull(left);

			print(")->append(");

			castNull(right);

			print(")");

			indent++;
			for (Expression e : extendedOperands) {
				println();
				printi("->append(");
				castNull(e);
				print(")");
			}
			indent--;

			print("->toString()");

			hardDep(ctx.resolve(StringBuilder.class));

			return false;
		}

		if (node.getOperator().equals(InfixExpression.Operator.REMAINDER)) {
			if (tb.getName().equals("float") || tb.getName().equals("double")) {
				deps.setNeedsFmod();
				print("std::fmod(");
				cast(left, tb, true);
				print(", ");
				cast(right, tb, true);
				print(")");

				return false;
			}
		}

		if (printIntegerOperation(node)) {
			return false;
		}

		ITypeBinding common = null;
		// JDT gives instanceof pattern operands no type
		if (lt != null && rt != null && !lt.isEqualTo(rt)) {
			// If we have pointer compares, we need the complete type
			hardDep(lt);
			hardDep(rt);
			if (!lt.isPrimitive()
					&& !rt.isPrimitive()
					&& (node.getOperator().equals(
							InfixExpression.Operator.EQUALS) || node
							.getOperator().equals(
									InfixExpression.Operator.NOT_EQUALS))) {
				common = TypeUtil.commonBase(lt, rt, ctx.resolve(Object.class));
			}
		}

		boolean cast = false;
		if (node.getOperator().equals(
				InfixExpression.Operator.RIGHT_SHIFT_UNSIGNED)) {
			print("static_cast<" + CName.of(lt) + ">(");
			cast = true;
			if (lt.getName().equals("long")) {
				print("static_cast<uint64_t>(");
			} else {
				print("static_cast<uint32_t>(");
			}
			left.accept(this);
			print(") >>");
		} else {
			staticCast(left, lt, common);
			print(" "); // for cases like x= i - -1; or x= i++ + ++i;

			print(node.getOperator().toString());
		}

		print(" ");

		staticCast(right, rt, common);

		if (!extendedOperands.isEmpty()) {
			print(" ");

			for (Expression e : extendedOperands) {
				print(node.getOperator() + " ");
				e.accept(this);
				// Pattern operands have no type (see above)
				if (e.resolveTypeBinding() != null) {
					hardDep(e.resolveTypeBinding());
				}
			}
		}

		if (cast) {
			print(")");
		}
		return false;
	}

	/**
	 * Integer division, remainder and shifts through the java_math.hpp
	 * helpers, which give Java's results where C++ is undefined. A single
	 * constant operand that is already safe keeps the plain operator.
	 */
	private boolean printIntegerOperation(InfixExpression node) {
		String helper = integerHelper(node.getOperator().toString());
		if (helper == null || !isIntegral(node.resolveTypeBinding())) {
			return false;
		}
		boolean shift = !helper.equals("jdiv") && !helper.equals("jrem");

		List<Expression> operands = new ArrayList<Expression>();
		operands.add(node.getLeftOperand());
		operands.add(node.getRightOperand());
		operands.addAll(node.extendedOperands());

		String type = promoted(operandType(operands.get(0)), null);
		if (operands.size() == 2
				&& (shift ? safeShiftCount(operands.get(1), type)
						: safeDivisor(operands.get(1)))) {
			return false;
		}

		// Java promotes step by step: (int / int) / long divides as int first
		String[] stepTypes = new String[operands.size()];
		for (int i = 1; i < operands.size(); ++i) {
			if (!shift) {
				type = promoted(type, operandType(operands.get(i)));
			}
			stepTypes[i] = type;
		}

		deps.setNeedsJavaMath();
		for (Expression operand : operands) {
			hardDep(operand.resolveTypeBinding());
		}
		for (int i = operands.size() - 1; i >= 1; --i) {
			print(helper + "<" + stepTypes[i] + ">(");
		}
		operands.get(0).accept(this);
		for (int i = 1; i < operands.size(); ++i) {
			print(", ");
			operands.get(i).accept(this);
			print(")");
		}
		return true;
	}

	/** Compound integer division, remainder and shifts (see above) */
	private boolean printIntegerAssignment(Assignment node) {
		String operator = node.getOperator().toString();
		String helper = integerHelper(operator.substring(0,
				operator.length() - 1));
		ITypeBinding target = node.getLeftHandSide().resolveTypeBinding();
		if (helper == null || !target.isPrimitive() || !isIntegral(target)) {
			return false;
		}
		boolean shift = !helper.equals("jdiv") && !helper.equals("jrem");
		Expression value = node.getRightHandSide();
		if (!shift && !isIntegral(operandType(value))) {
			// Floating-point compound division stays a C++ operation
			return false;
		}

		String type = promoted(target, null);
		if (!shift) {
			type = promoted(type, operandType(value));
		}
		if (!helper.equals("jushr")
				&& (shift ? safeShiftCount(value, type) : safeDivisor(value))) {
			return false;
		}

		deps.setNeedsJavaMath();
		hardDep(value.resolveTypeBinding());
		print(helper + "_set<" + type + ">(");
		node.getLeftHandSide().accept(this);
		print(", ");
		value.accept(this);
		print(")");
		return true;
	}

	private static String integerHelper(String operator) {
		switch (operator) {
		case "/":
			return "jdiv";
		case "%":
			return "jrem";
		case "<<":
			return "jshl";
		case ">>":
			return "jshr";
		case ">>>":
			return "jushr";
		default:
			return null;
		}
	}

	/** The primitive type of an operand, after any unboxing */
	private static ITypeBinding operandType(Expression operand) {
		ITypeBinding type = operand.resolveTypeBinding();
		if (type == null || type.isPrimitive()) {
			return type;
		}
		String unboxed;
		switch (type.getQualifiedName()) {
		case "java.lang.Long":
			unboxed = "long";
			break;
		case "java.lang.Integer":
			unboxed = "int";
			break;
		case "java.lang.Short":
			unboxed = "short";
			break;
		case "java.lang.Byte":
			unboxed = "byte";
			break;
		case "java.lang.Character":
			unboxed = "char";
			break;
		case "java.lang.Double":
			unboxed = "double";
			break;
		case "java.lang.Float":
			unboxed = "float";
			break;
		default:
			return type;
		}
		return operand.getAST().resolveWellKnownType(unboxed);
	}

	private static boolean isIntegral(ITypeBinding type) {
		if (type == null || !type.isPrimitive()) {
			return false;
		}
		switch (type.getName()) {
		case "int":
		case "long":
		case "short":
		case "byte":
		case "char":
			return true;
		default:
			return false;
		}
	}

	private static boolean isFloating(ITypeBinding type) {
		return type != null && type.isPrimitive()
				&& (type.getName().equals("double") || type.getName().equals("float"));
	}

	/** Binary numeric promotion of integral types, as a C++ type name */
	private static String promoted(ITypeBinding type, ITypeBinding other) {
		return promoted(type != null && type.getName().equals("long") ? "int64_t"
				: "int32_t", other);
	}

	private static String promoted(String type, ITypeBinding other) {
		return type.equals("int64_t")
				|| (other != null && other.getName().equals("long")) ? "int64_t"
				: "int32_t";
	}

	private static boolean safeDivisor(Expression divisor) {
		Long value = constantValue(divisor);
		return value != null && value != 0 && value != -1;
	}

	private static boolean safeShiftCount(Expression count, String type) {
		Long value = constantValue(count);
		return value != null && value >= 0
				&& value < (type.equals("int64_t") ? 64 : 32);
	}

	private static Long constantValue(Expression expression) {
		Object value = expression.resolveConstantExpressionValue();
		if (value instanceof Character) {
			return (long) ((Character) value).charValue();
		}
		if (value instanceof Number && !(value instanceof Float)
				&& !(value instanceof Double)) {
			return ((Number) value).longValue();
		}
		return null;
	}

	/**
	 * A JUnit assertion as a j2c_junit.hpp call: values in Java order, then
	 * messages (JUnit 4 passes its message first), with the Java parameter
	 * types as template arguments so overloads convert as in Java.
	 */
	private void printAssertion(MethodInvocation node,
			JUnitSupport.Assertion assertion) {
		deps.setNeedsJUnit();
		ctx.usesJUnitAssertions();
		List<Expression> arguments = node.arguments();

		print("::j2c::junit::" + assertion.helper);
		if (assertion.thrown != null) {
			hardDep(assertion.thrown);
			print("< " + CName.relative(assertion.thrown, type, true) + " >");
		} else if (assertion.typeArguments != null) {
			String separator = "";
			print("< ");
			for (ITypeBinding typeArgument : assertion.typeArguments) {
				print(separator);
				separator = ", ";
				if (typeArgument == null) {
					print("void");
				} else {
					hardDep(typeArgument);
					print(TransformUtil.relativeRef(typeArgument, type, true));
				}
			}
			print(" >");
		}

		print("(");
		String separator = "";
		if (assertion.thrown != null) {
			print("\"" + assertion.thrown.getQualifiedName() + "\"");
			separator = ", ";
		}
		List<Integer> order = new ArrayList<Integer>(assertion.values);
		order.addAll(assertion.messages);
		for (int index : order) {
			print(separator);
			separator = ", ";
			Expression argument = arguments.get(index);
			hardDep(argument.resolveTypeBinding());
			argument.accept(this);
		}
		print(")");
	}

	private void staticCast(Expression left, ITypeBinding lt,
			ITypeBinding common) {
		if (common != null && !lt.isEqualTo(common) && !lt.isNullType()) {
			staticCast(left.resolveTypeBinding(), common);
			left.accept(this);
			print(")");
		} else {
			left.accept(this);
		}
	}

	private void castNull(Expression left) {
		ITypeBinding tb = left.resolveTypeBinding();
		if (tb.isNullType()
				|| !(tb.isPrimitive() || TransformUtil.same(tb, String.class))) {
			hardDep(tb);

			staticCast(tb, ctx.resolve(Object.class));
			left.accept(this);
			print(")");
		} else {
			left.accept(this);
		}
	}

	@Override
	public boolean visit(Initializer node) {
		// Processed using InitInfo
		return false;
	}

	@Override
	public boolean visit(InstanceofExpression node) {
		ITypeBinding lb = node.getLeftOperand().resolveTypeBinding();
		ITypeBinding rb = node.getRightOperand().resolveBinding();

		hardDep(lb);
		hardDep(rb);
		print("dynamic_cast< " + CName.relative(rb, type, true) + "* >(");
		node.getLeftOperand().accept(this);
		print(") != nullptr");
		return false;
	}

	@Override
	public boolean visit(PatternInstanceofExpression node) {
		SingleVariableDeclaration variable = node.getRightOperand();
		ITypeBinding target = variable.getType().resolveBinding();
		hardDep(target);
		String cast = "dynamic_cast< " + CName.relative(target, type, true)
				+ "* >(";

		// Hoisted by the enclosing statement (see hoistPatterns)
		String name = patternNames.get(variable.resolveBinding().getKey());
		print(name == null ? "(" + cast : "((" + name + " = " + cast);
		node.getLeftOperand().accept(this);
		print(name == null ? ") != nullptr)" : ")) != nullptr)");
		return false;
	}

	private List<LabeledStatement> labels = new ArrayList<LabeledStatement>();
	private Map<String, Integer> labelNames = new TreeMap<String, Integer>();

	@Override
	public boolean visit(LabeledStatement node) {
		labels.add(node);
		String id = node.getLabel().getIdentifier();
		if (labelNames.containsKey(id)) {
			labelNames.put(id, labelNames.get(id) + 1);
		} else {
			labelNames.put(id, 0);
		}

		node.getBody().accept(this);

		labels.remove(labels.size() - 1);

		// We put a break label after the body as this is where break <label>
		// statements end up
		println();
		printLabelName(node.getLabel());
		println("_break:;");

		return false;
	}

	private void printLabelName(SimpleName n) {
		n.accept(this);
		int i = labelNames.get(n.getIdentifier());
		print("" + i);
	}

	private void handleLoopBody(Statement loop, Statement body) {
		if (loop.getParent() instanceof LabeledStatement) {
			LabeledStatement ls = (LabeledStatement) loop.getParent();
			println("{");
			indent++;

			printContinueVar(ls);

			if (body instanceof Block) {
				visitAll(((Block) body).statements());
			} else {
				body.accept(this);
			}

			println();

			handleLoopEnd(loop);
			indent--;
			printlni("}");
		} else {
			if (body instanceof Block) {
				body.accept(this);
			} else {
				println();
				indent++;
				printi();
				body.accept(this);
				indent--;
			}

			println();
		}
	}

	private void handleLoopEnd(Statement loop) {
		for (LabeledStatement ls : labels) {
			if (!loopStatements.contains(ls.getBody())) {
				continue;
			}

			printi("if(");
			printLabelName(ls.getLabel());
			print("_continue) ");
			if (isLabeled(loop, ls.getLabel())) {
				println("continue;");
			} else {
				println("break;");
			}
		}
	}

	@Override
	public boolean visit(MethodDeclaration node) {
		IMethodBinding mb = node.resolveBinding();
		if (TransformUtil.baseDeclared(ctx, type, mb)) {
			return false;
		}

		impl.method(mb);

		if (node.getBody() == null) {
			return false;
		}

		locals.add(new ArrayList<String>());

		printi(TransformUtil.typeParameters(node.typeParameters()));

		if (node.isConstructor()) {
			constructors.add(node);

			printi("void " + qcname + "::" + CName.CTOR + "(");

			String sep = TransformUtil.printEnumCtorParams(ctx, out, type, "",
					deps);
			if (!node.parameters().isEmpty()) {
				print(sep);
			}
		} else {
			ITypeBinding rt = TransformUtil.returnType(type, node);
			softDep(rt);
			print(TransformUtil.qualifiedRef(rt, false) + " ");

			print(qcname + "::");

			node.getName().accept(this);

			print("(");
		}

		if (node.isCompactConstructor() && record != null) {
			printRecordParameters();
		} else {
			visitAllCSV(node.parameters(), false);
		}

		print(")");

		println(TransformUtil.throwsDecl(node.thrownExceptionTypes()));

		if (node.isConstructor()) {
			println("{");
			indent++;
			Statement first = null;
			List<Statement> statements = node.getBody().statements();
			if (!statements.isEmpty()) {
				first = statements.get(0);
			}

			if (!(first instanceof ConstructorInvocation)) {
				if (!(first instanceof SuperConstructorInvocation)) {
					if (type.getSuperclass() != null) {
						printi("super::");
						TransformUtil.printEmptyCtorCall(out, type);
						println(";");
					}
				} else {
					first.accept(this);
				}

				if (typeInfo.hasInit()) {
					printlni(CName.INSTANCE_INIT + "();");
				}
			}

			visitAll(first instanceof SuperConstructorInvocation ? statements
					.subList(1, statements.size()) : statements);
			if (node.isCompactConstructor() && record != null) {
				for (Object object : record.recordComponents()) {
					SingleVariableDeclaration component = (SingleVariableDeclaration) object;
					printi(CName.of(component.resolveBinding(), type) + " = "
							+ CName.keywords(
									component.getName().getIdentifier())
							+ ";");
					println();
				}
			}
			indent--;
			print("}");
		} else {
			node.getBody().accept(this);
		}

		locals.remove(locals.size() - 1);

		println();
		println();

		TransformUtil.defineBridge(ctx, out, type, mb, deps);

		return false;
	}

	private void printNames(Iterable<SingleVariableDeclaration> parameters) {
		for (Iterator<SingleVariableDeclaration> it = parameters.iterator(); it
				.hasNext();) {
			SingleVariableDeclaration v = it.next();

			v.getName().accept(this);

			if (it.hasNext()) {
				print(",");
			}
		}
	}

	@Override
	public boolean visit(MethodInvocation node) {
		IMethodBinding b = node.resolveMethodBinding();
		if (ctx.translatesTests() && JUnitSupport.isAssertionMethod(b)) {
			JUnitSupport.Assertion assertion = JUnitSupport.assertion(b,
					node.arguments());
			if (assertion != null) {
				printAssertion(node, assertion);
				return false;
			}
			// Unsupported forms are reported by JUnitSupport.problems
		}
		ITypeBinding rtb = b.getMethodDeclaration().getReturnType();
		boolean erased = (rtb.isTypeVariable() || rtb.isArray()
				&& rtb.getElementType().isTypeVariable())
				&& !(node.getParent() instanceof ExpressionStatement);

		if (erased) {
			javaCast(rtb.getErasure(), b.getReturnType());
		} else {
			ITypeBinding actualRt = TransformUtil.returnType(b
					.getMethodDeclaration().getDeclaringClass(), b
					.getMethodDeclaration());
			if (!actualRt.isEqualTo(rtb.getErasure())) {
				javaCast(actualRt, rtb);
				erased = true;
			}
		}

		Expression expr = node.getExpression();
		if (expr != null) {
			ITypeBinding etb = expr.resolveTypeBinding().getErasure();
			boolean isType = expr instanceof Name
					&& ((Name) expr).resolveBinding() instanceof ITypeBinding;

			int parens = 0;

			if (!isType && hidden(etb, b)) {
				// Method hidden by method in subclass
				assert (!TransformUtil.isStatic(b));
				staticCast(etb, b.getDeclaringClass());
				parens++;
			}

			if (TransformUtil.needsJavaCast(etb, b.getDeclaringClass())) {
				javaCast(etb, b.getDeclaringClass());
				parens++;
			}

			if (!isType) {
				npcAccept(expr);
			} else {
				expr.accept(this);
			}

			for (int i = 0; i < parens; ++i) {
				print(")");
			}

			if (isType) {
				print("::");
			} else {
				print("->");
			}

			hardDep(etb);
		} else {
			String bname = CName.of(b);
			boolean found = localInScope(bname);

			if (!b.getDeclaringClass().isEqualTo(type)) {
				for (IVariableBinding vb : type.getDeclaredFields()) {
					if (bname.equals(CName.of(vb))) {
						found = true;
					}
				}
			}

			// If the name is already qualified, there won't be a clash
			if (found
					&& !needsQualification(node.getName(),
							b.getDeclaringClass())) {
				if (TransformUtil.isStatic(b)) {
					print(CName.of(b.getDeclaringClass()) + "::");
				} else {
					boolean cast = !b.getDeclaringClass().isEqualTo(type);
					if (cast) {
						staticCast(type, b.getDeclaringClass());
					}

					print("this");

					if (cast) {
						print(")");
					}

					print("->");
				}
			}
		}

		print(TransformUtil.typeArguments(node.typeArguments()));

		node.getName().accept(this);

		callArgs(b, node.arguments(), true, false);

		if (erased) {
			print(")");
		}

		return false;
	}

	private boolean localInScope(String bname) {
		boolean found = false;
		for (List<String> l : locals) {
			if (l.contains(bname)) {
				found = true;
				break;
			}
		}
		return found;
	}

	private void callArgs(IMethodBinding b, List<Expression> arguments,
			boolean parens, boolean isCtorCall) {
		if (parens) {
			print("(");
		}

		String s = "";
		if (isCtorCall) {
			s = TransformUtil.printEnumCtorCallParams(out, type, "");
		}

		boolean isVarArg = false;
		b = b.getMethodDeclaration();

		boolean hasOverloads = TypeUtil.isOverloaded(b,
				ctx.resolve(Object.class));
		ITypeBinding[] paramTypes = b.getParameterTypes();
		for (int i = 0; i < arguments.size(); ++i) {
			print(s);
			s = ", ";
			if (b.isVarargs() && i == paramTypes.length - 1) {
				ITypeBinding tb = paramTypes[paramTypes.length - 1]
						.getErasure();
				ITypeBinding ab = arguments.get(i).resolveTypeBinding();

				if (!ab.isAssignmentCompatible(tb) || i != arguments.size() - 1) {
					hardDep(tb);
					print("new " + CName.relative(tb, type, true) + "({");
					isVarArg = true;
				}
			}

			ITypeBinding pb;
			if (b.isVarargs() && i >= paramTypes.length - 1) {
				pb = paramTypes[paramTypes.length - 1];
				if (isVarArg) {
					pb = pb.getComponentType();
				}
			} else {
				pb = paramTypes[i];
			}

			Expression argument = arguments.get(i);
			if (isVarArg) {
				arrayInitCast(paramTypes[paramTypes.length - 1].getErasure()
						.getComponentType(), argument);
			} else {
				cast(argument, pb, hasOverloads);
			}
			if (isVarArg && i == arguments.size() - 1
					&& i >= paramTypes.length - 1) {
				print("})");
			}
		}

		if (b.isVarargs() && arguments.size() < paramTypes.length) {
			print(s);

			ITypeBinding tb = paramTypes[paramTypes.length - 1];
			hardDep(tb);
			print("new " + CName.relative(tb, type, true) + "()");
		}

		if (parens) {
			print(")");
		}
	}

	private void javaCast(ITypeBinding source, ITypeBinding target) {
		hardDep(source);
		hardDep(target);
		deps.setJavaCast();
		print(CName.JAVA_CAST + "< " + CName.relative(target, type, true)
				+ "* >(");
	}

	private void cast(Expression argument, ITypeBinding pb, boolean hasOverloads) {
		ITypeBinding tb = argument.resolveTypeBinding();
		if (!tb.isEqualTo(pb)
				&& !(argument.resolveBoxing() || argument.resolveUnboxing())) {
			// Java has different implicit cast rules when resolving overloads
			// i e int -> double promotion, int vs pointer
			hardDep(tb);
			if (TransformUtil.needsJavaCast(tb, pb)) {
				javaCast(tb, pb);
				argument.accept(this);
				print(")");
			} else if (hasOverloads) {
				staticCast(tb, pb);
				argument.accept(this);
				print(")");
			} else {
				argument.accept(this);
			}
		} else {
			argument.accept(this);
		}
	}

	@Override
	public boolean visit(PostfixExpression node) {
		Expression operand = node.getOperand();
		if (boxedTarget(operand)) {
			String operator = node.getOperator() == PostfixExpression.Operator.INCREMENT ? "+"
					: "-";
			ASTNode parent = node.getParent();
			if (parent instanceof ExpressionStatement
					|| parent instanceof ForStatement) {
				// The old value is unused
				printBoxedUpdate(operand, operator, null);
			} else {
				// x++ evaluates to the old box
				ITypeBinding box = operand.resolveTypeBinding().getErasure();
				print("[&]() { auto _j2c_old = ");
				unboxedTargets.add(operand);
				operand.accept(this);
				print("; ");
				printBoxedStoreStart(operand);
				print(CName.relative(box, type, true) + "::valueOf(static_cast<"
						+ CName.of(unboxed(operand)) + ">(");
				npc();
				print("_j2c_old)->" + TransformUtil.reverses.get(box.getQualifiedName())
						+ "Value() " + operator + " 1))");
				printBoxedStoreEnd(operand);
				print("; return _j2c_old; }()");
			}
			return false;
		}
		operand.accept(this);
		print(node.getOperator().toString());
		return false;
	}

	@Override
	public boolean visit(PrefixExpression node) {
		Expression operand = node.getOperand();
		if ((node.getOperator() == PrefixExpression.Operator.INCREMENT || node
				.getOperator() == PrefixExpression.Operator.DECREMENT)
				&& boxedTarget(operand)) {
			print("(");
			printBoxedUpdate(operand,
					node.getOperator() == PrefixExpression.Operator.INCREMENT ? "+"
							: "-", null);
			print(")");
			return false;
		}
		print(node.getOperator().toString());
		operand.accept(this);
		return false;
	}

	/**
	 * Whether an updated variable is a box (Integer, Character...), which Java
	 * replaces with a new box rather than changing in place. An array element
	 * qualifies only when its array and index can be evaluated twice.
	 */
	private static boolean boxedTarget(Expression target) {
		ITypeBinding tb = target.resolveTypeBinding();
		if (tb == null || tb.isPrimitive()
				|| !TransformUtil.reverses.containsKey(tb.getErasure().getQualifiedName())) {
			return false;
		}
		if (target instanceof ArrayAccess) {
			ArrayAccess aa = (ArrayAccess) target;
			return repeatable(aa.getArray()) && repeatable(aa.getIndex());
		}
		return target instanceof Name || target instanceof FieldAccess;
	}

	/** Whether evaluating an expression twice is harmless */
	private static boolean repeatable(Expression e) {
		return e instanceof Name || e instanceof NumberLiteral
				|| e instanceof ThisExpression
				|| (e instanceof FieldAccess && ((FieldAccess) e).getExpression() instanceof ThisExpression);
	}

	/** "target = ", or "array->set(index, " for an element of an object array */
	private void printBoxedStoreStart(Expression target) {
		if (target instanceof ArrayAccess) {
			ArrayAccess aa = (ArrayAccess) target;
			hardDep(aa.getArray().resolveTypeBinding());
			aa.getArray().accept(this);
			print("->set(");
			aa.getIndex().accept(this);
			print(", ");
		} else {
			unboxedTargets.add(target);
			target.accept(this);
			print(" = ");
		}
	}

	private void printBoxedStoreEnd(Expression target) {
		if (target instanceof ArrayAccess) {
			print(")");
		}
	}

	private static ITypeBinding unboxed(Expression target) {
		return target.getAST().resolveWellKnownType(
				TransformUtil.reverses.get(target.resolveTypeBinding().getErasure()
						.getQualifiedName()));
	}

	/**
	 * target = Box::valueOf(static_cast<primitive>(target op value)), with
	 * value 1 when null (++ and --)
	 */
	private void printBoxedUpdate(Expression target, String operator,
			Expression value) {
		ITypeBinding box = target.resolveTypeBinding().getErasure();
		ITypeBinding primitive = unboxed(target);
		hardDep(box);
		printBoxedStoreStart(target);
		print(CName.relative(box, type, true) + "::valueOf(static_cast<"
				+ CName.of(primitive) + ">(");
		String helper = value != null && isIntegral(primitive) ? integerHelper(operator)
				: null;
		if (helper != null) {
			boolean shift = !helper.equals("jdiv") && !helper.equals("jrem");
			String promotedType = promoted(primitive, null);
			if (!shift) {
				promotedType = promoted(promotedType, operandType(value));
			}
			deps.setNeedsJavaMath();
			print(helper + "<" + promotedType + ">(");
			target.accept(this);
			print(", ");
			value.accept(this);
			print(")");
		} else {
			target.accept(this);
			print(" " + operator + " ");
			if (value == null) {
				print("1");
			} else {
				hardDep(value.resolveTypeBinding());
				value.accept(this);
			}
		}
		print("))");
		printBoxedStoreEnd(target);
	}

	@Override
	public boolean visit(ReturnStatement node) {
		printi("return");
		if (node.getExpression() != null) {
			hardDep(node.getExpression().resolveTypeBinding());
			print(" ");
			node.getExpression().accept(this);
		}

		println(";");
		return false;
	}

	@Override
	public boolean visit(SimpleName node) {
		IBinding b = node.resolveBinding();
		if (b instanceof IVariableBinding) {
			String patternName = patternNames.get(b.getKey());
			if (patternName != null) {
				print(patternName);
				return false;
			}
		}
		boolean ret = super.visit(node);

		if (b instanceof IVariableBinding) {
			IVariableBinding vb = (IVariableBinding) b;
			if (TransformUtil.asMethod(vb)) {
				if (vb.getDeclaringClass().isEqualTo(type)) {
					print("_");
				} else {
					print("()");
				}
			}

			if (isVolatileAccess(node)) {
				print(".load()");
			}
		}

		return ret;
	}

	@Override
	public boolean visit(SingleVariableDeclaration node) {
		IVariableBinding vb = node.resolveBinding();
		if (!vb.isField()) {
			locals.get(locals.size() - 1).add(CName.of(vb));
		}

		ITypeBinding tb = node.getType().resolveBinding();
		if (node.getExtraDimensions() > 0) {
			tb = tb.createArrayType(node.getExtraDimensions());
		}

		if (node.isVarargs()) {
			tb = tb.createArrayType(1);
			print(TransformUtil.relativeRef(tb, type, true));
			print("/*...*/");
		} else {
			print(TransformUtil.varTypeCName(vb.getModifiers(), tb, type, deps));
		}

		print(" ");

		SimpleName name = node.getName();
		Expression initializer = node.getInitializer();
		acceptVariableInit(vb, name, initializer);

		return false;
	}

	private void acceptVariableInit(IVariableBinding vb, SimpleName name,
			Expression initializer) {
		if (initializer != null) {
			print(TransformUtil.constVar(vb));
			name.accept(this);
			ITypeBinding ntb = initializer.resolveTypeBinding();
			hardDep(ntb);
			print(" = ");

			boolean cast = false;
			if (TransformUtil.needsJavaCast(ntb, vb.getType())) {
				javaCast(ntb, vb.getType());
				cast = true;
			}

			initializer.accept(this);
			if (cast) {
				print(")");
			}
		} else {
			name.accept(this);
		}
	}

	@Override
	public boolean visit(SuperConstructorInvocation node) {
		// We skip node.getExpression() here as that should have been taken
		// care of by the constructor invocation
		printi(TransformUtil.typeArguments(node.typeArguments()));

		print("super::" + CName.CTOR);

		callArgs(node.resolveConstructorBinding(), node.arguments(), true, true);

		println(";");
		return false;
	}

	@Override
	public boolean visit(SuperFieldAccess node) {
		// Qualification is handled in TransformWriter.visit(SimpleName)
		node.getName().accept(this);

		return false;
	}

	@Override
	public boolean visit(SuperMethodInvocation node) {
		IMethodBinding b = node.resolveMethodBinding();
		boolean erased = TransformUtil.returnErased(b);

		if (erased) {
			javaCast(b.getMethodDeclaration().getReturnType().getErasure(),
					b.getReturnType());
		}

		// Qualification is handled in TransformWriter.visit(SimpleName)
		print(TransformUtil.typeArguments(node.typeArguments()));

		node.getName().accept(this);

		callArgs(b, node.arguments(), true, false);

		if (erased) {
			print(")");
		}

		return false;
	}

	@Override
	public boolean visit(SwitchCase node) {
		printi();

		if (node.isDefault()) {
			print("default:");
		} else {
			for (Expression expression : (Iterable<Expression>) node
					.expressions()) {
				print("case ");
				expression.accept(this);
				print(": ");
			}
		}

		return false;
	}

	@Override
	public boolean visit(SwitchExpression node) {
		ITypeBinding result = node.resolveTypeBinding();
		hardDep(result);
		print("([&]() -> " + TransformUtil.relativeRef(result, type, true)
				+ " {");
		println();
		indent++;
		ITypeBinding selector = node.getExpression().resolveTypeBinding();
		if ((selector.isEnum() || isString(selector))
				&& hasSwitchRules(node.statements())) {
			conditionalSwitch(node, isString(selector));
		} else if (selector.isEnum() || isString(selector)) {
			// Colon form may fall through; yield returns from the lambda
			enumSwitch(node.getExpression(), node, node.statements(),
					isString(selector));
		} else {
			printi("switch (");
			node.getExpression().accept(this);
			println(") {");
			indent++;
			for (Statement statement : (Iterable<Statement>) node.statements()) {
				statement.accept(this);
			}
			indent--;
			printlni("}");
		}
		printlni("throw \"Non-exhaustive Java switch expression\";");
		indent--;
		printi("}())");
		return false;
	}

	/**
	 * Rule-form ("case X ->") switch expression on an enum or String: each
	 * rule has exactly one body statement, and rules never fall through, so
	 * an if per rule with the default last is equivalent.
	 */
	private void conditionalSwitch(SwitchExpression node, boolean strings) {
		String name = "v";
		while (localInScope(name)) {
			name = name + "_";
		}
		locals.add(new ArrayList<String>());
		locals.get(locals.size() - 1).add(name);

		// npc() throws NullPointerException for a null selector, as Java does
		printi("auto " + name + " = ");
		npc();
		node.getExpression().accept(this);
		println(");");

		List<Statement> statements = node.statements();
		Statement defaultBody = null;
		for (int i = 0; i + 1 < statements.size(); i += 2) {
			SwitchCase sc = (SwitchCase) statements.get(i);
			Statement body = statements.get(i + 1);
			if (sc.isDefault()) {
				defaultBody = body;
				continue;
			}

			printi("if (");
			String sep = "";
			for (Expression label : (Iterable<Expression>) sc.expressions()) {
				print(sep);
				sep = " || ";
				printCaseTest(name, label, true, strings);
			}
			println(") {");
			indent++;
			ruleBody(body);
			indent--;
			printlni("}");
		}
		if (defaultBody != null) {
			ruleBody(defaultBody);
		}

		locals.remove(locals.size() - 1);
	}

	private void ruleBody(Statement body) {
		if (body instanceof Block) {
			// Blocks print their braces inline, unlike other statements
			printi();
			body.accept(this);
			println();
		} else {
			body.accept(this);
		}
	}

	@Override
	public boolean visit(YieldStatement node) {
		if (node.isImplicit() && node.getParent() instanceof SwitchStatement) {
			// Expression body of a "case X -> expr;" rule in a switch statement
			printi();
			node.getExpression().accept(this);
			println(";");
			return false;
		}

		printi("return ");
		node.getExpression().accept(this);
		println(";");
		return false;
	}

	@Override
	public boolean visit(SwitchStatement node) {
		List<Statement> statements = node.statements();
		List<VariableDeclarationStatement> vdss = declarations(statements);

		if (!vdss.isEmpty()) {
			printlni("{");
			indent++;

			for (VariableDeclarationStatement vds : vdss) {
				for (VariableDeclarationFragment fragment : (Iterable<VariableDeclarationFragment>) vds
						.fragments()) {
					ITypeBinding fb = fragment.resolveBinding().getType();
					hardDep(fb);

					printi(TransformUtil.variableModifiers(type,
							vds.getModifiers()));
					print(TransformUtil.relativeRef(fb, type, true) + " ");
					fragment.getName().accept(this);
					println(";");
				}
			}
		}

		ITypeBinding selector = node.getExpression().resolveTypeBinding();
		if (selector.isEnum() || isString(selector)) {
			enumSwitch(node.getExpression(), node, statements,
					isString(selector));
		} else {
			nativeSwitch(node, statements);
		}

		if (!vdss.isEmpty()) {
			indent--;
			printlni("}");
		}
		println();

		return false;
	}

	/** Switch statements and expressions being written as if-chains */
	private final List<ASTNode> enumSwitches = new ArrayList<ASTNode>();
	private int enumSwitchCount = 0;
	private final List<String> enumSwitchLabels = new ArrayList<String>();

	private void enumSwitch(Expression selector, ASTNode node,
			List<Statement> statements, boolean strings) {
		// Enum constants as we translate them are not C++ constexpr:s, and
		// strings compare with equals(), so we have to rewrite the switch to
		// if:s
		printlni("{");
		indent++;

		String name = "v";
		while (localInScope(name)) {
			name = name + "_";
		}

		locals.add(new ArrayList<String>());
		locals.get(locals.size() - 1).add(name);
		printi("auto " + name + " = ");
		selector.accept(this);
		println(";");

		List<SwitchCase> cases = new ArrayList<SwitchCase>();
		List<SwitchCase> allCases = new ArrayList<SwitchCase>();
		enumSwitches.add(node);
		enumSwitchLabels.add("end_switch" + enumSwitchCount);
		enumSwitchCount++;

		boolean arrows = hasSwitchRules(statements);
		boolean wasCase = false;
		boolean indented = false;
		for (Statement s : statements) {
			if (s instanceof SwitchCase) {
				if (!wasCase && indented) {
					indent--;
					printlni("}");
					indented = false;
				}

				SwitchCase sc = (SwitchCase) s;
				if (!sc.isDefault()) {
					cases.add(sc);
				}

				allCases.add(sc);
				wasCase = true;
			} else {
				if (wasCase) {
					printi("if(");
					String sep = "";
					boolean hasDefault = allCases.get(allCases.size() - 1)
							.isDefault();

					if (hasDefault) {
						print("(");
					}
					// JLS14+ cases hold every label in expressions();
					// getExpression() only yields a MISSING placeholder
					for (SwitchCase x : cases) {
						for (Expression label : (Iterable<Expression>) x
								.expressions()) {
							print(sep);
							sep = " || ";
							printCaseTest(name, label, true, strings);
						}
					}

					if (hasDefault) {
						print(sep);
						sep = "";
						print("(");
						for (SwitchCase x : allCases) {
							for (Expression label : (Iterable<Expression>) x
									.expressions()) {
								print(sep);
								sep = " && ";
								printCaseTest(name, label, false, strings);
							}
						}
						if (sep.isEmpty()) {
							// default is the only label
							print("true");
						}
						print("))");
					}

					println(") {");
					indent++;
					indented = true;
				}

				wasCase = false;
				if (s instanceof BreakStatement || s instanceof ReturnStatement
						|| s instanceof YieldStatement) {
					cases.clear();
				}
				ruleBody(s);
				if (arrows) {
					printlni("goto " + enumSwitchLabel() + ";");
					cases.clear();
				}
			}

		}

		if (indented) {
			indent--;
			printlni("}");
		}

		String label = enumSwitchLabel();
		println(label + ":;");
		enumSwitches.remove(enumSwitches.size() - 1);
		enumSwitchLabels.remove(enumSwitchLabels.size() - 1);

		indent--;
		locals.remove(locals.size() - 1);
		printlni("}");
	}

	private static boolean isString(ITypeBinding tb) {
		return tb != null && "java.lang.String".equals(tb.getQualifiedName());
	}

	private void printCaseTest(String name, Expression label, boolean equal,
			boolean strings) {
		if (strings) {
			print("(" + (equal ? "" : "!"));
			npc();
			print(name + ")->equals(");
			label.accept(this);
			print("))");
		} else {
			print("(" + name + (equal ? " == " : " != "));
			label.accept(this);
			print(")");
		}
	}

	private String enumSwitchLabel() {
		return enumSwitchLabels.get(enumSwitchLabels.size() - 1);
	}

	private void nativeSwitch(SwitchStatement node, List<Statement> statements) {
		printi("switch (");
		node.getExpression().accept(this);
		println(") {");

		boolean arrows = hasSwitchRules(statements);
		boolean indented = false;
		boolean wasCase = false;
		for (int i = 0; i < statements.size(); ++i) {
			Statement s = statements.get(i);

			if (s instanceof VariableDeclarationStatement) {
				if (wasCase) {
					println();
				}
				VariableDeclarationStatement vds = (VariableDeclarationStatement) s;

				for (VariableDeclarationFragment fragment : (Iterable<VariableDeclarationFragment>) vds
						.fragments()) {
					if (fragment.getInitializer() != null) {
						printi();
						fragment.getName().accept(this);
						print(" = ");
						fragment.getInitializer().accept(this);
						println(";");
						// The declaration is hoisted, so converting the value
						// to the variable's type needs its complete class
						ITypeBinding ib = fragment.getInitializer()
								.resolveTypeBinding();
						if (ib != null
								&& !ib.isEqualTo(fragment.resolveBinding().getType())) {
							hardDep(ib);
						}
					}
				}
			} else if (s instanceof SwitchCase) {
				if (wasCase) {
					println();
				}

				if (indented) {
					indent--;
				}

				s.accept(this);

				if (i == statements.size() - 1) {
					println(" { }");
				}
				indent++;
				indented = true;
			} else if (s instanceof Block) {
				if (wasCase) {
					print(" ");
				}
				s.accept(this);
				println();
				if (arrows) {
					printlni("break;");
				}
			} else {
				if (wasCase) {
					println();
				}

				s.accept(this);
				if (arrows) {
					printlni("break;");
				}
			}

			wasCase = s instanceof SwitchCase;
		}

		if (indented) {
			indent--;
			indented = false;
		}

		printlni("}");
	}

	/** "case X ->" rules never fall through, unlike "case X:" groups */
	private static boolean hasSwitchRules(List<Statement> statements) {
		for (Statement s : statements) {
			if (s instanceof SwitchCase && ((SwitchCase) s).isSwitchLabeledRule()) {
				return true;
			}
		}
		return false;
	}

	private static List<VariableDeclarationStatement> declarations(
			List<Statement> statements) {
		List<VariableDeclarationStatement> ret = new ArrayList<VariableDeclarationStatement>();
		for (Statement s : statements) {
			if (s instanceof VariableDeclarationStatement) {
				ret.add((VariableDeclarationStatement) s);
			}
		}
		return ret;
	}

	private int sc;

	@Override
	public boolean visit(SynchronizedStatement node) {
		printlni("{");
		indent++;
		printi("synchronized synchronized_" + sc + "(");
		hardDep(node.getExpression().resolveTypeBinding());
		node.getExpression().accept(this);
		println(");");
		printi();
		node.getBody().accept(this);
		println();
		indent--;

		printlni("}");

		deps.setNeedsSynchronized();

		sc++;
		return false;
	}

	@Override
	public boolean visit(ThisExpression node) {
		if (!qualify(node.getQualifier())) {
			print("this");
		}

		return false;
	}

	private boolean qualify(Name qualifier) {
		if (qualifier == null) {
			return false;
		}

		ITypeBinding qt = qualifier.resolveTypeBinding().getErasure();
		ITypeBinding x = type.getErasure();
		if (x.isSubTypeCompatible(qt)) {
			return false;
		}

		String sep = "";
		for (; x.getDeclaringClass() != null && !x.isSubTypeCompatible(qt); x = x
				.getDeclaringClass().getErasure()) {
			hardDep(x.getDeclaringClass());

			print(sep + TransformUtil.outerThisName(x));
			sep = "->";
		}

		return true;
	}

	@Override
	public boolean visit(ThrowStatement node) {
		Expression expr = node.getExpression();
		hardDep(expr.resolveTypeBinding());
		if (expr instanceof ClassInstanceCreation) {
			// The static type of a new object is its dynamic type
			printi("throw ");
			expr.accept(this);
			println(";");
		} else {
			// Throw the dynamic type, and NullPointerException for null
			printi();
			npc();
			expr.accept(this);
			println(")->" + CName.THROW + "();");
		}

		return false;
	}

	private int fc;

	@Override
	public boolean visit(TryStatement node) {
		if (node.getFinally() != null) {
			deps.setNeedsFinally();
			printlni("{");
			indent++;

			printi("auto finally" + fc + " = finally([&] ");
			node.getFinally().accept(this);
			println(");");
			fc++;
		}

		if (!node.catchClauses().isEmpty()) {
			printi("try ");
		} else {
			printi();
		}

		if (node.resources().isEmpty()) {
			node.getBody().accept(this);
		} else {
			resourceBlock(node);
		}

		visitAll(node.catchClauses());

		if (node.getFinally() != null) {
			indent--;
			println();
			printi("}");
			if (node.catchClauses().isEmpty()) {
				println();
			}
		}
		println();

		return false;
	}

	/**
	 * Java closes resources in reverse order after the body and before any
	 * catch or finally clause runs, adding exceptions from close() to the
	 * suppressed list of an exception already thrown (JLS 14.20.3.1). Each
	 * resource gets a block, nested in the order of declaration: a guard
	 * closes it on a normal exit, and a handler closes it and rethrows on an
	 * exceptional one. The enclosing block ends before the surrounding try's
	 * handlers run.
	 */
	private void resourceBlock(TryStatement node) {
		deps.setNeedsFinally();
		println("{");
		indent++;
		resource(node, 0);
		indent--;
		printi("}");
	}

	private void resource(TryStatement node, int i) {
		if (i == node.resources().size()) {
			printi();
			node.getBody().accept(this);
			println();
			return;
		}

		Expression resource = (Expression) node.resources().get(i);
		Expression ref = resource;
		if (resource instanceof VariableDeclarationExpression) {
			VariableDeclarationExpression vde = (VariableDeclarationExpression) resource;
			printi();
			vde.accept(this);
			println(";");
			ref = ((VariableDeclarationFragment) vde.fragments().get(0))
					.getName();
		}

		hardDep(ref.resolveTypeBinding());
		ITypeBinding throwable = node.getAST().resolveWellKnownType(
				"java.lang.Throwable");
		hardDep(throwable);
		String throwableRef = TransformUtil.relativeRef(throwable, type, true);
		int n = fc++;

		printi("auto finally" + n + " = on_normal_exit([&] { if (");
		ref.accept(this);
		print(" != nullptr) { ");
		ref.accept(this);
		println("->close(); } });");

		printlni("try {");
		indent++;
		resource(node, i + 1);
		indent--;
		printlni("} catch (" + throwableRef + " primary" + n + ") {");
		indent++;
		printi("if (");
		ref.accept(this);
		println(" != nullptr) {");
		indent++;
		printlni("try {");
		indent++;
		printi();
		ref.accept(this);
		println("->close();");
		indent--;
		printlni("} catch (" + throwableRef + " suppressed" + n + ") {");
		indent++;
		printlni("primary" + n + "->addSuppressed(suppressed" + n + ");");
		indent--;
		printlni("}");
		indent--;
		printlni("}");
		printlni("throw;");
		indent--;
		printlni("}");
	}

	@Override
	public boolean visit(TypeDeclaration node) {
		ctx.write(unitInfo, root, node);

		return false;
	}

	@Override
	public boolean visit(RecordDeclaration node) {
		ctx.write(unitInfo, root, node);

		return false;
	}

	@Override
	public boolean visit(TypeLiteral node) {
		if (node.getType().isPrimitiveType()) {
			Code code = ((PrimitiveType) node.getType()).getPrimitiveTypeCode();
			if (code.equals(PrimitiveType.BOOLEAN)) {
				hardDep(node.getAST().resolveWellKnownType("java.lang.Boolean"));
				print("::java::lang::Boolean::TYPE()");
			} else if (code.equals(PrimitiveType.BYTE)) {
				hardDep(node.getAST().resolveWellKnownType("java.lang.Byte"));
				print("::java::lang::Byte::TYPE()");
			} else if (code.equals(PrimitiveType.CHAR)) {
				hardDep(node.getAST().resolveWellKnownType(
						"java.lang.Character"));
				print("::java::lang::Character::TYPE()");
			} else if (code.equals(PrimitiveType.DOUBLE)) {
				hardDep(node.getAST().resolveWellKnownType("java.lang.Double"));
				print("::java::lang::Double::TYPE()");
			} else if (code.equals(PrimitiveType.FLOAT)) {
				hardDep(node.getAST().resolveWellKnownType("java.lang.Float"));
				print("::java::lang::Float::TYPE()");
			} else if (code.equals(PrimitiveType.INT)) {
				hardDep(node.getAST().resolveWellKnownType("java.lang.Integer"));
				print("::java::lang::Integer::TYPE()");
			} else if (code.equals(PrimitiveType.LONG)) {
				hardDep(node.getAST().resolveWellKnownType("java.lang.Long"));
				print("::java::lang::Long::TYPE()");
			} else if (code.equals(PrimitiveType.SHORT)) {
				hardDep(node.getAST().resolveWellKnownType("java.lang.Short"));
				print("::java::lang::Short::TYPE()");
			} else if (code.equals(PrimitiveType.VOID)) {
				hardDep(node.getAST().resolveWellKnownType("java.lang.Void"));
				print("::java::lang::Void::TYPE()");
			}
		} else {
			hardDep(node.getType().resolveBinding());
			node.getType().accept(this);
			print("::class_()");
		}
		return false;
	}

	@Override
	public boolean visit(TypeParameter node) {
		node.getName().accept(this);
		if (!node.typeBounds().isEmpty()) {
			print(" extends ");
			for (Iterator<Type> it = node.typeBounds().iterator(); it.hasNext();) {
				Type t = it.next();
				t.accept(this);
				if (it.hasNext()) {
					print(" & ");
				}
			}
		}
		return false;
	}

	@Override
	public boolean visit(UnionType node) {
		for (Iterator<Type> it = node.types().iterator(); it.hasNext();) {
			Type t = it.next();
			t.accept(this);
			if (it.hasNext()) {
				print("|");
			}
		}
		return false;
	}

	@Override
	public boolean visit(VariableDeclarationFragment node) {
		IVariableBinding vb = node.resolveBinding();
		if (!vb.isField()) {
			locals.get(locals.size() - 1).add(CName.of(vb));
		}

		// VariableDeclarationStatements lack ref - they can never be volatile
		if (node.getParent() instanceof VariableDeclarationExpression) {
			print(TransformUtil.ref(vb.getType()));
		}

		acceptVariableInit(vb, node.getName(), node.getInitializer());

		return false;
	}

	@Override
	public boolean visit(VariableDeclarationExpression node) {
		int modifiers = node.getModifiers();
		print(TransformUtil.variableModifiers(type, modifiers));
		if (TransformUtil.canDeclareAuto(node, null)) {
			print("auto ");
		} else {
			print(CName.relative(node.getType().resolveBinding(), type, true)
					+ " ");
		}

		visitAllCSV(node.fragments(), false);

		return false;
	}

	@Override
	public boolean visit(VariableDeclarationStatement node) {
		List<VariableDeclarationFragment> fragments = node.fragments();

		int modifiers = node.getModifiers();
		if (isAnySpecial(fragments)) {
			for (VariableDeclarationFragment fragment : fragments) {
				printi(TransformUtil.variableModifiers(type, modifiers));
				ITypeBinding fb = fragment.resolveBinding().getType();
				softDep(fb);

				if (TransformUtil.canDeclareAuto(node, fragment)) {
					print("auto ");
				} else {
					print(TransformUtil.varTypeCName(modifiers, fb, type, deps)
							+ " ");
				}
				fragment.accept(this);
				println(";");
			}
		} else {
			printi(TransformUtil.variableModifiers(type, modifiers));
			if (TransformUtil.canDeclareAuto(node, null)) {
				print("auto ");
			} else {
				print(TransformUtil.varTypeCName(modifiers, node.getType()
						.resolveBinding(), type, deps)
						+ " ");
			}

			visitAllCSV(fragments, false);

			println(";");
		}

		return false;
	}

	@Override
	public boolean visit(WhileStatement node) {
		printi("while (");
		node.getExpression().accept(this);
		print(") ");
		handleLoopBody(node, node.getBody());

		return false;
	}
}
