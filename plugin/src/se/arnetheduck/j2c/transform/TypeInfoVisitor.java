package se.arnetheduck.j2c.transform;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.RecordDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

public class TypeInfoVisitor extends ASTVisitor {
	private UnitInfo unitInfo;
	private TypeInfo typeInfo;

	public TypeInfoVisitor(UnitInfo unitInfo) {
		this.unitInfo = unitInfo;
	}

	@Override
	public boolean visit(AnnotationTypeDeclaration node) {
		typeInfo = new TypeInfo(typeInfo, node.resolveBinding());
		unitInfo.types.put(typeInfo.type(), typeInfo);
		return true;
	}

	@Override
	public void endVisit(AnnotationTypeDeclaration node) {
		typeInfo = typeInfo.parent();
	}

	@Override
	public boolean visit(AnonymousClassDeclaration node) {
		typeInfo = new TypeInfo(typeInfo, node.resolveBinding());
		unitInfo.types.put(typeInfo.type(), typeInfo);
		return true;
	}

	@Override
	public void endVisit(AnonymousClassDeclaration node) {
		TypeInfo anonType = typeInfo;
		typeInfo = typeInfo.parent();

		if (typeInfo.closures() != null && anonType.closures() != null) {
			for (IVariableBinding vb : anonType.closures()) {
				boolean outer = vb.getDeclaringMethod() == null ? !declaredWithin(
						enclosingTypeBody(node.getParent()), vb)
						: !vb.getDeclaringMethod().getDeclaringClass()
								.isEqualTo(typeInfo.type());
				if (outer) {
					typeInfo.addClosure(vb);
				}
			}
		}
	}

	/** The nearest class body around a node */
	private static ASTNode enclosingTypeBody(ASTNode node) {
		for (ASTNode n = node; n != null; n = n.getParent()) {
			if (n instanceof AbstractTypeDeclaration
					|| n instanceof AnonymousClassDeclaration) {
				return n;
			}
		}
		return null;
	}

	/** Whether a variable is declared inside scope; unknown counts as inside */
	private static boolean declaredWithin(ASTNode scope, IVariableBinding vb) {
		if (scope == null || !(scope.getRoot() instanceof CompilationUnit)) {
			return true;
		}
		ASTNode declaration = ((CompilationUnit) scope.getRoot())
				.findDeclaringNode(vb);
		if (declaration == null) {
			return true;
		}
		for (ASTNode n = declaration; n != null; n = n.getParent()) {
			if (n == scope) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean visit(EnumDeclaration node) {
		typeInfo = new TypeInfo(typeInfo, node.resolveBinding());
		unitInfo.types.put(typeInfo.type(), typeInfo);
		return true;
	}

	@Override
	public void endVisit(EnumDeclaration node) {
		typeInfo = typeInfo.parent();
	}

	@Override
	public boolean visit(MethodDeclaration node) {
		if (Modifier.isNative(node.getModifiers())) {
			typeInfo.setHasNatives();
		}

		if (TransformUtil.isMain(node.resolveBinding())) {
			typeInfo.setHasMain();
		}

		return true;
	}

	@Override
	public boolean visit(TypeDeclaration node) {
		typeInfo = new TypeInfo(typeInfo, node.resolveBinding());
		unitInfo.types.put(typeInfo.type(), typeInfo);
		return true;
	}

	@Override
	public boolean visit(RecordDeclaration node) {
		typeInfo = new TypeInfo(typeInfo, node.resolveBinding());
		unitInfo.types.put(typeInfo.type(), typeInfo);
		return true;
	}

	@Override
	public void endVisit(RecordDeclaration node) {
		typeInfo = typeInfo.parent();
	}

	@Override
	public void endVisit(TypeDeclaration node) {
		typeInfo = typeInfo.parent();
	}


	@Override
	public boolean visit(Initializer node) {
		typeInfo.addInit(node);
		return super.visit(node);
	}

	@Override
	public boolean visit(SimpleName node) {
		IBinding b = node.resolveBinding();
		if (b instanceof IVariableBinding) {
			IVariableBinding vb = (IVariableBinding) b;

			if (isClosure(node, vb)) {
				typeInfo.addClosure(vb);
			}
		}

		return super.visit(node);
	}

	@Override
	public boolean visit(VariableDeclarationFragment node) {
		if (TransformUtil.initInInit(node)) {
			typeInfo.addInit(node);
		}

		return super.visit(node);
	}

	private boolean isClosure(SimpleName node, IVariableBinding vb) {
		if (vb.isField()) {
			return false;
		}

		// A local of an initializer block is captured when it is declared
		// outside the class using it (the CLI has no Eclipse Java model)
		if (vb.getDeclaringMethod() == null
				&& declaredWithin(enclosingTypeBody(node), vb)) {
			return false;
		}

		// Java 8+ captures effectively final variables too, which lambdas
		// (rewritten into anonymous classes) almost always rely on
		if (!Modifier.isFinal(vb.getModifiers()) && !vb.isEffectivelyFinal()) {
			return false;
		}
		VariableDeclarationFragment vdf = initializer(node);
		if (vdf == null && vb.getDeclaringMethod() != null) {
			IMethodBinding pmb = parentMethod(node);
			if (pmb.isEqualTo(vb.getDeclaringMethod())) {
				// Final local variable
				return false;
			}
		}
		return true;
	}

	private static IMethodBinding parentMethod(ASTNode node) {
		for (ASTNode n = node; n != null; n = n.getParent()) {
			if (n instanceof MethodDeclaration) {
				return ((MethodDeclaration) n).resolveBinding();
			}
		}

		return null;
	}

	private VariableDeclarationFragment initializer(ASTNode node) {
		for (ASTNode n = node; n != null; n = n.getParent()) {
			if (n.getParent() instanceof VariableDeclarationFragment) {
				VariableDeclarationFragment vdf = (VariableDeclarationFragment) n
						.getParent();
				if (type().isEqualTo(vdf.resolveBinding().getDeclaringClass())
						&& vdf.getInitializer() == n) {
					return vdf;
				}
			}
		}

		return null;
	}

	private ITypeBinding type() {
		return typeInfo.type();
	}
}
